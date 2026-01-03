package com.maswadkar.developers.androidify.ui.components

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.Offer
import com.maswadkar.developers.androidify.data.OfferFilters
import com.maswadkar.developers.androidify.data.Supplier
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferDetailBottomSheet(
    offer: Offer,
    supplier: Supplier?,
    isLoadingSupplier: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.offer_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Product Info Section
            ProductInfoSection(offer = offer, dateFormat = dateFormat)

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // Supplier Section
            Text(
                text = stringResource(R.string.supplier_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingSupplier) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (supplier != null) {
                SupplierInfoSection(
                    supplier = supplier,
                    offerSupplierName = offer.supplierName
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Contact Buttons
                if (supplier.phone.isNotEmpty()) {
                    ContactButtons(
                        onWhatsAppClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = "https://wa.me/${supplier.phone.replace("+", "").replace(" ", "")}".toUri()
                            }
                            context.startActivity(intent)
                        },
                        onCallClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = "tel:${supplier.phone}".toUri()
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            } else {
                // Supplier not found, show basic info from offer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = offer.supplierName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.supplier_contact_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductInfoSection(
    offer: Offer,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Product Name
        Text(
            text = offer.productName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category chip
        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = OfferFilters.getCategoryDisplayName(offer.category),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Details Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DetailItem(
                label = stringResource(R.string.pack_size),
                value = "${offer.packSize.toInt()} ${offer.packUnit}"
            )

            DetailItem(
                label = stringResource(R.string.price_retail),
                value = "₹${offer.priceRetail.toInt()}"
            )
        }

        if (!offer.npkRaw.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    label = stringResource(R.string.npk_ratio),
                    value = offer.npkRaw
                )

                DetailItem(
                    label = stringResource(R.string.price_per_kg),
                    value = "₹${String.format(Locale.getDefault(), "%.2f", offer.priceNormalized)}/kg"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Location
        DetailItem(
            label = stringResource(R.string.location),
            value = OfferFilters.getDistrictDisplayName(offer.districtId)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Posted Date
        offer.createdAt?.toDate()?.let { date ->
            Text(
                text = stringResource(R.string.posted_on, dateFormat.format(date)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SupplierInfoSection(
    supplier: Supplier,
    offerSupplierName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = supplier.businessName.ifEmpty { offerSupplierName },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            if (supplier.address.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = supplier.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (supplier.phone.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = supplier.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactButtons(
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onWhatsAppClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_whatsapp),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.contact_whatsapp))
        }

        OutlinedButton(
            onClick = onCallClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_phone),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.contact_call))
        }
    }
}

