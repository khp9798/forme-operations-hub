from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

import psycopg
from airflow.sdk import dag, get_current_context, task
from airflow.sdk.bases.hook import BaseHook


DAG_ID = "forme_daily_sales_pipeline"


def warehouse_connection() -> psycopg.Connection:
    connection = BaseHook.get_connection("forme_warehouse")
    return psycopg.connect(
        host=connection.host,
        port=connection.port or 5432,
        dbname=connection.schema,
        user=connection.login,
        password=connection.password,
    )


def pipeline_run_id(dag_run_id: str) -> uuid.UUID:
    return uuid.uuid5(uuid.NAMESPACE_URL, f"{DAG_ID}:{dag_run_id}")


def requested_data_interval(context) -> tuple[datetime, datetime]:
    interval_start = context["data_interval_start"]
    interval_end = context["data_interval_end"]
    if interval_end <= interval_start:
        interval_start = context["logical_date"]
        interval_end = interval_start + timedelta(hours=1)
    return interval_start, interval_end


def should_reprocess_rejected(context) -> bool:
    dag_run = context.get("dag_run")
    return bool(dag_run and (dag_run.conf or {}).get("reprocess_rejected", False))


def mark_pipeline_failed(context) -> None:
    dag_run_id = context["run_id"]
    run_id = pipeline_run_id(dag_run_id)
    exception = context.get("exception")
    error_message = str(exception)[:1000] if exception else "Airflow task failed"

    with warehouse_connection() as connection, connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE analytics.pipeline_runs
            SET status = 'FAILED', error_message = %s, completed_at = CURRENT_TIMESTAMP
            WHERE id = %s
            """,
            (error_message, run_id),
        )


@dag(
    dag_id=DAG_ID,
    schedule="0 * * * *",
    start_date=datetime(2026, 1, 1, tzinfo=timezone.utc),
    catchup=False,
    max_active_runs=1,
    default_args={"retry_delay": timedelta(seconds=10)},
    on_failure_callback=mark_pipeline_failed,
    tags=["forme", "orders", "analytics"],
)
def daily_sales_pipeline():
    @task(retries=2)
    def start_pipeline() -> str:
        context = get_current_context()
        dag_run_id = context["run_id"]
        run_id = pipeline_run_id(dag_run_id)
        interval_start, interval_end = requested_data_interval(context)

        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO analytics.pipeline_runs (
                    id, dag_id, dag_run_id, data_interval_start, data_interval_end, status
                ) VALUES (%s, %s, %s, %s, %s, 'RUNNING')
                ON CONFLICT (dag_id, dag_run_id) DO UPDATE
                SET status = 'RUNNING', error_message = NULL, completed_at = NULL
                """,
                (run_id, DAG_ID, dag_run_id, interval_start, interval_end),
            )
        return str(run_id)

    @task(retries=2)
    def extract_to_staging(pipeline_id: str) -> int:
        context = get_current_context()
        interval_start, interval_end = requested_data_interval(context)
        reprocess_rejected = should_reprocess_rejected(context)

        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO analytics.stg_order_items (
                    id, pipeline_run_id, source_order_item_id, source_system,
                    source_order_id, ordered_at, sku_code, brand_code, style_code,
                    product_name, quantity, unit_price, currency, source_updated_at
                )
                SELECT
                    gen_random_uuid(), %s, item.id, orders.source_system,
                    orders.source_order_id, orders.ordered_at, sku.sku_code,
                    product.brand_code, product.style_code, product.name,
                    item.quantity, item.unit_price, orders.currency,
                    GREATEST(orders.updated_at, sku.updated_at, product.updated_at)
                FROM external_order_items item
                JOIN external_orders orders ON orders.id = item.order_id
                JOIN skus sku ON sku.id = item.sku_id
                JOIN products product ON product.id = sku.product_id
                WHERE (orders.ordered_at >= %s AND orders.ordered_at < %s)
                   OR (%s AND EXISTS (
                       SELECT 1 FROM analytics.rejected_order_items rejected
                       WHERE rejected.source_order_item_id = item.id
                         AND rejected.resolution_status = 'PENDING'
                   ))
                ON CONFLICT (pipeline_run_id, source_order_item_id) DO NOTHING
                """,
                (pipeline_id, interval_start, interval_end, reprocess_rejected),
            )
            extracted_count = cursor.rowcount
            cursor.execute(
                """
                UPDATE analytics.pipeline_runs
                SET extracted_count = %s
                WHERE id = %s
                """,
                (extracted_count, pipeline_id),
            )
        return extracted_count

    @task(retries=1)
    def validate_and_quarantine(pipeline_id: str) -> int:
        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                UPDATE analytics.stg_order_items staging
                SET validation_status = CASE
                        WHEN sku.active AND product.active THEN 'VALID'
                        ELSE 'INVALID'
                    END,
                    validation_errors = CASE
                        WHEN NOT sku.active THEN '["INACTIVE_SKU"]'::jsonb
                        WHEN NOT product.active THEN '["INACTIVE_PRODUCT"]'::jsonb
                        ELSE '[]'::jsonb
                    END
                FROM external_order_items item
                JOIN skus sku ON sku.id = item.sku_id
                JOIN products product ON product.id = sku.product_id
                WHERE staging.source_order_item_id = item.id
                  AND staging.pipeline_run_id = %s
                """,
                (pipeline_id,),
            )
            cursor.execute(
                """
                INSERT INTO analytics.rejected_order_items (
                    id, pipeline_run_id, staging_row_id, source_order_item_id, error_codes
                )
                SELECT gen_random_uuid(), pipeline_run_id, id, source_order_item_id, validation_errors
                FROM analytics.stg_order_items
                WHERE pipeline_run_id = %s AND validation_status = 'INVALID'
                ON CONFLICT (pipeline_run_id, staging_row_id) DO UPDATE
                SET error_codes = EXCLUDED.error_codes, rejected_at = CURRENT_TIMESTAMP
                """,
                (pipeline_id,),
            )
            cursor.execute(
                """
                SELECT COUNT(*)
                FROM analytics.stg_order_items
                WHERE pipeline_run_id = %s AND validation_status = 'INVALID'
                """,
                (pipeline_id,),
            )
            rejected_count = cursor.fetchone()[0]
            cursor.execute(
                "UPDATE analytics.pipeline_runs SET rejected_count = %s WHERE id = %s",
                (rejected_count, pipeline_id),
            )
        return rejected_count

    @task(retries=2)
    def load_dimensions(pipeline_id: str) -> int:
        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO analytics.dim_products (
                    source_product_id, brand_code, style_code, product_name,
                    season_code, active, source_updated_at
                )
                SELECT DISTINCT ON (product.id)
                    product.id, product.brand_code, product.style_code, product.name,
                    product.season_code, product.active, product.updated_at
                FROM analytics.stg_order_items staging
                JOIN external_order_items item ON item.id = staging.source_order_item_id
                JOIN skus sku ON sku.id = item.sku_id
                JOIN products product ON product.id = sku.product_id
                WHERE staging.pipeline_run_id = %s AND staging.validation_status = 'VALID'
                ON CONFLICT (source_product_id) DO UPDATE
                SET brand_code = EXCLUDED.brand_code,
                    style_code = EXCLUDED.style_code,
                    product_name = EXCLUDED.product_name,
                    season_code = EXCLUDED.season_code,
                    active = EXCLUDED.active,
                    source_updated_at = EXCLUDED.source_updated_at,
                    warehouse_updated_at = CURRENT_TIMESTAMP
                """,
                (pipeline_id,),
            )
            return cursor.rowcount

    @task(retries=2)
    def load_facts(pipeline_id: str) -> int:
        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO analytics.fact_order_items (
                    source_order_item_id, pipeline_run_id, product_key, source_system,
                    source_order_id, ordered_at, order_date, sku_code, quantity,
                    unit_price, gross_amount, currency
                )
                SELECT
                    staging.source_order_item_id, staging.pipeline_run_id,
                    product.product_key, staging.source_system, staging.source_order_id,
                    staging.ordered_at, (staging.ordered_at AT TIME ZONE 'Asia/Seoul')::date,
                    staging.sku_code, staging.quantity, staging.unit_price,
                    staging.quantity * staging.unit_price, staging.currency
                FROM analytics.stg_order_items staging
                JOIN external_order_items item ON item.id = staging.source_order_item_id
                JOIN skus sku ON sku.id = item.sku_id
                JOIN analytics.dim_products product ON product.source_product_id = sku.product_id
                WHERE staging.pipeline_run_id = %s AND staging.validation_status = 'VALID'
                ON CONFLICT (source_order_item_id) DO NOTHING
                """,
                (pipeline_id,),
            )
            loaded_count = cursor.rowcount
            cursor.execute(
                "UPDATE analytics.pipeline_runs SET loaded_count = %s WHERE id = %s",
                (loaded_count, pipeline_id),
            )
        return loaded_count

    @task(retries=2)
    def refresh_daily_mart(pipeline_id: str) -> int:
        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO analytics.mart_daily_product_sales (
                    sales_date, product_key, source_system, currency,
                    order_count, units_sold, gross_sales
                )
                SELECT
                    fact.order_date, fact.product_key, fact.source_system, fact.currency,
                    COUNT(DISTINCT fact.source_order_id), SUM(fact.quantity), SUM(fact.gross_amount)
                FROM analytics.fact_order_items fact
                WHERE fact.order_date IN (
                    SELECT DISTINCT (ordered_at AT TIME ZONE 'Asia/Seoul')::date
                    FROM analytics.stg_order_items
                    WHERE pipeline_run_id = %s AND validation_status = 'VALID'
                )
                GROUP BY fact.order_date, fact.product_key, fact.source_system, fact.currency
                ON CONFLICT (sales_date, product_key, source_system, currency) DO UPDATE
                SET order_count = EXCLUDED.order_count,
                    units_sold = EXCLUDED.units_sold,
                    gross_sales = EXCLUDED.gross_sales,
                    refreshed_at = CURRENT_TIMESTAMP
                """,
                (pipeline_id,),
            )
            return cursor.rowcount

    @task(retries=1)
    def resolve_reprocessed_rejections(pipeline_id: str) -> int:
        context = get_current_context()
        if not should_reprocess_rejected(context):
            return 0

        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                UPDATE analytics.rejected_order_items rejected
                SET resolution_status = 'RESOLVED',
                    resolved_at = CURRENT_TIMESTAMP,
                    resolved_by_pipeline_run_id = %s
                WHERE rejected.resolution_status = 'PENDING'
                  AND EXISTS (
                      SELECT 1 FROM analytics.stg_order_items staging
                      WHERE staging.pipeline_run_id = %s
                        AND staging.source_order_item_id = rejected.source_order_item_id
                        AND staging.validation_status = 'VALID'
                  )
                """,
                (pipeline_id, pipeline_id),
            )
            return cursor.rowcount

    @task
    def complete_pipeline(pipeline_id: str) -> None:
        with warehouse_connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                UPDATE analytics.pipeline_runs
                SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP
                WHERE id = %s
                """,
                (pipeline_id,),
            )

    run_id = start_pipeline()
    extracted = extract_to_staging(run_id)
    validated = validate_and_quarantine(run_id)
    dimensions = load_dimensions(run_id)
    facts = load_facts(run_id)
    resolved = resolve_reprocessed_rejections(run_id)
    mart = refresh_daily_mart(run_id)
    completed = complete_pipeline(run_id)

    extracted >> validated
    validated >> dimensions >> facts >> resolved >> mart >> completed


daily_sales_pipeline()
