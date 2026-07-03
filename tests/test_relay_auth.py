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
