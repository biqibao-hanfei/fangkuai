package com.example.rossblocks.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 轻量合成音效（无需 raw 资源）：放置与消除听感区分明显。
 */
class GameSoundPlayer {

    private val sampleRate = 24_000

    private fun buildTrack(minBuffer: Int): AudioTrack {
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val bufSize = maxOf(
            minBuffer * 2,
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        )
        return AudioTrack.Builder()
            .setAudioAttributes(attr)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    private fun sineWave(
        durationMs: Int,
        freq: Double,
        amplitude: Double = 0.55,
        fadeInMs: Int = 6,
        fadeOutMs: Int = 18
    ): ShortArray {
        val n = sampleRate * durationMs / 1000
        val out = ShortArray(n)
        val fi = sampleRate * fadeInMs / 1000
        val fo = sampleRate * fadeOutMs / 1000
        for (i in 0 until n) {
            val t = i / sampleRate.toDouble()
            val envIn = min(1.0, i / fi.toDouble().coerceAtLeast(1.0))
            val envOut = min(1.0, (n - i) / fo.toDouble().coerceAtLeast(1.0))
            val env = envIn * envOut
            val s = sin(2 * PI * freq * t) * amplitude * env
            out[i] = (s * 32000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun mixTwoTone(
        durationMs: Int,
        f1: Double,
        f2: Double,
        split: Double = 0.42
    ): ShortArray {
        val n = sampleRate * durationMs / 1000
        val out = ShortArray(n)
        val splitI = (n * split).toInt().coerceIn(1, n - 1)
        for (i in 0 until n) {
            val t = i / sampleRate.toDouble()
            val f = if (i < splitI) f1 else f2
            val env = min(1.0, i / (sampleRate * 0.008)) * min(1.0, (n - i) / (sampleRate * 0.025))
            val s = sin(2 * PI * f * t) * 0.5 * env
            out[i] = (s * 30000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun playPcm(samples: ShortArray) {
        if (samples.isEmpty()) return
        val bb = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)
        bb.rewind()
        val track = buildTrack(samples.size)
        track.write(bb, samples.size * 2, AudioTrack.WRITE_BLOCKING)
        track.setVolume(0.32f)
        track.play()
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    t?.stop()
                    t?.release()
                }

                override fun onPeriodicNotification(t: AudioTrack?) {}
            },
            Handler(Looper.getMainLooper())
        )
    }

    fun playPlace() {
        try {
            playPcm(mixTwoTone(110, 330.0, 495.0, 0.38))
        } catch (_: Exception) {
        }
    }

    /** 消除时从左到右：step 越大音略高 */
    fun playClearStep(step: Int) {
        try {
            val f = 520.0 + step * 28.0
            playPcm(sineWave(38, f, amplitude = 0.42, fadeInMs = 2, fadeOutMs = 12))
        } catch (_: Exception) {
        }
    }

    fun playClearFinish() {
        try {
            val a = sineWave(55, 660.0, 0.35)
            val b = sineWave(55, 880.0, 0.28)
            for (i in a.indices) {
                a[i] = (a[i] + b.getOrElse(i) { 0 }).toInt().coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                ).toShort()
            }
            playPcm(a)
        } catch (_: Exception) {
        }
    }

    fun playHammer() {
        try {
            playPcm(sineWave(70, 180.0, 0.6, fadeInMs = 2, fadeOutMs = 25))
        } catch (_: Exception) {
        }
    }
}
