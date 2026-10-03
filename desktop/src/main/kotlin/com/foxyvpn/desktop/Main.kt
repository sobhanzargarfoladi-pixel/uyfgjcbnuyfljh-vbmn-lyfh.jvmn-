package com.foxyvpn.desktop

import com.foxyvpn.app.data.*
import com.foxyvpn.app.data.model.VpnCountry
import kotlinx.coroutines.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater { App().show() }
}

private class App {
    private val store = TokenStore()
    private val repo = FxaAuthRepository(store)
    private val tunnel = Tunnel(repo)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val frame = JFrame("FoxyVPN")
    private val cards = CardLayout()
    private val root = JPanel(cards)
    private val status = JLabel(" ", SwingConstants.CENTER)

    private val email = JTextField(20)
    private val password = JPasswordField(20)
    private val code = JTextField(10)
    private val signIn = JButton("Sign in")
    private val verify = JButton("Verify code")
    private val codeRow = JPanel(FlowLayout()).apply { isVisible = false }

    private val locations = JComboBox<Loc>()
    private val power = JButton("Connect")
    private var countries: List<VpnCountry> = emptyList()

    private data class Loc(val code: String, val name: String) { override fun toString() = name }

    fun show() {
        root.add(loginPanel(), "login")
        root.add(mainPanel(), "main")
        frame.layout = BorderLayout()
        frame.add(root, BorderLayout.CENTER)
        frame.add(status, BorderLayout.SOUTH)
        frame.setSize(380, 300)
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) { quit() }
        })
        Runtime.getRuntime().addShutdownHook(Thread { MacProxy.disable() })
        frame.isVisible = true
        scope.launch {
            val s = repo.restoreSession()
            ui { if (s == SessionStatus.NEEDS_LOGIN) cards.show(root, "login") else enterMain() }
        }
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)
    private fun say(text: String) = ui { status.text = text }

    private fun loginPanel() = JPanel(GridLayout(0, 1, 4, 4)).apply {
        border = BorderFactory.createEmptyBorder(16, 24, 8, 24)
        add(JLabel("Firefox account email")); add(email)
        add(JLabel("Password")); add(password)
        add(signIn)
        codeRow.add(JLabel("Code")); codeRow.add(code); codeRow.add(verify); add(codeRow)
        signIn.addActionListener {
            signIn.isEnabled = false; say("Signing in…")
            scope.launch {
                repo.startLogin(email.text.trim(), String(password.password))
                    .onSuccess { needs2fa ->
                        ui { signIn.isEnabled = true
                            if (needs2fa) { codeRow.isVisible = true; status.text = "Enter the code sent to your email" }
                            else enterMain() }
                    }
                    .onFailure { e -> ui { signIn.isEnabled = true; status.text = "Sign-in failed: ${e.message}" } }
            }
        }
        verify.addActionListener {
            scope.launch {
                repo.submitTwoFactorCode(code.text.trim())
                    .onSuccess { ui { enterMain() } }
                    .onFailure { e -> say("Code rejected: ${e.message}") }
            }
        }
    }

    private fun mainPanel() = JPanel(GridLayout(0, 1, 6, 6)).apply {
        border = BorderFactory.createEmptyBorder(16, 24, 8, 24)
        add(JLabel("Location")); add(locations); add(power)
        add(JButton("Copy logs").apply {
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(AppLogger.exportAsText()), null)
                say("Logs copied")
            }
        })
        add(JButton("Sign out").apply {
            addActionListener { scope.launch { tunnel.stop(); store.clear(); ui { cards.show(root, "login"); status.text = " " } } }
        })
        power.addActionListener { toggle() }
    }

    private fun enterMain() {
        cards.show(root, "main"); say("Loading locations…")
        scope.launch {
            runCatching { ServerListClient().fetchCountries() }
                .onSuccess { list ->
                    countries = list
                    ui {
                        locations.removeAllItems()
                        locations.addItem(Loc(RECOMMENDED_COUNTRY_CODE, "Recommended"))
                        list.sortedBy { it.name }.forEach { locations.addItem(Loc(it.code, it.name)) }
                        status.text = "Ready"
                    }
                }
                .onFailure { e -> say("Could not load locations: ${e.message}") }
        }
    }

    private fun toggle() {
        power.isEnabled = false
        if (tunnel.running) {
            say("Disconnecting…")
            scope.launch { tunnel.stop(); ui { power.text = "Connect"; power.isEnabled = true; status.text = "Disconnected" } }
            return
        }
        val loc = locations.selectedItem as? Loc ?: run { power.isEnabled = true; return }
        say("Connecting…")
        scope.launch {
            runCatching {
                var list = ServerListClient.candidatesForCountry(countries, loc.code)
                if (list.isEmpty()) list = countries.flatMap { ServerListClient.candidatesForCountry(countries, it.code) }.shuffled()
                tunnel.start(list)
            }.onSuccess {
                ui { power.text = "Disconnect"; power.isEnabled = true; status.text = "Connected — system SOCKS proxy on 127.0.0.1:$LOCAL_PORT" }
            }.onFailure { e ->
                runCatching { tunnel.stop() }
                ui { power.isEnabled = true; status.text = "Failed: ${e.message}" }
            }
        }
    }

    private fun quit() {
        scope.launch { runCatching { tunnel.stop() }; System.exit(0) }
    }
}
