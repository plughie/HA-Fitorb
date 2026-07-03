# Fitorb Android Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android-only Fitorb Relay path that reads the ring on a configurable, battery-conscious schedule and uploads timestamped batches to the existing Home Assistant integration over HTTPS.

**Architecture:** First define a shared relay contract in the Home Assistant integration, then add authenticated ingest and diagnostics, then build the Android app around the same DTOs, local queue, uploader, scheduler, and BLE collector. Home Assistant remains the source of truth; Android is a local-first collector with acknowledgement-based delivery.

**Tech Stack:** Python 3.12, Home Assistant custom integration APIs, Home Assistant `Store`, pytest, pytest-homeassistant-custom-component, Ruff, Android Kotlin, Gradle, Jetpack Compose, Room, WorkManager, OkHttp, kotlinx.serialization, Android BLE APIs.

## Global Constraints

- Domain remains `fitorb`.
- Android-only solution.
- Direct HTTPS access to Home Assistant is the mobile upload path.
- The relay is not a full mobile health dashboard.
- Default ring sync interval is 10 minutes.
- Ring sync interval must be configurable.
- The relay must never use a permanent BLE connection in normal operation.
- The app must scan briefly before connecting and skip the cycle when the ring is not visible.
- Failed uploads must not trigger additional BLE reads.
- Home Assistant must use relay-specific tokens, not normal Home Assistant long-lived access tokens.
- Relay tokens are scoped only to Fitorb relay ingest and can be revoked per Android device.
- Version 1 stores relay samples in the Fitorb history store or a compatible extension.
- Do not write old Home Assistant recorder state rows directly.
- Long-term statistics publishing is a follow-up after sample semantics, units, and timestamp behavior have been validated against real hardware.
- Direct Home Assistant BLE remains available as fallback and debugging path.
- Mobile relay mode should avoid aggressive Home Assistant BLE polling that competes with the Android app.

---

## Source Notes

- Existing direct BLE implementation lives in `custom_components/fitorb/bluetooth.py`.
- Existing protocol helpers live in `custom_components/fitorb/protocol.py` and `custom_components/fitorb/history_protocol.py`.
- Existing persisted history metadata lives in `custom_components/fitorb/history_store.py`.
- Existing sensors and binary sensors follow description dictionaries in `custom_components/fitorb/sensor.py` and `custom_components/fitorb/binary_sensor.py`.
- Android background BLE work should use a foreground service for active BLE and scheduled work for periodic/retry orchestration.
- Home Assistant endpoint registration should use a custom HTTP view registered during integration setup, with explicit unload cleanup where Home Assistant provides one.

---

## File Structure

Create these Home Assistant files:

- `custom_components/fitorb/relay.py`: relay payload dataclasses, validation, JSON parsing, and acknowledgement result helpers.
- `custom_components/fitorb/relay_auth.py`: relay-scoped token generation, hashing, validation, and revocation backed by `Store`.
- `custom_components/fitorb/relay_api.py`: Home Assistant HTTP view for `/api/fitorb/relay/v1/samples`.
- `tests/test_relay.py`: pure relay payload parsing, validation, and acknowledgement tests.
- `tests/test_relay_auth.py`: token store generation, validation, revocation, and persistence tests.
- `tests/test_relay_api.py`: HTTP ingest view behavior with accepted, duplicate, rejected, unauthorized, and oversized batches.

Modify these Home Assistant files:

- `custom_components/fitorb/__init__.py`: load relay auth store, register relay services, register the HTTP view, and expose stores through `hass.data`.
- `custom_components/fitorb/models.py`: add relay diagnostic fields to `FitorbData`.
- `custom_components/fitorb/history_store.py`: persist relay samples and relay diagnostic metadata.
- `custom_components/fitorb/coordinator.py`: expose a method to apply accepted relay samples to current coordinator data.
- `custom_components/fitorb/sensor.py`: add relay diagnostic sensors.
- `custom_components/fitorb/binary_sensor.py`: add mobile relay activity binary sensor.
- `custom_components/fitorb/diagnostics.py`: include redacted relay diagnostics.
- `custom_components/fitorb/strings.json`: add service, option, and entity labels.
- `custom_components/fitorb/translations/de.json`: add German relay labels.
- `custom_components/fitorb/translations/en.json`: add English relay labels.
- `custom_components/fitorb/const.py`: add relay constants and bump `VERSION`.
- `custom_components/fitorb/manifest.json`: bump version.
- `README.md`: document relay setup, HTTPS, token scope, intervals, ring battery protection, and limitations.
- `tests/test_sensor.py`: cover relay diagnostic sensors.
- `tests/test_diagnostics.py`: cover relay diagnostic redaction.
- `tests/test_manifest.py`: cover version consistency after bump.

Create these Android files:

- `android/settings.gradle.kts`: Android project settings.
- `android/build.gradle.kts`: root Android Gradle plugin configuration.
- `android/gradle/libs.versions.toml`: Android dependency versions.
- `android/gradlew`: generated Gradle wrapper script.
- `android/gradlew.bat`: generated Gradle wrapper script for Windows.
- `android/gradle/wrapper/gradle-wrapper.jar`: generated Gradle wrapper runtime.
- `android/gradle/wrapper/gradle-wrapper.properties`: generated Gradle wrapper configuration.
- `android/app/build.gradle.kts`: application module build.
- `android/app/src/main/AndroidManifest.xml`: app permissions, service declaration, and network security constraints.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/MainActivity.kt`: minimal Compose setup/status UI.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelayDtos.kt`: Kotlin DTOs matching the Home Assistant contract.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleValueSerializer.kt`: JSON serializer for primitive relay sample values.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelayDatabase.kt`: Room database.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleEntity.kt`: queued sample entity.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleDao.kt`: queue DAO.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/network/FitorbRelayApi.kt`: OkHttp uploader.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/sync/SyncPolicy.kt`: interval and backoff policy.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/sync/RelayWorker.kt`: WorkManager entry point.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/service/FitorbRelayService.kt`: foreground service shell for active BLE work.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/ble/FitorbProtocol.kt`: Kotlin command builders and packet parsers ported from Python.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/ble/FitorbBleCollector.kt`: fakeable BLE collection interface and Android implementation boundary.
- `android/app/src/main/java/io/github/ichwars/fitorb/relay/settings/RelaySettings.kt`: persisted HA URL, token, ring ID, and interval settings.
- `android/app/src/test/java/io/github/ichwars/fitorb/relay/data/RelayDtosTest.kt`: DTO serialization unit tests.
- `android/app/src/test/java/io/github/ichwars/fitorb/relay/data/RelayQueueTest.kt`: queue unit tests.
- `android/app/src/test/java/io/github/ichwars/fitorb/relay/network/FitorbRelayApiTest.kt`: uploader unit tests with MockWebServer.
- `android/app/src/test/java/io/github/ichwars/fitorb/relay/sync/SyncPolicyTest.kt`: interval and backoff tests.
- `android/app/src/test/java/io/github/ichwars/fitorb/relay/ble/FitorbProtocolTest.kt`: Kotlin protocol parity tests.

---

### Task 1: Home Assistant Relay Contract

**Files:**
- Create: `custom_components/fitorb/relay.py`
- Create: `tests/test_relay.py`

**Interfaces:**
- Produces: `RelayMetric(StrEnum)` values `steps`, `calories`, `distance`, `heart_rate`, `spo2`, `stress`, `sleep_stage`, `sleep_summary`, `battery`, `charging`.
- Produces: `RelaySample(sample_id: str, ring_id: str, metric: RelayMetric, timestamp: datetime, value: int | float | str | bool, source: str, captured_at: datetime, unit: str | None = None, local_date: date | None = None, uploaded_at: datetime | None = None, raw_hex: str | None = None, protocol_version: int = 1)`.
- Produces: `RelayBatch(relay_id: str, ring_id: str, app_version: str, protocol_version: int, sent_at: datetime, samples: tuple[RelaySample, ...])`.
- Produces: `RelayRejectedSample(sample_id: str, reason: str)`.
- Produces: `RelayAckResult(accepted: tuple[str, ...], duplicates: tuple[str, ...], rejected: tuple[RelayRejectedSample, ...], server_time: datetime)`.
- Produces: `parse_relay_batch(payload: dict[str, object], *, max_samples: int) -> RelayBatch`.
- Produces: `relay_ack_to_json(result: RelayAckResult) -> dict[str, object]`.

- [ ] **Step 1: Write failing relay parsing tests**

Create `tests/test_relay.py`:

