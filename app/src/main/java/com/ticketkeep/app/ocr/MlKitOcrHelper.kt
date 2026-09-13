package com.ticketkeep.app.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class MlKitOcrHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: TicketOcrParser,
) {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(uri: Uri): OcrParseResult = withContext(Dispatchers.IO) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (_: Exception) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext OcrParseResult()
            stream.use {
                val bitmap = BitmapFactory.decodeStream(it)
                    ?: return@withContext OcrParseResult()
                InputImage.fromBitmap(bitmap, 0)
            }
        }
        val result = recognizer.process(image).await()
        parser.parse(result.text.orEmpty())
    }

    suspend fun recognizeFile(path: String): OcrParseResult = withContext(Dispatchers.IO) {
        val file = java.io.File(path)
        if (!file.exists()) return@withContext OcrParseResult()
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val result = recognizer.process(image).await()
        parser.parse(result.text.orEmpty())
    }
}
