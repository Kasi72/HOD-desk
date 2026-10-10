package com.hoddesk.app.privacy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

class DatabasePassphraseProvider(context: Context) {
    private val prefs = context.getSharedPreferences("hod_desk_db_key", Context.MODE_PRIVATE)
    private val alias = "hod_desk_database_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun get(): ByteArray {
        val existing = prefs.getString("ciphertext", null)
        if (existing != null) {
            val raw = Base64.decode(existing, Base64.DEFAULT)
            val iv = raw.copyOfRange(0, 12)
            return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }.doFinal(raw.copyOfRange(12, raw.size))
        }
        val passphrase = Random.nextBytes(32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs.edit().putString("ciphertext", Base64.encodeToString(cipher.iv + cipher.doFinal(passphrase), Base64.NO_WRAP)).apply()
        return passphrase
    }
}
