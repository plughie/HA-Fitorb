from __future__ import annotations

import asyncio
import json
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest import IsolatedAsyncioTestCase
from unittest.mock import Mock, patch

from aiohttp import web
from homeassistant.components.http import KEY_HASS
from homeassistant.const import CONF_ADDRESS, CONF_NAME

import custom_components.fitorb as fitorb_init
from custom_components.fitorb.const import DOMAIN
from custom_components.fitorb.coordinator import FitorbDataUpdateCoordinator
from custom_components.fitorb.models import FitorbData
from custom_components.fitorb.relay import (
    RelayAckResult,
    RelayBatch,
    RelayMetric,
    RelayRejectedSample,
    RelaySample,
)
from custom_components.fitorb.relay_auth import RelayTokenRecord


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


def _token_record(entry_id: str = "entry-id") -> RelayTokenRecord:
    return RelayTokenRecord(
        token_id="token-id",
        entry_id=entry_id,
        label="Pixel 8",
        created_at=datetime(2026, 7, 3, 10, 0, tzinfo=UTC),
    )


class _FakeRequest:
    def __init__(
        self,
        hass: object,
        *,
        authorization: str | None = "Bearer fitorb_relay_test",
        payload: object = None,
        json_error: Exception | None = None,
    ) -> None:
        self.app = {KEY_HASS: hass}
        self.headers = {}
        if authorization is not None:
            self.headers["Authorization"] = authorization
        self._payload = _payload() if payload is None else payload
        self._json_error = json_error

    async def json(self) -> object:
        if self._json_error is not None:
            raise self._json_error
        return self._payload


class _FakeRelayTokenStore:
    def __init__(self, record: RelayTokenRecord | None) -> None:
        self.record = record
        self.validated_tokens: list[str] = []

    async def async_validate_token(self, token: str) -> RelayTokenRecord | None:
        self.validated_tokens.append(token)
        return self.record


class _FakeCoordinator:
    def __init__(self) -> None:
        self.recorded: list[tuple[RelayBatch, datetime]] = []

    async def async_record_relay_batch(
        self,
        batch: RelayBatch,
        received_at: datetime,
    ) -> RelayAckResult:
        self.recorded.append((batch, received_at))
        return RelayAckResult(
            accepted=("sample-heart-1",),
            duplicates=(),
            rejected=(),
            server_time=received_at,
        )


class _FakeHistoryStore:
    def __init__(self) -> None:
        self.last_sync = datetime(2026, 7, 3, 9, 0, tzinfo=UTC)
        self.last_sample_count = 42
        self.first_sample = None
        self.last_sample = None
        self.last_status = "success"
        self.unknown_packets = 0
        self.malformed_packets = 0
        self.sleep_summary = None
        self.relay_last_upload = None
        self.relay_last_sample = None
        self.relay_last_rejected_count = 0
        self.relay_app_version = None
        self.recorded_relay_batches: list[tuple[RelayBatch, datetime]] = []

    async def async_record_relay_batch(
        self,
        batch: RelayBatch,
        received_at: datetime,
    ) -> RelayAckResult:
        self.recorded_relay_batches.append((batch, received_at))
        self.relay_last_upload = received_at
        self.relay_last_sample = max(sample.timestamp for sample in batch.samples)
        self.relay_last_rejected_count = 1
        self.relay_app_version = batch.app_version
        return RelayAckResult(
            accepted=("sample-heart-1",),
            duplicates=(),
            rejected=(RelayRejectedSample("bad-sample", "invalid_metric"),),
            server_time=received_at,
        )


def _hass(
    token_store: _FakeRelayTokenStore,
    *,
    coordinator: _FakeCoordinator | None = None,
) -> SimpleNamespace:
    domain_data: dict[str, object] = {fitorb_init.DATA_RELAY_TOKENS: token_store}
    if coordinator is not None:
        domain_data["entry-id"] = coordinator
    return SimpleNamespace(data={DOMAIN: domain_data})


def _response_json(response: web.Response) -> dict[str, object]:
    body = response.body
    assert isinstance(body, bytes)
    return json.loads(body.decode())


