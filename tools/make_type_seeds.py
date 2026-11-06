"""유형 씨앗(중2-1 수학) 만들기: app/src/main/assets/seeds_types.json

중2-1 개념 문제집의 '필수 유형' 순서(7단원)를 따라, 유형마다 숫자를 무작위로 바꿔 가며 문제를 많이 만든다.
책의 문제나 글을 옮기지 않는다: 유형(무엇을 묻는가)만 따르고 수·식·상황은 모두 새로 정한다.
정답은 파이썬(분수 계산·sympy)이 계산하고, 객관식은 보기마다 참/거짓을 다시 확인한다 (AI가 정답을 쓰지 않음).
형식은 마클리 문제 은행(/api/bank/export)과 같아서 앱의 SeedImport가 그대로 읽는다.

    pip install sympy
    python3 tools/make_type_seeds.py

구조: 유형 하나 = @kind(단원, 유형 이름, 개수)를 붙인 함수 하나. 함수는 부를 때마다 숫자를 새로 뽑아 문제 하나를 만든다.
조건이 안 맞는 숫자가 뽑히면 Retry를 던지고 다시 뽑는다. 같은 문제(문장+보기)는 한 번만 넣는다.
단원 안에서는 '유형마다 2문제씩 한 바퀴'를 여러 번 도는 순서로 담아서, 앞에서부터 풀어도 모든 유형을 골고루 만난다.
"""
from __future__ import annotations

import json
import math
import random
from fractions import Fraction as F
from pathlib import Path

import sympy as sp

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/seeds_types.json"
TITLE = "유형 씨앗 2-1"
ID_PREFIX = "t21"
MARKS = "①②③④⑤"
R = random.Random(20261104)
x, y, a, b = sp.symbols("x y a b")
SUP = str.maketrans("0123456789-", "⁰¹²³⁴⁵⁶⁷⁸⁹⁻")


class Retry(Exception):
    """이번에 뽑은 숫자로는 문제가 안 됨 -> 다시 뽑기."""


def need(cond):
    if not cond:
        raise Retry


TYPES: list[tuple[str, str, int, object]] = []


def kind(unit, concept, count=12):
    def deco(fn):
        TYPES.append((unit, concept, count, fn))
        return fn
    return deco


DETAIL = "##자세히"  # 이 줄 뒤는 '자세한 풀이'에만 나오고 3차 빈칸 풀이에는 안 나온다 (정답이 미리 보이지 않게)


def P(level, question, answer, *, choices=None, hint="", explanation="", steps=None, detail=None):
    """steps: 3차 빈칸 풀이 ([[값]]은 빈칸). 마지막 빈칸 줄 뒤의 줄(정리·답)과 detail은 자세한 풀이에만."""
    steps = list(steps or [])
    last = max((i for i, s_ in enumerate(steps) if "[[" in s_), default=-1)
    tail = steps[last + 1:] if last >= 0 else []
    if last >= 0:
        steps = steps[: last + 1]
    extra = tail + list(detail or [])
    if extra:
        steps = steps + [DETAIL] + extra
    return {"level": level, "question": question, "choices": choices or [], "answer": str(answer),
            "hint": hint, "explanation": explanation, "steps": steps}


def plain_tex(t: str) -> str:
    """수식(LaTeX)을 풀이 줄에 쓸 일반 글자로: \\frac{3}{4} -> 3/4, x^{2} -> x², \\dot{3} -> 3̇"""
    import re
    if "$" not in t and "\\" not in t:
        return t
    s_ = t.replace("$", "")
    s_ = re.sub(r"\\begin\{cases\}(.*?)\\end\{cases\}", lambda m: ", ".join(x_.strip() for x_ in m.group(1).split("\\\\")), s_)
    for k_, v_ in [("\\left", ""), ("\\right", ""), ("\\{", "("), ("\\}", ")"), ("\\cdots", "⋯"), ("\\times", "×"), ("\\div", "÷"),
                   ("\\geq", "≥"), ("\\leq", "≤"), ("\\square", "□"), ("\\overline", ""), ("\\dfrac", "\\frac")]:
        s_ = s_.replace(k_, v_)
    def sup_(m):
        e_ = m.group(1) or m.group(2)
        return e_.translate(SUP) if all(c_ in "0123456789-" for c_ in e_) else f"^({e_})"
    s_ = re.sub(r"\^\{([^{}]*)\}|\^(\d)", sup_, s_)
    s_ = re.sub(r"\\dot\{(\d)\}", lambda m: m.group(1) + "\u0307", s_)
    for _ in range(3):
        s_ = re.sub(r"\\frac\{([^{}]*)\}\{([^{}]*)\}", lambda m: (f"({m.group(1)})" if " " in m.group(1).strip() else m.group(1)) + "/" + (f"({m.group(2)})" if " " in m.group(2).strip() else m.group(2)), s_)
    return s_.replace("{", "").replace("}", "").strip()


def judge_lines(ch: list[str], judge, want=True) -> list[str]:
    """보기마다 한 줄: '① 3/8 → ⭕ 이유'. judge(보기) -> (조건을 만족하는지, 이유)."""
    out = []
    for i, c_ in enumerate(ch):
        ok, why = judge(c_)
        out.append(f"{MARKS[i]} {plain_tex(c_)} → {'⭕' if ok else '❌'} {why}")
    return out


def mc(correct: str, wrong: list[str]) -> tuple[list[str], str]:
    """보기 5개(정답 1 + 오답 4)를 섞고 정답 원문자를 돌려준다."""
    wrong = [w for w in dict.fromkeys(wrong) if w != correct]
    need(len(wrong) >= 4)
    opts = [correct] + R.sample(wrong, 4)
    R.shuffle(opts)
    return opts, MARKS[opts.index(correct)]


def pick(truth: list[tuple[str, bool]], want: bool) -> tuple[list[str], str]:
    """'옳은 것은?'(want=True) / '옳지 않은 것은?'(want=False): 원하는 쪽 하나 + 반대쪽 넷."""
    hit = list(dict.fromkeys(s for s, t_ in truth if t_ == want))
    miss = list(dict.fromkeys(s for s, t_ in truth if t_ != want))
    need(hit and len(miss) >= 4)
    return mc(R.choice(hit), miss)


def nz(lo, hi, skip=(0,)):
    return R.choice([k for k in range(lo, hi + 1) if k not in skip])


def sgn(v):
    """+ 3 / - 3 꼴 (앞 항 뒤에 붙일 때)."""
    return f"+ {v}" if v >= 0 else f"- {-v}"


def par(v):
    """음수면 괄호: 3 -> 3, -3 -> (-3)."""
    return f"({v})" if (isinstance(v, str) and v.startswith("-")) or (not isinstance(v, str) and v < 0) else f"{v}"


def has_batchim(ch: str) -> bool:
    if "가" <= ch <= "힣":
        return (ord(ch) - 0xAC00) % 28 != 0
    return ch in "013678"  # 영 일 삼 육 칠 팔


PAIRS = {"을": ("을", "를"), "를": ("을", "를"), "이": ("이", "가"), "가": ("이", "가"), "은": ("은", "는"), "는": ("은", "는"), "과": ("과", "와"), "와": ("과", "와")}


def josa(word: str, p_: str) -> str:
    """word + 알맞은 조사: josa('사과', '과') -> '사과와'"""
    a1, a2 = PAIRS[p_]
    return word + (a1 if has_batchim(word[-1]) else a2)


def tidy(text: str) -> str:
    """계수 1과 부호 겹침 정리: 1x -> x, -1x -> -x, -1( -> -(, + - -> -, - - -> +"""
    import re
    text = re.sub(r"(?<![\d./^{])1([a-z(])", r"\1", text)
    text = text.replace("+ -", "- ").replace("- -", "+ ").replace("-  ", "- ").replace("+  ", "+ ")
    return text


def fix_josa(text: str) -> str:
    """숫자 바로 뒤 조사(3를 -> 3을, 4이 -> 4가)와 수식 뒤 조사($(2, 6)$를 -> $(2, 6)$을)를 맞춘다."""
    import re
    def sub(m):
        d, p_ = m.group(1), m.group(3)
        a1, a2 = PAIRS[p_]
        return m.group(1) + m.group(2) + (a1 if has_batchim(d) else a2)
    text = re.sub(r"(\d)(\)?\$?|\$)(을|를|이|가|은|는|과|와)(?=[\s,.?])", sub, text)
    # 로/으로: 받침이 ㄹ이거나 없으면 '로', 그 밖의 받침이면 '으로' (분수 a/b는 분자로 읽으니 분자 끝자리로)
    def ro(m):
        d = m.group(1)
        return m.group(0)[: -len(m.group(2))] + ("으로" if d in "036" else "로")
    text = re.sub(r"(\d)(?:/\d+)?(로|으로)(?=[\s,.?:])", ro, text)
    # 이(가) 을(를) 와(과) 은(는): 숫자 뒤에서는 하나로 정한다
    both = {"이(가)": ("이", "가"), "을(를)": ("을", "를"), "와(과)": ("과", "와"), "은(는)": ("은", "는")}
    def one(m):
        a1, a2 = both[m.group(2)]
        return m.group(1) + (a1 if has_batchim(m.group(1)[-1]) else a2)
    text = re.sub(r"(\d)(이\(가\)|을\(를\)|와\(과\)|은\(는\))", one, text)
    # 이에요/예요, 이라/라: 숫자 뒤 (분수는 분자 끝자리)
    def cop(m):
        b_ = has_batchim(m.group(1))
        w_ = m.group(2)
        new = ("이에요" if b_ else "예요") if w_ in ("이에요", "예요") else ("이라" if b_ else "라")
        return m.group(0)[: -len(w_)] + new
    text = re.sub(r"(\d)(?:/\d+)?(이에요|예요|이라|라)(?=[\s,.)]|$)", cop, text)
    return text


# ───────── 식 쓰기 (교과서 꼴: 3x²y, -\frac{1}{2}a, x² + 2x - 1) ─────────

def term(c, mono, tex=True):
    letters = "".join((f"{v}^{{{e}}}" if tex else f"{v}{str(e).translate(SUP)}") if e != 1 else v for v, e in mono if e)
    c = F(c)
    m = abs(c)
    if m.denominator != 1:
        coef = f"\\frac{{{m.numerator}}}{{{m.denominator}}}" if tex else (f"({m.numerator}/{m.denominator})" if letters else f"{m.numerator}/{m.denominator}")
        body = coef + letters
    else:
        body = ("" if m == 1 and letters else str(m.numerator)) + letters
    return ("-" if c < 0 else "+"), body


def rat(c) -> F:
    n, d = sp.fraction(sp.nsimplify(c))
    return F(int(n), int(d))


def fmt(expr, gens, tex=True):
    """sympy 다항식 -> 교과서 꼴 문자열 (높은 차수부터)."""
    expr = sp.expand(sp.sympify(expr))
    if expr == 0:
        return "0"
    poly = sp.Poly(expr, *gens)
    out = ""
    for monom, coef in poly.terms(order="grlex"):
        sign, body = term(rat(coef), list(zip([str(g) for g in gens], monom)), tex)
        out = (("-" if sign == "-" else "") + body) if not out else out + f" {sign} {body}"
    return out


def mono(expr, gens, tex=True):
    """단항식(분모에 문자가 올 수도 있음) -> 문자열. 예: 3x^2/y -> \\frac{3x^{2}}{y}"""
    expr = sp.simplify(expr)
    num, den = sp.fraction(sp.together(expr))
    if den.free_symbols:
        s_ = -1 if sp.Poly(num, *gens).LC() < 0 else 1
        n_, d_ = fmt(s_ * num, gens, tex), fmt(den, gens, tex)
        return ("-" if s_ < 0 else "") + (f"\\frac{{{n_}}}{{{d_}}}" if tex else f"{n_}/{d_}")
    return fmt(expr, gens, tex)


def T(e, g=(x, y)):
    return fmt(e, g)


def Pl(e, g=(x, y)):
    return fmt(e, g, tex=False)


def paren_t(e, g=(x, y)):
    s = T(e, g)
    return f"({s})" if s.startswith("-") or len(sp.Add.make_args(sp.expand(e))) > 1 else s


def ftex(f) -> str:
    f = F(f)
    if f.denominator == 1:
        return str(f.numerator)
    return ("-" if f < 0 else "") + f"\\frac{{{abs(f.numerator)}}}{{{f.denominator}}}"


def fpl(f) -> str:
    f = F(f)
    return str(f.numerator) if f.denominator == 1 else f"{f.numerator}/{f.denominator}"


def Q(f) -> sp.Rational:
    f = F(f)
    return sp.Rational(f.numerator, f.denominator)


def lin(cx, cy=0, c=0, vx="x", vy="y", tex=True):
    g = sp.symbols(f"{vx} {vy}")
    return fmt(Q(cx) * g[0] + Q(cy) * g[1] + Q(c), g, tex)


def fx(m, k, v="x", tex=True):
    s = sp.Symbol(v)
    return fmt(Q(m) * s + Q(k), (s,), tex)


def factor_str(n: int) -> str:
    """소인수분해 글자: 120 -> 2³×3×5"""
    out = []
    for q_, e in sorted(sp.factorint(n).items()):
        out.append(f"{q_}{str(e).translate(SUP) if e > 1 else ''}")
    return "×".join(out) or "1"


def poly_wrongs(e, g, k=8):
    """다항식 정답에서 흔한 실수 꼴을 만든다: 한 항 부호 바꾸기, 한 계수 ±1·×2, 전체 부호."""
    e = sp.expand(e)
    terms = sp.Add.make_args(e)
    out = [sp.expand(-e)]
    for t_ in terms:
        out += [sp.expand(e - 2 * t_), sp.expand(e + t_), sp.expand(e - t_ + t_ / 2) if t_.is_Number is False else sp.expand(e + 1)]
        c_ = t_.as_coeff_Mul()[1]
        out += [sp.expand(e + c_), sp.expand(e - c_)]
    out = [w for w in dict.fromkeys(out) if sp.expand(w - e) != 0 and w != 0]
    R.shuffle(out)
    return out


def mono_wrongs(e, g):
    """단항식 정답에서 흔한 실수 꼴: 부호, 계수, 지수 하나 ±1."""
    e = sp.simplify(e)
    out = [-e, 2 * e, e / 2 if e.as_coeff_Mul()[0] % 2 == 0 else 3 * e]
    for v in g:
        if e.has(v):
            out += [e * v, e / v]
    out = [sp.simplify(w) for w in out]
    out = [w for w in dict.fromkeys(out) if sp.simplify(w - e) != 0]
    R.shuffle(out)
    return out


def poly_mc(right, g, extra=()):
    return mc(f"${fmt(right, g)}$", [f"${fmt(w, g)}$" for w in list(extra) + poly_wrongs(right, g) if sp.expand(w - right) != 0])


def mono_mc(right, g, extra=()):
    return mc(f"${mono(right, g)}$", [f"${mono(w, g)}$" for w in list(extra) + mono_wrongs(right, g) if sp.simplify(w - right) != 0])


# ───────── 자세한 풀이 (3차에서 빈칸을 못 채울 때 보여주는 풀이 줄) ─────────

def plus_join(parts: list[str]) -> str:
    """['3x', '-2y', '5'] -> '3x - 2y + 5'"""
    out = ""
    for p_ in parts:
        if not out:
            out = p_
        elif p_.startswith("-"):
            out += " - " + p_[1:]
        else:
            out += " + " + p_
    return out or "0"


def combine_steps(parts, g, head="괄호 풀기"):
    """다항식 계산 풀이: 괄호를 푼 모든 항을 늘어놓고, 같은 문자끼리(동류항) 계수를 모은다."""
    terms = []
    for p_ in parts:
        terms += sorted(sp.Add.make_args(sp.expand(p_)), key=lambda t_: [-d for d in sp.Poly(t_, *g).monoms()[0]] if t_.free_symbols else [1])
    lines = [f"{head}: " + plus_join([fmt(t_, g, False) for t_ in terms if t_ != 0])]
    groups: dict = {}
    for t_ in terms:
        if t_ == 0:
            continue
        mon = sp.Poly(t_, *g).monoms()[0]
        groups.setdefault(mon, []).append(rat(sp.Poly(t_, *g).coeffs()[0]))
    order_ = sorted(groups, key=lambda m_: (-sum(m_), [-d for d in m_]))
    for mon in order_:
        cs = groups[mon]
        name = fmt(sp.prod([v ** e for v, e in zip(g, mon)]), g, False) if any(mon) else "상수항"
        total = sum(cs, F(0))
        if len(cs) == 1:
            continue
        joined = " + ".join(f"({fpl(c_)})" if c_ < 0 else fpl(c_) for c_ in cs)
        lines.append(f"{name}{'끼리' if any(mon) else ''}: {joined} = [[{fpl(total)}]]" + ("" if any(mon) else ""))
    result = sp.expand(sum(parts))
    lines.append(f"정리하면: {fmt(result, g, False)}")
    return lines


def mono_steps(factors, g):
    """단항식 곱셈·나눗셈 풀이. factors: [(단항식, 지수, '×' 또는 '÷'), ...] -> (풀이 줄, 결과)"""
    lines, powered = [], []
    for f_, k, op in factors:
        fk = sp.expand(f_ ** k)
        if k > 1:
            lines.append(f"거듭제곱 먼저: ({mono(f_, g, False)}){str(k).translate(SUP)} = {mono(fk, g, False)}")
        powered.append((fk, op))
    if any(op == "÷" for _, op in powered):
        lines.append("나눗셈은 역수의 곱셈으로 바꿔요: ÷ A → × 1/A")
    coef = F(1)
    cparts = []
    for fk, op in powered:
        c_ = rat(fk.as_coeff_Mul()[0])
        coef = coef * c_ if op == "×" else coef / c_
        cparts.append(("" if not cparts else f" {op} ") + (f"({fpl(c_)})" if c_ < 0 else fpl(c_)))
    lines.append(f"계수끼리: {''.join(cparts)} = [[{fpl(coef)}]]")
    result = Q(coef)
    for v in g:
        es = [(int(sp.powsimp(fk).as_powers_dict().get(v, 0)), op) for fk, op in powered]
        if not any(e for e, _ in es):
            continue
        tot = sum(e if op == "×" else -e for e, op in es)
        nzs = [(e, op) for e, op in es if e]
        joined = "".join((("-" if op == "÷" else "") if i == 0 else (" + " if op == "×" else " - ")) + (f"({e})" if e < 0 else str(e)) for i, (e, op) in enumerate(nzs))
        lines.append(f"{v}의 지수: {joined} = [[{tot}]]" + (f" (지수가 음수면 분모로: 1/{v}{str(-tot).translate(SUP) if tot < -1 else ''})" if tot < 0 else ""))
        result *= v ** tot
    lines.append(f"정리하면: {mono(result, g, False)}")
    return lines, result


def term_lines(poly, op, factor, g):
    """(다항식) × 또는 ÷ (단항식): 항마다 한 줄, 계수는 빈칸. 예: '6x²y ÷ (2/5xy) = [[15]]x'"""
    out = []
    terms = sorted(sp.Add.make_args(sp.expand(poly)), key=lambda t_: (-sp.Poly(t_, *g).total_degree(), [-d for d in sp.Poly(t_, *g).monoms()[0]]))
    for t_ in terms:
        r_ = sp.expand(t_ * factor if op == "×" else t_ / factor)
        c_, m_ = r_.as_coeff_Mul()
        c_ = rat(c_)
        if m_ == 1:
            body = f"[[{fpl(c_)}]]"
        else:
            body = f"[[{fpl(c_)}]]{fmt(m_, g, False)}"
        out.append(f"{fmt(t_, g, False)} {op} ({mono(factor, g, False)}) = {body}")
    return out


def subst_line(expr, vals: dict, g) -> str:
    """식에 수를 넣는 줄: 3x² - 5x (x = -3) -> '3×(-3)² - 5×(-3) = 27 + 15 = 42'"""
    terms = sorted(sp.Add.make_args(sp.expand(expr)), key=lambda t_: (-sp.Poly(t_, *g).total_degree(), [-d for d in sp.Poly(t_, *g).monoms()[0]]))
    shown, nums = [], []
    for t_ in terms:
        c_, m_ = t_.as_coeff_Mul()
        c_ = rat(c_)
        parts = []
        for v_ in g:
            e_ = int(m_.as_powers_dict().get(v_, 0)) if m_ != 1 else 0
            if e_:
                vv = vals[v_]
                parts.append((f"({vv})" if vv < 0 else str(vv)) + (str(e_).translate(SUP) if e_ > 1 else ""))
        body = "×".join(([fpl(abs(c_))] if abs(c_) != 1 or not parts else []) + parts)
        shown.append(("-" if c_ < 0 else "") + body)
        nums.append(t_.subs(vals))
    total = sum(nums)
    return f"{plus_join(shown)} = {plus_join([str(n_) for n_ in nums])} = [[{total}]]"


def mul(c_, v_):
    """계수 × 값: 1×(5) → 5, -1×(5) → -5, 3×(-2) → 3×(-2)"""
    if c_ == 1:
        return f"{v_}" if v_ >= 0 else f"({v_})"
    if c_ == -1:
        return f"-{v_}" if v_ >= 0 else f"-({v_})"
    return f"{c_}×{par(v_)}"


def elim_steps(a1, b1, c1, a2, b2, c2, sx, sy, pre=()):
    """연립방정식 가감법 풀이 (y를 없앤 뒤 x, 다시 대입해서 y)."""
    lines = list(pre)
    lines.append(f"① {lin(a1, b1, tex=False)} = {c1}")
    lines.append(f"② {lin(a2, b2, tex=False)} = {c2}")
    if b1 == 0 or b2 == 0:
        i, (a_, c_) = (1, (a1, c1)) if b1 == 0 else (2, (a2, c2))
        lines.append(f"{'①' if i == 1 else '②'}에서 {lin(a_, 0, tex=False)} = {c_} → x = [[{sx}]]")
    else:
        l_ = abs(b1) * abs(b2) // math.gcd(abs(b1), abs(b2))
        k1, k2 = l_ // abs(b1), l_ // abs(b2)
        e1, e2 = (a1 * k1, b1 * k1, c1 * k1), (a2 * k2, b2 * k2, c2 * k2)
        t1, t2 = ("①" if k1 == 1 else f"①×{k1}"), ("②" if k2 == 1 else f"②×{k2}")
        if k1 != 1 or k2 != 1:
            lines.append(f"y의 계수를 맞추기: {t1} → {lin(e1[0], e1[1], tex=False)} = {e1[2]}, {t2} → {lin(e2[0], e2[1], tex=False)} = {e2[2]}")
        same = e1[1] == e2[1]
        Da, Dc = (e1[0] - e2[0], e1[2] - e2[2]) if same else (e1[0] + e2[0], e1[2] + e2[2])
        assert Da * sx == Dc, (Da, Dc, sx)
        if Da == 1:
            lines.append(f"{t1} {'-' if same else '+'} {t2}: y가 없어져서 x = [[{sx}]]")
        else:
            lines.append(f"{t1} {'-' if same else '+'} {t2}: y가 없어져서 {lin(Da, 0, tex=False)} = {Dc}")
            lines.append(f"양변을 {Da}로 나누면 x = [[{sx}]]")
    if b1 != 0:
        rest = c1 - a1 * sx
        lines.append(f"x = {sx}를 ①에 넣으면: {mul(a1, sx)} {sgn(b1)}y = {c1} → {lin(b1, 0, 0, 'y', 'x', tex=False)} = {rest}")
    else:
        rest = c2 - a2 * sx
        lines.append(f"x = {sx}를 ②에 넣으면: {mul(a2, sx)} {sgn(b2)}y = {c2} → {lin(b2, 0, 0, 'y', 'x', tex=False)} = {rest}")
    lines.append(f"y = [[{sy}]]")
    return lines


def ineq_steps(L, Rr, rel, pre=(), scale=1, ask=None, val=None):
    """일차부등식 풀이: (배수를 곱하고) 이항 → 정리 → 나누기(음수면 방향 바뀜) → 해 (→ 정수)."""
    X = sp.Symbol("x")
    lines = list(pre)
    Ls, Rs = sp.expand(L * scale), sp.expand(Rr * scale)
    if scale != 1:
        lines.append(f"양변에 {scale}을 곱하면: {fmt(Ls, (X,), False)} {PL[rel]} {fmt(Rs, (X,), False)}")
    d_ = sp.expand(Ls - Rs)
    c_, k_ = rat(d_.coeff(X)), rat(d_.subs(X, 0))
    lc, lk, rc, rk = rat(Ls.coeff(X)), rat(Ls.subs(X, 0)), rat(Rs.coeff(X)), rat(Rs.subs(X, 0))
    if rc or lk:
        lft = fmt(Q(lc) * X, (X,), False) + (f" - ({fmt(Q(rc) * X, (X,), False)})" if rc else "")
        rgt = fpl(rk) + (f" - ({fpl(lk)})" if lk else "")
        lines.append(f"x항은 왼쪽, 수는 오른쪽으로 (옮기면 부호가 바뀌어요): {lft} {PL[rel]} {rgt}")
        lines.append(f"정리하면: {fmt(Q(c_) * X, (X,), False)} {PL[rel]} {fpl(-k_)}")
    elif scale == 1:
        lines.append(f"{fmt(Q(c_) * X, (X,), False)} {PL[rel]} {fpl(-k_)}")
    res = rel if c_ > 0 else FLIP[rel]
    bound = -k_ / c_
    if c_ != 1:
        lines.append(f"양변을 {fpl(c_)}로 나누면" + (" (음수로 나누니 부등호 방향이 바뀌어요)" if c_ < 0 else "") + f": x {PL[res]} [[{fpl(bound)}]]")
    if ask:
        lines.append(f"{'가장 큰' if ask == 'max' else '가장 작은'} 정수: [[{val}]]")
    return lines, res, bound


# ───────── 소수 ─────────

def only25(n: int) -> bool:
    return rest25(n) == 1


def rest25(n: int) -> int:
    for q_ in (2, 5):
        while n % q_ == 0:
            n //= q_
    return n


