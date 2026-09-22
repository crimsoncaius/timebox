import pytest
from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.testclient import TestClient

from app.api.deps import require_api_key
from app.core.config import Settings, get_settings
from app.main import cors_middleware_options

PREVIEW_ORIGIN_REGEX = r"^https://timebox-[a-z0-9-]+-caius-projects-fddd122e[.]vercel[.]app$"


def make_client(settings: Settings) -> TestClient:
    app = FastAPI()
    app.dependency_overrides[get_settings] = lambda: settings
    app.add_middleware(CORSMiddleware, **cors_middleware_options(settings))

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/protected", dependencies=[Depends(require_api_key)])
    def protected() -> dict[str, str]:
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


@pytest.mark.parametrize("origins", ["", "   ", " , , ", "*", " *, https://allowed.example ",
                                        " https://allowed.example, "])
@pytest.mark.parametrize("origin", ["https://allowed.example", "https://unrelated.example"])
def test_cors_configuration_matrix(origins: str, origin: str) -> None:
    client = make_client(Settings(cors_origins=origins, cors_origin_regex=None))
    allowed = "*" in origins or ("allowed.example" in origins and origin == "https://allowed.example")
    preflight = client.options("/health", headers={
        "Origin": origin,
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "X-API-Key,X-Timebox-Protocol",
    })
    assert preflight.status_code == (200 if allowed else 400)
    assert ("access-control-allow-origin" in preflight.headers) == allowed
    response = client.get("/health", headers={"Origin": origin})
    assert response.status_code == 200
    assert ("access-control-allow-origin" in response.headers) == allowed
    assert client.get("/health").status_code == 200


def test_absent_origins_preserve_localhost_defaults(monkeypatch) -> None:
    monkeypatch.delenv("CORS_ORIGINS", raising=False)
    client = make_client(Settings(_env_file=None, cors_origin_regex=None))
    for origin, status in [("http://localhost:5174", 200), ("http://127.0.0.1:5174", 200),
                           ("https://unrelated.example", 400)]:
        response = client.options("/health", headers={
            "Origin": origin, "Access-Control-Request-Method": "GET",
        })
        assert response.status_code == status


def test_empty_list_still_allows_regex_matches() -> None:
    client = make_client(Settings(cors_origins="", cors_origin_regex=PREVIEW_ORIGIN_REGEX))
    response = client.options("/health", headers={
        "Origin": "https://timebox-git-main-caius-projects-fddd122e.vercel.app",
        "Access-Control-Request-Method": "GET",
    })
    assert response.status_code == 200


@pytest.mark.parametrize("origins", ["", "*", "https://allowed.example"])
def test_cors_does_not_replace_api_key_authentication(origins: str) -> None:
    client = make_client(Settings(cors_origins=origins, cors_origin_regex=None, api_key="test-key"))
    headers = {"Origin": "https://allowed.example"}
    assert client.get("/protected", headers=headers).status_code == 401
    assert client.get("/protected", headers={**headers, "X-API-Key": "wrong"}).status_code == 403
    assert client.get("/protected", headers={**headers, "X-API-Key": "test-key"}).status_code == 200
