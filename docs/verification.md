# Verification

Latest recorded checks: 2026-11-02, Windows, JDK 17, Gradle 8.13, Android SDK 36/build tools 35.0.0. The physical test device is a G1 running Android 14 (API 34).

## Automated checks

| Check | Last run | Result |
| --- | --- | --- |
| Host unit suite | 0.7.10 | 74 tests across 14 suites; no failures, errors or skips. |
| Debug and signed release builds | 0.7.10 | Both APKs assembled successfully. |
| Debug lint | 0.7.10 | 0 errors, 21 warnings. |
| Instrumentation APK assembly | 0.7.10 | Compiled successfully; connected tests remain unrun. |

The host suite covers provider ownership/parsing, stable identities and ordering, permission/read failures, real DataStore persistence, intent validation, local HTTP artwork and image recycling, D-pad/focus restoration, Settings edits and Ukrainian localization. UI regressions cover viewport clipping, captions on every Home row, matching compact Home/editor tiles, banner/icon fallbacks on all app screens, neighbour overlap without reflow, foreground borders over opaque artwork, font bounds for two-line labels, saved Home-role status, and system date/time formatting independent of interface language. Date/time cases include US/UK defaults, Ukrainian long dates, live 12/24-hour changes and system locale changes. Host graphics use Robolectric shadows and do not establish native rendering performance.

Final build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --offline --console plain
```

Connected tests use `:app:connectedDebugAndroidTest` and require a matching debug target/test installation. The TV's personal signed release was preserved rather than uninstalled to switch signing keys.

## Current signed release

- APK: `app/build/outputs/apk/release/ClearViewLauncher-0.7.10-release.apk`; version code **19**, application ID `dev.personal.tvlauncher`.
- Size: **2,312,136 bytes**.
- SHA-256: `81c301e7798cba70e3d60c7ebe9427f60ddb9d0f5c14495d4e8ad8837705df48`.
- `apksigner verify --verbose --print-certs`: verifies with APK signature scheme v2 and the original RSA-3072 signer.
- Signing certificate SHA-256: `f0216e56a56d5443a5d040900eae7b59a9b8b48dadc458d6e22516cd3d3724e1`.

## Device checks

Clear View Launcher 0.7.10 installed successfully as a same-key release update. Earlier inspection confirmed version code 19 and the unchanged application ID. Native Settings checks verified the renamed Use as default launcher action, its disabled state while selected, its saved-selection explanation, and removal of Refresh content from the main Settings page. Content refresh remains on Channel lists. The latest deployment performed installation only, as requested.

The focus regression reproduced a transient Settings selection during content refresh before the fix and passes with an unstyled fallback-focus container. A native three-Home-press trace recorded no header-button focus events after the fix. The end-to-end focus check remains incomplete: its selected-channel retention assertion failed because focus ended on an app panel. Further TV checks were stopped at the user's request.

The preceding date/time check verified the system Ukrainian long date and default 24-hour time. Host regressions also exercise different interface/system languages. Earlier UI captures show the gear icon, enlarged app/content focus with foreground outlines, overlapping neighbours and complete two-line titles. Full Settings navigation smoke remains partial: an earlier run stopped at its expected Favourite-heading assertion.

Earlier native checks verified the full-width banner in the stock launcher, shared secondary-page titles, the persistent clock/settings header, grouped channel management, image-based favourites and the renamed favourite heading. Both English and Ukrainian have been observed on the TV. The system-settings action opened `com.android.tv.settings`; same-key updates preserved four favourites and saved channel selections. No automated smoke check edited those choices.

On the tested SDMC G1 firmware, Clear View held the Home role and saved preferred activity, but Google Home still resolved first with priority 2 versus Clear View's 0. Reapplying the default through ADB did not change that. At the user's explicit request, Google's launcher was disabled for user 0 while remaining installed. Home then opened Clear View from TV system Settings. The application does not disable launchers itself; the manual workaround and recovery commands are in the [development guide](development.md#home-button-workaround-on-the-tested-g1).

The requested reference screenshot is saved as [main-screen.png](main-screen.png). Diagnostic screenshots and build logs remain local in ignored `.tools`; they are not required to build the project. Review findings and user-reported focus/scrolling bugs have regression coverage; the latest focused review found no important issues.

## Remaining checks and limitations

- Connected instrumentation tests and compatibility on a second Android TV device.
- Grant/deny/revoke permission and verify preferences survive on hardware.
- Exhaustive playback/intent validation across source apps, missing/uninstalled sources and offline/inaccessible artwork on hardware.
- Preference persistence through process termination and reboot; Home-button behaviour and rollback on each device.
- Overscan, font scaling, remote/dialog coverage and measured cold startup, memory and scrolling performance. ADB launch timing alone is not a performance benchmark.
- After an exceptional app-discovery or stored-preference read error, leave and return to Home to restart observation; The Channel lists refresh action alone cannot restart a terminated stream. Saved preferences remain intact.
- Focused and pressed controls currently share their fill; separate pressed feedback remains optional polish.
