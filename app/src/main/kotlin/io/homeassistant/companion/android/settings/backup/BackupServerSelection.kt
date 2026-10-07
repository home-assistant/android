package io.homeassistant.companion.android.settings.backup

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.backup.SettingsBackupData

/** Not answering a destination question is different from deliberately excluding that server. */
internal sealed interface BackupServerTarget {
    data object Unselected : BackupServerTarget
    data object Skip : BackupServerTarget
    data class Server(val id: Int) : BackupServerTarget
}

internal data class BackupServerSelection(
    val reference: String,
    val name: String,
    val target: BackupServerTarget,
    val destinationName: String?,
) {
    val included: Boolean = target != BackupServerTarget.Skip
}

internal data class BackupServerSelections(
    val rows: List<BackupServerSelection>,
    val mapping: Map<String, Int>,
    @StringRes val problem: Int?,
) {
    val canExcludeServers: Boolean = rows.size > 1
}

/** Only ask for destinations used by the selected sections, including server references in zones. */
internal fun backupServerSelections(
    backup: SettingsBackupData,
    targets: Map<String, BackupServerTarget>,
    destinations: List<BackupDestination>,
): BackupServerSelections {
    val zoneServers = backup.sensorOptions.orEmpty().flatMap { sensor ->
        sensor.options.values.flatMap { it.zones.orEmpty() }.map { it.server }
    }.toSet()
    val serverSections = backup.servers.filter {
        backup.favorites != null || it.sensors != null || it.persistentConnection != null
    }.map { it.reference }.toSet()
    val rows = backup.servers.filter { it.reference in serverSections || it.reference in zoneServers }.map { server ->
        val target = targets[server.reference] ?: BackupServerTarget.Unselected
        BackupServerSelection(
            server.reference,
            server.name,
            target,
            (target as? BackupServerTarget.Server)?.let { selected ->
                destinations.find { it.id == selected.id }?.name
            },
        )
    }
    val mapping = rows.mapNotNull { row ->
        (row.target as? BackupServerTarget.Server)?.let { row.reference to it.id }
    }.toMap()
    return BackupServerSelections(rows, mapping, mappingProblem(rows, mapping, destinations, serverSections))
}

@StringRes
private fun mappingProblem(
    rows: List<BackupServerSelection>,
    mapping: Map<String, Int>,
    destinations: List<BackupDestination>,
    serverSections: Set<String>,
): Int? = when {
    rows.isNotEmpty() && destinations.isEmpty() -> R.string.backup_sign_in_first
    rows.any { it.target is BackupServerTarget.Unselected } -> R.string.backup_choose_servers
    rows.any {
        it.target is BackupServerTarget.Server && it.destinationName == null
    } -> R.string.backup_choose_servers
    mapping.values.distinct().size != mapping.size -> R.string.backup_duplicate_destination
    serverSections.isNotEmpty() && mapping.keys.none { it in serverSections } -> R.string.backup_select_one_server
    else -> null
}
