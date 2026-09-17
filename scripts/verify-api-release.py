"""Read-only release gate. API_KEY is optional and never printed."""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def verify(base_url):
    headers = {"X-Timebox-Protocol": "activity-online-v1"}
    if os.environ.get("API_KEY"):
        headers["X-API-Key"] = os.environ["API_KEY"]
    for path in ("/health", "/ready", "/activity"):
        request = urllib.request.Request(base_url.rstrip("/") + path, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                body = json.load(response)
        except urllib.error.HTTPError as error:
            try:
                detail = json.load(error).get("detail", "Request rejected")
            except (ValueError, AttributeError):
                detail = "Request rejected"
            raise RuntimeError(f"{path}: HTTP {error.code}: {detail}") from None
        if path == "/health" and body.get("status") != "ok":
            raise RuntimeError("Health check failed")
        if path == "/ready" and body.get("status") != "ready":
            raise RuntimeError("Database readiness check failed")
        if path == "/activity" and (body.get("protocol") != "activity-online-v1"
                                    or not body.get("reporting_timezone")
                                    or not isinstance(body.get("records"), list)):
            raise RuntimeError("Activity snapshot is incompatible with this release")
        print(f"{path}: ready")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    args = parser.parse_args()
    try:
        verify(args.base_url)
    except (RuntimeError, urllib.error.URLError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
