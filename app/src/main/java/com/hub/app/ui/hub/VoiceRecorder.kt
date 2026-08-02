package com.hub.app.ui.hub

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Nimmt eine Sprachnachricht als AAC/MPEG-4 (.m4a) in den App-Cache auf. Bewusst schlank:
 * Start/Stop/Abbruch. Aufnahme setzt die RECORD_AUDIO-Berechtigung voraus (der Aufrufer
 * fragt sie ab).
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean = runCatching {
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(64_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        outputFile = file
        true
    }.getOrElse {
        runCatching { recorder?.release() }
        recorder = null
        false
    }

    /** Beendet die Aufnahme und liefert die Datei (oder null bei Fehler/zu kurz). */
    fun stop(): File? {
        val file = outputFile
        val ok = runCatching {
            recorder?.stop()
            true
        }.getOrDefault(false)
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
        return if (ok) file else file?.also { it.delete() }.let { null }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
