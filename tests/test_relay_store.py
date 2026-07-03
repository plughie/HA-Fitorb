from __future__ import annotations

import copy
import json
from datetime import UTC, datetime, timedelta, timezone
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
                "last_sample": None,
                "last_rejected_count": 0,
                "app_version": "0.1.0",
                "samples": {"sample-heart-1": "bad-shape"},
            }
        }

        store = FitorbHistoryStore(object(), "entry-id")
        await store.async_load()
        result = await store.async_record_relay_batch(
            _batch(_sample("sample-heart-1")),
            datetime(2026, 7, 3, 10, 1, tzinfo=UTC),
        )

        assert result.accepted == ()
        assert result.duplicates == ("sample-heart-1",)
        persisted = _FakeStore._saved["fitorb_history_entry-id"]
        assert persisted["relay"]["samples"]["sample-heart-1"] == "bad-shape"

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
