from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query

from .config import settings
from .models import WarehouseOverview
from .repository import AnalyticsRepository
from .service import AnalyticsService

repository = AnalyticsRepository(settings.database_url)
service = AnalyticsService(repository)


@asynccontextmanager
async def lifespan(_: FastAPI):
    repository.open()
    yield
    repository.close()


app = FastAPI(title="FORME Analytics API", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    try:
        return {"status": "UP" if repository.health() else "DOWN"}
    except Exception as exception:
        raise HTTPException(status_code=503, detail="analytics database unavailable") from exception


@app.get("/api/v1/sales/overview", response_model=WarehouseOverview)
def sales_overview(days: int = Query(default=30, ge=1, le=365)) -> WarehouseOverview:
    return service.overview(days)

