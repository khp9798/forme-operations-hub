from datetime import date
from decimal import Decimal

from app.service import AnalyticsService
from app import main
from fastapi.testclient import TestClient


class FakeRepository:
    def summary(self, since: date):
        return {"order_count": 2, "units_sold": 3, "gross_sales": Decimal("354000")}
    def daily_sales(self, since: date): return []
    def top_products(self, since: date): return []
    def channels(self, since: date): return []
    def latest_pipeline(self): return None
    def refreshed_at(self): return {"refreshed_at": None}


def test_overview_combines_warehouse_results():
    result = AnalyticsService(FakeRepository()).overview(30)
    assert result.days == 30
    assert result.summary.order_count == 2
    assert result.summary.gross_sales == Decimal("354000")


def test_overview_endpoint_validates_days(monkeypatch):
    monkeypatch.setattr(main, "service", AnalyticsService(FakeRepository()))
    client = TestClient(main.app)

    response = client.get("/api/v1/sales/overview?days=30")
    assert response.status_code == 200
    assert response.json()["summary"]["grossSales"] == "354000"
    assert client.get("/api/v1/sales/overview?days=0").status_code == 422
