import io
import json
from pathlib import Path
import runpy
import urllib.error
import urllib.request

import pytest


verify = runpy.run_path(str(Path(__file__).parents[2] / "scripts" / "verify-activity-release.py"))["verify"]


@pytest.mark.parametrize("enabled", [False, True])
def test_release_check_requires_activity_even_when_health_and_schema_pass(monkeypatch, enabled):
    paths = []

    def open_request(request, timeout):
        path = request.full_url.rsplit("/", 1)[-1]
        paths.append(path)
        if path == "activity" and not enabled:
            raise urllib.error.HTTPError(request.full_url, 404, "Not Found", {},
                                         io.BytesIO(b'{"detail":"Activity Tracking requires database upgrade"}'))
        body = {"health": {"status": "ok"}, "ready": {"status": "ready"},
                "activity": {"protocol": "activity-online-v1", "records": [],
                             "reporting_timezone": "Asia/Singapore"}}[path]
        return io.BytesIO(json.dumps(body).encode())

    monkeypatch.setattr(urllib.request, "urlopen", open_request)
    if enabled:
        verify("https://example.test")
    else:
        with pytest.raises(RuntimeError, match="requires database upgrade"):
            verify("https://example.test")
    assert paths == ["health", "ready", "activity"]
