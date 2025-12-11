package com.maswadkar.developers.androidify.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioPlayer plays PCM audio received from Gemini Live API.
 * Supports streaming playback of audio chunks.
 */
class AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
        const val SAMPLE_RATE = 24000 // Gemini Live API outputs 24kHz audio
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isPlaying = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private val bufferSize: Int = maxOf(
        AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
        SAMPLE_RATE * 2 // 1 second buffer minimum
    )

    /**
     * Initialize the audio track for playback
     */
    fun initialize() {
        if (audioTrack != null) return

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            Log.d(TAG, "AudioTrack initialized - Sample rate: $SAMPLE_RATE, Buffer size: $bufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}")
        }
    }

    /**
     * Queue audio data for playback
     */
    fun queueAudio(audioData: ByteArray) {
        if (audioData.isNotEmpty()) {
            audioQueue.offer(audioData)
            Log.d(TAG, "🔊 Queued ${audioData.size} bytes for playback, queue size: ${audioQueue.size}")
        }
    }

    /**
     * Start playback loop - call this from a coroutine
     */
    suspend fun startPlayback() = withContext(Dispatchers.IO) {
        if (isPlaying.getAndSet(true)) {
            Log.w(TAG, "Playback already running")
            return@withContext
        }

        initialize()

        try {
            audioTrack?.play()
            Log.d(TAG, "Playback started, AudioTrack state: ${audioTrack?.state}, play state: ${audioTrack?.playState}")

            var totalBytesPlayed = 0L
            var chunksPlayed = 0

            while (isPlaying.get()) {
                if (isPaused.get()) {
                    kotlinx.coroutines.delay(50)
                    continue
                }

                val audioData = audioQueue.poll()
                if (audioData != null) {
                    chunksPlayed++
                    val written = audioTrack?.write(audioData, 0, audioData.size) ?: -1
                    if (written > 0) {
                        totalBytesPlayed += written
                        if (chunksPlayed % 10 == 1) {
                            Log.d(TAG, "Played chunk #$chunksPlayed: $written bytes, total: $totalBytesPlayed bytes")
                        }
                    } else if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                    }
                } else {
                    // No data available, wait a bit
                    kotlinx.coroutines.delay(10)
                }
            }

            Log.d(TAG, "Playback loop ended. Total chunks: $chunksPlayed, total bytes: $totalBytesPlayed")
        } catch (e: Exception) {
            Log.e(TAG, "Playback error: ${e.message}")
        } finally {
            stopPlayback()
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        isPaused.set(true)
        audioTrack?.pause()
        Log.d(TAG, "Playback paused")
    }

    /**
     * Resume playback
     */
    fun resume() {
        isPaused.set(false)
        audioTrack?.play()
        Log.d(TAG, "Playback resumed")
    }

    /**
     * Stop playback and clear queue
     */
    fun stopPlayback() {
        isPlaying.set(false)
        isPaused.set(false)
        audioQueue.clear()

        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.flush()
                Log.d(TAG, "Playback stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stopPlayback()
        try {
            audioTrack?.release()
            Log.d(TAG, "AudioTrack released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    /**
     * Check if currently playing audio
     */
    fun isCurrentlyPlaying(): Boolean {
        return isPlaying.get() && !isPaused.get() && audioQueue.isNotEmpty()
    }

    /**
     * Check if audio queue has data
     */
    fun hasQueuedAudio(): Boolean {
        return audioQueue.isNotEmpty()
    }
}

