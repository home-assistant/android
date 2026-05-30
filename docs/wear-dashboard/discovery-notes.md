# Wear Dashboard — Discovery Notes

Status: verified against repository on 2026-05-30  
Target issue: https://github.com/home-assistant/android/issues/5470

## Dependency baseline

| Item | Version / value | Source |
|------|-----------------|--------|
| Compile SDK | 37 | `gradle/libs.versions.toml` → `androidSdk-compile` |
| App target SDK | 36 | `gradle/libs.versions.toml` → `androidSdk-target` |
| Wear min SDK | 26 | `gradle/libs.versions.toml` → `androidSdk-wear-min` |
| Wear target SDK | 34 (bump to 36 in PR 12) | `gradle/libs.versions.toml` → `androidSdk-wear-target` |
| Wear Compose | 1.6.2 | `gradle/libs.versions.toml` → `wear-compose` |
| Wear Tiles | 1.6.0 | `gradle/libs.versions.toml` → `wear-tiles` |
| Wear ProtoLayout | 1.4.0 | `gradle/libs.versions.toml` → `wear-protolayout` |
| Compose BOM | 2026.05.01 | `gradle/libs.versions.toml` → `compose-bom` |
| Kotlin serialization | via project convention | `kotlinJsonMapper` in `JsonUtil.kt` |

## Modules

| Module | Role |
|--------|------|
| `:common` | Dashboard schema, validation, persistence, state cache |
| `:wear` | Tile/Compose renderers, action execution, watch UI |
| `:app` (full flavor) | Phone-side dashboard management and Data Layer sync |

## Key existing files

| Path | Notes |
|------|-------|
| `common/.../prefs/impl/entities/TemplateTileConfig.kt` | Legacy template tile config `{ template, refreshInterval }` |
| `common/.../prefs/WearPrefsRepository.kt` | Shortcut + template tile prefs |
| `common/.../integration/IntegrationRepository.kt` | `renderTemplate`, `getTemplateUpdates`, `getEntities`, `callAction` |
| `common/.../util/WearDataMessages.kt` | Data Layer message keys |
| `wear/.../tiles/TemplateTile.kt` | Legacy Jinja template tile (keep unchanged) |
| `wear/.../tiles/ShortcutsTile.kt` | Multi-instance tile pattern |
| `wear/.../tiles/TileActionReceiver.kt` | Entity toggle broadcast pattern |
| `wear/.../phone/PhoneSettingsListener.kt` | Phone → watch config sync |
| `wear/.../home/views/HomeView.kt` | Wear Compose navigation host |
| `app/src/full/.../settings/wear/SettingsWearViewModel.kt` | Phone-side wear settings + sync |

## Gaps confirmed

- No dashboard schema or declarative renderer
- No `getTemplateUpdates` usage on wear
- Template tile renders via network in `onTileRequest` (legacy; dashboard tile must use cache)
- Phone-side wear settings exist only under `app/src/full/`

## Build commands used during discovery

```bash
./gradlew ktlintCheck --continue
./gradlew :common:test :wear:test
```
