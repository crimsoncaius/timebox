from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import cors_middleware_options


PREVIEW_ORIGIN_REGEX = r"^https://timebox-[a-z0-9-]+-caius-projects-fddd122e[.]vercel[.]app$"


def make_client(settings: Settings) -> TestClient:
    app = FastAPI()
    app.add_middleware(CORSMiddleware, **cors_middleware_options(settings))

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return TestClient(app)


def test_cors_allows_exact_origin() -> None:
    client = make_client(
        Settings(
            cors_origins="https://timebox-umber.vercel.app",
            cors_origin_regex=None,
        )
    )

    response = client.options(
        "/health",
        headers={
            "Origin": "https://timebox-umber.vercel.app",
            "Access-Control-Request-Method": "GET",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "https://timebox-umber.vercel.app"


def test_cors_allows_matching_preview_origin() -> None:
    client = make_client(
        Settings(
            cors_origins="https://timebox-umber.vercel.app",
            cors_origin_regex=PREVIEW_ORIGIN_REGEX,
        )
    )

    response = client.options(
        "/health",
        headers={
            "Origin": "https://timebox-git-main-caius-projects-fddd122e.vercel.app",
            "Access-Control-Request-Method": "GET",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "https://timebox-git-main-caius-projects-fddd122e.vercel.app"


def test_cors_rejects_unrelated_origin() -> None:
    client = make_client(
        Settings(
            cors_origins="https://timebox-umber.vercel.app",
            cors_origin_regex=PREVIEW_ORIGIN_REGEX,
        )
    )

    response = client.options(
        "/health",
        headers={
            "Origin": "https://not-timebox.example.com",
            "Access-Control-Request-Method": "GET",
        },
    )

    assert response.status_code == 400