```python
from __future__ import annotations

from datetime import UTC, date, datetime

import pytest

from custom_components.fitorb.relay import (
    RelayAckResult,
    RelayMetric,
    RelayRejectedSample,
    parse_relay_batch,
    relay_ack_to_json,
)


def _payload() -> dict[str, object]:
    return {
        "relay_id": "pixel-8",
        "ring_id": "AA:BB:CC:DD:EE:FF",
        "app_version": "0.1.0",
        "protocol_version": 1,
        "sent_at": "2026-07-03T10:00:00Z",
        "samples": [
            {
                "sample_id": "sample-heart-1",
                "ring_id": "AA:BB:CC:DD:EE:FF",
                "metric": "heart_rate",
                "timestamp": "2026-07-03T09:55:00Z",
                "value": 72,
                "unit": "bpm",
                "source": "android_relay",
                "captured_at": "2026-07-03T09:55:05Z",
                "local_date": "2026-07-03",
                "protocol_version": 1,
            }
        ],
    }


def test_parse_relay_batch_normalizes_timestamps_and_metric() -> None:
    batch = parse_relay_batch(_payload(), max_samples=10)

    assert batch.relay_id == "pixel-8"
    assert batch.ring_id == "AA:BB:CC:DD:EE:FF"
    assert batch.sent_at == datetime(2026, 7, 3, 10, 0, tzinfo=UTC)
    assert len(batch.samples) == 1
    assert batch.samples[0].metric is RelayMetric.HEART_RATE
    assert batch.samples[0].timestamp == datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
    assert batch.samples[0].local_date == date(2026, 7, 3)


def test_parse_relay_batch_rejects_oversized_batches() -> None:
    payload = _payload()
    payload["samples"] = payload["samples"] * 2

    with pytest.raises(ValueError, match="too many samples"):
        parse_relay_batch(payload, max_samples=1)


def test_parse_relay_batch_rejects_metric_mismatch() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["metric"] = "unknown_metric"
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="invalid metric"):
        parse_relay_batch(payload, max_samples=10)


def test_parse_relay_batch_rejects_ring_id_mismatch() -> None:
    payload = _payload()
    sample = dict(payload["samples"][0])
    sample["ring_id"] = "11:22:33:44:55:66"
    payload["samples"] = [sample]

    with pytest.raises(ValueError, match="ring_id mismatch"):
        parse_relay_batch(payload, max_samples=10)


def test_relay_ack_to_json_uses_zulu_server_time() -> None:
    response = relay_ack_to_json(
        RelayAckResult(
            accepted=("sample-heart-1",),
            duplicates=("sample-heart-0",),
            rejected=(RelayRejectedSample("bad-sample", "invalid_metric"),),
            server_time=datetime(2026, 7, 3, 10, 2, tzinfo=UTC),
        )
    )

    assert response == {
        "accepted": ["sample-heart-1"],
        "duplicates": ["sample-heart-0"],
        "rejected": [{"sample_id": "bad-sample", "reason": "invalid_metric"}],
        "server_time": "2026-07-03T10:02:00Z",
    }
```

- [ ] **Step 2: Run relay parsing tests to verify they fail**

Run: `python -m pytest tests/test_relay.py -q`

Expected: FAIL with `ModuleNotFoundError: No module named 'custom_components.fitorb.relay'`.

- [ ] **Step 3: Add the relay contract implementation**

Create `custom_components/fitorb/relay.py`:

```python
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
    protocol_version = _required_int(payload, "protocol_version")
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
    local_date = _optional_date(value.get("local_date"))
    uploaded_at = _optional_datetime(value.get("uploaded_at"))
    return RelaySample(
        sample_id=_required_str(value, "sample_id"),
        ring_id=sample_ring_id,
        metric=metric,
        timestamp=_parse_datetime(_required_str(value, "timestamp")),
        value=sample_value,
        source=_required_str(value, "source"),
        captured_at=_parse_datetime(_required_str(value, "captured_at")),
        unit=_optional_str(value.get("unit")),
        local_date=local_date,
        uploaded_at=uploaded_at,
        raw_hex=_optional_str(value.get("raw_hex")),
        protocol_version=_optional_int(value.get("protocol_version"))
        or protocol_version,
    )


def _required_str(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{key} must be a non-empty string")
    return value


def _required_int(payload: dict[str, Any], key: str) -> int:
    value = payload.get(key)
    if not isinstance(value, int):
        raise ValueError(f"{key} must be an integer")
    return value


def _optional_str(value: object) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError("optional string field has invalid type")
    return value


def _optional_int(value: object) -> int | None:
    if value is None:
        return None
    if not isinstance(value, int):
        raise ValueError("optional integer field has invalid type")
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
```

- [ ] **Step 4: Run relay parsing tests to verify they pass**

Run: `python -m pytest tests/test_relay.py -q`

Expected: PASS, all tests in `tests/test_relay.py`.

- [ ] **Step 5: Commit relay contract**

```bash
git add custom_components/fitorb/relay.py tests/test_relay.py
git commit -m "feat: add fitorb relay payload contract"
```

---

### Task 2: Relay History Store Extension

**Files:**
- Modify: `custom_components/fitorb/models.py`
- Modify: `custom_components/fitorb/history_store.py`
- Create: `tests/test_relay_store.py`

**Interfaces:**
- Consumes: `RelayBatch`, `RelaySample`, `RelayAckResult`, and `RelayRejectedSample` from `custom_components.fitorb.relay`.
- Produces: `FitorbHistoryStore.async_record_relay_batch(batch: RelayBatch, received_at: datetime) -> RelayAckResult`.
- Produces: `FitorbHistoryStore.relay_last_upload -> datetime | None`.
- Produces: `FitorbHistoryStore.relay_last_sample -> datetime | None`.
- Produces: `FitorbHistoryStore.relay_last_rejected_count -> int`.
- Produces: `FitorbHistoryStore.relay_app_version -> str | None`.
- Produces: new `FitorbData` fields `last_relay_upload`, `last_relay_sample_time`, `relay_rejected_samples`, `relay_app_version`, `relay_backlog`, `relay_recently_active`.

- [ ] **Step 1: Write failing relay store tests**

Create `tests/test_relay_store.py`:

```python
from __future__ import annotations

import copy
import json
from datetime import UTC, datetime
from unittest import IsolatedAsyncioTestCase
from unittest.mock import patch

from custom_components.fitorb.relay import RelayBatch, RelayMetric, RelaySample


class _FakeStore:
    _saved: dict[str, dict[str, object]] = {}

    def __init__(self, hass: object, version: int, key: str) -> None:
        self.key = key

    async def async_load(self) -> dict[str, object] | None:
        data = self._saved.get(self.key)
        if data is None:
            return None
        return json.loads(json.dumps(data))

    async def async_save(self, data: dict[str, object]) -> None:
        self._saved[self.key] = copy.deepcopy(data)


def _sample(sample_id: str, value: int = 72) -> RelaySample:
    return RelaySample(
        sample_id=sample_id,
        ring_id="AA:BB:CC:DD:EE:FF",
        metric=RelayMetric.HEART_RATE,
        timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
        value=value,
        unit="bpm",
        source="android_relay",
        captured_at=datetime(2026, 7, 3, 9, 55, 5, tzinfo=UTC),
        protocol_version=1,
    )


def _batch(*samples: RelaySample) -> RelayBatch:
    return RelayBatch(
        relay_id="pixel-8",
        ring_id="AA:BB:CC:DD:EE:FF",
        app_version="0.1.0",
        protocol_version=1,
        sent_at=datetime(2026, 7, 3, 10, 0, tzinfo=UTC),
        samples=samples,
    )


class TestRelayHistoryStore(IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        _FakeStore._saved.clear()
        self._store_patch = patch(
            "custom_components.fitorb.history_store.Store",
            _FakeStore,
        )
        self._store_patch.start()
        self.addAsyncCleanup(self._async_cleanup)

    async def _async_cleanup(self) -> None:
        self._store_patch.stop()

    async def test_record_relay_batch_accepts_and_deduplicates(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        first = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )
        second = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 10, 2, tzinfo=UTC),
        )

        assert first.accepted == ("sample-heart-1",)
        assert first.duplicates == ()
        assert second.accepted == ()
        assert second.duplicates == ("sample-heart-1",)
        assert store.relay_last_upload == datetime(2026, 7, 3, 10, 2, tzinfo=UTC)
        assert store.relay_last_sample == datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
        assert store.relay_app_version == "0.1.0"

    async def test_record_relay_batch_rejects_wrong_ring_sample(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        bad = RelaySample(
            sample_id="wrong-ring",
            ring_id="11:22:33:44:55:66",
            metric=RelayMetric.HEART_RATE,
            timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
            value=72,
            source="android_relay",
            captured_at=datetime(2026, 7, 3, 9, 55, 5, tzinfo=UTC),
        )

        result = await store.async_record_relay_batch(
            _batch(bad),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )

        assert result.accepted == ()
        assert result.duplicates == ()
        assert [(item.sample_id, item.reason) for item in result.rejected] == [
            ("wrong-ring", "ring_id_mismatch")
        ]
        assert store.relay_last_rejected_count == 1
```

- [ ] **Step 2: Run relay store tests to verify they fail**

