from typing import Literal
from uuid import UUID

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field

from app.schemas.time_block import ActualBlockRead


class Calibration(BaseModel):
    model_config = ConfigDict(extra="forbid")
    server_at: AwareDatetime
    offset_ms: float = Field(allow_inf_nan=False)


class EffectiveTime(BaseModel):
    model_config = ConfigDict(extra="forbid")
    # Later reconciliation adds explicit ranges without conflating them with action_at.
    mode: Literal["server_now"]


class ActivityCommand(BaseModel):
    model_config = ConfigDict(extra="forbid")
    operation_id: UUID
    device_id: str = Field(min_length=1, max_length=100)
    sequence: int = Field(gt=0, le=2147483647)
    action_at: AwareDatetime
    calibration: Calibration
    base_cursor: int = Field(ge=0)
    effective: EffectiveTime
    target_id: int | None = None
    kind: Literal["start", "switch", "stop"]
    task_type_id: int | None = None
    name: str | None = Field(default=None, max_length=500)


class ActivityAcknowledgement(BaseModel):
    operation_id: str
    outcome: str
    effective_at: AwareDatetime | None


class ActivitySnapshot(BaseModel):
    protocol: Literal["activity-online-v1"] = "activity-online-v1"
    cursor: int
    server_at: AwareDatetime
    reporting_timezone: str
    full_snapshot: bool = True
    current: ActualBlockRead | None
    records: list[ActualBlockRead]
    tombstones: list[int] = []
    acknowledgement: ActivityAcknowledgement | None = None
