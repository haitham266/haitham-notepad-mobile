package com.haitham.notes.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * التشفير المحلي: كل الملاحظات والصور تُحفظ مشفرة بـ AES-256-GCM
 * بمفتاح داخل Android Keystore (لا يمكن استخراجه من الجهاز).
 */
object LocalCrypto {
    private const val ALIAS = "haitham_notes_master"
    private val key: SecretKey by lazy { loadOrCreate() }

    private fun loadOrCreate(): SecretKey {
        // بيئة الاختبار (Robolectric) لا تحتوي Keystore؛ لا يُستخدم هذا أبداً على جهاز حقيقي
        if (android.os.Build.FINGERPRINT == "robolectric") return SecretKeySpec(ByteArray(32), "AES")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    fun encryptString(s: String) = encrypt(s.toByteArray(Charsets.UTF_8))
    fun decryptString(b: ByteArray) = String(decrypt(b), Charsets.UTF_8)
}

/** تشفير النسخ السحابية بمفتاح عشوائي (256-bit) مشترك بين أجهزة نفس الحساب. */
object BoxCrypto {
    private val MAGIC = "HNB1".toByteArray()
    private val rnd = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        return MAGIC + iv + c.doFinal(plain)
    }

    fun open(key: ByteArray, box: ByteArray): ByteArray {
        require(box.size > 16 && box.copyOfRange(0, 4).contentEquals(MAGIC)) { "ملف غير صالح" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box, 4, 12))
        c.updateAAD(MAGIC)
        return c.doFinal(box, 16, box.size - 16)
    }
}
