package io.homeassistant.companion.android.database.migration

import android.content.Context
import androidx.core.content.edit
import androidx.room3.RenameColumn
import androidx.room3.RenameTable
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.IconDialogCompat
import java.util.UUID
import timber.log.Timber

internal fun migrationPath(context: Context): Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION6to7(context),
    MIGRATION_7_8,
    MIGRATION_8_9,
    Migration9to10(context),
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    Migration16to17(context),
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    Migration37to38(context),
    Migration40to41(context),
)

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensors` (`unique_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`unique_id`))",
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `button_widgets` (`id` INTEGER NOT NULL, `icon_id` INTEGER NOT NULL, `domain` TEXT NOT NULL, `service` TEXT NOT NULL, `service_data` TEXT NOT NULL, `label` TEXT, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_id` TEXT, `label` TEXT, PRIMARY KEY(`id`))",
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `static_widget` ADD `text_size` FLOAT NOT NULL DEFAULT '30'")
        connection.execSQL("ALTER TABLE `static_widget` ADD `separator` TEXT NOT NULL DEFAULT ' '")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `template_widgets` (`id` INTEGER NOT NULL, `template` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

private const val CREATE_STATIC_WIDGET_V6 =
    "CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, " +
        "`attribute_ids` TEXT, `label` TEXT, `text_size` FLOAT NOT NULL DEFAULT '30', " +
        "`state_separator` TEXT NOT NULL DEFAULT '', `attribute_separator` TEXT NOT NULL DEFAULT '', " +
        "PRIMARY KEY(`id`))"

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        try {
            // Read the old rows fully before dropping the table they came from.
            val widgets = connection.query("SELECT * FROM `static_widget`") { row ->
                mapOf(
                    "id" to row.getIntByName("id"),
                    "entity_id" to row.getString("entity_id"),
                    "attribute_ids" to row.getStringOrNull("attribute_id"),
                    "label" to row.getStringOrNull("label"),
                    "text_size" to row.getFloatOrZero("text_size"),
                    "state_separator" to row.getStringOrNull("separator"),
                    "attribute_separator" to " ",
                )
            }
            connection.execSQL("DROP TABLE IF EXISTS `static_widget`")
            connection.execSQL(CREATE_STATIC_WIDGET_V6)
            widgets.forEach { connection.insertOrReplace("static_widget", it) }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to migrate database version 5 to version 6")
        }
    }
}

private class MIGRATION6to7(private val context: Context) : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        var migrationFailed = false
        val sensors = try {
            connection.query("SELECT * FROM sensors") { row ->
                mapOf(
                    "id" to row.getString("unique_id"),
                    "enabled" to row.getIntByName("enabled"),
                    "registered" to row.getIntByName("registered"),
                    "state" to "",
                    "state_type" to "",
                    "type" to "",
                    "icon" to "",
                    "name" to "",
                    "device_class" to "",
                )
            }
        } catch (e: Exception) {
            migrationFailed = true
            Timber.e(e, "Unable to migrate, proceeding with recreating the table")
            null
        }
        connection.execSQL("DROP TABLE IF EXISTS `sensors`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))",
        )

        sensors?.forEach {
            connection.insertOrReplace("sensors", it)
        }
        if (migrationFailed) {
            notifyMigrationFailed(context)
        }

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensor_attributes` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`sensor_id`, `name`))",
        )
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sensor_attributes` ADD `value_type` TEXT NOT NULL DEFAULT 'string'")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sensors` ADD `state_changed` INTEGER NOT NULL DEFAULT ''")
    }
}

