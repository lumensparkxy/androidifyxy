package com.maswadkar.developers.androidify.ui.components

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.maswadkar.developers.androidify.ChatMessage
import com.maswadkar.developers.androidify.ui.theme.KrishiMitraTheme
import com.maswadkar.developers.androidify.util.ProductRecommendationParser
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

    // Parse product recommendations for model messages (not loading, not user)
    val parseResult = remember(message.text, message.isUser, message.isLoading) {
        if (!message.isUser && !message.isLoading) {
            ProductRecommendationParser.parse(message.text)
        } else {
            null
        }
    }

    // Use parsed display text for model messages, original text for user messages
    val displayText = parseResult?.displayText ?: message.text
    val recommendations = parseResult?.recommendations ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
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
                    // User messages - with optional image
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Display attached image if present (prefer local URI, fallback to remote URL for history)
                        val imageSource = message.imageUri?.let { Uri.parse(it) } ?: message.imageUrl
                        imageSource?.let { source ->
                            AsyncImage(
                                model = source,
                                contentDescription = "Attached image",
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (message.text.isNotBlank() && message.text != "[Image attached]") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Display text if present and not just placeholder
                        if (message.text.isNotBlank() && message.text != "[Image attached]") {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = chatColors.userBubbleText
                            )
                        } else if (imageSource == null) {
                            // Show text if no image (fallback)
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = chatColors.userBubbleText
                            )
                        }
                    }
                } else {
                    // Model messages - render markdown (with product block stripped)
                    MarkdownContent(
                        markdown = displayText,
                        textColor = chatColors.modelBubbleText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Product recommendations below the bubble (only for model messages)
        if (recommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ProductRecommendationList(
                recommendations = recommendations,
                modifier = Modifier.padding(start = 4.dp, end = 48.dp) // Align with model bubble
            )
        }
    }
}

@Composable
private fun MarkdownContent(
    markdown: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = markdownColor(
        text = textColor
    )

    val typography = markdownTypography(
        text = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        paragraph = MaterialTheme.typography.bodyLarge.copy(color = textColor)
    )

    Markdown(
        content = markdown,
        colors = colors,
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
