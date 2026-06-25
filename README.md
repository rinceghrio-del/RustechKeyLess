# RUSTECH KEYLESS

## Getting an APK without installing Android Studio (cloud build via GitHub Actions)

If your PC can't spare the memory for Android Studio, you can build the APK entirely in
the cloud, for free, using GitHub:

1. Go to **github.com** → log in (or sign up, it's free) → **New repository** →
   name it `RustechKeyLess` → Create (public or private, either works).
2. On the repo page, click **Add file → Upload files**, then drag the *entire*
   `RustechKeyLess` folder (all of it, including the hidden `.github` folder) into the
   browser window. Commit the upload.
   - Tip: if your browser doesn't show `.github` when dragging, that's just folder
     visibility on your OS — show hidden files/folders first, or upload that one file
     manually afterward via "Add file → Create new file" and paste in the same path
     `.github/workflows/build-apk.yml`.
3. Click the **Actions** tab on your repo → you should see "Build debug APK" →
   click **Run workflow** (the manual trigger button) → wait 3-5 minutes.
4. Once it finishes (green check), click into that run → scroll down to
   **Artifacts** → download `RustechKeyLess-debug-apk`. It's a zip containing
   `app-debug.apk`.
5. Send that APK to your phone (Drive, USB, anything), tap it to install. You may need
   to allow "Install unknown apps" for whatever app you used to open it.

This produces a **debug** build — auto-signed with Gradle's default debug key, totally
fine for testing on your own device.


Android control-center app for your BLE iBeacon keyless entry system. Two tabs in one app:

- **BROADCAST** — turns this phone into the iBeacon your ESP32 listens for. Set the
  UUID/Major/Minor, hit the switch, and it keeps broadcasting in the background via a
  foreground service (survives the app being minimized).
- **SCOPE** — a live radar view + list of nearby iBeacons, with RSSI and an estimated
  distance in meters. Use this to test/tune the RSSI threshold on your ESP32 relay logic
  before relying on it for real.

## Opening the project

1. Open Android Studio → **Open** → select the `RustechKeyLess` folder.
2. Let Gradle sync (Android Studio will offer to generate the Gradle wrapper
   automatically on first sync if it's missing — just accept it).
3. Plug in your phone (USB debugging on) or use an emulator with Bluetooth support
   (BLE advertising needs a real phone — emulators generally can't transmit BLE).
4. Run ▶. On first launch it'll ask for Bluetooth + notification permissions.

## Recommended setup to match your ESP32 system

Keep the **same UUID** across all your authorized phones, and only change
**Major/Minor per phone** — that's exactly the iBeacon-native way to tell multiple
authorized devices apart while your ESP32 still recognizes "this is a Rustech beacon."
For example:

| Phone | UUID (same for all) | Major | Minor |
|---|---|---|---|
| Rusty's phone | `8ec76ea3-6668-48da-9866-75be8bc86f4d` | 1 | 1 |
| 2nd authorized phone | same | 1 | 2 |

On the ESP32 side, just filter scan results by that UUID (and optionally Major/Minor if
you want per-device relay logic), then act on RSSI the same way you already do.

## Notes / things you might want to tweak

- **Default UUID** lives in `Prefs.kt` — change `DEFAULT_UUID` to your own value, or just
  use the in-app "Generate random UUID" button once and reuse it on every phone.
- **Distance formula** (`IBeacon.estimatedDistanceMeters()` in `BeaconUtils.kt`) is the
  standard iBeacon path-loss approximation — it's a rough "near/far" signal, not a tape
  measure. RSSI is noisy indoors.
- **Permissions**: Android 12+ uses `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` /
  `BLUETOOTH_CONNECT`; Android 11 and below falls back to `ACCESS_FINE_LOCATION` (a BLE
  scan quirk on older Android, not actually used for location here — that's why the
  `neverForLocation` flag is set on `BLUETOOTH_SCAN`).
- **Background broadcasting**: handled by `BeaconAdvertiseService`, a foreground service
  with a persistent notification (required by Android so the OS doesn't kill it).
- The phone must support BLE peripheral/advertising mode to broadcast (almost all phones
  from the last several years do — if "Broadcasting" fails immediately, that's the most
  likely cause).

## Project layout

```
app/src/main/java/com/rustech/keyless/
  MainActivity.kt            - UI glue: tabs, permissions, scan loop
  BeaconAdvertiseService.kt  - foreground service that broadcasts the iBeacon
  BeaconUtils.kt             - iBeacon packet build/parse + distance estimate
  ScopeView.kt               - custom radar canvas view
  BeaconAdapter.kt           - RecyclerView adapter for the scanned beacon list
  Prefs.kt                   - SharedPreferences for UUID/Major/Minor/label
```