private class Migration9to10(private val context: Context) : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        var migrationFailed = false
        val sensors = try {
            connection.query("SELECT * FROM sensors") { row ->
                mapOf(
                    "id" to row.getString("id"),
                    "enabled" to row.getIntByName("enabled"),
                    "registered" to row.getIntByName("registered"),
                    "state" to "",
                    "last_sent_state" to "",
                    "state_type" to "",
                    "type" to "",
                    "icon" to "",
                    "name" to "",
                )
            }
        } catch (e: Exception) {
            migrationFailed = true
            Timber.e(e, "Unable to migrate, proceeding with recreating the table")
            null
        }
        connection.execSQL("DROP TABLE IF EXISTS `sensors`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `last_sent_state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))",
        )

        sensors?.forEach {
            connection.insertOrReplace("sensors", it)
        }
        if (migrationFailed) {
            notifyMigrationFailed(context)
        }
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensor_settings` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, `value_type` TEXT NOT NULL DEFAULT 'string', PRIMARY KEY(`sensor_id`, `name`))",
        )
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `mediaplayctrls_widgets` (`id` INTEGER NOT NULL, `entityId` TEXT NOT NULL, `label` TEXT, `showSkip` INTEGER NOT NULL, `showSeek` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `received` INTEGER NOT NULL, `message` TEXT NOT NULL, `data` TEXT NOT NULL)",
        )
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `static_widget` ADD `last_update` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `template_widgets` ADD `last_update` TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sensor_settings` ADD `enabled` INTEGER NOT NULL DEFAULT '1'")
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `camera_widgets` (`id` INTEGER NOT NULL, `entityId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

private class Migration16to17(private val context: Context) : Migration(16, 17) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val sensorSettings = mutableListOf<Map<String, Any?>>()
        var migrationFailed = false
        try {
            val migrated = connection.query("SELECT * FROM sensor_settings") { row ->
                val currentSensorId = row.getString("sensor_id")
                val currentSensorSettingName = row.getString("name")
                var entries = ""
                var newSensorSettingName = currentSensorSettingName

                if (currentSensorId == "next_alarm" && currentSensorSettingName == "Allow List") {
                    newSensorSettingName = "nextalarm_allow_list"
                } else if ((
                        currentSensorId == "last_removed_notification" ||
                            currentSensorId == "last_notification"
                        ) &&
                    currentSensorSettingName == "Allow List"
                ) {
                    newSensorSettingName = "notification_allow_list"
                } else if (currentSensorId == "geocoded_location" &&
                    currentSensorSettingName == "Minimum Accuracy"
                ) {
                    newSensorSettingName = "geocode_minimum_accuracy"
                } else if ((
                        currentSensorId == "zone_background" ||
                            currentSensorId == "accurate_location" ||
                            currentSensorId == "location_background"
                        ) &&
                    currentSensorSettingName == "Minimum Accuracy"
                ) {
                    newSensorSettingName = "location_minimum_accuracy"
                } else if (currentSensorId == "accurate_location" &&
                    currentSensorSettingName == "Minimum time between updates"
                ) {
                    newSensorSettingName = "location_minimum_time_updates"
                } else if (currentSensorId == "accurate_location" &&
                    currentSensorSettingName == "Include in sensor update"
                ) {
                    newSensorSettingName = "location_include_sensor_update"
                } else if (currentSensorId == "location_background" &&
                    currentSensorSettingName == "High accuracy mode (May drain battery fast)"
                ) {
                    newSensorSettingName = "location_ham_enabled"
                } else if (currentSensorId == "location_background" &&
                    currentSensorSettingName == "High accuracy mode update interval (seconds)"
                ) {
                    newSensorSettingName = "location_ham_update_interval"
                } else if (currentSensorId == "location_background" &&
                    currentSensorSettingName ==
                    "High accuracy mode only when connected to BT devices"
                ) {
                    newSensorSettingName = "location_ham_only_bt_dev"
                } else if (currentSensorId == "location_background" &&
                    currentSensorSettingName == "High accuracy mode only when entering zone"
                ) {
                    newSensorSettingName = "location_ham_only_enter_zone"
                } else if (currentSensorId == "location_background" &&
                    currentSensorSettingName == "High accuracy mode trigger range for zone (meters)"
                ) {
                    newSensorSettingName = "location_ham_trigger_range"
                } else if (currentSensorId == "ble_emitter" && currentSensorSettingName == "UUID") {
                    newSensorSettingName = "ble_uuid"
                } else if (currentSensorId == "ble_emitter" &&
                    currentSensorSettingName == "Major"
                ) {
                    newSensorSettingName = "ble_major"
                } else if (currentSensorId == "ble_emitter" &&
                    currentSensorSettingName == "Minor"
                ) {
                    newSensorSettingName = "ble_minor"
                } else if (currentSensorId == "ble_emitter" &&
                    currentSensorSettingName == "transmit_power"
                ) {
                    newSensorSettingName = "ble_transmit_power"
                    entries = "ultraLow|low|medium|high"
                } else if (currentSensorId == "ble_emitter" &&
                    currentSensorSettingName == "Enable Transmitter"
                ) {
                    newSensorSettingName = "ble_transmit_enabled"
                } else if (currentSensorId == "ble_emitter" &&
                    currentSensorSettingName == "Include when enabling all sensors"
                ) {
                    newSensorSettingName = "ble_enable_toggle_all"
                } else if (currentSensorId == "last_reboot" &&
                    currentSensorSettingName == "deadband"
                ) {
                    newSensorSettingName = "lastreboot_deadband"
                } else if (currentSensorId == "last_update" &&
                    currentSensorSettingName == "Add New Intent"
                ) {
                    newSensorSettingName = "lastupdate_add_new_intent"
                } else if (currentSensorId == "last_update" &&
                    currentSensorSettingName.startsWith("intent")
                ) {
                    newSensorSettingName =
                        "lastupdate_intent_var1:" +
                        currentSensorSettingName.substringAfter("intent") +
                        ":"
                } else if (currentSensorId == "wifi_bssid" &&
                    currentSensorSettingName == "get_current_bssid"
                ) {
                    newSensorSettingName = "network_get_current_bssid"
                } else if (currentSensorId == "wifi_bssid" &&
                    currentSensorSettingName.startsWith("replace_")
                ) {
                    newSensorSettingName =
                        "network_replace_mac_var1:" +
                        currentSensorSettingName.substringAfter("replace_") +
                        ":"
                }
                mapOf(
                    "sensor_id" to row.getString("sensor_id"),
                    "name" to newSensorSettingName,
                    "value" to row.getString("value"),
                    "value_type" to row.getString("value_type"),
                    "entries" to entries,
                    "enabled" to row.getIntByName("enabled"),
                )
            }
            // The original iterated with moveToFirst() then while(moveToNext()), skipping the
            // first row; drop(1) preserves that exact behaviour so migration output is unchanged.
            sensorSettings.addAll(migrated.drop(1))
        } catch (e: Exception) {
            migrationFailed = true
            Timber.e(e, "Unable to migrate, proceeding with recreating the table")
        }
        connection.execSQL("DROP TABLE IF EXISTS `sensor_settings`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sensor_settings` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, `value_type` TEXT NOT NULL DEFAULT 'string', `entries` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT '1', PRIMARY KEY(`sensor_id`, `name`))",
        )

        sensorSettings.forEach {
            connection.insertOrReplace("sensor_settings", it)
        }
        if (migrationFailed) {
            notifyMigrationFailed(context)
        }
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `qs_tiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tileId` TEXT NOT NULL, `icon_id` INTEGER, `entityId` TEXT NOT NULL, `label` TEXT NOT NULL, `subtitle` TEXT)",
        )
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sensors` ADD `state_class` TEXT")
        connection.execSQL("ALTER TABLE `sensors` ADD `entity_category` TEXT")
        connection.execSQL("ALTER TABLE `sensors` ADD `core_registration` TEXT")
        connection.execSQL("ALTER TABLE `sensors` ADD `app_registration` TEXT")
    }
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorites` (`id` TEXT PRIMARY KEY NOT NULL, `position` INTEGER)",
        )
    }
}

