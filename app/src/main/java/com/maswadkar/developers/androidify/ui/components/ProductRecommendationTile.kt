package com.maswadkar.developers.androidify.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.ProductRecommendation
import com.maswadkar.developers.androidify.data.ProductType
import com.maswadkar.developers.androidify.util.AppConfigManager

/**
 * A clickable tile displaying a product recommendation from the AI.
 * When clicked, opens WhatsApp with a pre-composed inquiry message.
 */
@Composable
fun ProductRecommendationTile(
    recommendation: ProductRecommendation,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val productType = recommendation.getProductTypeEnum()

    // Build quantity string if available
    val quantityString = if (!recommendation.quantity.isNullOrBlank()) {
        val unit = recommendation.unit ?: ""
        " (${recommendation.quantity} $unit)".trim()
    } else {
        ""
    }

    // Get localized WhatsApp message
    val whatsappMessage = stringResource(
        R.string.product_recommendation_whatsapp_message,
        recommendation.productName,
        quantityString
    )

    val shape = RoundedCornerShape(14.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)

    // Slightly more premium background than flat tertiaryContainer
    val containerStart = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val containerEnd = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)

    Card(
        onClick = {
            // Open WhatsApp with pre-composed message
            val phoneNumber = AppConfigManager.getSupplierWhatsAppNumber()
            val encodedMessage = java.net.URLEncoder.encode(whatsappMessage, "UTF-8")
            val whatsappUri = "https://wa.me/$phoneNumber?text=$encodedMessage".toUri()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = whatsappUri
            }
            context.startActivity(intent)
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(listOf(containerStart, containerEnd)),
                shape = shape
            )
            .padding(1.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Inner surface to create a subtle stroke-like border
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    shape = shape
                )
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = productType.toIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Product info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = recommendation.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Quantity and type
                val typeLabel = productType.toLocalizedString()
                val detailText = if (!recommendation.quantity.isNullOrBlank()) {
                    "${recommendation.quantity} ${recommendation.unit ?: ""} • $typeLabel"
                } else {
                    typeLabel
                }

                Text(
                    text = detailText.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // CTA "chip"
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_whatsapp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = stringResource(R.string.product_recommendation_cta),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Ultra subtle border overlay (keeps it crisp on all backgrounds)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp)
        ) {
            // no-op placeholder (kept to avoid re-architecting; border handled by layered backgrounds)
        }
    }
}

/**
 * Displays a list of product recommendation tiles
 */
@Composable
fun ProductRecommendationList(
    recommendations: List<ProductRecommendation>,
    modifier: Modifier = Modifier
) {
    if (recommendations.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recommendations.forEach { recommendation ->
            ProductRecommendationTile(recommendation = recommendation)
        }
    }
}

/**
 * Get icon for product type
 */
private fun ProductType.toIcon(): ImageVector {
    return when (this) {
        ProductType.PESTICIDE -> Icons.Default.BugReport
        ProductType.FERTILIZER -> Icons.Default.Science
        ProductType.SEED -> Icons.Default.Grass
        ProductType.EQUIPMENT -> Icons.Default.Agriculture
        ProductType.OTHER -> Icons.Default.ShoppingBag
    }
}

/**
 * Get localized string for product type
 */
@Composable
private fun ProductType.toLocalizedString(): String {
    return when (this) {
        ProductType.PESTICIDE -> stringResource(R.string.product_type_pesticide)
        ProductType.FERTILIZER -> stringResource(R.string.product_type_fertilizer)
        ProductType.SEED -> stringResource(R.string.product_type_seed)
        ProductType.EQUIPMENT -> stringResource(R.string.product_type_equipment)
        ProductType.OTHER -> stringResource(R.string.product_type_other)
    }
}
