package com.suhwak.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.suhwak.app.R

/** Combo sounds: 띠링, 딩동댕동 (default), 뾰로롱. Bigger combos play the same sound a step higher. */
class Sfx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = listOf(R.raw.combo_coin, R.raw.combo_chime, R.raw.combo_bubble).map { pool.load(context, it, 1) }

    fun combo(kind: Int, combo: Int) {
        val rate = when {
            combo >= 10 -> 1.26f
            combo >= 5 -> 1.12f
            else -> 1f
        }
        pool.play(ids[kind.coerceIn(0, ids.lastIndex)], 0.9f, 0.9f, 1, 0, rate)
    }

    fun release() = pool.release()

    companion object {
        val names = listOf("띠링", "딩동댕동", "뾰로롱")
    }
}
