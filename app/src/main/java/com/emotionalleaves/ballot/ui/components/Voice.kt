package com.emotionalleaves.ballot.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

/**
 * On-device speech-to-text that keeps listening across pauses until you tap stop.
 * Prefers the offline recognizer so debriefs work without a connection.
 */
class SpeechCapture(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set
    private val finals = StringBuilder()
    private var recognizer: SpeechRecognizer? = null
    private var startedAt = 0L
    private var onFinished: ((String, Int) -> Unit)? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
    }

    fun start(onDone: (text: String, seconds: Int) -> Unit) {
        if (!available) { error = "Speech recognition is not available on this phone."; return }
        finals.clear(); partial = ""; error = ""
        onFinished = onDone
        startedAt = System.currentTimeMillis()
        listening = true
        listenOnce()
    }

    private fun listenOnce() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                partial = (finals.toString() + " " + p).trim()
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { finals.append(' ').append(it) }
                partial = finals.toString().trim()
                if (listening) listenOnce()
            }
            override fun onError(code: Int) {
                when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (listening) listenOnce()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { error = "Microphone permission is needed."; stop() }
                    else -> { if (listening && finals.isEmpty()) error = "The recognizer stopped (code $code). Tap to try again."; stop() }
                }
            }
        })
        r.startListening(intent())
    }

    fun stop() {
        if (!listening) return
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        val text = (finals.toString().trim().ifBlank { partial }).trim()
        onFinished?.invoke(text, secs)
        onFinished = null
    }

    fun release() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }
}

@Composable
fun rememberSpeechCapture(): SpeechCapture {
    val ctx = LocalContext.current
    val sc = remember { SpeechCapture(ctx) }
    DisposableEffect(Unit) { onDispose { sc.release() } }
    return sc
}

/** Asks for the microphone once, then runs the action. */
@Composable
fun rememberMicGate(): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.invoke()
        pending = null
    }
    return { action ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { pending = action; launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

/** Audio recording for speaking drills, to listen back. Files stay in app storage. */
class DrillRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set
    var recording by mutableStateOf(false)
        private set

    fun start(): Boolean = runCatching {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val f = File(dir, "rep-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(22_050)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
        recording = true
        true
    }.getOrDefault(false)

    fun stop(): File? {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        recording = false
        return file
    }
}
