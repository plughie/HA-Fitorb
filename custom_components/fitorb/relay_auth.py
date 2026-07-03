from __future__ import annotations

import asyncio
import hashlib
import hmac
import secrets
import string
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .const import DOMAIN

_STORE_VERSION = 1
_STORE_KEY = f"{DOMAIN}_relay_tokens"
_TOKEN_PREFIX = "fitorb_relay_"
_SHA256_HEX_LENGTH = 64
_HEX_DIGITS = frozenset(string.hexdigits)


@dataclass(frozen=True)
class RelayTokenRecord:
    """Stored metadata for one relay token."""

    token_id: str
    entry_id: str
    label: str
    created_at: datetime


@dataclass(frozen=True)
class RelayTokenCreated:
    """Newly created relay token and its metadata."""

    token: str
    record: RelayTokenRecord


@dataclass(frozen=True)
class _StoredRelayToken:
    record: RelayTokenRecord
    token_hash: str


class FitorbRelayTokenStore:
    """Persist and validate Android relay bearer tokens."""

    def __init__(self, hass: HomeAssistant) -> None:
        self._store: Store[dict[str, Any]] = Store(hass, _STORE_VERSION, _STORE_KEY)
        self._tokens: dict[str, _StoredRelayToken] = {}
        self._lock = asyncio.Lock()

    async def async_load(self) -> None:
        """Load relay tokens from storage."""
        loaded = await self._store.async_load()
        self._tokens = _tokens_from_json(loaded)

    async def async_create_token(
        self,
        entry_id: str,
        label: str,
    ) -> RelayTokenCreated:
        """Create and persist a relay token for a config entry."""
        async with self._lock:
            token_id = _generate_token_id(self._tokens)
            token = f"{_TOKEN_PREFIX}{secrets.token_urlsafe(32)}"
            record = RelayTokenRecord(
                token_id=token_id,
                entry_id=entry_id,
                label=label,
                created_at=datetime.now(UTC),
            )
            self._tokens[token_id] = _StoredRelayToken(
                record=record,
                token_hash=_hash_token(token),
            )
            await self._async_save()
            return RelayTokenCreated(token=token, record=record)

    async def async_validate_token(self, token: str) -> RelayTokenRecord | None:
        """Return token metadata when the relay token is valid."""
        if not token.startswith(_TOKEN_PREFIX):
            return None

        token_hash = _hash_token(token)
        for stored in tuple(self._tokens.values()):
            if not _is_valid_stored_token(stored):
                continue
            if hmac.compare_digest(stored.token_hash, token_hash):
                return stored.record
        return None

    async def async_revoke_token(self, token_id: str) -> bool:
        """Revoke a relay token by token ID."""
        async with self._lock:
            if token_id not in self._tokens:
                return False
            del self._tokens[token_id]
            await self._async_save()
            return True

    async def _async_save(self) -> None:
        """Persist normalized relay token metadata."""
        await self._store.async_save(
            {
                "tokens": {
                    token_id: _stored_token_to_json(stored)
                    for token_id, stored in self._tokens.items()
                    if _is_valid_stored_token(stored)
                }
            }
        )


def _generate_token_id(tokens: dict[str, _StoredRelayToken]) -> str:
    token_id = secrets.token_urlsafe(16)
    while token_id in tokens:
        token_id = secrets.token_urlsafe(16)
    return token_id


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _tokens_from_json(data: object) -> dict[str, _StoredRelayToken]:
    if not isinstance(data, dict):
        return {}

    tokens = data.get("tokens")
    if not isinstance(tokens, dict):
        return {}

    normalized: dict[str, _StoredRelayToken] = {}
    for token_id, value in tokens.items():
        if not isinstance(token_id, str):
            continue
        stored = _stored_token_from_json(token_id, value)
        if stored is not None:
            normalized[token_id] = stored
    return normalized


def _stored_token_from_json(
    token_id: str,
    value: object,
) -> _StoredRelayToken | None:
    if not isinstance(value, dict):
        return None

    value_token_id = value.get("token_id")
    entry_id = value.get("entry_id")
    label = value.get("label")
    token_hash = value.get("token_hash")
    created_at = _parse_datetime(value.get("created_at"))

    if value_token_id != token_id:
        return None
    if not isinstance(entry_id, str) or not entry_id:
        return None
    if not isinstance(label, str) or not label:
        return None
    if not _is_sha256_hexdigest(token_hash):
        return None
    if created_at is None:
        return None

    return _StoredRelayToken(
        record=RelayTokenRecord(
            token_id=token_id,
            entry_id=entry_id,
            label=label,
            created_at=created_at,
        ),
        token_hash=token_hash.lower(),
    )


def _stored_token_to_json(stored: _StoredRelayToken) -> dict[str, str]:
    return {
        "token_id": stored.record.token_id,
        "entry_id": stored.record.entry_id,
        "label": stored.record.label,
        "created_at": stored.record.created_at.astimezone(UTC).isoformat(),
        "token_hash": stored.token_hash,
    }


def _is_valid_stored_token(stored: _StoredRelayToken) -> bool:
    return (
        bool(stored.record.token_id)
        and bool(stored.record.entry_id)
        and bool(stored.record.label)
        and stored.record.created_at.tzinfo is not None
        and _is_sha256_hexdigest(stored.token_hash)
    )


def _is_sha256_hexdigest(value: object) -> bool:
    return (
        isinstance(value, str)
        and len(value) == _SHA256_HEX_LENGTH
        and all(char in _HEX_DIGITS for char in value)
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
