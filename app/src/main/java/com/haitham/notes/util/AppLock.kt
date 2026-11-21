package com.haitham.notes.util

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.haitham.notes.data.Prefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** قفل التطبيق برمز PIN (مخزّن كـ PBKDF2 وليس كنص) مع خيار البصمة. */
object AppLock {
    fun enabled(p: Prefs) = p.pinHash != null

    fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return Base64.encodeToString(k, Base64.NO_WRAP)
    }

    fun setPin(p: Prefs, pin: String?) {
        if (pin == null) { p.pinHash = null; p.pinSalt = null; p.biometric = false; return }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.pinSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
        p.pinHash = hash(pin, salt)
    }

    fun check(p: Prefs, pin: String): Boolean {
        val salt = p.pinSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), (p.pinHash ?: "").toByteArray())
    }

    /** قفل قسم: يعيد (بصمة الرمز، الملح) لتخزينهما داخل القسم */
    fun categoryLock(pin: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return hash(pin, salt) to Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /** يقبل رمز القسم، أو رمز قفل التطبيق كرمز احتياطي إذا نسيت رمز القسم */
    fun checkCategory(c: com.haitham.notes.data.Category, p: Prefs, pin: String): Boolean {
        val h = c.pinHash ?: return true
        val salt = c.pinSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        if (MessageDigest.isEqual(hash(pin, salt).toByteArray(), h.toByteArray())) return true
        return enabled(p) && check(p, pin)
    }

    fun biometricAvailable(a: FragmentActivity) =
        BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptBiometric(a: FragmentActivity, onOk: () -> Unit) {
        val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح Haitham Notes")
                .setSubtitle("استخدم البصمة")
                .setNegativeButtonText("استخدام الرمز")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
}
