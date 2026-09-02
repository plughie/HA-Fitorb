# Fitorb Relay for iOS

The iOS companion collects battery, activity, live heart rate, SpO2, stress,
and cached sleep summaries from a compatible COLMI ring and uploads them to the
same Home Assistant relay endpoint as the Android app.

iOS does not permit this app to run continuously in the background. Once setup
is complete, the app sends when it is opened and repeats at the configured
interval while it remains in the foreground. The **Send** button starts the same
operation manually. The dashboard records how many samples each operation sent
and how Home Assistant classified them.

## Build

Open `FitorbRelay.xcodeproj` in Xcode, select your development team and iPhone,
then build and run. The deployment target is iOS 16.0. Bluetooth permission is
requested by iOS on first use.

## Apple Health

In setup or Settings, enable **Save ring data to Apple Health** and approve the
requested write access. The app writes heart rate, blood oxygen, completed-day
steps/distance/active energy, and completed sleep stages. Daily activity is
written only after the day has ended so repeated cumulative ring snapshots do
not inflate Health totals. Stress is not exported because HealthKit has no
direct stress type. Apple Health is optional; Home Assistant uploads continue
if it is disabled or unavailable.

During setup, select the discovered ring and enter the ring ID used by the Home
Assistant integration (normally its Bluetooth MAC address), Home Assistant URL,
relay token, and a distinct relay ID such as `ios-iphone`.

The project permits HTTP so it can reach a Home Assistant server on a trusted
local network. Prefer HTTPS whenever the server is reachable outside that
network because relay tokens are credentials.

## Optional build-time defaults

For repeated clean-install testing, the same fields can be supplied as Xcode
build settings:

- `FITORB_DEFAULT_HOME_ASSISTANT_URL`
- `FITORB_DEFAULT_RELAY_TOKEN`
- `FITORB_DEFAULT_RELAY_ID`
- `FITORB_DEFAULT_RING_ID`

Their committed values are empty. Values entered and saved in the app take
precedence over build-time defaults and survive installing a newer build with
the same bundle identifier. A relay token compiled into an app can be extracted
from that build, so use only a relay-scoped token and never commit it.
