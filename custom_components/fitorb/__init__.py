from __future__ import annotations

import asyncio
import logging

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import CONF_ADDRESS
from homeassistant.core import HomeAssistant, ServiceCall, SupportsResponse
from homeassistant.exceptions import HomeAssistantError

from .bluetooth import FitorbBleClient
from .const import DOMAIN, PLATFORMS
from .coordinator import FitorbDataUpdateCoordinator
from .relay_api import (
    DATA_RELAY_TOKENS,
    DATA_RELAY_VIEW_REGISTERED,
    FitorbRelaySamplesView,
)
from .relay_auth import FitorbRelayTokenStore

_LOGGER = logging.getLogger(__name__)

SERVICE_CREATE_RELAY_TOKEN = "create_relay_token"
SERVICE_REVOKE_RELAY_TOKEN = "revoke_relay_token"
DATA_RELAY_SETUP_LOCK = "relay_setup_lock"
_RESERVED_DOMAIN_DATA_KEYS = {
    DATA_RELAY_SETUP_LOCK,
    DATA_RELAY_TOKENS,
    DATA_RELAY_VIEW_REGISTERED,
}

_REQUIRED_STRING = vol.All(str, vol.Length(min=1))
_CREATE_RELAY_TOKEN_SCHEMA = vol.Schema(
    {
        vol.Required("entry_id"): _REQUIRED_STRING,
        vol.Required("label"): _REQUIRED_STRING,
    }
)
_REVOKE_RELAY_TOKEN_SCHEMA = vol.Schema(
    {
        vol.Required("token_id"): _REQUIRED_STRING,
    }
)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Set up Fitorb from a config entry."""
    hass.data.setdefault(DOMAIN, {})
    await _async_setup_relay_services(hass)
    entry.async_on_unload(entry.add_update_listener(_async_reload_entry))
    client = FitorbBleClient(hass, entry.data[CONF_ADDRESS])
    coordinator = FitorbDataUpdateCoordinator(hass, entry, client)
    await coordinator.history_store.async_load()
    fallback = coordinator._apply_history_store_summary(
        coordinator.data or coordinator.base_data
    )
    coordinator.async_set_updated_data(
        fallback.with_values(
            available=False,
            last_error="Waiting for first Bluetooth update",
        )
    )
    hass.data[DOMAIN][entry.entry_id] = coordinator
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    refresh_task = hass.async_create_task(_async_refresh_after_setup(coordinator))
    entry.async_on_unload(refresh_task.cancel)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Unload a Fitorb config entry."""
    unload_ok = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unload_ok:
        hass.data[DOMAIN].pop(entry.entry_id, None)
        _async_remove_relay_services_if_unused(hass)
    return unload_ok


async def _async_reload_entry(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Reload Fitorb when config entry options change."""
    await hass.config_entries.async_reload(entry.entry_id)


async def _async_setup_relay_services(hass: HomeAssistant) -> None:
    """Set up relay token services."""
    domain_data = hass.data.setdefault(DOMAIN, {})
    token_store = await _async_get_relay_token_store(hass, domain_data)

    _register_relay_view_once(hass, domain_data)

    if hass.services.has_service(DOMAIN, SERVICE_CREATE_RELAY_TOKEN):
        return

    async def _async_create_relay_token(call: ServiceCall) -> dict[str, str]:
        return await _async_create_relay_token_response(
            token_store,
            domain_data,
            call.data["entry_id"],
            call.data["label"],
        )

    async def _async_revoke_relay_token(call: ServiceCall) -> dict[str, bool]:
        revoked = await token_store.async_revoke_token(call.data["token_id"])
        return {"revoked": revoked}

    hass.services.async_register(
        DOMAIN,
        SERVICE_CREATE_RELAY_TOKEN,
        _async_create_relay_token,
        schema=_CREATE_RELAY_TOKEN_SCHEMA,
        supports_response=SupportsResponse.ONLY,
    )
    hass.services.async_register(
        DOMAIN,
        SERVICE_REVOKE_RELAY_TOKEN,
        _async_revoke_relay_token,
        schema=_REVOKE_RELAY_TOKEN_SCHEMA,
        supports_response=SupportsResponse.ONLY,
    )


async def _async_get_relay_token_store(
    hass: HomeAssistant,
    domain_data: dict[str, object],
) -> FitorbRelayTokenStore:
    """Return the shared relay token store, creating it once."""
    setup_lock = domain_data.get(DATA_RELAY_SETUP_LOCK)
    if not isinstance(setup_lock, asyncio.Lock):
        setup_lock = asyncio.Lock()
        domain_data[DATA_RELAY_SETUP_LOCK] = setup_lock

    async with setup_lock:
        token_store = domain_data.get(DATA_RELAY_TOKENS)
        if isinstance(token_store, FitorbRelayTokenStore):
            return token_store

        token_store = FitorbRelayTokenStore(hass)
        await token_store.async_load()
        domain_data[DATA_RELAY_TOKENS] = token_store
        return token_store


async def _async_create_relay_token_response(
    token_store: FitorbRelayTokenStore,
    domain_data: dict[str, object],
    entry_id: str,
    label: str,
) -> dict[str, str]:
    """Create a relay token service response for a loaded config entry."""
    if entry_id in _RESERVED_DOMAIN_DATA_KEYS or entry_id not in domain_data:
        raise HomeAssistantError(f"Unknown Fitorb config entry ID: {entry_id}")

    created = await token_store.async_create_token(entry_id, label)
    return {
        "token_id": created.record.token_id,
        "token": created.token,
        "entry_id": created.record.entry_id,
        "label": created.record.label,
    }


def _async_remove_relay_services_if_unused(hass: HomeAssistant) -> None:
    """Remove relay services when no Fitorb config entries are loaded."""
    domain_data = hass.data.get(DOMAIN, {})
    if _has_loaded_fitorb_entries(domain_data):
        return

    hass.services.async_remove(DOMAIN, SERVICE_CREATE_RELAY_TOKEN)
    hass.services.async_remove(DOMAIN, SERVICE_REVOKE_RELAY_TOKEN)


def _register_relay_view_once(
    hass: HomeAssistant,
    domain_data: dict[str, object],
) -> None:
    """Register the Android relay ingest view once."""
    if domain_data.get(DATA_RELAY_VIEW_REGISTERED) is True:
        return

    if hass.http is None:
        _LOGGER.debug("HTTP server unavailable; relay ingest view not registered")
        return

    hass.http.register_view(FitorbRelaySamplesView())
    domain_data[DATA_RELAY_VIEW_REGISTERED] = True


def _has_loaded_fitorb_entries(domain_data: dict[str, object]) -> bool:
    """Return whether domain data still contains loaded config entries."""
    return any(key not in _RESERVED_DOMAIN_DATA_KEYS for key in domain_data)


async def _async_refresh_after_setup(
    coordinator: FitorbDataUpdateCoordinator,
) -> None:
    """Refresh Fitorb data without blocking config entry setup."""
    try:
        await coordinator.async_request_refresh()
    except Exception as err:
        _LOGGER.debug("Initial Fitorb refresh after setup failed: %s", err)
