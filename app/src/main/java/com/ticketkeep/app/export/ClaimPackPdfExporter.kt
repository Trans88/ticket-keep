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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pro：生成「理赔/送修材料包」PDF（封面、摘要、凭证图、OCR、免责页脚）。
 * 仅整理本地资料，不判定能否理赔；鉴权由调用方负责。
 */
@Singleton
class ClaimPackPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun exportToCache(ticket: Ticket): File {
        val dir = File(context.cacheDir, "exports").also { if (!it.exists()) it.mkdirs() }
        val file = File(dir, buildFileName(ticket, LocalDate.now()))

        val doc = PdfDocument()
        val typeface = resolveCjkTypeface()
        val titlePaint = paint(typeface, 20f, COLOR_PRIMARY, bold = true)
        val sectionPaint = paint(typeface, 13f, COLOR_PRIMARY, bold = true)
        val labelPaint = paint(typeface, 11f, COLOR_MUTED)
        val valuePaint = paint(typeface, 13f, COLOR_TEXT)
        val bodyPaint = paint(typeface, 11f, COLOR_TEXT)
        val footerPaint = paint(typeface, 9f, COLOR_MUTED)

        val cursor = PageCursor(doc)

        // 1. 封面
        cursor.canvas.drawText("理赔 / 送修材料包", MARGIN.toFloat(), cursor.y + 24f, titlePaint)
        cursor.y += 36f
        val genDate = LocalDate.now().format(DateFormats.display)
        cursor.canvas.drawText("生成日期：$genDate", MARGIN.toFloat(), cursor.y + 14f, valuePaint)
        cursor.y += 28f
        cursor.canvas.drawText("票证记 · 本地整理", MARGIN.toFloat(), cursor.y + 12f, labelPaint)
        cursor.y += 28f

        // 2. 摘要
        cursor.ensureSpace(40f, footerPaint)
        cursor.canvas.drawText("一、票证摘要", MARGIN.toFloat(), cursor.y + 14f, sectionPaint)
        cursor.y += 24f
        for ((label, value) in buildSummaryRows(ticket)) {
            cursor.ensureSpace(40f, footerPaint)
            cursor.canvas.drawText(label, MARGIN.toFloat(), cursor.y + 12f, labelPaint)
            cursor.y += 16f
            cursor.y = drawWrapped(
                cursor.canvas,
                value,
                cursor.y,
                valuePaint,
                maxBottom = PAGE_H - FOOTER_RESERVED,
            )
            cursor.y += 8f
        }

        // 3. 凭证图
        cursor.ensureSpace(40f, footerPaint)
        cursor.canvas.drawText("二、凭证图片", MARGIN.toFloat(), cursor.y + 14f, sectionPaint)
        cursor.y += 22f
        drawImagePaged(cursor, ticket.imagePath, bodyPaint, footerPaint)

        // 4. OCR
        cursor.ensureSpace(40f, footerPaint)
        cursor.canvas.drawText("三、识别原文", MARGIN.toFloat(), cursor.y + 14f, sectionPaint)
        cursor.y += 22f
        val ocr = ticket.ocrRawText.ifBlank { "（无识别原文）" }
        drawTextPaged(cursor, ocr, bodyPaint, footerPaint)

