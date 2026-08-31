from __future__ import annotations

import copy
import json
from datetime import UTC, datetime, timedelta, timezone
from unittest import IsolatedAsyncioTestCase
from unittest.mock import patch

from custom_components.fitorb.relay import (
    MAX_RELAY_VALUE_STRING_LENGTH,
    RelayBatch,
    RelayMetric,
    RelaySample,
)

_MAX_STORED_SAMPLES_PATH = (
    "custom_components.fitorb.history_store.MAX_RELAY_STORED_SAMPLES"
)


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


def _sample(
    sample_id: str,
    value: int = 72,
    timestamp: datetime | None = None,
) -> RelaySample:
    sample_time = timestamp or datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
    return RelaySample(
        sample_id=sample_id,
        ring_id="AA:BB:CC:DD:EE:FF",
        metric=RelayMetric.HEART_RATE,
        timestamp=sample_time,
        value=value,
        unit="bpm",
        source="android_relay",
        captured_at=sample_time + timedelta(seconds=5),
        protocol_version=1,
    )


def _batch(*samples: RelaySample, backlog: int | None = None) -> RelayBatch:
    return RelayBatch(
        relay_id="pixel-8",
        ring_id="AA:BB:CC:DD:EE:FF",
        app_version="0.1.0",
        protocol_version=1,
        sent_at=datetime(2026, 7, 3, 10, 0, tzinfo=UTC),
        samples=samples,
        backlog=backlog,
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
            _batch(_sample("sample-heart-1"), backlog=3),
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
        assert store.relay_backlog is None
        assert store.relay_latest_values == {RelayMetric.HEART_RATE: 72}
        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        assert persisted["relay"]["backlog"] is None

    async def test_record_relay_batch_deduplicates_across_reload(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        first = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )
        reloaded_store = FitorbHistoryStore(object(), "entry-id")
        await reloaded_store.async_load()
        second = await reloaded_store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 10, 2, tzinfo=UTC),
        )

        assert first.accepted == ("sample-heart-1",)
        assert second.accepted == ()
        assert second.duplicates == ("sample-heart-1",)
        assert reloaded_store.relay_last_upload == datetime(
            2026, 7, 3, 10, 2, tzinfo=UTC
        )
        assert reloaded_store.relay_last_sample == datetime(
            2026, 7, 3, 9, 55, tzinfo=UTC
        )
        assert reloaded_store.relay_app_version == "0.1.0"

    async def test_record_relay_batch_preserves_malformed_sample_keys(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        _FakeStore._saved["fitorb_history_entry-id"] = {
            "relay": {
                "last_upload": "2026-07-03T10:00:00+00:00",
                "last_sample": "2026-07-03T09:55:00+00:00",
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "backlog": 4,
                "samples": {"sample-heart-1": "bad-shape"},
            }
        }

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        result = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1"), backlog=4),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )

        assert result.accepted == ()
        assert result.duplicates == ("sample-heart-1",)
        assert store.relay_backlog == 4
        assert store.relay_last_sample == datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        assert persisted["relay"]["samples"]["sample-heart-1"] == "bad-shape"
        assert persisted["relay"]["last_sample"] == "2026-07-03T09:55:00+00:00"

    async def test_record_relay_batch_persists_backlog(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()

        await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1"), backlog=7),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )
        reloaded_store = FitorbHistoryStore(object(), "entry-id")
        await reloaded_store.async_load()

        assert reloaded_store.relay_backlog == 7
        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        assert persisted["relay"]["backlog"] == 7

    async def test_record_relay_batch_prunes_oldest_relay_samples(self) -> None:
        from custom_components.fitorb.history_store import (
            MAX_RELAY_STORED_SAMPLES,
            FitorbHistoryStore,
        )

        assert MAX_RELAY_STORED_SAMPLES == 10000
        _FakeStore._saved["fitorb_history_entry-id"] = {
            "relay": {
                "last_upload": "2026-07-03T10:00:00+00:00",
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "samples": {
                    "malformed-timestamp": {
                        "sample_id": "malformed-timestamp",
                        "ring_id": "AA:BB:CC:DD:EE:FF",
                        "metric": "heart_rate",
                        "timestamp": "not-a-timestamp",
                        "value": 72,
                        "unit": "bpm",
                        "source": "android_relay",
                        "captured_at": "2026-07-03T09:54:05+00:00",
                        "protocol_version": 1,
                    }
                },
            }
        }

        with patch(_MAX_STORED_SAMPLES_PATH, 3):
            store = FitorbHistoryStore(object(), "entry-id")
            await store.async_load()
            await store.async_record_relay_batch(
                _batch(
                    _sample(
                        "oldest-valid",
                        timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
                    ),
                    _sample(
                        "middle-valid",
                        timestamp=datetime(2026, 7, 3, 9, 56, tzinfo=UTC),
                    ),
                    _sample(
                        "newer-valid",
                        timestamp=datetime(2026, 7, 3, 9, 57, tzinfo=UTC),
                    ),
                    _sample(
                        "newest-valid",
                        timestamp=datetime(2026, 7, 3, 9, 58, tzinfo=UTC),
                    ),
                ),
                datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
            )

        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        relay_samples = persisted["relay"]["samples"]
        assert set(relay_samples) == {
            "middle-valid",
            "newer-valid",
            "newest-valid",
        }
        assert persisted["relay"]["last_sample"] == "2026-07-03T09:58:00+00:00"

    async def test_record_relay_batch_prunes_future_malformed_before_valid(
        self,
    ) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        _FakeStore._saved["fitorb_history_entry-id"] = {
            "relay": {
                "last_upload": "2026-07-03T10:00:00+00:00",
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "samples": {
                    "malformed-future": {
                        "sample_id": "malformed-future",
                        "ring_id": "",
                        "metric": "heart_rate",
                        "timestamp": "2026-07-05T09:55:00+00:00",
                        "value": 72,
                        "source": "android_relay",
                        "captured_at": "2026-07-05T09:55:05+00:00",
                        "protocol_version": 1,
                    }
                },
            }
        }

        with patch(_MAX_STORED_SAMPLES_PATH, 2):
            store = FitorbHistoryStore(object(), "entry-id")
            await store.async_load()
            await store.async_record_relay_batch(
                _batch(
                    _sample(
                        "older-valid",
                        timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
                    ),
                    _sample(
                        "newer-valid",
                        timestamp=datetime(2026, 7, 4, 9, 55, tzinfo=UTC),
                    ),
                ),
                datetime(2026, 7, 4, 10, 1, tzinfo=UTC),
            )

        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        relay_samples = persisted["relay"]["samples"]
        assert set(relay_samples) == {"older-valid", "newer-valid"}
        assert persisted["relay"]["last_sample"] == "2026-07-04T09:55:00+00:00"

    async def test_record_relay_batch_prunes_oversized_future_string_value_first(
        self,
    ) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        _FakeStore._saved["fitorb_history_entry-id"] = {
            "relay": {
                "last_upload": "2026-07-03T10:00:00+00:00",
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "samples": {
                    "oversized-future": {
                        "sample_id": "oversized-future",
                        "ring_id": "AA:BB:CC:DD:EE:FF",
                        "metric": "heart_rate",
                        "timestamp": "2026-07-05T09:55:00+00:00",
                        "value": "v" * (MAX_RELAY_VALUE_STRING_LENGTH + 1),
                        "source": "android_relay",
                        "captured_at": "2026-07-05T09:55:05+00:00",
                        "protocol_version": 1,
                    }
                },
            }
        }

        with patch(_MAX_STORED_SAMPLES_PATH, 2):
            store = FitorbHistoryStore(object(), "entry-id")
            await store.async_load()
            await store.async_record_relay_batch(
                _batch(
                    _sample(
                        "older-valid",
                        timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
                    ),
                    _sample(
                        "newer-valid",
                        timestamp=datetime(2026, 7, 4, 9, 55, tzinfo=UTC),
                    ),
                ),
                datetime(2026, 7, 4, 10, 1, tzinfo=UTC),
            )

        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        relay_samples = persisted["relay"]["samples"]
        assert set(relay_samples) == {"older-valid", "newer-valid"}
        assert persisted["relay"]["last_sample"] == "2026-07-04T09:55:00+00:00"

    async def test_load_ignores_invalid_relay_backlog(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        _FakeStore._saved["fitorb_history_entry-id"] = {
            "relay": {
                "last_upload": "2026-07-03T10:00:00+00:00",
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "backlog": -1,
                "samples": {},
            }
        }

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()

        assert store.relay_backlog is None

    async def test_relay_last_sample_ignores_malformed_future_timestamp(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        _FakeStore._saved["fitorb_history_entry-id"] = {
            "relay": {
                "last_upload": "2026-07-03T10:00:00+00:00",
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "samples": {
                    "sample-heart-1": {
                        "sample_id": "sample-heart-1",
                        "ring_id": "AA:BB:CC:DD:EE:FF",
                        "metric": "heart_rate",
                        "timestamp": "2026-07-03T09:55:00+00:00",
                        "value": 72,
                        "unit": "bpm",
                        "source": "android_relay",
                        "captured_at": "2026-07-03T09:55:05+00:00",
                        "protocol_version": 1,
                    },
                    "bad-future": {
                        "sample_id": "bad-future",
                        "ring_id": "",
                        "metric": "heart_rate",
                        "timestamp": "2026-07-04T09:55:00+00:00",
                        "captured_at": "2026-07-04T09:55:05+00:00",
                        "protocol_version": False,
                    },
                    "missing-value-future": {
                        "sample_id": "missing-value-future",
                        "ring_id": "AA:BB:CC:DD:EE:FF",
                        "metric": "heart_rate",
                        "timestamp": "2026-07-05T09:55:00+00:00",
                        "source": "android_relay",
                        "captured_at": "2026-07-05T09:55:05+00:00",
                        "protocol_version": 1,
                    },
                    "list-value-future": {
                        "sample_id": "list-value-future",
                        "ring_id": "AA:BB:CC:DD:EE:FF",
                        "metric": "heart_rate",
                        "timestamp": "2026-07-06T09:55:00+00:00",
                        "value": [72],
                        "source": "android_relay",
                        "captured_at": "2026-07-06T09:55:05+00:00",
                        "protocol_version": 1,
                    },
                },
            }
        }

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        result = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )

        assert result.duplicates == ("sample-heart-1",)
        assert store.relay_last_sample == datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        assert "bad-future" in persisted["relay"]["samples"]
        assert "missing-value-future" in persisted["relay"]["samples"]
        assert "list-value-future" in persisted["relay"]["samples"]
        assert persisted["relay"]["last_sample"] == "2026-07-03T09:55:00+00:00"

    async def test_record_relay_batch_returns_utc_server_time(self) -> None:
        from custom_components.fitorb.history_store import FitorbHistoryStore

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        result = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 12, 1, tzinfo=timezone(timedelta(hours=2))),
        )

        assert result.server_time.isoformat() == "2026-07-03T10:01:00+00:00"

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
