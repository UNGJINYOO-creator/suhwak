package com.suhwak.app.data

/**
 * 수식(LaTeX) 문장을 목록·리포트·알림에서 읽을 수 있는 일반 글자로 바꾼다.
 * 풀이 화면은 KaTeX로 제대로 그리고([com.suhwak.app.ui.MathView]), 그 밖의 모든 곳은 이걸 쓴다.
 *   "$\frac{7}{250}$을 소수로" -> "7/250을 소수로",  "$2^{3} \times 5$" -> "2³ × 5",  "$0.\dot{4}$" -> "0.4̇"
 */
object MathText {
    private val sup = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '-' to '⁻', '+' to '⁺', 'n' to 'ⁿ', '(' to '⁽', ')' to '⁾',
    )
    private val symbols = linkedMapOf(
        "\\times" to "×", "\\div" to "÷", "\\cdot" to "·", "\\leq" to "≤", "\\geq" to "≥", "\\le" to "≤", "\\ge" to "≥",
        "\\neq" to "≠", "\\pm" to "±", "\\square" to "□", "\\pi" to "π", "\\circ" to "°", "\\angle" to "∠",
        "\\triangle" to "△", "\\left" to "", "\\right" to "", "\\," to " ", "\\;" to " ", "\\quad" to " ",
    )
    private val frac = Regex("\\\\[dt]?frac\\{([^{}]*)\\}\\{([^{}]*)\\}")
    private val sqrt = Regex("\\\\sqrt\\{([^{}]*)\\}")
    private val dot = Regex("\\\\dot\\{([^{}]*)\\}")
    private val power = Regex("\\^\\{([^{}]*)\\}|\\^([0-9a-z])")
    private val cases = Regex("\\\\begin\\{cases\\}(.*?)\\\\end\\{cases\\}", RegexOption.DOT_MATCHES_ALL)

    fun plain(text: String): String {
        if (!text.contains('$') && !text.contains('\\')) return text
        var s = text
        s = cases.replace(s) { m -> m.groupValues[1].split("\\\\").joinToString(", ") { it.trim() } }
        repeat(3) { s = frac.replace(s) { m -> "${wrap(m.groupValues[1])}/${wrap(m.groupValues[2])}" } }
        s = sqrt.replace(s) { m -> "√" + wrap(m.groupValues[1]) }
        s = dot.replace(s) { m -> m.groupValues[1].map { "$iṫ" }.joinToString("") }
        s = power.replace(s) { m ->
            val p = m.groupValues[1].ifEmpty { m.groupValues[2] }
            if (p.all { it in sup }) p.map { sup.getValue(it) }.joinToString("") else "^($p)"
        }
        symbols.forEach { (k, v) -> s = s.replace(k, v) }
        s = s.replace("$", "").replace("{", "").replace("}", "")
        return s.replace(Regex("[ \\t]+"), " ").trim()
    }

    /** 분자·분모가 여러 항이면 괄호로 묶는다: \frac{x+1}{2} -> (x+1)/2 */
    private fun wrap(s: String): String {
        val t = s.trim()
        return if (t.any { it == '+' || it == ' ' } || (t.length > 1 && t.drop(1).contains('-'))) "($t)" else t
    }
}
