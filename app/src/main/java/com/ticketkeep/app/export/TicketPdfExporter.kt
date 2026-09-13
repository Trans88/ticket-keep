package com.ticketkeep.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.MoneyFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pro：单条票证 → PDF（系统 [PdfDocument]，无第三方 PDF 库）。
 */
@Singleton
class TicketPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun exportToCache(ticket: Ticket): File {
        val dir = File(context.cacheDir, "exports").also { if (!it.exists()) it.mkdirs() }
        val stamp = LocalDateTime.now().format(STAMP)
        val file = File(dir, "票证记_导出_${stamp}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val typeface = resolveCjkTypeface()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = 22f
            color = 0xFF0F766E.toInt()
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f
            color = 0xFF64748B.toInt()
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 14f
            color = 0xFF1E293B.toInt()
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 11f
            color = 0xFF334155.toInt()
        }

        var y = MARGIN.toFloat()
        canvas.drawText("票证记 · 票证导出", MARGIN.toFloat(), y + 22f, titlePaint)
        y += 40f

        y = drawField(canvas, "商家", ticket.merchantName.ifBlank { "—" }, y, labelPaint, valuePaint)
        y = drawField(canvas, "金额", MoneyFormats.formatYuan(ticket.amountCents), y, labelPaint, valuePaint)
        y = drawField(canvas, "购买日", DateFormats.formatEpochDay(ticket.purchaseDateEpochDay), y, labelPaint, valuePaint)
        y = drawField(
            canvas,
            "保修到期",
            DateFormats.formatEpochDay(ticket.warrantyEndEpochDay),
            y,
            labelPaint,
            valuePaint,
        )
        y = drawField(
            canvas,
            "保修月数",
            ticket.warrantyMonths?.let { "$it 个月" } ?: "—",
            y,
            labelPaint,
            valuePaint,
        )
        y = drawField(canvas, "备注", ticket.note.ifBlank { "—" }, y, labelPaint, valuePaint)

        y += 8f
        canvas.drawText("收据图片", MARGIN.toFloat(), y + 12f, labelPaint)
        y += 20f
        y = drawThumbnailOrNote(canvas, ticket.imagePath, y, bodyPaint)

        y += 12f
        canvas.drawText("识别原文", MARGIN.toFloat(), y + 12f, labelPaint)
        y += 20f
        val ocr = ticket.ocrRawText.ifBlank { "（无）" }
        y = drawWrapped(canvas, ocr, y, bodyPaint, maxBottom = PAGE_H - MARGIN)

        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    private fun drawField(
        canvas: Canvas,
        label: String,
        value: String,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint,
    ): Float {
        var yy = y
        canvas.drawText(label, MARGIN.toFloat(), yy + 12f, labelPaint)
        yy += 18f
        yy = drawWrapped(canvas, value, yy, valuePaint, maxBottom = PAGE_H - MARGIN)
        return yy + 10f
    }

    private fun drawThumbnailOrNote(
        canvas: Canvas,
        imagePath: String?,
        y: Float,
        paint: Paint,
    ): Float {
        if (imagePath.isNullOrBlank()) {
            return drawWrapped(canvas, "（无图片）", y, paint, maxBottom = PAGE_H - MARGIN)
        }
        val file = File(imagePath)
        if (!file.exists()) {
            return drawWrapped(
                canvas,
                "图片缺失：$imagePath",
                y,
                paint,
                maxBottom = PAGE_H - MARGIN,
            )
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, opts)
        val maxW = (PAGE_W - 2 * MARGIN).toFloat()
        val maxH = 220f
        var sample = 1
        while (opts.outWidth / sample > maxW * 2 || opts.outHeight / sample > maxH * 2) {
            sample *= 2
        }
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(imagePath, decode)
            ?: return drawWrapped(canvas, "无法解码图片：$imagePath", y, paint, maxBottom = PAGE_H - MARGIN)
        try {
            val scale = minOf(maxW / bmp.width, maxH / bmp.height, 1f)
            val w = (bmp.width * scale).toInt().coerceAtLeast(1)
            val h = (bmp.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            if (scaled !== bmp) bmp.recycle()
            canvas.drawBitmap(scaled, MARGIN.toFloat(), y, null)
            scaled.recycle()
            return y + h + 8f
        } catch (_: Exception) {
            bmp.recycle()
            return drawWrapped(canvas, "图片路径：$imagePath", y, paint, maxBottom = PAGE_H - MARGIN)
        }
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        startY: Float,
        paint: Paint,
        maxBottom: Int,
    ): Float {
        val maxWidth = (PAGE_W - 2 * MARGIN).toFloat()
        var y = startY
        val normalized = text.replace("\r\n", "\n")
        for (paragraph in normalized.split('\n')) {
            var remaining = paragraph
            if (remaining.isEmpty()) {
                y += paint.textSize + 4f
                continue
            }
            while (remaining.isNotEmpty()) {
                if (y + paint.textSize >= maxBottom) {
                    canvas.drawText("…（已截断）", MARGIN.toFloat(), y + paint.textSize, paint)
                    return y + paint.textSize + 4f
                }
                val count = paint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                val piece = remaining.substring(0, count)
                canvas.drawText(piece, MARGIN.toFloat(), y + paint.textSize, paint)
                remaining = remaining.substring(count)
                y += paint.textSize + 4f
            }
        }
        return y
    }

    private fun resolveCjkTypeface(): Typeface {
        val candidates = listOf(
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/NotoSansSC-Regular.otf",
            "/system/fonts/NotoSansCJKsc-Regular.otf",
            "/system/fonts/DroidSansFallback.ttf",
            "/system/fonts/NotoSansCJK-Regular.ttf",
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) {
                runCatching { Typeface.createFromFile(f) }.getOrNull()?.let { return it }
            }
        }
        return Typeface.DEFAULT
    }

    companion object {
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 40
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    }
}
