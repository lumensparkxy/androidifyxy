package com.maswadkar.developers.androidify.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maswadkar.developers.androidify.R
import com.maswadkar.developers.androidify.data.VoiceUsage

@Composable
fun LiveConversationScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveConversationViewModel = viewModel()
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val voiceUsage by viewModel.voiceUsageFlow.collectAsState()
    val quotaExceeded by viewModel.quotaExceeded.collectAsState()

    // Handle back press
    BackHandler {
        viewModel.endSession()
    }

    // Start session when screen is shown (only if quota not exceeded)
    LaunchedEffect(Unit) {
        if (viewModel.hasRecordPermission() && !quotaExceeded) {
            viewModel.startSession()
        }
    }

    // Clean up and dismiss when session ends
    LaunchedEffect(sessionState) {
        if (sessionState == LiveSessionState.Ended) {
            viewModel.resetState()
            onDismiss()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.endSession()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Remaining quota indicator at the top
            QuotaIndicator(
                voiceUsage = voiceUsage,
                quotaExceeded = quotaExceeded
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status indicator
            AnimatedStatusIndicator(
                state = sessionState,
                quotaExceeded = quotaExceeded,
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status text
            Text(
                text = if (quotaExceeded && sessionState is LiveSessionState.Idle) {
                    stringResource(R.string.voice_quota_exceeded_title)
                } else {
                    getStatusText(sessionState)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle/hint
            Text(
                text = if (quotaExceeded && sessionState is LiveSessionState.Idle) {
                    stringResource(R.string.voice_quota_exceeded)
                } else {
                    getStatusHint(sessionState)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            // End call button
            FilledIconButton(
                onClick = {
                    if (quotaExceeded && sessionState is LiveSessionState.Idle) {
                        onDismiss()
                    } else {
                        viewModel.endSession()
                    }
                },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = stringResource(R.string.end_call),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (quotaExceeded && sessionState is LiveSessionState.Idle) {
                    stringResource(R.string.close)
                } else {
                    stringResource(R.string.end_call)
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun QuotaIndicator(
    voiceUsage: VoiceUsage,
    quotaExceeded: Boolean,
    modifier: Modifier = Modifier
) {
    val remainingMinutes = voiceUsage.remainingMinutes()
    val backgroundColor = if (quotaExceeded) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
    } else if (remainingMinutes < 10) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    }

    val textColor = if (quotaExceeded) {
        MaterialTheme.colorScheme.error
    } else if (remainingMinutes < 10) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Text(
            text = stringResource(R.string.voice_quota_remaining, remainingMinutes.toInt()),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AnimatedStatusIndicator(
    state: LiveSessionState,
    quotaExceeded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Pulsing animation for listening state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Wave animation for speaking state
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Show quota exceeded indicator when quota is exhausted and idle
        if (quotaExceeded && state is LiveSessionState.Idle) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
            }
        } else when (state) {
            is LiveSessionState.Connecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }

            is LiveSessionState.Listening -> {
                // Pulsing circle with mic icon
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            is LiveSessionState.Processing -> {
                // Thinking indicator
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }

            is LiveSessionState.ModelSpeaking -> {
                // Animated wave effect with speaker icon
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(waveScale)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            is LiveSessionState.Error -> {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                }
            }

            else -> {
                // Idle or Ended - simple circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun getStatusText(state: LiveSessionState): String {
    return when (state) {
        is LiveSessionState.Idle -> stringResource(R.string.live_idle)
        is LiveSessionState.Connecting -> stringResource(R.string.live_connecting)
        is LiveSessionState.Listening -> stringResource(R.string.live_listening)
        is LiveSessionState.Processing -> stringResource(R.string.live_processing)
        is LiveSessionState.ModelSpeaking -> stringResource(R.string.live_speaking)
        is LiveSessionState.Ended -> stringResource(R.string.live_ended)
        is LiveSessionState.Error -> stringResource(R.string.live_error)
    }
}

@Composable
private fun getStatusHint(state: LiveSessionState): String {
    return when (state) {
        is LiveSessionState.Listening -> stringResource(R.string.live_listening_hint)
        is LiveSessionState.ModelSpeaking -> stringResource(R.string.live_speaking_hint)
        is LiveSessionState.Error -> state.message
        else -> ""
    }
}
