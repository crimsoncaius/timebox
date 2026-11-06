"""Resolve shared calendar settings at domain boundaries, including non-HTTP callers."""

from app.models.activity import ActivityState


def reporting_settings(db, settings):
    state = db.get(ActivityState, 1)
    if state and state.reporting_timezone:
        return settings.model_copy(update={"app_timezone": state.reporting_timezone})
    return settings
