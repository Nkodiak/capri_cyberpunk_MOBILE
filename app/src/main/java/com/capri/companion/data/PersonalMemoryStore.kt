
package com.capri.companion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PersonalMemory(
    val id: String,
    val category: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

class PersonalMemoryStore(context: Context) {
    private val file = AtomicFile(
        File(context.filesDir, "personal-memory.enc")
    )

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                    )
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )
    }

    private fun decrypt(value: ByteArray): String {
        require(value.size > IV_SIZE) {
            "El archivo de memoria está incompleto."
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(
                128,
                value.copyOfRange(0, IV_SIZE)
            )
        )

        return String(
            cipher.doFinal(value.copyOfRange(IV_SIZE, value.size)),
            Charsets.UTF_8
        )
    }

    @Synchronized
    fun load(): List<PersonalMemory> {
        if (!file.baseFile.exists()) return emptyList()

        val encrypted = file.readFully()
        val json = JSONArray(decrypt(encrypted))

        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)

            PersonalMemory(
                id = item.getString("id"),
                category = item.getString("category"),
                content = item.getString("content"),
                createdAt = item.getLong("createdAt"),
                updatedAt = item.getLong("updatedAt")
            )
        }.takeLast(MAX_MEMORIES)
    }

    @Synchronized
    fun save(memories: List<PersonalMemory>) {
        require(memories.size <= MAX_MEMORIES) {
            "Se alcanzó el límite de recuerdos."
        }

        val json = JSONArray().apply {
            memories.forEach { memory ->
                put(
                    JSONObject()
                        .put("id", memory.id)
                        .put("category", memory.category)
                        .put("content", memory.content)
                        .put("createdAt", memory.createdAt)
                        .put("updatedAt", memory.updatedAt)
                )
            }
        }

        val encrypted = encrypt(json.toString())
        val output = file.startWrite()

        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    @Synchronized
    fun erase() {
        file.delete()
    }

    companion object {
        private const val KEY_ALIAS = "capri.personal.memory"
        private const val IV_SIZE = 12
        const val MAX_MEMORIES = 300
    }
}
