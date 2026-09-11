"""Operator entrypoint for the local restored-copy rehearsal (never startup)."""
import argparse
import json

from sqlalchemy.orm import Session

import app.models  # noqa: F401
from app.core.config import get_settings
from app.db.session import get_engine
from app.db.activity_admission import admission
from app.services import activity_cutover


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["preflight", "apply", "rollback", "pause", "resume"])
    parser.add_argument("--restored-database", required=True, help="Exact name of the restored rehearsal database")
    args = parser.parse_args()
    engine = get_engine()
    settings = get_settings()
    if (engine.dialect.name != "postgresql" or engine.url.host not in {"127.0.0.1", "localhost"}
            or engine.url.database != args.restored_database or not settings.activity_tracking_dev):
        parser.error("Requires the named local restored PostgreSQL database and ACTIVITY_TRACKING_DEV=true")
    if args.action == "preflight":
        with admission(engine, exclusive=True) as connection, Session(connection) as db:
            archive, _ = activity_cutover.preflight(db, settings.app_timezone)
            print(json.dumps({"records": len(archive), "ids": [row["id"] for row in archive], "timezone": settings.app_timezone}))
    elif args.action == "apply":
        archive = activity_cutover.apply(engine, settings.app_timezone)
        print(json.dumps({"records": len(archive["source"]), "timezone": archive["timezone"], "paused": archive["paused"]}))
    elif args.action == "rollback":
        activity_cutover.rollback(engine)
        print("Pre-write rollback complete")
    else:
        activity_cutover.pause(engine, args.action == "pause")
        print(f"Recovery writes {args.action} complete; legacy protocol remains closed")


if __name__ == "__main__":
    main()
