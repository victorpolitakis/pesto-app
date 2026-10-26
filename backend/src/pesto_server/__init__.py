def main() -> None:
    import uvicorn

    from .app import create_app
    from .config import Settings

    settings = Settings()
    if not settings.pesto_app_token:
        raise SystemExit("PESTO_APP_TOKEN is missing from backend/.env")
    uvicorn.run(create_app(settings), host=settings.host, port=settings.port)
