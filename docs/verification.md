# Verification

Latest recorded checks: 2026-11-02, Windows, JDK 17, Gradle 8.13, Android SDK 36/build tools 35.0.0. The physical test device is a G1 running Android 14 (API 34).

## Automated checks

| Check | Last run | Result |
| --- | --- | --- |
| Host unit suite | 0.8.0 | 104 tests across 16 suites; no failures, errors or skips. |
| Caption, settings and customization suites | 0.8.0 | 46 tests across three suites; no failures, errors or skips. |
| Signed release build | 0.8.0 | APK assembled and verified with the original signer. |
| Debug build | 0.7.10 | APK assembled successfully. |
| Debug lint | 0.8.0 | 0 errors, 14 warnings. |
| Instrumentation APK assembly | 0.7.10 | Compiled successfully; connected tests remain unrun. |

The host suite covers provider ownership/parsing, stable identities and ordering, permission/read failures, real DataStore persistence, intent validation, local HTTP artwork and image recycling, D-pad/focus restoration, Settings edits and Ukrainian localization. UI regressions cover viewport clipping, captions on every Home row, matching compact Home/editor tiles, banner/icon fallbacks on all app screens, neighbour overlap without reflow, foreground borders over opaque artwork, font bounds for two-line labels, saved Home-role status, initial selection after delayed preferences, System settings placement and its standard intent, and system date/time formatting independent of interface language. Home customization regressions cover hidden Apps across populated/empty/missing/denied/loading channel states, inert date navigation, responsive grid wrapping and stable app focus across reflow, six saved gradients, actual visibility/layout control writes, legacy preference defaults and DataStore recreation. Review regressions retain saved app anchors while preferences load, preserve channel-row identity when sources reuse internal item IDs, and restore chooser focus after a refresh and cancellation. Date/time cases include US/UK defaults, Ukrainian long dates, live 12/24-hour changes and system locale changes. New regressions cover Home row/grid horizontal edge containment (including single-item rows), date-to-Settings navigation, the bottom two-column Options section, and its focus retention through content refresh. The existing favourite-editor lifecycle regression follows the new section order while retaining saved app-row positions. Square, widescreen and extra-wide channel cards have matching neighbour overlap within 2dp, preserve image proportions and keep their neighbours stationary. Home-intent regressions reset selection in Apps row/grid modes from cards, header controls and secondary pages, cover All apps/hidden Apps/empty and denied channels, and verify that delayed preferences or channel data do not restore the old selection. Ordinary refreshes retain the selected card, and the lifecycle test continues to reject transient Settings focus. All existing Back/refresh cases also pass in the current suite. Host graphics use Robolectric shadows and do not establish native rendering performance.

Final build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --no-daemon --console plain
```

Connected tests use `:app:connectedDebugAndroidTest` and require a matching debug target/test installation. The TV's personal signed release was preserved rather than uninstalled to switch signing keys. The preceding 0.8.0 Home-button selection build was installed successfully. The latest Options-order update is verified locally and has not been installed on the TV. No TV checks were performed for this update.

## Current signed release

- APK: `app/build/outputs/apk/release/ClearViewLauncher-0.8.0-release.apk`; version code **21**, application ID `dev.personal.tvlauncher`.
- Size: **2,319,084 bytes**.
- SHA-256: `c4425ef9ca99121519317a4dd9facee9cdae89f004a681183c84fd080e5e4787`.
- `apksigner verify --verbose --print-certs`: verifies with APK signature scheme v2 and the original RSA-3072 signer.
- Signing certificate SHA-256: `f0216e56a56d5443a5d040900eae7b59a9b8b48dadc458d6e22516cd3d3724e1`.

## Device checks

The preceding 0.8.0 build installed successfully with `adb install -r`. Installation only was performed, with no device checks or app launch afterward.

Version 0.7.11 installed successfully as a same-key release update. That deployment performed installation only, with no device checks or app launch afterward.

Clear View Launcher 0.7.10 installed successfully as a same-key release update. Earlier inspection confirmed version code 19 and the unchanged application ID. Native Settings checks verified the renamed Use as default launcher action, its disabled state while selected, its saved-selection explanation, and removal of Refresh content from the main Settings page. Content refresh remains on Channel lists. Subsequent deployments performed installation only, as requested.

The focus regression reproduced a transient Settings selection during content refresh before the fix and passes with an unstyled fallback-focus container. A native three-Home-press trace recorded no header-button focus events after the fix. The end-to-end focus check remains incomplete: its selected-channel retention assertion failed because focus ended on an app panel. Further TV checks were stopped at the user's request.

The preceding date/time check verified the system Ukrainian long date and default 24-hour time. Host regressions also exercise different interface/system languages. Earlier UI captures show the former header gear icon (now moved to Settings > Device), enlarged app/content focus with foreground outlines, overlapping neighbours and complete two-line titles. Full Settings navigation smoke remains partial: an earlier run stopped at its expected Favourite-heading assertion.

Earlier native checks verified the full-width banner in the stock launcher, shared secondary-page titles, the persistent clock/settings header, grouped channel management, image-based favourites and the renamed favourite heading. Both English and Ukrainian have been observed on the TV. The system-settings action opened `com.android.tv.settings`; same-key updates preserved four favourites and saved channel selections. No automated smoke check edited those choices.

On the tested SDMC G1 firmware, Clear View held the Home role and saved preferred activity, but Google Home still resolved first with priority 2 versus Clear View's 0. Reapplying the default through ADB did not change that. At the user's explicit request, Google's launcher was disabled for user 0 while remaining installed. Home then opened Clear View from TV system Settings. The application does not disable launchers itself; the manual workaround and recovery commands are in the [development guide](development.md#home-button-workaround-on-the-tested-g1).

The requested reference screenshot is saved as [main-screen.png](main-screen.png). Diagnostic screenshots and build logs remain local in ignored `.tools`; they are not required to build the project. Review findings and user-reported focus/scrolling bugs have regression coverage; the latest review identified focus-restoration gaps that were reproduced in tests and fixed with passing regression tests.

## Remaining checks and limitations

- Physical-device review of the new date focus, background chooser and Apps visibility/grid controls; this release was verified locally only at the user's request.
- Connected instrumentation tests and compatibility on a second Android TV device.
- Grant/deny/revoke permission and verify preferences survive on hardware.
- Exhaustive playback/intent validation across source apps, missing/uninstalled sources and offline/inaccessible artwork on hardware.
- Preference persistence through process termination and reboot; Home-button behaviour and rollback on each device.
- Overscan, font scaling, remote/dialog coverage and measured cold startup, memory and scrolling performance. ADB launch timing alone is not a performance benchmark.
- After an exceptional app-discovery or stored-preference read error, leave and return to Home to restart observation; The Channel lists refresh action alone cannot restart a terminated stream. Saved preferences remain intact.
- Focused and pressed controls currently share their fill; separate pressed feedback remains optional polish.
