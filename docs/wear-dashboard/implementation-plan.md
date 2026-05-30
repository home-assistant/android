# Home Assistant Android — Wear Dashboard Implementation Plan

Status: implemented (jitterbox fork)  
Target repository: https://github.com/home-assistant/android  
Primary request: https://github.com/home-assistant/android/issues/5470  
Last reviewed: 2026-05-30

Discovery notes: [docs/wear-dashboard/discovery-notes.md](../docs/wear-dashboard/discovery-notes.md)

## 1. Goal

Implement richer Home Assistant dashboards on Wear OS without running the full Lovelace/web frontend on the watch.

The **Home Assistant Wear Dashboard schema** is versioned JSON describing HA semantics (entities, templates, actions). Render targets translate it to native surfaces:

| Surface | Technology | Status |
|---------|------------|--------|
| Glanceable tile | ProtoLayout + `WearDashboardTile` | Implemented |
| Full drill-in | Wear Compose (`WearDashboardScreen`) | Implemented |
| Long-running state | Ongoing Activity (Wear OS 6 and earlier) | Implemented |
| Long-running state | Live Updates (Wear OS 7+) | Gated; Ongoing Activity used below API 36 |
| Next-gen glanceable | Wear Widgets (Remote Compose) | Stub behind `WEAR_WIDGETS_ENABLED=false` |

Central design rule:

> Dashboard configuration describes **Home Assistant semantics**, not Android UI. Compose, ProtoLayout, Remote Compose, and notifications are render targets.

## 2. Fork and upstream workflow (GitHub CLI, jitterbox)

