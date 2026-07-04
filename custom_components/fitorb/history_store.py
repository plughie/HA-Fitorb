from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .const import DOMAIN
from .models import FitorbHistoryResult, FitorbHistorySample, FitorbSleepSummary
from .relay import (
    RelayAckResult,
    RelayBatch,
    RelayMetric,
    RelayRejectedSample,
    RelaySample,
)

_STORE_VERSION = 1
MAX_RELAY_STORED_SAMPLES = 10000


class FitorbHistoryStore:
    """Persist historical sync metadata and dedupe keys for one config entry."""

    def __init__(self, hass: HomeAssistant, entry_id: str) -> None:
        self._store: Store[dict[str, Any]] = Store(
            hass,
            _STORE_VERSION,
            f"{DOMAIN}_history_{entry_id}",
        )
        self._data: dict[str, Any] = {
            "last_sync": None,
            "last_sample_count": 0,
            "first_sample": None,
            "last_sample": None,
            "last_status": None,
            "unknown_packets": 0,
            "malformed_packets": 0,
            "sleep_summary": None,
            "samples": {},
            "relay": {
                "last_upload": None,
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": None,
                "backlog": None,
                "samples": {},
            },
        }

    @property
    def last_sync(self) -> datetime | None:
        """Return the last history sync timestamp."""
        return _parse_datetime(self._data.get("last_sync"))

    @property
    def last_sample_count(self) -> int:
        """Return total unique samples recorded in the ledger."""
        return int(self._data.get("last_sample_count") or 0)

    @property
    def first_sample(self) -> datetime | None:
        """Return the earliest unique historical sample timestamp."""
        return _parse_datetime(self._data.get("first_sample"))

    @property
    def last_sample(self) -> datetime | None:
        """Return the latest unique historical sample timestamp."""
        return _parse_datetime(self._data.get("last_sample"))

    @property
    def last_status(self) -> str | None:
        """Return the last history sync status."""
        value = self._data.get("last_status")
        return value if isinstance(value, str) else None

    @property
    def unknown_packets(self) -> int:
        """Return unknown packet count from the last history sync."""
        return _parse_int(self._data.get("unknown_packets"))

    @property
    def malformed_packets(self) -> int:
        """Return malformed packet count from the last history sync."""
        return _parse_int(self._data.get("malformed_packets"))

    @property
    def sleep_summary(self) -> FitorbSleepSummary | None:
        """Return the latest persisted sleep summary."""
        return _sleep_summary_from_json(self._data.get("sleep_summary"))

    @property
    def relay_last_upload(self) -> datetime | None:
        """Return the last relay upload timestamp."""
        return _parse_datetime(self._relay_data().get("last_upload"))

    @property
    def relay_last_sample(self) -> datetime | None:
        """Return the latest stored relay sample timestamp."""
        return _parse_datetime(self._relay_data().get("last_sample"))

    @property
    def relay_last_rejected_count(self) -> int:
        """Return rejected sample count from the last relay upload."""
        return _parse_int(self._relay_data().get("last_rejected_count"))

    @property
    def relay_app_version(self) -> str | None:
        """Return the last relay app version."""
        value = self._relay_data().get("app_version")
        return value if isinstance(value, str) else None

    @property
    def relay_backlog(self) -> int | None:
        """Return the last relay app backlog count."""
        return _parse_optional_nonnegative_int(self._relay_data().get("backlog"))

    async def async_load(self) -> None:
        """Load store data from disk."""
        loaded = await self._store.async_load()
        if loaded is not None:
            self._data.update(loaded)
        if not isinstance(self._data.get("samples"), dict):
            self._data["samples"] = {}
        self._relay_data()

    async def async_record_result(
        self,
        result: FitorbHistoryResult,
        synced_at: datetime,
    ) -> tuple[FitorbHistorySample, ...]:
        """Record unique samples from a sync result and persist metadata."""
        samples: dict[str, dict[str, Any]] = self._data.setdefault("samples", {})
        new_samples: list[FitorbHistorySample] = []

        for sample in result.samples:
            key = _sample_key(sample)
            if key in samples:
                continue
            samples[key] = _sample_to_json(sample)
            new_samples.append(sample)

        self._data["last_sync"] = synced_at.astimezone(UTC).isoformat()
        self._data["last_sample_count"] = len(samples)
        self._data["last_status"] = result.status
        self._data["unknown_packets"] = result.unknown_packets
        self._data["malformed_packets"] = result.malformed_packets
        if result.sleep_summary is not None:
            self._data["sleep_summary"] = _sleep_summary_to_json(
                result.sleep_summary
            )

        timestamps = [
            _parse_datetime(item.get("timestamp"))
            for item in samples.values()
            if isinstance(item, dict)
        ]
        valid_timestamps = [stamp for stamp in timestamps if stamp is not None]
        self._data["first_sample"] = (
            min(valid_timestamps).isoformat() if valid_timestamps else None
        )
        self._data["last_sample"] = (
            max(valid_timestamps).isoformat() if valid_timestamps else None
        )

        await self._store.async_save(self._data)
        return tuple(new_samples)

    async def async_record_relay_batch(
        self,
        batch: RelayBatch,
        received_at: datetime,
    ) -> RelayAckResult:
        """Record unique relay samples and persist relay metadata."""
        relay = self._relay_data()
        samples: dict[str, object] = relay["samples"]
        accepted: list[str] = []
        duplicates: list[str] = []
        rejected: list[RelayRejectedSample] = []
        received_at_utc = received_at.astimezone(UTC)

        for sample in batch.samples:
            if sample.ring_id != batch.ring_id:
                rejected.append(
                    RelayRejectedSample(sample.sample_id, "ring_id_mismatch")
                )
                continue
            if sample.sample_id in samples:
                duplicates.append(sample.sample_id)
                continue
            samples[sample.sample_id] = _relay_sample_to_json(sample)
            accepted.append(sample.sample_id)

        _prune_relay_samples(samples)
        relay["last_upload"] = received_at_utc.isoformat()
        relay["last_rejected_count"] = len(rejected)
        relay["app_version"] = batch.app_version
        relay["backlog"] = batch.backlog
        latest_sample = _latest_relay_timestamp(samples)
        if latest_sample is not None:
            relay["last_sample"] = latest_sample

        await self._store.async_save(self._data)
        return RelayAckResult(
            accepted=tuple(accepted),
            duplicates=tuple(duplicates),
            rejected=tuple(rejected),
            server_time=received_at_utc,
        )

    def _relay_data(self) -> dict[str, Any]:
        """Return normalized relay store data."""
        relay = self._data.get("relay")
        if not isinstance(relay, dict):
            relay = {}
            self._data["relay"] = relay

        samples = relay.get("samples")
        if not isinstance(samples, dict):
            samples = {}
        else:
            samples = {
                key: item
                for key, item in samples.items()
                if isinstance(key, str)
            }
        _prune_relay_samples(samples)
        relay["samples"] = samples

        last_upload = _parse_datetime(relay.get("last_upload"))
        relay["last_upload"] = (
            last_upload.isoformat() if last_upload is not None else None
        )

        latest_sample = _latest_relay_timestamp(samples)
        last_sample = _parse_datetime(relay.get("last_sample"))
        relay["last_sample"] = (
            latest_sample
            if latest_sample is not None
            else last_sample.isoformat()
            if last_sample is not None
            else None
        )
        relay["last_rejected_count"] = _parse_int(relay.get("last_rejected_count"))

        app_version = relay.get("app_version")
        relay["app_version"] = app_version if isinstance(app_version, str) else None
        relay["backlog"] = _parse_optional_nonnegative_int(relay.get("backlog"))
        return relay


