import pytest


@pytest.mark.parametrize("duration", [1, 2, 5, 29])
def test_short_planned_blocks_round_trip_and_resize(client, duration):
    day = "2026-09-13"
    response = client.post(f"/days/{day}/blocks", json={
        "lane": "planned", "name": "Short block", "start_minute": 600,
        "end_minute": 600 + duration,
    })
    assert response.status_code == 200, response.text
    block_id = response.json()["time_blocks"][0]["id"]
    changed = client.patch(f"/days/{day}/blocks/{block_id}", json={"end_minute": 601})
    assert changed.status_code == 200, changed.text
    saved = client.get(f"/days/{day}").json()["time_blocks"][0]
    assert (saved["start_minute"], saved["end_minute"]) == (600, 601)
    invalid = client.patch(f"/days/{day}/blocks/{block_id}", json={"end_minute": 600})
    assert invalid.status_code == 422
