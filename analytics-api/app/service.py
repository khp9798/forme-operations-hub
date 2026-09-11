from datetime import date, timedelta

from .models import WarehouseOverview
from .repository import AnalyticsRepository


class AnalyticsService:
    def __init__(self, repository: AnalyticsRepository):
        self.repository = repository

    def overview(self, days: int) -> WarehouseOverview:
        since = date.today() - timedelta(days=days - 1)
        return WarehouseOverview(
            days=days,
            summary=self.repository.summary(since),
            daily_sales=self.repository.daily_sales(since),
            top_products=self.repository.top_products(since),
            channels=self.repository.channels(since),
            pipeline=self.repository.latest_pipeline(),
            refreshed_at=self.repository.refreshed_at()["refreshed_at"],
        )

