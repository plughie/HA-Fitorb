from __future__ import annotations

import logging

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import CONF_ADDRESS
from homeassistant.core import HomeAssistant, ServiceCall, SupportsResponse

from .bluetooth import FitorbBleClient
from .const import DOMAIN, PLATFORMS
from .coordinator import FitorbDataUpdateCoordinator
from .relay_auth import FitorbRelayTokenStore

_LOGGER = logging.getLogger(__name__)

DATA_RELAY_TOKENS = "relay_tokens"
SERVICE_CREATE_RELAY_TOKEN = "create_relay_token"
SERVICE_REVOKE_RELAY_TOKEN = "revoke_relay_token"

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
        hass.data[DOMAIN].pop(entry.entry_id)
    return unload_ok


async def _async_reload_entry(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Reload Fitorb when config entry options change."""
    await hass.config_entries.async_reload(entry.entry_id)


async def _async_setup_relay_services(hass: HomeAssistant) -> None:
    """Set up relay token services."""
    domain_data = hass.data.setdefault(DOMAIN, {})
    token_store = domain_data.get(DATA_RELAY_TOKENS)
    if not isinstance(token_store, FitorbRelayTokenStore):
        token_store = FitorbRelayTokenStore(hass)
        await token_store.async_load()
        domain_data[DATA_RELAY_TOKENS] = token_store

    if hass.services.has_service(DOMAIN, SERVICE_CREATE_RELAY_TOKEN):
        return

    async def _async_create_relay_token(call: ServiceCall) -> dict[str, str]:
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


async def _async_refresh_after_setup(
    coordinator: FitorbDataUpdateCoordinator,
) -> None:
    """Refresh Fitorb data without blocking config entry setup."""
    try:
        await coordinator.async_request_refresh()
    except Exception as err:
        _LOGGER.debug("Initial Fitorb refresh after setup failed: %s", err)
