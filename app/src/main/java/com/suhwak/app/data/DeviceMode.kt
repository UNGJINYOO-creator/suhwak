package com.suhwak.app.data

import android.content.Context

/**
 * 이 기기에만 있는 설정 (백업·가족 동기화에 넣지 않는다). 지금은 🧪 테스트 모드 하나.
 * 테스트 모드(부모 폰용): 잠금 없음, 공부 기록을 남기지 않음, 게임 무제한, 이 기기의 기록은 동기화로 올리지 않음.
 */
object DeviceMode {
    /** SharedPreferences 파일 이름: Backup·FamilySync가 이 파일은 건너뛴다. */
    const val PREFS = "device"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isTest(context: Context): Boolean = prefs(context).getBoolean("test", false)

    fun setTest(context: Context, on: Boolean) = prefs(context).edit().putBoolean("test", on).apply()
}
