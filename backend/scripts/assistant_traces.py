"""Explicitly clear Assistant traces in the local Phoenix project."""
import argparse
import datetime as dt
from urllib.parse import urlparse

import httpx


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:12022")
    parser.add_argument("--clear", action="store_true", help="Delete all stored Assistant traces")
    args = parser.parse_args()
    if urlparse(args.url).hostname not in {"localhost", "127.0.0.1", "::1"}:
        parser.error("This helper only operates on local Phoenix.")
    if not args.clear:
        parser.error("Pass --clear to explicitly delete the timebox-assistant project's traces.")
    response = httpx.delete(
        f"{args.url.rstrip('/')}/v1/projects/timebox-assistant/traces",
        params={"start_time": "1970-01-01T00:00:00Z", "end_time": dt.datetime.now(dt.timezone.utc).isoformat()},
        timeout=30,
    )
    response.raise_for_status()
    print("Assistant traces cleared. Conversation state and Timebox records are unchanged.")


if __name__ == "__main__":
    main()
