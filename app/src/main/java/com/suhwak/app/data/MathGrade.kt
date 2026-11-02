package com.suhwak.app.data

/**
 * 수학 답 채점 (마클리 core/grading.py와 같은 규칙). 표기 차이는 정답으로 본다:
 *  - 공백:              "x=4, y=-2"  == "x = 4,y=-2"
 *  - 원문자 <-> 숫자:     "③" == "3", 여러 개는 순서 상관없이 "②, ⑤" == "5,2"
 *  - 위첨자:             "12cm²" == "12cm^2"
 *  - 변수=값 순서:        "x=4, y=-2" == "y=-2, x=4"
 *  - 변수 하나:           "x=4" == "4"
 *  - pi / π, ° / 도:     "9pi" == "9π", "65°" == "65도"
 *  - 흔한 단위 생략:      "65°" == "65", "15cm" == "15"
 *  - 분수·소수 같은 값:   "1/2" == "0.5" == "2/4"
 * AI 없이 기기 안에서 바로 채점한다.
 */
object MathGrade {
    private const val CIRCLED = "①②③④⑤"
    private val superscripts = mapOf('²' to "^2", '³' to "^3", '⁴' to "^4")
    private val unitSuffix = Regex("(cm\\^?2|cm\\^?3|cm|mm|km|kg|도|개|명|살|점|원|초|분|시간|g|m)$")
    private val varValue = Regex("^([a-zA-Z])=(-?\\d+(?:\\.\\d+)?(?:/\\d+)?)$")
    private val choiceToken = Regex("[①②③④⑤]|\\d+")
    private val number = Regex("^[-+]?\\d+(\\.\\d+)?(/\\d+(\\.\\d+)?)?$")

    fun isRight(qtype: String, correct: String, submitted: String): Boolean {
        if (submitted.isBlank()) return false
        return if (qtype == "mc") choice(correct) == choice(submitted) else short(correct, submitted)
    }

    /** 객관식: 원문자/숫자를 숫자로, 여러 개면 정렬해서 "2,5". */
    fun choice(s: String): String {
        val tokens = choiceToken.findAll(s.trim()).map { m ->
            val v = m.value
            if (v.length == 1 && v[0] in CIRCLED) (CIRCLED.indexOf(v[0]) + 1).toString() else v
        }.toSortedSet(compareBy { it.toIntOrNull() ?: 0 }).toList()
        return if (tokens.isEmpty()) s.trim() else tokens.joinToString(",")
    }

    fun normalize(s: String): String {
        var t = s.trim()
        superscripts.forEach { (k, v) -> t = t.replace(k.toString(), v) }
        t = t.replace(Regex("\\s+"), "").lowercase().replace("pi", "π").replace("°", "도")
        return t
    }

    private fun dropUnit(s: String) = unitSuffix.replaceFirst(s, "")

    private fun dropSingleVar(s: String) = varValue.matchEntire(s)?.groupValues?.get(2) ?: s

    /** "x=4,y=-2" -> {(x,4),(y,-2)}, 아니면 null. */
    private fun pairs(s: String): Set<Pair<String, String>>? {
        val parts = s.split(",")
        if (parts.size < 2) return null
        return parts.map { varValue.matchEntire(it) ?: return null }.map { it.groupValues[1] to it.groupValues[2] }.toSet()
    }

    /** "3/8", "0.375", "-2" 같은 수를 정확한 분수(분자, 분모)로. 수가 아니면 null. */
    private fun value(s: String): Pair<java.math.BigInteger, java.math.BigInteger>? {
        if (!number.matches(s)) return null
        val (a, b) = if ("/" in s) s.split("/").let { it[0] to it[1] } else s to "1"
        if (java.math.BigDecimal(b).signum() == 0) return null
        val x = java.math.BigDecimal(a).divide(java.math.BigDecimal(b), 20, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros()
        // 소수점 아래 20자리에서 끊으므로 1/3 같은 값은 같은 값끼리만 같게 나온다 (그걸로 충분)
        val scale = x.scale().coerceAtLeast(0)
        val num = x.movePointRight(scale).toBigIntegerExact()
        val den = java.math.BigInteger.TEN.pow(scale)
        val g = num.gcd(den).takeIf { it.signum() != 0 } ?: java.math.BigInteger.ONE
        return num.divide(g) to den.divide(g)
    }

    fun short(correct: String, submitted: String): Boolean {
        val c = normalize(correct)
        val m = normalize(submitted)
        if (c == m) return true
        val cu = dropSingleVar(dropUnit(c))
        val mu = dropSingleVar(dropUnit(m))
        if (cu == mu) return true
        val cp = pairs(cu)
        val mp = pairs(mu)
        if (cp != null && mp != null) return cp == mp
        val cv = value(cu)
        return cv != null && cv == value(mu)
    }
}
