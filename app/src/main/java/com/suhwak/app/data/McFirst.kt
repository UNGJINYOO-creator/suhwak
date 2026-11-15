package com.suhwak.app.data

import android.content.Context
import org.json.JSONArray
import kotlin.math.abs

/**
 * 객관식 먼저: 주관식이 많으면 아이가 쉽게 포기하니까, 유형마다 객관식으로 먼저 단련한 뒤 주관식을 낸다.
 *  - 주관식 씨앗은 그 유형이 아직 '열리지' 않았으면 앱이 만든 오답 보기 4개를 섞어 객관식(5지선다)으로 보여준다.
 *    (답이 숫자·분수·x=값·연립 x=a, y=b 일 때. 그 밖의 답은 원래 주관식으로)
 *  - 그 유형의 객관식(원래 객관식이든 바꾼 것이든)을 힌트 없이 1차에 서로 다른 문제로 [StudySettings.mcBeforeShort]번 맞히면 열린다.
 *  - 기록은 씨앗의 sourceId(기기마다 같은 값)로 남겨서 가족 기기 동기화에서도 같은 문제로 센다.
 */
class McFirst(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val settings = StudySettings(context)

    private fun keyOf(w: Word) = w.sourceId.ifBlank { "id${w.id}" }

    /** 이 유형에서 1차에 맞힌 객관식 문제들. */
    fun solved(concept: String): Set<String> = prefs.getStringSet(concept, emptySet())!!

    /** 이 유형의 주관식이 열렸는지 (설정이 0이면 처음부터 열림 = 원래대로). */
    fun unlocked(concept: String): Boolean {
        val need = settings.mcBeforeShort
        return need <= 0 || concept.isBlank() || solved(concept).size >= need
    }

    /** 객관식 문제를 1차에 맞힘. 이번에 유형이 열렸으면 true. */
    fun record(w: Word): Boolean {
        if (w.pos.isBlank()) return false
        val before = unlocked(w.pos)
        prefs.edit().putStringSet(w.pos, solved(w.pos) + keyOf(w)).apply()
        return !before && unlocked(w.pos)
    }

    /** 지금 보여줄 모양: 주관식인데 아직 안 열린 유형이면 객관식으로 바꾼 것, 아니면 그대로. */
    fun view(w: Word): Word = if (w.qtype == "short" && !unlocked(w.pos)) asChoice(w) ?: w else w

    companion object {
        const val PREFS = "mcFirst"
        private const val CIRCLES = "①②③④⑤"

        /** 주관식 씨앗을 5지선다로 (보기 순서는 문제마다 늘 같게). 보기를 못 만들면 null. */
        fun asChoice(w: Word): Word? {
            val wrong = distractors(w.meaning, w.id) ?: return null
            val right = show(w.meaning)
            val rnd = kotlin.random.Random(w.id * 31 + 7)
            val all = (wrong + right).shuffled(rnd)
            val at = all.indexOf(right)
            return w.copy(qtype = "mc", choices = JSONArray(all).toString(), meaning = CIRCLES[at].toString())
        }

        private val INT = Regex("""^-?\d+$""")
        private val DEC = Regex("""^-?\d+\.\d+$""")
        private val FRAC = Regex("""^(-?)(\d+)/(\d+)$""")
        private val VAR = Regex("""^([a-z])=(.+)$""")
        private val PAIR = Regex("""^([a-z])=([^,]+),([a-z])=([^,]+)$""")

        /** 답을 보기 글자로: 분수는 수식으로. */
        fun show(answer: String): String {
            val a = answer.replace(" ", "")
            PAIR.matchEntire(a)?.let { m -> return "${m.groupValues[1]}=${num(m.groupValues[2])}, ${m.groupValues[3]}=${num(m.groupValues[4])}" }
            VAR.matchEntire(a)?.let { m -> return "${m.groupValues[1]}=${num(m.groupValues[2])}" }
            return num(a)
        }

        private fun num(v: String): String = FRAC.matchEntire(v)?.let { m ->
            "${'$'}${m.groupValues[1]}\\frac{${m.groupValues[2]}}{${m.groupValues[3]}}${'$'}"
        } ?: v

        /** 오답 보기 4개 (서로 다르고 정답과 값이 다름). 만들 수 없는 답이면 null. */
        fun distractors(answer: String, seed: Long): List<String>? {
            val a = answer.replace(" ", "")
            val rnd = kotlin.random.Random(seed)
            PAIR.matchEntire(a)?.let { m ->
                val (x, xv, y, yv) = m.destructured
                val xs = (listOf(xv) + (numberWrongs(xv, rnd) ?: return null)).take(3)
                val ys = (listOf(yv) + (numberWrongs(yv, rnd) ?: return null)).take(3)
                val pairs = mutableListOf<Pair<String, String>>()
                if (xv != yv) pairs += yv to xv // x와 y를 바꿔 쓴 실수
                for (i in xs) for (j in ys) if (!(i == xv && j == yv)) pairs += i to j
                val picked = pairs.distinct().let { listOf(it.first()) + it.drop(1).shuffled(rnd) }.take(4)
                if (picked.size < 4) return null
                return picked.map { (i, j) -> "$x=${num(i)}, $y=${num(j)}" }
            }
            VAR.matchEntire(a)?.let { m ->
                return numberWrongs(m.groupValues[2], rnd)?.take(4)?.map { "${m.groupValues[1]}=${num(it)}" }
            }
            return numberWrongs(a, rnd)?.take(4)?.map { num(it) }
        }

        /** 수 하나의 그럴듯한 오답들 (가까운 수, 부호 실수, 두 배 등; 분수는 뒤집기·분자/분모 하나 차이). */
        private fun numberWrongs(v: String, rnd: kotlin.random.Random): List<String>? {
            val out = linkedSetOf<String>()
            when {
                INT.matches(v) -> {
                    val n = v.toLong()
                    val near = listOf(n + 1, n - 1, n + 2, n - 2, n * 2, n + 10, n - 10, n + 3)
                    val first = if (n != 0L) listOf(-n) else emptyList()
                    (first + near.shuffled(rnd)).forEach { if (it != n) out += it.toString() }
                }
                DEC.matches(v) -> {
                    val x = java.math.BigDecimal(v)
                    val step = java.math.BigDecimal.ONE.movePointLeft(x.scale())
                    val cands = listOf(
                        x.negate(), x + step, x - step, x.movePointRight(1), x.movePointLeft(1),
                        x + step + step, x + java.math.BigDecimal.ONE, x - java.math.BigDecimal.ONE,
                    )
                    (listOf(cands[0]) + cands.drop(1).shuffled(rnd)).forEach { c ->
                        if (c.compareTo(x) != 0) out += c.stripTrailingZeros().toPlainString()
                    }
                }
                FRAC.matches(v) -> {
                    val (sign, ps, qs) = FRAC.matchEntire(v)!!.destructured
                    val p = ps.toLong(); val q = qs.toLong()
                    if (q == 0L) return null
                    val neg = sign == "-"
                    fun f(a: Long, b: Long, minus: Boolean = neg) = if (a <= 0 || b <= 1 || a % b == 0L) null else "${if (minus) "-" else ""}$a/$b"
                    val cands = listOfNotNull(f(p, q, !neg), f(q, p), f(p + 1, q), f(p, q + 1), f(p - 1, q), f(p, q - 1), f(p * 2, q), f(p + q, q))
                    val value = (if (neg) -1.0 else 1.0) * p / q
                    (listOf(cands[0]) + cands.drop(1).shuffled(rnd)).forEach { c ->
                        val m = FRAC.matchEntire(c)!!
                        val cv = (if (m.groupValues[1] == "-") -1.0 else 1.0) * m.groupValues[2].toLong() / m.groupValues[3].toLong()
                        if (abs(cv - value) > 1e-12 && out.none { o -> FRAC.matchEntire(o)?.let { om -> abs((if (om.groupValues[1] == "-") -1.0 else 1.0) * om.groupValues[2].toLong() / om.groupValues[3].toLong() - cv) < 1e-12 } == true }) out += c
                    }
                }
                else -> return null
            }
            return out.toList().takeIf { it.size >= 4 }
        }
    }
}
