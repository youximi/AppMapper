package dev.youximi.appmapper.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

object DeviceKeyStore {
    fun create(): Pair<String, String> {
        val alias = "appmapper-${UUID.randomUUID()}"
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        val pair = generator.generateKeyPair()
        return alias to Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    fun sign(alias: String, payload: ByteArray): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(alias, null) ?: error("Pairing key is unavailable")
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(key as java.security.PrivateKey)
        signer.update(payload)
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }

    fun delete(alias: String) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.deleteEntry(alias)
    }
}
