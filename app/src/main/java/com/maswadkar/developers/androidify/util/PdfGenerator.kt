package com.maswadkar.developers.androidify.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.maswadkar.developers.androidify.data.Conversation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object to generate PDF documents from conversations
 */
object PdfGenerator {

    private const val PAGE_WIDTH = 595  // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN).toInt()

    private const val HEADER_TEXT_SIZE = 18f
    private const val TITLE_TEXT_SIZE = 14f
    private const val DATE_TEXT_SIZE = 10f
    private const val MESSAGE_TEXT_SIZE = 11f
    private const val SENDER_TEXT_SIZE = 9f

    private const val MESSAGE_PADDING = 8f
    private const val MESSAGE_SPACING = 12f
    private const val BUBBLE_CORNER_RADIUS = 6f

    /**
     * Generate a PDF file from a conversation
     * @param context Android context
     * @param conversation The conversation to export
     * @return The generated PDF file, or null if generation failed
     */
    fun generatePdf(context: Context, conversation: Conversation): File? {
        return try {
            val document = PdfDocument()
            var pageNumber = 1
            var currentPage = createNewPage(document, pageNumber)
            var canvas = currentPage.canvas
            var yPosition = MARGIN

            // Setup paints
            val headerPaint = createTextPaint(HEADER_TEXT_SIZE, Color.parseColor("#1B5E20"), true)
            val titlePaint = createTextPaint(TITLE_TEXT_SIZE, Color.DKGRAY, true)
            val datePaint = createTextPaint(DATE_TEXT_SIZE, Color.GRAY, false)
            val userMessagePaint = createTextPaint(MESSAGE_TEXT_SIZE, Color.parseColor("#1B5E20"), false)
            val aiMessagePaint = createTextPaint(MESSAGE_TEXT_SIZE, Color.parseColor("#37474F"), false)
            val senderPaint = createTextPaint(SENDER_TEXT_SIZE, Color.GRAY, false)

            val userBubblePaint = Paint().apply {
                color = Color.parseColor("#E8F5E9") // Light green
                style = Paint.Style.FILL
            }
            val aiBubblePaint = Paint().apply {
                color = Color.parseColor("#ECEFF1") // Light blue-gray
                style = Paint.Style.FILL
            }

            // Draw header
            canvas.drawText("Krishi AI", MARGIN, yPosition + HEADER_TEXT_SIZE, headerPaint)
            yPosition += HEADER_TEXT_SIZE + 12f

            // Draw conversation title
            val title = conversation.title.ifEmpty { "Conversation" }
            val titleLayout = createStaticLayout(title, titlePaint, CONTENT_WIDTH)
            canvas.save()
            canvas.translate(MARGIN, yPosition)
            titleLayout.draw(canvas)
            canvas.restore()
            yPosition += titleLayout.height + 6f

            // Draw export date
            val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val exportDate = "Exported on ${dateFormat.format(Date())}"
            canvas.drawText(exportDate, MARGIN, yPosition + DATE_TEXT_SIZE, datePaint)
            yPosition += DATE_TEXT_SIZE + 16f

            // Draw separator line
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }
            canvas.drawLine(MARGIN, yPosition, PAGE_WIDTH - MARGIN, yPosition, linePaint)
            yPosition += 16f

            // Draw messages in continuous flow
            for (message in conversation.messages) {
                val isUser = message.isUser
                val messagePaint = if (isUser) userMessagePaint else aiMessagePaint
                val bubblePaint = if (isUser) userBubblePaint else aiBubblePaint
                val senderLabel = if (isUser) "You" else "Krishi AI"

                // Calculate message layout - use 75% of content width for bubbles
                val messageWidth = (CONTENT_WIDTH * 0.75f).toInt()
                val textWidth = messageWidth - (2 * MESSAGE_PADDING).toInt()
                val messageLayout = createStaticLayout(message.text, messagePaint, textWidth)

                // Calculate total height needed for this message bubble
                val senderHeight = SENDER_TEXT_SIZE + 4f
                val bubbleHeight = senderHeight + messageLayout.height + (2 * MESSAGE_PADDING)

                // Check if we need a new page (only if bubble won't fit)
                if (yPosition + bubbleHeight > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(currentPage)
                    pageNumber++
                    currentPage = createNewPage(document, pageNumber)
                    canvas = currentPage.canvas
                    yPosition = MARGIN
                }

                // Calculate bubble position (right-align for user, left-align for AI)
                val bubbleLeft = if (isUser) {
                    PAGE_WIDTH - MARGIN - messageWidth
                } else {
                    MARGIN
                }
                val bubbleRight = bubbleLeft + messageWidth
                val bubbleTop = yPosition
                val bubbleBottom = yPosition + bubbleHeight

                // Draw bubble background
                canvas.drawRoundRect(
                    bubbleLeft,
                    bubbleTop,
                    bubbleRight,
                    bubbleBottom,
                    BUBBLE_CORNER_RADIUS,
                    BUBBLE_CORNER_RADIUS,
                    bubblePaint
                )

                // Draw sender label
                val labelX = bubbleLeft + MESSAGE_PADDING
                canvas.drawText(senderLabel, labelX, bubbleTop + MESSAGE_PADDING + SENDER_TEXT_SIZE, senderPaint)

                // Draw message text
                canvas.save()
                canvas.translate(bubbleLeft + MESSAGE_PADDING, bubbleTop + MESSAGE_PADDING + senderHeight)
                messageLayout.draw(canvas)
                canvas.restore()

                // Move to next message position
                yPosition = bubbleBottom + MESSAGE_SPACING
            }

            // Finish the last page
            document.finishPage(currentPage)

            // Save to file
            val fileName = "Krishi_AI_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }
            document.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createNewPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(pageInfo)
    }

    private fun createTextPaint(textSize: Float, color: Int, isBold: Boolean): TextPaint {
        return TextPaint().apply {
            this.textSize = textSize
            this.color = color
            this.isAntiAlias = true
            if (isBold) {
                this.isFakeBoldText = true
            }
        }
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(true)
            .build()
    }
}

