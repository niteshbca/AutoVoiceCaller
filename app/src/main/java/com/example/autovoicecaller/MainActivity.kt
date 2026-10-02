package com.example.autovoicecaller

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var number: EditText
    private lateinit var message: EditText
    private lateinit var maximum: EditText
    private lateinit var delay: EditText
    private lateinit var numberList: LinearLayout
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var roleText: TextView
    private lateinit var manualButton: Button
    private lateinit var continueButton: Button
    private lateinit var callPanel: LinearLayout
    private lateinit var monitor: PhoneMonitor
    private val numbers = mutableListOf<String>()
    private var resumed = false
    private val permissionResult = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasCallPermissions()) monitor.start() else Session.show("Call permission denied")
        render()
    }
    private val roleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { render() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = PhoneMonitor(this)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 24); setBackgroundColor(Color.rgb(242, 246, 244)) }
        scroll.addView(root); setContentView(scroll)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(24 + bars.left, 20 + bars.top, 24 + bars.right, 24 + bars.bottom)
            } else { @Suppress("DEPRECATION")
                view.setPadding(24, 20 + insets.systemWindowInsetTop, 24, 24 + insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
        label(getString(R.string.app_name), 25f)
        label(getString(R.string.physical_path), 14f)
        roleText = label("")
        button("Enable automatic mode (default Phone app)") { requestRole() }
        button("Grant call / notification permissions") { requestPermissions() }
        
        // Server mode toggle
        val serverToggle = CheckBox(this).apply {
            text = "Use Server Mode (calls via backend - audio plays on client side)"
            isChecked = Session.useServer
            setOnCheckedChangeListener { _, checked ->
                Session.setServerMode(checked)
                render()
            }
        }
        root.addView(serverToggle)
        
        // Provider spinner
        val providerSpinner = Spinner(this).apply {
            val providers = arrayOf("Custom Server", "Exotel", "Knowlarity", "Plivo", "Twilio")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, providers)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val provider = ServerCaller.Provider.values()[position]
                    Session.serverCaller?.setProvider(provider)
                    if (provider != ServerCaller.Provider.CUSTOM) {
                        showProviderConfigDialog(provider)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        root.addView(providerSpinner)
        
        number = input("Customer phone number (+91 optional)", InputType.TYPE_CLASS_PHONE)
        button("Call entered number once (normal Phone call)") {
            if (Session.running) { Session.show("Stop queue before making another call"); return@button }
            val value = number.text.toString().trim()
            if (!Regex("[+0-9*#]{1,20}").matches(value)) { Session.show("Enter a valid dialable number"); return@button }
            if (!hasCallPermissions()) { requestPermissions(); return@button }
            try { getSystemService(TelecomManager::class.java).placeCall(android.net.Uri.fromParts("tel", value, null), Bundle()) }
            catch (_: SecurityException) { Session.show("Call permission denied") }
            catch (_: Exception) { Session.show("Phone call cannot be started") }
        }
        button("Add number") {
            if (Session.running) { Session.show("Stop session before editing queue"); return@button }
            val normalized = NumberRules.normalize(number.text.toString())
            if (normalized == null) Session.show("Invalid phone number: use 7–15 digits, optional +")
            else if (numbers.size >= 50) Session.show("Prototype queue hard limit: 50")
            else if (numbers.contains(normalized)) Session.show("Number already in queue")
            else { numbers.add(normalized); number.text.clear(); renderNumbers(); saveDraft() }
        }
        numberList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(numberList)
        message = input("Message to speak (Hindi voice; max 4000 characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply { minLines = 4 }
        delay = input("Delay after speech in seconds (0–30)", InputType.TYPE_CLASS_NUMBER)
        maximum = input("Maximum calls per session (1–50)", InputType.TYPE_CLASS_NUMBER)
        button("Start Calling") { confirmStart() }
        button("Stop") { Session.stop() }
        manualButton = button("Customer answered — speak message") {
            AlertDialog.Builder(this).setTitle("Has the customer answered?")
                .setMessage("OFFHOOK includes ringing/dialing. Only confirm after hearing the customer's answer. Enable Speaker in the system Phone app first.")
                .setPositiveButton("Yes, answered") { _, _ -> Session.confirmAnswered() }.setNegativeButton("Cancel", null).show()
        }
        continueButton = button("Continue / call next number") { Session.continueQueue() }
        status = label("Ready", 17f)
        callPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(callPanel)
        log = label("")
        label(Session.audioCapabilities(), 12f)
        button("Android TTS settings") { try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
        button("Restore your usual Phone app") { try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
        val prefs = getPreferences(MODE_PRIVATE)
        numbers.addAll(prefs.getString("numbers", "")!!.split("\n").filter { NumberRules.normalize(it) != null })
        message.setText(prefs.getString("message", getString(R.string.default_message)))
        delay.setText(prefs.getString("delay", "2")); maximum.setText(prefs.getString("maximum", "10"))
        acceptDialIntent(intent)
        Session.updates.observe(this) { render() }
        renderNumbers(); render()
        if (hasCallPermissions()) monitor.start()
    }
    private fun label(text: String, size: Float = 14f) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(24, 45, 37)); setPadding(0, 10, 0, 10); root.addView(this)
    }
    private fun input(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; inputType = type; root.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() }; root.addView(this) }
    private fun renderNumbers() {
        numberList.removeAllViews()
        numbers.forEachIndexed { index, value ->
            numberList.addView(Button(this).apply {
                text = "${index + 1}. $value   × Remove"
                setOnClickListener { if (!Session.running) { numbers.remove(value); renderNumbers(); saveDraft() } }
            })
        }
    }
    private fun render() {
        if (!::status.isInitialized) return
        val modeText = if (Session.useServer) "SERVER MODE - calls via backend" else if (Session.isDialer()) "Automatic mode: Android default Phone role enabled" else "Manual fallback: answer confirmation and manual hang-up required"
        roleText.text = modeText
        status.text = "${Session.status}\n${Session.audioStatus}\n${Session.speech.description}"
        log.text = Session.log.joinToString("\n")
        manualButton.visibility = if (Session.running && Session.manual && !Session.useServer) View.VISIBLE else View.GONE
        continueButton.visibility = if (Session.awaitingNext && !Session.useServer) View.VISIBLE else View.GONE
        number.isEnabled = !Session.running; message.isEnabled = !Session.running
        maximum.isEnabled = !Session.running; delay.isEnabled = !Session.running
        callPanel.removeAllViews()
        Session.calls.toList().forEach { call ->
            callPanel.addView(TextView(this).apply { text = "SIM call: ${call.details.handle?.schemeSpecificPart ?: "Private number"} • state ${call.state}" })
            fun control(text: String, action: () -> Unit) { callPanel.addView(Button(this).apply {
                this.text = text; setOnClickListener { try { action() } catch (_: Exception) { Session.show("Call control unavailable") } }
            }) }
            if (call.state == Call.STATE_RINGING) control("Answer incoming call") { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
            control("End / reject call") { if (Session.running) Session.stop("Stopped by user"); call.disconnect() }
            control("Speaker ON") { Session.service?.requestSpeaker() }
            control("Mute microphone") { Session.service?.setMuted(true) }
            control("Unmute microphone") { Session.service?.setMuted(false) }
            if (call.details.can(Call.Details.CAPABILITY_HOLD)) {
                control(if (call.state == Call.STATE_HOLDING) "Unhold" else "Hold") {
                    if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
                }
            }
            control("Keypad / DTMF") {
                val digits = EditText(this).apply { inputType = InputType.TYPE_CLASS_PHONE }
                AlertDialog.Builder(this).setTitle("Send digits (0–9, *, #)").setView(digits)
                    .setPositiveButton("Send") { _, _ ->
                        val tones = digits.text.toString().filter { it in "0123456789*#" }
                        tones.forEachIndexed { i, c -> android.os.Handler(mainLooper).postDelayed({
                            if (call.state == Call.STATE_ACTIVE) { call.playDtmfTone(c); android.os.Handler(mainLooper).postDelayed({ call.stopDtmfTone() }, 150) }
                        }, i * 300L) }
                    }.setNegativeButton("Cancel", null).show()
            }
        }
    }
    private fun hasCallPermissions() = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionResult.launch(needed.toTypedArray())
    }
    private fun requestRole() {
        if (Session.running || Session.calls.isNotEmpty()) { Session.show("Stop session and finish calls before changing Phone role"); return }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) { Session.show("Phone role unavailable: use manual fallback"); return }
                roleResult.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } else roleResult.launch(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName))
        } catch (_: Exception) { Session.show("Phone role unavailable: use manual fallback") }
    }
    private fun confirmStart() {
        if (!hasCallPermissions()) { requestPermissions(); return }
        if (Session.running) return
        val max = maximum.text.toString().toIntOrNull()
        val wait = delay.text.toString().toIntOrNull()
        if (max == null || max !in 1..50 || wait == null || wait !in 0..30) { Session.show("Maximum must be 1–50; delay must be 0–30 seconds"); return }
        if (numbers.isEmpty() || numbers.size > max) { Session.show("Add numbers within your configured session maximum"); return }
        if (message.text.isBlank() || message.text.length > 4000) { Session.show("Message must contain 1–4000 characters"); return }
        if (Session.isDialer() && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Session.show("Enable notifications before using automatic Phone mode (incoming call controls)"); requestPermissions(); return
        }
        saveDraft()
        AlertDialog.Builder(this).setTitle(if (numbers.size >= 5) "Confirm large calling queue: ${numbers.size} calls" else "Start ${numbers.size} SIM call(s)?")
            .setMessage("Only call recipients who consented. SIM charges apply. Keep this app visible for automatic next calls. Speaker/microphone transmission may be suppressed by your phone. ${if (!Session.isDialer()) "Manual answer confirmation and hang-up will be required." else "This app now handles incoming Phone calls too."}")
            .setPositiveButton("Start Calling") { _, _ -> Session.start(numbers, message.text.toString(), wait, max) }
            .setNegativeButton("Cancel", null).show()
    }
    private fun showProviderConfigDialog(provider: ServerCaller.Provider) {
        AlertDialog.Builder(this).setTitle("Configure $provider")
            .setMessage("This provider requires manual configuration in code. See ServerCaller.kt")
            .setPositiveButton("OK", null).show()
    }
    private fun saveDraft() {
        if (!::message.isInitialized) return
        getPreferences(MODE_PRIVATE).edit().putString("numbers", numbers.joinToString("\n"))
            .putString("message", message.text.toString()).putString("delay", delay.text.toString())
            .putString("maximum", maximum.text.toString()).apply()
    }
    private fun acceptDialIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL && intent.data?.scheme == "tel") number.setText(intent.data?.schemeSpecificPart)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptDialIntent(intent) }
    override fun onResume() { super.onResume(); resumed = true; Session.visible = true; render() }
    override fun onPause() { resumed = false; Session.visible = false; saveDraft(); super.onPause() }
    override fun onDestroy() {
        monitor.stop()
        if (isFinishing && Session.running) Session.stop("App closed: queue stopped")
        super.onDestroy()
    }
}
