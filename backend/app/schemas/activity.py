from typing import Literal
from uuid import UUID

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field

from app.schemas.time_block import ActualBlockRead
from app.schemas.task_type import TaskTypeRead


class Calibration(BaseModel):
    model_config = ConfigDict(extra="forbid")
    server_at: AwareDatetime
    offset_ms: float = Field(allow_inf_nan=False)


class EffectiveTime(BaseModel):
    model_config = ConfigDict(extra="forbid")
    # Later reconciliation adds explicit ranges without conflating them with action_at.
    mode: Literal["server_now", "instant", "range"]
    at: AwareDatetime | None = None
    end: AwareDatetime | None = None


class ActivityCommand(BaseModel):
    model_config = ConfigDict(extra="forbid")
    operation_id: UUID
    device_id: str = Field(min_length=1, max_length=100, pattern=r"^[\x20-\x7E]+$")
    sequence: int = Field(gt=0, le=2147483647)
    action_at: AwareDatetime
    calibration: Calibration
    base_cursor: int = Field(ge=0)
    effective: EffectiveTime
    target_id: int | None = None
    predecessor_id: UUID | None = None
    clear_fields: list[Literal["name", "note"]] = []
    target_source: str | None = None
    target_start_at: AwareDatetime | None = None
    kind: Literal["start", "switch", "stop", "describe", "add", "edit", "delete"]
    selection_snapshot: bool = False
    task_type_id: int | None = None
    name: str | None = Field(default=None, max_length=500)
    note: str | None = None
    task_id: int | None = None
    planned_block_id: int | None = None


class ActivityAcknowledgement(BaseModel):
    operation_id: str
    outcome: str
    effective_at: AwareDatetime | None


class ActivitySnapshot(BaseModel):
    protocol: Literal["activity-online-v1"] = "activity-online-v1"
    offline_ready: bool = True
    cursor: int
    server_at: AwareDatetime
    reporting_timezone_initialized: bool = False
    reporting_timezone: str
    full_snapshot: bool = True
    current: ActualBlockRead | None
    records: list[ActualBlockRead]
    task_types: list[TaskTypeRead] = []
    plans: list[dict] = []
    tombstones: list[int] = []
    provenance: dict[int, str] = {}
    operation_outcomes: dict[str, dict[str, str]] = {}
    coverage: list[dict] = []
    acknowledgement: ActivityAcknowledgement | None = None
