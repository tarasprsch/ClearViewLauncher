# Architecture

Clear View Launcher is a single-module Kotlin/Android Views Home application for Android TV 14+ (minimum API 34, compile/target API 36). Its application ID is `dev.personal.tvlauncher`. Dependency versions live in [the version catalog](../gradle/libs.versions.toml); build, installation and remote controls are covered in the [development guide](development.md).

## Components

Source packages are under [app/src/main/java/dev/personal/tvlauncher](../app/src/main/java/dev/personal/tvlauncher).

| Component | Responsibility |
| --- | --- |
| `apps/AppsRepository` | Discover enabled, exported TV launch activities; refresh on package changes. |
| `channels/PreviewReader` | Parse readable preview channels/programmes, validate ownership and preserve published artwork/order. |
| `channels/ChannelRepository` | Observe provider changes and permission state; debounce notifications and read off the main thread. |
| `settings/PreferencesRepository` | Persist favourite-app order and selected-list identity, title and order in Preferences DataStore. |
| `home/HomeViewModel` | Combine repositories into display state and retain stable focus anchors for card screens. |
| `home/MainActivity`, `CardAdapter`, `ContentCard`, `CardGeometry` | Render native screens, handle D-pad navigation and display asynchronous artwork. |
| `apps/ContentLauncher` | Validate source-owned content intents and report localized launch failures. |

## Provider and persistence rules

- Read standard `content://android.media.tv/channel` and `preview_program` data with `READ_TV_LISTINGS`. No source app is required, preselected or integrated through a private API. An installed app must publish readable preview channels to supply content rows.
- Query without caller-supplied SQL selection, then filter preview channels in the reader. Programmes must belong to the channel's owner package. Malformed records are skipped or reported as a read failure.
- List identity combines the owner package with the internal provider ID, falling back to the numeric channel ID. Item identity follows the same internal-ID fallback within its row. Renamed lists retain selection; recreated lists without a stable identity may need reselection.
- Keep source item order: weight descending, then provider ID. Users control selected-list order independently; new selections append to it.
- Keep preferences and the last successful channel snapshot through failed or denied reads. Missing selected lists remain saved and removable from Settings. Home omits empty and unavailable rows and shows no channel setup notices.
- The JSON preference schema remains version 1 under DataStore key `settings_v1`. Its legacy `visible` field remains serialized for compatibility; Home shows every selected nonempty list regardless of that field. Do not reintroduce a visibility toggle when maintaining this schema.
- Settings remain local to each device. There is no backend, account system, embedded playback, analytics or cross-device synchronization. Source applications own authentication and playback.

## UI, focus and artwork

All editing belongs in Settings. Home and All apps only launch items. Apps & favourites uses image tiles with normal-OK edit dialogs; channel management keeps selected lists in global order and groups available lists by source app. List headings use `Application: Channel list`; programme titles remain source-authored.

Home and Apps & favourites share compact 120dp tiles with 64dp artwork areas and identical margins/focus styling. All apps retains 136dp tiles with 72dp artwork areas. Prefer activity/application TV banners that fill the tile, with uncropped 48dp icons as fallback; app grids use five tiles per row. Content artwork is 104dp high and uses the declared aspect ratio. Labels reserve their space but appear only on focus; accessibility labels remain available. Focus scales cards to 1.24 with a foreground outline that remains visible over opaque artwork; Home and favourite-editor tiles have 2dp side margins and slightly overlap their neighbours on focus without moving them. Row edges reserve 32dp for focus growth; All apps retains its larger tile spacing.

`CardGeometry` derives label heights from font bounds and row clearance from focus growth, so two-line labels retain descenders and larger text has enough space. Recycling cancels image requests and animations and resets transforms. Restore the scroll position before requesting final focus after edits, so selected controls stay visible. Card screens retain separate stable focus anchors and include the nested back button in vertical navigation.

The root clips the scrolling viewport at its bounds so content cannot paint over the fixed clock/settings header. That header remains visible on secondary pages. Every Home row registers its section heading. On vertical focus changes, the viewport reveals the heading and the full row together, with an 8dp leading gap. This applies to Apps and every channel list; stale focus callbacks are ignored after a re-render.

Coil prefers poster, thumbnail, then logo artwork. Logos use fit-centre. Requests are sized to the card; memory/disk caches are bounded at 24/48 MiB. Content/resource and HTTP(S) image URIs are supported; HTTP is allowed for provider-supplied hosts. Missing or failed artwork retains a placeholder and the focused title. Navigation does not require connectivity.

The supplied root [icon.png](../icon.png) is packaged unchanged as `drawable-nodpi/app_icon.png`. The separate `app_banner.png` adapts it to a full-width peach/coral TV banner. `drawable/ic_launcher.xml` and `tv_banner.xml` define their display bounds; the banner fills the 16:9 panel rather than embedding a square icon on a dark background.

## Platform integration

The manifest declares Home and Leanback launcher entry points. Set as Home requests the platform Home role when available and treats that role as the authoritative status. If the role is unavailable, it checks which package resolves the default Home intent. The manifest explicitly queries Home handlers so package visibility does not leave only this app in the resolver results. Settings places Refresh content first under Device, with extra space before Channel access. Channel access reflects the actual runtime permission. These actions disable when already satisfied and refresh when returning from system screens. The TV settings button uses a gear icon and opens the standard `Settings.ACTION_SETTINGS` intent.

English resources are the fallback, with Ukrainian in `values-uk`. `locale_config.xml` declares both; `LocaleManager` handles the System/English/Ukrainian choice. The header uses Android's `DateFormat.getTimeFormat` and `getLongDateFormat` with a configuration context containing `LocaleManager.systemLocales`. Its language, date layout and default hour cycle follow the TV, independent of the interface language. The explicit system 12/24-hour setting remains authoritative. Time/date broadcasts, a lifecycle-scoped hour-preference observer and resume refresh both header values; no system preferences are changed. Error state stores resource IDs so language changes translate existing failures. Provider titles and app labels remain source-authored.

Content intents are restricted to their source package. Reject conflicting explicit packages/components, selectors and `javascript:`, `file:` or `data:` schemes; replace incoming flags with `FLAG_ACTIVITY_NEW_TASK`. Failed launches offer an explicit source-app fallback. The launcher never disables or uninstalls the stock Home app.

Tests are separated into host unit tests and an instrumentation-only fixture provider. The fixture is excluded from release builds. See [verification](verification.md) for recorded checks and remaining hardware work.