def _sample_key(sample: FitorbHistorySample) -> str:
    return "|".join(
        [
            sample.metric.value,
            sample.timestamp.astimezone(UTC).isoformat(),
            str(sample.value),
        ]
    )


def _sample_to_json(sample: FitorbHistorySample) -> dict[str, Any]:
    return {
        "metric": sample.metric.value,
        "timestamp": sample.timestamp.astimezone(UTC).isoformat(),
        "value": sample.value,
        "source_day": sample.source_day.isoformat(),
        "raw_hex": sample.raw_hex,
    }


def _relay_sample_to_json(sample: RelaySample) -> dict[str, Any]:
    return {
        "sample_id": sample.sample_id,
        "ring_id": sample.ring_id,
        "metric": sample.metric.value,
        "timestamp": sample.timestamp.astimezone(UTC).isoformat(),
        "value": sample.value,
        "unit": sample.unit,
        "source": sample.source,
        "captured_at": sample.captured_at.astimezone(UTC).isoformat(),
        "local_date": sample.local_date.isoformat()
        if sample.local_date is not None
        else None,
        "uploaded_at": sample.uploaded_at.astimezone(UTC).isoformat()
        if sample.uploaded_at is not None
        else None,
        "raw_hex": sample.raw_hex,
        "protocol_version": sample.protocol_version,
    }


