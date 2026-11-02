package com.suhwak.app.data

/**
 * Pure helpers that keep the evening Telegram short: the day's game results (one text entry per
 * 낚시·원정·시험, see [EveningReport.add]) become a few summary lines, and keyboard-mash spellings
 * are left out of the typo list.
 */
object EveningSummary {
    private val caughtRe = Regex("""^(?:🎣 낚시|⛵ 원정 최종전): (\S+) (.+?) ([\d.]+)(kg|톤) 낚음""")
    private val scoreRe = Regex("""정답 (\d+)/(\d+)""")
    private const val WRONG = "틀린 단어: "

    private data class Catch(val emoji: String, val name: String, val kg: Double, val label: String)

    /** The English words of a "틀린 단어: word(뜻), word(뜻(…), …)" line, splitting only outside brackets. */
    fun wrongWords(line: String): List<String> {
        val body = line.substringAfter(WRONG, "")
        if (body.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        var depth = 0
        val cur = StringBuilder()
        for (c in body) {
            when {
                c == '(' -> { depth++; cur.append(c) }
                c == ')' -> { depth--; cur.append(c) }
                c == ',' && depth <= 0 -> { parts += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        parts += cur.toString()
        return parts.map { it.substringBefore('(').trim() }.filter { it.isNotEmpty() }
    }

    /** The day's 낚시·원정·시험 entries as at most a handful of lines. */
    fun games(entries: List<String>): List<String> {
        val fishing = entries.filter { it.startsWith("🎣 낚시") }
        val giant = entries.filter { it.startsWith("⛵") }
        val tests = entries.filter { it.startsWith("📝") }
        val out = mutableListOf<String>()

        if (fishing.isNotEmpty()) {
            val catches = fishing.mapNotNull { e ->
                caughtRe.find(e.lineSequence().first())?.let { m ->
                    val n = m.groupValues[3].toDoubleOrNull() ?: 0.0
                    val kg = if (m.groupValues[4] == "톤") n * 1000 else n
                    Catch(m.groupValues[1], m.groupValues[2], kg, m.groupValues[3] + m.groupValues[4])
                }
            }
            val lost = fishing.size - catches.size
            val records = fishing.count { it.lineSequence().first().contains("🏆") }
            val (right, total) = fishing.mapNotNull { scoreRe.find(it) }
                .fold(0 to 0) { acc, m -> (acc.first + m.groupValues[1].toInt()) to (acc.second + m.groupValues[2].toInt()) }
            out += buildString {
                append("🎣 낚시 ${fishing.size}판 · ${catches.size}마리")
                if (lost > 0) append(" (놓침 $lost)")
                if (records > 0) append(" · 🏆 최고 기록 ${records}번")
                if (total > 0) append(" · 정답 ${right * 100 / total}%")
            }
            if (catches.isNotEmpty()) {
                val max = catches.maxOf { it.kg }
                val biggest = catches.filter { it.kg == max }
                out += "${biggest.first().emoji} 가장 큰 고기: ${biggest.map { it.name }.distinct().joinToString("·")} " +
                    "${biggest.first().label} · 잡은 종류 ${catches.map { it.name }.distinct().size}가지"
            }
            val wrong = fishing.flatMap { e -> e.lines().filter { WRONG in it }.flatMap(::wrongWords) }
            if (wrong.isNotEmpty()) {
                val counts = wrong.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                val again = counts.filter { it.value >= 2 }
                out += if (again.isNotEmpty()) {
                    "❌ 낚시에서 틀린 단어 ${counts.size}개 (여러 번 틀림: ${again.take(3).joinToString(", ") { "${it.key} ${it.value}번" }})"
                } else {
                    "❌ 낚시에서 틀린 단어 ${counts.size}개: ${counts.take(5).joinToString(", ") { it.key }}${if (counts.size > 5) " 등" else ""}"
                }
            }
        }
        // 원정 최종전 and 시험 are rare and matter: one line each, without the word list.
        (giant + tests).take(4).forEach { out += it.lineSequence().first() }
        if (giant.size + tests.size > 4) out += "   그 밖에 ${giant.size + tests.size - 4}개 더"
        val testWrong = tests.flatMap { e -> e.lines().filter { WRONG in it }.flatMap(::wrongWords) }.distinct()
        if (testWrong.isNotEmpty()) {
            out += "❌ 시험에서 틀린 단어: ${testWrong.take(5).joinToString(", ")}${if (testWrong.size > 5) " 등" else ""}"
        }
        return out
    }

    /** A real try at spelling [word], not a keyboard mash like "AAHcn" for "carry". */
    fun isRealTypo(response: String, word: String): Boolean {
        val r = response.trim()
        if (r.isEmpty() || !r.all { it in 'a'..'z' || it == ' ' || it == '-' || it == '\'' }) return false
        return distance(r, word.lowercase()) <= maxOf(2, word.length / 3)
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
