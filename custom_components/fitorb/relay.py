from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, date, datetime
from enum import StrEnum
from typing import Any


class RelayMetric(StrEnum):
    """Metrics accepted from the Android relay."""

    STEPS = "steps"
    CALORIES = "calories"
    DISTANCE = "distance"
    HEART_RATE = "heart_rate"
    SPO2 = "spo2"
    STRESS = "stress"
    SLEEP_STAGE = "sleep_stage"
    SLEEP_SUMMARY = "sleep_summary"
    BATTERY = "battery"
    CHARGING = "charging"


@dataclass(frozen=True, slots=True)
class RelaySample:
    """One idempotent Android relay sample."""

    sample_id: str
    ring_id: str
    metric: RelayMetric
    timestamp: datetime
    value: int | float | str | bool
    source: str
    captured_at: datetime
    unit: str | None = None
    local_date: date | None = None
    uploaded_at: datetime | None = None
    raw_hex: str | None = None
    protocol_version: int = 1


@dataclass(frozen=True, slots=True)
class RelayBatch:
    """One Android relay upload request."""

    relay_id: str
    ring_id: str
    app_version: str
    protocol_version: int
    sent_at: datetime
    samples: tuple[RelaySample, ...]


@dataclass(frozen=True, slots=True)
class RelayRejectedSample:
    """A sample rejected by Home Assistant with a stable reason."""

    sample_id: str
    reason: str


@dataclass(frozen=True, slots=True)
class RelayAckResult:
    """Per-sample upload acknowledgement."""

    accepted: tuple[str, ...]
    duplicates: tuple[str, ...]
    rejected: tuple[RelayRejectedSample, ...]
    server_time: datetime


def parse_relay_batch(payload: dict[str, object], *, max_samples: int) -> RelayBatch:
    """Parse and validate one relay upload payload."""
    relay_id = _required_str(payload, "relay_id")
    ring_id = _required_str(payload, "ring_id")
    app_version = _required_str(payload, "app_version")
    protocol_version = _required_positive_int(payload, "protocol_version")
    sent_at = _parse_datetime(_required_str(payload, "sent_at"))
    raw_samples = payload.get("samples")
    if not isinstance(raw_samples, list):
        raise ValueError("samples must be a list")
    if len(raw_samples) > max_samples:
        raise ValueError("too many samples")

    samples = tuple(
        _parse_sample(item, ring_id=ring_id, protocol_version=protocol_version)
        for item in raw_samples
    )
    return RelayBatch(
        relay_id=relay_id,
        ring_id=ring_id,
        app_version=app_version,
        protocol_version=protocol_version,
        sent_at=sent_at,
        samples=samples,
    )


def relay_ack_to_json(result: RelayAckResult) -> dict[str, object]:
    """Return Home Assistant JSON response body for an acknowledgement."""
    return {
        "accepted": list(result.accepted),
        "duplicates": list(result.duplicates),
        "rejected": [
            {"sample_id": item.sample_id, "reason": item.reason}
            for item in result.rejected
        ],
        "server_time": _format_datetime(result.server_time),
    }


def _parse_sample(
    value: object,
    *,
    ring_id: str,
    protocol_version: int,
) -> RelaySample:
    if not isinstance(value, dict):
        raise ValueError("sample must be an object")

    sample_ring_id = _required_str(value, "ring_id")
    if sample_ring_id != ring_id:
        raise ValueError("ring_id mismatch")

    metric_value = _required_str(value, "metric")
    try:
        metric = RelayMetric(metric_value)
    except ValueError as err:
        raise ValueError("invalid metric") from err

    sample_value = value.get("value")
    if not isinstance(sample_value, int | float | str | bool):
        raise ValueError("sample value has invalid type")

    sample_protocol_version = _optional_positive_int(
        value.get("protocol_version"),
        "protocol_version",
    )

    return RelaySample(
        sample_id=_required_str(value, "sample_id"),
        ring_id=sample_ring_id,
        metric=metric,
        timestamp=_parse_datetime(_required_str(value, "timestamp")),
        value=sample_value,
        source=_required_str(value, "source"),
        captured_at=_parse_datetime(_required_str(value, "captured_at")),
        unit=_optional_str(value.get("unit")),
        local_date=_optional_date(value.get("local_date")),
        uploaded_at=_optional_datetime(value.get("uploaded_at")),
        raw_hex=_optional_str(value.get("raw_hex")),
        protocol_version=sample_protocol_version
        if sample_protocol_version is not None
        else protocol_version,
    )


def _required_str(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{key} must be a non-empty string")
    return value


def _required_positive_int(payload: dict[str, Any], key: str) -> int:
    value = payload.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{key} must be a positive integer")
    return value


def _optional_str(value: object) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError("optional string field has invalid type")
    return value


def _optional_positive_int(value: object, key: str) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{key} must be a positive integer")
    return value


def _optional_date(value: object) -> date | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError("local_date must be a string")
    try:
        return date.fromisoformat(value)
    except ValueError as err:
        raise ValueError("local_date must be ISO formatted") from err


def _optional_datetime(value: object) -> datetime | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError("optional datetime field has invalid type")
    return _parse_datetime(value)


def _parse_datetime(value: str) -> datetime:
    normalized = value.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as err:
        raise ValueError("datetime must be ISO formatted") from err
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def _format_datetime(value: datetime) -> str:
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")