def expand(fr: F):
    """양의 분수 -> (정수 부분, 순환하지 않는 부분, 순환마디)."""
    ip, r_, den = fr.numerator // fr.denominator, fr.numerator % fr.denominator, fr.denominator
    digits, seen = [], {}
    while r_ and r_ not in seen:
        seen[r_] = len(digits)
        r_ *= 10
        digits.append(str(r_ // den))
        r_ %= den
    if not r_:
        return ip, "".join(digits), ""
    s = seen[r_]
    return ip, "".join(digits[:s]), "".join(digits[s:])


def dval(ip: int, pre: str, rep: str) -> F:
    if not rep:
        return ip + (F(int(pre), 10 ** len(pre)) if pre else 0)
    return ip + F(int(pre + rep) - (int(pre) if pre else 0), (10 ** len(rep) - 1) * 10 ** len(pre))


def dtex(ip: int, pre: str, rep: str) -> str:
    if not rep:
        return f"{ip}.{pre}"
    body = f"\\dot{{{rep}}}" if len(rep) == 1 else f"\\dot{{{rep[0]}}}{rep[1:-1]}\\dot{{{rep[-1]}}}"
    return f"{ip}.{pre}{body}"


def dplain(ip: int, pre: str, rep: str, n=10) -> str:
    s = pre
    while len(s) < n:
        s += rep
    return f"{ip}.{s[:n]}…"


def digit_at(fr: F, n: int) -> str:
    ip, pre, rep = expand(fr)
    if n <= len(pre):
        return pre[n - 1]
    return rep[(n - len(pre) - 1) % len(rep)]


def rand_repeating(max_rep=4, max_pre=2, max_ip=3):
    """무작위 순환소수 (정규형). (값, ip, pre, rep)"""
    ip = R.randint(0, max_ip)
    pre = "".join(R.choice("0123456789") for _ in range(R.randint(0, max_pre)))
    rep = "".join(R.choice("0123456789") for _ in range(R.randint(1, max_rep)))
    need(set(rep) != {"0"} and set(rep) != {"9"})
    v = dval(ip, pre, rep)
    ip2, pre2, rep2 = expand(v)
    need(rep2 and len(rep2) <= max_rep)
    return v, ip2, pre2, rep2


def wrong_dots(ip, pre, rep, fr):
    """같은 숫자를 늘어놓고 점만 잘못 찍은 꼴들 (값이 다른 것만)."""
    digits = pre + rep * 3
    out = []
    for pl in range(0, len(pre) + 2):
        for rl in range(1, len(rep) + 3):
            if pl + rl <= len(digits):
                cand = (digits[:pl], digits[pl:pl + rl])
                if cand[1] and dval(ip, *cand) != fr:
                    out.append(cand)
    return out


# ═════════════════════ 1. 유리수와 순환소수 ═════════════════════
U = "1. 유리수와 순환소수"
DEN = [6, 8, 9, 12, 14, 15, 16, 18, 20, 21, 24, 25, 28, 30, 35, 36, 40, 42, 45, 48, 50, 56, 60, 63, 66, 70, 72, 75, 80,
       84, 88, 90, 96, 105, 110, 120, 125, 140, 150, 160, 200, 250]


def frac_of(c: str) -> F:
    import re
    m_ = re.search(r"\\frac\{(\d+)\}\{(\d+)\}", c)
    return F(int(m_.group(1)), int(m_.group(2)))


def finite_why(n: int, d: int) -> tuple[bool, str]:
    f = F(n, d)
    red = f"{n}/{d} = {fpl(f)}, " if (f.numerator, f.denominator) != (n, d) else ""
    others = [str(q_) for q_ in sp.factorint(f.denominator) if q_ not in (2, 5)]
    if not others:
        return True, f"{red}분모 {f.denominator} = {factor_str(f.denominator)} → 2와 5뿐 → 유한소수"
    return False, f"{red}분모 {f.denominator} = {factor_str(f.denominator)} → {', '.join(others)}이(가) 있어 무한소수"


def remainders(n: int, d: int, k: int = 8) -> list[int]:
    r_, out = n % d, []
    for _ in range(k):
        out.append(r_)
        r_ = r_ * 10 % d
        if r_ == 0:
            break
    return out


@kind(U, "유한소수와 무한소수 구분", 12)
def _():
    want = R.random() < 0.5
    hit, miss = [], []
    for _ in range(200):
        d = R.choice(DEN)
        n = R.randint(1, 2 * d)
        if n % d == 0:
            continue
        f = F(n, d)
        (hit if only25(f.denominator) == want else miss).append((n, d))
        if hit and len(miss) >= 4:
            break
    need(hit and len(miss) >= 4)
    n, d = hit[0]
    f = F(n, d)
    ch, ans = mc(f"$\\frac{{{n}}}{{{d}}}$", [f"$\\frac{{{p_}}}{{{q_}}}$" for p_, q_ in miss])
    def judge(c_):
        import re
        m_ = re.search(r"\\frac\{(\d+)\}\{(\d+)\}", c_)
        fin, why = finite_why(int(m_.group(1)), int(m_.group(2)))
        return fin == want, why
    return P("쉬움", f"다음 분수를 소수로 나타낼 때, {'유한소수' if want else '무한소수'}인 것은?", ans, choices=ch,
             hint="기약분수로 만든 뒤 분모의 소인수가 2와 5뿐이면 유한소수, 다른 소인수가 있으면 무한소수(순환소수)예요.",
             explanation=finite_why(n, d)[1],
             steps=["먼저 분자와 분모를 약분해서 기약분수로 만들어요",
                    f"정답 보기 {n}/{d}를 기약분수로: [[{fpl(f)}]]", f"분모 {f.denominator}를 소인수분해: {factor_str(f.denominator)}",
                    "분모의 소인수가 2와 5뿐이면 [[유한소수]], 다른 소인수가 있으면 무한소수"],
             detail=[f"보기를 하나씩 확인해요 (⭕ = {'유한소수' if want else '무한소수'}):"] + judge_lines(ch, judge) + [f"그래서 답은 {ans}"])


@kind(U, "순환소수 표현하기", 12)
def _():
    want = R.random() < 0.5
    truth, info = [], {}
    for _ in range(60):
        v, ip, pre, rep = rand_repeating(3, 2, 4)
        right = R.random() < (0.3 if want else 0.7)
        if right:
            s = dtex(ip, pre, rep)
        else:
            w = wrong_dots(ip, pre, rep, v)
            if not w:
                continue
            s = dtex(ip, *R.choice(w))
        long_ = dplain(ip, pre, rep, 9).replace("…", r"\cdots")
        item = f"${long_} = {s}$"
        truth.append((item, right))
        info[item] = (ip, pre, rep)
        if sum(t_ == want for _, t_ in truth) >= 1 and sum(t_ != want for _, t_ in truth) >= 4:
            break
    ch, ans = pick(truth, want)
    def judge(c_):
        ip, pre, rep = info[c_]
        ok = dict(truth)[c_]
        good = plain_tex(f"${dtex(ip, pre, rep)}$")
        return ok, f"순환마디 {rep}" + (" → 바르게 썼어요" if ok else f" → 바른 표현은 {good}")
    tgt = info[ch[MARKS.index(ans)]]
    return P("보통", f"다음 중 순환소수의 표현이 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="소수점 아래에서 처음 되풀이되는 숫자 묶음(순환마디)을 찾아, 그 처음과 끝 숫자 위에만 점을 찍어요.",
             explanation="각 보기의 순환마디를 찾아, 점이 순환마디의 처음과 끝에 찍혔는지 확인해요.",
             steps=["소수점 아래에서 처음으로 되풀이되는 숫자 묶음을 찾아요 (순환마디)",
                    f"정답 보기 {dplain(*tgt, 9)}의 순환마디: [[{tgt[2]}]]",
                    "점은 순환마디의 처음 숫자와 끝 숫자 위에만 찍어요 (한 자리면 그 숫자 하나)"],
             detail=["보기를 하나씩 확인해요 (⭕ = 옳은 표현):"] + judge_lines(ch, judge) + [f"그래서 {'옳은' if want else '옳지 않은'} 것은 {ans}"])


@kind(U, "분수를 순환소수로", 12)
def _():
    d = R.choice([3, 6, 7, 9, 11, 12, 13, 15, 18, 22, 27, 30, 33, 37, 44, 45, 55, 66, 74, 90, 99, 111, 135, 148])
    n = R.randint(1, 3 * d)
    f = F(n, d)
    need(f.denominator == d and n % d)
    ip, pre, rep = expand(f)
    need(rep and len(rep) <= 6)
    ch, ans = mc(f"${dtex(ip, pre, rep)}$", [f"${dtex(ip, *w)}$" for w in wrong_dots(ip, pre, rep, f)])
    rs = remainders(n, d, len(pre) + len(rep) + 1)
    return P("보통", f"분수 $\\frac{{{n}}}{{{d}}}$을 순환소수로 바르게 나타낸 것은?", ans, choices=ch,
             hint="직접 나눗셈을 해서 같은 나머지가 다시 나오는 곳을 찾아요.",
             explanation=f"{n} ÷ {d} = {dplain(ip, pre, rep, 10)} 이므로 순환마디는 {rep}예요.",
             steps=[f"{n} ÷ {d}를 직접 나눠요: 몫은 {dplain(ip, pre, rep, 10)}",
                    f"나머지를 차례로 쓰면 {', '.join(map(str, rs))} → 나머지 {rs[-1]}이(가) 다시 나와서 그 뒤로 똑같이 되풀이돼요",
                    (f"순환하지 않는 부분: 소수점 아래 {pre}" if pre else "소수점 바로 다음 자리부터 되풀이돼요"),
                    f"되풀이되는 묶음(순환마디): [[{rep}]]",
                    f"점은 순환마디의 처음 숫자 {rep[0]}" + (f"와(과) 끝 숫자 {rep[-1]} 위에" if len(rep) > 1 else " 위에만"),
                    f"그래서 {plain_tex('$' + dtex(ip, pre, rep) + '$')} → 답 {ans}"])


@kind(U, "소수점 아래 n번째 자리의 숫자", 14)
def _():
    if R.random() < 0.5:
        d = R.choice([7, 11, 13, 27, 37, 41, 33, 22, 74, 101])
        n = R.randint(1, d - 1)
        f = F(n, d)
        need(f.denominator == d)
        ip, pre, rep = expand(f)
        q = f"분수 $\\frac{{{n}}}{{{d}}}$을 소수로 나타낼 때"
    else:
        f, ip, pre, rep = rand_repeating(4, 2, 0)
        need(len(rep) >= 2)
        q = f"순환소수 ${dtex(ip, pre, rep)}$에서"
    nth = R.randint(15, 120)
    need(nth > len(pre) + len(rep))
    dg = digit_at(f, nth)
    m_ = nth - len(pre)
    qq, k = divmod(m_, len(rep))
    pos = k if k else len(rep)
    return P("어려움", f"{q}, 소수점 아래 {nth}번째 자리의 숫자를 구하시오.", dg,
             hint="순환마디의 숫자 개수로 나눈 나머지를 봐요. 순환하지 않는 자리가 있으면 그만큼 먼저 빼요.",
             explanation=f"{dplain(ip, pre, rep, 12)}: 순환마디 {rep} ({len(rep)}개)" + (f", 앞의 {len(pre)}자리는 빼고 셈" if pre else "")
             + f" → {m_} ÷ {len(rep)}의 나머지 {k} → {dg}",
             steps=[f"소수로 쓰면 {dplain(ip, pre, rep, 12)}",
                    f"순환마디 {rep}: 숫자 [[{len(rep)}]]개가 한 묶음"]
             + ([f"순환하지 않는 자리 {len(pre)}개({pre})를 먼저 빼면: {nth} - {len(pre)} = [[{m_}]]번째 (순환마디 안에서)"] if pre else [])
             + [f"{m_} ÷ {len(rep)} = {qq} … 나머지 [[{k}]]",
                (f"나머지가 {k}이면 순환마디의 {k}번째 숫자" if k else "나머지가 0이면 순환마디의 마지막 숫자") + f" → 순환마디 {rep}의 {pos}번째",
                f"구하는 숫자: [[{dg}]]"])


@kind(U, "10의 거듭제곱을 이용하여 유한소수로", 12)
def _():
    e2, e5 = R.randint(0, 5), R.randint(0, 4)
    d = 2 ** e2 * 5 ** e5
    need(4 <= d <= 250 and e2 != e5)
    n = R.randint(1, d - 1)
    need(math.gcd(n, d) == 1)
    k = max(e2, e5)
    aa = n * 10 ** k // d
    lack, times = ("5", e2 - e5) if e2 > e5 else ("2", e5 - e2)
    mult = int(lack) ** times
    if R.random() < 0.6:
        q, ans_ = f"분수 $\\frac{{{n}}}{{{d}}}$를 $\\frac{{a}}{{10^{{n}}}}$ 꼴로 나타낼 때, 자연수 $a$, $n$에 대하여 $a + n$의 가장 작은 값을 구하시오.", aa + k
    else:
        q, ans_ = f"분수 $\\frac{{{n}}}{{{d}}}$를 분모가 10의 거듭제곱인 분수로 나타낼 때, 분자가 될 수 있는 가장 작은 자연수를 구하시오.", aa
    return P("보통", q, ans_,
             hint="분모를 2와 5의 거듭제곱으로 나타낸 뒤, 지수가 같아지도록 부족한 2나 5를 분모·분자에 곱해요.",
             explanation=f"{d} = {factor_str(d)} → 부족한 {lack}를 {times}번 곱하면 {n}/{d} = {aa}/10{str(k).translate(SUP)}",
             steps=[f"분모를 소인수분해: {d} = {factor_str(d)}",
                    "10 = 2 × 5 이므로, 2와 5의 지수가 같아야 10의 거듭제곱이 돼요",
                    f"{lack}가 {times}번 부족 → 분모와 분자에 {lack}{str(times).translate(SUP) if times > 1 else ''} = {mult}를 곱해요",
                    f"{n} × {mult} / ({d} × {mult}) = {aa}/{10 ** k} = {aa}/10{str(k).translate(SUP)}",
                    f"n = [[{k}]], a = [[{aa}]]"] + ([f"a + n = {aa} + {k} = [[{aa + k}]]"] if ans_ == aa + k else []))


@kind(U, "유한소수가 되도록 미지수 정하기", 14)
def _():
    t_ = R.choice([3, 7, 9, 11, 13, 21, 33, 3, 7])
    N = 2 ** R.randint(0, 3) * 5 ** R.randint(0, 2) * t_
    need(12 <= N <= 700)
    mode = R.random()
    if mode < 0.5:
        n = R.randint(1, N - 1)
        red = F(n, N)
        need(not only25(red.denominator))
        m = rest25(red.denominator)
        g_ = math.gcd(n, N)
        return P("보통", f"분수 $\\frac{{{n}}}{{{N}}} \\times a$를 소수로 나타내면 유한소수가 될 때, $a$의 값이 될 수 있는 가장 작은 자연수를 구하시오.", m,
                 hint="기약분수로 만든 뒤, 분모에서 2와 5가 아닌 소인수를 a가 없애 줘야 해요.",
                 explanation=(f"{n}/{N} = {fpl(red)}, " if g_ > 1 else "") + f"분모 {red.denominator} = {factor_str(red.denominator)} → a는 {m}의 배수",
                 steps=([f"{n}과(와) {N}의 최대공약수 {g_}로 약분: [[{fpl(red)}]]"] if g_ > 1 else [f"{n}/{N}은 이미 기약분수: [[{fpl(red)}]]"])
                 + [f"분모 {red.denominator}를 소인수분해: {factor_str(red.denominator)}",
                    f"유한소수가 되려면 분모에 2와 5만 남아야 해요 → 2·5가 아닌 소인수의 곱 [[{m}]]을(를) a가 약분해 줘야 해요",
                    f"a는 {m}의 배수 → 가장 작은 a = [[{m}]]"])
    lo, hi = (10, 99) if mode < 0.8 else (1, 50)
    goods = [k for k in range(lo, hi + 1) if only25(F(k, N).denominator)]
    need(goods)
    base = [f"분모를 소인수분해: {N} = {factor_str(N)}",
            f"2·5가 아닌 소인수의 곱은 {t_} → a가 {t_}의 배수여야 분모의 {t_}가 약분돼요",
            f"a는 [[{t_}]]의 배수"]
    if mode < 0.8:
        return P("어려움", f"분수 $\\frac{{a}}{{{N}}}$를 소수로 나타내면 유한소수가 될 때, $a$의 값이 될 수 있는 두 자리 자연수의 개수를 구하시오.", len(goods),
                 hint="분모를 소인수분해해서 a가 무엇의 배수여야 하는지 찾아요.",
                 explanation=f"{N} = {factor_str(N)} → a는 {t_}의 배수, 두 자리 {t_}의 배수는 {len(goods)}개",
                 steps=base + [f"두 자리 {t_}의 배수: {goods[0]}, {goods[1] if len(goods) > 1 else ''}, …, {goods[-1]}".replace(", , …", ", …"),
                               f"개수: ({goods[-1]} - {goods[0]}) ÷ {t_} + 1 = [[{len(goods)}]]"])
    return P("어려움", f"분수 $\\frac{{a}}{{{N}}}$를 소수로 나타내면 유한소수가 될 때, 50 이하의 자연수 $a$ 중 가장 큰 수를 구하시오.", max(goods),
             hint="분모를 소인수분해해서 a가 무엇의 배수여야 하는지 찾아요.",
             explanation=f"{N} = {factor_str(N)} → a는 {t_}의 배수, 50 이하에서 가장 큰 것은 {max(goods)}",
             steps=base + [f"50 ÷ {t_} = {50 // t_} … {50 % t_} → 50 이하에서 가장 큰 {t_}의 배수: {t_} × {50 // t_} = [[{max(goods)}]]"])


@kind(U, "순환소수가 되도록 미지수 정하기", 10)
def _():
    num = R.choice([3, 6, 7, 9, 12, 14, 18, 21, 22, 33, 35, 39, 42])
    den0 = R.choice([2, 4, 5, 8, 10, 20])
    pool = list(range(2, 40))
    inf = [k for k in pool if not only25(F(num, den0 * k).denominator)]
    fin = [k for k in pool if only25(F(num, den0 * k).denominator)]
    need(inf and len(fin) >= 4)
    good = R.choice(inf)
    ch, ans = mc(str(good), [str(k) for k in fin])
    f = F(num, den0 * good)
    def judge(c_):
        k = int(c_)
        fin_, why = finite_why(num, den0 * k)
        return not fin_, why.replace(f"{num}/{den0 * k}", f"x = {k}: {num}/{den0 * k}", 1) if f"{num}/{den0 * k} =" in why else f"x = {k}: " + why
    return P("보통", f"분수 $\\frac{{{num}}}{{{den0} \\times x}}$를 소수로 나타내면 순환소수가 될 때, 다음 중 $x$의 값이 될 수 있는 것은?", ans, choices=ch,
             hint="보기를 하나씩 넣어 기약분수로 만든 뒤, 분모에 2와 5가 아닌 소인수가 남는지 봐요.",
             explanation=f"x = {good}이면 {num}/{den0 * good}" + (f" = {fpl(f)}" if f.numerator != num else "") + f", 분모 {factor_str(f.denominator)}에 2·5가 아닌 소인수가 남아 순환소수",
             steps=["순환소수 = 기약분수의 분모에 2와 5 말고 다른 소인수가 남는 경우",
                    f"분자 {num} = {factor_str(num)} → 분모의 이 소인수들은 약분돼서 없어질 수 있어요",
                    f"정답 x = {good}: {num}/{den0 * good}를 기약분수로 [[{fpl(f)}]]",
                    f"분모 {f.denominator} = {factor_str(f.denominator)} → 2·5가 아닌 소인수가 남아서 [[순환소수]]"],
             detail=["보기를 하나씩 넣어 봐요 (⭕ = 순환소수):"] + judge_lines(ch, judge) + [f"그래서 답은 {ans}"])


@kind(U, "순환소수를 분수로 (10의 거듭제곱 이용)", 12)
def _():
    v, ip, pre, rep = rand_repeating(3, 2, 2)
    hi, lo = 10 ** (len(pre) + len(rep)), 10 ** len(pre)
    def e(h, l):
        return f"${h if h > 1 else ''}x - {l if l > 1 else ''}x$"
    diff = v * hi - v * lo
    need(diff.denominator == 1)
    lx = f"{lo}x" if lo > 1 else "x"
    core = [f"x = {dplain(ip, pre, rep, 10)}",
            (f"순환하지 않는 자리가 {len(pre)}개 → 소수점을 {len(pre)}칸 옮기면 {lx} = {dplain(*expand(v * lo), 8)} (바로 순환마디가 시작)" if pre
             else "소수점 바로 다음부터 순환마디가 시작해요: x 그대로"),
            f"순환마디 {rep}가 {len(rep)}자리 → 거기서 {len(rep)}칸 더 옮기면 {hi}x = {dplain(*expand(v * hi), 8)}",
            f"두 수의 소수점 아래가 똑같아서 빼면 정수만 남아요: {hi}x - {lx} = [[{int(diff)}]]",
            f"x = {int(diff)}/{hi - lo} = [[{fpl(v)}]]"]
    if R.random() < 0.6:
        pool = [e(h, l) for h in (10, 100, 1000, 10000) for l in (1, 10, 100) if h > l]
        ch, ans = mc(e(hi, lo), pool)
        return P("보통", f"순환소수 $x = {dtex(ip, pre, rep)}$를 분수로 나타낼 때, 가장 편리한 식은?", ans, choices=ch,
                 hint="소수점 아래가 똑같이 되풀이되는 두 수를 만들어 빼면 소수 부분이 없어져요.",
                 explanation=f"{hi}x = {dplain(*expand(v * hi), 6)}, {lx} = {dplain(*expand(v * lo), 6)} → 빼면 소수 부분이 없어져요.",
                 steps=core, detail=[f"그래서 가장 편리한 식은 {hi}x - {lx} → 답 {ans}"])
    return P("보통", f"순환소수 $x = {dtex(ip, pre, rep)}$일 때, ${hi}x - {lo if lo > 1 else ''}x$의 값을 구하시오.", int(diff),
             hint="두 수의 소수 부분이 같아서 빼면 정수만 남아요.",
             explanation=f"{hi}x - {lx} = {int(diff)} → x = {int(diff)}/{hi - lo} = {fpl(v)}", steps=core[:4])


@kind(U, "순환소수를 기약분수로", 16)
def _():
    v, ip, pre, rep = rand_repeating(3, 2, 3)
    whole, cut = int(str(ip) + pre + rep), int(str(ip) + pre)
    den = int("9" * len(rep) + "0" * len(pre))
    g_ = math.gcd(whole - cut, den)
    return P("보통" if not pre else "어려움", f"순환소수 ${dtex(ip, pre, rep)}$를 기약분수로 나타내시오.", fpl(v),
             hint="분모는 순환마디 숫자 개수만큼 9, 그 뒤에 순환하지 않는 자리 수만큼 0. 분자는 (전체 수) - (순환하지 않는 부분)",
             explanation=f"({whole} - {cut}) / {den} = {whole - cut}/{den}" + (f" = {fpl(v)}" if g_ > 1 else ""),
             steps=[f"{plain_tex('$' + dtex(ip, pre, rep) + '$')} = {dplain(ip, pre, rep, 10)}: 순환마디 {rep} ({len(rep)}자리)" + (f", 순환하지 않는 부분 {pre} ({len(pre)}자리)" if pre else ""),
                    f"분모: 9를 순환마디 자리 수({len(rep)})만큼" + (f", 그 뒤에 0을 순환하지 않는 자리 수({len(pre)})만큼" if pre else "") + f" → [[{den}]]",
                    f"분자: 점을 빼고 쓴 수 {whole} - 순환하지 않는 부분 {cut} = [[{whole - cut}]]",
                    f"분수: {whole - cut}/{den}" + (f" → 최대공약수 {g_}로 약분" if g_ > 1 else " (더 약분되지 않아요)"),
                    f"기약분수: [[{fpl(v)}]]"])


@kind(U, "두 분수 사이의 유한소수", 10)
def _():
    t_ = R.choice([3, 7, 9, 11])
    d = 2 ** R.randint(0, 3) * 5 ** R.randint(0, 1) * t_
    need(14 <= d <= 120)
    lo = F(R.randint(1, 4), R.choice([3, 4, 5, 6, 8]))
    hi = F(R.randint(2, 7), R.choice([3, 4, 5, 6, 8]))
    need(F(1, 10) < lo < hi < 1 and hi - lo > F(1, 4))
    ks = [k for k in range(1, d) if lo < F(k, d) < hi]
    goods = [k for k in ks if only25(F(k, d).denominator)]
    cnt = len(goods)
    need(ks and cnt >= 1)
    return P("어려움", f"두 분수 ${ftex(lo)}$과 ${ftex(hi)}$ 사이에 있는 분수 중에서 분모가 {d}이고 분자가 자연수인 분수 가운데, 유한소수로 나타낼 수 있는 것의 개수를 구하시오.", cnt,
             hint=f"분모 {d}로 통분해 분자의 범위를 구하고, 유한소수가 되려면 분자가 무엇의 배수인지 찾아요.",
             explanation=f"{ks[0]} ≤ a ≤ {ks[-1]}, {d} = {factor_str(d)} → a는 {t_}의 배수 → {cnt}개",
             steps=[f"구하는 분수를 a/{d}로 놓아요",
                    f"분모를 {d}로 맞추면: {fpl(lo)} = {fpl(lo * d)}/{d}, {fpl(hi)} = {fpl(hi * d)}/{d}",
                    f"{fpl(lo * d)} < a < {fpl(hi * d)} 인 자연수 a: [[{ks[0]}]]부터 [[{ks[-1]}]]까지",
                    f"분모 {d} = {factor_str(d)} → 유한소수가 되려면 a가 [[{t_}]]의 배수",
                    f"그 범위의 {t_}의 배수: {', '.join(map(str, goods))}",
                    f"개수: [[{cnt}]]"])


@kind(U, "순환소수에 곱해서 유한소수 만들기", 12)
def _():
    v, ip, pre, rep = rand_repeating(2, 2, 1)
    m = rest25(v.denominator)
    need(1 < m <= 99)
    whole, cut = int(str(ip) + pre + rep), int(str(ip) + pre)
    den = int("9" * len(rep) + "0" * len(pre))
    return P("어려움", f"순환소수 ${dtex(ip, pre, rep)}$에 어떤 자연수 $x$를 곱하면 유한소수가 된다. $x$의 값이 될 수 있는 가장 작은 자연수를 구하시오.", m,
             hint="먼저 순환소수를 기약분수로 바꾸고, 분모에서 2와 5가 아닌 부분을 없애요.",
             explanation=f"{dplain(ip, pre, rep, 6)} = {fpl(v)}, 분모 {v.denominator} = {factor_str(v.denominator)} → x = {m}",
             steps=[f"순환소수를 분수로: ({whole} - {cut}) / {den} = {whole - cut}/{den}",
                    f"기약분수로: [[{fpl(v)}]]",
                    f"분모 {v.denominator} = {factor_str(v.denominator)}",
                    f"유한소수가 되려면 분모에 2와 5만 남아야 해요 → 2·5가 아닌 부분 {m}을(를) x가 약분해 줘야 해요",
                    f"x는 {m}의 배수 → 가장 작은 x = [[{m}]]"])


FACTS = [
    ("모든 순환소수는 유리수이다.", True, "순환소수는 분수로 나타낼 수 있어요"),
    ("모든 유한소수는 유리수이다.", True, "유한소수는 분모가 10의 거듭제곱인 분수예요"),
    ("기약분수의 분모에 2와 5 이외의 소인수가 있으면 유한소수로 나타낼 수 없다.", True, "그런 분수는 순환소수가 돼요"),
    ("정수가 아닌 유리수는 유한소수 또는 순환소수로 나타낼 수 있다.", True, "분수를 나누면 끝나거나 되풀이돼요"),
    ("순환소수는 분수로 나타낼 수 있다.", True, "10의 거듭제곱을 곱해 빼면 분수가 돼요"),
    ("유한소수는 분모가 10의 거듭제곱인 분수로 나타낼 수 있다.", True, "예: 0.25 = 25/100"),
    ("무한소수는 모두 유리수이다.", False, "순환하지 않는 무한소수(예: π)는 유리수가 아니에요"),
    ("순환소수 중에는 유리수가 아닌 것도 있다.", False, "순환소수는 모두 유리수예요"),
    ("분모에 3이 있는 분수는 항상 순환소수로 나타내어진다.", False, "약분되면 달라져요 (예: 3/6 = 1/2 = 0.5)"),
    ("유한소수로 나타낼 수 없는 분수는 유리수가 아니다.", False, "순환소수가 되는 분수도 유리수예요"),
    ("$0.\\dot{9}$는 1보다 작다.", False, "0.9̇ = 9/9 = 1 이에요"),
    ("모든 무한소수는 순환소수이다.", False, "순환하지 않는 무한소수도 있어요"),
    ("순환소수는 모두 무리수이다.", False, "순환소수는 모두 유리수예요"),
]


@kind(U, "유리수와 소수의 관계", 8)
def _():
    want = R.random() < 0.5
    ch, ans = pick([(s_, t_) for s_, t_, _ in R.sample(FACTS, len(FACTS))], want)
    why = {s_: (t_, r_) for s_, t_, r_ in FACTS}
    def judge(c_):
        t_, r_ = why[c_]
        return t_, ("옳아요: " if t_ else "틀려요: ") + r_
    return P("보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="유한소수와 순환소수는 분수로 나타낼 수 있어요 → 유리수. 순환하지 않는 무한소수는 유리수가 아니에요.",
             explanation="정수가 아닌 유리수 = 유한소수 또는 순환소수. 순환소수·유한소수는 모두 유리수예요.",
             steps=["유리수 = 분수로 나타낼 수 있는 수", "유한소수, 순환소수는 분수로 나타낼 수 있으니 모두 [[유리수]]",
                    "순환하지 않는 무한소수는 분수로 나타낼 수 없어요 (유리수가 아님)"],
             detail=["보기를 하나씩 확인해요 (⭕ = 옳은 문장):"] + judge_lines(ch, judge) + [f"그래서 {'옳은' if want else '옳지 않은'} 것은 {ans}"])


# ═════════════════════ 2. 단항식의 계산 ═════════════════════
U = "2. 단항식의 계산"


def law_statements():
    """(식, 참/거짓, 이유) 후보들: 지수법칙."""
    out = []
    v = R.choice("axy")
    def pw(e):
        return f"{v}{str(e).translate(SUP)}" if e != 1 else v
    m, n = R.randint(2, 7), R.randint(2, 6)
    out += [(f"${v}^{{{m}}} \\times {v}^{{{n}}} = {v}^{{{m + n}}}$", True, f"곱하면 지수를 더해요: {m} + {n} = {m + n}"),
            (f"${v}^{{{m}}} \\times {v}^{{{n}}} = {v}^{{{m * n}}}$", m * n == m + n, f"곱하면 지수를 더해요 → {pw(m + n)}")]
    m, n = R.randint(2, 6), R.randint(2, 5)
    out += [(f"$({v}^{{{m}}})^{{{n}}} = {v}^{{{m * n}}}$", True, f"거듭제곱의 거듭제곱은 지수를 곱해요: {m} × {n} = {m * n}"),
            (f"$({v}^{{{m}}})^{{{n}}} = {v}^{{{m + n}}}$", m * n == m + n, f"거듭제곱의 거듭제곱은 지수를 곱해요 → {pw(m * n)}")]
    m, n = R.randint(6, 12), R.randint(2, 5)
    out += [(f"${v}^{{{m}}} \\div {v}^{{{n}}} = {v}^{{{m - n}}}$", True, f"나누면 지수를 빼요: {m} - {n} = {m - n}")]
    if m % n == 0 and m // n != m - n:
        out += [(f"${v}^{{{m}}} \\div {v}^{{{n}}} = {v}^{{{m // n}}}$", False, f"나누면 지수를 빼요 → {pw(m - n)}")]
    out += [(f"${v}^{{{n}}} \\div {v}^{{{n}}} = 0$", False, "같은 수로 나누면 1이에요"),
            (f"${v}^{{{n}}} \\div {v}^{{{n}}} = 1$", True, "같은 수로 나누면 1이에요"),
            (f"${v}^{{{n}}} \\div {v}^{{{n + 2}}} = \\frac{{1}}{{{v}^{{2}}}}$", True, f"나누는 쪽 지수가 더 크면 1/{v}^(차): {n + 2} - {n} = 2"),
            (f"${v}^{{{n}}} \\div {v}^{{{n + 2}}} = {v}^{{2}}$", False, f"나누는 쪽 지수가 더 크면 분모에 남아요 → 1/{v}²")]
    k, m, n = R.randint(2, 4), R.randint(2, 4), R.randint(2, 3)
    out += [(f"$({k}{v}^{{{m}}})^{{{n}}} = {k ** n}{v}^{{{m * n}}}$", True, f"계수도 {n}제곱: {k}{str(n).translate(SUP)} = {k ** n}"),
            (f"$({k}{v}^{{{m}}})^{{{n}}} = {k}{v}^{{{m * n}}}$", False, f"계수도 {n}제곱 해야 해요 → {k ** n}{pw(m * n)}"),
            (f"$(-{v}^{{{m}}})^{{{2 * n}}} = -{v}^{{{2 * m * n}}}$", False, f"음수의 짝수 제곱은 양수 → {pw(2 * m * n)}"),
            (f"$(-{v}^{{{m}}})^{{{2 * n + 1}}} = -{v}^{{{m * (2 * n + 1)}}}$", True, "음수의 홀수 제곱은 음수예요")]
    m, n, k = R.randint(1, 4), R.randint(2, 4), R.randint(2, 4)
    xm = "x" if m == 1 else f"x^{{{m}}}"
    out += [(f"$\\left(\\frac{{{xm}}}{{y^{{{n}}}}}\\right)^{{{k}}} = \\frac{{x^{{{m * k}}}}}{{y^{{{n * k}}}}}$", True, "분자와 분모에 모두 지수를 곱해요"),
            (f"$\\left(\\frac{{{xm}}}{{y^{{{n}}}}}\\right)^{{{k}}} = \\frac{{x^{{{m * k}}}}}{{y^{{{n}}}}}$", False, f"분모에도 지수를 곱해요 → y의 지수 {n * k}"),
            (f"$\\left(-\\frac{{a}}{{b^{{{n}}}}}\\right)^{{{2 * k}}} = -\\frac{{a^{{{2 * k}}}}}{{b^{{{2 * k * n}}}}}$", False, "짝수 제곱이라 부호가 + 예요")]
    return [(t_.replace("^{1}", ""), v_, w_) for t_, v_, w_ in out]


@kind(U, "지수법칙", 14)
def _():
    want = R.random() < 0.5
    sts = law_statements()
    ch, ans = pick([(t_, v_) for t_, v_, _ in sts], want)
    why = {t_: (v_, w_) for t_, v_, w_ in sts}
    def judge(c_):
        v_, w_ = why[c_]
        return v_, ("옳아요: " if v_ else "틀려요: ") + w_
    return P("보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="곱하면 지수를 더하고, 거듭제곱의 거듭제곱은 지수를 곱하고, 나누면 지수를 빼요. 음수의 짝수 제곱은 양수!",
             explanation="aᵐ×aⁿ = aᵐ⁺ⁿ, (aᵐ)ⁿ = aᵐⁿ, aᵐ÷aⁿ = aᵐ⁻ⁿ (m>n), aⁿ÷aⁿ = 1, (ab)ⁿ = aⁿbⁿ 를 하나씩 확인해요.",
             steps=["지수법칙 다섯 가지를 떠올려요", "aᵐ × aⁿ: 지수를 [[더한다]]", "(aᵐ)ⁿ: 지수를 [[곱한다]]",
                    "aᵐ ÷ aⁿ: 지수를 빼요 (같으면 1, 나누는 쪽이 크면 1/a^(차))", "(ab)ⁿ = aⁿbⁿ: 계수에도 지수를, 음수의 짝수 제곱은 양수"],
             detail=["보기를 하나씩 확인해요 (⭕ = 옳은 식):"] + judge_lines(ch, judge) + [f"그래서 {'옳은' if want else '옳지 않은'} 것은 {ans}"])


@kind(U, "지수법칙: 지수의 합과 차", 14)
def _():
    base = R.choice([2, 3, 5])
    lg = {base ** k: k for k in range(1, 6) if base ** k <= 625}
    nums = list(lg)
    terms = [(R.choice(nums), R.randint(1, 4))]
    for _ in range(R.randint(1, 2)):
        terms.append((R.choice(nums), R.choice([1, 2, 3, -1, -2, -3])))
    tot = sum(lg[b0] * e for b0, e in terms)
    need(tot > 0 and len({b0 for b0, _ in terms}) >= 2)
    s = f"{terms[0][0]}^{{{terms[0][1]}}}"
    for b0, e in terms[1:]:
        s += f" \\times {b0}^{{{e}}}" if e > 0 else f" \\div {b0}^{{{-e}}}"
    s = s.replace("^{1}", "")
    def sup_(e):
        return str(e).translate(SUP) if e != 1 else ""
    conv = [f"{b0} = {base}{sup_(lg[b0])}" for b0 in dict.fromkeys(b0 for b0, _ in terms)]
    lines = [f"모두 {base}의 거듭제곱으로 바꿔요: " + ", ".join(conv)]
    for b0, e in terms:
        lines.append(f"{b0}{sup_(abs(e))} = ({base}{sup_(lg[b0])}){sup_(abs(e))} = {base}^[[{lg[b0] * abs(e)}]]")
    expr = " ".join(("" if i == 0 else ("+ " if e > 0 else "- ")) + str(lg[b0] * abs(e)) for i, (b0, e) in enumerate(terms))
    lines.append(f"곱하면 지수를 더하고 나누면 빼요: x = {expr} = [[{tot}]]")
    return P("보통", f"${s} = {base}^{{x}}$일 때, 자연수 $x$의 값을 구하시오.", tot,
             hint=f"모든 수를 {base}의 거듭제곱으로 바꾼 뒤, 곱하면 지수를 더하고 나누면 빼요.",
             explanation=", ".join(conv) + f" → x = {tot}", steps=lines)


@kind(U, "지수법칙: 지수의 분배", 14)
def _():
    mode = R.random()
    if mode < 0.55:
        k, m, n = R.randint(2, 5), R.randint(2, 5), R.randint(2, 4)
        need(k ** n <= 1024)
        c = R.randint(1, 4)
        yc = "y" if c == 1 else f"y^{{{c}}}"
        q = f"$({k}x^{{a}}{yc})^{{b}} = {k ** n}x^{{{m * n}}}y^{{c}}$일 때, 자연수 $a$, $b$, $c$에 대하여 $a + b + c$의 값을 구하시오."
        return P("보통", q, m + n + c * n,
                 hint=f"계수: {k}의 몇 제곱이 {k ** n}인지 먼저 찾아 b를 구해요.",
                 explanation=f"{k}^b = {k ** n} → b = {n}, a × {n} = {m * n} → a = {m}, c = {c} × {n} = {c * n} → 합 {m + n + c * n}",
                 steps=[f"왼쪽을 펼치면: ({k}xᵃ{'y' if c == 1 else 'y' + str(c).translate(SUP)})ᵇ = {k}ᵇ x^(a×b) y^({c}×b)",
                        f"계수 비교: {k}ᵇ = {k ** n} = {k}{str(n).translate(SUP)} → b = [[{n}]]",
                        f"x의 지수 비교: a × {n} = {m * n} → a = [[{m}]]",
                        f"y의 지수: c = {c} × {n} = [[{c * n}]]",
                        f"a + b + c = {m} + {n} + {c * n} = [[{m + n + c * n}]]"])
    c, m, k = R.randint(2, 4), R.randint(1, 3), R.randint(2, 3)
    inner = (-Q(c) * y ** m / ((c + 1) * x)) ** k
    right = sp.simplify(inner ** 2)
    ch, ans = mono_mc(right, (x, y), [-right, sp.simplify(inner * 2), sp.simplify(Q(c) ** (2 * k) * y ** (2 * m * k) / ((c + 1) * x ** (2 * k)))])
    ym = "y" if m == 1 else f"y^{{{m}}}"
    return P("어려움", f"$\\left\\{{\\left(-\\frac{{{c}{ym}}}{{{c + 1}x}}\\right)^{{{k}}}\\right\\}}^{{2}}$을 간단히 하면?", ans, choices=ch,
             hint="분자·분모에 각각 지수를 분배하고, 음수의 짝수 제곱은 양수예요.",
             explanation=f"전체 지수 {k}×2 = {2 * k}, 짝수 제곱이라 부호 + → {mono(right, (x, y), False)}",
             steps=[f"거듭제곱의 거듭제곱: 지수를 곱해서 전체 {k} × 2 = [[{2 * k}]]제곱",
                    f"부호: 음수를 {2 * k}번(짝수 번) 곱하면 [[+]]",
                    f"분자: {c}{str(2 * k).translate(SUP)} = {c ** (2 * k)}, y의 지수 {m} × {2 * k} = [[{2 * m * k}]]",
                    f"분모: {c + 1}{str(2 * k).translate(SUP)} = {(c + 1) ** (2 * k)}, x의 지수 1 × {2 * k} = [[{2 * k}]]",
                    f"정리하면: {mono(right, (x, y), False)} → 답 {ans}"])


@kind(U, "거듭제곱의 합", 12)
def _():
    base = R.choice([2, 3, 4, 5])
    e = R.randint(2, 9)
    mode = R.random()
    if base == 2 and mode < 0.5:
        k = R.choice([2, 4, 8])
        lg = int(math.log2(k))
        s = " + ".join([f"2^{{{e}}}"] * k)
        return P("보통", f"${s}$을 2의 거듭제곱으로 나타내면 $2^{{\\square}}$이다. $\\square$ 안에 알맞은 수를 구하시오.", e + lg,
                 hint="같은 수를 여러 번 더하면 곱셈이에요. 더한 개수도 2의 거듭제곱으로 바꿔요.",
                 explanation=f"{k} × 2{str(e).translate(SUP)} = 2{str(lg).translate(SUP)} × 2{str(e).translate(SUP)} = 2{str(e + lg).translate(SUP)}",
                 steps=[f"같은 수 2{str(e).translate(SUP)}을 {k}번 더했어요 → {k} × 2{str(e).translate(SUP)}",
                        f"{k}도 2의 거듭제곱으로: {k} = 2^[[{lg}]]",
                        f"2{str(lg).translate(SUP)} × 2{str(e).translate(SUP)}: 곱하면 지수를 더해요 → {lg} + {e}",
                        f"= 2^[[{e + lg}]]"])
    if base in (2, 3) and mode < 0.8:
        k1 = base
        top = R.randint(4, 9)
        b2 = base ** 2
        e2 = R.randint(2, 3)
        res = (top + 1) - (2 * e2 + 1)
        need(res >= 1)
        s1 = " + ".join([f"{base}^{{{top}}}"] * k1)
        s2 = " + ".join([f"{b2}^{{{e2}}}"] * k1)
        return P("어려움", f"$\\frac{{{s1}}}{{{s2}}} = {base}^{{\\square}}$일 때, $\\square$ 안에 알맞은 자연수를 구하시오.", res,
                 hint=f"분자 = {base} × {base}^{top}, 분모 = {base} × {b2}^{e2}, {b2} = {base}²",
                 explanation=f"분자 {base}^{top + 1}, 분모 {base}^{2 * e2 + 1} → {base}^{res}",
                 steps=[f"분자: {base}{str(top).translate(SUP)}을 {base}번 더함 → {base} × {base}{str(top).translate(SUP)} = {base}^[[{top + 1}]]",
                        f"분모: {b2}{str(e2).translate(SUP)}을 {base}번 더함 → {base} × {b2}{str(e2).translate(SUP)}",
                        f"{b2} = {base}² 이므로 {b2}{str(e2).translate(SUP)} = {base}{str(2 * e2).translate(SUP)} → 분모 = {base} × {base}{str(2 * e2).translate(SUP)} = {base}^[[{2 * e2 + 1}]]",
                        f"나누면 지수를 빼요: {top + 1} - {2 * e2 + 1} = [[{res}]]"])
    k = base
    s = " + ".join([f"{base}^{{{e}}}"] * k)
    return P("보통", f"${s}$을 {base}의 거듭제곱으로 나타내면 ${base}^{{\\square}}$이다. $\\square$ 안에 알맞은 수를 구하시오.", e + 1,
             hint=f"{base}^{e}를 {base}번 더하면 {base} × {base}^{e} 이에요.",
             explanation=f"{base} × {base}{str(e).translate(SUP)} = {base}{str(e + 1).translate(SUP)}",
             steps=[f"같은 수 {base}{str(e).translate(SUP)}을 {base}번 더했어요 → {base} × {base}{str(e).translate(SUP)}",
                    f"{base} = {base}¹ 이니까 {base}¹ × {base}{str(e).translate(SUP)}: 지수를 더해요 → 1 + {e}",
                    f"= {base}^[[{e + 1}]]"])


@kind(U, "문자를 사용하여 거듭제곱 나타내기", 12)
def _():
    base = R.choice([2, 3, 5])
    k = R.randint(2, 5)
    big = base ** k
    need(big <= 625)
    if R.random() < 0.5:
        ch, ans = mc(f"$A^{{{k}}}$", [f"$A^{{{k + 1}}}$", f"${k}A$", f"${k}A^{{{k}}}$", f"$A^{{{2 * k}}}$", f"$A^{{{k - 1}}}$" if k > 2 else "$A^{5}$", f"${big}A$"])
        return P("보통", f"${base}^{{x}} = A$라 할 때, ${big}^{{x}}$을 $A$를 사용하여 나타내면? (단, $x$는 자연수)", ans, choices=ch,
                 hint=f"{big}을 {base}의 거듭제곱으로 바꾼 뒤 지수의 순서를 바꿔요.",
                 explanation=f"{big}ˣ = ({base}{str(k).translate(SUP)})ˣ = ({base}ˣ){str(k).translate(SUP)} = A{str(k).translate(SUP)}",
                 steps=[f"{big}을 {base}의 거듭제곱으로: {big} = {base}^[[{k}]]",
                        f"{big}ˣ = ({base}{str(k).translate(SUP)})ˣ = {base}^({k}x)",
                        f"지수의 곱셈은 순서를 바꿔도 같아요: {base}^({k}x) = ({base}ˣ){str(k).translate(SUP)}",
                        f"{base}ˣ = A 이므로 = A^[[{k}]]"],
                 detail=[f"그래서 답은 {ans}"])
    e = R.randint(2, 6)
    ch, ans = mc(f"$\\frac{{1}}{{A^{{{k}}}}}$", [f"$\\frac{{1}}{{A^{{{j}}}}}$" for j in range(2, 3 * k + 2) if j != k] + [f"$A^{{{k}}}$"])
    return P("어려움", f"${base}^{{{e}}} = A$라 할 때, $\\frac{{1}}{{{big}^{{{e}}}}}$을 $A$를 사용하여 나타내면?", ans, choices=ch,
             hint=f"{big}을 {base}의 거듭제곱으로 바꿔요.",
             explanation=f"{big}{str(e).translate(SUP)} = {base}{str(k * e).translate(SUP)} = ({base}{str(e).translate(SUP)}){str(k).translate(SUP)} = A{str(k).translate(SUP)}",
             steps=[f"{big}을 {base}의 거듭제곱으로: {big} = {base}{str(k).translate(SUP)}",
                    f"{big}{str(e).translate(SUP)} = ({base}{str(k).translate(SUP)}){str(e).translate(SUP)} = {base}^[[{k * e}]]",
                    f"{base}{str(k * e).translate(SUP)} = ({base}{str(e).translate(SUP)}){str(k).translate(SUP)} = A^[[{k}]]",
                    f"그래서 1/{big}{str(e).translate(SUP)} = 1/A{str(k).translate(SUP)} → 답 {ans}"])


@kind(U, "n자리 자연수", 12)
def _():
    e2, e5 = R.randint(2, 12), R.randint(2, 12)
    need(e2 != e5)
    extra = R.choice([1, 1, 3, 7, 9, 11, 3 ** 2])
    N = 2 ** e2 * 5 ** e5 * extra
    m = min(e2, e5)
    rest = N // 10 ** m
    left = (f"2{str(e2 - m).translate(SUP) if e2 - m > 1 else ''}" if e2 > m else f"5{str(e5 - m).translate(SUP) if e5 - m > 1 else ''}")
    q = f"2^{{{e2}}} \\times 5^{{{e5}}}" + (f" \\times {extra}" if extra != 1 else "")
    return P("어려움", f"${q}$은 몇 자리 자연수인지 구하시오.", len(str(N)),
             hint="2와 5를 같은 지수끼리 묶어 10의 거듭제곱을 만들어요.",
             explanation=f"= {rest} × 10{str(m).translate(SUP)} → ({len(str(rest))}자리) + {m} = {len(str(N))}자리",
             steps=[f"2 × 5 = 10 이니까, 2와 5를 지수가 작은 쪽({m})에 맞춰 묶어요",
                    f"2{str(m).translate(SUP)} × 5{str(m).translate(SUP)} = 10^[[{m}]]",
                    f"남는 것: {left}" + (f" × {extra}" if extra != 1 else "") + f" = [[{rest}]]",
                    f"그래서 {rest} × 10{str(m).translate(SUP)} = {rest} 뒤에 0이 {m}개",
                    f"자릿수: {len(str(rest))} + {m} = [[{len(str(N))}]]"])


@kind(U, "(단항식)×(단항식)", 14)
def _():
    c1, a1, b1, k1 = nz(-3, 3, (0, 1)), R.randint(1, 3), R.randint(1, 3), R.randint(2, 3)
    c2, a2, b2 = nz(-6, 6), R.randint(0, 3), R.randint(1, 4)
    d3, a3, b3 = R.randint(2, 30), R.randint(1, 6), R.randint(1, 6)
    s3 = R.choice([1, -1])
    e = sp.expand((c1 * x ** a1 * y ** b1) ** k1 * (c2 * x ** a2 * y ** b2) * s3 / (d3 * x ** a3 * y ** b3))
    need(e.is_polynomial(x, y) and e != 0)
    (mon, coef), = sp.Poly(e, x, y).terms()
    B, C = mon
    need(B >= 1 and C >= 1 and coef.is_integer and abs(coef) <= 60)
    second = paren_t(c2 * x ** a2 * y ** b2)
    third = f"\\left({'-' if s3 < 0 else ''}\\frac{{1}}{{{T(d3 * x ** a3 * y ** b3)}}}\\right)"
    ms, chk = mono_steps([(c1 * x ** a1 * y ** b1, k1, "×"), (c2 * x ** a2 * y ** b2, 1, "×"), (s3 * d3 * x ** a3 * y ** b3, 1, "÷")], (x, y))
    need(sp.simplify(chk - e) == 0)
    if R.random() < 0.6:
        return P("어려움", f"$({T(c1 * x ** a1 * y ** b1)})^{{{k1}}} \\times {second} \\times {third} = Ax^{{B}}y^{{C}}$일 때, $A + B + C$의 값을 구하시오. (단, $A$는 수, $B$, $C$는 자연수)",
                 int(coef) + B + C, hint="부호 → 계수 → 문자 순서로 따로 계산해요.",
                 explanation=f"간단히 하면 {Pl(e)} → A = {coef}, B = {B}, C = {C}",
                 steps=ms + [f"A + B + C = {coef} + {B} + {C} = [[{int(coef) + B + C}]]"])
    ch, ans = mono_mc(e, (x, y))
    return P("보통", f"$({T(c1 * x ** a1 * y ** b1)})^{{{k1}}} \\times {second} \\times {third}$를 간단히 하면?", ans, choices=ch,
             hint="거듭제곱을 먼저 하고, 계수끼리·문자끼리 따로 계산해요.", explanation=f"간단히 하면 {Pl(e)}", steps=ms)


@kind(U, "(단항식)÷(단항식)", 14)
def _():
    c, m, n, k = R.randint(2, 4), R.randint(1, 3), R.randint(1, 3), R.randint(2, 3)
    dc = F(R.randint(1, 9), R.randint(1, 6))
    dm, dn = R.randint(1, m * k), R.randint(0, n * k)
    res = sp.expand((c * a ** m * b ** n) ** k / (Q(dc) * a ** dm * b ** dn))
    need(res.is_polynomial(a, b))
    (mon, coef), = sp.Poly(res, a, b).terms()
    need(coef.is_integer and 1 < abs(coef) <= 100 and mon[0] >= 1 and mon[1] >= 1 and dc.denominator > 1)
    dtx = ftex(dc) + T(a ** dm * b ** dn, (a, b))
    ms, chk = mono_steps([(c * a ** m * b ** n, k, "×"), (Q(dc) * a ** dm * b ** dn, 1, "÷")], (a, b))
    need(sp.simplify(chk - res) == 0)
    if R.random() < 0.6:
        return P("보통", f"$({T(c * a ** m * b ** n, (a, b))})^{{{k}}} \\div {dtx} = {coef}a^{{x}}b^{{y}}$일 때, 자연수 $x$, $y$에 대하여 $x + y$의 값을 구하시오.",
                 mon[0] + mon[1], hint="분수로 나누는 것은 역수를 곱하는 것과 같아요.",
                 explanation=f"({Pl(c * a ** m * b ** n, (a, b))})^{k} = {Pl((c * a ** m * b ** n) ** k, (a, b))}, ÷ {fpl(dc)}{Pl(a ** dm * b ** dn, (a, b))} → {Pl(res, (a, b))}",
                 steps=ms + [f"x + y = {mon[0]} + {mon[1]} = [[{mon[0] + mon[1]}]]"])
    ch, ans = mono_mc(res, (a, b))
    return P("보통", f"$({T(c * a ** m * b ** n, (a, b))})^{{{k}}} \\div {dtx}$를 계산하면?", ans, choices=ch,
             hint="나눗셈은 역수의 곱셈으로 바꿔요: ÷ (p/q)A = × q/(pA)", explanation=f"계산하면 {Pl(res, (a, b))}", steps=ms)


@kind(U, "곱셈과 나눗셈의 혼합 계산", 14)
def _():
    c1, a1, k1 = nz(-4, 4), R.randint(1, 3), R.randint(1, 2)
    c2, a2, b2, k2 = nz(-3, 3), R.randint(1, 2), R.randint(1, 2), R.randint(2, 3)
    dc = F(R.randint(1, 4), R.randint(1, 4))
    da, db = R.randint(1, 4), R.randint(1, 3)
    e1, e2 = (c1 * x ** a1) ** k1, (c2 * x ** a2 * y ** b2) ** k2
    res = sp.expand(e1 * e2 / (Q(dc) * x ** da * y ** db))
    need(res.is_polynomial(x, y))
    (mon, coef), = sp.Poly(res, x, y).terms()
    need(coef.is_integer and abs(coef) <= 200 and mon[0] >= 1 and mon[1] >= 1)
    ch, ans = mono_mc(res, (x, y), [sp.expand(e1 * e2 * Q(dc) * x ** da * y ** db)])
    ms, chk = mono_steps([(c1 * x ** a1, k1, "×"), (c2 * x ** a2 * y ** b2, k2, "×"), (Q(dc) * x ** da * y ** db, 1, "÷")], (x, y))
    need(sp.simplify(chk - res) == 0)
    q = f"$({T(c1 * x ** a1)})" + (f"^{{{k1}}}" if k1 > 1 else "") + f" \\times ({T(c2 * x ** a2 * y ** b2)})^{{{k2}}} \\div {ftex(dc) if dc != 1 else ''}{T(x ** da * y ** db)}$을 계산하면?"
    return P("보통", q, ans, choices=ch, hint="거듭제곱을 먼저 하고, 나눗셈은 역수의 곱셈으로 바꿔요.",
             explanation=f"계산하면 {Pl(res)}", steps=ms)


@kind(U, "□ 안에 알맞은 식 (단항식)", 12)
def _():
    L = R.randint(2, 12) * a ** R.randint(1, 3) * b ** R.randint(0, 2)
    BX = nz(-6, 6) * a ** R.randint(0, 3) * b ** R.randint(0, 3)
    need(BX.free_symbols)
    D = R.randint(2, 12) * a ** R.randint(1, 3) * b ** R.randint(0, 2)
    Rr = sp.simplify(L * BX / D)
    need(sp.fraction(sp.together(Rr))[1] == 1)
    if R.random() < 0.5:
        q = f"${T(L, (a, b))} \\times \\square \\div {T(D, (a, b))} = {mono(Rr, (a, b))}$일 때, $\\square$ 안에 알맞은 식은?"
        ex = f"□ = {mono(Rr, (a, b), False)} × {Pl(D, (a, b))} ÷ {Pl(L, (a, b))} = {mono(BX, (a, b), False)}"
        ms, chk = mono_steps([(Rr, 1, "×"), (D, 1, "×"), (L, 1, "÷")], (a, b))
    else:
        q = f"$\\square \\div {T(D, (a, b))} \\times {T(L, (a, b))} = {mono(Rr, (a, b))}$일 때, $\\square$ 안에 알맞은 식은?"
        ex = f"□ = {mono(Rr, (a, b), False)} ÷ {Pl(L, (a, b))} × {Pl(D, (a, b))} = {mono(BX, (a, b), False)}"
        ms, chk = mono_steps([(Rr, 1, "×"), (L, 1, "÷"), (D, 1, "×")], (a, b))
    need(sp.simplify(chk - BX) == 0)
    ch, ans = mono_mc(BX, (a, b))
    return P("보통", q, ans, choices=ch, hint="□를 남기고 나머지를 반대 연산으로 넘겨요: 곱한 것은 나누고, 나눈 것은 곱해요.",
             explanation=ex, steps=["□만 남기고 나머지는 반대 연산으로: " + ex.split(" = ")[0] + " = " + ex.split(" = ")[1]] + ms)


@kind(U, "단항식: 도형에의 활용", 12)
def _():
    mode = R.choice(["para", "rect", "cuboid", "tri", "prism"])
    g = (a, b) if R.random() < 0.5 else (x, y)
    u, v = g
    m1 = R.randint(1, 6) * u ** R.randint(1, 2) * v ** R.randint(0, 2)
    m2 = R.randint(1, 6) * u ** R.randint(0, 2) * v ** R.randint(1, 3)
    if mode == "para":
        q = f"밑변의 길이가 ${T(m1, g)}$인 평행사변형의 넓이가 ${T(m1 * m2, g)}$일 때, 이 평행사변형의 높이는?"
        ans_, ex, hint = m2, f"높이 = {Pl(m1 * m2, g)} ÷ {Pl(m1, g)} = {Pl(m2, g)}", "평행사변형의 넓이 = 밑변 × 높이"
    elif mode == "rect":
        q = f"세로의 길이가 ${T(m1, g)}$인 직사각형의 넓이가 ${T(m1 * m2, g)}$일 때, 이 직사각형의 가로의 길이는?"
        ans_, ex, hint = m2, f"가로 = {Pl(m1 * m2, g)} ÷ {Pl(m1, g)} = {Pl(m2, g)}", "직사각형의 넓이 = 가로 × 세로"
    elif mode == "tri":
        need(sp.Poly(m1 * m2, *g).LC() % 2 == 0)
        q = f"높이가 ${T(m1, g)}$인 삼각형의 넓이가 ${T(sp.expand(m1 * m2 / 2), g)}$일 때, 이 삼각형의 밑변의 길이는?"
        ans_, ex, hint = m2, f"밑변 = {Pl(sp.expand(m1 * m2 / 2), g)} × 2 ÷ {Pl(m1, g)} = {Pl(m2, g)}", "삼각형의 넓이 = 밑변 × 높이 ÷ 2"
    elif mode == "cuboid":
        m3 = R.randint(1, 4) * u ** R.randint(0, 2) * v ** R.randint(0, 2)
        need(m3.free_symbols)
        q = f"가로의 길이가 ${T(m1, g)}$, 세로의 길이가 ${T(m3, g)}$인 직육면체의 부피가 ${T(sp.expand(m1 * m2 * m3), g)}$일 때, 이 직육면체의 높이는?"
        ans_, ex, hint = m2, f"높이 = {Pl(sp.expand(m1 * m2 * m3), g)} ÷ ({Pl(sp.expand(m1 * m3), g)}) = {Pl(m2, g)}", "직육면체의 부피 = 가로 × 세로 × 높이"
    else:
        m3 = R.randint(1, 4) * u ** R.randint(0, 1) * v ** R.randint(0, 1) * 2
        base_area = sp.expand(m1 * m3 / 2)
        q = f"밑면이 두 변의 길이가 ${T(m1, g)}$, ${T(m3, g)}$인 직각삼각형인 삼각기둥의 부피가 ${T(sp.expand(base_area * m2), g)}$일 때, 이 삼각기둥의 높이는?"
        ans_, ex, hint = m2, f"밑넓이 = {Pl(base_area, g)}, 높이 = {Pl(sp.expand(base_area * m2), g)} ÷ {Pl(base_area, g)} = {Pl(m2, g)}", "기둥의 부피 = 밑넓이 × 높이"
    ch, ans = mono_mc(ans_, g)
    total = {"para": m1 * m2, "rect": m1 * m2, "tri": sp.expand(m1 * m2 / 2)}.get(mode)
    if mode in ("para", "rect"):
        what = "높이" if mode == "para" else "가로"
        head = [f"공식: {hint}", f"그래서 {what} = (넓이) ÷ ({'밑변' if mode == 'para' else '세로'}) = {Pl(m1 * m2, g)} ÷ {Pl(m1, g)}"]
        ms, chk = mono_steps([(m1 * m2, 1, "×"), (m1, 1, "÷")], g)
    elif mode == "tri":
        head = [f"공식: {hint}", f"그래서 밑변 = (넓이) × 2 ÷ (높이) = {Pl(total, g)} × 2 ÷ {Pl(m1, g)}"]
        ms, chk = mono_steps([(total, 1, "×"), (sp.Integer(2), 1, "×"), (m1, 1, "÷")], g)
    elif mode == "cuboid":
        head = [f"공식: {hint}", f"가로 × 세로 = {Pl(m1, g)} × {Pl(m3, g)} = {Pl(sp.expand(m1 * m3), g)}",
                f"그래서 높이 = (부피) ÷ (가로 × 세로) = {Pl(sp.expand(m1 * m2 * m3), g)} ÷ {Pl(sp.expand(m1 * m3), g)}"]
        ms, chk = mono_steps([(sp.expand(m1 * m2 * m3), 1, "×"), (sp.expand(m1 * m3), 1, "÷")], g)
    else:
        head = [f"공식: {hint}", f"밑넓이 = (직각삼각형) {Pl(m1, g)} × {Pl(m3, g)} ÷ 2 = {Pl(base_area, g)}",
                f"그래서 높이 = (부피) ÷ (밑넓이) = {Pl(sp.expand(base_area * m2), g)} ÷ {Pl(base_area, g)}"]
        ms, chk = mono_steps([(sp.expand(base_area * m2), 1, "×"), (base_area, 1, "÷")], g)
    need(sp.simplify(chk - ans_) == 0)
    return P("보통", q, ans, choices=ch, hint=hint, explanation=ex, steps=head + ms, detail=[f"답: {ans}"])


@kind(U, "잘못 계산한 식 바로잡기 (단항식)", 12)
def _():
    g = (a, b) if R.random() < 0.5 else (x, y)
    u, v = g
    X = nz(-5, 5) * u ** R.randint(0, 2) * v ** R.randint(0, 2) / (u ** R.randint(0, 2))
    X = sp.simplify(X)
    need(X.free_symbols)
    Y = sp.simplify(Q(F(nz(-6, 6), R.choice([1, 1, 2, 3]))) * u ** R.randint(1, 3) * v ** R.randint(1, 3))
    mul = R.random() < 0.6  # 곱해야 할 것을 나눔 / 나눠야 할 것을 곱함
    A_ = sp.simplify(Y * X if mul else Y / X)
    right = sp.simplify(A_ * X if mul else A_ / X)
    ch, ans = mono_mc(right, g, [A_, Y])
    q = (f"어떤 식에 ${mono(X, g)}$를 곱해야 할 것을 잘못하여 나누었더니 ${mono(Y, g)}$가 되었다. 바르게 계산한 식은?" if mul else
         f"어떤 식을 ${mono(X, g)}$로 나누어야 할 것을 잘못하여 곱하였더니 ${mono(Y, g)}$가 되었다. 바르게 계산한 식은?")
    return P("어려움", q, ans, choices=ch,
             hint="먼저 어떤 식 A를 구해요: " + ("A ÷ X = Y 이므로 A = Y × X" if mul else "A × X = Y 이므로 A = Y ÷ X"),
             explanation=f"A = {mono(A_, g, False)}, 바른 계산 = {mono(right, g, False)}",
             steps=([f"잘못한 계산: A ÷ ({mono(X, g, False)}) = {mono(Y, g, False)}", f"거꾸로 하면 어떤 식 A = ({mono(Y, g, False)}) × ({mono(X, g, False)}) = [[{mono(A_, g, False)}]]".replace("[[", "").replace("]]", ""),
                     f"바른 계산: A × ({mono(X, g, False)}) = ({mono(A_, g, False)}) × ({mono(X, g, False)})"] if mul else
                    [f"잘못한 계산: A × ({mono(X, g, False)}) = {mono(Y, g, False)}", f"거꾸로 하면 어떤 식 A = ({mono(Y, g, False)}) ÷ ({mono(X, g, False)}) = {mono(A_, g, False)}",
                     f"바른 계산: A ÷ ({mono(X, g, False)}) = ({mono(A_, g, False)}) ÷ ({mono(X, g, False)})"])
             + mono_steps([(A_, 1, "×"), (X, 1, "×" if mul else "÷")], g)[0], detail=[f"답: {ans}"])


# ═════════════════════ 3. 다항식의 계산 ═════════════════════
U = "3. 다항식의 계산"


def rlin(g, const=True):
    return nz(-6, 6) * g[0] + nz(-6, 6) * g[1] + (R.randint(-9, 9) if const else 0)


@kind(U, "다항식의 덧셈과 뺄셈", 14)
def _():
    g = (a, b) if R.random() < 0.5 else (x, y)
    if R.random() < 0.6:
        E1, E2, k = rlin(g), rlin(g), R.choice([1, 2, 3, -2, -3, 4])
        Rr = sp.expand(E1 - k * E2)
        need(len(sp.Add.make_args(Rr)) >= 2)
        ch, ans = poly_mc(Rr, g, [sp.expand(E1 + k * E2), sp.expand(E1 - E2)])
        kk = "" if k == 1 else str(k)
        return P("쉬움", f"$({fmt(E1, g)}) - {kk}({fmt(E2, g)})$를 계산하면?", ans, choices=ch,
                 hint="괄호 앞의 수를 모든 항에 곱하고, 빼기 뒤의 괄호는 부호를 모두 바꿔요.",
                 explanation=f"괄호를 풀고 동류항끼리 모으면 {fmt(Rr, g, False)}",
                 steps=combine_steps([E1, -k * E2], g))
    p1, q1, p2, q2 = (F(nz(-5, 5), R.choice([2, 3, 4, 6])) for _ in range(4))
    A_, B_ = p1 + p2, q1 + q2
    need(A_ != 0 and B_ != 0)
    def two(c1, c2):
        return fmt(Q(c1) * g[0] + Q(c2) * g[1], g)
    op = R.choice(["+", "-"])
    A_, B_ = (p1 + p2, q1 + q2) if op == "+" else (p1 - p2, q1 - q2)
    need(A_ != 0 and B_ != 0 and (A_ * B_).denominator <= 36)
    return P("보통", f"$\\left({two(p1, q1)}\\right) {op} \\left({two(p2, q2)}\\right) = p{g[0]} + q{g[1]}$일 때, 수 $p$, $q$에 대하여 $pq$의 값을 구하시오.",
             fpl(A_ * B_), hint=f"{g[0]}는 {g[0]}끼리, {g[1]}는 {g[1]}끼리 통분해서 계산해요.",
             explanation=f"p = {fpl(A_)}, q = {fpl(B_)}, pq = {fpl(A_ * B_)}",
             steps=[f"{g[0]}끼리: {fpl(p1)} {op} ({fpl(p2)}) = [[{fpl(A_)}]]", f"{g[1]}끼리: {fpl(q1)} {op} ({fpl(q2)}) = [[{fpl(B_)}]]",
                    f"pq = ({fpl(A_)}) × ({fpl(B_)}) = [[{fpl(A_ * B_)}]]"])


@kind(U, "이차식의 덧셈과 뺄셈", 14)
def _():
    if R.random() < 0.6:
        E1 = nz(-4, 4) * x ** 2 + R.randint(-8, 8) * x + R.randint(-9, 9)
        E2 = nz(-4, 4) * x ** 2 + R.randint(-8, 8) * x + R.randint(-9, 9)
        k = R.choice([1, 2, 3, 4, -2])
        Rr = sp.expand(E1 - k * E2)
        po = sp.Poly(Rr, x)
        c2, c1, c0 = (po.coeff_monomial(m_) for m_ in (x ** 2, x, 1))
        ask = R.choice(["x²의 계수와 상수항의 합", "x의 계수와 상수항의 합", "x²의 계수와 x의 계수의 곱"])
        val = {"x²의 계수와 상수항의 합": c2 + c0, "x의 계수와 상수항의 합": c1 + c0, "x²의 계수와 x의 계수의 곱": c2 * c1}[ask]
        kk = "" if k == 1 else str(k)
        return P("보통", f"$({fmt(E1, (x,))}) - {kk}({fmt(E2, (x,))})$를 계산했을 때, {ask.replace('x²', '$x^{2}$').replace('x의', '$x$의')}을 구하시오.", int(val),
                 hint="괄호를 풀 때 빼기 뒤의 모든 항의 부호를 바꾸고, 차수가 같은 항끼리 모아요.",
                 explanation=f"계산하면 {fmt(Rr, (x,), False)}", steps=combine_steps([E1, -k * E2], (x,)) + [f"답: [[{val}]]"])
    m, n = R.choice([(2, 3), (3, 2), (2, 4), (4, 3), (3, 6), (2, 5)])
    E1 = R.randint(1, 3) * x ** 2 + R.randint(-5, 5) * x + R.randint(-5, 5)
    E2 = nz(-3, 3) * x ** 2 + R.randint(-5, 5) * x + R.randint(-5, 5)
    S = sp.expand(E1 / m + E2 / n)
    po = sp.Poly(S, x)
    A2, B2, C2 = (rat(po.coeff_monomial(m_)) for m_ in (x ** 2, x, 1))
    val = A2 + B2 + C2
    return P("어려움", f"$\\frac{{{fmt(E1, (x,))}}}{{{m}}} + \\frac{{{fmt(E2, (x,))}}}{{{n}}} = ax^{{2}} + bx + c$일 때, 수 $a$, $b$, $c$에 대하여 $a + b + c$의 값을 구하시오.",
             fpl(val), hint=f"분모 {m}과 {n}의 최소공배수로 통분해요.",
             explanation=f"a = {fpl(A2)}, b = {fpl(B2)}, c = {fpl(C2)} → {fpl(val)}",
             steps=combine_steps([E1 / m, E2 / n], (x,), head="각 항을 분모로 나누면") + [f"a = [[{fpl(A2)}]], b = [[{fpl(B2)}]], c = [[{fpl(C2)}]]", f"a + b + c = [[{fpl(val)}]]"])


@kind(U, "여러 가지 괄호가 있는 계산", 14)
def _():
    g = (a, b) if R.random() < 0.5 else (x, y)
    u, v = g
    A1, B1, C1, D1 = (nz(-5, 5) * R.choice(g) for _ in range(4))
    D2 = nz(-5, 5) * R.choice(g)
    need(len(sp.Add.make_args(sp.expand(D1 + D2))) == 2)
    s1, s2 = R.choice([1, -1]), R.choice([1, -1])
    e = A1 - (B1 - (C1 + s1 * (D1 + D2)))
    Rr = sp.expand(e)
    need(len(sp.Add.make_args(Rr)) == 2)
    def tt(t_):
        return T(t_, g)
    inner = f"({fmt(D1 + D2, g)})"
    q = f"${tt(A1)} - [{tt(B1)} - \\{{{tt(C1)} {'+' if s1 > 0 else '-'} {inner}\\}}]$"
    q = q.replace("- -", "+ ").replace("+ -", "- ")
    # 정답 확인: 화면에 보이는 식을 그대로 다시 계산
    check = A1 - (B1 - (C1 + s1 * (D1 + D2)))
    need(sp.expand(check - Rr) == 0)
    ch, ans = poly_mc(Rr, g)
    return P("보통", f"{q}를 계산하면?", ans, choices=ch, hint="( ) → { } → [ ] 순서로 안쪽 괄호부터 풀어요. 괄호 앞이 -면 부호를 모두 바꿔요.",
             explanation=f"안쪽부터 풀면 {fmt(Rr, g, False)}",
             steps=[f"( ) 먼저: {fmt(C1, g, False)} {'+' if s1 > 0 else '-'} ({fmt(D1 + D2, g, False)}) = {fmt(sp.expand(C1 + s1 * (D1 + D2)), g, False)}",
                    f"{{ }} 다음: {fmt(B1, g, False)} - ({fmt(sp.expand(C1 + s1 * (D1 + D2)), g, False)}) = {fmt(sp.expand(B1 - (C1 + s1 * (D1 + D2))), g, False)}",
                    f"[ ] 마지막: {fmt(A1, g, False)} - ({fmt(sp.expand(B1 - (C1 + s1 * (D1 + D2))), g, False)}) = {fmt(Rr, g, False)}",
                    f"답: {fmt(Rr, g, False)}"])


@kind(U, "여러 가지 괄호: 계수 구하기", 10)
def _():
    c = [nz(-5, 5) for _ in range(6)]
    e = c[0] * a - (c[1] * a ** 2 - (c[2] * a + c[3] * a ** 2 - (c[4] * a ** 2 + c[5] * a)))
    po = sp.Poly(sp.expand(e), a)
    p2, p1 = po.coeff_monomial(a ** 2), po.coeff_monomial(a)
    need(p2 != 0 and p1 != 0)
    def tt(k, e_):
        return T(k * e_, (a,))
    q = (f"${tt(c[0], a)} - [{tt(c[1], a ** 2)} - \\{{{tt(c[2], a)} {sgn(c[3]).split()[0]} {T(abs(c[3]) * a ** 2, (a,))} - ({fmt(c[4] * a ** 2 + c[5] * a, (a,))})\\}}]$")
    return P("어려움", f"{q}를 계산했을 때, $a^{{2}}$의 계수를 $p$, $a$의 계수를 $q$라 하자. $p + q$의 값을 구하시오.", int(p2 + p1),
             hint="안쪽 괄호부터 하나씩 풀고, 괄호 앞이 빼기면 부호를 모두 바꿔요.",
             explanation=f"계산하면 {fmt(sp.expand(e), (a,), False)} → p + q = {p2} + ({p1}) = {p2 + p1}",
             steps=[f"( ) 먼저 풀고 {{ }}: {fmt(sp.expand(c[2] * a + c[3] * a ** 2 - (c[4] * a ** 2 + c[5] * a)), (a,), False)}",
                    f"[ ] 풀기: {fmt(sp.expand(c[1] * a ** 2 - (c[2] * a + c[3] * a ** 2 - (c[4] * a ** 2 + c[5] * a))), (a,), False)}",
                    f"전체: {fmt(sp.expand(e), (a,), False)}", f"p = [[{p2}]], q = [[{p1}]]", f"p + q = [[{p2 + p1}]]"])


@kind(U, "바르게 계산한 식 구하기", 14)
def _():
    A_ = nz(-5, 5) * x ** 2 + R.randint(-6, 6) * x + R.randint(-6, 6)
    Bw = nz(-5, 5) * x ** 2 + R.randint(-6, 6) * x + R.randint(-6, 6)
    mode = R.choice(["add_sub", "sub_add"])
    if mode == "add_sub":
        orig, right = sp.expand(Bw + A_), sp.expand(Bw + 2 * A_)
        q = f"어떤 식에 ${fmt(A_, (x,))}$를 더해야 할 것을 잘못하여 뺐더니 ${fmt(Bw, (x,))}$가 되었다. 바르게 계산한 식은?"
        hint = "먼저 어떤 식을 구해요: (어떤 식) - A = B 이므로 (어떤 식) = B + A"
    else:
        orig, right = sp.expand(Bw - A_), sp.expand(Bw - 2 * A_)
        q = f"어떤 식에서 ${fmt(A_, (x,))}$를 빼야 할 것을 잘못하여 더했더니 ${fmt(Bw, (x,))}$가 되었다. 바르게 계산한 식은?"
        hint = "먼저 어떤 식을 구해요: (어떤 식) + A = B 이므로 (어떤 식) = B - A"
    need(right != 0 and orig != 0)
    ch, ans = poly_mc(right, (x,), [orig, Bw])
    return P("어려움", q, ans, choices=ch, hint=hint,
             explanation=f"어떤 식 = {fmt(orig, (x,), False)}, 바른 계산 = {fmt(right, (x,), False)}",
             steps=(([f"잘못한 계산: (어떤 식) - ({fmt(A_, (x,), False)}) = {fmt(Bw, (x,), False)}",
                      f"그래서 어떤 식 = ({fmt(Bw, (x,), False)}) + ({fmt(A_, (x,), False)})"]
                     + [l_.replace("정리하면", "어떤 식") for l_ in combine_steps([Bw, A_], (x,), head="괄호를 풀면")]
                     + [f"바른 계산: ({fmt(orig, (x,), False)}) + ({fmt(A_, (x,), False)})"]
                     + combine_steps([orig, A_], (x,), head="괄호를 풀면")) if mode == "add_sub" else
                    ([f"잘못한 계산: (어떤 식) + ({fmt(A_, (x,), False)}) = {fmt(Bw, (x,), False)}",
                      f"그래서 어떤 식 = ({fmt(Bw, (x,), False)}) - ({fmt(A_, (x,), False)})"]
                     + [l_.replace("정리하면", "어떤 식") for l_ in combine_steps([Bw, -A_], (x,), head="괄호를 풀면 (빼는 식은 부호가 바뀌어요)")]
                     + [f"바른 계산: ({fmt(orig, (x,), False)}) - ({fmt(A_, (x,), False)})"]
                     + combine_steps([orig, -A_], (x,), head="괄호를 풀면"))),
             detail=[f"답: {ans}"])


@kind(U, "(단항식)×(다항식)", 14)
def _():
    if R.random() < 0.5:
        m1 = nz(-4, 4) * x
        p1 = x + nz(-4, 4) * y + R.randint(-6, 6)
        m2 = nz(-6, 6) * R.choice([x, y])
        p2 = nz(-4, 4) * x + nz(-4, 4) * y + R.randint(-5, 5)
        e1, e2 = sp.expand(m1 * p1), sp.expand(p2 * m2)
        ca = sp.Poly(e1, x, y).coeff_monomial(x ** 2)
        cb = sp.Poly(e2, x, y).coeff_monomial(x * y)
        need(ca != 0 and cb != 0)
        return P("보통", f"${T(m1)}({fmt(p1, (x, y))})$를 전개한 식의 $x^{{2}}$의 계수를 $a$, $({fmt(p2, (x, y))}) \\times {paren_t(m2)}$를 전개한 식의 $xy$의 계수를 $b$라 할 때, $ab$의 값을 구하시오.",
                 int(ca * cb), hint="분배법칙으로 괄호 안의 모든 항에 곱해요.",
                 explanation=f"{Pl(e1)} → a = {ca}, {Pl(e2)} → b = {cb}, ab = {ca * cb}",
                 steps=[f"분배법칙: {Pl(m1)}({fmt(p1, (x, y), False)}) = {Pl(e1)}", f"x²의 계수 a = [[{ca}]]",
                        f"분배법칙: ({fmt(p2, (x, y), False)}) × {Pl(m2)} = {Pl(e2)}", f"xy의 계수 b = [[{cb}]]", f"ab = [[{ca * cb}]]"])
    st, info = [], {}
    for _ in range(5):
        g = R.choice([(a, b), (x, y)])
        mm = nz(-4, 4) * R.choice(g) * R.choice([1, 1, g[0], g[1]])
        pp = nz(-4, 4) * R.choice(g) + nz(-5, 5) * R.choice([1, g[1], g[0]])
        need(len(sp.Add.make_args(sp.expand(pp))) == 2)
        right = sp.expand(mm * pp)
        ok = R.random() < 0.5
        shown = right if ok else R.choice(poly_wrongs(right, g))
        key = f"${T(mm, g)}({fmt(pp, g)}) = {fmt(shown, g)}$"
        info[key] = (mm, pp, right, g, sp.expand(shown - right) == 0)
        st.append((key, info[key][4]))
    want = R.random() < 0.5
    ch, ans = pick(st, want)
    mm, pp, right, g, _ok = info[ch[MARKS.index(ans)]]

    def judge(c_):
        mm_, pp_, right_, g_, ok_ = info[c_]
        return ok_, ("바르게 전개했어요" if ok_ else f"바르게 전개하면 {fmt(right_, g_, False)}")
    return P("보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="괄호 안의 각 항에 빠짐없이 곱하고, 부호를 꼭 확인해요.",
             explanation="각 식을 분배법칙으로 전개해서 오른쪽과 비교해요.",
             steps=["분배법칙: 괄호 밖의 단항식을 괄호 안의 모든 항에 하나씩 곱해요 (부호까지)",
                    f"정답 보기 {Pl(mm, g)}({fmt(pp, g, False)})를 항마다 곱하면:"] + term_lines(pp, "×", mm, g)
                   + [f"모으면: {fmt(right, g, False)}"],
             detail=["보기를 하나씩 전개해 봐요 (⭕ = 옳은 식):"] + judge_lines(ch, judge) + [f"그래서 {'옳은' if want else '옳지 않은'} 것은 {ans}"])


@kind(U, "(다항식)÷(단항식)", 14)
def _():
    g = (x, y) if R.random() < 0.5 else (a, b)
    u, v = g
    ans_ = nz(-8, 8) * u + nz(-8, 8) * v + nz(-8, 8)
    dv = Q(F(nz(-5, 5), R.choice([1, 2, 3, 4, 5]))) * u * v
    num_ = sp.expand(ans_ * dv)
    need(all(rat(c_).denominator == 1 for c_ in sp.Poly(num_, *g).coeffs()))
    po = sp.Poly(ans_, *g)
    A1, B1, C1 = po.coeff_monomial(u), po.coeff_monomial(v), po.coeff_monomial(1)
    combo = R.choice([("a + b + c", A1 + B1 + C1), ("a - b - c", A1 - B1 - C1), ("abc", A1 * B1 * C1), ("a + b - c", A1 + B1 - C1)])
    div_lines = ["(다항식) ÷ (단항식): 각 항을 따로 나눠요" + (" (분수로 나누면 역수를 곱해요)" if rat(dv.as_coeff_Mul()[0]).denominator > 1 else "")] + term_lines(num_, "÷", dv, g)
    if R.random() < 0.6:
        return P("보통", f"$({fmt(num_, g)}) \\div \\left({mono(dv, g)}\\right) = a{u} + b{v} + c$일 때, 수 $a$, $b$, $c$에 대하여 ${combo[0]}$의 값을 구하시오.",
                 int(combo[1]), hint="분수로 나눌 때는 역수를 곱해요. 각 항을 따로 나눠요.",
                 explanation=f"계산하면 {fmt(ans_, g, False)} → a = {A1}, b = {B1}, c = {C1}",
                 steps=div_lines + [f"a = [[{A1}]], b = [[{B1}]], c = [[{C1}]]", f"{combo[0]} = [[{combo[1]}]]"])
    ch, ans = poly_mc(ans_, g)
    return P("보통", f"$({fmt(num_, g)}) \\div \\left({mono(dv, g)}\\right)$를 계산하면?", ans, choices=ch,
             hint="각 항을 단항식으로 나눠요. 분수로 나누면 역수를 곱해요.", explanation=f"계산하면 {fmt(ans_, g, False)}",
             steps=div_lines + [f"정리하면: {fmt(ans_, g, False)}"])


@kind(U, "식의 값", 14)
def _():
    p1, q1 = nz(-6, 6) * x * y ** 2, nz(-6, 6) * x ** 2
    d1 = nz(-3, 3) * x
    p2, q2 = nz(-6, 6) * x * y, nz(-6, 6) * x ** 2 * y
    d2 = nz(-3, 3) * y
    E = (p1 + q1) / d1 - (p2 + q2) / d2
    S = sp.expand(sp.simplify(E))
    need(S.is_polynomial(x, y) and all(rat(c_).denominator == 1 for c_ in sp.Poly(S, x, y).coeffs()))
    xv, yv = nz(-3, 3), nz(-3, 3)
    val_ = S.subs({x: xv, y: yv})
    need(abs(val_) <= 99)
    return P("보통", f"$x = {xv}$, $y = {yv}$일 때, $\\frac{{{fmt(p1 + q1, (x, y))}}}{{{T(d1)}}} - \\frac{{{fmt(p2 + q2, (x, y))}}}{{{T(d2)}}}$의 값을 구하시오.", int(val_),
             hint="먼저 식을 간단히 한 뒤 수를 대입해요. 음수는 괄호를 씌워요.",
             explanation=f"간단히 하면 {Pl(S)}, 대입하면 {val_}",
             steps=[f"앞의 분수: ({fmt(p1 + q1, (x, y), False)}) ÷ ({Pl(d1)}) = {Pl(sp.expand((p1 + q1) / d1))}",
                    f"뒤의 분수: ({fmt(p2 + q2, (x, y), False)}) ÷ ({Pl(d2)}) = {Pl(sp.expand((p2 + q2) / d2))}",
                    f"빼면: {Pl(S)}", f"x = {xv}, y = {yv}를 넣어요 (음수는 괄호를 씌워요)", subst_line(S, {x: xv, y: yv}, (x, y))])


@kind(U, "덧셈·뺄셈·곱셈·나눗셈이 혼합된 계산", 12)
def _():
    k = R.choice([2, 3])
    q1, q2 = nz(-4, 4), nz(-4, 4)
    num1 = sp.expand((q1 * x + q2 * y) * k ** 2 * x ** 2)
    m = nz(-4, 4)
    p2 = nz(-4, 4) * x * y + nz(-3, 3) * y ** 2
    E = num1 / (k * x) ** 2 + p2 * (Q(m) / y)
    S = sp.expand(sp.simplify(E))
    need(len(sp.Add.make_args(S)) == 2)
    ch, ans = poly_mc(S, (x, y))
    mtx = f"\\left({'-' if m < 0 else ''}\\frac{{{abs(m)}}}{{y}}\\right)"
    return P("어려움", f"$({fmt(num1, (x, y))}) \\div ({k}x)^{{2}} + ({fmt(p2, (x, y))}) \\times {mtx}$를 계산하면?", ans, choices=ch,
             hint="거듭제곱 → 곱셈·나눗셈 → 덧셈·뺄셈 순서로 해요.",
             explanation=f"앞: {Pl(sp.expand(num1 / (k * x) ** 2))}, 뒤: {Pl(sp.expand(p2 * m / y))} → {Pl(S)}",
             steps=[f"순서: 거듭제곱 → 곱셈·나눗셈 → 덧셈·뺄셈", f"거듭제곱 먼저: ({k}x)² = [[{k * k}]]x²",
                    f"앞부분: ({fmt(num1, (x, y), False)}) ÷ {k * k}x², 각 항을 나눠요"] + term_lines(num1, "÷", k * k * x ** 2, (x, y))
                   + [f"뒷부분: ({fmt(p2, (x, y), False)}) × ({m}/y), 각 항에 곱해요"] + term_lines(p2, "×", Q(m) / y, (x, y))
                   + [f"두 부분을 더하고 동류항끼리 모으면: {Pl(S)}"], detail=[f"답: {ans}"])


@kind(U, "□ 안에 알맞은 식 (다항식)", 12)
def _():
    g = (a, b) if R.random() < 0.5 else (x, y)
    u, v = g
    L = Q(F(nz(-5, 5), R.choice([1, 2, 3]))) * u * v ** R.randint(0, 1)
    BX = nz(-6, 6) * u ** 2 + nz(-6, 6) * u * v
    Rr = sp.expand(L * BX)
    need(all(rat(c_).denominator == 1 for c_ in sp.Poly(Rr, *g).coeffs()))
    if R.random() < 0.5:
        q = f"$\\left({mono(L, g)}\\right) \\times \\square = {fmt(Rr, g)}$일 때, $\\square$ 안에 알맞은 식은?"
        ex = f"□ = ({fmt(Rr, g, False)}) ÷ ({mono(L, g, False)}) = {fmt(BX, g, False)}"
    else:
        Rr, BX = BX, Rr
        q = f"$\\square \\div \\left({mono(L, g)}\\right) = {fmt(Rr, g)}$일 때, $\\square$ 안에 알맞은 식은?"
        ex = f"□ = ({fmt(Rr, g, False)}) × ({mono(L, g, False)}) = {fmt(BX, g, False)}"
    ch, ans = poly_mc(BX, g)
    if "÷ (" in ex.split(" = ")[1] or ex.count("÷"):
        lines = [f"□ = ({fmt(Rr, g, False)}) ÷ ({mono(L, g, False)}) (곱한 것은 나눠서 구해요)", "다항식 ÷ 단항식: 각 항을 따로 나눠요"] + term_lines(Rr, "÷", L, g)
    else:
        lines = [f"□ = ({fmt(Rr, g, False)}) × ({mono(L, g, False)}) (나눈 것은 곱해서 구해요)", "분배법칙: 각 항에 곱해요"] + term_lines(Rr, "×", L, g)
    return P("보통", q, ans, choices=ch, hint="곱한 것은 나누고, 나눈 것은 곱해서 □를 구해요.", explanation=ex,
             steps=lines, detail=[f"정리하면: □ = {fmt(BX, g, False)} → 답 {ans}"])


@kind(U, "다항식: 도형에의 활용", 14)
def _():
    mode = R.choice(["trap", "tri", "cuboid", "shade"])
    g = (a, b) if R.random() < 0.5 else (x, y)
    u, v = g
    if mode == "trap":
        top, bot, hgt = R.randint(1, 5) * v, R.randint(2, 8) * u * v + nz(-4, 4) * v, R.randint(1, 4) * u ** 2 * v
        need(sp.Poly(sp.expand(top + bot), *g).coeff_monomial(v) != 0)
        S = sp.expand((top + bot) * hgt / 2)
        need(all(rat(c_).denominator == 1 for c_ in sp.Poly(S, *g).coeffs()))
        q = f"윗변의 길이가 ${T(top, g)}$, 아랫변의 길이가 ${fmt(bot, g)}$, 높이가 ${T(hgt, g)}$인 사다리꼴의 넓이는?"
        ex, hint = f"({Pl(top, g)} + {fmt(bot, g, False)}) × {Pl(hgt, g)} ÷ 2 = {fmt(S, g, False)}", "사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2"
        extra = [sp.expand((top + bot) * hgt), sp.expand(bot * hgt / 2)]
    elif mode == "tri":
        h_ = R.randint(2, 6) * u
        base = R.randint(1, 5) * u + R.randint(1, 5) * v
        S_ = sp.expand(base * h_ / 2)
        need(all(rat(c_).denominator == 1 for c_ in sp.Poly(S_, *g).coeffs()))
        q = f"높이가 ${T(h_, g)}$인 삼각형의 넓이가 ${fmt(S_, g)}$일 때, 이 삼각형의 밑변의 길이는?"
        S, ex, hint = base, f"({fmt(S_, g, False)}) × 2 ÷ {Pl(h_, g)} = {fmt(base, g, False)}", "삼각형의 넓이 = 밑변 × 높이 ÷ 2 이므로 밑변 = 넓이 × 2 ÷ 높이"
        extra = [sp.expand(S_ / h_)]
    elif mode == "cuboid":
        w_, l_ = R.randint(1, 4) * u, R.randint(1, 4) * v
        h_ = R.randint(1, 5) * u + nz(-4, 4) * v
        need(sp.Poly(h_, *g).coeff_monomial(u) != 0)
        vol = sp.expand(w_ * l_ * h_)
        q = f"가로의 길이가 ${T(w_, g)}$, 세로의 길이가 ${T(l_, g)}$인 직육면체의 부피가 ${fmt(vol, g)}$일 때, 이 직육면체의 높이는?"
        S, ex, hint = h_, f"({fmt(vol, g, False)}) ÷ ({Pl(w_ * l_, g)}) = {fmt(h_, g, False)}", "직육면체의 부피 = 가로 × 세로 × 높이"
        extra = []
    else:
        W, H = R.randint(3, 8) * u, R.randint(3, 8) * v
        eb, fc = R.randint(1, 3) * u, R.randint(1, 3) * v
        need(sp.Poly(W - eb, *g).LC() > 0 and sp.Poly(H - fc, *g).LC() > 0)
        t_ade, t_ebf, t_fcd = (W - eb) * H / 2, eb * (H - fc) / 2, fc * W / 2
        S = sp.expand(W * H - t_ade - t_ebf - t_fcd)
        need(S != 0 and all(rat(c_).denominator == 1 for c_ in sp.Poly(S, *g).coeffs()))
        q = (f"가로 ${T(W, g)}$, 세로 ${T(H, g)}$인 직사각형 ABCD (A 왼쪽 위, B 오른쪽 위, C 오른쪽 아래, D 왼쪽 아래)에서 "
             f"변 AB 위에 $\\overline{{EB}} = {T(eb, g)}$인 점 E, 변 BC 위에 $\\overline{{FC}} = {T(fc, g)}$인 점 F를 잡을 때, 삼각형 DEF의 넓이는? (공책에 그림을 그려 봐요)")
        ex = f"{Pl(W * H, g)} - {Pl(sp.expand(t_ade), g)} - {Pl(sp.expand(t_ebf), g)} - {Pl(sp.expand(t_fcd), g)} = {Pl(S, g)}"
        hint = "(삼각형 DEF) = (직사각형) - (삼각형 ADE) - (삼각형 EBF) - (삼각형 FCD)"
        extra = [sp.expand(W * H - S)]
        ch, ans = mono_mc(S, g, extra)
        rect = sp.expand(W * H)
        lines = ["그림: 직사각형 안의 삼각형 DEF = 직사각형에서 둘레의 직각삼각형 3개를 뺀 것",
                 f"직사각형 ABCD: {Pl(W, g)} × {Pl(H, g)} = {Pl(rect, g)}",
                 f"AE = {Pl(W, g)} - {Pl(eb, g)} = {Pl(sp.expand(W - eb), g)}, BF = {Pl(H, g)} - {Pl(fc, g)} = {Pl(sp.expand(H - fc), g)}",
                 f"삼각형 ADE: AE × AD ÷ 2 = {Pl(sp.expand(W - eb), g)} × {Pl(H, g)} ÷ 2 = [[{rat(sp.expand(t_ade).as_coeff_Mul()[0])}]]{Pl(sp.expand(t_ade).as_coeff_Mul()[1], g)}",
                 f"삼각형 EBF: EB × BF ÷ 2 = {Pl(eb, g)} × {Pl(sp.expand(H - fc), g)} ÷ 2 = [[{rat(sp.expand(t_ebf).as_coeff_Mul()[0])}]]{Pl(sp.expand(t_ebf).as_coeff_Mul()[1], g)}",
                 f"삼각형 FCD: FC × CD ÷ 2 = {Pl(fc, g)} × {Pl(W, g)} ÷ 2 = [[{rat(sp.expand(t_fcd).as_coeff_Mul()[0])}]]{Pl(sp.expand(t_fcd).as_coeff_Mul()[1], g)}",
                 f"삼각형 DEF = {Pl(rect, g)} - {Pl(sp.expand(t_ade), g)} - {Pl(sp.expand(t_ebf), g)} - {Pl(sp.expand(t_fcd), g)} = [[{rat(S.as_coeff_Mul()[0])}]]{Pl(S.as_coeff_Mul()[1], g)}"]
        return P("어려움", q, ans, choices=ch, hint=hint, explanation=ex, steps=lines, detail=[f"답: {ans}"])
    ch, ans = poly_mc(S, g, extra)
    if mode == "trap":
        tb = sp.expand(top + bot)
        lines = [f"공식: {hint}", f"윗변 + 아랫변 = {Pl(top, g)} + ({fmt(bot, g, False)}) = {fmt(tb, g, False)}",
                 f"높이를 곱하고 2로 나누면: ({fmt(tb, g, False)}) × {Pl(hgt, g)} ÷ 2 = ({fmt(tb, g, False)}) × {mono(hgt / 2, g, False)}",
                 "분배법칙: 각 항에 곱해요"] + term_lines(tb, "×", hgt / 2, g)
    elif mode == "tri":
        lines = [f"공식: {hint}", f"밑변 = ({fmt(S_, g, False)}) × 2 ÷ {Pl(h_, g)} = ({fmt(S_, g, False)}) ÷ {mono(h_ / 2, g, False)}",
                 "각 항을 따로 나눠요"] + term_lines(S_, "÷", h_ / 2, g)
    else:
        lines = [f"공식: {hint}", f"가로 × 세로 = {Pl(w_, g)} × {Pl(l_, g)} = {Pl(sp.expand(w_ * l_), g)}",
                 f"높이 = (부피) ÷ (가로 × 세로) = ({fmt(vol, g, False)}) ÷ {Pl(sp.expand(w_ * l_), g)}",
                 "각 항을 따로 나눠요"] + term_lines(vol, "÷", sp.expand(w_ * l_), g)
    return P("보통", q, ans, choices=ch, hint=hint, explanation=ex, steps=lines, detail=[f"정리하면: {fmt(S, g, False)} → 답 {ans}"])


# ═════════════════════ 4. 일차부등식 ═════════════════════
U = "4. 일차부등식"
REL = {">": ">", "<": "<", ">=": "\\geq", "<=": "\\leq"}
FLIP = {">": "<", "<": ">", ">=": "<=", "<=": ">="}
PL = {">": ">", "<": "<", ">=": "≥", "<=": "≤"}
WORD = {">": "보다 크다", "<": "보다 작다", ">=": " 이상이다", "<=": " 이하이다"}
WORD2 = {">": "보다 크다", "<": "보다 작다", ">=": "보다 작지 않다", "<=": "보다 크지 않다"}


def holds(l_, rel, r_):
    return {">": l_ > r_, "<": l_ < r_, ">=": l_ >= r_, "<=": l_ <= r_}[rel]


def sol_text(sol) -> str:
    lo, hi = sol.inf, sol.sup
    def q_(v):
        v = sp.Rational(v)
        return str(v.p) if v.q == 1 else f"{v.p}/{v.q}"
    if lo == -sp.oo:
        return f"x {'<' if sol.right_open else '≤'} {q_(hi)}"
    return f"x {'>' if sol.left_open else '≥'} {q_(lo)}"


@kind(U, "부등식으로 나타내기", 14)
def _():
    rel = R.choice(list(REL))
    kind_ = R.randint(0, 3)
    if kind_ == 0:
        p_, q_, c = R.randint(2, 6), R.randint(1, 9), R.randint(5, 30)
        s = f"어떤 수 $x$의 {p_}배에서 {q_}를 뺀 값은 {c}{WORD2[rel]}."
        right = f"${p_}x - {q_} {REL[rel]} {c}$"
        wr = [f"${p_}x - {q_} {REL[FLIP[rel]]} {c}$", f"${p_}(x - {q_}) {REL[rel]} {c}$", f"${q_} - {p_}x {REL[rel]} {c}$", f"${p_}x + {q_} {REL[rel]} {c}$"]
    elif kind_ == 1:
        p_, q_, c = R.choice([500, 700, 800, 1200, 1500]), R.choice([1000, 2000, 2500, 3000]), R.choice([10000, 15000, 20000])
        rel = R.choice(["<=", "<"])
        s = f"한 개에 {p_}원인 과자 $x$개와 {q_}원짜리 음료수 한 개의 값은 {c}원 {'이하이다' if rel == '<=' else '미만이다'}."
        right = f"${p_}x + {q_} {REL[rel]} {c}$"
        wr = [f"${p_}x + {q_} {REL[FLIP[rel]]} {c}$", f"${p_} + {q_}x {REL[rel]} {c}$", f"$({p_} + {q_})x {REL[rel]} {c}$", f"${p_}x - {q_} {REL[rel]} {c}$"]
    elif kind_ == 2:
        v, t_ = R.choice([3, 4, 5, 60, 80]), R.randint(2, 5)
        rel = R.choice(["<", "<="])
        s = f"$x$ km의 거리를 시속 {v} km로 갈 때 걸리는 시간은 {t_}시간{'미만이다' if rel == '<' else ' 이하이다'}."
        right = f"$\\frac{{x}}{{{v}}} {REL[rel]} {t_}$"
        wr = [f"${v}x {REL[rel]} {t_}$", f"$\\frac{{{v}}}{{x}} {REL[rel]} {t_}$", f"$\\frac{{x}}{{{v}}} {REL[FLIP[rel]]} {t_}$", f"$x + {v} {REL[rel]} {t_}$"]
    else:
        p_, q_, r_ = R.randint(2, 5), R.randint(1, 9), R.randint(2, 6)
        need(p_ != r_)
        s = f"$x$의 {p_}배에 {q_}를 더한 수는 $x$의 {r_}배{WORD[rel]}."
        right = f"${p_}x + {q_} {REL[rel]} {r_}x$"
        wr = [f"${p_}x + {q_} {REL[FLIP[rel]]} {r_}x$", f"${p_}(x + {q_}) {REL[rel]} {r_}x$", f"${p_}x {REL[rel]} {r_}x + {q_}$", f"${p_} + {q_}x {REL[rel]} {r_}x$"]
    ch, ans = mc(right, wr)
    word_rel = {">": "크다 / 초과 → >", "<": "작다 / 미만 → <", ">=": "작지 않다 / 이상 → ≥", "<=": "크지 않다 / 이하 → ≤"}[rel]
    if kind_ == 0:
        parts = [f"'어떤 수 x의 {p_}배' → [[{p_}]]x", f"'{q_}를 뺀 값' → {p_}x - [[{q_}]]", f"'{c}' 쪽 관계: {word_rel}"]
    elif kind_ == 1:
        parts = [f"'한 개에 {p_}원인 과자 x개'의 값 → [[{p_}]]x원", f"'{q_}원짜리 음료수 한 개'를 더하면 → {p_}x + [[{q_}]]", f"'{c}원 {'이하' if rel == '<=' else '미만'}' → {PL[rel]} {c}"]
    elif kind_ == 2:
        parts = ["시간 = 거리 ÷ 속력", f"x km를 시속 {v} km로 → 시간은 x/[[{v}]]시간", f"'{t_}시간 {'미만' if rel == '<' else '이하'}' → {PL[rel]} {t_}"]
    else:
        parts = [f"'x의 {p_}배에 {q_}를 더한 수' → {p_}x + [[{q_}]]", f"'x의 {r_}배' → [[{r_}]]x", f"관계: {word_rel}"]
    return P("쉬움", f"다음 문장을 부등식으로 나타내면? \"{s}\"", ans, choices=ch,
             hint="크지 않다 = 이하(≤), 작지 않다 = 이상(≥), 미만은 <, 초과는 >",
             explanation="문장을 앞에서부터 차례로 식으로 옮겨요: " + right.strip("$").replace("\\geq", "≥").replace("\\leq", "≤"),
             steps=["문장을 앞에서부터 조각내서 식으로 옮겨요"] + parts,
             detail=["부등호 말 정리: 초과 >, 미만 <, 이상(작지 않다) ≥, 이하(크지 않다) ≤",
                     f"합치면: {plain_tex(right)}", f"그래서 답은 {ans}"])


@kind(U, "부등식의 해", 12)
def _():
    xv = nz(-4, 5)
    cand = []
    info = {}
    for _ in range(40):
        c1, c0, rhs = nz(-5, 5), R.randint(-8, 8), R.randint(-10, 10)
        rel = R.choice(list(REL))
        item = f"${lin(c1, 0, c0)} {REL[rel]} {rhs}$"
        cand.append((item, holds(c1 * xv + c0, rel, rhs)))
        info[item] = (c1, c0, rel, rhs)
    want = R.random() < 0.6
    ch, ans = pick(cand, want)
    def judge(c_):
        c1, c0, rel, rhs = info[c_]
        lv = c1 * xv + c0
        ok = holds(lv, rel, rhs)
        return ok == want, f"왼쪽 = {c1}×({xv}) {sgn(c0)} = {lv} → {lv} {PL[rel]} {rhs}는 {'참' if ok else '거짓'}"
    tgt = info[ch[MARKS.index(ans)]]
    return P("쉬움", f"다음 부등식 중 $x = {xv}$가 해{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint=f"x 자리에 {xv}를 넣어서 부등식이 참이 되는지 하나씩 확인해요.",
             explanation=f"x = {xv}를 대입해서 참·거짓을 따져 보면 답은 {ans}예요.",
             steps=["부등식의 해 = 넣었을 때 부등식이 참이 되는 x의 값",
                    f"정답 보기 {plain_tex(ch[MARKS.index(ans)])}에 x = {xv}를 넣으면 왼쪽 = {tgt[0]}×({xv}) {sgn(tgt[1])} = [[{tgt[0] * xv + tgt[1]}]]",
                    f"{tgt[0] * xv + tgt[1]} {PL[tgt[2]]} {tgt[3]} → {'참' if want else '거짓'}"],
             detail=[f"보기마다 x = {xv}를 넣어 봐요 (⭕ = 문제의 조건에 맞음):"] + judge_lines(ch, judge) + [f"그래서 답은 {ans}"])


@kind(U, "부등식의 성질", 14)
def _():
    k = R.randint(2, 6)
    big = R.random() < 0.5  # a > b 또는 a < b
    base = ">" if big else "<"
    opp = "<" if big else ">"
    keep, flip_ = "방향 그대로", "방향이 바뀌어요"
    st3 = [(f"${k}a {base} {k}b$", True, f"양수 {k}를 곱함 → {keep}"), (f"$-{k}a {opp} -{k}b$", True, f"음수 -{k}를 곱함 → {flip_}"),
           (f"$a - {k} {base} b - {k}$", True, f"{k}를 뺌 → {keep}"), (f"$\\frac{{a}}{{{k}}} {base} \\frac{{b}}{{{k}}}$", True, f"양수 {k}로 나눔 → {keep}"),
           (f"${k} - a {opp} {k} - b$", True, f"-1을 곱하고 {k}를 더함 → {flip_}"), (f"$-\\frac{{a}}{{{k}}} {opp} -\\frac{{b}}{{{k}}}$", True, f"음수 -{k}로 나눔 → {flip_}"),
           (f"$-\\frac{{a}}{{{k}}} {base} -\\frac{{b}}{{{k}}}$", False, f"음수 -{k}로 나눴는데 방향이 그대로예요 (바뀌어야 해요)"),
           (f"$-{k}a + 1 {base} -{k}b + 1$", False, f"음수 -{k}를 곱했는데 방향이 그대로예요"), (f"$a + {k} {opp} b + {k}$", False, f"{k}를 더했을 뿐인데 방향이 바뀌었어요"),
           (f"${k} - 2a {base} {k} - 2b$", False, "음수 -2를 곱했는데 방향이 그대로예요"), (f"${k}a - 1 {opp} {k}b - 1$", False, f"양수 {k}를 곱하고 1을 뺐는데 방향이 바뀌었어요"),
           (f"$-{k}a {base} -{k}b$", False, f"음수 -{k}를 곱했는데 방향이 그대로예요")]
    st = [(t_, v_) for t_, v_, _ in st3]
    why = {t_: (v_, w_) for t_, v_, w_ in st3}
    want = R.random() < 0.5
    ch, ans = pick(st, want)
    def judge(c_):
        v_, w_ = why[c_]
        return v_, w_
    return P("보통", f"$a {base} b$일 때, 다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="양변에 같은 음수를 곱하거나 나누면 부등호 방향이 바뀌어요. 더하고 빼는 것은 그대로예요.",
             explanation="음수를 곱하거나 나눈 식은 부등호 방향이 바뀌고, 나머지는 그대로예요.",
             steps=["부등식의 성질 세 가지", "양변에 같은 수를 더하거나 빼도 방향은 [[그대로]]", "양변에 같은 양수를 곱하거나 나눠도 방향은 그대로",
                    "양변에 같은 음수를 곱하거나 나누면 방향이 [[바뀐다]]"],
             detail=[f"a {PL[base]} b에서 시작해서 보기를 하나씩 확인해요 (⭕ = 옳은 식):"] + judge_lines(ch, judge) + [f"그래서 {'옳은' if want else '옳지 않은'} 것은 {ans}"])


@kind(U, "식의 값의 범위", 12)
def _():
    lo = R.randint(-5, 2)
    hi = lo + R.randint(2, 6)
    lor, hir = R.choice(["<", "<="]), R.choice(["<", "<="])
    m, c = nz(-4, 4, (0, 1)), R.randint(-6, 6)
    v1, v2 = m * lo + c, m * hi + c
    E = lin(m, 0, c)
    if m > 0:
        right = f"${v1} {REL[lor]} {E} {REL[hir]} {v2}$"
        wr = [f"${v1} {REL[hir]} {E} {REL[lor]} {v2}$" if lor != hir else f"${v1} {REL[FLIP[FLIP[lor]]].replace('<', '>')} {E}$",
              f"${v1 - 1} {REL[lor]} {E} {REL[hir]} {v2 - 1}$", f"${v1 + 1} {REL[lor]} {E} {REL[hir]} {v2 + 1}$",
              f"${m * lo} {REL[lor]} {E} {REL[hir]} {m * hi}$", f"${v1} {REL[lor]} {E} {REL[hir]} {v2 + 2}$", f"${-v2} {REL[lor]} {E} {REL[hir]} {-v1}$"]
    else:
        right = f"${v2} {REL[hir]} {E} {REL[lor]} {v1}$"
        wr = [f"${v1} {REL[lor]} {E} {REL[hir]} {v2}$", f"${v2} {REL[lor]} {E} {REL[hir]} {v1}$" if lor != hir else f"${v2 - 2} {REL[hir]} {E} {REL[lor]} {v1}$",
              f"${-v1} {REL[hir]} {E} {REL[lor]} {-v2}$", f"${v2 + 1} {REL[hir]} {E} {REL[lor]} {v1 + 1}$", f"${v2 - 1} {REL[hir]} {E} {REL[lor]} {v1 - 1}$"]
    ch, ans = mc(right, wr)
    return P("보통", f"${lo} {REL[lor]} x {REL[hir]} {hi}$일 때, ${E}$의 값의 범위는?", ans, choices=ch,
             hint="각 변에 같은 수를 곱하고 더해요. 음수를 곱하면 부등호 방향이 모두 바뀌어요!",
             explanation=f"x = {lo} → {v1}, x = {hi} → {v2}" + (" (음수를 곱해서 방향이 바뀜)" if m < 0 else ""),
             steps=[f"{lo} {PL[lor]} x {PL[hir]} {hi}의 각 변에 {m}을 곱해요" + (" (음수라 부등호 방향이 모두 바뀌어요)" if m < 0 else ""),
                    (f"{m * lo} {PL[lor]} {m}x {PL[hir]} {m * hi}" if m > 0 else f"{m * hi} {PL[hir]} {m}x {PL[lor]} {m * lo}"),
                    f"각 변에 {c}를 더해요 (방향 그대로)",
                    f"x = {lo} 쪽 끝: {m}×({lo}) {sgn(c)} = [[{v1}]]", f"x = {hi} 쪽 끝: {m}×({hi}) {sgn(c)} = [[{v2}]]"],
             detail=[f"작은 수를 왼쪽에 쓰면: {plain_tex(right)}", "끝에 등호(=)가 있는지는 원래 부등식의 그 끝을 따라가요", f"그래서 답은 {ans}"])


@kind(U, "일차부등식 찾기", 8)
def _():
    pool = []
    for _ in range(12):
        p_, q_, r_ = nz(-5, 5), R.randint(-6, 6), R.randint(-6, 6)
        pool += [(f"${lin(p_, 0, q_)} > {lin(p_, 0, r_)}$", False, "정리하면 x가 없어져요 → 일차부등식이 아니에요"),
                 (f"$x^{{2}} + {lin(p_, 0, 0)} > x^{{2}} {sgn(q_ or 1)}$".replace("+ -", "- "), True, "x²이 양쪽에서 지워지고 일차식만 남아요 → 일차부등식"),
                 (f"${lin(p_, 0, q_)} \\leq {r_}$", True, "(일차식) ≤ (수) 꼴 → 일차부등식"),
                 (f"${lin(p_, 0, q_)} = {lin(r_ or 2, 0, 0)}$", False, "= 이 있는 등식이에요 (방정식)"),
                 (f"$x^{{2}} + {abs(q_) + 1} > {lin(p_, 0, 0)}$", False, "x²이 남아요 → 일차가 아니에요"),
                 (f"$\\frac{{{abs(p_)}}}{{x}} > {r_}$", False, "x가 분모에 있어요 → 일차식이 아니에요"),
                 (f"${abs(p_)}(x {sgn(q_)}) \\geq {abs(p_)}x$", False, "괄호를 풀면 x가 지워져요 → 일차부등식이 아니에요")]
    pool = [(s.replace("x^{2} + -", "x^{2} - "), t_, w_) for s, t_, w_ in pool]
    why = {s_: (t_, w_) for s_, t_, w_ in pool}
    want = R.random() < 0.6
    ch, ans = pick([(s_, t_) for s_, t_, _ in pool], want)
    def judge(c_):
        return why[c_]
    return P("보통", f"다음 중 일차부등식{'인' if want else '이 아닌'} 것은?", ans, choices=ch,
             hint="모든 항을 왼쪽으로 옮겨 정리했을 때 (x에 대한 일차식) > 0 꼴(또는 <, ≥, ≤)이 되는지 봐요.",
             explanation="정리했을 때 x²이 지워지고 일차식만 남으면 일차부등식, x가 없어지거나 등식이면 아니에요.",
             steps=["오른쪽의 모든 항을 [[왼쪽]]으로 옮겨 정리해요", "(x에 대한 일차식) > 0, < 0, ≥ 0, ≤ 0 꼴이면 [[일차부등식]]",
                    "x가 지워지거나, x²이 남거나, x가 분모에 있거나, 등식(=)이면 일차부등식이 아니에요"],
             detail=["보기를 하나씩 정리해 봐요 (⭕ = 일차부등식):"] + judge_lines(ch, lambda c_: why[c_]) + [f"그래서 답은 {ans}"])


@kind(U, "일차부등식의 풀이", 14)
def _():
    k = R.randint(-5, 6)
    c1, c2 = nz(-6, 6), nz(-6, 6)
    need(c1 != c2)
    d0 = R.randint(-9, 9)
    rel = R.choice(list(REL))
    e0 = (c1 - c2) * k + d0
    coef = c1 - c2
    res_rel = rel if coef > 0 else FLIP[rel]
    right = f"$x {REL[res_rel]} {k}$"
    wr = [f"$x {REL[FLIP[res_rel]]} {k}$", f"$x {REL[res_rel]} {-k}$", f"$x {REL[res_rel]} {k + 1}$", f"$x {REL[FLIP[res_rel]]} {-k}$",
          f"$x {REL[res_rel]} {k - 1}$", f"$x {REL[FLIP[res_rel]]} {k + 1}$"]
    ch, ans = mc(right, wr)
    return P("보통", f"일차부등식 ${lin(c1, 0, d0)} {REL[rel]} {lin(c2, 0, e0)}$의 해는?", ans, choices=ch,
             hint="x항은 왼쪽, 수는 오른쪽으로 이항해요. x의 계수가 음수면 나눌 때 부등호 방향이 바뀌어요.",
             explanation=f"{lin(coef, 0, 0, tex=False)} {PL[rel]} {e0 - d0}" + (f" → 양변을 {coef}로 나누면 x {PL[res_rel]} {k}" if coef != 1 else ""),
             steps=ineq_steps(sp.Symbol("x") * c1 + d0, sp.Symbol("x") * c2 + e0, rel)[0] + [f"답: x {PL[res_rel]} {k}"])


@kind(U, "괄호가 있는 일차부등식", 14)
def _():
    X = sp.Symbol("x")
    p1, q1, c1 = nz(-4, 4), nz(-5, 5), R.randint(-9, 9)
    p2, q2 = nz(-4, 4), nz(-5, 5)
    rel = R.choice(list(REL))
    L, Rr = p1 * (X + q1) + c1, p2 * (X + q2)
    need(p1 != p2)
    sol = sp.solve_univariate_inequality({">": L > Rr, "<": L < Rr, ">=": L >= Rr, "<=": L <= Rr}[rel], X, relational=False)
    ask = "max" if sol.inf == -sp.oo else "min"
    val_ = math.floor(sol.sup) - (1 if sol.right_open and sol.sup == math.floor(sol.sup) else 0) if ask == "max" else \
        math.ceil(sol.inf) + (1 if sol.left_open and sol.inf == math.ceil(sol.inf) else 0)
    need(sol.contains(val_) and not sol.contains(val_ + (1 if ask == "max" else -1)))
    def grp(pp, qq):
        return ("-" if pp == -1 else "" if pp == 1 else str(pp)) + f"({lin(1, 0, qq)})"
    lhs = grp(p1, q1) + (f" {sgn(c1)}" if c1 else "")
    return P("보통", f"일차부등식 ${lhs} {REL[rel]} {grp(p2, q2)}$를 만족시키는 {'가장 큰' if ask == 'max' else '가장 작은'} 정수 $x$의 값을 구하시오.", val_,
             hint="분배법칙으로 괄호를 먼저 풀어요.", explanation=f"정리하면 {sol_text(sol)} → {val_}",
             steps=ineq_steps(L, Rr, rel, pre=[f"괄호 풀기: {fmt(sp.expand(L), (X,), False)} {PL[rel]} {fmt(sp.expand(Rr), (X,), False)}"], ask=ask, val=val_)[0])


@kind(U, "계수가 소수 또는 분수인 일차부등식", 14)
def _():
    X = sp.Symbol("x")
    rel = R.choice(list(REL))
    if R.random() < 0.5:
        p_, q_, r_, s_ = (nz(-9, 9) for _ in range(4))
        need(p_ != r_)
        L, Rr = Q(F(p_, 10)) * X + Q(F(q_, 10)), Q(F(r_, 10)) * X + Q(F(s_, 10))
        def dx(c):
            return f"{c / 10:g}x"
        def dc(c):
            return f"+ {c / 10:g}" if c > 0 else f"- {-c / 10:g}"
        txt = f"{dx(p_)} {dc(q_)} {REL[rel]} {dx(r_)} {dc(s_)}"
        hint = "양변에 10을 곱해 정수 계수로 만들어요."
        scale = 10
    else:
        m, n = R.choice([(2, 3), (3, 4), (2, 5), (3, 2), (4, 6), (2, 4)])
        p_, q_, c = R.randint(-5, 5), R.randint(-5, 5), R.randint(-3, 3)
        L, Rr = (X + p_) / m - (X + q_) / n, sp.Integer(c)
        need(m != n)
        txt = f"\\frac{{{lin(1, 0, p_)}}}{{{m}}} - \\frac{{{lin(1, 0, q_)}}}{{{n}}} {REL[rel]} {c}"
        hint = f"분모 {m}과 {n}의 최소공배수를 양변에 곱해요."
        scale = m * n // math.gcd(m, n)
    sol = sp.solve_univariate_inequality({">": L > Rr, "<": L < Rr, ">=": L >= Rr, "<=": L <= Rr}[rel], X, relational=False)
    need(isinstance(sol, sp.Interval))
    ask = "max" if sol.inf == -sp.oo else "min"
    ints = [k for k in range(-200, 201) if sol.contains(k)]
    need(ints)
    val_ = max(ints) if ask == "max" else min(ints)
    need(-60 <= val_ <= 60)
    return P("어려움", f"일차부등식 ${txt}$를 만족시키는 {'가장 큰' if ask == 'max' else '가장 작은'} 정수 $x$의 값을 구하시오.", val_,
             hint=hint, explanation=f"정리하면 {sol_text(sol)} → {val_}", steps=ineq_steps(L, Rr, rel, scale=scale, ask=ask, val=val_)[0])


@kind(U, "해가 주어질 때 미지수 구하기", 12)
def _():
    c1, rhs, k = nz(-5, 5, (0, 1, -1)), R.randint(-9, 9), nz(-6, 6)
    rel = R.choice(list(REL))
    aval = rhs - c1 * k
    res_rel = rel if c1 > 0 else FLIP[rel]
    return P("보통", f"$x$에 대한 일차부등식 ${c1}x + a {REL[rel]} {rhs}$의 해가 $x {REL[res_rel]} {k}$일 때, 수 $a$의 값을 구하시오.", aval,
             hint="a를 수처럼 두고 부등식을 푼 뒤, 주어진 해와 비교해요.",
             explanation=f"{c1}x {PL[rel]} {rhs} - a → x {PL[res_rel]} ({rhs} - a) ÷ ({c1}) 이므로 ({rhs} - a) ÷ ({c1}) = {k} → a = {aval}",
             steps=["a를 그냥 수라고 생각하고 부등식을 풀어요",
                    f"a를 오른쪽으로 옮기면: {c1}x {PL[rel]} {rhs} - a",
                    f"양변을 {c1}로 나누면" + (" (음수라 부등호 방향이 바뀌어요)" if c1 < 0 else "") + f": x {PL[res_rel]} ({rhs} - a) ÷ ({c1})",
                    f"주어진 해 x {PL[res_rel]} {k}와 같아야 하니까: ({rhs} - a) ÷ ({c1}) = {k}",
                    f"{rhs} - a = {c1} × ({k}) = [[{c1 * k}]]",
                    f"a = {rhs} - ({c1 * k}) = [[{aval}]]"],
             detail=[f"확인: a = {aval}이면 {c1}x {sgn(aval)} {PL[rel]} {rhs} → {c1}x {PL[rel]} {rhs - aval} → x {PL[res_rel]} {k} ✔"])


@kind(U, "계수가 문자인 일차부등식", 10)
def _():
    sign_ = R.choice(["<", ">"])
    k = nz(-6, 6)
    rel = R.choice(list(REL))
    res = rel if sign_ == ">" else FLIP[rel]
    m = nz(-5, 5)
    rr = k - m
    right = f"$x {REL[res]} {rr}$"
    wr = [f"$x {REL[FLIP[res]]} {rr}$", f"$x {REL[res]} {-rr}$", f"$x {REL[FLIP[res]]} {-rr}$", f"$x {REL[res]} {k + m}$", f"$x {REL[res]} {k}$"]
    ch, ans = mc(right, wr)
    q = f"$a {sign_} 0$일 때, $x$에 대한 일차부등식 $ax {sgn(m)}a {REL[rel]} {k}a$의 해는?".replace("+ 1a", "+ a").replace("- 1a", "- a").replace(" 1a", " a").replace("-1a", "-a")
    return P("어려움", q, ans, choices=ch,
             hint="문자로 나눌 때 그 문자가 양수인지 음수인지 꼭 확인해요. 음수면 방향이 바뀌어요.",
             explanation=f"양변을 a로 나누면" + (" (a < 0이라 방향이 바뀜)" if sign_ == "<" else "") + f" x {sgn(m)} {PL[res]} {k} → x {PL[res]} {rr}",
             steps=[f"a {sign_} 0 → a는 {'음수' if sign_ == '<' else '양수'}예요",
                    f"모든 항에 a가 있으니 양변을 a로 나눠요" + (" → 음수로 나누니 부등호 방향이 [[바뀐다]]" if sign_ == "<" else " → 양수로 나누니 방향은 [[그대로]]"),
                    f"x {sgn(m)} {PL[res]} {k}",
                    f"{m}을(를) 오른쪽으로 옮기면: x {PL[res]} {k} - ({m}) = [[{rr}]]"],
             detail=[f"그래서 답은 {ans}"])


def word_steps(var_def, setup, L, Rr, rel, pick_line, ans_, unit="", pre=(), check=True):
    """활용 문제 풀이: 미지수 정하기 → 식 세우기 → 풀기 → 조건에 맞는 답 → 확인."""
    X = sp.Symbol("x")
    dens = [rat(c_).denominator for e_ in (L, Rr) for c_ in sp.Poly(sp.expand(e_), X).coeffs()]
    scale = 1
    for d_ in dens:
        scale = scale * d_ // math.gcd(scale, d_)
    lines = [f"미지수 정하기: {var_def}", f"부등식 세우기: {setup}"] + list(pre)
    lines += ineq_steps(L, Rr, rel, scale=scale)[0]
    lines.append(f"{pick_line} → [[{ans_}]]{unit}")
    det = []
    if check:
        try:
            v_ = F(str(ans_))
            lv, rv = L.subs(X, Q(v_)), Rr.subs(X, Q(v_))
            det.append(f"확인: x = {ans_}이면 왼쪽 {fpl(rat(lv))}, 오른쪽 {fpl(rat(rv))} → {fpl(rat(lv))} {PL[rel]} {fpl(rat(rv))} ✔")
        except Exception:
            pass
    return lines, det


@kind(U, "일차부등식의 활용: 수", 12)
def _():
    X = sp.Symbol("x")
    if R.random() < 0.5:
        p_, q_, r_, s_ = R.randint(3, 7), R.randint(1, 9), R.randint(2, 4), R.randint(5, 30)
        need(p_ > r_)
        cands = [k for k in range(1, 500) if p_ * k + q_ < r_ * k + s_]
        need(cands)
        n = max(cands)
        st, det = word_steps("어떤 자연수를 x라 해요", f"{p_}x + {q_} < {r_}x + {s_}", p_ * X + q_, r_ * X + s_, "<",
                             "이걸 만족하는 가장 큰 자연수", n)
        return P("보통", f"자연수 $x$를 {p_}배 한 수에 {q_}를 더하면, $x$를 {r_}배 한 수에 {s_}를 더한 것보다 작다. 이러한 자연수 $x$ 중 가장 큰 수를 구하시오.", n,
                 hint=f"부등식을 세워요: {p_}x + {q_} < {r_}x + {s_}", explanation=f"{p_ - r_}x < {s_ - q_} → 가장 큰 자연수 {n}",
                 steps=st, detail=det + [f"x = {n + 1}이면 {p_ * (n + 1) + q_} < {r_ * (n + 1) + s_}가 거짓이라 안 돼요"])
    odd = R.random() < 0.5
    k, lim = R.randint(2, 4), R.randint(30, 90)
    cands = [n for n in range(1, 200) if n % 2 == (1 if odd else 0) and n * k + (n + 2) <= lim]
    need(cands)
    n = max(cands)
    nm = "홀수" if odd else "짝수"
    st, det = word_steps(f"작은 {nm}를 x라 하면, 큰 {nm}는 x + 2 (연속하는 {nm}는 2씩 차이)", f"{k}x + (x + 2) ≤ {lim}",
                         k * X + X + 2, sp.Integer(lim), "<=", f"이 범위에서 가장 큰 {nm} x", n)
    return P("보통", f"연속하는 두 {nm}가 있다. 작은 수의 {k}배와 큰 수의 합이 {lim} 이하일 때, 큰 수가 될 수 있는 가장 큰 수를 구하시오.", n + 2,
             hint="작은 수를 x로 놓으면 큰 수는 x + 2", explanation=f"{k}x + (x + 2) ≤ {lim} → x ≤ {fpl(F(lim - 2, k + 1))} → 작은 수 {n}, 큰 수 {n + 2}",
             steps=st + [f"큰 수 = x + 2 = [[{n + 2}]]"], detail=det)


@kind(U, "일차부등식의 활용: 최대 개수", 12)
def _():
    X = sp.Symbol("x")
    p1, p2 = R.choice([(700, 1000), (900, 1200), (800, 1500), (1200, 1600), (500, 900), (1500, 2000)])
    tot = R.randint(10, 25)
    money = R.randint(p1 * tot // 100 + 1, p2 * tot // 100 - 1) * 100
    k = max(k for k in range(0, tot + 1) if p2 * k + p1 * (tot - k) <= money)
    need(0 < k < tot)
    items = R.choice([("공책", "볼펜"), ("사과", "배"), ("연필", "색연필"), ("빵", "케이크 조각"), ("엽서", "스티커북")])
    st, det = word_steps(f"{josa(items[1], '을')} x개 사면, {josa(items[0], '은')} ({tot} - x)개", f"{p2}x + {p1}({tot} - x) ≤ {money}",
                         p2 * X + p1 * (tot - X), sp.Integer(money), "<=", "개수는 자연수니까 가장 큰 자연수", k, "개",
                         pre=[f"괄호를 풀면: {p2}x + {p1 * tot} - {p1}x ≤ {money}"])
    return P("보통", f"한 개에 {p1}원인 {josa(items[0], '과')} 한 개에 {p2}원인 {josa(items[1], '을')} 합하여 {tot}개를 사려고 한다. 전체 가격을 {money}원 이하로 할 때, {josa(items[1], '은')} 최대 몇 개까지 살 수 있는지 구하시오.",
             k, hint=f"{josa(items[1], '을')} x개라 하면 {josa(items[0], '은')} ({tot} - x)개예요.",
             explanation=f"{p2}x + {p1}({tot} - x) ≤ {money} → {p2 - p1}x ≤ {money - p1 * tot} → x ≤ {fpl(F(money - p1 * tot, p2 - p1))}",
             steps=st, detail=det + [f"{k + 1}개를 사면 {p2 * (k + 1) + p1 * (tot - k - 1)}원이라 {money}원을 넘어요"])


@kind(U, "일차부등식의 활용: 도형", 12)
def _():
    X = sp.Symbol("x")
    if R.random() < 0.5:
        t_, bt = R.randint(2, 8), R.randint(4, 12)
        S = R.randint(10, 80)
        h = min(h for h in range(1, 300) if (t_ + bt) * h / 2 >= S)
        st, det = word_steps("높이를 x cm라 해요", f"({t_} + {bt}) × x ÷ 2 ≥ {S}  (사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2)",
                             Q(F(t_ + bt, 2)) * X, sp.Integer(S), ">=", "높이는 자연수니까 가장 작은 자연수", h, " cm")
        return P("보통", f"윗변의 길이가 {t_} cm, 아랫변의 길이가 {bt} cm인 사다리꼴의 넓이가 {S} cm² 이상이 되게 하려면, 높이는 최소 몇 cm이어야 하는지 구하시오. (단, 높이는 자연수)",
                 h, hint="사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2", explanation=f"({t_} + {bt}) × h ÷ 2 ≥ {S} → h ≥ {fpl(F(2 * S, t_ + bt))} → {h}",
                 steps=st, detail=det)
    d, P_ = R.randint(3, 15), R.randint(40, 120)
    w = max(w for w in range(1, 200) if 2 * (w + w + d) <= P_)
    st, det = word_steps(f"세로를 x cm라 하면, 가로는 (x + {d}) cm", f"2 × {{(x + {d}) + x}} ≤ {P_}  (둘레 = 2 × (가로 + 세로))",
                         2 * (2 * X + d), sp.Integer(P_), "<=", "길이는 자연수니까 가장 큰 자연수", w, " cm",
                         pre=[f"괄호를 풀면: 4x + {2 * d} ≤ {P_}"])
    return P("보통", f"가로의 길이가 세로의 길이보다 {d} cm 더 긴 직사각형의 둘레의 길이가 {P_} cm 이하일 때, 세로의 길이는 최대 몇 cm인지 구하시오. (단, 길이는 자연수)",
             w, hint="세로를 x cm라 하면 가로는 (x + d) cm, 둘레 = 2 × (가로 + 세로)",
             explanation=f"2(x + x + {d}) ≤ {P_} → 4x ≤ {P_ - 2 * d} → x ≤ {fpl(F(P_ - 2 * d, 4))}", steps=st, detail=det)


@kind(U, "일차부등식의 활용: 추가 요금", 12)
def _():
    X = sp.Symbol("x")
    base_n = R.choice([4, 5, 10])
    p1 = R.choice([2000, 2500, 3000, 4000])
    p2 = p1 - R.choice([500, 800, 1000, 1200])
    avg = R.randint(p2 // 100 + 1, p1 // 100 - 1) * 100
    cands = [n for n in range(base_n + 1, 500) if base_n * p1 + (n - base_n) * p2 < n * avg]
    need(cands)
    n = min(cands)
    place = R.choice(["체험관", "수영장", "박물관", "놀이 시설"])
    st, det = word_steps(f"x명이 입장한다고 해요 (x > {base_n})",
                         f"전체 요금 < (평균 {avg}원) × (사람 수): {base_n * p1} + {p2}(x - {base_n}) < {avg}x",
                         base_n * p1 + p2 * (X - base_n), avg * X, "<", "사람 수는 자연수니까 가장 작은 자연수", n, "명",
                         pre=[f"{base_n}명까지 요금: {base_n} × {p1} = {base_n * p1}원, 넘은 (x - {base_n})명: {p2}(x - {base_n})원",
                              f"괄호를 풀면: {base_n * p1} + {p2}x - {p2 * base_n} < {avg}x"])
    return P("어려움", f"어느 {place}의 입장료는 {base_n}명까지는 한 사람에 {p1}원이고, {base_n}명을 넘으면 넘은 사람은 한 사람에 {p2}원이다. 한 사람의 평균 입장료가 {avg}원보다 적어지려면 최소 몇 명이 입장해야 하는지 구하시오.",
             n, hint=f"x명이 입장하면 전체 요금은 {base_n * p1} + {p2}(x - {base_n})원",
             explanation=f"{base_n * p1} + {p2}(x - {base_n}) < {avg}x → x > {fpl(F(base_n * (p1 - p2), avg - p2))} → {n}명", steps=st, detail=det)


@kind(U, "일차부등식의 활용: 예금액", 12)
def _():
    X = sp.Symbol("x")
    a0, b0 = R.randint(1, 8) * 10000, R.randint(2, 10) * 10000
    da, db = R.randint(2, 8) * 1000, R.randint(1, 4) * 1000
    k = R.choice([1, 2])
    cands = [m for m in range(1, 200) if a0 + da * m > k * (b0 + db * m)]
    need(cands and da > k * db)
    m = min(cands)
    need(2 <= m <= 24)
    who = R.choice([("지수", "동생"), ("민호", "누나"), ("서연", "오빠")])
    cond = "많아지는" if k == 1 else "2배보다 많아지는"
    setup = f"{a0} + {da}x > " + (f"2({b0} + {db}x)" if k == 2 else f"{b0} + {db}x")
    st, det = word_steps(f"x개월 후라고 해요", setup, a0 + da * X, k * (b0 + db * X), ">", "'처음으로'니까 가장 작은 자연수", m, "개월 후",
                         pre=[f"x개월 후 {who[0]}: {a0} + {da}x (원), {who[1]}: {b0} + {db}x (원)"] + ([f"괄호를 풀면: {a0} + {da}x > {2 * b0} + {2 * db}x"] if k == 2 else []))
    return P("어려움", f"현재 {who[0]}의 통장에는 {a0}원, {who[1]}의 통장에는 {b0}원이 있다. 다음 달부터 {josa(who[0], '는')} 매달 {da}원씩, {josa(who[1], '는')} 매달 {db}원씩 예금한다면, {who[0]}의 예금액이 처음으로 {who[1]}의 예금액{'보다' if k == 1 else '의'} {cond} 것은 몇 개월 후인지 구하시오.",
             m, hint=f"x개월 후 {who[0]}: {a0} + {da}x, {who[1]}: {b0} + {db}x",
             explanation=f"{setup} → x > {fpl(F(k * b0 - a0, da - k * db))} → {m}개월 후", steps=st, detail=det)


@kind(U, "일차부등식의 활용: 거리, 속력, 시간", 12)
def _():
    X = sp.Symbol("x")
    if R.random() < 0.5:
        v1, v2 = R.choice([(2, 3), (3, 4), (2, 4), (3, 5), (4, 6)])
        tt = F(R.randint(2, 8), 2)
        dmax = tt * v1 * v2 / (v1 + v2)
        need(dmax.denominator <= 5)
        h, mi = int(tt), int((tt - int(tt)) * 60)
        tstr = f"{h}시간" + (f" {mi}분" if mi else "")
        st, det = word_steps("떨어진 거리를 x km라 해요", f"(갈 때 시간) + (올 때 시간) ≤ {tstr}: x/{v1} + x/{v2} ≤ {fpl(tt)}",
                             X / v1 + X / v2, Q(tt), "<=", "그래서 최대 거리", fpl(dmax), " km",
                             pre=[f"시간 = 거리 ÷ 속력, {tstr} = {fpl(tt)}시간"])
        return P("어려움", f"어느 지점까지 갈 때는 시속 {v1} km로, 돌아올 때는 같은 길을 시속 {v2} km로 걸어서 {tstr} 이내로 다녀오려고 한다. 최대 몇 km 떨어진 지점까지 다녀올 수 있는지 구하시오.",
                 fpl(dmax), hint=f"시간 = 거리 ÷ 속력. {tstr} = {fpl(tt)}시간",
                 explanation=f"x/{v1} + x/{v2} ≤ {fpl(tt)} → x ≤ {fpl(dmax)}", steps=st, detail=det)
    D = R.randint(3, 12)
    vw, vr = R.choice([(3, 6), (4, 8), (3, 9), (4, 12), (2, 6)])
    tt = F(R.randint(2, 6), 2)
    xmax = (tt - F(D, vr)) / (F(1, vw) - F(1, vr))
    need(0 < xmax < D and xmax.denominator <= 4)
    h, mi = int(tt), int((tt - int(tt)) * 60)
    tstr = f"{h}시간" + (f" {mi}분" if mi else "")
    st, det = word_steps(f"걸은 거리를 x km라 하면, 뛴 거리는 ({D} - x) km", f"(걸은 시간) + (뛴 시간) ≤ {fpl(tt)}: x/{vw} + ({D} - x)/{vr} ≤ {fpl(tt)}",
                         X / vw + (D - X) / vr, Q(tt), "<=", "그래서 걸은 거리는 최대", fpl(xmax), " km",
                         pre=[f"시간 = 거리 ÷ 속력, {tstr} = {fpl(tt)}시간"])
    return P("어려움", f"집에서 {D} km 떨어진 공원까지 가는데, 처음에는 시속 {vw} km로 걷다가 도중에 시속 {vr} km로 뛰어서 {tstr} 이내에 도착하려고 한다. 걸은 거리는 최대 몇 km인지 구하시오.",
             fpl(xmax), hint=f"걸은 거리 x km, 뛴 거리 ({D} - x) km. 시간 = 거리 ÷ 속력",
             explanation=f"x/{vw} + ({D} - x)/{vr} ≤ {fpl(tt)} → x ≤ {fpl(xmax)}", steps=st, detail=det)


@kind(U, "일차부등식의 활용: 유리한 방법 선택", 12)
def _():
    X = sp.Symbol("x")
    if R.random() < 0.5:
        price = R.choice([600, 800, 1000, 1200, 1500, 2000])
        disc = R.choice([10, 20, 25, 30])
        fare = R.choice([1000, 1200, 1500, 2000, 2500])
        cands = [n for n in range(1, 500) if price * n * (100 - disc) / 100 + fare < price * n]
        n = min(cands)
        item = R.choice(["아이스크림", "음료수", "공책", "과자"])
        sale = Q(F(price * (100 - disc), 100))
        st, det = word_steps(f"{josa(item, '을')} x개 산다고 해요",
                             f"(할인 매장 값 + 교통비) < (동네 가게 값): {fpl(rat(sale))}x + {fare} < {price}x",
                             sale * X + fare, price * X, "<", "개수는 자연수니까 가장 작은 자연수", n, "개",
                             pre=[f"할인된 한 개 값: {price} × (1 - {disc}/100) = {fpl(rat(sale))}원"])
        return P("어려움", f"동네 가게에서 한 개에 {price}원인 {josa(item, '을')} 할인 매장에서는 {disc}% 할인된 가격에 판다. 할인 매장에 다녀오는 왕복 교통비가 {fare}원일 때, {josa(item, '을')} 최소 몇 개 이상 사야 할인 매장에서 사는 것이 유리한지 구하시오.",
                 n, hint=f"x개를 산다면: 동네 가게 {price}x원, 할인 매장 {fpl(rat(sale))}x + {fare}원",
                 explanation=f"{fpl(rat(sale))}x + {fare} < {price}x → x > {fpl(F(fare * 100, price * disc))} → {n}개", steps=st, detail=det)
    fee, per_m, per = R.choice([(10000, 3000, 5000), (20000, 2000, 4000), (15000, 2500, 4000), (12000, 1000, 2500)])
    cands = [n for n in range(1, 200) if fee + per_m * n < per * n]
    n = min(cands)
    st, det = word_steps("한 해에 x회 이용한다고 해요", f"(연회원 비용) < (일반 비용): {fee} + {per_m}x < {per}x",
                         fee + per_m * X, per * X, "<", "횟수는 자연수니까 가장 작은 자연수", n, "회")
    return P("어려움", f"어느 수영장의 1회 입장료는 {per}원이다. 연회원은 가입비 {fee}원을 내고 1회에 {per_m}원만 내면 된다. 한 해에 최소 몇 회 이상 이용해야 연회원이 유리한지 구하시오.",
             n, hint=f"x회 이용하면: 일반 {per}x원, 연회원 {fee} + {per_m}x원",
             explanation=f"{fee} + {per_m}x < {per}x → x > {fpl(F(fee, per - per_m))} → {n}회", steps=st, detail=det)


# ═════════════════════ 5. 연립일차방정식 ═════════════════════
U = "5. 연립일차방정식"


def eq_tex(a1, b1, c1):
    return f"{lin(a1, b1)} = {c1}"


def sub2(a1, b1, sx, sy, vx="x", vy="y"):
    """a×(sx) + b×(sy) = 값  (빈칸 없이)"""
    X_, Y_ = sp.symbols(f"{vx} {vy}")
    parts = subst_line(a1 * X_ + b1 * Y_, {X_: sx, Y_: sy}, (X_, Y_)).replace("[[", "").replace("]]", "").split(" = ")
    return " = ".join(dict.fromkeys(parts))


def solve1(coef, rhs, v="a", blank=True):
    """coef·v = rhs 를 풀어 v 구하기 (줄 목록)."""
    val = F(rhs, coef)
    b_ = (lambda s_: f"[[{s_}]]") if blank else (lambda s_: s_)
    if coef == 1:
        return [f"{v} = {b_(fpl(val))}"]
    if coef == -1:
        return [f"-{v} = {rhs} → {v} = {b_(fpl(val))}"]
    return [f"{lin(coef, 0, 0, v, 'z', tex=False)} = {rhs}", f"양변을 {par(coef)}로 나누면: {v} = {b_(fpl(val))}"]


def chk_sys(eqs, sx, sy):
    """확인 줄: 두 식에 해를 넣어 성립하는지."""
    out = []
    for tag, (a_, b_, c_) in zip("①②", eqs):
        out.append(f"확인 {tag}: {sub2(a_, b_, sx, sy)} ✔")
    return out


@kind(U, "미지수가 2개인 일차방정식", 8)
def _():
    p_, q_, r_ = nz(-5, 5), nz(-5, 5), R.randint(-9, 9)
    pool = [(f"${lin(p_, q_)} = {r_}$", True, "x, y가 모두 있고 차수가 1이에요"),
            (f"${lin(p_, 0, r_)} = {lin(q_, 0, 0)}$", False, "y가 없어요 (미지수가 x 하나)"),
            (f"$x + y^{{2}} = {abs(r_) + 1}$", False, "y²이 있어 차수가 2예요"),
            (f"$xy {sgn(abs(r_) + 1)} = 0$", False, "xy는 차수가 2인 항이에요"),
            (f"$\\frac{{1}}{{x}} + y = {abs(q_)}$", False, "x가 분모에 있어서 일차식이 아니에요"),
            (f"$x {sgn(q_)}y = {q_}y {sgn(r_)}$", False, f"{q_}y를 옮기면 지워져서 x만 남아요"),
            (f"${abs(p_)}(x + y) = {abs(p_)}x {sgn(r_)}$", False, f"괄호를 풀면 {abs(p_)}x가 지워져서 y만 남아요"),
            (f"$x^{{2}} + y = x^{{2}} {sgn(q_)}x$", True, f"x²이 지워져서 {lin(-q_, 1, tex=False)} = 0 → 일차방정식이에요"),
            (f"${lin(p_, q_, 0)} = {lin(p_, 0, r_)}$", False, f"{p_}x가 지워져서 y만 남아요"),
            (f"$\\frac{{x}}{{{abs(p_) + 1}}} - y = {r_}$", True, f"x의 계수가 1/{abs(p_) + 1}일 뿐, 차수가 1이에요")]
    pool = [(s_.replace("$1x", "$x").replace("$-1x", "$-x").replace(" 1y", " y").replace("= 1y", "= y").replace("= -1y", "= -y"), t_, w_) for s_, t_, w_ in pool]
    why = {s_: (t_, w_) for s_, t_, w_ in pool}
    want = R.random() < 0.6
    ch, ans = pick([(s_, t_) for s_, t_, _ in pool], want)
    return P("쉬움", f"다음 중 미지수가 2개인 일차방정식{'인' if want else '이 아닌'} 것은?", ans, choices=ch,
             hint="정리했을 때 ax + by + c = 0 (a ≠ 0, b ≠ 0) 꼴인지 봐요. 한 문자가 지워지면 아니에요.",
             explanation="모든 항을 왼쪽으로 옮겨 정리했을 때 x와 y가 모두 남고 차수가 1인 등식이어야 해요.",
             steps=["모든 항을 왼쪽으로 옮겨서 정리해 봐요",
                    "ax + by + c = 0 꼴이고 a ≠ [[0]], b ≠ [[0]]이어야 해요 (x, y 둘 다 남아야 해요)",
                    "각 항의 차수가 [[1]]이어야 해요 (x², xy, 1/x가 남으면 안 돼요)"],
             detail=["보기를 하나씩 정리해 봐요 (⭕ = 미지수가 2개인 일차방정식):"] + judge_lines(ch, lambda c_: why[c_]) + [f"그래서 답은 {ans}"])


@kind(U, "일차방정식의 해", 12)
def _():
    A1, B1 = nz(-4, 4), nz(-4, 4)
    sols = [(i, j) for i in range(-6, 7) for j in range(-6, 7)]
    C1 = A1 * R.randint(-4, 4) + B1 * R.randint(-4, 4)
    good = [(i, j) for i, j in sols if A1 * i + B1 * j == C1]
    bad = [(i, j) for i, j in sols if A1 * i + B1 * j != C1]
    need(len(good) >= 4)
    want = R.random() < 0.5
    pts = {}
    for i, j in R.sample(good, min(6, len(good))) + R.sample(bad, 6):
        pts[f"$({i}, {j})$"] = (i, j)
    ch, ans = pick([(k_, A1 * v_[0] + B1 * v_[1] == C1) for k_, v_ in pts.items()], want)
    ti, tj = pts[ch[MARKS.index(ans)]]

    def judge(c_):
        i, j = pts[c_]
        v_ = A1 * i + B1 * j
        return v_ == C1, f"{sub2(A1, B1, i, j)}" + (" → 해예요" if v_ == C1 else f" ≠ {C1} → 해가 아니에요")
    X_, Y_ = sp.symbols("x y")
    return P("쉬움", f"다음 중 일차방정식 ${eq_tex(A1, B1, C1)}$의 해{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint="x, y 값을 넣어서 등식이 성립하는지 하나씩 확인해요.",
             explanation=f"각 순서쌍을 대입해 좌변이 {C1}가 되는지 확인해요.",
             steps=[f"순서쌍 (x, y)의 x 값은 x 자리에, y 값은 y 자리에 넣어요",
                    f"좌변이 {C1}과 같으면 해, 다르면 해가 아니에요",
                    f"정답 보기 ({ti}, {tj})를 넣으면: " + subst_line(A1 * X_ + B1 * Y_, {X_: ti, Y_: tj}, (X_, Y_))],
             detail=["보기마다 넣어 보면 (⭕ = 해):"] + judge_lines(ch, judge) + [f"그래서 답은 {ans}"])


@kind(U, "x, y가 자연수인 해", 12)
def _():
    A1, B1 = R.randint(1, 5), R.randint(1, 5)
    C1 = R.randint(8, 40)
    sols = [(i, j) for i in range(1, 50) for j in range(1, 50) if A1 * i + B1 * j == C1]
    need(1 <= len(sols) <= 8)
    # 계수가 큰 문자에 1, 2, 3, ...을 넣는다 (넣어 볼 수가 적다)
    if B1 >= A1:
        big, bc, oc, bn, on = "y", B1, A1, "y", "x"
    else:
        big, bc, oc, bn, on = "x", A1, B1, "x", "y"
    table = []
    k = 1
    while C1 - bc * k > 0:
        rest = C1 - bc * k
        ok = rest % oc == 0
        val = f"{rest // oc}" if ok else f"{fpl(F(rest, oc))}"
        table.append(f"{bn} = {k} → " + (f"{lin(oc, 0, 0, on, 'z', tex=False)} = {rest} → " if oc != 1 else "") + f"{on} = {val} " + ("✔ 자연수" if ok else "✘ 자연수가 아님"))
        k += 1
    first = sols[0] if bn == "x" else sorted(sols, key=lambda s_: s_[1])[0]
    fk = first[0] if bn == "x" else first[1]
    fo = first[1] if bn == "x" else first[0]
    return P("보통", f"$x$, $y$의 값이 자연수일 때, 일차방정식 ${eq_tex(A1, B1, C1)}$의 해의 개수를 구하시오.", len(sols),
             hint="y(또는 x)에 1, 2, 3, ...을 차례로 넣어 다른 문자도 자연수가 되는 것만 찾아요.",
             explanation="해: " + ", ".join(f"({i}, {j})" for i, j in sols),
             steps=[f"계수가 큰 {bn}에 1, 2, 3, …을 차례로 넣어요 ({on}도 자연수여야 하니까 {lin(oc, 0, 0, on, 'z', tex=False)} > 0일 때까지)",
                    f"예: {bn} = {fk}이면 " + (f"{lin(oc, 0, 0, on, 'z', tex=False)} = {C1} - {bc * fk} = {C1 - bc * fk} → {on} = [[{fo}]]" if oc != 1 else f"{on} = {C1} - {bc * fk} = [[{fo}]]") + " (자연수 ✔)",
                    f"이렇게 {on}가 자연수가 되는 경우만 세면: 해의 개수 [[{len(sols)}]]"],
             detail=["하나씩 넣어 본 표:"] + table + ["해: " + ", ".join(f"({i}, {j})" for i, j in sols), f"그래서 해는 {len(sols)}개"])


@kind(U, "해가 주어질 때 계수 구하기", 12)
def _():
    px, py = nz(-5, 5), nz(-5, 5)
    A1, av = nz(-5, 5), nz(-6, 6)
    if R.random() < 0.5:
        C1 = A1 * px - av * py
        q = f"일차방정식 ${A1}x - ay = {C1}$의 한 해가 $({px}, {py})$일 때, 수 $a$의 값을 구하시오.".replace("$1x", "$x").replace("$-1x", "$-x")
        ex = f"{A1}×({px}) - a×({py}) = {C1} → a = {av}"
        st = [f"해 ({px}, {py})를 넣으면 등식이 성립해요: x = {px}, y = {py}",
              f"{A1}×({px}) - a×({py}) = {C1}",
              f"계산하면: {A1 * px} {sgn(-py)}a = {C1}".replace(" 1a", " a"),
              f"{A1 * px}을(를) 옮기면: {lin(-py, 0, 0, 'a', 'z', tex=False)} = {C1} - ({A1 * px}) = [[{C1 - A1 * px}]]"]
        st += solve1(-py, C1 - A1 * px)[-1:] if -py not in (1,) else []
        chk = f"확인: a = {av}이면 {A1}×({px}) - ({av})×({py}) = {C1} ✔"
    else:
        C1 = av * px + A1 * py
        q = f"일차방정식 $ax {sgn(A1)}y = {C1}$의 한 해가 $({px}, {py})$일 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y")
        ex = f"a×({px}) + ({A1})×({py}) = {C1} → a = {av}"
        st = [f"해 ({px}, {py})를 넣으면 등식이 성립해요: x = {px}, y = {py}",
              f"a×({px}) + ({A1})×({py}) = {C1}",
              f"계산하면: {lin(px, 0, A1 * py, 'a', 'z', tex=False)} = {C1}",
              f"{A1 * py}을(를) 옮기면: {lin(px, 0, 0, 'a', 'z', tex=False)} = {C1} - ({A1 * py}) = [[{C1 - A1 * py}]]"]
        st += solve1(px, C1 - A1 * py)[-1:] if px != 1 else []
        chk = f"확인: a = {av}이면 ({av})×({px}) + ({A1})×({py}) = {C1} ✔"
    return P("보통", q, av, hint="해를 방정식에 대입하면 등식이 성립해요.", explanation=ex, steps=st, detail=[chk, f"답: a = {av}"])


@kind(U, "연립방정식의 해", 10)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    info = {}
    for _ in range(30):
        a1, b1, a2, b2 = (nz(-3, 3) for _ in range(4))
        if a1 * b2 == a2 * b1:
            continue
        c1, c2 = a1 * sx + b1 * sy, a2 * sx + b2 * sy + R.choice([0, 0, 1, -1, 2])
        info[f"$\\begin{{cases}} {eq_tex(a1, b1, c1)} \\\\ {eq_tex(a2, b2, c2)} \\end{{cases}}$"] = (a1, b1, c1, a2, b2, c2)
    ch, ans = pick([(k_, v_[3] * sx + v_[4] * sy == v_[5]) for k_, v_ in info.items()], True)
    a1, b1, c1, a2, b2, c2 = info[ch[MARKS.index(ans)]]

    def judge(c_):
        a1, b1, c1, a2, b2, c2 = info[c_]
        v2 = a2 * sx + b2 * sy
        return v2 == c2, f"첫째 식 {a1 * sx + b1 * sy} = {c1} ✔, 둘째 식 {sub2(a2, b2, sx, sy)}" + (" ✔" if v2 == c2 else f" ≠ {c2} ✘")
    return P("보통", f"다음 연립방정식 중 $x = {sx}$, $y = {sy}$가 해인 것은?", ans, choices=ch,
             hint="두 식 모두에 대입해서 둘 다 성립해야 해요.", explanation="두 방정식을 동시에 만족시키는 것을 찾아요.",
             steps=["연립방정식의 해는 두 식을 [[동시에]] 만족해야 해요",
                    f"x = {sx}, y = {sy}를 두 식에 각각 넣어 봐요",
                    f"정답 보기 첫째 식: {sub2(a1, b1, sx, sy).rsplit('=', 1)[0]}= [[{c1}]]",
                    f"정답 보기 둘째 식: {sub2(a2, b2, sx, sy).rsplit('=', 1)[0]}= [[{c2}]]"],
             detail=["보기마다 확인하면 (⭕ = 해):"] + judge_lines(ch, judge) + [f"그래서 답은 {ans}"])


@kind(U, "연립방정식의 계수가 문자인 경우", 12)
def _():
    sx, sy = nz(-4, 4), nz(-4, 4)
    av, bv = nz(-5, 5), nz(-5, 5)
    k1, k2 = nz(-4, 4), nz(-4, 4)
    c1, c2 = av * sx + k1 * sy, bv * sx + k2 * sy
    combo = R.choice([("a + b", av + bv), ("a - b", av - bv), ("ab", av * bv)])
    st = [f"해 x = {sx}, y = {sy}를 두 식에 각각 넣어요",
          f"첫째 식: a×({sx}) + ({k1})×({sy}) = {c1} → {lin(sx, 0, k1 * sy, 'a', 'z', tex=False)} = {c1}",
          f"{k1 * sy}을(를) 옮기면: {lin(sx, 0, 0, 'a', 'z', tex=False)} = [[{c1 - k1 * sy}]]"]
    st += solve1(sx, c1 - k1 * sy)[1:] if sx not in (1, -1) else ([f"a = [[{av}]]"] if sx == -1 else [])
    st += [f"둘째 식: b×({sx}) + ({k2})×({sy}) = {c2} → {lin(sx, 0, k2 * sy, 'b', 'z', tex=False)} = {c2}",
           f"{k2 * sy}을(를) 옮기면: {lin(sx, 0, 0, 'b', 'z', tex=False)} = [[{c2 - k2 * sy}]]"]
    st += solve1(sx, c2 - k2 * sy, "b")[1:] if sx not in (1, -1) else ([f"b = [[{bv}]]"] if sx == -1 else [])
    sym = {"a + b": f"({av}) + ({bv})", "a - b": f"({av}) - ({bv})", "ab": f"({av}) × ({bv})"}[combo[0]]
    st.append(f"{combo[0]} = {sym} = [[{combo[1]}]]")
    return P("보통", f"연립방정식 $\\begin{{cases}} ax {sgn(k1)}y = {c1} \\\\ bx {sgn(k2)}y = {c2} \\end{{cases}}$의 해가 $x = {sx}$, $y = {sy}$일 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y"),
             combo[1], hint="해를 두 식에 각각 대입하면 a, b에 대한 일차방정식이 돼요.",
             explanation=f"{sx}a + ({k1 * sy}) = {c1} → a = {av}, {sx}b + ({k2 * sy}) = {c2} → b = {bv}",
             steps=st, detail=[f"확인: ({av})×({sx}) + ({k1})×({sy}) = {c1} ✔, ({bv})×({sx}) + ({k2})×({sy}) = {c2} ✔", f"답: {combo[1]}"])


@kind(U, "연립방정식의 해가 문자인 경우", 12)
def _():
    p_, q_ = nz(-3, 3), nz(-3, 3)
    m, bv = nz(-4, 4), nz(-5, 5)
    c1 = p_ * m + q_ * bv
    r_ = nz(-3, 3)
    c2 = R.randint(-9, 9)
    aval = F(c2 - r_ * bv, m)
    need(aval.denominator == 1)
    st = ["a가 없는 첫째 식에 먼저 넣어서 b를 구해요",
          f"첫째 식에 x = {m}, y = b: {p_}×({m}) + ({q_})×b = {c1} → {lin(q_, 0, p_ * m, 'b', 'z', tex=False)} = {c1}",
          f"{p_ * m}을(를) 옮기면: {lin(q_, 0, 0, 'b', 'z', tex=False)} = [[{c1 - p_ * m}]]"]
    st += solve1(q_, c1 - p_ * m, "b")[1:] if q_ not in (1, -1) else ([f"b = [[{bv}]]"] if q_ == -1 else [])
    st += [f"둘째 식에 x = {m}, y = {bv}: a×({m}) + ({r_})×({bv}) = {c2} → {lin(m, 0, r_ * bv, 'a', 'z', tex=False)} = {c2}",
           f"{r_ * bv}을(를) 옮기면: {lin(m, 0, 0, 'a', 'z', tex=False)} = [[{c2 - r_ * bv}]]"]
    st += solve1(m, c2 - r_ * bv)[1:] if m not in (1, -1) else ([f"a = [[{fpl(aval)}]]"] if m == -1 else [])
    st.append(f"a + b = ({fpl(aval)}) + ({bv}) = [[{fpl(aval + bv)}]]")
    return P("어려움", f"연립방정식 $\\begin{{cases}} {eq_tex(p_, q_, c1)} \\\\ ax {sgn(r_)}y = {c2} \\end{{cases}}$의 해가 $x = {m}$, $y = b$일 때, 수 $a$, $b$에 대하여 $a + b$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y"),
             fpl(aval + bv), hint="a가 없는 식에 먼저 대입해서 b를 구해요.",
             explanation=f"{p_}×({m}) + ({q_})b = {c1} → b = {bv}, ({m})a + ({r_})×({bv}) = {c2} → a = {fpl(aval)}",
             steps=st, detail=[f"답: a = {fpl(aval)}, b = {bv} → a + b = {fpl(aval + bv)}"])


def sys_q(level, concept, txt, sx, sy, hint, exp, word="연립방정식", steps=None, chk=None):
    return P(level, f"{word} ${txt}$를 푸시오. (예: x=1, y=2)", f"x={sx}, y={sy}", hint=hint, explanation=exp,
             steps=steps or [f"x = [[{sx}]]", f"y = [[{sy}]]"],
             detail=(chk_sys(chk, sx, sy) if chk else []) + [f"답: x = {sx}, y = {sy}"])


@kind(U, "연립방정식의 풀이: 대입법", 12)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    m = nz(-4, 4)
    k = sy - m * sx
    a2, b2 = nz(-5, 5), nz(-5, 5)
    need(a2 + b2 * m != 0)
    c = a2 * sx + b2 * sy
    if R.random() < 0.5:
        txt = f"\\begin{{cases}} y = {fx(m, k)} \\\\ {eq_tex(a2, b2, c)} \\end{{cases}}"
        cx = a2 + b2 * m
        st = [f"① y = {fx(m, k, tex=False)}", f"② {lin(a2, b2, tex=False)} = {c}",
              f"①을 ②의 y 자리에 넣으면: {a2}x {'+' if b2 > 0 else '-'} {abs(b2)}({fx(m, k, tex=False)}) = {c}",
              f"괄호를 풀면: {lin(cx, 0, b2 * k, tex=False)} = {c}",
              f"{b2 * k}을(를) 옮기면: {lin(cx, 0, tex=False)} = {c - b2 * k} → x = [[{sx}]]",
              f"x = {sx}를 ①에 넣으면: y = {m}×({sx}) + ({k}) = [[{sy}]]"]
        chk = [(-m, 1, k), (a2, b2, c)]
    else:
        k2 = sx - m * sy
        need(b2 + a2 * m != 0)
        txt = f"\\begin{{cases}} x = {fx(m, k2, 'y')} \\\\ {eq_tex(a2, b2, c)} \\end{{cases}}"
        cy = b2 + a2 * m
        st = [f"① x = {fx(m, k2, 'y', tex=False)}", f"② {lin(a2, b2, tex=False)} = {c}",
              f"①을 ②의 x 자리에 넣으면: {a2}({fx(m, k2, 'y', tex=False)}) {sgn(b2)}y = {c}",
              f"괄호를 풀면: {lin(cy, 0, a2 * k2, 'y', 'x', tex=False)} = {c}",
              f"{a2 * k2}을(를) 옮기면: {lin(cy, 0, 0, 'y', 'x', tex=False)} = {c - a2 * k2} → y = [[{sy}]]",
              f"y = {sy}를 ①에 넣으면: x = {m}×({sy}) + ({k2}) = [[{sx}]]"]
        chk = [(1, -m, k2), (a2, b2, c)]
    return sys_q("보통", "", txt, sx, sy, "한 식을 다른 식의 문자 자리에 그대로 넣어요.", f"대입해서 풀면 x = {sx}, y = {sy}", steps=st, chk=chk)


@kind(U, "연립방정식의 풀이: 가감법", 14)
def _():
    sx, sy = nz(-6, 6), nz(-6, 6)
    a1, b1, a2, b2 = nz(-5, 5), nz(-5, 5), nz(-5, 5), nz(-5, 5)
    need(a1 * b2 - a2 * b1 != 0 and abs(a1) + abs(b1) > 2)
    e1, e2 = (a1, b1, a1 * sx + b1 * sy), (a2, b2, a2 * sx + b2 * sy)
    txt = f"\\begin{{cases}} {eq_tex(*e1)} \\\\ {eq_tex(*e2)} \\end{{cases}}"
    return sys_q("보통", "", txt, sx, sy, "없앨 문자의 계수의 절댓값을 같게 만든 뒤 더하거나 빼요.", f"가감법으로 풀면 x = {sx}, y = {sy}",
                 steps=elim_steps(*e1, *e2, sx, sy), chk=[e1, e2])


@kind(U, "괄호가 있는 연립방정식", 10)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    p_, q_, r_ = nz(-3, 3, (0, 1)), nz(-4, 4), nz(-3, 3)
    c1 = p_ * (sx + q_) + r_ * sy
    a2, b2 = nz(-4, 4), nz(-4, 4)
    need(p_ * b2 - a2 * r_ != 0)
    txt = f"\\begin{{cases}} {'-' if p_ == -1 else p_}({lin(1, 0, q_)}) {sgn(r_)}y = {c1} \\\\ {eq_tex(a2, b2, a2 * sx + b2 * sy)} \\end{{cases}}".replace("+ 1y", "+ y").replace("- 1y", "- y")
    a1_, c1_ = p_, c1 - p_ * q_
    return sys_q("보통", "", txt, sx, sy, "괄호를 먼저 풀어서 ax + by = c 꼴로 정리해요.", f"정리해서 풀면 x = {sx}, y = {sy}",
                 steps=elim_steps(a1_, r_, c1_, a2, b2, a2 * sx + b2 * sy, sx, sy,
                                  pre=[f"첫째 식의 괄호를 풀면: {lin(p_, r_, p_ * q_, tex=False)} = {c1}",
                                       f"{p_ * q_}을(를) 옮기면: {lin(a1_, r_, tex=False)} = {c1} - ({p_ * q_}) = {c1_}"]),
                 chk=[(a1_, r_, c1_), (a2, b2, a2 * sx + b2 * sy)])


@kind(U, "계수가 소수 또는 분수인 연립방정식", 14)
def _():
    sx, sy = nz(-6, 6), nz(-6, 6)
    a1, b1 = nz(-5, 5), nz(-5, 5)
    v1 = F(a1 * sx + b1 * sy, 10)
    m, n = R.choice([(2, 3), (3, 4), (2, 5), (4, 3), (3, 2), (6, 4)])
    s_ = R.choice([1, -1])
    v2 = F(sx, m) + s_ * F(sy, n)
    need(F(a1, 10) * F(s_, n) - F(b1, 10) * F(1, m) != 0)
    dec = f"{a1 / 10:g}x {('+ ' + format(b1 / 10, 'g')) if b1 > 0 else ('- ' + format(-b1 / 10, 'g'))}y = {float(v1):g}"
    frac = f"\\frac{{x}}{{{m}}} {'+' if s_ > 0 else '-'} \\frac{{y}}{{{n}}} = {ftex(v2)}"
    txt = f"\\begin{{cases}} {dec} \\\\ {frac} \\end{{cases}}" if R.random() < 0.5 else f"\\begin{{cases}} {frac} \\\\ {dec} \\end{{cases}}"
    L_ = m * n // math.gcd(m, n)
    fa, fb, fc = L_ // m, s_ * (L_ // n), v2 * L_
    need(fc.denominator == 1)
    pre = [f"소수 식의 양변에 10을 곱하면: {lin(a1, b1, tex=False)} = {a1 * sx + b1 * sy}",
           f"분수 식의 양변에 분모 {m}, {n}의 최소공배수 {L_}를 곱하면: {lin(fa, fb, tex=False)} = {int(fc)}  (x/{m}×{L_} = {L_ // m}x, y/{n}×{L_} = {L_ // n}y)"]
    return sys_q("어려움", "", txt, sx, sy, "소수는 10을, 분수는 분모의 최소공배수를 곱해 정수 계수로 바꿔요.", f"정수 계수로 바꿔 풀면 x = {sx}, y = {sy}",
                 steps=elim_steps(a1, b1, a1 * sx + b1 * sy, fa, fb, int(fc), sx, sy, pre=pre),
                 chk=[(a1, b1, a1 * sx + b1 * sy), (fa, fb, int(fc))])


@kind(U, "A = B = C 꼴의 방정식", 10)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    a1, b1, a2, b2 = nz(-4, 4), nz(-4, 4), nz(-4, 4), nz(-4, 4)
    need(a1 * b2 - a2 * b1 != 0)
    Cv = R.randint(-9, 9)
    k1, k2 = Cv - (a1 * sx + b1 * sy), Cv - (a2 * sx + b2 * sy)
    txt = f"{lin(a1, b1, k1)} = {lin(a2, b2, k2)} = {Cv}"
    return sys_q("어려움", "", txt, sx, sy, "A = C, B = C 두 식으로 나누어 연립해요.", f"{lin(a1, b1, k1, tex=False)} = {Cv}, {lin(a2, b2, k2, tex=False)} = {Cv} → x = {sx}, y = {sy}", word="방정식",
                 steps=elim_steps(a1, b1, Cv - k1, a2, b2, Cv - k2, sx, sy,
                                  pre=["A = B = C는 A = C, B = C 두 식으로 나눠서 연립방정식으로 풀어요",
                                       f"A = C: {lin(a1, b1, k1, tex=False)} = {Cv}", f"B = C: {lin(a2, b2, k2, tex=False)} = {Cv}",
                                       "수를 오른쪽으로 옮겨 정리하면"]),
                 chk=[(a1, b1, Cv - k1), (a2, b2, Cv - k2)])


@kind(U, "두 연립방정식의 해가 서로 같은 경우", 12)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    p1, q1, p2, q2 = nz(-3, 3), nz(-3, 3), nz(-3, 3), nz(-3, 3)
    need(p1 * q2 - p2 * q1 != 0)
    av, bv = nz(-5, 5), nz(-5, 5)
    r1, r2 = nz(-3, 3), nz(-3, 3)
    e1, e2 = eq_tex(p1, q1, p1 * sx + q1 * sy), eq_tex(p2, q2, p2 * sx + q2 * sy)
    f1 = f"ax {sgn(r1)}y = {av * sx + r1 * sy}".replace("+ 1y", "+ y").replace("- 1y", "- y")
    f2 = f"{lin(r2, 0, 0)} + by = {r2 * sx + bv * sy}"
    combo = R.choice([("a + b", av + bv), ("a - b", av - bv), ("ab", av * bv)])
    st = ["해가 같으니까 a, b가 없는 두 식끼리 먼저 연립해요"]
    st += elim_steps(p1, q1, p1 * sx + q1 * sy, p2, q2, p2 * sx + q2 * sy, sx, sy)
    ca = av * sx + r1 * sy
    st += [f"x = {sx}, y = {sy}를 a가 있는 식에 넣으면: a×({sx}) + ({r1})×({sy}) = {ca} → {lin(sx, 0, 0, 'a', 'z', tex=False)} = {ca} - ({r1 * sy}) = {ca - r1 * sy}",
           f"a = [[{av}]]"]
    cb = r2 * sx + bv * sy
    st += [f"b가 있는 식에 넣으면: ({r2})×({sx}) + b×({sy}) = {cb} → {lin(sy, 0, 0, 'b', 'z', tex=False)} = {cb} - ({r2 * sx}) = {cb - r2 * sx}",
           f"b = [[{bv}]]"]
    sym = {"a + b": f"({av}) + ({bv})", "a - b": f"({av}) - ({bv})", "ab": f"({av}) × ({bv})"}[combo[0]]
    st.append(f"{combo[0]} = {sym} = [[{combo[1]}]]")
    return P("어려움", f"두 연립방정식 $\\begin{{cases}} {e1} \\\\ {f1} \\end{{cases}}$, $\\begin{{cases}} {e2} \\\\ {f2} \\end{{cases}}$의 해가 서로 같을 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.",
             combo[1], hint="문자 a, b가 없는 두 식끼리 먼저 연립해서 해를 구해요.",
             explanation=f"{lin(p1, q1, tex=False)} = {p1 * sx + q1 * sy}, {lin(p2, q2, tex=False)} = {p2 * sx + q2 * sy} → x = {sx}, y = {sy} → a = {av}, b = {bv}",
             steps=st, detail=[f"네 식 모두 x = {sx}, y = {sy}에서 성립해요 ✔", f"답: {combo[1]}"])


def word_sys(level, q, ans_, hint, ex, var_def, setup, eqs, sx, sy, final=None, answer_txt=""):
    """연립방정식 활용: 미지수 정하기 → 식 세우기 → (정리) → 가감법 → 답 → 확인."""
    st = [f"미지수 정하기: {var_def}"] + [f"식 세우기 {s_}" for s_ in setup] + ["정리하면"]
    st += elim_steps(*eqs[0], *eqs[1], sx, sy)
    if final:
        st.append(final)
    return P(level, q, ans_, hint=hint, explanation=ex, steps=st,
             detail=chk_sys(eqs, sx, sy) + [f"답: {answer_txt or ans_}"])


@kind(U, "연립방정식의 활용: 수", 14)
def _():
    if R.random() < 0.5:
        s_ = R.randint(20, 90)
        k, d = R.randint(2, 5), R.randint(-6, 8)
        need(d != 0)
        sm = [(i, s_ - i) for i in range(1, s_) if s_ - i == k * i + d and s_ - i > i]
        need(sm)
        sml, bg = sm[0]
        return word_sys("보통", f"합이 {s_}인 두 자연수가 있다. 큰 수는 작은 수의 {k}배보다 {abs(d)}만큼 {'크다' if d >= 0 else '작다'}. 큰 수를 구하시오.", bg,
                        "작은 수 x, 큰 수 y로 놓고 두 식을 세워요.", f"x + y = {s_}, y = {k}x {sgn(d)} → x = {sml}, y = {bg}",
                        "작은 수를 x, 큰 수를 y라 해요",
                        [f"① 두 수의 합: x + y = {s_}", f"② 큰 수 = 작은 수의 {k}배 {'+' if d > 0 else '-'} {abs(d)}: y = {fx(k, d, tex=False)} → {lin(-k, 1, tex=False)} = {d}"],
                        [(1, 1, s_), (-k, 1, d)], sml, bg, answer_txt=f"큰 수 {bg}")
    t_, u = R.randint(1, 9), R.randint(1, 9)
    need(t_ != u)
    diff = 9 * (u - t_)
    word = "크다" if diff > 0 else "작다"
    return word_sys("어려움", f"각 자리 숫자의 합이 {t_ + u}인 두 자리 자연수가 있다. 십의 자리 숫자와 일의 자리 숫자를 바꾼 수는 처음 수보다 {abs(diff)}만큼 {word}. 처음 수를 구하시오.",
                    10 * t_ + u, "십의 자리 x, 일의 자리 y이면 처음 수는 10x + y, 바꾼 수는 10y + x예요.",
                    f"x + y = {t_ + u}, (10y + x) - (10x + y) = {diff} → x = {t_}, y = {u}",
                    "십의 자리 숫자를 x, 일의 자리 숫자를 y라 하면 처음 수는 10x + y, 바꾼 수는 10y + x",
                    [f"① 자리 숫자의 합: x + y = {t_ + u}",
                     f"② (바꾼 수) - (처음 수) = {diff}: (10y + x) - (10x + y) = {diff} → -9x + 9y = {diff} → 양변을 9로 나누면 -x + y = {u - t_}"],
                    [(1, 1, t_ + u), (-1, 1, u - t_)], t_, u, final=f"처음 수 = 10×{t_} + {u} = [[{10 * t_ + u}]]")


@kind(U, "연립방정식의 활용: 개수와 가격", 14)
def _():
    if R.random() < 0.5:
        p1, p2 = R.choice([(2, 3), (3, 4), (3, 5), (4, 6), (2, 5)])
        n1, n2 = R.randint(3, 20), R.randint(3, 20)
        tot, sc = n1 + n2, p1 * n1 + p2 * n2
        return word_sys("보통", f"어느 시험에서 {p1}점짜리와 {p2}점짜리 문제를 합하여 {tot}개를 모두 맞혀 {sc}점을 받았다. {p2}점짜리 문제는 몇 개인지 구하시오.", n2,
                        f"{p1}점짜리 x개, {p2}점짜리 y개로 놓아요.", f"x + y = {tot}, {p1}x + {p2}y = {sc} → x = {n1}, y = {n2}",
                        f"{p1}점짜리 문제를 x개, {p2}점짜리 문제를 y개라 해요",
                        [f"① 문제 수: x + y = {tot}", f"② 점수: {p1}x + {p2}y = {sc}"],
                        [(1, 1, tot), (p1, p2, sc)], n1, n2, answer_txt=f"{p2}점짜리 {n2}개")
    p1, p2 = R.choice([(3000, 2000), (5000, 3000), (4000, 2500), (6000, 3500), (2000, 1200)])
    n1, n2 = R.randint(2, 15), R.randint(2, 15)
    tot, money = n1 + n2, p1 * n1 + p2 * n2
    who = R.choice([("어른", "청소년"), ("어른", "어린이")])
    return word_sys("보통", f"어느 미술관의 입장료는 {who[0]} {p1}원, {who[1]} {p2}원이다. {josa(who[0], '과')} {josa(who[1], '을')} 합하여 {tot}명이 입장하고 {money}원을 냈을 때, {josa(who[1], '은')} 몇 명인지 구하시오.", n2,
                    f"{who[0]} x명, {who[1]} y명으로 놓아요.", f"x + y = {tot}, {p1}x + {p2}y = {money} → x = {n1}, y = {n2}",
                    f"{josa(who[0], '을')} x명, {josa(who[1], '을')} y명이라 해요",
                    [f"① 사람 수: x + y = {tot}", f"② 입장료: {p1}x + {p2}y = {money}"],
                    [(1, 1, tot), (p1, p2, money)], n1, n2, answer_txt=f"{who[1]} {n2}명")


@kind(U, "연립방정식의 활용: 나이", 12)
def _():
    child = R.randint(4, 15)
    k = R.randint(2, 4)
    d = R.randint(0, 8)
    adult = k * child + d
    yrs = R.randint(3, 12)
    m = R.randint(2, 3)
    extra = (adult + yrs) - m * (child + yrs)
    need(extra >= 0 and m < k)
    who = R.choice(["이모", "삼촌", "아버지", "어머니", "고모"])
    first = f"조카의 나이의 {k}배" + (f"보다 {d}살 많고" if d else "이고")
    c2 = m * yrs + extra - yrs
    return word_sys("보통", f"현재 {who}의 나이는 {first}, {yrs}년 후에는 {who}의 나이가 조카의 나이의 {m}배{'보다 ' + str(extra) + '살 많아진다' if extra else '가 된다'}. 현재 조카의 나이를 구하시오.",
                    child, f"현재 {who} x살, 조카 y살. {yrs}년 후에는 둘 다 {yrs}살씩 많아져요.",
                    f"x = {k}y {sgn(d)}, x + {yrs} = {m}(y + {yrs}) {sgn(extra)} → y = {child}, x = {adult}",
                    f"현재 {who}의 나이를 x살, 조카의 나이를 y살이라 해요 ({yrs}년 후엔 x + {yrs}, y + {yrs})",
                    [f"① 현재: x = {fx(k, d, 'y', tex=False)} → {lin(1, -k, tex=False)} = {d}",
                     f"② {yrs}년 후: x + {yrs} = {m}(y + {yrs})" + (f" + {extra}" if extra else "") + f" → 괄호를 풀고 옮기면 {lin(1, -m, tex=False)} = {c2}"],
                    [(1, -k, d), (1, -m, c2)], adult, child, answer_txt=f"조카 {child}살")


@kind(U, "연립방정식의 활용: 도형", 12)
def _():
    if R.random() < 0.6:
        h = R.randint(3, 20)
        k, d = R.randint(2, 3), R.randint(-5, 6)
        w = k * h + d
        need(w > 0 and w != h)
        P_ = 2 * (w + h)
        rel = f"세로의 길이의 {k}배보다 {abs(d)} cm {'길' if d > 0 else '짧'}" if d else f"세로의 길이의 {k}배"
        return word_sys("보통", f"둘레의 길이가 {P_} cm인 직사각형이 있다. 가로의 길이가 {rel}{'을' if d else '일'} 때, 이 직사각형의 넓이를 구하시오.".replace("길을 때", "길 때").replace("짧을 때", "짧을 때"),
                        w * h, "가로 x cm, 세로 y cm로 놓고 둘레와 길이 관계로 두 식을 세워요.",
                        f"2(x + y) = {P_}, x = {k}y {sgn(d)} → x = {w}, y = {h} → 넓이 {w * h}",
                        "가로를 x cm, 세로를 y cm라 해요",
                        [f"① 둘레: 2(x + y) = {P_} → 양변을 2로 나누면 x + y = {P_ // 2}",
                         f"② 가로와 세로의 관계: x = {fx(k, d, 'y', tex=False)} → {lin(1, -k, tex=False)} = {d}"],
                        [(1, 1, P_ // 2), (1, -k, d)], w, h, final=f"넓이 = 가로 × 세로 = {w} × {h} = [[{w * h}]] cm²", answer_txt=f"{w * h} cm²")
    bt = R.randint(6, 16)
    d = R.randint(1, bt - 2)
    tp = bt - d
    hgt = R.randint(2, 10)
    S = (tp + bt) * hgt
    need(S % 2 == 0)
    S //= 2
    return word_sys("보통", f"윗변의 길이가 아랫변의 길이보다 {d} cm 짧고 높이가 {hgt} cm인 사다리꼴의 넓이가 {S} cm²일 때, 아랫변의 길이를 구하시오.", bt,
                    "윗변 x cm, 아랫변 y cm로 놓아요. 사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2",
                    f"x = y - {d}, (x + y) × {hgt} ÷ 2 = {S} → y = {bt}",
                    "윗변을 x cm, 아랫변을 y cm라 해요",
                    [f"① 윗변 = 아랫변 - {d}: x = y - {d} → x - y = {-d}",
                     f"② 넓이: (x + y) × {hgt} ÷ 2 = {S} → 양변에 2를 곱하고 {hgt}로 나누면 x + y = {tp + bt}"],
                    [(1, -1, -d), (1, 1, tp + bt)], tp, bt, answer_txt=f"아랫변 {bt} cm")


@kind(U, "연립방정식의 활용: 거리, 속력, 시간", 12)
def _():
    v1, v2 = R.choice([(2, 3), (3, 4), (2, 4), (3, 5), (4, 6), (3, 6)])
    d1 = R.randint(2, 9)
    extra = R.randint(1, 3)
    d2 = d1 + extra
    tt = F(d1, v1) + F(d2, v2)
    need(tt.denominator in (1, 2, 4, 3))
    h, mi = int(tt), int((tt - int(tt)) * 60)
    tstr = f"{h}시간" + (f" {mi}분" if mi else "")
    place = R.choice(["도서관", "할머니 댁", "공원", "산 정상"])
    sc = math.lcm(v1, v2, tt.denominator)
    e2 = (sc // v1, sc // v2, int(tt * sc))
    return word_sys("어려움", f"{place}에 갈 때는 시속 {v1} km로 걷고, 올 때는 갈 때보다 {extra} km 더 먼 길을 시속 {v2} km로 걸었더니 모두 {tstr}이 걸렸다. 갈 때 걸은 거리는 몇 km인지 구하시오.",
                    d1, "갈 때 x km, 올 때 y km. 시간 = 거리 ÷ 속력",
                    f"y = x + {extra}, x/{v1} + y/{v2} = {fpl(tt)} → x = {d1}",
                    f"갈 때 거리를 x km, 올 때 거리를 y km라 해요 (시간 = 거리 ÷ 속력, {tstr} = {fpl(tt)}시간)",
                    [f"① 올 때가 {extra} km 더 멀어요: y = x + {extra} → -x + y = {extra}",
                     f"② 걸린 시간: x/{v1} + y/{v2} = {fpl(tt)} → 양변에 {sc}를 곱하면 {lin(*e2[:2], tex=False)} = {e2[2]}"],
                    [(-1, 1, extra), e2], d1, d2, answer_txt=f"갈 때 {d1} km")


@kind(U, "연립방정식의 활용: 계단 오르기", 10)
def _():
    up, down = R.choice([(2, 1), (3, 1), (3, 2), (4, 1)])
    w, l = R.randint(3, 15), R.randint(2, 12)
    pos = up * w - down * l
    need(pos > 0)
    game = R.choice(["동전을 던져 앞면이 나오면", "주사위를 던져 짝수가 나오면"])
    other = "뒷면이 나오면" if "동전" in game else "홀수가 나오면"
    side = "앞면" if "동전" in game else "짝수"
    return word_sys("어려움", f"{game} {up}계단 올라가고, {other} {down}계단 내려가기로 했다. {w + l}번 던진 뒤 처음 위치보다 {pos}계단 위에 있었다. {game.split('나오면')[0].split('던져 ')[1]}는 몇 번 나왔는지 구하시오.".replace("앞면이 는", "앞면은").replace("짝수가 는", "짝수는"),
                    w, "올라간 횟수 x, 내려간 횟수 y로 놓아요.", f"x + y = {w + l}, {lin(up, -down, tex=False)} = {pos} → x = {w}, y = {l}",
                    f"{side}이 나온 횟수(올라간 횟수)를 x번, 내려간 횟수를 y번이라 해요".replace("짝수이", "짝수가"),
                    [f"① 던진 횟수: x + y = {w + l}", f"② 위치: 올라간 {up}x계단 - 내려간 {down}y계단 = {pos} → {lin(up, -down, tex=False)} = {pos}".replace(" 1x계단", " x계단").replace(" 1y계단", " y계단")],
                    [(1, 1, w + l), (up, -down, pos)], w, l, answer_txt=f"{w}번")


# ═════════════════════ 6. 일차함수와 그 그래프 ═════════════════════
U = "6. 일차함수와 그 그래프"
FUNC_FACTS = [("자연수 $x$보다 작은 짝수 $y$", False, "x = 5이면 y = 2, 4로 두 개예요"),
              ("자연수 $x$의 약수 $y$", False, "x = 4이면 y = 1, 2, 4로 여러 개예요"),
              ("자연수 $x$를 3으로 나눈 나머지 $y$", True, "나머지는 하나로 정해져요 (x = 5 → y = 2)"),
              ("한 변의 길이가 $x$ cm인 정사각형의 둘레 $y$ cm", True, "y = 4x로 하나로 정해져요"),
              ("절댓값이 $x$인 수 $y$", False, "x = 3이면 y = 3, -3 두 개예요"),
              ("$x$보다 큰 자연수 $y$", False, "x = 3이면 y = 4, 5, 6, …으로 여러 개예요"),
              ("자연수 $x$의 약수의 개수 $y$", True, "개수는 하나로 정해져요 (x = 6 → y = 4)"),
              ("시속 $x$ km로 2시간 동안 간 거리 $y$ km", True, "y = 2x로 하나로 정해져요"),
              ("자연수 $x$의 배수 $y$", False, "x = 2이면 y = 2, 4, 6, …으로 여러 개예요"),
              ("둘레가 $x$ cm인 직사각형의 넓이 $y$ cm²", False, "둘레가 같아도 가로, 세로에 따라 넓이가 달라요"),
              ("$x$살인 학생의 키 $y$ cm", False, "나이가 같아도 키는 사람마다 달라요"),
              ("1개에 500원인 사탕 $x$개의 값 $y$원", True, "y = 500x로 하나로 정해져요")]


def cx(r):
    """계수 쓰기: 3 -> 3, 5/4 -> (5/4)"""
    return fpl(r) if F(r).denominator == 1 else f"({fpl(r)})"


def pm(c):
    """상수항 붙이기: 3 -> ' + 3', -3 -> ' - 3', 0 -> ''"""
    return f" {sgn(c)}" if c else ""


def lin_solve(coef, const, target, v="a"):
    """coef·v + const = target 풀기 (줄 목록, 빈칸 포함)."""
    rhs = target - const
    lhs = lin(coef, 0, 0, v, "z", tex=False)
    val = fpl(F(rhs) / F(coef))
    out = []
    if const:
        out.append(f"{fpl(F(const))}을(를) 옮기면: {lhs} = {fpl(F(target))} - ({fpl(F(const))}) = " + (f"[[{val}]]" if coef == 1 else f"[[{fpl(F(rhs))}]]"))
    elif coef == 1:
        out.append(f"{v} = [[{val}]]")
    else:
        out.append(f"{lhs} = {fpl(F(rhs))}")
    if coef == -1:
        out.append(f"{v} = [[{val}]]")
    elif coef != 1:
        out.append(f"양변을 {fpl(F(coef))}로 나누면: {v} = [[{val}]]")
    return out


@kind(U, "함수의 뜻", 8)
def _():
    want = R.random() < 0.5
    why = {s_: (t_, w_) for s_, t_, w_ in FUNC_FACTS}
    ch, ans = pick(R.sample([(s_, t_) for s_, t_, _ in FUNC_FACTS], len(FUNC_FACTS)), want)
    return P("보통", f"다음 중 $y$가 $x$의 함수{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint="x 하나에 y가 꼭 하나씩만 정해지면 함수예요. 두 개 이상 나오거나 정해지지 않으면 함수가 아니에요.",
             explanation="x = 4 같은 수를 넣어서 y가 하나로 정해지는지 확인해요.",
             steps=["x에 3, 4, 5 같은 수를 직접 넣어서 y가 몇 개 나오는지 세어 봐요",
                    "y가 2개 이상 나오거나, 하나로 정해지지 않으면 함수가 아니에요",
                    "함수: x의 값 하나에 y의 값이 [[1]]개씩 정해지는 관계"],
             detail=["보기를 하나씩 확인해요 (⭕ = 함수):"] + judge_lines(ch, lambda c_: why[c_]) + [f"그래서 답은 {ans}"])


@kind(U, "함숫값", 12)
def _():
    frac_ = R.random() < 0.5
    if frac_:
        k_ = nz(-24, 24)
        x1, x2 = nz(-6, 6), nz(-6, 6)
        need(x1 != x2 and k_ % x1 == 0 and k_ % x2 == 0)
        ftx = f"\\frac{{{k_}}}{{x}}" if k_ > 0 else f"-\\frac{{{-k_}}}{{x}}"
        f_ = lambda t_: F(k_, t_)
        show = lambda t_: f"{k_}/{par(t_)}" if k_ > 0 else f"-{-k_}/{par(t_)}"
    else:
        k_ = F(nz(-6, 6), R.choice([1, 2, 3]))
        x1, x2 = nz(-6, 6), nz(-6, 6)
        need(x1 != x2)
        ftx = fx(k_, 0)
        f_ = lambda t_: k_ * t_
        show = lambda t_: mul(k_, t_)
    c1, c2 = R.choice([(1, 1), (1, -1), (2, 1)])
    v1, v2 = f_(x1), f_(x2)
    v = v1 * c1 + v2 * c2
    need(F(v).denominator <= 6)
    expr = f"{'2' if c1 == 2 else ''}f({x1}) {'+' if c2 > 0 else '-'} f({x2})"
    comb = f"{'2×' if c1 == 2 else ''}{par(fpl(v1))} {'+' if c2 > 0 else '-'} {par(fpl(v2))}"
    return P("쉬움", f"함수 $f(x) = {ftx}$에 대하여 ${expr}$의 값을 구하시오.", fpl(v),
             hint="x 자리에 그 수를 넣어 계산해요.", explanation=f"x = {x1}, {x2}를 차례로 넣어 계산하면 {fpl(v)}",
             steps=[f"f(■)는 식의 x 자리에 ■를 넣은 값이에요",
                    f"f({x1}) = {show(x1)} = [[{fpl(v1)}]]",
                    f"f({x2}) = {show(x2)} = [[{fpl(v2)}]]",
                    f"{expr} = {comb} = [[{fpl(v)}]]"],
             detail=[f"답: {fpl(v)}"])


@kind(U, "함숫값이 주어질 때 미지수 구하기", 12)
def _():
    if R.random() < 0.5:
        av = nz(-24, 24)
        x1, x2 = nz(-6, 6), nz(-6, 6)
        need(av % x1 == 0 and x1 != x2)
        v1, v2 = F(av, x1), F(av, x2)
        need(v2.denominator <= 3)
        return P("보통", f"함수 $f(x) = \\frac{{a}}{{x}}$에 대하여 $f({x1}) = {fpl(v1)}$일 때, $f({x2})$의 값을 구하시오.", fpl(v2),
                 hint=f"먼저 f({x1}) = {fpl(v1)} 에서 a를 구해요.", explanation=f"a/({x1}) = {fpl(v1)} → a = {av}, f({x2}) = {av}/({x2}) = {fpl(v2)}",
                 steps=[f"x = {x1}을 넣으면: f({x1}) = a/{par(x1)} = {fpl(v1)}",
                        f"양변에 {x1}을 곱하면: a = {fpl(v1)} × {par(x1)} = [[{av}]]",
                        f"그래서 f(x) = {av}/x",
                        f"f({x2}) = {av}/{par(x2)} = [[{fpl(v2)}]]"],
                 detail=[f"확인: f({x1}) = {av}/{par(x1)} = {fpl(v1)} ✔", f"답: {fpl(v2)}"])
    av = nz(-6, 6)
    x1, x2 = nz(-5, 5), nz(-5, 5)
    need(x1 != x2)
    return P("보통", f"함수 $f(x) = ax$에 대하여 $f({x1}) = {av * x1}$일 때, $f({x2})$의 값을 구하시오.", av * x2,
             hint=f"먼저 f({x1}) = {av * x1} 에서 a를 구해요.", explanation=f"{x1}a = {av * x1} → a = {av}, f({x2}) = {av * x2}",
             steps=[f"x = {x1}을 넣으면: f({x1}) = a×{par(x1)} = {av * x1}"] + lin_solve(x1, 0, av * x1)
                   + [f"그래서 f(x) = {fx(av, 0, tex=False)}", f"f({x2}) = {mul(av, x2)} = [[{av * x2}]]"],
             detail=[f"답: {av * x2}"])


LIN_POOL = [("$y = 3x - 1$", True, "y = (x의 일차식) 꼴이에요"), ("$y = \\frac{2}{x}$", False, "x가 분모에 있어요"),
            ("$y = x^{2} + 1$", False, "x²이 있어 일차식이 아니에요"), ("$y = 5$", False, "x가 없어요"),
            ("$y = x(x - 2) - x^{2}$", True, "괄호를 풀면 x²이 지워져 y = -2x"),
            ("$y = 2(x + 1) - 2x$", False, "괄호를 풀면 y = 2, x가 지워져요"), ("$y = -\\frac{x}{4}$", True, "y = -(1/4)x 꼴이에요"),
            ("$y = \\frac{1}{x} + 1$", False, "x가 분모에 있어요"), ("$xy = 6$", False, "y = 6/x, x가 분모에 있어요"),
            ("$y = 0.5x + 2$", True, "계수가 소수일 뿐 y = (x의 일차식)이에요"), ("$y - x = x + 3$", True, "x를 옮기면 y = 2x + 3"),
            ("$y = x^{2} - x(x + 1)$", True, "괄호를 풀면 x²이 지워져 y = -x"), ("$y = 3 - x^{2}$", False, "x²이 있어 일차식이 아니에요")]


@kind(U, "일차함수의 뜻", 8)
def _():
    want = R.random() < 0.6
    why = {s_: (t_, w_) for s_, t_, w_ in LIN_POOL}
    ch, ans = pick(R.sample([(s_, t_) for s_, t_, _ in LIN_POOL], len(LIN_POOL)), want)
    return P("보통", f"다음 중 $y$가 $x$에 대한 일차함수{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint="정리해서 y = ax + b (a ≠ 0) 꼴이면 일차함수예요. 괄호는 먼저 풀어 봐요.",
             explanation="괄호를 풀고 y = (x에 대한 일차식) 꼴인지 확인해요. x²이 지워지면 일차함수일 수 있어요.",
             steps=["괄호를 풀고, y만 왼쪽에 남게 정리해요",
                    "x²이 남거나, x가 분모에 있거나, x가 없어지면 일차함수가 아니에요",
                    "y = ax + b 꼴이고 a ≠ [[0]]이면 일차함수"],
             detail=["보기를 하나씩 정리해 봐요 (⭕ = 일차함수):"] + judge_lines(ch, lambda c_: why[c_]) + [f"그래서 답은 {ans}"])


@kind(U, "일차함수의 함숫값", 12)
def _():
    m, c = nz(-5, 5), R.randint(-9, 9)
    av = nz(-6, 6)
    target = m * av + c
    if R.random() < 0.5:
        return P("쉬움", f"일차함수 $f(x) = {fx(m, c)}$에 대하여 $f(a) = {target}$일 때, $a$의 값을 구하시오.", av,
                 hint="x 자리에 a를 넣고 방정식을 풀어요.", explanation=f"{m}a {sgn(c)} = {target} → a = {av}",
                 steps=[f"x 자리에 a를 넣으면: f(a) = {fx(m, c, 'a', tex=False)} = {target}"] + lin_solve(m, c, target),
                 detail=[f"확인: f({av}) = {mul(m, av)}{pm(c)} = {target} ✔", f"답: a = {av}"])
    p_ = nz(-4, 4)
    q_ = R.randint(-9, 9)
    k = nz(-4, 4)
    t_ = p_ * k + q_
    return P("쉬움", f"일차함수 $f(x) = ax {sgn(q_)}$에 대하여 $f({k}) = {t_}$일 때, 수 $a$의 값을 구하시오.".replace(" + 0$", "$"), p_,
             hint="x 자리에 수를 넣으면 a에 대한 방정식이 돼요.", explanation=f"{k}a {sgn(q_)} = {t_} → a = {p_}",
             steps=[f"x = {k}을 넣으면: f({k}) = a×{par(k)}{pm(q_)} = {t_}", f"{lin(k, 0, q_, 'a', 'z', tex=False)} = {t_}"] + lin_solve(k, q_, t_),
             detail=[f"확인: f({k}) = {mul(p_, k)}{pm(q_)} = {t_} ✔", f"답: a = {p_}"])


@kind(U, "일차함수의 그래프의 평행이동", 12)
def _():
    m, c0, sh = nz(-5, 5), R.randint(-6, 6), nz(-7, 7)
    right = f"$y = {fx(m, c0 + sh)}$"
    wr = [f"$y = {fx(-m, c0 + sh)}$", f"$y = {fx(m, c0 - sh)}$", f"$y = {fx(m + sh, c0)}$", f"$y = {fx(m, -(c0 + sh))}$", f"$y = {fx(c0 + sh, m)}$"]
    ch, ans = mc(right, wr)
    return P("쉬움", f"일차함수 $y = {fx(m, c0)}$의 그래프를 $y$축의 방향으로 ${sh}$만큼 평행이동한 그래프의 식은?", ans, choices=ch,
             hint="y축 방향으로 k만큼 평행이동하면 상수항에 k를 더해요. 기울기는 그대로예요.",
             explanation=f"y = {fx(m, c0, tex=False)} {sgn(sh)} → y = {fx(m, c0 + sh, tex=False)}",
             steps=["y축 방향으로 k만큼 평행이동: y = ax + b → y = ax + b + k",
                    f"기울기(x의 계수)는 그대로: [[{m}]]",
                    f"상수항에 {sh}를 더해요: {c0} + ({sh}) = [[{c0 + sh}]]"],
             detail=[f"평행이동한 식: y = {fx(m, c0 + sh, tex=False)}", f"그래서 답은 {ans}"])


@kind(U, "평행이동한 그래프 위의 점", 12)
def _():
    m, c, sh, px = nz(-5, 5), R.randint(-6, 6), nz(-6, 6), nz(-4, 4)
    yy = m * px + c + sh
    if R.random() < 0.5:
        return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프를 $y$축의 방향으로 ${sh}$만큼 평행이동한 그래프가 점 $({px}, k)$를 지날 때, $k$의 값을 구하시오.", yy,
                 hint="먼저 평행이동한 식을 구한 뒤 x에 대입해요.", explanation=f"y = {fx(m, c + sh, tex=False)}, x = {px} → k = {yy}",
                 steps=[f"평행이동하면 상수항에 {sh}를 더해요: {c} + ({sh}) = [[{c + sh}]]",
                        f"평행이동한 식: y = {fx(m, c + sh, tex=False)}",
                        f"점 ({px}, k)를 지나니까 x = {px}를 넣으면: k = {mul(m, px)}{pm(c + sh)} = [[{yy}]]"],
                 detail=[f"답: k = {yy}"])
    return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프를 $y$축의 방향으로 $p$만큼 평행이동하였더니 점 $({px}, {yy})$를 지났다. $p$의 값을 구하시오.", sh,
             hint="평행이동한 식 y = (기울기)x + (상수항 + p)에 점을 대입해요.", explanation=f"{yy} = {m}×({px}) {sgn(c)} + p → p = {sh}",
             steps=[f"평행이동한 식: y = {fx(m, c, tex=False)} + p",
                    f"점 ({px}, {yy})를 넣으면: {yy} = {mul(m, px)}{pm(c)} + p",
                    f"계산하면: {yy} = {m * px + c} + p",
                    f"p = {yy} - ({m * px + c}) = [[{sh}]]"],
             detail=[f"확인: y = {fx(m, c + sh, tex=False)}에 x = {px}를 넣으면 {yy} ✔", f"답: p = {sh}"])


@kind(U, "x절편과 y절편", 12)
def _():
    m = F(nz(-6, 6), R.choice([1, 1, 2, 3, 4]))
    c = nz(-9, 9)
    xi = F(-c) / m
    need(xi.denominator <= 4)
    combo = R.choice([("p + q", xi + c, "+"), ("p - q", xi - c, "-"), ("pq", xi * c, "×")])
    return P("쉬움", f"일차함수 $y = {fx(m, c)}$의 그래프의 $x$절편을 $p$, $y$절편을 $q$라 할 때, ${combo[0]}$의 값을 구하시오.", fpl(combo[1]),
             hint="x절편: y = 0일 때 x의 값, y절편: x = 0일 때 y의 값",
             explanation=f"p = {fpl(xi)}, q = {c} → {combo[0]} = {fpl(combo[1])}",
             steps=[f"x절편은 y = 0일 때 x: 0 = {fx(m, c, tex=False)} → {fx(m, 0, tex=False)} = {-c}",
                    f"x = {-c} ÷ {par(fpl(m))} = [[{fpl(xi)}]] → p = {fpl(xi)}",
                    f"y절편은 x = 0일 때 y (상수항): q = [[{c}]]",
                    f"{combo[0]} = {par(fpl(xi))} {combo[2]} {par(c)} = [[{fpl(combo[1])}]]"],
             detail=[f"그래프는 점 ({fpl(xi)}, 0)과 (0, {c})를 지나요", f"답: {fpl(combo[1])}"])


@kind(U, "절편을 이용하여 미지수 구하기", 12)
def _():
    m = F(nz(-6, 6), R.choice([1, 2, 3]))
    xi = nz(-8, 8)
    k = -m * xi
    need(k.denominator == 1)
    if R.random() < 0.5:
        return P("보통", f"일차함수 $y = {fx(m, 0)} + k$의 그래프의 $x$절편이 ${xi}$일 때, 수 $k$의 값을 구하시오.", fpl(k),
                 hint=f"x절편이 {xi}이면 점 ({xi}, 0)을 지나요.", explanation=f"0 = {fpl(m)} × ({xi}) + k → k = {fpl(k)}",
                 steps=[f"x절편이 {xi} → 점 ({xi}, 0)을 지나요 (y = 0일 때 x = {xi})",
                        f"x = {xi}, y = 0을 넣으면: 0 = {fpl(m)} × {par(xi)} + k",
                        f"0 = [[{fpl(m * xi)}]] + k",
                        f"k = [[{fpl(k)}]]"],
                 detail=[f"확인: y = {fx(m, k, tex=False)}에 x = {xi}를 넣으면 y = 0 ✔", f"답: k = {fpl(k)}"])
    c = nz(-9, 9)
    m2 = F(-c, xi)
    need(m2.denominator <= 3 and m2 != 0)
    return P("보통", f"일차함수 $y = ax {sgn(c)}$의 그래프의 $x$절편이 ${xi}$일 때, 수 $a$의 값을 구하시오.", fpl(m2),
             hint=f"점 ({xi}, 0)을 대입해요.", explanation=f"0 = {xi}a {sgn(c)} → a = {fpl(m2)}",
             steps=[f"x절편이 {xi} → 점 ({xi}, 0)을 지나요",
                    f"x = {xi}, y = 0을 넣으면: 0 = a×{par(xi)} {sgn(c)}",
                    f"{c}을(를) 옮기면: {lin(xi, 0, 0, 'a', 'z', tex=False)} = [[{-c}]]",
                    f"a = {-c} ÷ {par(xi)} = [[{fpl(m2)}]]"],
             detail=[f"확인: y = {fx(m2, c, tex=False)}에 x = {xi}를 넣으면 y = 0 ✔", f"답: a = {fpl(m2)}"])


@kind(U, "일차함수의 그래프의 기울기", 12)
def _():
    m = F(nz(-6, 6), R.choice([1, 2, 3, 4]))
    c = nz(-5, 5)
    if R.random() < 0.5:
        dx = R.choice([m.denominator * k for k in (1, 2, 3, 4)])
        dy = m * dx
        return P("쉬움", f"일차함수 $y = {fx(m, c)}$의 그래프에서 $x$의 값이 {dx}만큼 증가할 때, $y$의 값의 증가량을 구하시오.", fpl(dy),
                 hint="(y의 값의 증가량) = (기울기) × (x의 값의 증가량)", explanation=f"{fpl(m)} × {dx} = {fpl(dy)}",
                 steps=["기울기 = (y의 값의 증가량) ÷ (x의 값의 증가량)",
                        f"y = ax + b에서 기울기는 x의 계수: [[{fpl(m)}]]",
                        f"y의 값의 증가량 = (기울기) × (x의 값의 증가량) = {par(fpl(m))} × {dx} = [[{fpl(dy)}]]"],
                 detail=[f"확인: x = 0이면 y = {c}, x = {dx}이면 y = {fpl(m * dx + c)} → 증가량 {fpl(dy)} ✔", f"답: {fpl(dy)}"])
    x1 = R.randint(-4, 3)
    x2 = x1 + R.randint(1, 6)
    dy = m * (x2 - x1)
    need(dy.denominator == 1)
    return P("쉬움", f"일차함수 $y = {fx(m, c)}$의 그래프에서 $x$의 값이 ${x1}$에서 ${x2}$까지 증가할 때, $y$의 값의 증가량을 구하시오.", fpl(dy),
             hint="x의 값의 증가량을 먼저 구한 뒤 기울기를 곱해요.", explanation=f"x 증가량 {x2 - x1}, × {fpl(m)} = {fpl(dy)}",
             steps=[f"x의 값의 증가량 = {x2} - {par(x1)} = [[{x2 - x1}]]",
                    f"기울기(x의 계수) = [[{fpl(m)}]]",
                    f"y의 값의 증가량 = {par(fpl(m))} × {x2 - x1} = [[{fpl(dy)}]]"],
             detail=[f"확인: x = {x1}이면 y = {fpl(m * x1 + c)}, x = {x2}이면 y = {fpl(m * x2 + c)} → {fpl(m * x2 + c)} - {par(fpl(m * x1 + c))} = {fpl(dy)} ✔", f"답: {fpl(dy)}"])


@kind(U, "두 점을 지나는 직선의 기울기", 14)
def _():
    x1, x2 = nz(-6, 6), nz(-6, 6)
    need(x1 != x2)
    sl = F(nz(-5, 5), R.choice([1, 1, 2]))
    y2 = R.randint(-9, 9)
    y1 = y2 + sl * (x1 - x2)
    need(y1.denominator == 1)
    y1 = int(y1)
    if R.random() < 0.4:
        return P("보통", f"두 점 $({x1}, {y1})$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 기울기를 구하시오.", fpl(sl),
                 hint="기울기 = (y의 값의 차) ÷ (x의 값의 차)", explanation=f"({y2} - ({y1})) ÷ ({x2} - ({x1})) = {fpl(sl)}",
                 steps=["기울기 = (y의 값의 증가량) ÷ (x의 값의 증가량) (같은 순서로 빼요)",
                        f"y의 값의 증가량: {y2} - {par(y1)} = [[{y2 - y1}]]",
                        f"x의 값의 증가량: {x2} - {par(x1)} = [[{x2 - x1}]]",
                        f"기울기 = {y2 - y1} ÷ {par(x2 - x1)} = [[{fpl(sl)}]]"],
                 detail=[f"거꾸로 빼도 같아요: ({y1} - {par(y2)}) ÷ ({x1} - {par(x2)}) = {fpl(sl)}", f"답: {fpl(sl)}"])
    return P("보통", f"두 점 $({x1}, k)$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 기울기가 ${ftex(sl)}$일 때, $k$의 값을 구하시오.", y1,
             hint="기울기 = (y의 값의 차) ÷ (x의 값의 차) 로 식을 세워요.", explanation=f"({y2} - k) ÷ ({x2} - ({x1})) = {fpl(sl)} → k = {y1}",
             steps=[f"기울기 = ({y2} - k) ÷ ({x2} - {par(x1)}) = {fpl(sl)}",
                    f"x의 값의 증가량: {x2} - {par(x1)} = [[{x2 - x1}]]",
                    f"{y2} - k = {par(fpl(sl))} × {par(x2 - x1)} = [[{y2 - y1}]]",
                    f"k = {y2} - {par(y2 - y1)} = [[{y1}]]"],
             detail=[f"확인: ({y2} - {par(y1)}) ÷ {par(x2 - x1)} = {fpl(sl)} ✔", f"답: k = {y1}"])


@kind(U, "그래프와 좌표축으로 둘러싸인 삼각형의 넓이", 12)
def _():
    m = F(nz(-5, 5), R.choice([1, 2, 3, 4]))
    c = nz(-9, 9)
    xi = F(-c) / m
    S = abs(xi) * abs(c) / 2
    need(S.denominator <= 2 and S <= 80)
    return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프와 $x$축 및 $y$축으로 둘러싸인 삼각형의 넓이를 구하시오.", fpl(S),
             hint="x절편과 y절편을 구하면 직각삼각형의 두 변 길이가 나와요.",
             explanation=f"x절편 {fpl(xi)}, y절편 {c} → {fpl(abs(xi))} × {abs(c)} ÷ 2 = {fpl(S)}",
             steps=[f"x절편: y = 0 → {fx(m, 0, tex=False)} = {-c} → x = [[{fpl(xi)}]]",
                    f"y절편: x = 0 → y = [[{c}]]",
                    f"삼각형은 원점, ({fpl(xi)}, 0), (0, {c})을 꼭짓점으로 하는 직각삼각형",
                    f"밑변 = |{fpl(xi)}| = {fpl(abs(xi))}, 높이 = |{c}| = {abs(c)} (길이는 양수)",
                    f"넓이 = {fpl(abs(xi))} × {abs(c)} ÷ 2 = [[{fpl(S)}]]"],
             detail=[f"답: {fpl(S)}"])


@kind(U, "세 점이 한 직선 위에 있을 조건", 12)
def _():
    x1, x2, x3 = R.sample(range(-6, 7), 3)
    sl = F(nz(-4, 4), R.choice([1, 2]))
    y1 = R.randint(-6, 6)
    y2, y3 = y1 + sl * (x2 - x1), y1 + sl * (x3 - x1)
    need(y2.denominator == 1 and y3.denominator == 1)
    y2, y3 = int(y2), int(y3)
    return P("어려움", f"세 점 $({x1}, {y1})$, $({x2}, {y2})$, $({x3}, k)$가 한 직선 위에 있을 때, $k$의 값을 구하시오.", y3,
             hint="어느 두 점을 골라도 기울기가 같아요.", explanation=f"기울기 {fpl(sl)} → k = {y1} + ({fpl(sl)}) × ({x3} - ({x1})) = {y3}",
             steps=["한 직선 위에 있으면 어느 두 점으로 구해도 기울기가 같아요",
                    f"두 점 ({x1}, {y1}), ({x2}, {y2})의 기울기: ({y2} - {par(y1)}) ÷ ({x2} - {par(x1)}) = {y2 - y1} ÷ {par(x2 - x1)} = [[{fpl(sl)}]]",
                    f"두 점 ({x1}, {y1}), ({x3}, k)의 기울기도 같아요: (k - {par(y1)}) ÷ ({x3} - {par(x1)}) = {fpl(sl)}",
                    f"k - {par(y1)} = {par(fpl(sl))} × {par(x3 - x1)} = [[{y3 - y1}]]",
                    f"k = {y3 - y1} + {par(y1)} = [[{y3}]]"],
             detail=[f"확인: ({y3} - {par(y2)}) ÷ ({x3} - {par(x2)}) = {fpl(sl)} ✔", f"답: k = {y3}"])


@kind(U, "일차함수의 그래프의 성질", 12)
def _():
    m = F(nz(-5, 5), R.choice([1, 2, 3]))
    c = nz(-8, 8)
    xi = F(-c) / m
    quads = {1, 3} if m > 0 else {2, 4}
    quads |= ({2} if (m > 0 and c > 0) else {4} if (m > 0 and c < 0) else {1} if (m < 0 and c > 0) else {3})
    miss = ({1, 2, 3, 4} - quads).pop()
    px = R.randint(-3, 3)
    py = m * px + c
    dx = m.denominator * R.randint(1, 2)
    up = "오른쪽 위" if m > 0 else "오른쪽 아래"
    qs = ", ".join(map(str, sorted(quads)))
    st = [(f"{up}로 향하는 직선이다.", True, f"기울기 {fpl(m)}이(가) {'양수' if m > 0 else '음수'}"),
          (f"{'오른쪽 아래' if m > 0 else '오른쪽 위'}로 향하는 직선이다.", False, f"기울기 {fpl(m)}이(가) {'양수' if m > 0 else '음수'}라 {up}로 향해요"),
          (f"$y$절편은 ${c}$이다.", True, f"x = 0이면 y = {c}"), (f"$y$절편은 ${-c}$이다.", False, f"y절편은 {c}예요"),
          (f"$x$절편은 ${ftex(xi)}$이다.", True, f"y = 0이면 x = {fpl(xi)}"), (f"$x$절편은 ${ftex(-xi)}$이다.", False, f"x절편은 {fpl(xi)}예요"),
          (f"$y = {fx(m, 0)}$의 그래프와 평행하다.", True, "기울기가 같고 y절편이 달라요"),
          (f"$y = {fx(-m, c)}$의 그래프와 평행하다.", False, f"기울기가 {fpl(-m)}로 달라요"),
          (f"제{miss}사분면을 지나지 않는다.", True, f"제{qs}사분면만 지나요"), (f"제{miss}사분면을 지난다.", False, f"제{qs}사분면만 지나요"),
          (f"$x$의 값이 {dx}만큼 증가할 때 $y$의 값은 {fpl(abs(m * dx))}만큼 {'증가' if m > 0 else '감소'}한다.", True, f"{fpl(m)} × {dx} = {fpl(m * dx)}"),
          (f"$x$의 값이 {dx}만큼 증가할 때 $y$의 값은 {fpl(abs(m * dx))}만큼 {'감소' if m > 0 else '증가'}한다.", False, f"{fpl(m)} × {dx} = {fpl(m * dx)} → {'증가' if m > 0 else '감소'}해요"),
          (f"점 $({px}, {fpl(py)})$를 지난다.", True, f"x = {px}이면 y = {fpl(py)}"),
          (f"점 $({px}, {fpl(py + 1)})$를 지난다.", False, f"x = {px}이면 y = {fpl(py)}예요")]
    why = {s_: (t_, w_) for s_, t_, w_ in st}
    want = R.random() < 0.5
    ch, ans = pick([(s_, t_) for s_, t_, _ in st], want)
    return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프에 대한 설명으로 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="기울기가 양수면 오른쪽 위, 음수면 오른쪽 아래. y절편으로 y축과 만나는 곳을 봐요.",
             explanation=f"기울기 {fpl(m)}, y절편 {c}, x절편 {fpl(xi)} → 제{qs}사분면을 지나요.",
             steps=[f"기울기 = [[{fpl(m)}]] → {up}로 향하는 직선",
                    f"y절편 = [[{c}]] (x = 0일 때 y)",
                    f"x절편: 0 = {fx(m, c, tex=False)} → x = [[{fpl(xi)}]]"],
             detail=[f"두 점 ({fpl(xi)}, 0), (0, {c})를 지나는 직선 → 제{qs}사분면을 지나요",
                     "보기를 하나씩 확인해요 (⭕ = 옳은 설명):"] + judge_lines(ch, lambda c_: why[c_]) + [f"그래서 {'옳은' if want else '옳지 않은'} 것은 {ans}"])


@kind(U, "y = ax + b의 그래프와 a, b의 부호", 8)
def _():
    sa, sb = R.choice([1, -1]), R.choice([1, -1])
    quads = ({1, 3} if sa > 0 else {2, 4}) | ({2} if (sa > 0 and sb > 0) else {4} if (sa > 0 and sb < 0) else {1} if (sa < 0 and sb > 0) else {3})

    def s(v):
        return "> 0" if v > 0 else "< 0"
    right = f"$a {s(sa)}$, $b {s(sb)}$"
    wr = [f"$a {s(p_)}$, $b {s(q_)}$" for p_ in (1, -1) for q_ in (1, -1) if (p_, q_) != (sa, sb)] + ["$a = 0$, $b > 0$", "$a = 0$, $b < 0$"]
    ch, ans = mc(right, wr)
    qs = ", ".join(map(str, sorted(quads)))
    dirn = "오른쪽 위" if sa > 0 else "오른쪽 아래"
    hi = "위" if sb > 0 else "아래"
    if R.random() < 0.5:
        q = f"일차함수 $y = ax + b$의 그래프가 제{qs}사분면을 지날 때, $a$, $b$의 부호는?"
        how = [f"제{qs}사분면을 지나는 직선을 그려 보면: {dirn}로 향하고, y축과 원점보다 {hi}쪽에서 만나요"]
    else:
        q = f"일차함수 $y = ax + b$의 그래프가 {dirn}로 향하고, $y$축과 원점보다 {hi}쪽에서 만날 때, $a$, $b$의 부호는?"
        how = []
    return P("어려움", q, ans, choices=ch, hint="오른쪽 위로 향하면 a > 0, y절편이 원점보다 위면 b > 0",
             explanation=f"기울기 a {s(sa)}, y절편 b {s(sb)}",
             steps=["a는 기울기: 오른쪽 위로 향하면 a > 0, 오른쪽 아래로 향하면 a < 0",
                    "b는 y절편: y축과 원점보다 위에서 만나면 b > 0, 아래에서 만나면 b < 0"] + how
                   + [f"{dirn}로 향하니까 a [[{s(sa)}]]", f"원점보다 {hi}에서 만나니까 b [[{s(sb)}]]"],
             detail=[f"그래서 답은 {ans}"])


@kind(U, "일차함수의 그래프의 평행과 일치", 12)
def _():
    k = nz(-4, 4, (0,))
    m = nz(-8, 8)
    need(m % k == 0)
    if R.random() < 0.5:
        c1, c2 = R.randint(-6, 6), R.randint(-6, 6)
        need(c1 != c2)
        return P("보통", f"두 일차함수 $y = {k if k not in (1, -1) else ('-' if k < 0 else '')}ax {sgn(c1)}$, $y = {fx(m, c2)}$의 그래프가 평행할 때, 수 $a$의 값을 구하시오.".replace(" + 0$", "$"), m // k,
                 hint="두 직선이 평행하면 기울기가 같고 y절편은 달라요.", explanation=f"{k}a = {m} → a = {m // k}",
                 steps=["두 직선이 평행하면 기울기가 같고 y절편은 달라요",
                        f"첫째 식의 기울기 {lin(k, 0, 0, 'a', 'z', tex=False)}, 둘째 식의 기울기 [[{m}]]",
                        f"{lin(k, 0, 0, 'a', 'z', tex=False)} = {m}"] + ([f"양변을 {k}로 나누면: a = [[{m // k}]]"] if k != 1 else [f"a = [[{m}]]"]),
                 detail=[f"확인: 기울기 {mul(k, m // k)} = {m}로 같고, y절편 {c1} ≠ {c2} → 평행 ✔", f"답: a = {m // k}"])
    av, bv = nz(-6, 6), nz(-9, 9)
    c2 = R.randint(-9, 9)
    bval = bv - c2
    combo = R.choice([("a + b", av + bval, f"({av}) + ({bval})"), ("ab", av * bval, f"({av}) × ({bval})"), ("a - b", av - bval, f"({av}) - ({bval})")])
    return P("보통", f"두 일차함수 $y = ax {sgn(bv)}$와 $y = {fx(av, 0)} + b {sgn(c2)}$의 그래프가 일치할 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.".replace(" + 0$", "$"),
             combo[1], hint="일치하면 기울기도 같고 y절편도 같아요.",
             explanation=f"a = {av}, b {sgn(c2)} = {bv} → b = {bval} → {combo[0]} = {combo[1]}",
             steps=["두 직선이 일치하면 기울기도 같고 y절편도 같아요",
                    f"기울기: a = [[{av}]]",
                    f"y절편: b{pm(c2)} = {bv}" + (f" → b = {bv} - ({c2}) = [[{bval}]]" if c2 else f" → b = [[{bval}]]"),
                    f"{combo[0]} = {combo[2]} = [[{combo[1]}]]"],
             detail=[f"두 식 모두 y = {fx(av, bv, tex=False)} ✔", f"답: {combo[1]}"])


def line_mc(m, c, q, hint, ex, steps, level="보통"):
    right = f"$y = {fx(m, c)}$"
    wr = [f"$y = {fx(-m, c)}$", f"$y = {fx(m, -c)}$" if c else f"$y = {fx(m, 2)}$", f"$y = {fx(m, c + 1)}$", f"$y = {fx(m, c - 1)}$",
          f"$y = {fx(m * 2, c)}$", f"$y = {fx(c, m)}$" if c and c != m else f"$y = {fx(m, c + 3)}$", f"$y = {fx(-m, -c)}$"]
    ch, ans = mc(right, wr)
    return P(level, q, ans, choices=ch, hint=hint, explanation=ex, steps=steps,
             detail=[f"y = ax + b에 a = {fpl(m)}, b = {fpl(c)}를 넣으면: y = {fx(m, c, tex=False)}", f"그래서 답은 {ans}"])


@kind(U, "일차함수의 식 구하기: 기울기와 y절편", 12)
def _():
    dx = R.randint(1, 4)
    m = F(nz(-4, 4), R.choice([1, 1, 2]))
    dy = m * dx
    need(dy.denominator == 1 and dy != 0)
    c = nz(-7, 7)
    if R.random() < 0.5:
        q = f"$x$의 값이 {dx}만큼 증가할 때 $y$의 값은 {abs(int(dy))}만큼 {'증가' if dy > 0 else '감소'}하고, $y$절편이 ${c}$인 직선을 그래프로 하는 일차함수의 식은?"
        st = [f"기울기 = (y의 증가량) ÷ (x의 증가량) = {int(dy)} ÷ {dx} = [[{fpl(m)}]]" + (" (감소는 음수)" if dy < 0 else ""),
              f"y절편 b = [[{c}]]"]
    else:
        m0 = R.choice([1, 2, -1, 3])
        need(m0 != c)
        q = f"일차함수 $y = {fx(m, m0)}$의 그래프와 평행하고, $y$절편이 ${c}$인 직선을 그래프로 하는 일차함수의 식은?"
        st = ["평행한 두 직선은 기울기가 같아요", f"기울기 = [[{fpl(m)}]]", f"y절편 b = [[{c}]]"]
    return line_mc(m, c, q, "기울기 = (y의 증가량) ÷ (x의 증가량), 평행하면 기울기가 같아요.", f"기울기 {fpl(m)}, y절편 {c} → y = {fx(m, c, tex=False)}",
                   ["구하는 식을 y = ax + b로 놓아요 (a: 기울기, b: y절편)"] + st)


@kind(U, "일차함수의 식 구하기: 기울기와 한 점", 12)
def _():
    m = F(nz(-4, 4), R.choice([1, 1, 2]))
    px, py = nz(-4, 4), R.randint(-6, 6)
    c = py - m * px
    need(c.denominator == 1)
    x1 = R.randint(-3, 3)
    dx = R.randint(1, 4)
    dy = m * dx
    need(dy.denominator == 1)
    q = f"$x$의 값이 ${x1}$에서 ${x1 + dx}$까지 증가할 때 $y$의 값은 {abs(int(dy))}만큼 {'증가' if dy > 0 else '감소'}하고, 점 $({px}, {py})$를 지나는 직선을 그래프로 하는 일차함수의 식은?"
    return line_mc(m, c, q, "기울기를 먼저 구하고, y = ax + b에 점을 대입해 b를 구해요.", f"기울기 {fpl(m)}, {py} = {fpl(m)} × ({px}) + b → b = {fpl(c)}",
                   [f"x의 증가량: {x1 + dx} - {par(x1)} = {dx}",
                    f"기울기 = {int(dy)} ÷ {dx} = [[{fpl(m)}]]",
                    f"y = {fx(m, 0, tex=False)} + b에 점 ({px}, {py})를 넣으면: {py} = {par(fpl(m))} × {par(px)} + b",
                    f"{py} = {fpl(m * px)} + b → b = {py} - {par(fpl(m * px))} = [[{fpl(c)}]]"])


@kind(U, "일차함수의 식 구하기: 두 점", 12)
def _():
    x1, x2 = nz(-5, 5), nz(-5, 5)
    need(x1 != x2)
    m = F(nz(-4, 4), R.choice([1, 1, 2]))
    y1 = R.randint(-6, 6)
    y2 = y1 + m * (x2 - x1)
    c = y1 - m * x1
    need(y2.denominator == 1 and c.denominator == 1)
    y2 = int(y2)
    return line_mc(m, c, f"두 점 $({x1}, {y1})$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 식은?",
                   "두 점으로 기울기를 구한 뒤 한 점을 대입해 y절편을 구해요.", f"기울기 {fpl(m)}, {y1} = {fpl(m)} × ({x1}) + b → b = {fpl(c)}",
                   [f"기울기 = ({y2} - {par(y1)}) ÷ ({x2} - {par(x1)}) = {y2 - y1} ÷ {par(x2 - x1)} = [[{fpl(m)}]]",
                    f"y = {fx(m, 0, tex=False)} + b에 점 ({x1}, {y1})을 넣으면: {y1} = {par(fpl(m))} × {par(x1)} + b",
                    f"{y1} = {fpl(m * x1)} + b → b = {y1} - {par(fpl(m * x1))} = [[{fpl(c)}]]"])


@kind(U, "일차함수의 식 구하기: x절편과 y절편", 12)
def _():
    xi, yi = nz(-6, 6), nz(-6, 6)
    m = F(-yi, xi)
    return line_mc(m, yi, f"$x$절편이 ${xi}$, $y$절편이 ${yi}$인 직선을 그래프로 하는 일차함수의 식은?",
                   f"두 점 ({xi}, 0), (0, {yi})를 지나요. 기울기 = -(y절편) ÷ (x절편)", f"기울기 ({yi} - 0) ÷ (0 - ({xi})) = {fpl(m)}, y절편 {yi}",
                   [f"x절편 {xi} → 점 ({xi}, 0), y절편 {yi} → 점 (0, {yi})를 지나요",
                    f"기울기 = ({yi} - 0) ÷ (0 - {par(xi)}) = {yi} ÷ {par(-xi)} = [[{fpl(m)}]]",
                    f"y절편 b = [[{yi}]]"])


@kind(U, "일차함수의 활용: 온도, 높이, 길이", 12)
def _():
    mode = R.randint(0, 2)
    if mode == 0:
        h0, v = R.choice([90, 120, 150, 180, 200, 240]), R.choice([2, 3, 4, 5, 6])
        target = R.randint(1, h0 // v - 1) * v
        tt = (h0 - target) // v
        return P("보통", f"지면에서 {h0} m 높이에 떠 있던 열기구가 1초에 {v} m씩 일정하게 내려온다. 내려오기 시작한 지 몇 초 후에 지면에서 높이가 {target} m가 되는지 구하시오.", tt,
                 hint=f"x초 후 높이를 y m라 하면 y = {h0} - {v}x", explanation=f"{target} = {h0} - {v}x → x = {tt}",
                 steps=["x초 후의 높이를 y m라 해요",
                        f"1초에 {v} m씩 내려오니 x초 동안 {v}x m 내려와요",
                        f"y = {h0} - [[{v}]]x",
                        f"y = {target}을 넣으면: {target} = {h0} - {v}x",
                        f"{v}x = {h0} - {target} = [[{h0 - target}]]",
                        f"x = {h0 - target} ÷ {v} = [[{tt}]]"],
                 detail=[f"확인: {tt}초 동안 {v * tt} m 내려와서 {h0} - {v * tt} = {target} m ✔", f"답: {tt}초 후"])
    if mode == 1:
        t0, dm, dt = R.choice([80, 90, 70, 60]), R.choice([2, 3, 4, 5]), R.choice([3, 4, 5, 6])
        mins = R.randint(2, 6) * dm
        r_ = F(dt, dm)
        tv = t0 - r_ * mins
        need(tv > 20)
        return P("보통", f"{t0} °C의 물을 실온에 두면 {dm}분마다 {dt} °C씩 일정하게 온도가 내려간다. {mins}분 후의 물의 온도는 몇 °C인지 구하시오.", fpl(tv),
                 hint=f"1분에 {fpl(r_)} °C씩 내려가요.", explanation=f"y = {t0} - {fpl(r_)}x, x = {mins} → y = {fpl(tv)}",
                 steps=[f"1분에 내려가는 온도: {dt} ÷ {dm} = [[{fpl(r_)}]] °C",
                        f"x분 후 온도를 y °C라 하면: y = {t0} - {cx(r_)}x",
                        f"{mins}분 동안 내려간 온도: {fpl(r_)} × {mins} = [[{fpl(r_ * mins)}]] °C",
                        f"y = {t0} - {fpl(r_ * mins)} = [[{fpl(tv)}]]"],
                 detail=[f"다른 방법: {mins}분은 {dm}분의 {mins // dm}배 → {dt} × {mins // dm} = {dt * mins // dm} °C 내려가요", f"답: {fpl(tv)} °C"])
    L0, burn = R.choice([20, 24, 30, 36]), F(R.randint(1, 4), R.choice([1, 2, 5]))
    mins = R.randint(2, 12) * burn.denominator
    rem = L0 - burn * mins
    need(rem > 0)
    return P("보통", f"길이가 {L0} cm인 양초에 불을 붙이면 1분에 {fpl(burn)} cm씩 일정하게 짧아진다. 불을 붙인 지 {mins}분 후 양초의 길이는 몇 cm인지 구하시오.", fpl(rem),
             hint=f"x분 후 길이 y = {L0} - {fpl(burn)}x", explanation=f"y = {L0} - {fpl(burn)} × {mins} = {fpl(rem)}",
             steps=["x분 후 양초의 길이를 y cm라 해요",
                    f"y = {L0} - {cx(burn)}x",
                    f"{mins}분 동안 짧아진 길이: {fpl(burn)} × {mins} = [[{fpl(burn * mins)}]] cm",
                    f"y = {L0} - {fpl(burn * mins)} = [[{fpl(rem)}]]"],
             detail=[f"답: {fpl(rem)} cm"])


@kind(U, "일차함수의 활용: 물의 양", 12)
def _():
    w0 = R.choice([20, 30, 40, 50, 80, 100])
    dm, dv = R.choice([(2, 3), (3, 5), (4, 6), (5, 8), (1, 2), (3, 4)])
    fill = R.random() < 0.5
    mins = R.randint(2, 8) * dm
    r_ = F(dv, dm)
    v = w0 + (1 if fill else -1) * r_ * mins
    need(v > 0)
    sg = "+" if fill else "-"
    return P("보통", f"물이 {w0} L 들어 있는 수조{'에' if fill else '에서'} {dm}분에 {dv} L씩 일정하게 물을 {'넣는다' if fill else '빼낸다'}. {mins}분 후 수조에 들어 있는 물의 양은 몇 L인지 구하시오.", fpl(v),
             hint=f"1분에 {fpl(r_)} L씩 {'늘어나요' if fill else '줄어들어요'}.",
             explanation=f"y = {w0} {sg} {fpl(r_)}x, x = {mins} → y = {fpl(v)}",
             steps=[f"1분에 {'넣는' if fill else '빼내는'} 물: {dv} ÷ {dm} = [[{fpl(r_)}]] L",
                    f"x분 후 물의 양을 y L라 하면: y = {w0} {sg} {cx(r_)}x",
                    f"{mins}분 동안 {'넣은' if fill else '빼낸'} 물: {fpl(r_)} × {mins} = [[{fpl(r_ * mins)}]] L",
                    f"y = {w0} {sg} {fpl(r_ * mins)} = [[{fpl(v)}]]"],
             detail=[f"답: {fpl(v)} L"])


@kind(U, "일차함수의 활용: 거리, 속력, 시간", 12)
def _():
    D, v = R.choice([(240, 80), (300, 60), (180, 60), (270, 90), (200, 50), (150, 60), (360, 80)])
    tt = F(R.randint(1, 2 * D // v - 1), 2)
    rem = D - v * tt
    need(rem > 0)
    h, mi = int(tt), int((tt - int(tt)) * 60)
    tstr = ((f"{h}시간" if h else "") + (f" {mi}분" if mi else "")).strip()
    place = R.choice(["할머니 댁", "캠핑장", "바닷가", "수목원"])
    return P("보통", f"{D} km 떨어진 {place}까지 시속 {v} km로 갈 때, 출발한 지 {tstr} 후 {place}까지 남은 거리는 몇 km인지 구하시오.", fpl(rem),
             hint=f"x시간 후 남은 거리 y = {D} - {v}x", explanation=f"{tstr} = {fpl(tt)}시간, y = {D} - {v} × {fpl(tt)} = {fpl(rem)}",
             steps=["x시간 후 남은 거리를 y km라 해요 (간 거리 = 속력 × 시간)",
                    f"y = {D} - {v}x",
                    f"시간을 '시간' 단위로: {tstr} = [[{fpl(tt)}]]시간" + (" (30분 = 1/2시간)" if mi else ""),
                    f"간 거리: {v} × {fpl(tt)} = [[{fpl(v * tt)}]] km",
                    f"y = {D} - {fpl(v * tt)} = [[{fpl(rem)}]]"],
             detail=[f"답: {fpl(rem)} km"])


@kind(U, "일차함수의 활용: 도형", 10)
def _():
    W, H = R.randint(6, 16), R.randint(4, 12)
    sp_ = R.choice([1, 2])
    S = R.randint(4, H * W // 2 - 1)
    tt = F(2 * S, H * sp_)
    need(0 < tt * sp_ < W and tt.denominator <= 2)
    k_ = F(H * sp_, 2)
    return P("어려움", f"가로 {W} cm, 세로 {H} cm인 직사각형 ABCD에서 점 P가 꼭짓점 B를 출발하여 변 BC 위를 C 쪽으로 1초에 {sp_} cm씩 움직인다. 삼각형 ABP의 넓이가 {S} cm²가 되는 것은 출발한 지 몇 초 후인지 구하시오. (단, AB = {H} cm, BC = {W} cm)",
             fpl(tt), hint=f"x초 후 BP = {sp_}x cm, 삼각형 ABP의 넓이 y = {H} × {sp_}x ÷ 2",
             explanation=f"y = {fpl(k_)}x = {S} → x = {fpl(tt)}",
             steps=[f"x초 후 BP = {sp_}x cm" if sp_ != 1 else "x초 후 BP = x cm",
                    f"삼각형 ABP는 밑변 BP, 높이 AB = {H} cm인 직각삼각형",
                    f"넓이 y = {H} × {sp_}x ÷ 2 = [[{fpl(k_)}]]x".replace("× 1x", "× x"),
                    f"y = {S}을 넣으면: {fpl(k_)}x = {S}",
                    f"x = {S} ÷ {fpl(k_)} = [[{fpl(tt)}]]"],
             detail=[f"확인: {fpl(tt)}초 후 BP = {fpl(tt * sp_)} cm (BC = {W} cm보다 짧아 변 위에 있어요), 넓이 {H} × {fpl(tt * sp_)} ÷ 2 = {S} ✔", f"답: {fpl(tt)}초 후"])


# ═════════════════════ 7. 일차함수와 일차방정식의 관계 ═════════════════════
U = "7. 일차함수와 일차방정식"


@kind(U, "일차함수와 일차방정식의 관계", 12)
def _():
    A1, B1, C1 = nz(-5, 5), nz(-5, 5), R.randint(-9, 9)
    m, c = F(-A1, B1), F(-C1, B1)
    right = f"$y = {fx(m, c)}$"
    wr = [f"$y = {fx(-m, c)}$", f"$y = {fx(m, -c)}$", f"$y = {fx(-m, -c)}$", f"$y = {fx(F(B1, A1), c)}$" if F(B1, A1) != m else f"$y = {fx(m, c + 1)}$",
          f"$y = {fx(A1, C1)}$", f"$y = {fx(m, c + 2)}$"]
    ch, ans = mc(right, wr)
    st = ["y에 대하여 풀어요 (y항만 왼쪽에 남겨요)",
          f"x항과 수를 오른쪽으로 옮기면: {lin(0, B1, 0, tex=False)} = {lin(-A1, 0, -C1, tex=False)}"]
    if B1 != 1:
        st.append(f"양변을 {B1}로 나누면: y = ■x + ■")
    st += [f"기울기(x의 계수) = [[{fpl(m)}]]", f"y절편(상수항) = [[{fpl(c)}]]"]
    return P("쉬움", f"일차방정식 ${lin(A1, B1, C1)} = 0$의 그래프와 같은 직선을 그래프로 하는 일차함수는?", ans, choices=ch,
             hint="y에 대하여 풀어요: y = ... 꼴로", explanation=f"{B1}y = {lin(-A1, 0, -C1, tex=False)} → y = {fx(m, c, tex=False)}",
             steps=st, detail=[f"y = {fx(m, c, tex=False)}", f"그래서 답은 {ans}"])


@kind(U, "일차방정식의 그래프 위의 점", 12)
def _():
    A1, B1 = nz(-5, 5), nz(-5, 5)
    px, py = nz(-5, 5), nz(-5, 5)
    C1 = -(A1 * px + B1 * py)
    if R.random() < 0.5:
        return P("쉬움", f"점 $({px}, k)$가 일차방정식 ${lin(A1, B1, C1)} = 0$의 그래프 위에 있을 때, $k$의 값을 구하시오.", py,
                 hint="점의 좌표를 대입해요.", explanation=f"({A1})×({px}) + ({B1})k {sgn(C1)} = 0 → k = {py}",
                 steps=["그래프 위의 점은 x, y 값을 넣으면 등식이 성립해요",
                        f"x = {px}, y = k를 넣으면: {mul(A1, px)} + {B1}k{pm(C1)} = 0".replace("+ 1k", "+ k").replace("+ -", "- "),
                        f"계산하면: {lin(B1, 0, A1 * px + C1, 'k', 'z', tex=False)} = 0"] + lin_solve(B1, A1 * px + C1, 0, "k"),
                 detail=[f"확인: {sub2(A1, B1, px, py)}, {A1 * px + B1 * py}{pm(C1)} = 0 ✔", f"답: k = {py}"])
    return P("보통", f"일차방정식 $ax {sgn(B1)}y {sgn(C1)} = 0$의 그래프가 점 $({px}, {py})$를 지날 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y").replace(" + 0 =", " ="),
             A1, hint="점의 좌표를 대입해 a에 대한 방정식을 풀어요.", explanation=f"({px})a + ({B1})×({py}) {sgn(C1)} = 0 → a = {A1}",
             steps=["그래프가 점을 지나면 그 점의 x, y 값을 넣어도 등식이 성립해요",
                    f"x = {px}, y = {py}를 넣으면: a×{par(px)} + {mul(B1, py)}{pm(C1)} = 0".replace("+ -", "- "),
                    f"계산하면: {lin(px, 0, B1 * py + C1, 'a', 'z', tex=False)} = 0"] + lin_solve(px, B1 * py + C1, 0),
             detail=[f"답: a = {A1}"])


@kind(U, "직선의 방정식 구하기", 12)
def _():
    A1, B1 = nz(-5, 5), R.choice([1, 1, 2, -1])
    m = F(-A1, B1)
    xi = nz(-5, 5)
    c = -m * xi
    need(c.denominator == 1)
    k = -B1 * c

    def eqs(p_, q_, r_):
        return f"${lin(p_, q_, r_)} = 0$"
    right = eqs(A1, B1, k)
    wr = [eqs(A1, -B1, k), eqs(A1, B1, -k), eqs(B1, A1, k), eqs(A1, B1, k + 1), eqs(-A1, B1, k), eqs(A1, B1, k - 2)]
    ch, ans = mc(right, wr)
    c0 = R.randint(-9, 9)
    need(c0 != k)
    return P("보통", f"직선 ${lin(A1, B1, c0)} = 0$과 평행하고, $x$절편이 ${xi}$인 직선의 방정식은?".replace(" + 0 = 0", " = 0"), ans, choices=ch,
             hint=f"평행하면 기울기가 같아요. 기울기 {fpl(m)}, 점 ({xi}, 0)을 지나요.",
             explanation=f"y = {fx(m, 0, tex=False)} + b, 0 = {fpl(m)} × ({xi}) + b → b = {fpl(c)} → {right.strip('$')}",
             steps=[f"주어진 직선을 y에 대해 풀면: y = {fx(m, F(-c0, B1), tex=False)}",
                    f"평행하니까 기울기가 같아요: 기울기 = [[{fpl(m)}]]",
                    f"구하는 직선: y = {fx(m, 0, tex=False)} + b",
                    f"x절편이 {xi} → 점 ({xi}, 0)을 넣으면: 0 = {par(fpl(m))} × {par(xi)} + b → b = [[{fpl(c)}]]"],
             detail=[f"y = {fx(m, c, tex=False)}" + (f"의 양변에 {B1}를 곱하고" if B1 != 1 else "에서") + f" 모든 항을 왼쪽으로 옮기면: {lin(A1, B1, k, tex=False)} = 0",
                     f"그래서 답은 {ans}"])


@kind(U, "좌표축에 평행한 직선", 14)
def _():
    if R.random() < 0.4:
        p1, p2 = (nz(-6, 6), nz(-6, 6)), (nz(-6, 6), nz(-6, 6))
        d1 = R.choice(["x축에 수직인", "y축에 평행한"])
        d2 = R.choice(["y축에 수직인", "x축에 평행한"])
        ans_ = p1[0] + p2[1]
        return P("쉬움", f"점 $({p1[0]}, {p1[1]})$을 지나고 ${d1[0]}$축{d1[2:]} 직선과 점 $({p2[0]}, {p2[1]})$을 지나고 ${d2[0]}$축{d2[2:]} 직선의 교점의 좌표를 $(p, q)$라 할 때, $p + q$의 값을 구하시오.", ans_,
                 hint="x축에 수직(y축에 평행)인 직선은 x = (수), y축에 수직(x축에 평행)인 직선은 y = (수)",
                 explanation=f"x = {p1[0]}, y = {p2[1]} → 교점 ({p1[0]}, {p2[1]}) → p + q = {ans_}",
                 steps=["x축에 수직(= y축에 평행)인 직선: x = (수), 세로로 선 직선",
                        "y축에 수직(= x축에 평행)인 직선: y = (수), 가로로 누운 직선",
                        f"점 ({p1[0]}, {p1[1]})을 지나는 세로 직선: x = [[{p1[0]}]]",
                        f"점 ({p2[0]}, {p2[1]})을 지나는 가로 직선: y = [[{p2[1]}]]",
                        f"교점 ({p1[0]}, {p2[1]}) → p + q = {p1[0]} + {par(p2[1])} = [[{ans_}]]"],
                 detail=[f"답: {ans_}"])
    k1, k2 = nz(-4, 4, (0, 1)), nz(-4, 4)
    need(k1 != k2)
    c1, c2 = R.randint(-8, 8), R.randint(-8, 8)
    av = F(c2 - c1, k1 - k2)
    need(av.denominator == 1)
    av = int(av)
    ya, yb = R.randint(-6, 6), R.randint(-6, 6)
    need(ya != yb)
    axis = R.choice(["$y$축에 평행할", "$x$축에 수직일"])
    e1, e2 = lin(k1, 0, c1, 'a', 'z', tex=False), lin(k2, 0, c2, 'a', 'z', tex=False)
    return P("보통", f"두 점 $({lin(k1, 0, c1, 'a')}, {ya})$, $({lin(k2, 0, c2, 'a')}, {yb})$를 지나는 직선이 {axis} 때, $a$의 값을 구하시오.",
             av, hint="y축에 평행(x축에 수직)한 직선 위의 점은 x좌표가 모두 같아요.",
             explanation=f"{e1} = {e2} → a = {av}",
             steps=["y축에 평행(x축에 수직)한 직선은 x = (수) 꼴 → 그 위의 점은 x좌표가 모두 같아요",
                    f"두 점의 x좌표가 같아요: {e1} = {e2}",
                    f"a항은 왼쪽, 수는 오른쪽으로: {lin(k1 - k2, 0, 0, 'a', 'z', tex=False)} = [[{c2 - c1}]]"]
                   + ([f"양변을 {k1 - k2}로 나누면: a = [[{av}]]"] if k1 - k2 not in (1, -1) else ([f"a = [[{av}]]"] if k1 - k2 == -1 else [])),
             detail=[f"확인: a = {av}이면 x좌표가 둘 다 {k1 * av + c1} → 직선 x = {k1 * av + c1} ✔", f"답: a = {av}"])


@kind(U, "ax + by + c = 0의 그래프와 부호", 8)
def _():
    sa, sb = R.choice([1, -1]), R.choice([1, -1])

    def s(v):
        return "> 0" if v > 0 else "< 0"
    right = f"$a {s(sa)}$, $b {s(sb)}$"
    wr = [f"$a {s(p_)}$, $b {s(q_)}$" for p_ in (1, -1) for q_ in (1, -1) if (p_, q_) != (sa, sb)] + ["$a = 0$, $b > 0$", "$a > 0$, $b = 0$"]
    ch, ans = mc(right, wr)
    ps = lambda v: "양수" if v > 0 else "음수"
    return P("어려움", f"일차방정식 $ax + by - 1 = 0$의 그래프의 $x$절편이 {ps(sa)}, $y$절편이 {ps(sb)}일 때, $a$, $b$의 부호는?", ans, choices=ch,
             hint="x절편 = 1/a, y절편 = 1/b", explanation=f"1/a {s(sa)}, 1/b {s(sb)} → a {s(sa)}, b {s(sb)}",
             steps=["x절편: y = 0을 넣으면 ax - 1 = 0 → x = [[1/a]]",
                    "y절편: x = 0을 넣으면 by - 1 = 0 → y = [[1/b]]",
                    f"1/a의 값이 {ps(sa)} → a도 {ps(sa)}: a [[{s(sa)}]]",
                    f"1/b의 값이 {ps(sb)} → b도 {ps(sb)}: b [[{s(sb)}]]"],
             detail=["1을 어떤 수로 나눈 값의 부호는 그 수의 부호와 같아요", f"그래서 답은 {ans}"])


@kind(U, "연립방정식의 해와 그래프의 교점", 14)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    a1, b1, a2, b2 = (nz(-4, 4) for _ in range(4))
    need(a1 * b2 - a2 * b1 != 0)
    c1, c2 = -(a1 * sx + b1 * sy), -(a2 * sx + b2 * sy)
    combo = R.choice([("p + q", sx + sy, "+"), ("pq", sx * sy, "×"), ("p - q", sx - sy, "-")])
    if R.random() < 0.7:
        return P("보통", f"두 일차방정식 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$의 그래프의 교점의 좌표를 $(p, q)$라 할 때, ${combo[0]}$의 값을 구하시오.", combo[1],
                 hint="두 그래프의 교점 = 연립방정식의 해", explanation=f"연립해서 풀면 x = {sx}, y = {sy}",
                 steps=["두 그래프의 교점의 좌표 = 두 식을 연립한 연립방정식의 해", "상수항을 오른쪽으로 옮겨 연립해요"]
                       + elim_steps(a1, b1, -c1, a2, b2, -c2, sx, sy)
                       + [f"교점 ({sx}, {sy}) → {combo[0]} = {par(sx)} {combo[2]} {par(sy)} = [[{combo[1]}]]"],
                 detail=chk_sys([(a1, b1, -c1), (a2, b2, -c2)], sx, sy) + [f"답: {combo[1]}"])
    av = nz(-5, 5)
    c2b = -(av * sx + b2 * sy)
    return P("어려움", f"두 일차방정식 ${lin(a1, b1, c1)} = 0$, $ax {sgn(b2)}y {sgn(c2b)} = 0$의 그래프의 교점의 $x$좌표가 ${sx}$일 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y").replace(" + 0 =", " ="),
             av, hint="a가 없는 식에 x좌표를 넣어 y좌표를 먼저 구해요.", explanation=f"교점 ({sx}, {sy}) → ({sx})a + ({b2})×({sy}) {sgn(c2b)} = 0 → a = {av}",
             steps=["교점은 두 그래프 위에 모두 있어요",
                    f"a가 없는 첫째 식에 x = {sx}를 넣으면: {mul(a1, sx)} + {b1}y{pm(c1)} = 0".replace("+ 1y", "+ y").replace("+ -", "- "),
                    f"{lin(b1, 0, a1 * sx + c1, 'y', 'z', tex=False)} = 0"] + lin_solve(b1, a1 * sx + c1, 0, "y")
                   + [f"교점 ({sx}, {sy})를 둘째 식에 넣으면: a×{par(sx)} + {mul(b2, sy)}{pm(c2b)} = 0".replace("+ -", "- "),
                      f"{lin(sx, 0, b2 * sy + c2b, 'a', 'z', tex=False)} = 0"] + lin_solve(sx, b2 * sy + c2b, 0),
             detail=[f"답: a = {av}"])


@kind(U, "두 직선의 교점을 지나는 직선", 12)
def _():
    sx, sy = nz(-4, 4), nz(-5, 5)
    a1, b1, a2, b2 = (nz(-4, 4) for _ in range(4))
    need(a1 * b2 - a2 * b1 != 0)
    c1, c2 = -(a1 * sx + b1 * sy), -(a2 * sx + b2 * sy)
    m = nz(-4, 4)
    c = sy - m * sx
    if R.random() < 0.5:
        q = f"두 직선 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$의 교점을 지나고, 기울기가 ${m}$인 직선의 방정식은?"
        sl = f"기울기 = [[{m}]] (문제에 주어짐)"
    else:
        c0 = R.randint(-6, 6)
        need(c0 != c)
        q = f"두 직선 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$의 교점을 지나고, 직선 $y = {fx(m, c0)}$와 평행한 직선의 방정식은?"
        sl = f"평행하니까 기울기가 같아요: 기울기 = [[{m}]]"
    st = ["먼저 두 직선의 교점(연립방정식의 해)을 구해요"] + elim_steps(a1, b1, -c1, a2, b2, -c2, sx, sy) + [
        sl, f"y = {fx(m, 0, tex=False)} + b에 교점 ({sx}, {sy})를 넣으면: {sy} = {mul(m, sx)} + b",
        f"b = {sy} - {par(m * sx)} = [[{c}]]"]
    return line_mc(F(m), F(c), q, "먼저 연립해서 교점을 구한 뒤 y = (기울기)x + b에 대입해요.", f"교점 ({sx}, {sy}) → b = {c}", st, level="어려움")


@kind(U, "두 직선과 좌표축으로 둘러싸인 도형의 넓이", 10)
def _():
    sx = nz(-5, 5)
    sy = nz(-5, 5)
    m1, m2 = nz(-3, 3), nz(-3, 3)
    need(m1 != m2)
    c1, c2 = sy - m1 * sx, sy - m2 * sx
    S = F(abs(c1 - c2) * abs(sx), 2)
    need(S > 0)
    return P("어려움", f"두 직선 $y = {fx(m1, c1)}$, $y = {fx(m2, c2)}$와 $y$축으로 둘러싸인 삼각형의 넓이를 구하시오.", fpl(S),
             hint="두 직선의 y절편 사이 거리를 밑변으로, 교점의 x좌표(절댓값)를 높이로 봐요.",
             explanation=f"y절편 {c1}, {c2} → 밑변 {abs(c1 - c2)}, 교점 ({sx}, {sy}) → 높이 {abs(sx)} → 넓이 {fpl(S)}",
             steps=[f"두 직선이 y축과 만나는 점(y절편): (0, [[{c1}]]), (0, {c2})",
                    f"두 직선의 교점: {fx(m1, c1, tex=False)} = {fx(m2, c2, tex=False)} → {lin(m1 - m2, 0, 0, tex=False)} = {c2 - c1} → x = [[{sx}]]",
                    f"교점의 y좌표: y = {mul(m1, sx)}{pm(c1)} = {sy} → 교점 ({sx}, {sy})",
                    f"밑변: y축 위의 두 점 사이 거리 |{c1} - {par(c2)}| = [[{abs(c1 - c2)}]]",
                    f"높이: 교점에서 y축까지 거리 |{sx}| = [[{abs(sx)}]]",
                    f"넓이 = {abs(c1 - c2)} × {abs(sx)} ÷ 2 = [[{fpl(S)}]]"],
             detail=[f"답: {fpl(S)}"])


@kind(U, "한 점에서 만나는 세 직선", 12)
def _():
    sx, sy = nz(-4, 4), nz(-4, 4)
    a1, b1, a2, b2 = (nz(-3, 3) for _ in range(4))
    need(a1 * b2 - a2 * b1 != 0)
    c1, c2 = -(a1 * sx + b1 * sy), -(a2 * sx + b2 * sy)
    b3, c3 = nz(-4, 4), R.randint(-6, 6)
    av = F(-(b3 * sy + c3), sx)
    need(av.denominator == 1)
    return P("어려움", f"세 직선 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$, $ax {sgn(b3)}y {sgn(c3)} = 0$이 한 점에서 만날 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y").replace(" + 0 =", " ="),
             int(av), hint="a가 없는 두 직선의 교점을 먼저 구하고, 세 번째 식에 대입해요.",
             explanation=f"교점 ({sx}, {sy}) → ({sx})a + ({b3})×({sy}) {sgn(c3)} = 0 → a = {int(av)}",
             steps=["세 직선이 한 점에서 만나면, a가 없는 두 직선의 교점을 셋째 직선도 지나요",
                    "a가 없는 두 식을 연립해요 (상수항은 오른쪽으로)"] + elim_steps(a1, b1, -c1, a2, b2, -c2, sx, sy)
                   + [f"교점 ({sx}, {sy})를 셋째 식에 넣으면: a×{par(sx)} + {mul(b3, sy)}{pm(c3)} = 0".replace("+ -", "- "),
                      f"{lin(sx, 0, b3 * sy + c3, 'a', 'z', tex=False)} = 0"] + lin_solve(sx, b3 * sy + c3, 0),
             detail=[f"답: a = {int(av)}"])


@kind(U, "연립방정식의 해의 개수와 그래프", 14)
def _():
    p_, q_, r_ = nz(-4, 4), nz(-4, 4), nz(-6, 6)
    k = nz(-3, 3, (0, 1))
    if R.random() < 0.5:
        av = q_
        bval = r_ * k
        combo = R.choice([("a + b", av + bval, "+"), ("ab", av * bval, "×"), ("a - b", av - bval, "-")])
        return P("어려움", f"연립방정식 $\\begin{{cases}} {lin(p_, 0, 0)} + ay = {r_} \\\\ {lin(k * p_, k * q_, 0)} = b \\end{{cases}}$의 해가 무수히 많을 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.",
                 combo[1], hint=f"해가 무수히 많으면 두 직선이 일치해요: 첫 식에 {k}를 곱하면 둘째 식과 같아야 해요.",
                 explanation=f"첫 식 × {k}: {lin(k * p_, 0, 0, tex=False)} + {k}ay = {k * r_} → {k}a = {k * q_}, b = {k * r_} → a = {av}, b = {bval}",
                 steps=["해가 무수히 많다 = 두 직선이 일치 = 한 식에 어떤 수를 곱하면 다른 식과 똑같아요",
                        f"x의 계수 비교: {p_} × (몇 배) = {k * p_} → [[{k}]]배",
                        f"첫째 식 × {k}: {lin(k * p_, 0, 0, tex=False)} + {k}ay = {k * r_}",
                        f"y의 계수 비교: {k}a = {k * q_} → a = [[{av}]]",
                        f"상수항 비교: b = [[{bval}]]",
                        f"{combo[0]} = {par(av)} {combo[2]} {par(bval)} = [[{combo[1]}]]"],
                 detail=[f"답: {combo[1]}"])
    av = k * p_
    c2 = R.randint(-9, 9)
    need(c2 != k * r_)
    return P("어려움", f"연립방정식 $\\begin{{cases}} {lin(p_, q_, 0)} = {r_} \\\\ ax {sgn(k * q_)}y = {c2} \\end{{cases}}$의 해가 없을 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y"),
             av, hint="해가 없으면 두 직선이 평행해요: x, y의 계수의 비는 같고 상수항의 비는 달라요.",
             explanation=f"{p_} : {q_} = a : {k * q_} → a = {av} (상수항 {r_} : {c2}는 비가 달라 평행)",
             steps=["해가 없다 = 두 직선이 평행 (기울기는 같고 y절편은 달라요)",
                    "→ x, y의 계수는 같은 비율(같은 배수)이고, 상수항만 비율이 달라요",
                    f"y의 계수 비교: {q_} × (몇 배) = {k * q_} → [[{k}]]배",
                    f"x의 계수도 {k}배: a = {p_} × {par(k)} = [[{av}]]"],
             detail=[f"상수항은 {r_} × {par(k)} = {k * r_} ≠ {c2} → 일치가 아니라 평행 → 해가 없어요 ✔", f"답: a = {av}"])


# ═════════════════════ 만들기 ═════════════════════

def build():
    by_unit: dict[str, list[list[dict]]] = {}
    report = []
    for unit, concept, cnt, fn in TYPES:
        got, seen, tries = [], set(), 0
        while len(got) < cnt and tries < cnt * 80:
            tries += 1
            try:
                q = fn()
            except (Retry, ZeroDivisionError, ValueError, IndexError, StopIteration, TypeError, KeyError):
                continue
            key = q["question"] + "|" + "|".join(sorted(q["choices"]))
            if key in seen:
                continue
            seen.add(key)
            q["concept"] = concept
            for k_ in ("question", "explanation", "hint"):
                q[k_] = fix_josa(tidy(q[k_]))
            q["choices"] = [fix_josa(tidy(c_)) for c_ in q["choices"]]
            q["steps"] = [fix_josa(tidy(s_)) for s_ in q["steps"]]
            if len(set(q["choices"])) != len(q["choices"]):
                continue
            got.append(q)
        report.append((unit, concept, len(got), cnt))
        by_unit.setdefault(unit, []).append(got)
    short = [r_ for r_ in report if r_[2] < r_[3]]
    for unit, concept, n, cnt in short:
        print(f"  (모자람) {unit} / {concept}: {n}/{cnt}")
    assert all(n >= 3 for _, _, n, _ in report), [r_ for r_ in report if r_[2] < 3]
    pages, uid = [], 0
    for ui, (unit, lists) in enumerate(by_unit.items(), 1):
        # 유형마다 2문제씩 한 바퀴 → 다음 바퀴 ... (앞에서부터 풀어도 모든 유형을 골고루 만남)
        order, rnd = [], 0
        while any(len(l_) > rnd * 2 for l_ in lists):
            for l_ in lists:
                order += l_[rnd * 2: rnd * 2 + 2]
            rnd += 1
        probs = []
        for q in order:
            uid += 1
            q = {"bank_id": f"{ID_PREFIX}_{ui}_{uid:04d}", "concept": q["concept"],
                 "type": "multiple_choice" if q["choices"] else "short_answer", **{k: q[k] for k in ("level", "question", "choices", "answer", "hint", "explanation", "steps")}}
            probs.append(q)
        pages.append({"page": ui, "unit": unit, "problems": probs})
    return pages, report


def check(pages):
    import re
    num = re.compile(r"^-?\d+(/\d+)?$|^x=-?\d+, y=-?\d+$")
    errs = {}
    for pg in pages:
        for q in pg["problems"]:
            blob = " ".join([q["question"], q["explanation"]] + q["choices"] + q["steps"])
            probs = []
            if not (q["question"] and q["answer"]):
                probs.append("빈 값")
            if q["type"] == "multiple_choice":
                if not (len(q["choices"]) == 5 and len(set(q["choices"])) == 5 and q["answer"] in MARKS):
                    probs.append("보기")
            elif not num.match(q["answer"]):
                probs.append("답 꼴")
            if any(s_.count("[[") != s_.count("]]") for s_ in q["steps"]):
                probs.append("빈칸")
            if "None" in blob or q["question"].count("$") % 2 or any(c_.count("$") % 2 for c_ in q["choices"]):
                probs.append("None/$")
            for bad in ("^{1}", "+ -", "- -", "+ +", "= +"):
                if bad in blob:
                    probs.append(bad)
            if re.search(r"(?<![\d./])1[xyab](?![\w^])", blob):
                probs.append("1x")
            if re.search(r"(?<![\d.])-1\(", blob):
                probs.append("-1(")
            for p_ in probs:
                errs.setdefault((q["concept"], p_), []).append(blob[:200])
    for (c_, p_), ex in errs.items():
        print(f"  [검사] {c_} / {p_}: {len(ex)}개 예) {ex[0]}")
    return errs


if __name__ == "__main__":
    pages, report = build()
    errs = check(pages)
    assert not errs, f"검사 실패 {len(errs)}종"
    data = {"books": [{"book": TITLE, "title": TITLE, "pages": pages}]}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    for pg in pages:
        n_types = sum(1 for u, *_ in report if u == pg["unit"])
        print(f"{pg['unit']}: 유형 {n_types}개, 문제 {len(pg['problems'])}개")
    print("총", sum(len(pg["problems"]) for pg in pages), "문제,", len(report), "유형")
