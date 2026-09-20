from __future__ import annotations

from fastapi import HTTPException

#: Service-layer ValueError messages that mean "this resource does not exist".
#: Everything else a service raises is a rule violation, which is 422.
NOT_FOUND_MESSAGES = frozenset(
    {
        "Actual Block not found",
        "Block not found",
        "Planned Block not found",
        "Project not found",
        "Task not found",
        "Task type not found",
    }
)


def domain_http_error(exc: ValueError) -> HTTPException:
    """Translate one service-layer ValueError into its HTTP response.

    The service layer speaks in ValueError so it stays framework-free; this is
    the single place that decides the status code, so every router answers a
    missing resource the same way.
    """

    message = str(exc)
    status = 404 if message in NOT_FOUND_MESSAGES else 422
    return HTTPException(status_code=status, detail=message)
