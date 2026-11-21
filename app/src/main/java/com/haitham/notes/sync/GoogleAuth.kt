package com.haitham.notes.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * تسجيل الدخول بحساب جوجل وطلب صلاحية Google Drive.
 * - drive.file: يرى التطبيق فقط الملفات التي أنشأها (مجلد Haitham Notes)، لا يرى بقية ملفاتك.
 * - drive.appdata: مجلد مخفي خاص بالتطبيق لحفظ مفتاح التشفير.
 */
object GoogleAuth {
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/drive.file"),
        Scope("https://www.googleapis.com/auth/drive.appdata"),
        Scope("email"),
        Scope("profile"),
    )

    private fun request(email: String?) = AuthorizationRequest.builder()
        .setRequestedScopes(scopes)
        .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
        .build()

    /** يعيد النتيجة؛ إذا كانت تحتاج موافقة المستخدم فـ hasResolution() = true */
    suspend fun authorize(ctx: Context, email: String?): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).authorize(request(email)).await()

    fun fromIntent(ctx: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)

    /** توكن صالح بدون واجهة (للمزامنة في الخلفية)، أو null إذا يلزم تسجيل الدخول من جديد */
    suspend fun silentToken(ctx: Context, email: String): String? = runCatching {
        val r = authorize(ctx, email)
        if (r.hasResolution()) null else r.accessToken
    }.getOrNull()
}