        cursor.finish(footerPaint)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    private fun paint(tf: Typeface, size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) Typeface.create(tf, Typeface.BOLD) else tf
            textSize = size
            this.color = color
        }

    private inner class PageCursor(private val doc: PdfDocument) {
        var pageIndex: Int = 1
            private set
        private var page: PdfDocument.Page = start(pageIndex)
        var canvas: Canvas = page.canvas
            private set
        var y: Float = MARGIN.toFloat()

        fun ensureSpace(needed: Float, footerPaint: Paint) {
            if (y + needed <= PAGE_H - FOOTER_RESERVED) return
            finish(footerPaint)
            pageIndex++
            page = start(pageIndex)
            canvas = page.canvas
            y = MARGIN.toFloat()
        }

        fun finish(footerPaint: Paint) {
            val maxWidth = (PAGE_W - 2 * MARGIN).toFloat()
            var fy = (PAGE_H - 40).toFloat()
            var remaining = DISCLAIMER
            while (remaining.isNotEmpty() && fy < PAGE_H - 14) {
                val count = footerPaint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                canvas.drawText(remaining.substring(0, count), MARGIN.toFloat(), fy, footerPaint)
                remaining = remaining.substring(count)
                fy += footerPaint.textSize + 2f
            }
            canvas.drawText(
                "第 $pageIndex 页",
                (PAGE_W - MARGIN - 48).toFloat(),
                (PAGE_H - 12).toFloat(),
                footerPaint,
            )
            doc.finishPage(page)
        }

        private fun start(index: Int): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index).create()
            return doc.startPage(info)
        }
    }

    private fun drawImagePaged(
        cursor: PageCursor,
        imagePath: String?,
        bodyPaint: Paint,
        footerPaint: Paint,
    ) {
        if (imagePath.isNullOrBlank()) {
            cursor.y = drawWrapped(
                cursor.canvas,
                "（无凭证图片）",
                cursor.y,
                bodyPaint,
                maxBottom = PAGE_H - FOOTER_RESERVED,
            )
            return
        }
        val file = File(imagePath)
        if (!file.exists()) {
            cursor.y = drawWrapped(
                cursor.canvas,
                "图片缺失，请在编辑页重新添加凭证图。",
                cursor.y,
                bodyPaint,
                maxBottom = PAGE_H - FOOTER_RESERVED,
            )
            return
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        val maxW = (PAGE_W - 2 * MARGIN).toFloat()
        var sample = 1
        while (bounds.outWidth / sample > maxW * 2) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            imagePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
        if (bmp == null) {
            cursor.y = drawWrapped(
                cursor.canvas,
                "无法解码图片",
                cursor.y,
                bodyPaint,
                maxBottom = PAGE_H - FOOTER_RESERVED,
            )
            return
        }
        try {
            val scale = minOf(maxW / bmp.width, 1f)
            val fullW = (bmp.width * scale).toInt().coerceAtLeast(1)
            val fullH = (bmp.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, fullW, fullH, true)
            if (scaled !== bmp) bmp.recycle()

            var srcTop = 0
            while (srcTop < scaled.height) {
                val avail = ((PAGE_H - FOOTER_RESERVED) - cursor.y).toInt()
                if (avail < 48) {
                    cursor.ensureSpace(80f, footerPaint)
                    continue
                }
                val sliceH = minOf(avail, scaled.height - srcTop).coerceAtLeast(1)
                val slice = Bitmap.createBitmap(scaled, 0, srcTop, scaled.width, sliceH)
                cursor.canvas.drawBitmap(slice, MARGIN.toFloat(), cursor.y, null)
                slice.recycle()
                cursor.y += sliceH + 8f
                srcTop += sliceH
            }
            scaled.recycle()
        } catch (_: Exception) {
            bmp.recycle()
            cursor.y = drawWrapped(
                cursor.canvas,
                "图片路径：$imagePath",
                cursor.y,
                bodyPaint,
                maxBottom = PAGE_H - FOOTER_RESERVED,
            )
        }
    }

    private fun drawTextPaged(
        cursor: PageCursor,
        text: String,
        paint: Paint,
        footerPaint: Paint,
    ) {
        val maxWidth = (PAGE_W - 2 * MARGIN).toFloat()
        val normalized = text.replace("\r\n", "\n")
        for (paragraph in normalized.split('\n')) {
            var remaining = if (paragraph.isEmpty()) " " else paragraph
            while (remaining.isNotEmpty()) {
                cursor.ensureSpace(paint.textSize + 8f, footerPaint)
                val count = paint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                val piece = remaining.substring(0, count)
                cursor.canvas.drawText(piece, MARGIN.toFloat(), cursor.y + paint.textSize, paint)
                remaining = remaining.substring(count)
                cursor.y += paint.textSize + 4f
            }
        }
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
        private const val FOOTER_RESERVED = 56
        private const val COLOR_PRIMARY = 0xFF0F766E.toInt()
        private const val COLOR_TEXT = 0xFF1E293B.toInt()
        private const val COLOR_MUTED = 0xFF64748B.toInt()
        private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        const val DISCLAIMER: String =
            "免责声明：本材料包仅整理自用户本地票证资料，不构成理赔承诺、保险责任认定或法律意见。"

        fun buildSummaryRows(ticket: Ticket): List<Pair<String, String>> = listOf(
            "商家" to ticket.merchantName.ifBlank { "—" },
            "金额" to MoneyFormats.formatYuan(ticket.amountCents),
            "购买日" to DateFormats.formatEpochDay(ticket.purchaseDateEpochDay),
            "保修到期" to DateFormats.formatEpochDay(ticket.warrantyEndEpochDay),
            "备注" to ticket.note.ifBlank { "—" },
        )

        fun buildFileName(ticket: Ticket, date: LocalDate): String {
            val merchant = sanitizeFileToken(ticket.merchantName).ifBlank { "未命名" }
            val day = date.format(FILE_STAMP)
            return "票证记_送修材料包_${merchant}_$day.pdf"
        }

        fun sanitizeFileToken(raw: String): String {
            val cleaned = raw.trim()
                .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
                .take(24)
            return cleaned.trim('_')
        }

        fun drawWrapped(
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
                        canvas.drawText("…", MARGIN.toFloat(), y + paint.textSize, paint)
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
    }
}
