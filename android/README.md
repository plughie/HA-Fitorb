# Fitorb Mobile Relay Android app

## Build-time defaults

The app can prefill its first-run connection settings from environment variables
at build time. Defaults are intentionally empty so builds do not contain a
developer's server address, ring identifier, or credentials.

| Variable | Unset fallback |
| --- | --- |
| `FITORB_DEFAULT_HOME_ASSISTANT_URL` | Empty string |
| `FITORB_DEFAULT_RING_ID` | Empty string |
| `FITORB_DEFAULT_RELAY_TOKEN` | Empty string |
| `FITORB_ALLOW_CLEARTEXT_HTTP` | `false` |

These values are compiled into the app. In particular, a relay token included
this way can be extracted from the APK, so use a relay-scoped token and do not
commit it to this repository.

Release builds require HTTPS by default. Debug/installable builds accept HTTP
for a trusted local Home Assistant network, so a saved local address does not
disable **Send** after an upgrade. To opt a release build into local HTTP, set
`FITORB_ALLOW_CLEARTEXT_HTTP=true` while building. This setting is compiled into
the APK and enables both Android cleartext traffic and the app's URL validation.

For a local debug build, set `FITORB_DEFAULT_RELAY_TOKEN` in your shell, adjust
the non-secret defaults in `build-with-defaults.example.sh` if needed, and run
the script. Do not put a real token in the script. The debug APK is written under
`app/build/outputs/apk/debug/`.

Without build-time overrides, run:

```sh
./gradlew assembleDebug
```

## Ring collection behavior

The relay connects only for a bounded collection cycle. It reads battery,
activity, heart-rate history, live SpO2 and stress, and cached sleep history,
then disconnects before uploading the queued batch.

Live SpO2 and stress measurements use the COLMI start, continue, and stop
sequence. The stop command is sent after a value, timeout, or error so the
optical sensor cannot be left active by an incomplete measurement.

Sleep detection is automatic on compatible rings. No bedtime action is needed
in the app. After the ring closes a sleep session, the next sync retrieves its
summary and awake, light, deep, and REM durations. Activity, heart rate, SpO2,
stress, and a complete staged sleep session have been validated with a COLMI
R12.

After setup, use **Send** to run a collection immediately. The status display
updates after both manual sends and scheduled work with the number of samples
sent and Home Assistant's accepted, duplicate, and rejected counts.

## Health Connect

In **More**, enable **Save ring data to Health Connect** and approve the
requested write permissions. The app writes heart rate, blood oxygen,
completed-day steps/distance/active calories, and completed sleep sessions with
stages. Daily activity is deferred until the day has ended so repeated
cumulative snapshots do not inflate totals. Stress remains Home Assistant-only
because Health Connect has no direct stress record. This feature needs Android
9 or newer with Health Connect available; Android 14 and newer include Health
Connect in the system. Home Assistant uploads continue if health export is off,
unavailable, or fails.
