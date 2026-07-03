# Fitorb Android Relay Design

## Goal

Add a mobile relay path for Fitorb/Colmi-compatible smart rings so Home
Assistant can receive a continuous, timestamped data stream even when the ring
is away from the Home Assistant Bluetooth adapter for multiple days.

The relay is not meant to be a full health dashboard. It is a small Android
collector that reads the ring conservatively over BLE, persists samples locally,
and uploads confirmed batches to the existing Home Assistant custom integration
over the user's HTTPS endpoint.

## Context

The current Home Assistant integration reads the ring directly over BLE GATT.
That works only when the ring is close to a Home Assistant Bluetooth adapter or
an active GATT-capable proxy. Passive Bluetooth proxies and the Home Assistant
mobile companion app do not act as a generic GATT relay for this device.

The user wants a continuous history in Home Assistant while traveling, with an
Android-only solution, direct HTTPS access to Home Assistant, configurable sync
intervals, and explicit care for the ring battery.

## Recommended Approach

Build two cooperating pieces:

1. A native Android application named `Fitorb Relay`.
2. A push-ingest mode in the existing `fitorb` Home Assistant integration.

The Android app owns BLE communication while mobile. It periodically wakes,
scans briefly for the ring, connects only when the ring is visible, reads new
samples since the last sync cursor with a small overlap, stores them in a local
queue, uploads them to Home Assistant, and marks samples as delivered only after
Home Assistant acknowledges them.

Home Assistant remains the source of truth. It receives structured batches,
deduplicates samples, persists them in the Fitorb history store, and exposes
diagnostic entities showing relay health and data freshness.

## Alternatives Considered

### Direct Home Assistant BLE Only

Direct BLE is already implemented and remains useful at home, but it cannot
solve multi-day travel unless the ring returns to Home Assistant Bluetooth
range before its internal cache expires.

### ESPHome Bluetooth Proxies

Active ESPHome Bluetooth proxies can extend in-home range. They do not solve the
mobile travel case unless a proxy is always near the wearer.

### Home Assistant Companion App Or Health Connect

The Home Assistant companion app can publish phone sensors and some Health
Connect data, but it does not read the Fitorb ring protocol. A Health Connect
route would add another lossy synchronization layer and would make per-sample
acknowledgement, deduplication, and protocol diagnostics harder.

### QRing Cloud Or Reverse Engineering

Reverse engineering QRing cloud endpoints might avoid a custom Android app, but
it would be fragile, app-version dependent, and unclear until proven. It also
does not provide the same local-first queue and acknowledgement model.

### Gadgetbridge As Collector

Gadgetbridge supports many Colmi/Yawell rings and could be a useful reference or
fallback. For this project, a focused relay app is still preferred because it can
match the Home Assistant ingest contract directly, keep the UI minimal, and
avoid depending on another application's export behavior.

## Scope

Version 1 should include:

- Native Android app in Kotlin.
- Ring pairing and device selection.
- Configurable ring sync interval, defaulting to 10 minutes.
- Direct HTTPS Home Assistant endpoint configuration.
- Relay token configuration.
- Local SQLite/Room queue.
- Conservative BLE scan/connect/read/disconnect cycle.
- Upload retry independent of BLE retry.
- Batch upload with per-sample acknowledgement.
- Home Assistant ingest endpoint in the existing `fitorb` integration.
- Relay token creation, storage, validation, and revocation.
- Deduplication and sample persistence in the Fitorb history store.
- Diagnostic entities for relay freshness, last upload, rejected samples, and
  backlog.

## Deferred Scope

These are intentionally out of scope for the first relay version:

- iOS support.
- A full mobile health dashboard.
- Cloud service operated by this project.
- QRing cloud integration.
- Continuous BLE connection.
- Manual live health measurement loops.
- Firmware updates or ring configuration management.
- Direct editing of old Home Assistant recorder state rows.
- Long-term statistics publishing until timestamp and unit semantics are proven
  from real relay data.

## System Architecture

```text
Fitorb Ring
  -> Android BLE collector
  -> local Android sample queue
  -> HTTPS batch upload
  -> Home Assistant Fitorb relay endpoint
  -> Fitorb history store
  -> sensors, binary sensors, and diagnostics
```

The direct Home Assistant BLE path remains available as a fallback and for local
debugging. When mobile relay mode is enabled, the integration should avoid
aggressive BLE polling that would compete with the Android app.

## Android Application

The Android app should be a small operational tool:

- A setup screen for selecting the ring and entering the Home Assistant URL.
- A token screen or setup step for the relay token.
- A sync interval setting with common presets: 1, 5, 10, 15, 30, and 60 minutes.
- A status screen showing service state, last successful ring sync, last
  successful upload, pending queue size, ring battery, and last error.
- A foreground service notification while relay mode is enabled.

The app should use Android background primitives conservatively:

- A foreground service for active BLE work, using the connected-device service
  type where applicable.
- Scheduled work for periodic sync and upload retries.
- Android BLE permissions appropriate to the device OS version, including
  Bluetooth scan/connect permissions on modern Android.

## Ring Battery Protection

The relay must never use a permanent BLE connection in normal operation.

Each ring sync cycle should:

1. Start a short filtered scan for the configured ring.
2. Skip the cycle if the ring is not visible.
3. Connect only after a matching scan result.
4. Read only the data needed since the last cursor plus a small overlap.
5. Disconnect immediately after the read finishes or times out.

Recommended defaults:

