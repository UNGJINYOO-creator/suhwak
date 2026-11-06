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
    val html = remember(question, choices) { page(question, choices) }
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

/** 보기가 모두 짧으면(수식 기호를 빼고 14자 이하, 분수·연립 없음) 두 줄로 크게 놓는다. */
private fun shortChoices(choices: List<String>): Boolean = choices.isNotEmpty() && choices.all { c ->
    "\\begin" !in c && "\\frac" !in c && c.replace(Regex("""\\[a-zA-Z]+|[${'$'}{}\s]"""), "").length <= 14
}

private fun page(question: String, choices: List<String>): String {
    val grid = shortChoices(choices)
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
.c{display:flex;gap:12px;align-items:center;text-align:left;background:#fff;border:3px solid #fff;border-radius:18px;padding:10px 14px;font-size:18px;min-height:56px;color:#222B18;font-family:inherit;width:100%;box-sizing:border-box}
.g .c{justify-content:center;min-height:64px;font-size:20px}
.g .c.w{grid-column:span 2}
.c .mk{font-size:17px;color:#5A6448}
.c.on{background:#DCE6C4;border-color:#4B6B2A}.c.on .mk{color:#2F4419;font-weight:700}
.c.ans{background:#FBE3D5;border-color:#D9622B}.c.ans .mk{color:#A9481B;font-weight:700}
.c.bad{background:#FDECEC;border-color:#C8372D}
</style></head><body>
${if (question.isBlank()) "" else "<div class=\"q\" id=\"q\">" + esc(big(question)) + "</div>"}
<div class="cs${if (grid) " g" else ""}">$buttons</div>
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