Run: `python -m pytest tests/test_relay_store.py -q`

Expected: FAIL with `AttributeError` for missing `async_record_relay_batch`.

- [ ] **Step 3: Add relay diagnostic fields to `FitorbData`**

Modify `custom_components/fitorb/models.py` inside `FitorbData`:

```python
    last_relay_upload: datetime | None = None
    last_relay_sample_time: datetime | None = None
    relay_rejected_samples: int = 0
    relay_app_version: str | None = None
    relay_backlog: int | None = None
    relay_recently_active: bool = False
```

- [ ] **Step 4: Extend the history store with relay persistence**

Modify `custom_components/fitorb/history_store.py`:

```python
from .relay import RelayAckResult, RelayBatch, RelayRejectedSample, RelaySample
```

Extend the initial `_data` dict with:

```python
            "relay": {
                "last_upload": None,
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": None,
                "samples": {},
            },
```

Add properties and methods to `FitorbHistoryStore`:

```python
    @property
    def relay_last_upload(self) -> datetime | None:
        """Return the latest mobile relay upload timestamp."""
        return _parse_datetime(self._relay_data().get("last_upload"))

    @property
    def relay_last_sample(self) -> datetime | None:
        """Return the latest sample timestamp accepted from mobile relay."""
        return _parse_datetime(self._relay_data().get("last_sample"))

    @property
    def relay_last_rejected_count(self) -> int:
        """Return rejected sample count from the latest relay batch."""
        return _parse_int(self._relay_data().get("last_rejected_count"))

    @property
    def relay_app_version(self) -> str | None:
        """Return the latest Android relay app version."""
        value = self._relay_data().get("app_version")
        return value if isinstance(value, str) else None

    async def async_record_relay_batch(
        self,
        batch: RelayBatch,
        received_at: datetime,
    ) -> RelayAckResult:
        """Record relay samples and return per-sample acknowledgement."""
        relay = self._relay_data()
        samples: dict[str, dict[str, Any]] = relay.setdefault("samples", {})
        accepted: list[str] = []
        duplicates: list[str] = []
        rejected: list[RelayRejectedSample] = []

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

        relay["last_upload"] = received_at.astimezone(UTC).isoformat()
        relay["last_rejected_count"] = len(rejected)
        relay["app_version"] = batch.app_version
        relay["last_sample"] = _latest_relay_timestamp(samples)
        await self._store.async_save(self._data)
        return RelayAckResult(
            accepted=tuple(accepted),
            duplicates=tuple(duplicates),
            rejected=tuple(rejected),
            server_time=received_at,
        )

    def _relay_data(self) -> dict[str, Any]:
        relay = self._data.setdefault("relay", {})
        if not isinstance(relay, dict):
            relay = {}
            self._data["relay"] = relay
        if not isinstance(relay.get("samples"), dict):
            relay["samples"] = {}
        return relay
```

Add helper functions near the bottom of the file:

```python
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
        "local_date": sample.local_date.isoformat() if sample.local_date else None,
        "uploaded_at": sample.uploaded_at.astimezone(UTC).isoformat()
        if sample.uploaded_at
        else None,
        "raw_hex": sample.raw_hex,
        "protocol_version": sample.protocol_version,
    }


def _latest_relay_timestamp(samples: dict[str, dict[str, Any]]) -> str | None:
    timestamps = [
        _parse_datetime(item.get("timestamp"))
        for item in samples.values()
        if isinstance(item, dict)
    ]
    valid = [timestamp for timestamp in timestamps if timestamp is not None]
    return max(valid).isoformat() if valid else None
```

- [ ] **Step 5: Run relay store tests to verify they pass**

Run: `python -m pytest tests/test_relay_store.py -q`

Expected: PASS, all tests in `tests/test_relay_store.py`.

- [ ] **Step 6: Run existing history store tests to verify no regression**

Run: `python -m pytest tests/test_history_store.py -q`

Expected: PASS, all tests in `tests/test_history_store.py`.

- [ ] **Step 7: Commit relay history store extension**

```bash
git add custom_components/fitorb/models.py custom_components/fitorb/history_store.py tests/test_relay_store.py
git commit -m "feat: persist fitorb relay samples"
```

---

### Task 3: Relay Token Store And Services

**Files:**
- Create: `custom_components/fitorb/relay_auth.py`
- Create: `tests/test_relay_auth.py`
- Modify: `custom_components/fitorb/__init__.py`
- Modify: `custom_components/fitorb/strings.json`
- Modify: `custom_components/fitorb/translations/de.json`
- Modify: `custom_components/fitorb/translations/en.json`

**Interfaces:**
- Produces: `FitorbRelayTokenStore.async_load() -> None`.
- Produces: `FitorbRelayTokenStore.async_create_token(entry_id: str, label: str) -> RelayTokenCreated`.
- Produces: `FitorbRelayTokenStore.async_validate_token(token: str) -> RelayTokenRecord | None`.
- Produces: `FitorbRelayTokenStore.async_revoke_token(token_id: str) -> bool`.
- Produces: services `fitorb.create_relay_token` and `fitorb.revoke_relay_token`.
- Consumes: Home Assistant service calls with `entry_id`, `label`, and `token_id`.

- [ ] **Step 1: Write failing relay auth tests**

Create `tests/test_relay_auth.py`:

```python
from __future__ import annotations

import copy
import json
from unittest import IsolatedAsyncioTestCase
from unittest.mock import patch


class _FakeStore:
    _saved: dict[str, dict[str, object]] = {}

    def __init__(self, hass: object, version: int, key: str) -> None:
        self.key = key

    async def async_load(self) -> dict[str, object] | None:
        data = self._saved.get(self.key)
        if data is None:
            return None
        return json.loads(json.dumps(data))

    async def async_save(self, data: dict[str, object]) -> None:
        self._saved[self.key] = copy.deepcopy(data)


class TestRelayAuth(IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        _FakeStore._saved.clear()
        self._store_patch = patch(
            "custom_components.fitorb.relay_auth.Store",
            _FakeStore,
        )
        self._store_patch.start()
        self.addAsyncCleanup(self._async_cleanup)

    async def _async_cleanup(self) -> None:
        self._store_patch.stop()

    async def test_create_validate_and_revoke_token(self) -> None:
        from custom_components.fitorb.relay_auth import FitorbRelayTokenStore

        store = FitorbRelayTokenStore(object())
        await store.async_load()
        created = await store.async_create_token("entry-id", "Pixel 8")

        assert created.token.startswith("fitorb_relay_")
        assert created.record.entry_id == "entry-id"
        assert created.record.label == "Pixel 8"
        assert created.token not in json.dumps(_FakeStore._saved)

        validated = await store.async_validate_token(created.token)
        assert validated == created.record

        assert await store.async_revoke_token(created.record.token_id) is True
        assert await store.async_validate_token(created.token) is None

    async def test_token_records_survive_reload(self) -> None:
        from custom_components.fitorb.relay_auth import FitorbRelayTokenStore

        store = FitorbRelayTokenStore(object())
        await store.async_load()
        created = await store.async_create_token("entry-id", "Pixel 8")

        reloaded = FitorbRelayTokenStore(object())
        await reloaded.async_load()

        assert await reloaded.async_validate_token(created.token) == created.record
```

- [ ] **Step 2: Run relay auth tests to verify they fail**

Run: `python -m pytest tests/test_relay_auth.py -q`

Expected: FAIL with `ModuleNotFoundError: No module named 'custom_components.fitorb.relay_auth'`.

- [ ] **Step 3: Add relay token store implementation**

Create `custom_components/fitorb/relay_auth.py`:

