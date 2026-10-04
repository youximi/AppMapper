package dev.youximi.appmapper.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PairingStoreTest {
    @get:Rule val directory = TemporaryFolder()

    private fun record(id: String, addedAt: Long? = 1000L) = PairedComputer(
        "192.168.1.10", 8765, id, "fingerprint-$id", "phone", "key-$id", "电脑 $id", addedAt,
    )

    private fun storageFile() = File(directory.root, "config/paired-computer.json")

    @Test fun legacyRecordKeepsCredentialsAndUnknownDateWhenAddingAnotherComputer() {
        val legacy = record("A", null)
        val file = storageFile().apply { parentFile!!.mkdirs() }
        file.writeText(JSONObject()
            .put("host", legacy.host).put("port", legacy.port).put("serverId", legacy.serverId)
            .put("fingerprint", legacy.fingerprint).put("deviceId", legacy.deviceId)
            .put("keyAlias", legacy.keyAlias).put("name", legacy.name).toString())
        val original = file.readText()
        val store = PairingStore(file)
        assertEquals(PairingState(listOf(legacy), "A"), store.read())
        assertEquals(original, file.readText())

        store.save(record("B"))

        assertEquals(PairingState(listOf(legacy, record("B")), "B"), PairingStore(file).read())
        assertEquals(2, JSONObject(file.readText()).getInt("version"))
    }

    @Test fun rescanUpdatesOnlyMatchingIdentityAndKeepsOriginalAddedTime() {
        val store = PairingStore(storageFile())
        val first = record("A")
        val second = record("B", 2000L)
        store.save(first)
        store.save(second)
        val updated = first.copy(host = "192.168.1.25", port = 9000,
            name = "重新命名", keyAlias = "replacement-key", addedAtMillis = 3000L)

        assertEquals(first, store.save(updated))

        assertEquals(listOf(updated.copy(addedAtMillis = 1000L), second), store.read().computers)
        assertEquals("A", store.read().selectedServerId)
    }

    @Test fun rescanOfLegacyComputerDoesNotInventItsOriginalDate() {
        val store = PairingStore(storageFile())
        store.save(record("A", null))
        store.save(record("A", 5000L).copy(keyAlias = "new-key"))
        assertNull(store.read().selectedComputer!!.addedAtMillis)
    }

    @Test fun forgettingNonSelectedComputerPreservesSelectedComputerAndKey() {
        val store = PairingStore(storageFile())
        store.save(record("A"))
        store.save(record("B"))

        assertEquals(record("A"), store.remove("A"))

        assertEquals(PairingState(listOf(record("B")), "B"), store.read())
    }

    @Test fun forgettingSelectedComputerDoesNotChooseAnotherTarget() {
        val store = PairingStore(storageFile())
        store.save(record("A"))
        store.save(record("B"))
        store.remove("B")
        val reopened = PairingStore(storageFile()).read()
        assertEquals(listOf(record("A")), reopened.computers)
        assertNull(reopened.selectedComputer)
        assertNull(reopened.selectedServerId)
    }

    @Test fun selectingComputerPersistsWithoutChangingAnyCredentials() {
        val file = storageFile()
        val store = PairingStore(file)
        store.save(record("A"))
        store.save(record("B"))
        assertEquals(record("A"), store.select("A"))
        assertEquals(PairingState(listOf(record("A"), record("B")), "A"), PairingStore(file).read())
    }

    @Test fun unknownSelectionDoesNotOverwriteExistingRecords() {
        val file = storageFile()
        val store = PairingStore(file)
        store.save(record("A"))
        val original = file.readText()
        assertThrows(IllegalStateException::class.java) { store.select("missing") }
        assertEquals(original, file.readText())
    }

    @Test fun damagedStorageCannotBeOverwrittenByEnrollmentOrRemoval() {
        val file = storageFile().apply { parentFile!!.mkdirs(); writeText("damaged record") }
        val store = PairingStore(file)
        assertThrows(org.json.JSONException::class.java) { store.save(record("B")) }
        assertThrows(org.json.JSONException::class.java) { store.remove("A") }
        assertEquals("damaged record", file.readText())
    }

    @Test fun differentStoreInstancesDoNotLoseEachOthersComputers() {
        val file = storageFile()
        val first = PairingStore(file)
        val second = PairingStore(file)
        val saves = (1..20).map { number ->
            Thread { (if (number % 2 == 0) first else second).save(record(number.toString())) }
        }
        saves.forEach { it.start() }
        saves.forEach { it.join() }
        assertEquals((1..20).map { it.toString() }.toSet(), first.read().computers.map { it.serverId }.toSet())
    }
}
