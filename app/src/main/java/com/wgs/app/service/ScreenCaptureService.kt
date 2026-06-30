package com.wgs.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.wgs.app.MainActivity
import com.wgs.app.R
import com.wgs.app.data.repository.ScanRepository
import com.wgs.app.ocr.OcrProcessor
import com.wgs.app.ocr.OcrResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject lateinit var ocrProcessor: OcrProcessor
    @Inject lateinit var scanStateManager: ScanStateManager
    @Inject lateinit var scanRepository: ScanRepository

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var currentWarId = "war_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"

    companion object {
        const val CHANNEL_ID = "wgs_capture_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SCAN = "com.wgs.app.ACTION_SCAN"
        const val ACTION_STOP = "com.wgs.app.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("War Gold Scanner active"))
        initScreenMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCAN -> {
                performScan()
                return START_NOT_STICKY
            }
            else -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
                val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != -1 && resultData != null) {
                    setupMediaProjection(resultCode, resultData)
                }
            }
        }
        return START_STICKY
    }

    private fun initScreenMetrics() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "WGS_VirtualDisplay",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        scanStateManager.startSession()
        startOverlayService()
    }

    private fun startOverlayService() {
        val intent = Intent(this, com.wgs.app.overlay.FloatingOverlayService::class.java)
        startService(intent)
    }

    fun performScan() {
        serviceScope.launch {
            val screenshot = captureScreen() ?: run {
                scanStateManager.setError("Screen capture failed. Please try again.")
                return@launch
            }

            val phase = scanStateManager.phase.value
            when {
                phase is ScanPhase.WaitingForBase || phase is ScanPhase.Failure -> {
                    scanStateManager.setCapturingBase()
                    handleBaseCapture(screenshot)
                }
                phase is ScanPhase.WaitingForGold -> {
                    scanStateManager.setCapturingGold(phase.baseNumber, phase.playerName)
                    handleGoldCapture(screenshot, phase.baseNumber, phase.playerName)
                }
                else -> {}
            }
            screenshot.recycle()
        }
    }

    private fun captureScreen(): Bitmap? {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun handleBaseCapture(screenshot: Bitmap) {
        val result = ocrProcessor.extractBaseInfo(screenshot)
        when (result) {
            is OcrResult.BaseInfo -> scanStateManager.baseScanned(result.baseNumber, result.playerName)
            is OcrResult.Error -> scanStateManager.setError(result.message)
            else -> scanStateManager.setError("Unexpected OCR result.")
        }
    }

    private suspend fun handleGoldCapture(screenshot: Bitmap, baseNumber: Int, playerName: String) {
        val result = ocrProcessor.extractGoldValue(screenshot)
        when (result) {
            is OcrResult.GoldValue -> {
                val existing = scanRepository.findDuplicate(currentWarId, baseNumber)
                if (existing != null) {
                    scanStateManager.duplicateDetected(baseNumber, playerName, result.gold)
                } else {
                    saveRecord(baseNumber, playerName, result.gold, overwrite = false)
                }
            }
            is OcrResult.Error -> scanStateManager.setError(result.message)
            else -> scanStateManager.setError("Unexpected OCR result.")
        }
    }

    fun confirmSave(baseNumber: Int, playerName: String, gold: Long) {
        serviceScope.launch {
            saveRecord(baseNumber, playerName, gold, overwrite = true)
        }
    }

    private suspend fun saveRecord(baseNumber: Int, playerName: String, gold: Long, overwrite: Boolean) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val record = com.wgs.app.data.db.ScanRecord(
            warId = currentWarId,
            baseNumber = baseNumber,
            playerName = playerName,
            goldValue = gold,
            date = dateStr
        )
        scanRepository.saveRecord(record)
        scanStateManager.recordSaved(baseNumber, playerName, gold)
        delay(1500)
        scanStateManager.resetToWaiting()
    }

    fun setWarId(warId: String) {
        currentWarId = warId
    }

    fun getWarId(): String = currentWarId

    private fun teardown() {
        scanStateManager.stopSession()
        ocrProcessor.release()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        stopService(Intent(this, com.wgs.app.overlay.FloatingOverlayService::class.java))
    }

    override fun onDestroy() {
        teardown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("War Gold Scanner")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_scanner)
            .setContentIntent(mainIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "War Gold Scanner capture service"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
