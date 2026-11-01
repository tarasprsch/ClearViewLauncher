# Development and installation

This guide covers building, signing, installing and recovering Clear View Launcher. Run commands from the repository root. For an introduction to the app, see the [README](../README.md).

## Build

Use JDK 17, Android SDK 36 with build tools 35.0.0, and the included Gradle 8.13 wrapper. Set `JAVA_HOME` and `ANDROID_HOME`, or set `sdk.dir` in a local `local.properties` file. All dependency versions are pinned in `gradle/libs.versions.toml`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

`scripts/build.ps1` uses the locally downloaded JDK in `.tools` when `JAVA_HOME` is unset. The test fixture provider exists only in the instrumentation test APK; it is excluded from the launcher release.

For a personal signed release:

```powershell
.\scripts\new-signing-key.ps1 # once only
.\gradlew.bat :app:assembleRelease
```

Back up `.signing/personal.jks` and `keystore.properties` securely together. Both are excluded from source control. Never regenerate the key for an update. Without `keystore.properties`, Gradle produces an unsigned release. The debug key is for testing only.

## Install and update

1. Enable developer options and USB/network debugging on the TV; authorize your computer. Pair network ADB through the device's own settings where required.
2. Record the existing default Home component before changing it: `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`.
3. Install: `adb install -r app/build/outputs/apk/release/ClearViewLauncher-0.7.10-release.apk`. Use `app/build/outputs/apk/debug/ClearViewLauncher-0.7.10-debug.apk` for debug testing. Filenames include the application version automatically.
4. Open **Clear View Launcher** from your current launcher. Go to **Settings > Channel access** and approve the system permission prompt. Then use **Settings > Channel lists** to select rows grouped by their source app. No rows are automatically selected.
5. Use **Settings > Use as default launcher** when the system Home role is available. Some devices instead offer a default Home setting or a Home-button chooser. Device firmware can prevent changing Home.

For devices supporting it, explicitly select Home with:

```powershell
adb shell cmd package set-home-activity dev.personal.tvlauncher/.home.MainActivity
```

If the permission prompt is unavailable despite the app requesting it, inspect device permissions. On compatible firmware, this explicit grant can be used:

```powershell
adb shell pm grant dev.personal.tvlauncher android.permission.READ_TV_LISTINGS
```

Updates use `adb install -r` with a higher version code and the same signing key. The application ID remains `dev.personal.tvlauncher` for update compatibility. Local preferences remain on each device. Debug and personal-release keys differ: switching between them requires uninstalling and loses local settings.

## Remote and recovery

- D-pad moves across cards and between rows. OK opens content. Home and All apps have no long-press editing.
- **Settings > Apps & favourites** shows image tiles for apps on Home and available apps. Press OK on an available app and choose **Add to favourites**; saved apps offer removal and reordering.
- **Settings > Channel lists** shows selected lists in Home order and available lists grouped by app. Use Add, the up/down arrow icons and Remove. All selected nonempty lists appear on Home; missing and empty lists stay saved until restored or explicitly removed.
- **Channel access** disables once access is granted. **Use as default launcher** disables when Clear View is selected and explains the saved Home selection; some TV firmware still sends Home presses to its system launcher.
- Back closes dialogs/settings. At Home it leaves the launcher visible.
- A failed content launch offers an explicit **Open source app** action.
- Artwork uses asynchronous bounded caches and card-sized requests. Titles remain visible on missing/inaccessible media or network failure. Navigation and app launching do not require network access.

Provider-supplied HTTP artwork is explicitly permitted alongside HTTPS because source hosts are not known in advance. This app makes no account or playback requests. The policy follows Android's [cleartext configuration](https://developer.android.com/privacy-and-security/security-config#CleartextTrafficPermitted).

Known limitations and recovery guidance are recorded in [verification.md](verification.md#remaining-checks-and-limitations).

To roll back, restore the Home component recorded before installation using `adb shell cmd package set-home-activity PACKAGE/ACTIVITY`, or choose the original Home in device settings. Press Home and confirm the original launcher works before uninstalling Clear View Launcher. Uninstall: `adb uninstall dev.personal.tvlauncher`. This app never disables or uninstalls the stock launcher.


## Home-button workaround on the tested G1

On the SDMC G1 running the tested Android 14 firmware, Google Home has a higher intent priority than installed launchers. The Home role can name Clear View while the Home button still opens Google. Reapplying the default launcher alone did not resolve this.

After selecting Clear View as the default launcher, the tested device workaround is to disable the Google launcher for user 0. The package stays installed. These package names are specific to the tested TV; verify them before using these commands on another device.

```powershell
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

To restore Google Home:

```powershell
adb shell pm enable --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

The application never disables another launcher itself. Device changes are manual and optional. Keep ADB available when testing Home-button behaviour. Re-enable the original launcher before uninstalling Clear View.
