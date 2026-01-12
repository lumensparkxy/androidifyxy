package com.maswadkar.developers.androidify.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.KnowledgeDocument
import com.maswadkar.developers.androidify.ui.components.NativeAdCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDocumentsScreen(
    cropId: String,
    cropName: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeDocumentsViewModel = viewModel(
        factory = KnowledgeDocumentsViewModel.Factory(cropId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val languageCode = configuration.locales[0].language

    // Handle PDF opening when download URL is available
    LaunchedEffect(uiState.downloadUrl) {
        uiState.downloadUrl?.let { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "application/pdf")
                    flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_pdf_with)))
            } catch (e: Exception) {
                // If no PDF viewer is available, open in browser
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(browserIntent)
            }
            viewModel.clearDownloadUrl()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = cropName.ifEmpty { stringResource(R.string.knowledge_documents_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.knowledge_error),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.clearError()
                            viewModel.refresh()
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                uiState.documents.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.knowledge_no_documents),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Native Ad Unit
                        NativeAdCard(
                            adUnitId = "ca-app-pub-6317522941728465/6769905906"
                        )

                        DocumentsList(
                            documents = uiState.documents,
                            languageCode = languageCode,
                            downloadingDocumentId = uiState.downloadingDocumentId,
                            onDocumentClick = { viewModel.onDocumentClick(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentsList(
    documents: List<KnowledgeDocument>,
    languageCode: String,
    downloadingDocumentId: String?,
    onDocumentClick: (KnowledgeDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(documents, key = { it.id }) { document ->
            DocumentCard(
                document = document,
                languageCode = languageCode,
                isDownloading = downloadingDocumentId == document.id,
                onClick = { onDocumentClick(document) }
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: KnowledgeDocument,
    languageCode: String,
    isDownloading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = document.getLocalizedTitle(languageCode)
    val displayDescription = document.getLocalizedDescription(languageCode)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (displayDescription.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

