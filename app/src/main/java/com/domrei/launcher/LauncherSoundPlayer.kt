package com.domrei.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Low-latency sound pool for soft water-bobble click feedback across interactive UI elements.
 */
class LauncherSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var soundPool: SoundPool? = null
    private var bobbleSoundId: Int = 0

    @Volatile
    private var isLoaded: Boolean = false

    @Volatile
    private var isReleased: Boolean = false

    init {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == bobbleSoundId && !isReleased) {
                    isLoaded = true
                }
            }
            bobbleSoundId = pool.load(appContext, R.raw.ui_bobble, 1)
            soundPool = pool
        }
    }

    /** Plays the short bobble click pop if loaded and active. */
    fun playBobble() {
        val pool = soundPool ?: return
        if (!isLoaded || isReleased || bobbleSoundId == 0) return
        runCatching {
            pool.play(bobbleSoundId, 0.72f, 0.72f, 1, 0, 1.0f)
        }
    }

    /** Releases native SoundPool resources when leaving the launcher root composition. */
    fun release() {
        if (isReleased) return
        isReleased = true
        isLoaded = false
        runCatching {
            soundPool?.release()
        }
        soundPool = null
        bobbleSoundId = 0
    }
}

val LocalLauncherSoundPlayer = staticCompositionLocalOf<LauncherSoundPlayer?> { null }