```python
from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
import hashlib
import secrets
from typing import Any
from uuid import uuid4

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .const import DOMAIN

_STORE_VERSION = 1
_TOKEN_PREFIX = "fitorb_relay_"


@dataclass(frozen=True, slots=True)
class RelayTokenRecord:
    """Stored relay token metadata."""

    token_id: str
    entry_id: str
    label: str
    created_at: datetime


@dataclass(frozen=True, slots=True)
class RelayTokenCreated:
    """Created relay token with one-time plaintext."""

    token: str
    record: RelayTokenRecord


class FitorbRelayTokenStore:
    """Store relay-scoped token hashes."""

    def __init__(self, hass: HomeAssistant) -> None:
        self._store: Store[dict[str, Any]] = Store(
            hass,
            _STORE_VERSION,
            f"{DOMAIN}_relay_tokens",
        )
        self._data: dict[str, Any] = {"tokens": {}}

    async def async_load(self) -> None:
        """Load token metadata."""
        loaded = await self._store.async_load()
        if loaded is not None:
            self._data.update(loaded)
        if not isinstance(self._data.get("tokens"), dict):
            self._data["tokens"] = {}

    async def async_create_token(
        self,
        entry_id: str,
        label: str,
    ) -> RelayTokenCreated:
        """Create a relay token and persist only its hash."""
        token_id = uuid4().hex
        token = f"{_TOKEN_PREFIX}{secrets.token_urlsafe(32)}"
        now = datetime.now(UTC)
        record = RelayTokenRecord(
            token_id=token_id,
            entry_id=entry_id,
            label=label,
            created_at=now,
        )
        self._tokens()[token_id] = {
            "token_hash": _hash_token(token),
            "entry_id": entry_id,
            "label": label,
            "created_at": now.isoformat(),
        }
        await self._store.async_save(self._data)
        return RelayTokenCreated(token=token, record=record)

    async def async_validate_token(self, token: str) -> RelayTokenRecord | None:
        """Return token metadata when a relay token is valid."""
        token_hash = _hash_token(token)
        for token_id, item in self._tokens().items():
            if not isinstance(item, dict):
                continue
            if item.get("token_hash") != token_hash:
                continue
            return _record_from_json(token_id, item)
        return None

    async def async_revoke_token(self, token_id: str) -> bool:
        """Revoke a token by id."""
        tokens = self._tokens()
        if token_id not in tokens:
            return False
        del tokens[token_id]
        await self._store.async_save(self._data)
        return True

    def _tokens(self) -> dict[str, Any]:
        tokens = self._data.setdefault("tokens", {})
        if not isinstance(tokens, dict):
            tokens = {}
            self._data["tokens"] = tokens
        return tokens


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _record_from_json(token_id: str, value: dict[str, Any]) -> RelayTokenRecord | None:
    entry_id = value.get("entry_id")
    label = value.get("label")
    created_at = value.get("created_at")
    if not isinstance(entry_id, str) or not isinstance(label, str):
        return None
    if not isinstance(created_at, str):
        return None
    try:
        parsed = datetime.fromisoformat(created_at)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return RelayTokenRecord(
        token_id=token_id,
        entry_id=entry_id,
        label=label,
        created_at=parsed.astimezone(UTC),
    )
```

- [ ] **Step 4: Run relay auth tests to verify they pass**

Run: `python -m pytest tests/test_relay_auth.py -q`

Expected: PASS, all tests in `tests/test_relay_auth.py`.

- [ ] **Step 5: Register relay token services**

Modify `custom_components/fitorb/__init__.py`:

```python
import voluptuous as vol

from homeassistant.core import ServiceCall, SupportsResponse

from .relay_auth import FitorbRelayTokenStore
```

Add service constants near the top:

```python
DATA_RELAY_TOKENS = "relay_tokens"
SERVICE_CREATE_RELAY_TOKEN = "create_relay_token"
SERVICE_REVOKE_RELAY_TOKEN = "revoke_relay_token"
```

Add this helper:

```python
async def _async_setup_relay_services(hass: HomeAssistant) -> None:
    """Set up relay token services once per Home Assistant process."""
    domain_data = hass.data.setdefault(DOMAIN, {})
    if DATA_RELAY_TOKENS not in domain_data:
        token_store = FitorbRelayTokenStore(hass)
        await token_store.async_load()
        domain_data[DATA_RELAY_TOKENS] = token_store
    else:
        token_store = domain_data[DATA_RELAY_TOKENS]

    if hass.services.has_service(DOMAIN, SERVICE_CREATE_RELAY_TOKEN):
        return

    async def _create_token(call: ServiceCall) -> dict[str, object]:
        created = await token_store.async_create_token(
            call.data["entry_id"],
            call.data["label"],
        )
        return {
            "token_id": created.record.token_id,
            "token": created.token,
            "entry_id": created.record.entry_id,
            "label": created.record.label,
        }

    async def _revoke_token(call: ServiceCall) -> dict[str, object]:
        revoked = await token_store.async_revoke_token(call.data["token_id"])
        return {"revoked": revoked}

    hass.services.async_register(
        DOMAIN,
        SERVICE_CREATE_RELAY_TOKEN,
        _create_token,
        schema=vol.Schema(
            {
                vol.Required("entry_id"): str,
                vol.Required("label"): str,
            }
        ),
        supports_response=SupportsResponse.ONLY,
    )
    hass.services.async_register(
        DOMAIN,
        SERVICE_REVOKE_RELAY_TOKEN,
        _revoke_token,
        schema=vol.Schema({vol.Required("token_id"): str}),
        supports_response=SupportsResponse.ONLY,
    )
```

Call it at the beginning of `async_setup_entry`:

```python
    await _async_setup_relay_services(hass)
```

- [ ] **Step 6: Add service labels**

Modify `custom_components/fitorb/strings.json`, `custom_components/fitorb/translations/en.json`, and `custom_components/fitorb/translations/de.json` so both services expose labels for `entry_id`, `label`, and `token_id`. Use `Fitorb Relay Token erstellen` and `Fitorb Relay Token widerrufen` for German service names.

- [ ] **Step 7: Run targeted auth and setup tests**

Run: `python -m pytest tests/test_relay_auth.py tests/test_config_flow.py -q`

Expected: PASS for relay auth and existing config flow tests.

- [ ] **Step 8: Commit relay auth**

```bash
git add custom_components/fitorb/__init__.py custom_components/fitorb/relay_auth.py custom_components/fitorb/strings.json custom_components/fitorb/translations/de.json custom_components/fitorb/translations/en.json tests/test_relay_auth.py
git commit -m "feat: add fitorb relay token services"
```

---

### Task 4: Home Assistant HTTPS Ingest Endpoint

**Files:**
- Create: `custom_components/fitorb/relay_api.py`
- Create: `tests/test_relay_api.py`
- Modify: `custom_components/fitorb/__init__.py`
- Modify: `custom_components/fitorb/coordinator.py`

**Interfaces:**
- Consumes: `FitorbRelayTokenStore.async_validate_token(token)`.
- Consumes: `parse_relay_batch(payload, max_samples=MAX_RELAY_BATCH_SAMPLES)`.
- Produces: `FitorbRelaySamplesView`.
- Produces: `FitorbDataUpdateCoordinator.async_record_relay_batch(batch: RelayBatch, received_at: datetime) -> RelayAckResult`.
- Endpoint: `POST /api/fitorb/relay/v1/samples`.

- [ ] **Step 1: Write failing relay API tests**

Create `tests/test_relay_api.py`:

```python
from __future__ import annotations

from datetime import UTC, datetime
from unittest.mock import AsyncMock

from aiohttp.test_utils import make_mocked_request

from custom_components.fitorb.relay_api import FitorbRelaySamplesView
from custom_components.fitorb.relay_auth import RelayTokenRecord


class _FakeTokenStore:
    async def async_validate_token(self, token: str) -> RelayTokenRecord | None:
        if token != "valid-token":
            return None
        return RelayTokenRecord(
            token_id="token-id",
            entry_id="entry-id",
            label="Pixel 8",
            created_at=datetime(2026, 7, 3, 8, 0, tzinfo=UTC),
        )


def _payload() -> dict[str, object]:
    return {
        "relay_id": "pixel-8",
        "ring_id": "AA:BB:CC:DD:EE:FF",
        "app_version": "0.1.0",
        "protocol_version": 1,
        "sent_at": "2026-07-03T10:00:00Z",
        "samples": [
            {
                "sample_id": "sample-heart-1",
                "ring_id": "AA:BB:CC:DD:EE:FF",
                "metric": "heart_rate",
                "timestamp": "2026-07-03T09:55:00Z",
                "value": 72,
                "unit": "bpm",
                "source": "android_relay",
                "captured_at": "2026-07-03T09:55:05Z",
                "protocol_version": 1,
            }
        ],
    }


async def test_relay_view_rejects_missing_bearer_token() -> None:
    hass = AsyncMock()
    view = FitorbRelaySamplesView(hass, _FakeTokenStore())
    request = make_mocked_request("POST", "/api/fitorb/relay/v1/samples")
    request.json = AsyncMock(return_value=_payload())

    response = await view.post(request)

    assert response.status == 401


async def test_relay_view_calls_matching_coordinator() -> None:
    from custom_components.fitorb.relay import RelayAckResult

    coordinator = AsyncMock()
    coordinator.async_record_relay_batch.return_value = RelayAckResult(
        accepted=("sample-heart-1",),
        duplicates=(),
        rejected=(),
        server_time=datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
    )
    hass = AsyncMock()
    hass.data = {"fitorb": {"entry-id": coordinator, "relay_tokens": object()}}
    view = FitorbRelaySamplesView(hass, _FakeTokenStore())
    request = make_mocked_request(
        "POST",
        "/api/fitorb/relay/v1/samples",
        headers={"Authorization": "Bearer valid-token"},
    )
    request.json = AsyncMock(return_value=_payload())

    response = await view.post(request)

    assert response.status == 200
    body = response.text
    assert '"accepted": ["sample-heart-1"]' in body
    coordinator.async_record_relay_batch.assert_awaited_once()
```

