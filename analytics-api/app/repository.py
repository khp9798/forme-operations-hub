from datetime import date
from typing import Any

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool


class AnalyticsRepository:
    def __init__(self, database_url: str):
        self.pool = ConnectionPool(database_url, min_size=1, max_size=5, open=False)

    def open(self) -> None:
        self.pool.open()

    def close(self) -> None:
        self.pool.close()

    def health(self) -> bool:
        with self.pool.connection() as connection, connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            return cursor.fetchone()[0] == 1

    def summary(self, since: date) -> dict[str, Any]:
        return self._one("""
            SELECT COALESCE(SUM(order_count), 0)::int AS order_count,
                   COALESCE(SUM(units_sold), 0)::int AS units_sold,
                   COALESCE(SUM(gross_sales), 0) AS gross_sales
            FROM analytics.mart_daily_product_sales WHERE sales_date >= %s
        """, (since,))

    def daily_sales(self, since: date) -> list[dict[str, Any]]:
        return self._all("""
            SELECT sales_date, SUM(order_count)::int AS order_count,
                   SUM(units_sold)::int AS units_sold, SUM(gross_sales) AS gross_sales
            FROM analytics.mart_daily_product_sales WHERE sales_date >= %s
            GROUP BY sales_date ORDER BY sales_date
        """, (since,))

    def top_products(self, since: date) -> list[dict[str, Any]]:
        return self._all("""
            SELECT mart.product_key, product.brand_code, product.product_name,
                   SUM(mart.order_count)::int AS order_count,
                   SUM(mart.units_sold)::int AS units_sold,
                   SUM(mart.gross_sales) AS gross_sales
            FROM analytics.mart_daily_product_sales mart
            JOIN analytics.dim_products product ON product.product_key = mart.product_key
            WHERE mart.sales_date >= %s
            GROUP BY mart.product_key, product.brand_code, product.product_name
            ORDER BY gross_sales DESC, units_sold DESC LIMIT 10
        """, (since,))

    def channels(self, since: date) -> list[dict[str, Any]]:
        return self._all("""
            SELECT source_system, SUM(order_count)::int AS order_count,
                   SUM(units_sold)::int AS units_sold, SUM(gross_sales) AS gross_sales
            FROM analytics.mart_daily_product_sales WHERE sales_date >= %s
            GROUP BY source_system ORDER BY gross_sales DESC
        """, (since,))

    def latest_pipeline(self) -> dict[str, Any] | None:
        rows = self._all("""
            SELECT status, extracted_count, rejected_count, loaded_count, completed_at
            FROM analytics.pipeline_runs ORDER BY started_at DESC LIMIT 1
        """)
        return rows[0] if rows else None

    def refreshed_at(self) -> dict[str, Any]:
        return self._one("SELECT MAX(refreshed_at) AS refreshed_at FROM analytics.mart_daily_product_sales")

    def _one(self, query: str, params: tuple[Any, ...] = ()) -> dict[str, Any]:
        rows = self._all(query, params)
        return rows[0]

    def _all(self, query: str, params: tuple[Any, ...] = ()) -> list[dict[str, Any]]:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(query, params)
            return list(cursor.fetchall())