def _relay_sample(
    sample_id: str = "sample-heart-1",
    *,
    ring_id: str = "AA:BB:CC:DD:EE:FF",
) -> RelaySample:
    return RelaySample(
        sample_id=sample_id,
        ring_id=ring_id,
        metric=RelayMetric.HEART_RATE,
        timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
        value=72,
        unit="bpm",
        source="android_relay",
        captured_at=datetime(2026, 7, 3, 9, 55, 5, tzinfo=UTC),
    )


def _relay_batch(
    *samples: RelaySample,
    ring_id: str = "AA:BB:CC:DD:EE:FF",
) -> RelayBatch:
    return RelayBatch(
        relay_id="pixel-8",
        ring_id=ring_id,
        app_version="0.1.0",
        protocol_version=1,
        sent_at=datetime(2026, 7, 3, 10, 0, tzinfo=UTC),
        samples=samples,
    )


class TestFitorbRelaySamplesView(IsolatedAsyncioTestCase):
    async def test_missing_bearer_token_returns_401(self) -> None:
        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        token_store = _FakeRelayTokenStore(_token_record())
        response = await FitorbRelaySamplesView().post(
            _FakeRequest(_hass(token_store), authorization=None)
        )

        assert response.status == 401
        assert token_store.validated_tokens == []

    async def test_invalid_bearer_token_returns_401(self) -> None:
        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        token_store = _FakeRelayTokenStore(None)
        response = await FitorbRelaySamplesView().post(
            _FakeRequest(_hass(token_store), authorization="Bearer fitorb_relay_bad")
        )

        assert response.status == 401
        assert token_store.validated_tokens == ["fitorb_relay_bad"]

    async def test_valid_token_routes_to_matching_coordinator(self) -> None:
        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        received_at = datetime(2026, 7, 3, 10, 2, tzinfo=UTC)
        token_store = _FakeRelayTokenStore(_token_record())
        coordinator = _FakeCoordinator()
        with patch(
            "custom_components.fitorb.relay_api.datetime",
            Mock(now=Mock(return_value=received_at)),
        ):
            response = await FitorbRelaySamplesView().post(
                _FakeRequest(_hass(token_store, coordinator=coordinator))
            )

        assert response.status == 200
        assert _response_json(response) == {
            "accepted": ["sample-heart-1"],
            "duplicates": [],
            "rejected": [],
            "server_time": "2026-07-03T10:02:00Z",
        }
        assert token_store.validated_tokens == ["fitorb_relay_test"]
        assert len(coordinator.recorded) == 1
        batch, recorded_at = coordinator.recorded[0]
        assert batch.relay_id == "pixel-8"
        assert batch.samples[0].sample_id == "sample-heart-1"
        assert recorded_at == received_at

    async def test_invalid_payload_returns_400(self) -> None:
        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        token_store = _FakeRelayTokenStore(_token_record())
        payload = _payload()
        payload["samples"] = "not-a-list"

        response = await FitorbRelaySamplesView().post(
            _FakeRequest(_hass(token_store), payload=payload)
        )

        assert response.status == 400

    async def test_malformed_json_returns_400(self) -> None:
        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        token_store = _FakeRelayTokenStore(_token_record())

        response = await FitorbRelaySamplesView().post(
            _FakeRequest(_hass(token_store), json_error=ValueError("bad json"))
        )

        assert response.status == 400

    async def test_valid_token_with_unloaded_entry_returns_404(self) -> None:
        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        token_store = _FakeRelayTokenStore(_token_record("missing-entry"))
        response = await FitorbRelaySamplesView().post(_FakeRequest(_hass(token_store)))

        assert response.status == 404
        assert _response_json(response)["code"] == "entry_not_loaded"

    async def test_relay_view_registers_once_during_service_setup(self) -> None:
        class FakeRelayTokenStore:
            def __init__(self, hass: object) -> None:
                self.loaded = False

            async def async_load(self) -> None:
                self.loaded = True

        class FakeHttp:
            def __init__(self) -> None:
                self.views: list[object] = []

            def register_view(self, view: object) -> None:
                self.views.append(view)

        class FakeServices:
            def __init__(self) -> None:
                self.registered: set[tuple[str, str]] = set()

            def has_service(self, domain: str, service: str) -> bool:
                return (domain, service) in self.registered

            def async_register(
                self,
                domain: str,
                service: str,
                *args: object,
                **kwargs: object,
            ) -> None:
                self.registered.add((domain, service))

        hass = SimpleNamespace(data={}, http=FakeHttp(), services=FakeServices())

        with patch.object(fitorb_init, "FitorbRelayTokenStore", FakeRelayTokenStore):
            await fitorb_init._async_setup_relay_services(hass)
            await fitorb_init._async_setup_relay_services(hass)

        from custom_components.fitorb.relay_api import FitorbRelaySamplesView

        assert len(hass.http.views) == 1
        assert isinstance(hass.http.views[0], FitorbRelaySamplesView)
        assert hass.data[DOMAIN][fitorb_init.DATA_RELAY_VIEW_REGISTERED] is True

    async def test_relay_token_store_singleton_under_concurrent_setup(self) -> None:
        class FakeRelayTokenStore:
            instances: list[FakeRelayTokenStore] = []
            first_load_started = asyncio.Event()
            release_first_load = asyncio.Event()

            def __init__(self, hass: object) -> None:
                self.index = len(type(self).instances)
                self.loaded = False
                type(self).instances.append(self)

            async def async_load(self) -> None:
                if self.index == 0:
                    type(self).first_load_started.set()
                    await type(self).release_first_load.wait()
                self.loaded = True

        class FakeHttp:
            def __init__(self) -> None:
                self.views: list[object] = []

            def register_view(self, view: object) -> None:
                self.views.append(view)

        class FakeServices:
            def __init__(self) -> None:
                self.handlers: dict[tuple[str, str], object] = {}

            def has_service(self, domain: str, service: str) -> bool:
                return (domain, service) in self.handlers

            def async_register(
                self,
                domain: str,
                service: str,
                handler: object,
                *args: object,
                **kwargs: object,
            ) -> None:
                self.handlers[(domain, service)] = handler

        hass = SimpleNamespace(data={}, http=FakeHttp(), services=FakeServices())

        with patch.object(fitorb_init, "FitorbRelayTokenStore", FakeRelayTokenStore):
            first_setup = asyncio.create_task(
                fitorb_init._async_setup_relay_services(hass)
            )
            await asyncio.wait_for(FakeRelayTokenStore.first_load_started.wait(), 1)
            second_setup = asyncio.create_task(
                fitorb_init._async_setup_relay_services(hass)
            )
            await asyncio.sleep(0)
            FakeRelayTokenStore.release_first_load.set()
            await asyncio.wait_for(asyncio.gather(first_setup, second_setup), 1)

        assert len(FakeRelayTokenStore.instances) == 1
        assert FakeRelayTokenStore.instances[0].loaded is True
        assert (
            hass.data[DOMAIN][fitorb_init.DATA_RELAY_TOKENS]
            is FakeRelayTokenStore.instances[0]
        )
        assert len(hass.http.views) == 1
        assert len(hass.services.handlers) == 2

    async def test_coordinator_records_relay_batch_and_updates_diagnostics(
        self,
    ) -> None:
        entry = SimpleNamespace(
            data={CONF_ADDRESS: "AA:BB:CC:DD:EE:FF", CONF_NAME: "Ring"},
            entry_id="entry-id",
            options={},
            title="Ring",
        )
        store = _FakeHistoryStore()
        coordinator = FitorbDataUpdateCoordinator(
            SimpleNamespace(),
            entry,
            object(),
            history_store=store,
        )
        coordinator.async_set_updated_data(
            FitorbData(
                address="AA:BB:CC:DD:EE:FF",
                name="Ring",
                available=True,
                steps=123,
            )
        )
        sample = RelaySample(
            sample_id="sample-heart-1",
            ring_id="AA:BB:CC:DD:EE:FF",
            metric=RelayMetric.HEART_RATE,
            timestamp=datetime(2026, 7, 3, 9, 55, tzinfo=UTC),
            value=72,
            unit="bpm",
            source="android_relay",
            captured_at=datetime(2026, 7, 3, 9, 55, 5, tzinfo=UTC),
        )
        batch = RelayBatch(
            relay_id="pixel-8",
            ring_id="AA:BB:CC:DD:EE:FF",
            app_version="0.1.0",
            protocol_version=1,
            sent_at=datetime(2026, 7, 3, 10, 0, tzinfo=UTC),
            samples=(sample,),
        )
        received_at = datetime(2026, 7, 3, 10, 2, tzinfo=UTC)

        result = await coordinator.async_record_relay_batch(batch, received_at)

        assert result.accepted == ("sample-heart-1",)
        assert store.recorded_relay_batches == [(batch, received_at)]
        assert coordinator.data is not None
        assert coordinator.data.steps == 123
        assert coordinator.data.last_history_sync == store.last_sync
        assert coordinator.data.last_history_sample_count == 42
        assert coordinator.data.last_history_status == "success"
        assert coordinator.data.last_relay_upload == received_at
        assert coordinator.data.last_relay_sample_time == sample.timestamp
        assert coordinator.data.relay_rejected_samples == 1
        assert coordinator.data.relay_app_version == "0.1.0"
        assert coordinator.data.relay_recently_active is True

    async def test_coordinator_rejects_relay_batch_for_different_ring(self) -> None:
        entry = SimpleNamespace(
            data={CONF_ADDRESS: "AA:BB:CC:DD:EE:FF", CONF_NAME: "Ring"},
            entry_id="entry-id",
            options={},
            title="Ring",
        )
        store = _FakeHistoryStore()
        coordinator = FitorbDataUpdateCoordinator(
            SimpleNamespace(),
            entry,
            object(),
            history_store=store,
        )
        coordinator.async_set_updated_data(
            FitorbData(address="AA:BB:CC:DD:EE:FF", name="Ring", available=True)
        )
        received_at = datetime(2026, 7, 3, 10, 2, tzinfo=UTC)

        result = await coordinator.async_record_relay_batch(
            _relay_batch(
                _relay_sample("foreign-sample", ring_id="11:22:33:44:55:66"),
                ring_id="11:22:33:44:55:66",
            ),
            received_at,
        )

        assert result.accepted == ()
        assert result.duplicates == ()
        assert [(item.sample_id, item.reason) for item in result.rejected] == [
            ("foreign-sample", "ring_id_mismatch")
        ]
        assert result.server_time == received_at
        assert store.recorded_relay_batches == []
        assert coordinator.data is not None
        assert coordinator.data.last_relay_upload is None

    async def test_history_summary_applies_relay_diagnostics_after_reload(
        self,
    ) -> None:
        entry = SimpleNamespace(
            data={CONF_ADDRESS: "AA:BB:CC:DD:EE:FF", CONF_NAME: "Ring"},
            entry_id="entry-id",
            options={},
            title="Ring",
        )
        store = _FakeHistoryStore()
        store.last_sync = None
        store.last_sample_count = 0
        store.last_status = None
        store.relay_last_upload = datetime(2026, 7, 3, 10, 0, tzinfo=UTC)
        store.relay_last_sample = datetime(2026, 7, 3, 9, 55, tzinfo=UTC)
        store.relay_last_rejected_count = 2
        store.relay_app_version = "0.1.0"
        coordinator = FitorbDataUpdateCoordinator(
            SimpleNamespace(),
            entry,
            object(),
            history_store=store,
        )

        recent = coordinator._apply_history_store_summary(
            FitorbData(address="AA:BB:CC:DD:EE:FF", name="Ring"),
            now=datetime(2026, 7, 3, 10, 20, tzinfo=UTC),
        )
        stale = coordinator._apply_history_store_summary(
            recent,
            now=datetime(2026, 7, 3, 10, 31, tzinfo=UTC),
        )

        assert recent.last_relay_upload == store.relay_last_upload
        assert recent.last_relay_sample_time == store.relay_last_sample
        assert recent.relay_rejected_samples == 2
        assert recent.relay_app_version == "0.1.0"
        assert recent.relay_recently_active is True
        assert stale.relay_recently_active is False
