"""History list endpoint coverage."""


from __future__ import annotations


def test_list_days_returns_rows_without_summary(client):
    client.get("/days/2026-05-01")
    r = client.get("/days?limit=5")
    assert r.status_code == 200
    rows = r.json()
    assert len(rows) >= 1
    row = next(x for x in rows if x["date"] == "2026-05-01")
    assert "date" in row
    assert "updated_at" in row
    assert "summary" not in row
