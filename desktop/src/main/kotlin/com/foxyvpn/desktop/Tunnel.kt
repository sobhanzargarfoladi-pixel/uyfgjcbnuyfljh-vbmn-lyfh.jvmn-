package com.foxyvpn.desktop

import com.foxyvpn.app.data.AppLogger
import com.foxyvpn.app.data.FxaAuthRepository
import com.foxyvpn.app.data.GUARDIAN_ENDPOINT_DEFAULT
import com.foxyvpn.app.data.GuardianClient
import com.foxyvpn.app.data.ProxyPass
import com.foxyvpn.app.data.TokenInvalidError
import com.foxyvpn.app.data.model.ProxyCandidate
import com.foxyvpn.app.vpn.socks.LocalSocks5Server
import com.foxyvpn.app.vpn.upstream.H2UpstreamSession
import com.foxyvpn.app.vpn.upstream.UpstreamSession
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "Tunnel"
const val LOCAL_PORT = 1080

/** Firefox VPN HTTP/2 tunnel + local SOCKS5 listener + macOS system proxy. */
class Tunnel(private val auth: FxaAuthRepository) {
    private val guardian = GuardianClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var candidates: List<ProxyCandidate> = emptyList()
    private var socks: LocalSocks5Server? = null
    private var refresher: Job? = null
    @Volatile private var session: UpstreamSession? = null
    @Volatile private var lastRedial = 0L
    @Volatile private var passExpiry: Long? = null

    val running: Boolean get() = socks != null

    private suspend fun mintPass(): ProxyPass {
        val token = auth.ensureFreshAccessToken().accessToken
        val pass = try {
            guardian.fetchProxyPass(GUARDIAN_ENDPOINT_DEFAULT, token)
        } catch (e: TokenInvalidError) {
            guardian.activateGuardian(GUARDIAN_ENDPOINT_DEFAULT, token)
            guardian.fetchProxyPass(GUARDIAN_ENDPOINT_DEFAULT, token)
        }
        passExpiry = pass.expiresAtEpochSeconds
        return pass
    }

    private suspend fun dial(): H2UpstreamSession {
        var last: Throwable? = null
        for (c in candidates) {
            val s = try {
                val pass = mintPass()
                H2UpstreamSession(c.host, c.port, pass.token, null, null)
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { last = e; continue }
            try {
                withTimeout(20_000) { s.connect() }
                AppLogger.i(TAG, "tunnel established to ${c.authority}")
                return s
            } catch (e: TimeoutCancellationException) {
                last = e; runCatching { s.close() }
            } catch (e: CancellationException) {
                runCatching { s.close() }; throw e
            } catch (e: Throwable) {
                last = e; runCatching { s.close() }
                AppLogger.w(TAG, "dial to ${c.authority} failed: ${e.message}")
            }
        }
        throw IllegalStateException("No server reachable: ${last?.message}", last)
    }

    private suspend fun redial() = mutex.withLock {
        if (System.currentTimeMillis() - lastRedial < 10_000) return
        lastRedial = System.currentTimeMillis()
        val old = session
        runCatching { session = dial(); old?.close() }
            .onFailure { AppLogger.w(TAG, "re-dial failed", it) }
    }

    suspend fun start(list: List<ProxyCandidate>) = mutex.withLock {
        check(list.isNotEmpty()) { "No servers available for that location" }
        candidates = list
        session = dial()
        val server = LocalSocks5Server("127.0.0.1", LOCAL_PORT, false, { session }) { scope.launch { redial() } }
        server.start()
        socks = server
        withContext(Dispatchers.IO) { MacProxy.enable("127.0.0.1", LOCAL_PORT) }
        refresher = scope.launch {
            while (isActive) {
                val wait = passExpiry?.let { it * 1000 - System.currentTimeMillis() - 120_000 } ?: 20 * 60_000L
                delay(wait.coerceAtLeast(60_000L))
                runCatching { session?.updateBearerToken(mintPass().token) }
                    .onFailure { AppLogger.w(TAG, "could not refresh the proxy pass", it) }
            }
        }
    }

    suspend fun stop() = mutex.withLock {
        refresher?.cancel(); refresher = null
        withContext(Dispatchers.IO) { MacProxy.disable() }
        runCatching { socks?.stop() }; socks = null
        runCatching { session?.close() }; session = null
    }
}
