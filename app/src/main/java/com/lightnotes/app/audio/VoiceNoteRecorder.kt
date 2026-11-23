package com.lightnotes.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceNoteRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null
    private var startTimeMs: Long = 0

    val isRecording: Boolean get() = mediaRecorder != null
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    fun startRecording(): File {
        val audioDir = File(context.filesDir, "audio").apply { if (!exists()) mkdirs() }
        val file = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
        currentAudioFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        startTimeMs = System.currentTimeMillis()
        return file
    }

    fun stopRecording(): Long {
        val duration = System.currentTimeMillis() - startTimeMs
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        return duration
    }

    fun playAudio(file: File, onCompletion: () -> Unit) {
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener {
                stopPlayback()
                onCompletion()
            }
            start()
        }
    }

    fun pausePlayback() {
        mediaPlayer?.pause()
    }

    fun resumePlayback() {
        mediaPlayer?.start()
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun cleanup() {
        if (isRecording) stopRecording()
        stopPlayback()
    }
}