def _latest_relay_timestamp(samples: dict[str, object]) -> str | None:
    timestamps: list[datetime] = []
    for item in samples.values():
        if not _is_valid_relay_sample_json(item):
            continue
        timestamp = _parse_datetime(item.get("timestamp"))
        if timestamp is not None:
            timestamps.append(timestamp)
    return max(timestamps).isoformat() if timestamps else None


def _prune_relay_samples(samples: dict[str, object]) -> None:
    overflow = len(samples) - MAX_RELAY_STORED_SAMPLES
    if overflow <= 0:
        return

    for key, _item in sorted(
        samples.items(),
        key=lambda item: _relay_sample_retention_key(item[0], item[1]),
    )[:overflow]:
        samples.pop(key, None)


def _relay_sample_retention_key(
    sample_id: str,
    value: object,
) -> tuple[int, datetime, str]:
    timestamp = None
    if isinstance(value, dict):
        timestamp = _parse_datetime(value.get("timestamp"))
    retention_timestamp = timestamp or datetime.min.replace(tzinfo=UTC)
    if not _is_valid_relay_sample_json(value):
        return (0, retention_timestamp, sample_id)
    return (1, retention_timestamp, sample_id)


def _is_valid_relay_sample_json(value: object) -> bool:
    if not isinstance(value, dict):
        return False

    sample_id = value.get("sample_id")
    ring_id = value.get("ring_id")
    metric = value.get("metric")
    sample_value = value.get("value")
    source = value.get("source")
    protocol_version = value.get("protocol_version")

    if not isinstance(sample_id, str) or not sample_id:
        return False
    if not isinstance(ring_id, str) or not ring_id:
        return False
    if not isinstance(metric, str):
        return False
    try:
        RelayMetric(metric)
    except ValueError:
        return False
    if _parse_datetime(value.get("timestamp")) is None:
        return False
    if not isinstance(sample_value, int | float | str | bool):
        return False
    if not isinstance(source, str) or not source:
        return False
    if _parse_datetime(value.get("captured_at")) is None:
        return False
    return (
        not isinstance(protocol_version, bool)
        and isinstance(protocol_version, int)
        and protocol_version > 0
    )


def _sleep_summary_to_json(summary: FitorbSleepSummary) -> dict[str, Any]:
    return {
        "source_day": summary.source_day.isoformat(),
        "start": summary.start.astimezone(UTC).isoformat(),
        "end": summary.end.astimezone(UTC).isoformat(),
        "duration_minutes": summary.duration_minutes,
        "asleep_minutes": summary.asleep_minutes,
        "awake_minutes": summary.awake_minutes,
        "light_minutes": summary.light_minutes,
        "deep_minutes": summary.deep_minutes,
        "rem_minutes": summary.rem_minutes,
    }


def _sleep_summary_from_json(value: object) -> FitorbSleepSummary | None:
    if not isinstance(value, dict):
        return None
    start = _parse_datetime(value.get("start"))
    end = _parse_datetime(value.get("end"))
    source_day_value = value.get("source_day")
    if start is None or end is None or not isinstance(source_day_value, str):
        return None
    try:
        source_day = datetime.fromisoformat(source_day_value).date()
    except ValueError:
        return None
    return FitorbSleepSummary(
        source_day=source_day,
        start=start,
        end=end,
        duration_minutes=_parse_int(value.get("duration_minutes")),
        asleep_minutes=_parse_int(value.get("asleep_minutes")),
        awake_minutes=_parse_int(value.get("awake_minutes")),
        light_minutes=_parse_int(value.get("light_minutes")),
        deep_minutes=_parse_int(value.get("deep_minutes")),
        rem_minutes=_parse_int(value.get("rem_minutes")),
    )


def _parse_datetime(value: object) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def _parse_int(value: object) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def _parse_optional_nonnegative_int(value: object) -> int | None:
    if isinstance(value, bool):
        return None
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return parsed if parsed >= 0 else None
