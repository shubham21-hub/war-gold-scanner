package com.wgs.app.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import com.wgs.app.R
import com.wgs.app.service.ScreenCaptureService
import com.wgs.app.service.ScanPhase
import com.wgs.app.service.ScanStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class FloatingOverlayService : Service() {

    @Inject lateinit var scanStateManager: ScanStateManager

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        observeState()
    }

    private fun createOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_scan_button, null)
        val scanButton = overlayView?.findViewById<ImageButton>(R.id.btnScan)

        scanButton?.setOnClickListener {
            triggerScan()
        }

        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun observeState() {
        serviceScope.launch {
            scanStateManager.phase.collectLatest { phase ->
                when (phase) {
                    is ScanPhase.Success -> {
                        showToast("Saved: Base #${phase.baseNumber} — ${phase.playerName} — ${formatGold(phase.gold)}")
                    }
                    is ScanPhase.Failure -> {
                        showToast(phase.message)
                    }
                    is ScanPhase.Duplicate -> {
                        showToast("Base #${phase.baseNumber} already scanned. Use app to overwrite.")
                    }
                    is ScanPhase.WaitingForGold -> {
                        showToast("Base #${phase.baseNumber} captured. Open Gold Storage and SCAN.")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun triggerScan() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_SCAN
        }
        startService(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun formatGold(gold: Long): String {
        return when {
            gold >= 1_000_000 -> "${gold / 1_000_000}M"
            gold >= 1_000 -> "${gold / 1_000}K"
            else -> gold.toString()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
