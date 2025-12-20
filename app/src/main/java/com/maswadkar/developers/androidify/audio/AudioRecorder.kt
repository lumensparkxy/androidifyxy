package com.maswadkar.developers.androidify.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * AudioRecorder captures PCM audio at 16kHz, 16-bit mono for Gemini Live API.
 * Exposes audio data as a Flow of ByteArray chunks.
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000 // 16kHz required by Gemini Live API
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 100 // Send audio every 100ms
    }

    private var audioRecord: AudioRecord? = null
    private val bufferSize: Int = maxOf(
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
        SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000 // 16-bit = 2 bytes per sample
    )

    /**
     * Check if audio recording permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording and emit audio chunks as a Flow.
     * The flow will emit ByteArray chunks of PCM audio data.
     * Recording stops when the flow collection is cancelled.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<ByteArray> = flow {
        if (!hasPermission()) {
            Log.e(TAG, "Recording permission not granted")
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            audioRecord?.startRecording()
            Log.d(TAG, "Recording started - Sample rate: $SAMPLE_RATE, Buffer size: $bufferSize")
            Log.d(TAG, "AudioRecord state: ${audioRecord?.state}, recording state: ${audioRecord?.recordingState}")

            val buffer = ByteArray(bufferSize)
            var totalBytesRead = 0L
            var chunkCount = 0

            while (coroutineContext.isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    totalBytesRead += bytesRead
                    chunkCount++

                    // Log every 20th chunk to avoid spam
                    if (chunkCount % 20 == 1) {
                        // Calculate audio level (simple RMS)
                        var sum = 0L
                        for (i in 0 until bytesRead step 2) {
                            if (i + 1 < bytesRead) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                sum += sample.toLong() * sample.toLong()
                            }
                        }
                        val rms = kotlin.math.sqrt(sum.toDouble() / (bytesRead / 2))
                        Log.d(TAG, "Audio chunk #$chunkCount: $bytesRead bytes, RMS level: ${rms.toInt()}, total: $totalBytesRead bytes")
                    }

                    // Emit a copy of the buffer with actual data
                    emit(buffer.copyOf(bytesRead))
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    break
                }
            }

            Log.d(TAG, "Recording loop ended. Total chunks: $chunkCount, total bytes: $totalBytesRead")
        } finally {
            stopRecording()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stop recording and release resources
     */
    fun stopRecording() {
        try {
            audioRecord?.let { recorder ->
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                    Log.d(TAG, "Recording stopped")
                }
                recorder.release()
                Log.d(TAG, "AudioRecord released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        } finally {
            audioRecord = null
        }
    }
}

