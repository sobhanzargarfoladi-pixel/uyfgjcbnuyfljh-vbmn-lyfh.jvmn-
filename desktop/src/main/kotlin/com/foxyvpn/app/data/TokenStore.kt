package com.foxyvpn.app.data

import com.foxyvpn.app.data.model.RuntimeAuth
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

/** Desktop token store: a user-only (chmod 600) file under ~/Library/Application Support/FoxyVPN. */
class TokenStore(dir: File = defaultDir()) {
    private val file = File(dir, "session.properties").also { it.parentFile.mkdirs() }

    @Synchronized
    fun saveAuth(auth: RuntimeAuth) {
        val p = Properties()
        p.setProperty("access_token", auth.accessToken)
        auth.refreshToken?.let { p.setProperty("refresh_token", it) }
        p.setProperty("expires_at", auth.expiresAtEpochSeconds.toString())
        file.outputStream().use { p.store(it, null) }
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
    }

    @Synchronized
    fun loadAuth(): RuntimeAuth? = runCatching {
        if (!file.exists()) return@runCatching null
        val p = Properties().also { pr -> file.inputStream().use { pr.load(it) } }
        val access = p.getProperty("access_token")?.takeIf { it.isNotBlank() } ?: return@runCatching null
        RuntimeAuth(access, p.getProperty("refresh_token")?.takeIf { it.isNotBlank() }, p.getProperty("expires_at")?.toLongOrNull() ?: 0L)
    }.getOrNull()

    fun hasValidAccessToken(): Boolean {
        val auth = loadAuth() ?: return false
        if (auth.expiresAtEpochSeconds <= 0L) return false
        return auth.expiresAtEpochSeconds - System.currentTimeMillis() / 1000 > 60L
    }

    fun hasStoredSession(): Boolean = loadAuth() != null
    fun hasRefreshToken(): Boolean = loadAuth()?.refreshToken != null
    fun hasValidSession(): Boolean = hasValidAccessToken()

    @Synchronized
    fun clear() { file.delete() }

    companion object {
        fun defaultDir(): File = File(System.getProperty("user.home"), "Library/Application Support/FoxyVPN")
    }
}
