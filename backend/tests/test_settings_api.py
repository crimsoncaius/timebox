from __future__ import annotations


def test_get_settings_returns_defaults(client):
    r = client.get("/settings")
    assert r.status_code == 200
    data = r.json()
    assert data["id"] == 1
    assert data["start_hour"] == 8
    assert data["end_hour"] == 20
    assert data["show_full_day"] is False
    assert "created_at" in data
    assert "updated_at" in data


def test_patch_settings_updates_existing_days(client):
    client.get("/days/2026-04-13")
    client.get("/days/2026-04-14")

    r = client.patch(
        "/settings",
        json={"start_hour": 9, "end_hour": 18, "show_full_day": True},
    )
    assert r.status_code == 200
    assert r.json()["start_hour"] == 9
    assert r.json()["end_hour"] == 18
    assert r.json()["show_full_day"] is True

    d1 = client.get("/days/2026-04-13").json()
    d2 = client.get("/days/2026-04-14").json()
    assert d1["start_hour"] == 9 and d2["start_hour"] == 9
    assert d1["end_hour"] == 18 and d2["end_hour"] == 18
    assert d1["show_full_day"] is True and d2["show_full_day"] is True


def test_new_day_inherits_settings_after_patch(client):
    r = client.patch(
        "/settings",
        json={"start_hour": 10, "end_hour": 22, "show_full_day": False},
    )
    assert r.status_code == 200

    d = client.get("/days/2030-01-01").json()
    assert d["date"] == "2030-01-01"
    assert d["start_hour"] == 10
    assert d["end_hour"] == 22
    assert d["show_full_day"] is False


def test_patch_settings_invalid_window(client):
    r = client.patch("/settings", json={"start_hour": 12, "end_hour": 10})
    assert r.status_code == 422
