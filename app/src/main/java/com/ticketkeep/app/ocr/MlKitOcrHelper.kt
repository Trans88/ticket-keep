package com.ticketkeep.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 端侧 ML Kit 中文 OCR。
 *
 * 重要：Photo Picker / 部分 content:// URI 的输入流只能读一次。
 * 业务侧应先把图落到应用私有文件，再调用 [recognizeFile]；
 * 不要先 persist 再对同一 content URI 做二次 openInputStream。
 */
@Singleton
class MlKitOcrHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: TicketOcrParser,
) {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 对 content/file URI 识别。内部会把流整段读入内存再解码，避免「读 bounds 再读像素」二次 open 失败。
     * 若调用方已经 persist，请优先用 [recognizeFile]。
     */
    suspend fun recognize(uri: Uri): OcrParseResult = withContext(Dispatchers.IO) {
        val bytes = readAllBytes(uri) ?: return@withContext OcrParseResult()
        recognizeBytes(bytes)
    }

    /** 对已落盘的私有文件识别（推荐路径）。 */
    suspend fun recognizeFile(path: String): OcrParseResult = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return@withContext OcrParseResult()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext OcrParseResult()
        recognizeBytes(bytes)
    }

    private suspend fun recognizeBytes(bytes: ByteArray): OcrParseResult {
        val bitmap = decodeScaledBitmap(bytes) ?: return OcrParseResult()
        return try {
            val visionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            parser.parse(visionText.text.orEmpty())
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun readAllBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 单次字节缓冲上做 bounds + 采样解码，并按 EXIF 旋转。
     * 最长边限制在 [MAX_SIDE]，降低大图 OCR 内存峰值。
     */
    private fun decodeScaledBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sampleSize = 1
        val longest = max(width, height)
        while (longest / sampleSize > MAX_SIDE) {
            sampleSize *= 2
        }

        val decode = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode) ?: return null
        return applyExifRotation(bytes, raw)
    }

    private fun applyExifRotation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            return bitmap
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }

    companion object {
        private const val MAX_SIDE = 2048
    }
}