**GitHub identity:** [`jitterbox`](https://github.com/jitterbox)

```bash
gh auth status   # must show logged in as jitterbox
gh repo fork home-assistant/android --clone=true --remote=true
gh issue view 5470 --repo home-assistant/android
```

Sync fork before each PR:

```bash
gh repo sync jitterbox/android --source home-assistant/android --branch main
```

Open upstream PRs:

```bash
gh pr create \
  --repo home-assistant/android \
  --head jitterbox:feature/wear-dashboard-schema \
  --base main \
  --title "Add Wear Dashboard schema and validation" \
  --body "Refs #5470"
```

One phase per PR. Link `Closes #5470` or `Refs #5470` in every PR body.

## 3. Architecture

### 3.1 Package layout

```text
common/.../wear/dashboard/
  model/          # Schema (WearDashboardConfig, components, bindings, actions)
  validation/     # WearDashboardValidation
  state/          # Cache, update coordinator, dependency extractor
  WearDashboardRepository.kt

wear/.../tiles/dashboard/     # ProtoLayout renderer, tile service, actions
wear/.../home/views/dashboard/ # Compose renderer and screens
wear/.../dashboard/ongoing/    # Ongoing Activity manager
wear/.../widgets/              # Wear Widgets stub

app/src/full/.../settings/wear/ # Phone management UI and sync
```

### 3.2 Renderer contract

```kotlin
interface WearDashboardRenderer<T> {
    fun render(
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): T
}
```

Implementations: `ProtoLayoutWearDashboardRenderer`, `ComposeWearDashboardRenderer`, `WearDashboardWidgetRenderer` (stub).

### 3.3 Device capabilities (transparent to users)

`WearDashboardCapabilities` is derived at runtime from tile `DeviceParameters` or Compose configuration. Users configure one dashboard; the watch adapts layout density and child limits.

| Bucket | Typical devices | Tile constraints |
|--------|-----------------|------------------|
| Small | 41mm round (~192–210 dp) | Tighter child limits |
| Medium | 45mm round, Galaxy 40mm | Standard limits |
| Large | Galaxy 44–45mm rect | Full multi-slot when OS supports |

Reference QA devices: Pixel Watch 4 (41/45mm), Galaxy Watch 8, `WearDevices.SMALL_ROUND` / `LARGE_ROUND` emulators.

### 3.4 Power model

```text
Config → DependencyExtractor → WearDashboardUpdateCoordinator → StateCache → Tile/Compose render
```

Rules:

- **Never** call `renderTemplate` inside `onTileRequest` (dashboard tile reads cache only).
- Subscribe via `getEntityUpdates` and `getTemplateUpdates`.
- Throttle tile refresh requests (2s coalesce default).
- Dynamic expressions are budgeted (clock, countdown, progress only).

## 4. Schema

Canonical format: **JSON** (camelCase). YAML optional import/export later only.

Phase 1 components: `text`, `icon`, `button`, `status_chip`, `progress_ring`, `row`, `column`, `box`, `conditional`.

Actions: `toggle_entity`, `call_service`, `navigate`, `refresh`, `open_full_dashboard`. Sensitive actions support `requiresConfirmation`.

Starter templates ship in `common/src/main/assets/wear_dashboard_templates/` (home, car, energy, security).

Example:

```json
{
  "version": 1,
  "id": "car",
  "title": "Car",
  "surfaces": {
    "tile": { "page": "compact" },
    "app": { "startPage": "compact" }
  },
  "pages": [
    {
      "id": "compact",
      "title": "Car",
      "root": {
        "type": "box",
        "children": [
          {
            "type": "progress_ring",
            "id": "battery_ring",
            "value": { "type": "entity_state", "entityId": "sensor.car_battery" },
            "min": 0,
            "max": 100
          }
        ]
      }
    }
  ]
}
```

## 5. Implementation phases (PR mapping)

| PR | Scope | Status |
|----|-------|--------|
| 0 | Doc sync + discovery notes | Done |
| 1 | Schema, validation, capabilities | Done |
| 2 | `WearDashboardRepository` persistence | Done |
| 3 | State cache + update coordinator | Done |
| 4 | ProtoLayout renderer | Done |
| 5 | `WearDashboardTile` service | Done |
| 6 | Action execution + confirmation | Done |
| 7 | Phone/watch Data Layer sync | Done |
| 8 | Compose full dashboard | Done |
| 9 | Phone management UI + templates | Done |
| 10 | Ongoing Activity | Done |
| 11 | Dynamic expressions (selective) | Done |
| 12 | Platform hardening (wear targetSdk 36) | Done |
| 13 | Wear Widgets stub | Done |
| 14 | Migration helper + changelog | Done |

## 6. Phone-side UX (non-technical first)

Settings → Companion App → Wear OS → **Wear dashboards**

1. One-tap starter templates (Home, Car, Energy, Security)
2. Entity picker customization in editor
3. Advanced JSON import/export for power users
4. Validation preview before save
5. Automatic sync to watch via `/updateWearDashboards`

Watch shows read-only tile preview with “Edit on phone” guidance.

## 7. Backward compatibility

- **Template tiles unchanged** — existing Jinja template tiles continue to work.
- Dashboard tile is a **separate** `WearDashboardTile` service.
- Invalid stored JSON logs and recovers to empty config.
- Unsupported schema versions ignored with user-visible message.

Optional migration: `WearDashboardMigrationHelper` can create a dashboard from a template tile config.

## 8. Testing

| Layer | Coverage |
|-------|----------|
| Unit | Schema, validation, capabilities, dependency extraction, layout rules, repository |
| Manual | Pixel Watch 4, Galaxy Watch 8, small/large round emulators |

Run:

```bash
./gradlew :common:test :wear:test :app:testFullDebugUnitTest
```

## 9. MVP done definition

1. Template tiles still work
2. Dashboard tile renders text + progress ring + buttons from cache
3. Actions call HA services with confirmation for sensitive ops
4. Same config opens in Compose drill-in
5. Phone syncs config; starter template flow works without JSON editing
6. Capability-aware tile density on small vs large round
7. Tests cover schema, validation, cache, renderer decisions
8. This document matches implementation

## 10. References

- [Wear OS Tiles](https://developer.android.com/training/wearables/tiles)
- [Wear Widgets](https://developer.android.com/training/wearables/widgets)
- [Material 3 Expressive (Wear OS 6)](https://developer.android.com/training/wearables/versions/6/features)
- [Ongoing Activity](https://developer.android.com/training/wearables/notifications/ongoing-activity)
- [Live Updates](https://developer.android.com/training/wearables/notifications/live-updates)
- [Wear OS 6 changes](https://developer.android.com/training/wearables/versions/6/changes)

## 11. Key files

| Path | Role |
|------|------|
| `common/.../wear/dashboard/model/*` | Schema |
| `common/.../wear/dashboard/state/*` | Cache and updates |
| `wear/.../tiles/dashboard/WearDashboardTile.kt` | Tile service |
| `wear/.../home/views/dashboard/WearDashboardScreen.kt` | Compose UI |
| `wear/.../phone/PhoneSettingsListener.kt` | Watch sync receiver |
| `app/src/full/.../settings/wear/*` | Phone management |
| `common/src/main/assets/wear_dashboard_templates/*` | Starter templates |
