@file:OptIn(androidx.window.core.ExperimentalWindowApi::class)

package com.cerniera.giano

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import com.cerniera.kit.Card
import com.cerniera.kit.Crash
import com.cerniera.kit.Kit
import com.cerniera.kit.Row
import com.cerniera.kit.Screen
import com.cerniera.kit.doctrineCard
import com.cerniera.kit.endValue
import com.cerniera.kit.px
import com.cerniera.kit.text
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Giano, the probe (15 Nov 2026). Whether Giano can exist depends on one answer: will the Fold 8 Ultra
 * let an app show something on the cover screen while the main screen is in use (dual-screen,
 * "present on area")? Or only move the whole app there (rear display, "transfer")? This screen
 * asks Android, live as the phone folds and unfolds, lets you try both, and keeps a report to share.
 */
class MainActivity : Activity() {
    private val scope = MainScope()
    private var watch: Job? = null
    private val controller by lazy { WindowAreaController.getOrCreate() }
    private var areas: List<WindowAreaInfo> = emptyList()
    private var presenter: WindowAreaSessionPresenter? = null
    private var transfer: WindowAreaSession? = null
    private val log = mutableListOf<String>()
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var audioMode = AudioManager.MODE_NORMAL

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (Crash.show(this, "Giano")) return
        // Visconti, always: the outward face is a light in the dark
        Kit.dark = true
        window.statusBarColor = Kit.GROUND; window.navigationBarColor = Kit.GROUND
        note("Giano probe ${packageManager.getPackageInfo(packageName, 0).versionName} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), One UI ${oneUi()}")
        note("Window extensions version ${runCatching { androidx.window.WindowSdkExtensions.getInstance().extensionVersion }.getOrElse { "unknown (${it.javaClass.simpleName})" }}")
        displays()
        render()
        watch = scope.launch {
            runCatching {
                controller.windowAreaInfos.collect { list ->
                    areas = list
                    note("Window areas: ${if (list.isEmpty()) "none" else list.joinToString { describe(it) }}")
                    render()
                }
            }.onFailure { note("Window areas unreadable: $it"); render() }
        }
        val am = getSystemService(AudioManager::class.java)
        audioMode = am.mode
        modeListener = AudioManager.OnModeChangedListener { m ->
            audioMode = m; note("Audio mode → ${modeName(m)}"); render()
        }.also { am.addOnModeChangedListener(mainExecutor, it) }
    }

    override fun onDestroy() {
        modeListener?.let { getSystemService(AudioManager::class.java).removeOnModeChangedListener(it) }
        watch?.cancel(); scope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        note("Configuration: ${c.screenWidthDp}×${c.screenHeightDp} dp, smallest ${c.smallestScreenWidthDp} dp, ${if (c.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}")
        render()
    }

    // ---- What Android says ----

    private val rear get() = areas.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }

    private fun status(op: WindowAreaCapability.Operation): WindowAreaCapability.Status? = rear?.getCapability(op)?.status

    private fun statusWord(s: WindowAreaCapability.Status?): Pair<String, Int> = when (s) {
        null -> "No rear area" to Kit.MUTED
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> "Available" to Kit.GOOD
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> "Active" to Kit.CHAMPAGNE
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> "Not now" to Kit.WARN
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED -> "Unsupported" to Kit.ROSSO
        else -> "Unknown" to Kit.MUTED
    }

    private fun describe(i: WindowAreaInfo): String {
        val b = i.metrics.bounds
        val t = statusWord(i.getCapability(WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA)?.status).first
        val p = statusWord(i.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status).first
        return "${i.type} ${b.width()}×${b.height()} px (transfer: $t, present: $p)"
    }

    private fun displays() {
        val dm = getSystemService(DisplayManager::class.java)
        dm.displays.forEach { d -> note("Display ${d.displayId} \"${d.name}\" ${d.mode.physicalWidth}×${d.mode.physicalHeight}, state ${d.state}") }
        val rearCat = runCatching { dm.getDisplays("android.hardware.display.category.REAR") }.getOrNull()
        note("Rear-category displays: ${rearCat?.size ?: "unreadable"}")
        val pres = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        note("Presentation displays: ${pres.size}")
    }

    private fun oneUi(): String = runCatching {
        val v = Build::class.java.getField("SEM_PLATFORM_INT").getInt(null) - 90000
        "${v / 10000}.${(v % 10000) / 100}"
    }.getOrDefault("unknown")

    private fun modeName(m: Int) = when (m) {
        AudioManager.MODE_NORMAL -> "normal"
        AudioManager.MODE_RINGTONE -> "ringing"
        AudioManager.MODE_IN_CALL -> "in a phone call"
        AudioManager.MODE_IN_COMMUNICATION -> "in a call (Teams, WhatsApp, Meet)"
        AudioManager.MODE_CALL_SCREENING -> "screening a call"
        else -> "mode $m"
    }

    private fun note(s: String) {
        val line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + s
        log += line; Log.i("Giano", s)
    }

    // ---- Trying it ----

    private fun present() {
        val token = rear?.token ?: return note("Present: no rear area to present on")
        note("Present: asking…")
        runCatching {
            controller.presentContentOnWindowArea(token, this, mainExecutor, object : WindowAreaPresentationSessionCallback {
                override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                    presenter = session
                    session.setContentView(Face(session.context, "Giano"))
                    note("Present: STARTED. The back should show Giano and the light"); render()
                }
                override fun onSessionEnded(t: Throwable?) {
                    presenter = null; note("Present: ended${t?.let { " with $it" } ?: ""}"); render()
                }
                override fun onContainerVisibilityChanged(isVisible: Boolean) {
                    note("Present: the back is ${if (isVisible) "showing" else "hidden"}"); render()
                }
            })
        }.onFailure { note("Present failed: $it"); render() }
    }

    private fun moveToBack() {
        val token = rear?.token ?: return note("Transfer: no rear area")
        note("Transfer: asking (Android shows its own dialog)…")
        runCatching {
            controller.transferActivityToWindowArea(token, this, mainExecutor, object : WindowAreaSessionCallback {
                override fun onSessionStarted(session: WindowAreaSession) { transfer = session; note("Transfer: STARTED. This screen is now on the back"); render() }
                override fun onSessionEnded(t: Throwable?) { transfer = null; note("Transfer: ended${t?.let { " with $it" } ?: ""}"); render() }
            })
        }.onFailure { note("Transfer failed: $it"); render() }
    }

    // ---- The screen ----

    private fun render() {
        val s = Screen(this, "Giano", "The outward face · probe")
        val (pw, pc) = statusWord(status(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA))
        val (tw, tc) = statusWord(status(WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA))
        s.status.set(if (pw == "Available" || pw == "Active") "Giano can exist" else "Dual-screen: $pw", pc)
        s.status.visibility = android.view.View.VISIBLE

        s.add(text("Open this unfolded. Android's answer changes as you fold and unfold, and every change is logged below.", 15f, Kit.MUTED), px(6))

        s.section("What this Fold offers")
        s.add(Card(this).apply {
            row(Row(this@MainActivity, "Both screens at once", "Dual-screen: the back shows Giano while you use the inside. This is what Giano needs", endValue(pw, pc)))
            row(Row(this@MainActivity, "Move to the back", "Rear display: the whole app moves to the cover and the inside turns off", endValue(tw, tc)))
            row(Row(this@MainActivity, "Call detection", "What the lamp would follow. Start a Teams call to see it change", endValue(modeName(audioMode), if (audioMode == AudioManager.MODE_IN_COMMUNICATION || audioMode == AudioManager.MODE_IN_CALL) Kit.ROSSO else Kit.MUTED)))
        })

        s.section("Try it")
        s.add(Card(this).apply {
            if (presenter == null) row(Row(this@MainActivity, "Show Giano on the back", "Turn the phone round to look: a card and a breathing rear light", endValue("Try", Kit.CHAMPAGNE)) { present(); render() })
            else row(Row(this@MainActivity, "Bring it back", "Ends the session on the back", endValue("Close", Kit.CHAMPAGNE)) { presenter?.close() })
            if (transfer == null) row(Row(this@MainActivity, "Move this screen to the back", "Android asks first. Fold or unfold to bring it back", endValue("Try", Kit.CHAMPAGNE)) { moveToBack(); render() })
            else row(Row(this@MainActivity, "Bring this screen back", null, endValue("Close", Kit.CHAMPAGNE)) { transfer?.close() })
        })

        s.section("Report")
        s.add(Card(this).apply {
            row(Row(this@MainActivity, "Share the report", "Send it to Claude so the answer is on record", endValue("›")) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Giano probe").putExtra(Intent.EXTRA_TEXT, log.joinToString("\n")), "Share the report"))
            })
        })
        val lines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(4), px(12), px(4), 0) }
        log.takeLast(40).asReversed().forEach { lines.addView(text(it, 12.5f, Kit.MUTED).apply { (this as TextView).setTextIsSelectable(true) }) }
        s.add(lines)

        s.add(doctrineCard(listOf(
            "Membrane" to "Once Giano is real, the two faces are one sheet: words typed inside press through to the outside as you write.",
            "Concentrato" to "While the lamp is lit, the inside empties to a thin strip, so it never competes with the call.",
            "Giulietta" to "Giano has no screen of its own most of the time: a Quick Settings tile and an edge that widens under your thumb.")), px(18))
        setContentView(s.root)
    }
}
