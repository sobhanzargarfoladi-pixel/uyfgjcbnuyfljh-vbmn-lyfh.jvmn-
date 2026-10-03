package com.foxyvpn.desktop

import com.foxyvpn.app.data.AppLogger

/** Points macOS's system-wide SOCKS proxy at the local FoxyVPN listener (and back). */
object MacProxy {
    private const val TAG = "MacProxy"
    private val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun sh(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun run(vararg cmd: String): Pair<Int, String> {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return p.waitFor() to out
    }

    private fun services(): List<String> =
        run("networksetup", "-listallnetworkservices").second.lines().drop(1)
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("*") }

    private fun apply(commands: List<String>) {
        val script = commands.joinToString("; ")
        val (code, out) = run("/bin/sh", "-c", script)
        if (code == 0) return
        AppLogger.w(TAG, "networksetup needs admin rights ($out); asking macOS for permission")
        val apple = script.replace("\\", "\\\\").replace("\"", "\\\"")
        val (code2, out2) = run("osascript", "-e", "do shell script \"$apple\" with administrator privileges")
        if (code2 != 0) error("Could not change the system proxy: $out2")
    }

    fun enable(host: String, port: Int) {
        if (!isMac) { AppLogger.w(TAG, "not macOS; set your SOCKS5 proxy to $host:$port manually"); return }
        apply(services().flatMap {
            listOf(
                "networksetup -setsocksfirewallproxy ${sh(it)} $host $port",
                "networksetup -setsocksfirewallproxystate ${sh(it)} on",
            )
        })
    }

    fun disable() {
        if (!isMac) return
        runCatching { apply(services().map { "networksetup -setsocksfirewallproxystate ${sh(it)} off" }) }
            .onFailure { AppLogger.w(TAG, "could not switch the system proxy off", it) }
    }
}
