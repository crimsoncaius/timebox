import datetime as dt
from uuid import uuid4

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.activity import ActivityOperation, ActivityState
from app.models.assistant import QuickTrackingDiagnostic, QuickTrackingFailure
from app.services import quick_tracking
from app.services import quick_tracking_failures as failures
from tests.test_quick_tracking import execute, interpreted


def report(**overrides):
    return {"id": str(uuid4()), "device_id": str(uuid4()), "revision": 1,
        "occurred_at": dt.datetime.now(dt.UTC).isoformat(), "command": "Started work ten minutes ago",
        "status": "failed", "reason": "Cannot reach Assistant. Check your connection.",
        "stage": "interpretation", "retryable": True, "request_ids": [], "history": [], **overrides}


def test_upload_is_idempotent_revisioned_and_dismissal_survives_late_upload(client):
    body = report()
    assert client.post("/assistant/quick-failures", json=body).status_code == 200
    assert client.post("/assistant/quick-failures", json=body).status_code == 200
    newer = {**body, "revision": 2, "reason": "Second failure"}
    client.post("/assistant/quick-failures", json=newer)
    client.post("/assistant/quick-failures", json=body)
    assert client.get("/assistant/quick-failures").json()["items"][0]["reason"] == "Second failure"
    client.post(f"/assistant/quick-failures/{body['id']}/dismiss")
    client.post("/assistant/quick-failures", json={**newer, "revision": 3})
    assert client.get("/assistant/quick-failures").json()["items"] == []
    assert client.get("/assistant/quick-failures?history=true").json()["items"][0]["dismissed"]
    assert client.post("/assistant/quick-failures", json={**body, "device_id": str(uuid4())}).status_code == 409
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(QuickTrackingFailure)) == 1
        assert db.scalar(select(func.count()).select_from(ActivityState)) == 0


def test_unknown_outcome_is_overridden_by_actual_receipt_without_reexecution(client, monkeypatch):
    result = interpreted(monkeypatch)
    body = report(status="uncertain", retryable=False, request_ids=[result["request_id"]])
    client.post("/assistant/quick-failures", json=body)
    execute(result, "start", 1)
    assert client.get("/assistant/quick-failures").json()["items"] == []
    row = client.get("/assistant/quick-failures?history=true").json()["items"][0]
    assert row["status"] == "recorded" and row["review_id"] and not row["retryable"]
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(ActivityOperation)) == 1


def test_no_transcript_is_retained_without_inventing_command(client):
    body = report(command=None, stage="capture", retryable=False, reason="No speech heard.")
    assert client.post("/assistant/quick-failures", json=body).status_code == 200
    row = client.get("/assistant/quick-failures").json()["items"][0]
    assert row["command"] is None and not row["retryable"]


def test_diagnostics_expire_without_deleting_history_and_duplicate_upload_cannot_extend_retention(client):
    body = report()
    client.post("/assistant/quick-failures", json=body)
    event = {"id": str(uuid4()), "occurred_at": (dt.datetime.now(dt.UTC) - dt.timedelta(days=29)).isoformat(),
        "stage": "capture", "exception_type": "SpeechCaptureException", "error_code": 7, "frames": ["QuickTrackingSpeech.onError:102"]}
    path = f"/assistant/quick-failures/{body['id']}/diagnostics"
    assert client.post(path, json=event).status_code == 200
    client.post(path, json={**event, "occurred_at": dt.datetime.now(dt.UTC).isoformat()})
    with Session(get_engine()) as db:
        row = db.get(QuickTrackingDiagnostic, event["id"])
        assert row.expires_at.replace(tzinfo=dt.UTC) < dt.datetime.now(dt.UTC) + dt.timedelta(days=2)
        row.expires_at = dt.datetime.now(dt.UTC) - dt.timedelta(seconds=1)
        db.commit()
    failures.cleanup()
    assert client.get("/assistant/quick-failures").json()["items"][0]["id"] == body["id"]
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(QuickTrackingDiagnostic)) == 0
    assert client.post(path, json={**event, "headers": {"Authorization": "secret"}}).status_code == 422
    assert client.post(path, json={**event, "id": str(uuid4()), "occurred_at": (dt.datetime.now(dt.UTC) - dt.timedelta(days=31)).isoformat()}).status_code == 200
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(QuickTrackingDiagnostic)) == 0


def test_retry_preserves_original_reference_and_rejects_expired_command(client, monkeypatch):
    original = dt.datetime.now(dt.UTC) - dt.timedelta(minutes=3)
    observed = []
    async def workflow(state, message, now):
        observed.append(now)
        return quick_tracking.language.Decision(action=None), {}, {"choice": "no_change"}
    monkeypatch.setattr(quick_tracking.language, "interpret_workflow", workflow)
    body = {"request_id": str(uuid4()), "message": "Started ten minutes ago", "source": "app", "reference_at": original.isoformat()}
    response = client.post("/assistant/quick-tracking", json=body)
    assert response.status_code == 200
    assert observed == [original]
    assert client.post("/assistant/quick-tracking", json=body).json() == response.json()
    assert observed == [original]
    assert client.post("/assistant/quick-tracking", json={**body, "request_id": str(uuid4()), "reference_at": (original - dt.timedelta(minutes=20)).isoformat()}).status_code == 409


def test_backend_exception_diagnostics_do_not_capture_secret_message(client, monkeypatch):
    async def broken(body):
        raise ValueError("Authorization: Bearer VERY_SECRET transcript unrelated")
    monkeypatch.setattr(quick_tracking, "interpret", broken)
    from fastapi.testclient import TestClient

    from app.main import app
    response = TestClient(app, raise_server_exceptions=False).post("/assistant/quick-tracking", json={
        "request_id": str(uuid4()), "message": "work", "source": "app"})
    assert response.status_code == 500
    with Session(get_engine()) as db:
        data = db.scalar(select(QuickTrackingDiagnostic)).data
        assert data["exception_type"] == "ValueError"
        assert data["frames"]
        assert "VERY_SECRET" not in str(data) and "unrelated" not in str(data)