- [ ] **Step 2: Run relay API tests to verify they fail**

Run: `python -m pytest tests/test_relay_api.py -q`

Expected: FAIL with `ModuleNotFoundError: No module named 'custom_components.fitorb.relay_api'`.

- [ ] **Step 3: Add coordinator relay batch method**

Modify `custom_components/fitorb/coordinator.py`:

```python
from .relay import RelayAckResult, RelayBatch
```

Add this method to `FitorbDataUpdateCoordinator`:

```python
    async def async_record_relay_batch(
        self,
        batch: RelayBatch,
        received_at: datetime,
    ) -> RelayAckResult:
        """Persist relay samples and update coordinator diagnostics."""
        result = await self.history_store.async_record_relay_batch(
            batch,
            received_at,
        )
        current = self._apply_history_store_summary(self.data or self.base_data)
        relay_recent = self.history_store.relay_last_upload is not None and (
            received_at - self.history_store.relay_last_upload
            <= timedelta(minutes=30)
        )
        self.async_set_updated_data(
            current.with_values(
                last_relay_upload=self.history_store.relay_last_upload,
                last_relay_sample_time=self.history_store.relay_last_sample,
                relay_rejected_samples=self.history_store.relay_last_rejected_count,
                relay_app_version=self.history_store.relay_app_version,
                relay_recently_active=relay_recent,
            )
        )
        return result
```

- [ ] **Step 4: Add the HTTP view**

Create `custom_components/fitorb/relay_api.py`:

```python
from __future__ import annotations

from datetime import UTC, datetime
import json
from typing import Any

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant

from .const import DOMAIN
from .relay import parse_relay_batch, relay_ack_to_json
from .relay_auth import FitorbRelayTokenStore

MAX_RELAY_BATCH_SAMPLES = 500


class FitorbRelaySamplesView(HomeAssistantView):
    """Accept Android relay sample batches."""

    url = "/api/fitorb/relay/v1/samples"
    name = "api:fitorb:relay:samples"
    requires_auth = False

    def __init__(
        self,
        hass: HomeAssistant,
        token_store: FitorbRelayTokenStore,
    ) -> None:
        self.hass = hass
        self.token_store = token_store

    async def post(self, request: web.Request) -> web.Response:
        """Handle one relay upload."""
        token = _bearer_token(request.headers.get("Authorization"))
        if token is None:
            return web.json_response({"error": "unauthorized"}, status=401)
        record = await self.token_store.async_validate_token(token)
        if record is None:
            return web.json_response({"error": "unauthorized"}, status=401)
        try:
            payload = await request.json()
            if not isinstance(payload, dict):
                raise ValueError("payload must be an object")
            batch = parse_relay_batch(
                payload,
                max_samples=MAX_RELAY_BATCH_SAMPLES,
            )
        except (json.JSONDecodeError, ValueError) as err:
            return web.json_response({"error": str(err)}, status=400)

        coordinator = self.hass.data.get(DOMAIN, {}).get(record.entry_id)
        if coordinator is None:
            return web.json_response({"error": "entry_not_loaded"}, status=404)

        result = await coordinator.async_record_relay_batch(
            batch,
            datetime.now(UTC),
        )
        return web.json_response(relay_ack_to_json(result))


def _bearer_token(value: str | None) -> str | None:
    if value is None:
        return None
    prefix = "Bearer "
    if not value.startswith(prefix):
        return None
    token = value[len(prefix) :].strip()
    return token or None
```

- [ ] **Step 5: Register the view during setup**

Modify `custom_components/fitorb/__init__.py`:

```python
from .relay_api import FitorbRelaySamplesView
```

At the end of `_async_setup_relay_services`, after token store creation:

```python
    if not domain_data.get("relay_view_registered"):
        hass.http.register_view(FitorbRelaySamplesView(hass, token_store))
        domain_data["relay_view_registered"] = True
```

- [ ] **Step 6: Run relay API tests to verify they pass**

Run: `python -m pytest tests/test_relay_api.py -q`

Expected: PASS, all tests in `tests/test_relay_api.py`.

- [ ] **Step 7: Run relay-related Home Assistant tests**

Run: `python -m pytest tests/test_relay.py tests/test_relay_store.py tests/test_relay_auth.py tests/test_relay_api.py -q`

Expected: PASS for all relay tests.

- [ ] **Step 8: Commit relay ingest endpoint**

```bash
git add custom_components/fitorb/__init__.py custom_components/fitorb/coordinator.py custom_components/fitorb/relay_api.py tests/test_relay_api.py
git commit -m "feat: add fitorb relay ingest endpoint"
```

---

### Task 5: Relay Entities, Diagnostics, Version, And Docs

**Files:**
- Modify: `custom_components/fitorb/sensor.py`
- Modify: `custom_components/fitorb/binary_sensor.py`
- Modify: `custom_components/fitorb/diagnostics.py`
- Modify: `custom_components/fitorb/strings.json`
- Modify: `custom_components/fitorb/translations/de.json`
- Modify: `custom_components/fitorb/translations/en.json`
- Modify: `custom_components/fitorb/const.py`
- Modify: `custom_components/fitorb/manifest.json`
- Modify: `README.md`
- Modify: `tests/test_sensor.py`
- Modify: `tests/test_diagnostics.py`
- Modify: `tests/test_manifest.py`

**Interfaces:**
- Consumes: relay fields on `FitorbData`.
- Produces sensors `last_relay_upload`, `last_relay_sample_time`, `relay_rejected_samples`, `relay_app_version`, `relay_backlog`.
- Produces binary sensor `relay_recently_active`.
- Produces diagnostics key `relay`.

- [ ] **Step 1: Write failing sensor and diagnostics tests**

Append to `tests/test_sensor.py`:

```python
def test_relay_sensor_descriptions_exist() -> None:
    from custom_components.fitorb.sensor import SENSOR_DESCRIPTIONS

    assert "last_relay_upload" in SENSOR_DESCRIPTIONS
    assert "last_relay_sample_time" in SENSOR_DESCRIPTIONS
    assert "relay_rejected_samples" in SENSOR_DESCRIPTIONS
    assert "relay_app_version" in SENSOR_DESCRIPTIONS
```

Append to `tests/test_diagnostics.py` inside the fake data object setup by adding:

```python
        last_relay_upload=datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        last_relay_sample_time=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
        relay_rejected_samples=1,
        relay_app_version="0.1.0",
        relay_backlog=0,
        relay_recently_active=True,
```

Append to the diagnostics assertion:

```python
    assert diagnostics["relay"] == {
        "last_upload": "2026-07-03T10:01:00+00:00",
        "last_sample_time": "2026-07-03T09:55:00+00:00",
        "rejected_samples": 1,
        "app_version": "0.1.0",
        "backlog": 0,
        "recently_active": True,
    }
```

- [ ] **Step 2: Run sensor and diagnostics tests to verify they fail**

Run: `python -m pytest tests/test_sensor.py tests/test_diagnostics.py -q`

Expected: FAIL because relay sensor descriptions and diagnostics are not present.

- [ ] **Step 3: Add relay sensors**

Modify `custom_components/fitorb/sensor.py` in `SENSOR_DESCRIPTIONS`:

```python
    "last_relay_upload": FitorbSensorDescription(
        key="last_relay_upload",
        translation_key="last_relay_upload",
        device_class=SensorDeviceClass.TIMESTAMP,
        entity_category=EntityCategory.DIAGNOSTIC,
        value_fn=lambda data: data.last_relay_upload,
    ),
    "last_relay_sample_time": FitorbSensorDescription(
        key="last_relay_sample_time",
        translation_key="last_relay_sample_time",
        device_class=SensorDeviceClass.TIMESTAMP,
        entity_category=EntityCategory.DIAGNOSTIC,
        value_fn=lambda data: data.last_relay_sample_time,
    ),
    "relay_rejected_samples": FitorbSensorDescription(
        key="relay_rejected_samples",
        translation_key="relay_rejected_samples",
        entity_category=EntityCategory.DIAGNOSTIC,
        value_fn=lambda data: data.relay_rejected_samples,
    ),
    "relay_app_version": FitorbSensorDescription(
        key="relay_app_version",
        translation_key="relay_app_version",
        entity_category=EntityCategory.DIAGNOSTIC,
        value_fn=lambda data: data.relay_app_version,
    ),
    "relay_backlog": FitorbSensorDescription(
        key="relay_backlog",
        translation_key="relay_backlog",
        entity_category=EntityCategory.DIAGNOSTIC,
        value_fn=lambda data: data.relay_backlog,
    ),
```

- [ ] **Step 4: Add relay binary sensor**

Modify `custom_components/fitorb/binary_sensor.py` in `BINARY_SENSOR_DESCRIPTIONS`:

