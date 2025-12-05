package com.maswadkar.developers.androidify.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maswadkar.developers.androidify.ChatMessage
import com.maswadkar.developers.androidify.ui.theme.KrishiMitraTheme
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val chatColors = KrishiMitraTheme.chatColors
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp * 0.8).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = if (message.isUser) {
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 4.dp
                )
            } else {
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                )
            },
            color = if (message.isUser) chatColors.userBubble else chatColors.modelBubble,
            tonalElevation = if (message.isUser) 0.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .animateContentSize()
        ) {
            if (message.isLoading) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  Thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isUser) chatColors.userBubbleText else chatColors.modelBubbleText
                    )
                }
            } else if (message.isUser) {
                // User messages - plain text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = chatColors.userBubbleText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                // Model messages - render markdown
                MarkdownContent(
                    markdown = message.text,
                    textColor = chatColors.modelBubbleText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownContent(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val markdownColors = markdownColor(
        text = textColor,
        codeText = textColor,
        linkText = MaterialTheme.colorScheme.primary,
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
        dividerColor = MaterialTheme.colorScheme.outlineVariant
    )

    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        h2 = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        h3 = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        h4 = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        h5 = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        h6 = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        text = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        code = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = textColor
        ),
        quote = MaterialTheme.typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic,
            color = textColor.copy(alpha = 0.8f)
        ),
        paragraph = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        ordered = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        bullet = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        list = MaterialTheme.typography.bodyLarge.copy(color = textColor)
    )

    Markdown(
        content = markdown,
        colors = markdownColors,
        typography = typography,
        modifier = modifier
    )
}

@Composable
fun UserBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    ChatBubble(
        message = ChatMessage(text = text, isUser = true),
        modifier = modifier
    )
}

@Composable
fun ModelBubble(
    text: String,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    ChatBubble(
        message = ChatMessage(text = text, isUser = false, isLoading = isLoading),
        modifier = modifier
    )
}