private val MIGRATION_20_21 = object : Migration(20, 21) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `websocketSetting` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

private val MIGRATION_21_22 = object : Migration(21, 22) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `notification_history` ADD `source` TEXT NOT NULL DEFAULT 'FCM'")
    }
}

private val MIGRATION_22_23 = object : Migration(22, 23) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `mediaplayctrls_widgets` ADD `showVolume` INTEGER NOT NULL DEFAULT '0'")
    }
}

private val MIGRATION_23_24 = object : Migration(23, 24) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `settings` ADD `sensorUpdateFrequency` TEXT NOT NULL DEFAULT 'NORMAL'")
    }
}

internal class Migration27to28 : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        // Update 'registered' in the sensors table to set the value to null instead of the previous default of 0
        // This will force an update to indicate whether a sensor is not registered (null) or registered as disabled (0)
        connection.execSQL("UPDATE `sensors` SET `registered` = NULL")
    }
}

@RenameColumn.Entries(
    RenameColumn(
        tableName = "Authentication_List",
        fromColumnName = "Username",
        toColumnName = "username",
    ),
    RenameColumn(
        tableName = "Authentication_List",
        fromColumnName = "Password",
        toColumnName = "password",
    ),
    RenameColumn(
        tableName = "qs_tiles",
        fromColumnName = "tileId",
        toColumnName = "tile_id",
    ),
    RenameColumn(
        tableName = "qs_tiles",
        fromColumnName = "entityId",
        toColumnName = "entity_id",
    ),
    RenameColumn(
        tableName = "qs_tiles",
        fromColumnName = "shouldVibrate",
        toColumnName = "should_vibrate",
    ),
    RenameColumn(
        tableName = "qs_tiles",
        fromColumnName = "authRequired",
        toColumnName = "auth_required",
    ),
    RenameColumn(
        tableName = "settings",
        fromColumnName = "websocketSetting",
        toColumnName = "websocket_setting",
    ),
    RenameColumn(
        tableName = "settings",
        fromColumnName = "sensorUpdateFrequency",
        toColumnName = "sensor_update_frequency",
    ),
    RenameColumn(
        tableName = "camera_widgets",
        fromColumnName = "entityId",
        toColumnName = "entity_id",
    ),
    RenameColumn(
        tableName = "entityStateComplications",
        fromColumnName = "entityId",
        toColumnName = "entity_id",
    ),
    RenameColumn(
        tableName = "mediaplayctrls_widgets",
        fromColumnName = "entityId",
        toColumnName = "entity_id",
    ),
    RenameColumn(
        tableName = "mediaplayctrls_widgets",
        fromColumnName = "showSkip",
        toColumnName = "show_skip",
    ),
    RenameColumn(
        tableName = "mediaplayctrls_widgets",
        fromColumnName = "showSeek",
        toColumnName = "show_seek",
    ),
    RenameColumn(
        tableName = "mediaplayctrls_widgets",
        fromColumnName = "showVolume",
        toColumnName = "show_volume",
    ),
    RenameColumn(
        tableName = "mediaplayctrls_widgets",
        fromColumnName = "showSource",
        toColumnName = "show_source",
    ),
)
@RenameTable.Entries(
    RenameTable(
        fromTableName = "Authentication_List",
        toTableName = "authentication_list",
    ),
    RenameTable(
        fromTableName = "entityStateComplications",
        toTableName = "entity_state_complications",
    ),
    RenameTable(
        fromTableName = "mediaplayctrls_widgets",
        toTableName = "media_player_controls_widgets",
    ),
)
internal class Migration36to37 : AutoMigrationSpec