```python
    "relay_recently_active": FitorbBinarySensorDescription(
        key="relay_recently_active",
        translation_key="relay_recently_active",
        device_class=BinarySensorDeviceClass.CONNECTIVITY,
        entity_category=EntityCategory.DIAGNOSTIC,
        value_fn=lambda data: data.relay_recently_active,
    ),
```

- [ ] **Step 5: Add relay diagnostics**

Modify `custom_components/fitorb/diagnostics.py`:

```python
        "relay": {
            "last_upload": _iso_or_none(data.last_relay_upload) if data else None,
            "last_sample_time": _iso_or_none(data.last_relay_sample_time)
            if data
            else None,
            "rejected_samples": data.relay_rejected_samples if data else 0,
            "app_version": data.relay_app_version if data else None,
            "backlog": data.relay_backlog if data else None,
            "recently_active": data.relay_recently_active if data else False,
        },
```

- [ ] **Step 6: Add translations and version bump**

Modify:

- `custom_components/fitorb/strings.json`
- `custom_components/fitorb/translations/de.json`
- `custom_components/fitorb/translations/en.json`

Add labels for every new relay entity. Use German labels:

```text
Letzter mobiler Relay Upload
Letzter mobiler Relay Messpunkt
Abgelehnte Relay Samples
Relay App Version
Relay Warteschlange
Mobiler Relay kuerzlich aktiv
```

Modify `custom_components/fitorb/const.py`:

```python
VERSION = "0.3.0"
```

Modify `custom_components/fitorb/manifest.json`:

```json
  "version": "0.3.0"
```

- [ ] **Step 7: Document relay setup in README**

Add a `Mobile Android Relay` section to `README.md` with:

```markdown
## Mobile Android Relay

The optional Android relay app is designed for multi-day travel where the ring
is not near Home Assistant Bluetooth. The app reads the ring on a configurable
schedule, stores samples locally, and uploads batches to Home Assistant over
your own HTTPS endpoint.

Use the `fitorb.create_relay_token` service to create a relay-scoped token for
one Android device. Store that token in the relay app. The token is only valid
for Fitorb relay ingest and can be revoked with `fitorb.revoke_relay_token`.

Recommended defaults:

- Ring sync interval: 10 minutes.
- Scan window: 15-20 seconds.
- One retry per BLE cycle.
- Backoff after repeated failures up to 60 minutes.

The relay does not keep a permanent BLE connection to the ring. Upload retries
reuse locally queued samples and do not wake the ring again.
```

- [ ] **Step 8: Run entity, diagnostics, and manifest tests**

Run: `python -m pytest tests/test_sensor.py tests/test_diagnostics.py tests/test_manifest.py -q`

Expected: PASS for sensor, diagnostics, and manifest tests.

- [ ] **Step 9: Commit relay entities and docs**

```bash
git add README.md custom_components/fitorb/binary_sensor.py custom_components/fitorb/const.py custom_components/fitorb/diagnostics.py custom_components/fitorb/manifest.json custom_components/fitorb/sensor.py custom_components/fitorb/strings.json custom_components/fitorb/translations/de.json custom_components/fitorb/translations/en.json tests/test_diagnostics.py tests/test_manifest.py tests/test_sensor.py
git commit -m "feat: expose fitorb relay diagnostics"
```

---

### Task 6: Android Project Skeleton And Shared DTOs

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/gradlew`
- Create: `android/gradlew.bat`
- Create: `android/gradle/wrapper/gradle-wrapper.jar`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/MainActivity.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelayDtos.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleValueSerializer.kt`
- Create: `android/app/src/test/java/io/github/ichwars/fitorb/relay/data/RelayDtosTest.kt`

**Interfaces:**
- Produces Kotlin package `io.github.ichwars.fitorb.relay`.
- Produces `RelaySampleDto`, `RelayBatchDto`, `RelayAckDto`, and `RejectedSampleDto`.
- Produces JSON contract matching `custom_components/fitorb/relay.py`.

- [ ] **Step 1: Create failing DTO tests**

Create `android/app/src/test/java/io/github/ichwars/fitorb/relay/data/RelayDtosTest.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RelayDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun relayBatchSerializesWithContractFieldNames() {
        val batch = RelayBatchDto(
            relayId = "pixel-8",
            ringId = "AA:BB:CC:DD:EE:FF",
            appVersion = "0.1.0",
            protocolVersion = 1,
            sentAt = "2026-07-03T10:00:00Z",
            samples = listOf(
                RelaySampleDto(
                    sampleId = "sample-heart-1",
                    ringId = "AA:BB:CC:DD:EE:FF",
                    metric = "heart_rate",
                    timestamp = "2026-07-03T09:55:00Z",
                    value = RelaySampleValue.IntValue(72),
                    unit = "bpm",
                    source = "android_relay",
                    capturedAt = "2026-07-03T09:55:05Z",
                    localDate = "2026-07-03",
                    protocolVersion = 1,
                )
            ),
        )

        val encoded = json.encodeToString(RelayBatchDto.serializer(), batch)

        assertEquals(true, encoded.contains("\"relay_id\":\"pixel-8\""))
        assertEquals(true, encoded.contains("\"sample_id\":\"sample-heart-1\""))
        assertEquals(true, encoded.contains("\"metric\":\"heart_rate\""))
    }
}
```

- [ ] **Step 2: Add Android Gradle files**

Create `android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FitorbRelay"
include(":app")
```

Create `android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

Create `android/gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
coreKtx = "1.13.1"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
room = "2.6.1"
work = "2.10.0"
okhttp = "4.12.0"
serialization = "1.7.3"
junit = "4.13.2"
mockwebserver = "4.12.0"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "mockwebserver" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
junit = { module = "junit:junit", version.ref = "junit" }
```

Create `android/app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.ichwars.fitorb.relay"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ichwars.fitorb.relay"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
testImplementation(libs.mockwebserver)
}
```

- [ ] **Step 3: Generate the Gradle wrapper**

Run: `cd android; gradle wrapper --gradle-version 8.10.2`

Expected: Creates `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar`, and `android/gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 4: Add Android manifest and minimal activity**

Create `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="Fitorb Relay"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/MainActivity.kt`:

```kotlin
package io.github.ichwars.fitorb.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Fitorb Relay")
                }
            }
        }
    }
}
```

- [ ] **Step 5: Add Kotlin relay DTOs**

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelayDtos.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RelayBatchDto(
    @SerialName("relay_id") val relayId: String,
    @SerialName("ring_id") val ringId: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("sent_at") val sentAt: String,
    val samples: List<RelaySampleDto>,
)

@Serializable
data class RelaySampleDto(
    @SerialName("sample_id") val sampleId: String,
    @SerialName("ring_id") val ringId: String,
    val metric: String,
    val timestamp: String,
    val value: RelaySampleValue,
    val unit: String? = null,
    val source: String = "android_relay",
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("local_date") val localDate: String? = null,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
    @SerialName("raw_hex") val rawHex: String? = null,
    @SerialName("protocol_version") val protocolVersion: Int = 1,
)

@Serializable(with = RelaySampleValueSerializer::class)
sealed interface RelaySampleValue {
    val jsonElement: JsonElement

    data class IntValue(val value: Int) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }

    data class DoubleValue(val value: Double) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }

    data class StringValue(val value: String) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }

    data class BoolValue(val value: Boolean) : RelaySampleValue {
        override val jsonElement: JsonElement = JsonPrimitive(value)
    }
}

@Serializable
data class RelayAckDto(
    val accepted: List<String>,
    val duplicates: List<String>,
    val rejected: List<RejectedSampleDto>,
    @SerialName("server_time") val serverTime: String,
)

@Serializable
data class RejectedSampleDto(
    @SerialName("sample_id") val sampleId: String,
    val reason: String,
)
```

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleValueSerializer.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object RelaySampleValueSerializer : KSerializer<RelaySampleValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RelaySampleValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RelaySampleValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.jsonElement)
    }

    override fun deserialize(decoder: Decoder): RelaySampleValue {
        require(decoder is JsonDecoder)
        val primitive = decoder.decodeJsonElement().jsonPrimitive
        primitive.booleanOrNull?.let { return RelaySampleValue.BoolValue(it) }
        primitive.intOrNull?.let { return RelaySampleValue.IntValue(it) }
        primitive.doubleOrNull?.let { return RelaySampleValue.DoubleValue(it) }
        return RelaySampleValue.StringValue(primitive.content)
    }
}
```

- [ ] **Step 6: Run Android DTO tests**

Run: `cd android; .\gradlew.bat testDebugUnitTest`

Expected: PASS for `RelayDtosTest`.

- [ ] **Step 7: Commit Android skeleton**

```bash
git add android
git commit -m "feat: scaffold fitorb relay android app"
```

---

### Task 7: Android Queue And HTTPS Upload Client