- Ring sync interval: 10 minutes.
- Scan window: 15-20 seconds.
- Connect/read window: 20-30 seconds.
- Retries per cycle: 1.
- Backoff after failures: 10, 20, 40, then 60 minutes.
- Low ring battery behavior: stretch sync interval and avoid optional reads.

Upload retries must not trigger additional BLE reads. If Home Assistant is
temporarily unreachable, the app should continue to preserve local samples and
retry upload from the queue.

## Data Model

Each sample uploaded by the app should be stable and idempotent.

```text
RelaySample
  sample_id: stable hash of ring_id, metric, timestamp, source, and value kind
  ring_id: configured ring identifier
  metric: steps | calories | distance | heart_rate | spo2 | stress |
          sleep_stage | sleep_summary | battery | charging
  timestamp: original sample time in UTC
  local_date: ring-local date when relevant
  value: int | float | string | bool
  unit: optional unit string
  source: android_relay
  captured_at: app receipt time in UTC
  uploaded_at: optional app upload time in UTC
  raw_hex: optional debug payload, disabled by default
  protocol_version: relay protocol version
```

The app should also maintain a sync cursor per packet family where the ring
protocol supports it. Because the protocol may return overlapping historical
data, Home Assistant must deduplicate samples even when the app also attempts to
avoid duplicates locally.

## Home Assistant Ingest API

Add a versioned endpoint to the `fitorb` integration, for example:

```text
POST /api/fitorb/relay/v1/samples
Authorization: Bearer <relay-token>
Content-Type: application/json
```

The request body should contain:

```text
{
  "relay_id": "android-device-id-or-generated-id",
  "ring_id": "ring-id",
  "app_version": "x.y.z",
  "protocol_version": 1,
  "sent_at": "UTC timestamp",
  "samples": [...]
}
```

The response should acknowledge each sample:

```text
{
  "accepted": ["sample-id"],
  "duplicates": ["sample-id"],
  "rejected": [
    {"sample_id": "sample-id", "reason": "invalid_metric"}
  ],
  "server_time": "UTC timestamp"
}
```

Duplicates count as delivered from the app's perspective. Rejected samples stay
available locally with an error state so they can be inspected but should not be
retried forever without change.

## Authentication And Security

The Android app should not store a normal Home Assistant long-lived access token
for this relay path.

The integration should manage relay-specific tokens:

- Token is scoped only to Fitorb relay ingest.
- Token can be created and revoked per Android device.
- Token is stored hashed in Home Assistant.
- App stores the token in Android encrypted storage.
- Endpoint rejects requests without HTTPS in user-facing setup validation,
  except for local development.
- Request size and sample count limits protect Home Assistant from accidental
  large uploads.

The token can be entered manually in version 1. A QR setup flow can be added
later if the basic path proves stable.

## History Store And Home Assistant Entities

Version 1 should store relay samples in the existing Fitorb history store or a
compatible extension of it. It should not write old Home Assistant recorder
state rows directly.

The integration should expose:

- Latest metric sensors using the newest accepted sample per metric.
- Diagnostic sensor: last mobile relay upload.
- Diagnostic sensor: last mobile relay sample time.
- Diagnostic sensor: pending or suspected gap duration.
- Diagnostic sensor: last rejected sample count.
- Diagnostic sensor: relay app version.
- Binary sensor: mobile relay recently active.

Long-term statistics publishing should be a follow-up after sample semantics,
units, and timestamp behavior have been validated against real hardware.

## Error Handling

- Ring not visible: skip BLE read, increase BLE backoff, keep upload retries
  independent.
- BLE connect failure: one retry in the same cycle, then backoff.
- Partial ring response: persist successfully parsed samples and record a
  partial sync status.
- No mobile internet: keep samples queued locally.
- Home Assistant unreachable: retry upload without waking the ring again.
- Unauthorized upload: stop upload retries until credentials are changed.
- Duplicate sample: acknowledge as duplicate and mark delivered in the app.
- Invalid sample: reject with a reason and keep the app-side row for inspection.
- Home Assistant restart during upload: app retries the same batch safely.

## Testing Strategy

Home Assistant tests should cover:

- Relay token validation and revocation.
- Ingest endpoint request validation.
- Per-sample accepted, duplicate, and rejected responses.
- Deduplication across overlapping batches.
- History store writes from relay samples.
- Diagnostic entity updates after successful and failed uploads.

Android tests should cover:

- Queue insert, dedupe, upload, acknowledgement, and retry state transitions.
- Sync interval and backoff calculations.
- Upload retry without triggering BLE reads.
- Token storage abstraction.
- Handling partial acknowledgement responses.

Manual validation should include:

- Ring in range with 1, 5, 10, 15, 30, and 60 minute intervals.
- Ring absent for several sync windows.
- Home Assistant HTTPS outage followed by recovery.
- Mobile data outage followed by recovery.
- Low ring battery behavior.
- Multi-day travel simulation with app queue review and Home Assistant ingest.

## Acceptance Criteria

The design is ready for implementation when:

- The Android app can collect ring samples on a configurable schedule without a
  permanent BLE connection.
- Failed uploads do not cause extra ring wakeups.
- Home Assistant accepts batch uploads with relay-scoped authentication.
- Samples are deduplicated by stable sample identity.
- The app treats both accepted and duplicate samples as delivered.
- Home Assistant exposes the latest mobile relay data and relay diagnostics.
- A multi-day absence from Home Assistant Bluetooth range can still result in a
  complete timestamped history, limited only by what the ring and app actually
  collected.

