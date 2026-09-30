package dev.youximi.appmapper.data

import android.content.Context
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
)

class PairingStore(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, "config/paired-computer.json")

    @Synchronized
    fun read(): PairedComputer? {
        if (!file.exists()) return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return PairedComputer(
            json.getString("host"), json.getInt("port"), json.getString("serverId"),
            json.getString("fingerprint"), json.getString("deviceId"),
            json.getString("keyAlias"), json.getString("name"),
        )
    }

    @Synchronized
    fun save(computer: PairedComputer) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("host", computer.host).put("port", computer.port)
            .put("serverId", computer.serverId).put("fingerprint", computer.fingerprint)
            .put("deviceId", computer.deviceId).put("keyAlias", computer.keyAlias)
            .put("name", computer.name)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            temporary.writeText(json.toString(), Charsets.UTF_8)
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun clear() {
        if (file.exists() && !file.delete()) error("Could not delete pairing record")
    }
}