**Files:**
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleEntity.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelaySampleDao.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/data/RelayDatabase.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/network/FitorbRelayApi.kt`
- Create: `android/app/src/test/java/io/github/ichwars/fitorb/relay/data/RelayQueueTest.kt`
- Create: `android/app/src/test/java/io/github/ichwars/fitorb/relay/network/FitorbRelayApiTest.kt`

**Interfaces:**
- Consumes: `RelayBatchDto`, `RelaySampleDto`, and `RelayAckDto`.
- Produces: Room entity `RelaySampleEntity`.
- Produces: DAO methods `insertQueued`, `pendingBatch`, `markDelivered`, and `markRejected`.
- Produces: `FitorbRelayApi.upload(batch: RelayBatchDto, token: String) -> RelayAckDto`.

- [ ] **Step 1: Write failing queue and uploader tests**

Create `android/app/src/test/java/io/github/ichwars/fitorb/relay/network/FitorbRelayApiTest.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.network

import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class FitorbRelayApiTest {
    @Test
    fun uploadSendsBearerTokenAndParsesAck() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"accepted":["sample-heart-1"],"duplicates":[],"rejected":[],"server_time":"2026-07-03T10:01:00Z"}"""
                )
        )
        server.start()
        try {
            val api = FitorbRelayApi(server.url("/").toString())
            val ack = api.upload(sampleBatch(), "secret-token")
            val request = server.takeRequest()

            assertEquals("Bearer secret-token", request.getHeader("Authorization"))
            assertEquals("/api/fitorb/relay/v1/samples", request.path)
            assertEquals(listOf("sample-heart-1"), ack.accepted)
        } finally {
            server.shutdown()
        }
    }

    private fun sampleBatch() = RelayBatchDto(
        relayId = "pixel-8",
        ringId = "AA:BB:CC:DD:EE:FF",
        appVersion = "0.1.0",
        protocolVersion = 1,
        sentAt = "2026-07-03T10:00:00Z",
        samples = listOf(
            RelaySampleDto(
                sampleId = "sample-heart-1",
                ringId = "AA:BB:CC:DD:EE:FF",
                metric = "heart_rate",
                timestamp = "2026-07-03T09:55:00Z",
                value = RelaySampleValue.IntValue(72),
                unit = "bpm",
                capturedAt = "2026-07-03T09:55:05Z",
            )
        ),
    )
}
```

- [ ] **Step 2: Run Android network test to verify it fails**

Run: `cd android; .\gradlew.bat testDebugUnitTest --tests "*FitorbRelayApiTest"`

Expected: FAIL with unresolved `FitorbRelayApi`.

- [ ] **Step 3: Add Room queue files**

Create `RelaySampleEntity.kt`, `RelaySampleDao.kt`, and `RelayDatabase.kt` with:

```kotlin
@Entity(tableName = "relay_samples")
data class RelaySampleEntity(
    @PrimaryKey val sampleId: String,
    val ringId: String,
    val metric: String,
    val timestamp: String,
    val valueJson: String,
    val unit: String?,
    val capturedAt: String,
    val delivered: Boolean = false,
    val rejectedReason: String? = null,
)
```

```kotlin
@Dao
interface RelaySampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueued(sample: RelaySampleEntity)

    @Query("SELECT * FROM relay_samples WHERE delivered = 0 AND rejectedReason IS NULL ORDER BY timestamp LIMIT :limit")
    suspend fun pendingBatch(limit: Int): List<RelaySampleEntity>

    @Query("UPDATE relay_samples SET delivered = 1 WHERE sampleId IN (:sampleIds)")
    suspend fun markDelivered(sampleIds: List<String>)

    @Query("UPDATE relay_samples SET rejectedReason = :reason WHERE sampleId = :sampleId")
    suspend fun markRejected(sampleId: String, reason: String)
}
```

```kotlin
@Database(entities = [RelaySampleEntity::class], version = 1)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun relaySampleDao(): RelaySampleDao
}
```

- [ ] **Step 4: Add HTTPS uploader**

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/network/FitorbRelayApi.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.network

import io.github.ichwars.fitorb.relay.data.RelayAckDto
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FitorbRelayApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun upload(batch: RelayBatchDto, token: String): RelayAckDto =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(RelayBatchDto.serializer(), batch)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/fitorb/relay/v1/samples")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RelayUploadException("HTTP ${response.code}")
                }
                val responseBody = response.body?.string()
                    ?: throw RelayUploadException("empty response")
                json.decodeFromString(RelayAckDto.serializer(), responseBody)
            }
        }
}

class RelayUploadException(message: String) : RuntimeException(message)
```

- [ ] **Step 5: Run Android uploader test**

Run: `cd android; .\gradlew.bat testDebugUnitTest --tests "*FitorbRelayApiTest"`

Expected: PASS.

- [ ] **Step 6: Commit Android queue and uploader**

```bash
git add android/app/src/main/java/io/github/ichwars/fitorb/relay/data android/app/src/main/java/io/github/ichwars/fitorb/relay/network android/app/src/test/java/io/github/ichwars/fitorb/relay/data android/app/src/test/java/io/github/ichwars/fitorb/relay/network
git commit -m "feat: add fitorb relay android queue uploader"
```

---

### Task 8: Android Sync Policy, Settings, Worker, And Foreground Service

**Files:**
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/settings/RelaySettings.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/sync/SyncPolicy.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/sync/RelayWorker.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/service/FitorbRelayService.kt`
- Create: `android/app/src/test/java/io/github/ichwars/fitorb/relay/sync/SyncPolicyTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `SyncPolicy.nextDelayMinutes(failures: Int, configuredIntervalMinutes: Int) -> Long`.
- Produces: `SyncPolicy.shouldStretchForLowRingBattery(ringBatteryPercent: Int?) -> Boolean`.
- Produces: `RelayWorker` that uploads queued data without triggering BLE reads when only upload retry is needed.
- Produces: `FitorbRelayService` foreground service declared with `connectedDevice`.

- [ ] **Step 1: Write failing sync policy tests**

Create `android/app/src/test/java/io/github/ichwars/fitorb/relay/sync/SyncPolicyTest.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPolicyTest {
    @Test
    fun backoffCapsAtSixtyMinutes() {
        assertEquals(10, SyncPolicy.nextDelayMinutes(0, 10))
        assertEquals(20, SyncPolicy.nextDelayMinutes(1, 10))
        assertEquals(40, SyncPolicy.nextDelayMinutes(2, 10))
        assertEquals(60, SyncPolicy.nextDelayMinutes(3, 10))
        assertEquals(60, SyncPolicy.nextDelayMinutes(9, 10))
    }

    @Test
    fun lowRingBatteryStretchesSync() {
        assertTrue(SyncPolicy.shouldStretchForLowRingBattery(19))
        assertFalse(SyncPolicy.shouldStretchForLowRingBattery(20))
        assertFalse(SyncPolicy.shouldStretchForLowRingBattery(null))
    }
}
```

- [ ] **Step 2: Run sync policy tests to verify they fail**

Run: `cd android; .\gradlew.bat testDebugUnitTest --tests "*SyncPolicyTest"`

Expected: FAIL with unresolved `SyncPolicy`.

- [ ] **Step 3: Add sync policy**

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/sync/SyncPolicy.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.sync

object SyncPolicy {
    fun nextDelayMinutes(failures: Int, configuredIntervalMinutes: Int): Long {
        val base = configuredIntervalMinutes.coerceIn(1, 60)
        if (failures <= 0) return base.toLong()
        val multiplier = 1 shl failures.coerceAtMost(3)
        return (base * multiplier).coerceAtMost(60).toLong()
    }

    fun shouldStretchForLowRingBattery(ringBatteryPercent: Int?): Boolean {
        return ringBatteryPercent != null && ringBatteryPercent < 20
    }
}
```

- [ ] **Step 4: Add settings holder**

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/settings/RelaySettings.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.settings

data class RelaySettings(
    val homeAssistantUrl: String,
    val relayToken: String,
    val relayId: String,
    val ringId: String,
    val syncIntervalMinutes: Int = 10,
)
```

- [ ] **Step 5: Add worker and foreground service shells**

Create `RelayWorker.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RelayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
```

Create `FitorbRelayService.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FitorbRelayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
```

Modify `AndroidManifest.xml` inside `<application>`:

```xml
        <service
            android:name=".service.FitorbRelayService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
```

- [ ] **Step 6: Run sync policy tests**

Run: `cd android; .\gradlew.bat testDebugUnitTest --tests "*SyncPolicyTest"`

Expected: PASS.

- [ ] **Step 7: Commit scheduler shell**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/io/github/ichwars/fitorb/relay/service android/app/src/main/java/io/github/ichwars/fitorb/relay/settings android/app/src/main/java/io/github/ichwars/fitorb/relay/sync android/app/src/test/java/io/github/ichwars/fitorb/relay/sync
git commit -m "feat: add fitorb relay android sync policy"
```

---

### Task 9: Android BLE Protocol And Collector Boundary

**Files:**
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/ble/FitorbProtocol.kt`
- Create: `android/app/src/main/java/io/github/ichwars/fitorb/relay/ble/FitorbBleCollector.kt`
- Create: `android/app/src/test/java/io/github/ichwars/fitorb/relay/ble/FitorbProtocolTest.kt`

**Interfaces:**
- Produces: `FitorbProtocol.buildCommand(hexPayload: String): ByteArray`.
- Produces: `FitorbProtocol.parseNotification(payload: ByteArray): ParsedRingPacket?`.
- Produces: `FitorbBleCollector.collectOnce(ringId: String): List<RelaySampleDto>`.
- BLE collector must scan before connecting and return an empty list when the ring is not visible.

- [ ] **Step 1: Write failing Kotlin protocol parity tests**

Create `android/app/src/test/java/io/github/ichwars/fitorb/relay/ble/FitorbProtocolTest.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FitorbProtocolTest {
    @Test
    fun buildCommandPadsToSixteenBytesAndAddsChecksum() {
        assertContentEquals(
            byteArrayOf(0x0A, 0x02, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0C),
            FitorbProtocol.buildCommand("0a0200"),
        )
    }

    @Test
    fun parseBatteryPacket() {
        val parsed = FitorbProtocol.parseNotification(
            byteArrayOf(0x03, 71, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 75)
        )

        assertEquals(ParsedRingPacket.Battery(71, true), parsed)
    }
}
```

- [ ] **Step 2: Run Kotlin protocol tests to verify they fail**

Run: `cd android; .\gradlew.bat testDebugUnitTest --tests "*FitorbProtocolTest"`

Expected: FAIL with unresolved `FitorbProtocol`.

- [ ] **Step 3: Add Kotlin protocol parser**

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/ble/FitorbProtocol.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.ble

sealed interface ParsedRingPacket {
    data class Battery(val batteryLevel: Int, val isCharging: Boolean) : ParsedRingPacket
    data class Activity(val steps: Int, val calories: Int, val distance: Int) : ParsedRingPacket
    data class HeartRate(val value: Int?) : ParsedRingPacket
    data class Spo2(val value: Int?) : ParsedRingPacket
    data class Stress(val value: Int?) : ParsedRingPacket
}

object FitorbProtocol {
    fun buildCommand(hexPayload: String): ByteArray {
        require(hexPayload.length % 2 == 0) { "hex payload must have even length" }
        require(hexPayload.length <= 30) { "hex payload must fit in one packet" }
        val command = ByteArray(16)
        hexPayload.chunked(2).forEachIndexed { index, chunk ->
            command[index] = chunk.toInt(16).toByte()
        }
        command[15] = command.take(15).sumOf { it.toInt() and 0xff }.and(0xff).toByte()
        return command
    }

    fun parseNotification(payload: ByteArray): ParsedRingPacket? {
        if (payload.size != 16) return null
        val first = payload[0].toInt() and 0xff
        val second = payload[1].toInt() and 0xff
        if (first == 0x03) {
            return ParsedRingPacket.Battery(
                batteryLevel = payload[1].toInt() and 0xff,
                isCharging = payload[2].toInt() == 1,
            )
        }
        if (first == 0x73 && second == 0x12) {
            val steps = ((payload[2].toInt() and 0xff) shl 16) or
                ((payload[3].toInt() and 0xff) shl 8) or
                (payload[4].toInt() and 0xff)
            val calories = (((payload[5].toInt() and 0xff) shl 16) or
                ((payload[6].toInt() and 0xff) shl 8) or
                (payload[7].toInt() and 0xff)) / 1000
            val distance = ((payload[8].toInt() and 0xff) shl 16) or
                ((payload[9].toInt() and 0xff) shl 8) or
                (payload[10].toInt() and 0xff)
            return ParsedRingPacket.Activity(steps, calories, distance)
        }
        if (first == 0x69 && second == 0x01) {
            val value = payload[3].toInt() and 0xff
            return ParsedRingPacket.HeartRate(value.takeIf { it > 0 })
        }
        if (first == 0x69 && second == 0x03) {
            val value = payload[3].toInt() and 0xff
            return ParsedRingPacket.Spo2(value.takeIf { it > 0 })
        }
        if (first == 0x69 && second == 0x08) {
            val value = payload[3].toInt() and 0xff
            return ParsedRingPacket.Stress(value.takeIf { it > 0 })
        }
        return null
    }
}
```

- [ ] **Step 4: Add fakeable collector boundary**

Create `android/app/src/main/java/io/github/ichwars/fitorb/relay/ble/FitorbBleCollector.kt`:

```kotlin
package io.github.ichwars.fitorb.relay.ble

import io.github.ichwars.fitorb.relay.data.RelaySampleDto

interface FitorbBleCollector {
    suspend fun collectOnce(ringId: String): List<RelaySampleDto>
}

class AndroidFitorbBleCollector : FitorbBleCollector {
    override suspend fun collectOnce(ringId: String): List<RelaySampleDto> {
        return emptyList()
    }
}
```

- [ ] **Step 5: Run Kotlin protocol tests**

Run: `cd android; .\gradlew.bat testDebugUnitTest --tests "*FitorbProtocolTest"`

Expected: PASS.

- [ ] **Step 6: Commit Android BLE protocol boundary**

```bash
git add android/app/src/main/java/io/github/ichwars/fitorb/relay/ble android/app/src/test/java/io/github/ichwars/fitorb/relay/ble
git commit -m "feat: add fitorb relay android ble protocol"
```

---

### Task 10: End-To-End Contract Verification And Release Prep

**Files:**
- Modify: `README.md`
- Create: `docs/superpowers/specs/2026-07-03-fitorb-android-relay-design.md` only if design amendments are needed during implementation.
- Modify: `.gitignore` only if Android build outputs appear unignored.

**Interfaces:**
- Consumes all previous tasks.
- Produces a repeatable verification checklist for HA and Android.

- [ ] **Step 1: Run all Home Assistant checks**

Run:

```powershell
python -B -m compileall custom_components tests
python -m json.tool custom_components\fitorb\manifest.json
python -m pytest tests -q
git diff --check
```

Expected:

- `compileall` exits 0.
- `json.tool` exits 0.
- `pytest` exits 0.
- `git diff --check` exits 0.

- [ ] **Step 2: Run Android unit tests**

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
```

Expected: Gradle exits 0 and all Android unit tests pass.

- [ ] **Step 3: Verify relay token service manually in a Home Assistant dev instance**

In Home Assistant Developer Tools, call:

```yaml
service: fitorb.create_relay_token
data:
  entry_id: "<real-config-entry-id>"
  label: "Pixel test"
```

Expected response contains `token_id`, `token`, `entry_id`, and `label`. The `token` begins with `fitorb_relay_`.

- [ ] **Step 4: Verify ingest endpoint manually**

Run against the dev instance:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "https://YOUR_HA_HOST/api/fitorb/relay/v1/samples" `
  -Headers @{ Authorization = "Bearer YOUR_RELAY_TOKEN" } `
  -ContentType "application/json" `
  -Body '{
    "relay_id":"manual-test",
    "ring_id":"AA:BB:CC:DD:EE:FF",
    "app_version":"0.1.0",
    "protocol_version":1,
    "sent_at":"2026-07-03T10:00:00Z",
    "samples":[
      {
        "sample_id":"manual-heart-1",
        "ring_id":"AA:BB:CC:DD:EE:FF",
        "metric":"heart_rate",
        "timestamp":"2026-07-03T09:55:00Z",
        "value":72,
        "unit":"bpm",
        "source":"android_relay",
        "captured_at":"2026-07-03T09:55:05Z",
        "protocol_version":1
      }
    ]
  }'
```

Expected response:

```json
{
  "accepted": ["manual-heart-1"],
  "duplicates": [],
  "rejected": [],
  "server_time": "<server UTC timestamp>"
}
```

Run the same command a second time. Expected response has `manual-heart-1` in `duplicates`.

- [ ] **Step 5: Verify Android upload against Home Assistant**

Configure the Android app with:

- Home Assistant HTTPS URL.
- Relay token from Step 3.
- Ring ID matching the configured Fitorb entry.
- Sync interval `10`.

Trigger one upload using a queued fake sample in the Android debugger or a test button hidden behind a debug build flag named `debugUploadOneSample`.

Expected:

- App marks accepted or duplicate sample as delivered.
- Home Assistant relay diagnostic sensors update.
- Android does not perform a second BLE read when upload retry is tested.

- [ ] **Step 6: Update README with verified commands**

If command names or response fields changed during implementation, update the `Mobile Android Relay` README section with the final verified service and HTTP examples.

- [ ] **Step 7: Commit final verification docs**

```bash
git add README.md .gitignore docs/superpowers/specs/2026-07-03-fitorb-android-relay-design.md
git commit -m "docs: document fitorb relay verification"
```

Skip this commit only when none of those files changed.
