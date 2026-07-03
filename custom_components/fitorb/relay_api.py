from __future__ import annotations

from datetime import UTC, datetime
from http import HTTPStatus
from typing import Protocol, cast

from aiohttp import web
from homeassistant.components.http import KEY_HASS, HomeAssistantView

from .const import DOMAIN
from .relay import RelayAckResult, RelayBatch, parse_relay_batch, relay_ack_to_json

DATA_RELAY_TOKENS = "relay_tokens"
DATA_RELAY_VIEW_REGISTERED = "relay_view_registered"
MAX_RELAY_BATCH_SAMPLES = 500


class _RelayCoordinator(Protocol):
    async def async_record_relay_batch(
        self,
        batch: RelayBatch,
        received_at: datetime,
    ) -> RelayAckResult:
        """Persist one relay batch and return the acknowledgement."""


class _RelayTokenStore(Protocol):
    async def async_validate_token(self, token: str) -> object | None:
        """Return relay token metadata when the token is valid."""


class FitorbRelaySamplesView(HomeAssistantView):
    """Receive Android relay sample batches."""

    url = "/api/fitorb/relay/v1/samples"
    name = "api:fitorb:relay:v1:samples"
    requires_auth = False

    async def post(self, request: web.Request) -> web.Response:
        """Handle one relay sample upload."""
        token = _extract_bearer_token(request.headers.get("Authorization"))
        if token is None:
            return self.json_message(
                "Unauthorized",
                HTTPStatus.UNAUTHORIZED,
                "unauthorized",
            )

        hass = request.app[KEY_HASS]
        domain_data = hass.data.get(DOMAIN)
        if not isinstance(domain_data, dict):
            return self.json_message(
                "Unauthorized",
                HTTPStatus.UNAUTHORIZED,
                "unauthorized",
            )

        token_store = cast(_RelayTokenStore | None, domain_data.get(DATA_RELAY_TOKENS))
        if token_store is None or not callable(
            getattr(token_store, "async_validate_token", None)
        ):
            return self.json_message(
                "Unauthorized",
                HTTPStatus.UNAUTHORIZED,
                "unauthorized",
            )

        token_record = await token_store.async_validate_token(token)
        entry_id = getattr(token_record, "entry_id", None)
        if not isinstance(entry_id, str) or not entry_id:
            return self.json_message(
                "Unauthorized",
                HTTPStatus.UNAUTHORIZED,
                "unauthorized",
            )

        try:
            payload = await request.json()
        except ValueError:
            return self.json_message(
                "Malformed JSON",
                HTTPStatus.BAD_REQUEST,
                "invalid_json",
            )

        if not isinstance(payload, dict):
            return self.json_message(
                "Invalid relay payload",
                HTTPStatus.BAD_REQUEST,
                "invalid_payload",
            )

        try:
            batch = parse_relay_batch(payload, max_samples=MAX_RELAY_BATCH_SAMPLES)
        except ValueError as err:
            return self.json_message(
                str(err),
                HTTPStatus.BAD_REQUEST,
                "invalid_payload",
            )

        coordinator = cast(_RelayCoordinator | None, domain_data.get(entry_id))
        if coordinator is None or not callable(
            getattr(coordinator, "async_record_relay_batch", None)
        ):
            return self.json_message(
                "Fitorb config entry is not loaded",
                HTTPStatus.NOT_FOUND,
                "entry_not_loaded",
            )

        result = await coordinator.async_record_relay_batch(batch, datetime.now(UTC))
        return self.json(relay_ack_to_json(result))


def _extract_bearer_token(authorization: str | None) -> str | None:
    """Return the bearer token from an Authorization header."""
    if not authorization:
        return None

    scheme, separator, token = authorization.partition(" ")
    token = token.strip()
    if separator != " " or scheme.lower() != "bearer" or not token:
        return None
    return token
