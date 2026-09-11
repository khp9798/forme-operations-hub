from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "postgresql://forme:forme_local_password@localhost:5433/forme_ops"
    model_config = SettingsConfigDict(env_prefix="ANALYTICS_")


settings = Settings()

