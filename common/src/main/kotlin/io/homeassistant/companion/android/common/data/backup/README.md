# Companion settings backup, version 1

The document is pretty-printed UTF-8 JSON, limited to 1 MiB. The codec is independent
of Android's document picker so another storage destination can reuse the format.
The entry point is available in the phone app (full and minimal flavors). Android
Auto favorites are owned by that phone. The entry is hidden on Android Automotive
and Meta Quest until the document-picker workflow has been verified there.

Required metadata:

- `format`: `home-assistant-companion-settings`
- `schemaVersion`: `1`; unsupported versions are rejected before applying changes
- `appVersion`: source app version, informational
- `createdAt`: ISO-8601 UTC instant, informational
- `servers`: document-local `reference` and display `name` for each source server

Optional sections:

- `androidAutoFavorites`: ordered `{ "server": "reference", "entityId": "domain.object" }`
  list of Android Auto favorites
- `servers[].sensors`: sensor ID to enabled/disabled boolean
- `servers[].persistentConnection`: `NEVER`, `SCREEN_ON`, `ALWAYS`, or `HOME_WIFI`
- `sensorOptions`: sensor ID and map of declared setting names to values. Each
  option has `enabled` (visibility in sensor settings) and exactly one of `value`
  (the string representation) or `zones` (portable entity references).
- `sensorUpdateFrequency`: `NORMAL`, `FAST_WHILE_CHARGING`, or `FAST_ALWAYS`

An omitted/null section leaves the destination unchanged. An explicit empty
`androidAutoFavorites` list clears Android Auto favorites for mapped servers.
Missing individual settings are retained. The importer rejects malformed documents
and unknown fields.

Numeric sensor options capture the effective value on the source. If the stored
text cannot be parsed as the declared integer or decimal type, including a cleared
field, export writes the source setting's declared default. Valid numeric strings
are retained as entered. Restore applies the saved value even if the destination
has a different default; imported values must still be valid integers or finite
decimals.

## Restore behavior

The user signs in first, selects sections, assigns source references to destination
servers, reviews the proposed changes, and confirms. Destination questions start
unanswered, not skipped. Each included source needs an explicit radio-button
destination choice. When multiple sources need destinations, separate inclusion
switches allow excluding individual sources. A single source always requires a
destination; at least one destination is required for selected server-specific settings.
A destination can be mapped only once. Skipped servers retain their server-specific configuration. Sensor
options and update frequency are app-wide, so they also affect skipped servers.
Zones with any unmapped reference are skipped as a whole rather than partially
replacing a setting. Favorite order within the imported list is retained;
favorites on unmapped destination servers are retained ahead of that list. The
`androidAutoFavorites` section is exclusively Android Auto favorites; any future
favorite types must have their own sections rather than reinterpreting existing backups.

Unavailable sensors, options unknown to this app, invalid option types/choices,
and enabled sensors lacking permissions are reported and skipped. Entity IDs
are retained without contacting the server; users must map to servers containing
those entities. Importing the same document repeatedly does not add duplicates.
Permission and capability checks run again immediately before applying changes.
As in the existing update-frequency screen, changing sensor update frequency
requires an app restart to register the fast-update receiver.

Restored sensor selections are marked pending synchronization (`registered = null`),
even when the restored value matches the cached enabled state. The next sensor
update re-registers those choices before accepting conflicting enabled states from
a trusted server. Successful synchronization clears the pending status; subsequent
server changes follow the normal synchronization rules.

Database changes use one Room transaction. Favorites use the existing preferences
store and are compensated if that transaction fails. This is not a cross-store
crash-atomic transaction: abrupt process termination can leave favorites applied
alone. Reimporting the same file safely completes the restore. Lifecycle
cancellation does not interrupt the short commit/compensation sequence.

## Portability boundaries

The format never contains authentication, server URLs, webhooks, push tokens,
device registrations, cached sensor readings, or generated BLE transmitter
identity. Only declared sensor options are exported, excluding text options
(currently the BLE transmitter UUID/major/minor). Dynamic/runtime settings are
excluded. Sensor readings and registration version metadata on the destination
are retained; the synchronization status of restored selections is reset as
described above.

Android permissions, battery exemptions, app locks, notification channels,
home-Wi-Fi detection configuration, widgets, and Wear OS settings are not part of
this version. The document may still contain personal entity IDs, server labels,
app selections, Bluetooth addresses, and location preferences. It is intentionally
unencrypted for portability and should be stored accordingly.

The version-one fixture in `common/src/test/resources/backup/settings-v1.json`
pins compatibility separately from encoder/decoder round-trip tests. Future
format changes should add explicit migrations and compatibility fixtures.
