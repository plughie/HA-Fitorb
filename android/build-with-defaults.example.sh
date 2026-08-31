#!/usr/bin/env bash
set -euo pipefail

# Set the token in your shell or CI secret store before running this script.
# Never write a real relay token here: build-time defaults are embedded in the APK.
export FITORB_DEFAULT_HOME_ASSISTANT_URL="${FITORB_DEFAULT_HOME_ASSISTANT_URL:-https://homeassistant.example.com}"
export FITORB_DEFAULT_RING_ID="${FITORB_DEFAULT_RING_ID:-AA:BB:CC:DD:EE:FF}"
export FITORB_ALLOW_CLEARTEXT_HTTP="${FITORB_ALLOW_CLEARTEXT_HTTP:-false}"
: "${FITORB_DEFAULT_RELAY_TOKEN:?Set FITORB_DEFAULT_RELAY_TOKEN in your shell}"
export FITORB_DEFAULT_RELAY_TOKEN

./gradlew assembleDebug
