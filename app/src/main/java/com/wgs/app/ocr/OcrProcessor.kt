package com.wgs.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrRegions(
    val screenWidth: Int,
    val screenHeight: Int
) {
    val baseNumberRect: Rect get() = Rect(
        (screenWidth * 0.55f).toInt(),
        (screenHeight * 0.03f).toInt(),
        (screenWidth * 0.95f).toInt(),
        (screenHeight * 0.12f).toInt()
    )
    val playerNameRect: Rect get() = Rect(
        (screenWidth * 0.05f).toInt(),
        (screenHeight * 0.12f).toInt(),
        (screenWidth * 0.85f).toInt(),
        (screenHeight * 0.21f).toInt()
    )
    val goldStorageRect: Rect get() = Rect(
        (screenWidth * 0.30f).toInt(),
        (screenHeight * 0.40f).toInt(),
        (screenWidth * 0.80f).toInt(),
        (screenHeight * 0.55f).toInt()
    )
}

sealed class OcrResult {
    data class BaseInfo(val baseNumber: Int, val playerName: String) : OcrResult()
    data class GoldValue(val gold: Long) : OcrResult()
    data class Error(val message: String) : OcrResult()
}

@Singleton
class OcrProcessor @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractBaseInfo(screenshot: Bitmap): OcrResult {
        val regions = OcrRegions(screenshot.width, screenshot.height)

        val baseNumber = extractBaseNumber(screenshot, regions.baseNumberRect)
            ?: return OcrResult.Error("Unable to detect base number. Please scan again.")

        val playerName = extractPlayerName(screenshot, regions.playerNameRect)
            ?: return OcrResult.Error("Unable to detect player name. Please scan again.")

        return OcrResult.BaseInfo(baseNumber, playerName)
    }

    suspend fun extractGoldValue(screenshot: Bitmap): OcrResult {
        val regions = OcrRegions(screenshot.width, screenshot.height)
        val gold = extractGold(screenshot, regions.goldStorageRect)
            ?: return OcrResult.Error("Gold storage not detected. Please open the Gold Storage and scan again.")
        return OcrResult.GoldValue(gold)
    }

    private suspend fun extractBaseNumber(bitmap: Bitmap, rect: Rect): Int? {
        val cropped = safeCrop(bitmap, rect) ?: return null
        val text = runOcr(cropped)
        val normalized = normalizeText(text)
        val match = Regex("#?(\\d+)").find(normalized)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private suspend fun extractPlayerName(bitmap: Bitmap, rect: Rect): String? {
        val cropped = safeCrop(bitmap, rect) ?: return null
        val text = runOcr(cropped)
        val name = text.trim()
        return if (name.isBlank()) null else name
    }

    private suspend fun extractGold(bitmap: Bitmap, rect: Rect): Long? {
        val cropped = safeCrop(bitmap, rect) ?: return null
        val text = runOcr(cropped)
        val normalized = normalizeText(text)
        val part = normalized.split("/").firstOrNull()?.trim() ?: return null
        val digits = part.filter { it.isDigit() }
        return digits.toLongOrNull()
    }

    private fun safeCrop(bitmap: Bitmap, rect: Rect): Bitmap? {
        return try {
            val left = rect.left.coerceIn(0, bitmap.width - 1)
            val top = rect.top.coerceIn(0, bitmap.height - 1)
            val right = rect.right.coerceIn(left + 1, bitmap.width)
            val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun runOcr(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    private fun normalizeText(raw: String): String =
        raw
            .replace("O", "0")
            .replace("o", "0")
            .replace("I", "1")
            .replace("l", "1")
            .replace(",", "")
            .replace(".", "")
            .trim()

    fun release() {
        recognizer.close()
    }
}
