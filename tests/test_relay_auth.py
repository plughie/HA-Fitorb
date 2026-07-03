from __future__ import annotations

import asyncio
import copy
import json
from datetime import UTC, datetime
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


class _DelayedFirstSaveStore:
    _saved: dict[str, dict[str, object]] = {}
    _save_count = 0
    first_save_started: asyncio.Event
    release_first_save: asyncio.Event
    first_save_data: dict[str, object]

    def __init__(self, hass: object, version: int, key: str) -> None:
        self.key = key

    @classmethod
    def reset(cls) -> None:
        cls._saved = {}
        cls._save_count = 0
        cls.first_save_started = asyncio.Event()
        cls.release_first_save = asyncio.Event()
        cls.first_save_data = {}

    async def async_load(self) -> dict[str, object] | None:
        data = self._saved.get(self.key)
        if data is None:
            return None
        return json.loads(json.dumps(data))

    async def async_save(self, data: dict[str, object]) -> None:
        data_copy = copy.deepcopy(data)
        type(self)._save_count += 1
        if type(self)._save_count == 1:
            type(self).first_save_data = data_copy
            type(self).first_save_started.set()
            await type(self).release_first_save.wait()
        self._saved[self.key] = data_copy


class _FakeRelayTokenStore:
    def __init__(self) -> None:
        self.created: list[tuple[str, str]] = []

    async def async_create_token(self, entry_id: str, label: str) -> object:
        from custom_components.fitorb.relay_auth import (
            RelayTokenCreated,
            RelayTokenRecord,
        )

        self.created.append((entry_id, label))
        return RelayTokenCreated(
            token="fitorb_relay_test",
            record=RelayTokenRecord(
                token_id="token-id",
                entry_id=entry_id,
                label=label,
                created_at=datetime(2026, 7, 3, 10, 0, tzinfo=UTC),
            ),
        )


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

    async def test_malformed_records_are_ignored_during_validation(self) -> None:
        from custom_components.fitorb.relay_auth import FitorbRelayTokenStore

        _FakeStore._saved["fitorb_relay_tokens"] = {
            "tokens": {
                "bad-shape": "not-a-record",
                "missing-hash": {
                    "token_id": "missing-hash",
                    "entry_id": "entry-id",
                    "label": "Pixel 8",
                    "created_at": "2026-07-03T10:00:00+00:00",
                },
            }
        }

        store = FitorbRelayTokenStore(object())
        await store.async_load()

        assert await store.async_validate_token("fitorb_relay_bad") is None

    async def test_revoke_unknown_token_returns_false(self) -> None:
        from custom_components.fitorb.relay_auth import FitorbRelayTokenStore

        store = FitorbRelayTokenStore(object())
        await store.async_load()

        assert await store.async_revoke_token("missing-token-id") is False

    async def test_revoke_waits_for_pending_create_save(self) -> None:
        from custom_components.fitorb.relay_auth import FitorbRelayTokenStore

        _DelayedFirstSaveStore.reset()
        with patch(
            "custom_components.fitorb.relay_auth.Store",
            _DelayedFirstSaveStore,
        ):
            store = FitorbRelayTokenStore(object())
            await store.async_load()
            create_task = asyncio.create_task(
                store.async_create_token("entry-id", "Pixel 8")
            )
            await asyncio.wait_for(
                _DelayedFirstSaveStore.first_save_started.wait(),
                timeout=1,
            )
            first_tokens = _DelayedFirstSaveStore.first_save_data["tokens"]
            assert isinstance(first_tokens, dict)
            token_id = next(iter(first_tokens))

            revoke_task = asyncio.create_task(store.async_revoke_token(token_id))
            await asyncio.sleep(0)
            _DelayedFirstSaveStore.release_first_save.set()

            created = await asyncio.wait_for(create_task, timeout=1)
            assert created.record.token_id == token_id
            assert await asyncio.wait_for(revoke_task, timeout=1) is True

        persisted = _DelayedFirstSaveStore._saved["fitorb_relay_tokens"]
        assert persisted["tokens"] == {}

    async def test_create_relay_token_response_rejects_invalid_entry_id(self) -> None:
        from homeassistant.exceptions import HomeAssistantError

        from custom_components.fitorb import (
            DATA_RELAY_TOKENS,
            _async_create_relay_token_response,
        )

        token_store = _FakeRelayTokenStore()
        domain_data: dict[str, object] = {DATA_RELAY_TOKENS: token_store}

        with self.assertRaises(HomeAssistantError):
            await _async_create_relay_token_response(
                token_store,
                domain_data,
                "missing-entry",
                "Pixel 8",
            )
        with self.assertRaises(HomeAssistantError):
            await _async_create_relay_token_response(
                token_store,
                domain_data,
                DATA_RELAY_TOKENS,
                "Pixel 8",
            )

        assert token_store.created == []

    async def test_create_relay_token_response_returns_created_token(self) -> None:
        from custom_components.fitorb import _async_create_relay_token_response

        token_store = _FakeRelayTokenStore()
        result = await _async_create_relay_token_response(
            token_store,
            {"entry-id": object()},
            "entry-id",
            "Pixel 8",
        )

        assert result == {
            "token_id": "token-id",
            "token": "fitorb_relay_test",
            "entry_id": "entry-id",
            "label": "Pixel 8",
        }
        assert token_store.created == [("entry-id", "Pixel 8")]
