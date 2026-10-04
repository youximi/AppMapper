package dev.youximi.appmapper.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PairedComputer(
    val host: String,
    val port: Int,
    val serverId: String,
    val fingerprint: String,
    val deviceId: String,
    val keyAlias: String,
    val name: String,
    val addedAtMillis: Long? = null,
)

data class PairingState(
    val computers: List<PairedComputer> = emptyList(),
    val selectedServerId: String? = null,
) {
    val selectedComputer: PairedComputer?
        get() = computers.firstOrNull { it.serverId == selectedServerId }
}

class PairingStore internal constructor(private val file: File) {
    constructor(context: Context) : this(
        File(context.applicationContext.noBackupFilesDir, "config/paired-computer.json"),
    )

    fun read(): PairingState = synchronized(lock) {
        if (!file.exists()) return@synchronized PairingState()
        val json = JSONObject(file.readText(Charsets.UTF_8))
        // Keep the original record and key when opening a version 1 installation.
        if (!json.has("computers")) {
            val computer = readComputer(json)
            return@synchronized PairingState(listOf(computer), computer.serverId)
        }
        require(json.getInt("version") == 2) { "Unsupported pairing storage version" }
        val computers = json.getJSONArray("computers").let { entries ->
            List(entries.length()) { readComputer(entries.getJSONObject(it)) }
        }
        val selected = if (json.isNull("selectedServerId")) null else json.getString("selectedServerId")
        require(computers.map { it.serverId }.distinct().size == computers.size)
        require(selected == null || computers.any { it.serverId == selected })
        PairingState(computers, selected)
    }

    /** Returns only this computer's replaced record so other computers keep their keys. */
    fun save(computer: PairedComputer): PairedComputer? = synchronized(lock) {
        val state = read()
        val previous = state.computers.firstOrNull { it.serverId == computer.serverId }
        val saved = if (previous != null) computer.copy(addedAtMillis = previous.addedAtMillis) else computer
        val computers = if (previous == null) state.computers + saved else
            state.computers.map { if (it.serverId == saved.serverId) saved else it }
        write(PairingState(computers, saved.serverId))
        previous
    }

    fun select(serverId: String): PairedComputer = synchronized(lock) {
        val state = read()
        val computer = state.computers.firstOrNull { it.serverId == serverId }
            ?: error("The selected computer is no longer saved")
        write(state.copy(selectedServerId = serverId))
        computer
    }

    fun remove(serverId: String): PairedComputer? = synchronized(lock) {
        val state = read()
        val computer = state.computers.firstOrNull { it.serverId == serverId } ?: return@synchronized null
        write(PairingState(
            state.computers.filterNot { it.serverId == serverId },
            state.selectedServerId.takeUnless { it == serverId },
        ))
        computer
    }

    private fun readComputer(json: JSONObject) = PairedComputer(
        json.getString("host"), json.getInt("port"), json.getString("serverId"),
        json.getString("fingerprint"), json.getString("deviceId"),
        json.getString("keyAlias"), json.getString("name"),
        if (json.isNull("addedAtMillis")) null else json.getLong("addedAtMillis"),
    )

    private fun write(state: PairingState) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("version", 2)
            .put("selectedServerId", state.selectedServerId ?: JSONObject.NULL)
            .put("computers", JSONArray().apply {
                state.computers.forEach { computer ->
                    put(JSONObject()
                        .put("host", computer.host).put("port", computer.port)
                        .put("serverId", computer.serverId).put("fingerprint", computer.fingerprint)
                        .put("deviceId", computer.deviceId).put("keyAlias", computer.keyAlias)
                        .put("name", computer.name)
                        .put("addedAtMillis", computer.addedAtMillis ?: JSONObject.NULL))
                }
            })
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            temporary.writeText(json.toString(), Charsets.UTF_8)
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            revision.value += 1
        } finally {
            temporary.delete()
        }
    }

    companion object {
        // UI and service construct separate stores; synchronize transactions across both.
        private val lock = Any()
        private val revision = MutableStateFlow(0L)
        val changes = revision.asStateFlow()
    }
}
