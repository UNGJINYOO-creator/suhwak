package com.suhwak.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 수식이 든 문제와 보기를 교과서처럼 보여준다 (KaTeX, 앱 안 assets/katex — 인터넷 없이도 됨).
 * 문제 문장과 보기의 $...$ 부분이 수식으로 그려지고, 분수는 위아래로 크게 보인다.
 *
 * 보기를 누르면 [onPick](1부터)으로 알려준다. 고른 보기·정답 표시는 [selected]/[answer]/[locked]가 바뀔 때
 * 페이지를 다시 읽지 않고 스크립트로만 바꾼다 (깜빡임 없이).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathView(
    question: String,
    choices: List<String>,
    selected: Int?,
    answer: Int?,
    locked: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightDp by remember { mutableFloatStateOf(0f) }
    val latestPick by rememberUpdatedState(onPick)
    // 휴대폰(짧은 쪽 600dp 미만)은 글자를 줄이고 긴 보기는 한 줄씩
    val phone = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp < 600
    val html = remember(question, choices, phone) { page(question, choices, phone) }
    val state = "S(${selected ?: 0},${answer ?: 0},${if (locked) 1 else 0})"
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun height(css: Float) { post { heightDp = css } }

                        @JavascriptInterface
                        fun pick(i: Int) { post { latestPick(i) } }
                    },
                    "Bridge",
                )
            }
        },
        update = { wv ->
            if (wv.tag != html) {
                wv.tag = html
                wv.setTag(com.suhwak.app.R.id.math_state, state)
                wv.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html.replace("/*INIT*/", state),
                    "text/html",
                    "utf-8",
                    null,
                )
            } else if (wv.getTag(com.suhwak.app.R.id.math_state) != state) {
                wv.setTag(com.suhwak.app.R.id.math_state, state)
                wv.evaluateJavascript(state, null)
            }
        },
        modifier = modifier.fillMaxWidth().height(if (heightDp > 0f) heightDp.dp else 80.dp),
    )
}

private const val MARKS = "①②③④⑤"

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** 문장 속 분수가 작게 보이지 않게 \frac -> \dfrac. */
private fun big(s: String) = s.replace("\\frac", "\\dfrac")

/** 보기 글자 수 (수식 명령·괄호·빈칸 빼고). 분수·연립이 있으면 아주 길게 친다. */
private fun choiceLen(c: String): Int =
    if ("\\begin" in c || "\\frac" in c) 99 else c.replace(Regex("""\\[a-zA-Z]+|[${'$'}{}\s]"""), "").length

/**
 * 보기를 두 칸씩 놓을지: 0 = 한 줄씩, 1 = 태블릿(넓은 화면)에서만 두 칸 (14자 이하), 2 = 휴대폰에서도 두 칸 (7자 이하).
 * 휴대폰에서 "5 < -4x + 5 < 25" 같은 보기를 두 칸에 넣으면 세 줄로 꺾여서 읽기 어렵다.
 */
private fun gridLevel(choices: List<String>): Int {
    if (choices.isEmpty()) return 0
    val longest = choices.maxOf { choiceLen(it) }
    return when {
        longest <= 7 -> 2
        longest <= 14 -> 1
        else -> 0
    }
}

private fun page(question: String, choices: List<String>, phone: Boolean): String {
    val level = gridLevel(choices)
    val grid = level > 0
    val bodyClass = if (phone) " class=\"ph\"" else ""
    val buttons = choices.mapIndexed { i, c ->
        val wide = grid && choices.size % 2 == 1 && i == choices.lastIndex
        """<button class="c${if (wide) " w" else ""}" id="c${i + 1}" onclick="if(!L)Bridge.pick(${i + 1})"><span class="mk">${MARKS.getOrElse(i) { '•' }}</span><span class="ct">${esc(big(c))}</span></button>"""
    }.joinToString("")
    return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script><script src="auto-render.min.js"></script>
<style>
html,body{margin:0;padding:0;background:transparent;color:#222B18;font-family:sans-serif;-webkit-user-select:none;user-select:none}
.q{font-size:21px;line-height:1.75;word-break:keep-all;padding:18px 18px 20px;margin:0 0 12px;background:#2F4419;color:#fff;border-radius:22px}
.q .katex{font-size:1.1em}
.cs{display:flex;flex-direction:column;gap:9px}
.cs.g{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.g .ct{white-space:nowrap}
.c{display:flex;gap:12px;align-items:center;text-align:left;background:#fff;border:3px solid #fff;border-radius:18px;padding:10px 14px;font-size:18px;min-height:56px;color:#222B18;font-family:inherit;width:100%;box-sizing:border-box}
.g .c{justify-content:center;min-height:64px;font-size:20px}
.g .c.w{grid-column:span 2}
.c .mk{font-size:17px;color:#5A6448}
.c.on{background:#DCE6C4;border-color:#4B6B2A}.c.on .mk{color:#2F4419;font-weight:700}
.c.ans{background:#FBE3D5;border-color:#D9622B}.c.ans .mk{color:#A9481B;font-weight:700}
.c.bad{background:#FDECEC;border-color:#C8372D}
/* 휴대폰(화면 폭 600dp 미만, body.ph): 글자를 줄이고, 아주 짧은 보기(g2)만 두 칸 */
.ph .q{font-size:17px;line-height:1.7;padding:14px 16px 16px;border-radius:18px}
.ph .c{font-size:16px;min-height:50px;padding:8px 12px;border-radius:16px}
.ph .c .mk{font-size:15px}
.ph .cs.g:not(.g2){display:flex;flex-direction:column;gap:8px}
.ph .cs.g:not(.g2) .c{justify-content:flex-start;min-height:50px;font-size:16px}
.ph .g2 .c{min-height:54px;font-size:17px}
</style></head><body$bodyClass>
${if (question.isBlank()) "" else "<div class=\"q\" id=\"q\">" + esc(big(question)) + "</div>"}
<div class="cs${if (level == 2) " g g2" else if (level == 1) " g" else ""}">$buttons</div>
<script>
var L=0;
function S(sel,ans,locked){L=locked;
  for(var i=1;i<=${choices.size};i++){var b=document.getElementById('c'+i);if(!b)continue;
    b.className='c'+(i==ans?' ans':(i==sel?(locked&&ans?' bad':' on'):''));}
  report();}
function report(){Bridge.height(document.body.scrollHeight);}
document.addEventListener('DOMContentLoaded',function(){
  try{renderMathInElement(document.body,{delimiters:[{left:'$$',right:'$$',display:true},{left:'$',right:'$',display:false},{left:'\\(',right:'\\)',display:false}],throwOnError:false});}catch(e){}
  /*INIT*/;
  setTimeout(report,50);setTimeout(report,400);
});
window.addEventListener('resize',report);
</script></body></html>"""
}