private class Migration37to38(private val context: Context) : Migration(37, 38) {
    /**
     * Migrate code has been taken out of an autogenerated migration, to be able to use the context in onPostMigrate
     */
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `button_widgets` ADD COLUMN `server_id` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `camera_widgets` ADD COLUMN `server_id` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "ALTER TABLE `media_player_controls_widgets` ADD COLUMN `server_id` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL("ALTER TABLE `static_widget` ADD COLUMN `server_id` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `template_widgets` ADD COLUMN `server_id` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `notification_history` ADD COLUMN `server_id` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `qs_tiles` ADD COLUMN `server_id` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `servers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_name` TEXT NOT NULL, `name_override` TEXT, `_version` TEXT, `list_order` INTEGER NOT NULL, `device_name` TEXT, `external_url` TEXT NOT NULL, `internal_url` TEXT, `cloud_url` TEXT, `webhook_id` TEXT, `secret` TEXT, `cloudhook_url` TEXT, `use_cloud` INTEGER NOT NULL, `internal_ssids` TEXT NOT NULL, `prioritize_internal` INTEGER NOT NULL, `access_token` TEXT, `refresh_token` TEXT, `token_expiration` INTEGER, `token_type` TEXT, `install_id` TEXT)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_sensors` (`id` TEXT NOT NULL, `server_id` INTEGER NOT NULL DEFAULT 0, `enabled` INTEGER NOT NULL, `registered` INTEGER DEFAULT NULL, `state` TEXT NOT NULL, `last_sent_state` TEXT DEFAULT NULL, `last_sent_icon` TEXT DEFAULT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, `state_class` TEXT, `entity_category` TEXT, `core_registration` TEXT, `app_registration` TEXT, PRIMARY KEY(`id`, `server_id`))",
        )
        connection.execSQL(
            "INSERT INTO `_new_sensors` (`id`,`enabled`,`registered`,`state`,`last_sent_state`,`last_sent_icon`,`state_type`,`type`,`icon`,`name`,`device_class`,`unit_of_measurement`,`state_class`,`entity_category`,`core_registration`,`app_registration`) SELECT `id`,`enabled`,`registered`,`state`,`last_sent_state`,`last_sent_icon`,`state_type`,`type`,`icon`,`name`,`device_class`,`unit_of_measurement`,`state_class`,`entity_category`,`core_registration`,`app_registration` FROM `sensors`",
        )
        connection.execSQL("DROP TABLE `sensors`")
        connection.execSQL("ALTER TABLE `_new_sensors` RENAME TO `sensors`")
        onPostMigrate(connection)
    }

    private fun onPostMigrate(connection: SQLiteConnection) {
        val urlStorage = context.getSharedPreferences("url_0", Context.MODE_PRIVATE)
        val urlExternal = urlStorage.getString("remote_url", null)
        if (urlExternal.isNullOrBlank()) { // Cleanup anything that shouldn't be linked
            connection.execSQL("DELETE FROM `sensors`")
            connection.execSQL("DELETE FROM `sensor_attributes`")
            connection.execSQL("DELETE FROM `sensor_settings`")
            return
        }

        val urlInternal = urlStorage.getString("local_url", null)
        val urlCloud = urlStorage.getString("remote_ui_url", null)
        val urlWebhook = urlStorage.getString("webhook_id", null)
        val urlCloudhook = urlStorage.getString("cloudhook_url", null)
        val urlUseCloud = urlStorage.getBoolean("use_cloud", false)
        val urlInternalSsids = urlStorage.getStringSet("wifi_ssids", emptySet()).orEmpty().toList()
        val urlPrioritizeInternal = urlStorage.getBoolean("prioritize_internal", false)

        val authStorage = context.getSharedPreferences("session_0", Context.MODE_PRIVATE)
        val authAccessToken = authStorage.getString("access_token", null)
        val authRefreshToken = authStorage.getString("refresh_token", null)
        val authTokenExpiration = if (authStorage.contains(
                "expires_date",
            )
        ) {
            authStorage.getLong("expires_date", 0)
        } else {
            null
        }
        val authTokenType = authStorage.getString("token_type", null)
        val authInstallId = if (authStorage.contains("install_id")) {
            authStorage.getString("install_id", "")
        } else {
            val uuid = UUID.randomUUID().toString()
            authStorage.edit { putString("install_id", uuid) }
            uuid
        }

        val integrationStorage = context.getSharedPreferences("integration_0", Context.MODE_PRIVATE)
        val integrationHaVersion = integrationStorage.getString("ha_version", null)
        val integrationDeviceName = integrationStorage.getString("device_name", null)
        val integrationSecret = integrationStorage.getString("secret", null)

        val serverValues = mapOf(
            "_name" to "",
            "name_override" to null,
            "_version" to integrationHaVersion,
            "list_order" to -1,
            "device_name" to integrationDeviceName,
            "external_url" to urlExternal,
            "internal_url" to urlInternal,
            "cloud_url" to urlCloud,
            "webhook_id" to urlWebhook,
            "secret" to integrationSecret,
            "cloudhook_url" to urlCloudhook,
            "use_cloud" to urlUseCloud,
            "internal_ssids" to kotlinJsonMapper.encodeToString(urlInternalSsids),
            "prioritize_internal" to urlPrioritizeInternal,
            "access_token" to authAccessToken,
            "refresh_token" to authRefreshToken,
            "token_expiration" to authTokenExpiration,
            "token_type" to authTokenType,
            // The original only set install_id when an access token was present.
            "install_id" to authInstallId.takeIf { authAccessToken != null },
        )
        val serverId = connection.insertOrReplace("servers", serverValues)

        urlStorage.edit { clear() }
        authStorage.edit {
            remove("access_token")
            remove("refresh_token")
            remove("expires_date")
            remove("token_type")
        }
        integrationStorage.edit {
            remove("ha_version")
            remove("device_name")
            remove("secret")
        }

        // Copy existing DB settings to existing server - ID 0 is used for shared settings.
        // The original processed only the first settings row.
        connection.query("SELECT * FROM `settings`") { row ->
            mapOf(
                "id" to serverId,
                "websocket_setting" to (row.getStringOrNull("websocket_setting") ?: "NEVER"),
                "sensor_update_frequency" to (row.getStringOrNull("sensor_update_frequency") ?: "NORMAL"),
            )
        }.firstOrNull()?.let { connection.insertOrReplace("settings", it) }

        // Attribute existing shared preferences to the existing server
        if (authStorage.contains("biometric_enabled")) {
            authStorage.getBoolean("biometric_enabled", false).let {
                authStorage.edit { putBoolean("${serverId}_biometric_enabled", it) }
            }
        }
        if (authStorage.contains("biometric_home_bypass_enabled")) {
            authStorage.getBoolean("biometric_home_bypass_enabled", false).let {
                authStorage.edit { putBoolean("${serverId}_biometric_home_bypass_enabled", it) }
            }
        }
        authStorage.edit {
            remove("biometric_enabled")
            remove("biometric_home_bypass_enabled")
        }
        if (integrationStorage.contains("sensor_reg_last")) {
            integrationStorage.getLong("sensor_reg_last", 0).let {
                integrationStorage.edit { putLong("${serverId}_sensor_reg_last", it) }
            }
        }
        if (integrationStorage.contains("session_timeout")) {
            integrationStorage.getInt("session_timeout", 0).let {
                integrationStorage.edit { putInt("${serverId}_session_timeout", it) }
            }
        }
        if (integrationStorage.contains("session_expire")) {
            integrationStorage.getLong("session_expire", 0).let {
                integrationStorage.edit { putLong("${serverId}_session_expire", it) }
            }
        }
        if (integrationStorage.contains("sec_warning_last")) {
            integrationStorage.getLong("sec_warning_last", 0).let {
                integrationStorage.edit { putLong("${serverId}_sec_warning_last", it) }
            }
        }
        integrationStorage.edit {
            remove("sensor_reg_last")
            remove("session_timeout")
            remove("session_expire")
            remove("sec_warning_last")
        }

        // Attribute existing rows to the existing server
        connection.execSQL("UPDATE `button_widgets` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `camera_widgets` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `media_player_controls_widgets` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `notification_history` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `qs_tiles` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `sensors` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `static_widget` SET `server_id` = $serverId")
        connection.execSQL("UPDATE `template_widgets` SET `server_id` = $serverId")

        val prefsStorage = context.getSharedPreferences("themes_0", Context.MODE_PRIVATE)
        prefsStorage.getStringSet("controls_auth_entities", null)?.let {
            val newIds = it.map { control -> "$serverId.$control" }.toSet()
            prefsStorage.edit {
                putStringSet("controls_auth_entities", newIds)
            }
        }

        val existingZoneSetting = connection.query(
            "SELECT * FROM `sensor_settings` WHERE `sensor_id` = 'location_background' AND `name` = 'location_ham_only_enter_zone'",
        ) { row -> row.getStringOrNull("value") }.firstOrNull()
        if (!existingZoneSetting.isNullOrBlank()) {
            val newSetting = existingZoneSetting.split(", ").joinToString { zone -> "${serverId}_$zone" }
            connection.execSQL(
                "UPDATE `sensor_settings` SET `value` = '$newSetting' " +
                    "WHERE `sensor_id` = 'location_background' AND `name` = 'location_ham_only_enter_zone'",
            )
        }
    }
}

private class Migration40to41(private val context: Context) : Migration(40, 41) {
    private val iconIdToName: Map<Int, String> by lazy { IconDialogCompat(context.assets).loadAllIcons() }

    private fun SQLiteStatement.getIconName(name: String): String {
        val iconId = getIntByName(name)
        return "mdi:${iconIdToName.getValue(iconId)}"
    }

    override suspend fun migrate(connection: SQLiteConnection) {
        var migrationFailed = false
        val widgets = try {
            connection.query("SELECT * FROM `button_widgets`") { row ->
                mapOf(
                    "id" to row.getString("id"),
                    "server_id" to row.getIntByName("server_id"),
                    "domain" to row.getString("domain"),
                    "service" to row.getString("service"),
                    "service_data" to row.getString("service_data"),
                    "label" to row.getStringOrNull("label"),
                    "background_type" to row.getString("background_type"),
                    "text_color" to row.getStringOrNull("text_color"),
                    "require_authentication" to row.getIntByName("require_authentication"),
                    "icon_name" to row.getIconName("icon_id"),
                )
            }
        } catch (e: Exception) {
            migrationFailed = true
            Timber.e(e, "Unable to migrate, proceeding with recreating the table")
            null
        }
        connection.execSQL("DROP TABLE IF EXISTS `button_widgets`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `button_widgets` (`id` INTEGER NOT NULL, `server_id` INTEGER NOT NULL DEFAULT 0, `icon_name` TEXT NOT NULL, `domain` TEXT NOT NULL, `service` TEXT NOT NULL, `service_data` TEXT NOT NULL, `label` TEXT, `background_type` TEXT NOT NULL DEFAULT 'DAYNIGHT', `text_color` TEXT, `require_authentication` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        )
        widgets?.forEach {
            connection.insertOrReplace("button_widgets", it)
        }
        Timber.d("Migrated ${widgets?.size ?: "no"} button widgets to MDI icon names")

        val tiles = try {
            connection.query("SELECT * FROM `qs_tiles`") { row ->
                buildMap<String, Any?> {
                    put("id", row.getString("id"))
                    put("tile_id", row.getString("tile_id"))
                    put("added", row.getIntByName("added"))
                    put("server_id", row.getIntByName("server_id"))
                    put("entity_id", row.getString("entity_id"))
                    put("label", row.getString("label"))
                    put("subtitle", row.getStringOrNull("subtitle"))
                    put("should_vibrate", row.getIntByName("should_vibrate"))
                    put("auth_required", row.getIntByName("auth_required"))

                    // Only carry over the icon when the old row actually had one.
                    val oldIconColumn = row.columnIndex("icon_id")
                    if (oldIconColumn > -1 && !row.isNull(oldIconColumn)) {
                        put("icon_name", row.getIconName("icon_id"))
                    }
                }
            }
        } catch (e: Exception) {
            migrationFailed = true
            Timber.e(e, "Unable to migrate, proceeding with recreating the table")
            null
        }
        connection.execSQL("DROP TABLE IF EXISTS `qs_tiles`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `qs_tiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tile_id` TEXT NOT NULL, `added` INTEGER NOT NULL DEFAULT 1, `server_id` INTEGER NOT NULL DEFAULT 0, `icon_name` TEXT, `entity_id` TEXT NOT NULL, `label` TEXT NOT NULL, `subtitle` TEXT, `should_vibrate` INTEGER NOT NULL DEFAULT 0, `auth_required` INTEGER NOT NULL DEFAULT 0)",
        )
        tiles?.forEach {
            connection.insertOrReplace("qs_tiles", it)
        }
        Timber.d("Migrated ${tiles?.size ?: "no"} QS tiles to MDI icon names")

        if (migrationFailed) {
            notifyMigrationFailed(context)
        }
    }
}

/**
 * The cached name of a favorite is the display name of the entity, resolved from the entity
 * registry, not its `friendly_name` state attribute anymore.
 */
@RenameColumn(
    tableName = "favorite_cache",
    fromColumnName = "friendly_name",
    toColumnName = "name",
)
internal class Migration52to53 : AutoMigrationSpec
