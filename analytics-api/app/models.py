from datetime import date, datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SalesSummary(ApiModel):
    order_count: int
    units_sold: int
    gross_sales: Decimal


class DailySales(ApiModel):
    sales_date: date
    order_count: int
    units_sold: int
    gross_sales: Decimal


class ProductSales(ApiModel):
    product_key: int
    brand_code: str
    product_name: str
    order_count: int
    units_sold: int
    gross_sales: Decimal


class ChannelSales(ApiModel):
    source_system: str
    order_count: int
    units_sold: int
    gross_sales: Decimal


class PipelineStatus(ApiModel):
    status: str
    extracted_count: int
    rejected_count: int
    loaded_count: int
    completed_at: datetime | None


class WarehouseOverview(ApiModel):
    days: int
    summary: SalesSummary
    daily_sales: list[DailySales]
    top_products: list[ProductSales]
    channels: list[ChannelSales]
    pipeline: PipelineStatus | None
    refreshed_at: datetime | None
