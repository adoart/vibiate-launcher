# Vibiate — an Aviate-inspired launcher

A Jetpack Compose system launcher modeled on the discontinued Yahoo Aviate:
collections-style home, A–Z app list with index rail, widget space, rotating
wallpapers, icon packs, and a dark/light Aviate palette.

## Phase 0 — Scaffold & build ✅
- [x] Gradle wrapper (8.14.3), Kotlin DSL, version catalog
- [x] Single `app` module: AGP 8.11.1, Kotlin 2.2.x + compose plugin, Compose BOM
- [x] Manifest with HOME + LAUNCHER intent filters, `QUERY_ALL_PACKAGES`, `SET_WALLPAPER`
- [x] `assembleDebug` green

## Phase 1 — Core collections / A–Z / theming 🚧
- [x] AppRepository: load launchable apps off the main thread, cache icons/labels
- [x] Auto-categorization: `ApplicationInfo.category` + package-keyword heuristics
- [x] Collections home: letter-spaced headers, 5-icon collapsed row, wrapping expanded grid
- [x] A–Z screen: outlined letter groups, alphabet rail (tap + drag), search, A-Z/Z-A sort
- [x] Material 3 dark/light theme, `System / Light / Dark` in DataStore, dark default
- [x] Package add/remove/change refresh via receiver (runtime + manifest)
- [x] Long-press app icon → system app details

## Phase 2 — Widgets, wallpapers, icon packs 🚧
- [x] Widget space: AppWidgetHost (1024), pick → bind flow, persisted widget ids, remove
- [x] Rotating wallpapers: multi-image picker + WorkManager periodic rotation + "rotate now"
- [x] Icon packs: theme-intent discovery, appfilter.xml parsing, per-component resolution
- [ ] Icon pack icon size/shape preferences, icon backplates for unthemed icons

## Phase 3 — Contextual spaces
- [ ] Morning / Work / Night spaces like Aviate (time + location triggers)
- [ ] "Apps nearby" / commute collections, weather strip
- [ ] Context card header rotating with the active space

## Phase 4 — Gestures, search, badges
- [x] Swipe between home / widgets / collections / A–Z as a pager (+ custom pages)
- [x] Microsoft Launcher–style custom spaces: trailing "Add space" page appends
  per-screen widget spaces; per-screen widget lists, persisted; delete via header
  icon + confirm dialog
- [ ] Rename/reorder custom spaces, per-space wallpaper, drag widgets between spaces
- [x] Home page: large thin clock + date, first pager page (Aviate minimal)
- [x] Weather row: Open-Meteo + coarse location, cached in DataStore, tap to refresh
- [x] Google search widget on Home (Google Search bar, seeded as a real widget)
- [x] Microsoft Launcher–style dock: 5 icons, expandable 2nd row via chevron or drag
- [x] Long-press empty space → "Customize" sheet: wallpaper (one-shot pick or
  rotation settings), grid columns (4/5/6) for collections, icon pack picker,
  font family (system/sans/serif/mono), launcher settings shortcut
- [ ] Customize sheet follow-ups: icon size preference, dock row-2
  customization, per-screen wallpaper
- [x] Custom categories: user-created collections (create/edit/delete, glyph
  picker) shown first on the Collections page; add apps via searchable
  checklist; move apps between categories via icon long-press; per-component
  overrides persisted in DataStore
- [x] Drag an app from the A–Z page into a category: pager switches to
  Collections mid-drag, ghost follows the finger, placeholder marks the
  slot, drop persists override + order
- [x] Drag app icons to reorder within a category or move between
  categories (fixed slots, animated shift, order persisted)
- [x] Drag category headers to reorder categories: compact cards, outline
  placeholder, order persisted
- [ ] Category follow-ups: export/import category layout
- [ ] Swipe-down global app search with fuzzy matching
- [ ] Pull-down / double-tap gestures on home
- [ ] Notification badges on icons (NotificationListener)
- [ ] Dock customization: pin/reorder/replace slots, icon count
- [x] Home clock/date/weather replaced by Google widgets (Clock + At a glance) — no Vibiate weather service
- [ ] Widget resize handles & span control (moved from Phase 2)

## Aviate 3.2 parity (from reverse engineering)
- [x] Montserrat bundled as the Gotham substitute (`LauncherFont.AVIATE`, the
  new default font; OFL license in `assets/licenses/`)
- [x] Aviate holoBlue `#33B5E5` accent in both themes; translucent card/skrim
  palette (`LocalAviateColors`): dark 30%-black cards over wallpaper, light
  50%-white cards, Aviate greys for titles/labels/icons/dividers
- [x] Page backdrop: wallpaper clear on Home/Widgets/spaces, skrim faded
  in/out with pager position on Collections/A–Z, top gradient for the top bar
- [x] Flat white 48dp Aviate search bar on Collections (hops to A–Z and
  focuses the field) and A–Z (editable, sort toggle in the trailing slot)
- [x] Collection cards restyled: 2dp corners, 40dp headers, glyph 16dp, 12sp
  Medium letter-spaced titles, rotating chevron; expanded rows show labels;
  "New category" is now a bare text button
- [x] A–Z restyled: 4-column letter grids with the letter in cell 0 (filled
  60sp Thin, Roboto), 13sp labels, 1px group dividers, 34dp rail with left
  hairline and bold/dimmed letters
- [x] Dock: bare icons on the wallpaper over a bottom gradient, white chevron
- [x] Home search pill restyled to the flat Aviate bar (G + mic kept)
- [x] Settings: cyan top bar with back arrow + SETTINGS title, grey section
  bands, icon rows with hairline dividers
- [ ] Parity follow-ups: auto column count from screen width (Aviate uses
  floor((width − 2×4dp) / (icon + 14dp))), People drawer (favourite/recent
  contacts) on dock pull-up, time-of-day "spaces" with contextual cards

## Phase 5 — Polish & release
- [ ] Backup/restore of settings, expanded-state, widget layout
- [ ] Unit tests (classifier, icon pack parser) + Compose UI smoke tests
- [ ] ProGuard/R8 release build, signed bundle, Play listing assets
