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


def P(level, question, answer, *, choices=None, hint="", explanation="", steps=None):
    return {"level": level, "question": question, "choices": choices or [], "answer": str(answer),
            "hint": hint, "explanation": explanation, "steps": steps or []}


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
        es = [(sp.degree(fk, v), op) for fk, op in powered]
        if not any(e for e, _ in es):
            continue
        tot = sum(e if op == "×" else -e for e, op in es)
        joined = "".join(("" if i == 0 else (" + " if op == "×" else " - ")) + str(e) for i, (e, op) in enumerate((e, op) for e, op in es if e))
        lines.append(f"{v}의 지수: {joined} = [[{tot}]]")
        result *= v ** tot
    lines.append(f"정리하면: {mono(result, g, False)}")
    return lines, result


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
        lines.append(f"x = {sx}를 ①에 넣으면: {a1}×({sx}) {sgn(b1)}y = {c1} → {lin(b1, 0, 0, 'y', 'x', tex=False)} = {rest}")
    else:
        rest = c2 - a2 * sx
        lines.append(f"x = {sx}를 ②에 넣으면: {a2}×({sx}) {sgn(b2)}y = {c2} → {lin(b2, 0, 0, 'y', 'x', tex=False)} = {rest}")
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
    lines.append(f"x항은 왼쪽, 수는 오른쪽으로 옮기면: {fmt(Q(c_) * X, (X,), False)} {PL[rel]} {fpl(-k_)}")
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
    return P("쉬움", f"다음 분수를 소수로 나타낼 때, {'유한소수' if want else '무한소수'}인 것은?", ans, choices=ch,
             hint="기약분수로 만든 뒤 분모의 소인수가 2와 5뿐이면 유한소수, 다른 소인수가 있으면 무한소수(순환소수)예요.",
             explanation=(f"{n}/{d} = {fpl(f)}, " if F(n, d) != f or f.numerator != n else f"{n}/{d}: ") + f"분모 {f.denominator} = {factor_str(f.denominator)} → {'2와 5뿐이라 유한소수' if want else '2·5 말고 다른 소인수가 있어 무한소수'}",
             steps=[f"{n}/{d}를 기약분수로: [[{fpl(f)}]]", f"분모 {f.denominator}의 소인수분해: {factor_str(f.denominator)}"])


@kind(U, "순환소수 표현하기", 12)
def _():
    want = R.random() < 0.5
    truth = []
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
        truth.append((f"${long_} = {s}$", right))
        if sum(t_ == want for _, t_ in truth) >= 1 and sum(t_ != want for _, t_ in truth) >= 4:
            break
    ch, ans = pick(truth, want)
    return P("보통", f"다음 중 순환소수의 표현이 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="소수점 아래에서 처음 되풀이되는 숫자 묶음(순환마디)을 찾아, 그 처음과 끝 숫자 위에만 점을 찍어요.",
             explanation="각 보기의 순환마디를 찾아, 점이 순환마디의 처음과 끝에 찍혔는지 확인해요.",
             steps=["되풀이되는 묶음 = [[순환마디]]", "점은 순환마디의 처음과 [[끝]] 숫자 위에"])


@kind(U, "분수를 순환소수로", 12)
def _():
    d = R.choice([3, 6, 7, 9, 11, 12, 13, 15, 18, 22, 27, 30, 33, 37, 44, 45, 55, 66, 74, 90, 99, 111, 135, 148])
    n = R.randint(1, 3 * d)
    f = F(n, d)
    need(f.denominator == d and n % d)
    ip, pre, rep = expand(f)
    need(rep and len(rep) <= 6)
    ch, ans = mc(f"${dtex(ip, pre, rep)}$", [f"${dtex(ip, *w)}$" for w in wrong_dots(ip, pre, rep, f)])
    return P("보통", f"분수 $\\frac{{{n}}}{{{d}}}$을 순환소수로 바르게 나타낸 것은?", ans, choices=ch,
             hint="직접 나눗셈을 해서 같은 나머지가 다시 나오는 곳을 찾아요.",
             explanation=f"{n} ÷ {d} = {dplain(ip, pre, rep, 10)} 이므로 순환마디는 {rep}예요.",
             steps=[f"{n} ÷ {d} = {dplain(ip, pre, rep, 10)}", f"순환마디: [[{rep}]]"])


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
    k = (nth - len(pre)) % len(rep)
    return P("어려움", f"{q}, 소수점 아래 {nth}번째 자리의 숫자를 구하시오.", dg,
             hint="순환마디의 숫자 개수로 나눈 나머지를 봐요. 순환하지 않는 자리가 있으면 그만큼 먼저 빼요.",
             explanation=f"{dplain(ip, pre, rep, 12)}: 순환마디 {rep} ({len(rep)}개)" + (f", 앞의 {len(pre)}자리는 빼고 셈" if pre else "")
             + (f" → ({nth} - {len(pre)}) ÷ {len(rep)}" if pre else f" → {nth} ÷ {len(rep)}") + f"의 나머지 {k} → {dg}",
             steps=[f"순환마디 {rep}: 숫자 [[{len(rep)}]]개"] + ([f"순환하지 않는 자리: [[{len(pre)}]]개"] if pre else [])
             + [f"{nth - len(pre)} ÷ {len(rep)}의 나머지: [[{k}]]", f"구하는 숫자: [[{dg}]]"])


@kind(U, "10의 거듭제곱을 이용하여 유한소수로", 12)
def _():
    e2, e5 = R.randint(0, 5), R.randint(0, 4)
    d = 2 ** e2 * 5 ** e5
    need(4 <= d <= 250 and e2 != e5)
    n = R.randint(1, d - 1)
    need(math.gcd(n, d) == 1)
    k = max(e2, e5)
    aa = n * 10 ** k // d
    if R.random() < 0.6:
        q, ans_ = f"분수 $\\frac{{{n}}}{{{d}}}$를 $\\frac{{a}}{{10^{{n}}}}$ 꼴로 나타낼 때, 자연수 $a$, $n$에 대하여 $a + n$의 가장 작은 값을 구하시오.", aa + k
    else:
        q, ans_ = f"분수 $\\frac{{{n}}}{{{d}}}$를 분모가 10의 거듭제곱인 분수로 나타낼 때, 분자가 될 수 있는 가장 작은 자연수를 구하시오.", aa
    return P("보통", q, ans_,
             hint="분모를 2와 5의 거듭제곱으로 나타낸 뒤, 지수가 같아지도록 부족한 2나 5를 분모·분자에 곱해요.",
             explanation=f"{d} = {factor_str(d)} → 부족한 {'5' if e2 > e5 else '2'}를 {abs(e2 - e5)}번 곱하면 {n}/{d} = {aa}/10{str(k).translate(SUP)}",
             steps=[f"분모를 10{str(k).translate(SUP)}로: n = [[{k}]]", f"분자 a = [[{aa}]]"] + ([f"a + n = [[{aa + k}]]"] if ans_ == aa + k else []))


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
        return P("보통", f"분수 $\\frac{{{n}}}{{{N}}} \\times a$를 소수로 나타내면 유한소수가 될 때, $a$의 값이 될 수 있는 가장 작은 자연수를 구하시오.", m,
                 hint="기약분수로 만든 뒤, 분모에서 2와 5가 아닌 소인수를 a가 없애 줘야 해요.",
                 explanation=(f"{n}/{N} = {fpl(red)}, " if red != F(n, N) or red.numerator != n else "") + f"분모 {red.denominator} = {factor_str(red.denominator)} → a는 {m}의 배수",
                 steps=[f"기약분수: [[{fpl(red)}]]", f"분모에서 2와 5가 아닌 부분: [[{m}]]", f"가장 작은 a = [[{m}]]"])
    lo, hi = (10, 99) if mode < 0.8 else (1, 50)
    goods = [k for k in range(lo, hi + 1) if only25(F(k, N).denominator)]
    need(goods)
    if mode < 0.8:
        return P("어려움", f"분수 $\\frac{{a}}{{{N}}}$를 소수로 나타내면 유한소수가 될 때, $a$의 값이 될 수 있는 두 자리 자연수의 개수를 구하시오.", len(goods),
                 hint="분모를 소인수분해해서 a가 무엇의 배수여야 하는지 찾아요.",
                 explanation=f"{N} = {factor_str(N)} → a는 {t_}의 배수, 두 자리 {t_}의 배수는 {len(goods)}개",
                 steps=[f"a는 [[{t_}]]의 배수", f"두 자리 자연수 중 개수: [[{len(goods)}]]"])
    return P("어려움", f"분수 $\\frac{{a}}{{{N}}}$를 소수로 나타내면 유한소수가 될 때, 50 이하의 자연수 $a$ 중 가장 큰 수를 구하시오.", max(goods),
             hint="분모를 소인수분해해서 a가 무엇의 배수여야 하는지 찾아요.",
             explanation=f"{N} = {factor_str(N)} → a는 {t_}의 배수, 50 이하에서 가장 큰 것은 {max(goods)}",
             steps=[f"a는 [[{t_}]]의 배수", f"가장 큰 a = [[{max(goods)}]]"])


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
    return P("보통", f"분수 $\\frac{{{num}}}{{{den0} \\times x}}$를 소수로 나타내면 순환소수가 될 때, 다음 중 $x$의 값이 될 수 있는 것은?", ans, choices=ch,
             hint="보기를 하나씩 넣어 기약분수로 만든 뒤, 분모에 2와 5가 아닌 소인수가 남는지 봐요.",
             explanation=f"x = {good}이면 {num}/{den0 * good}" + (f" = {fpl(f)}" if f.numerator != num else "") + f", 분모 {factor_str(f.denominator)}에 2·5가 아닌 소인수가 남아 순환소수",
             steps=[f"x = {good}: 기약분수 [[{fpl(f)}]]", "2·5가 아닌 소인수가 남으면 [[순환소수]]"])


@kind(U, "순환소수를 분수로 (10의 거듭제곱 이용)", 12)
def _():
    v, ip, pre, rep = rand_repeating(3, 2, 2)
    hi, lo = 10 ** (len(pre) + len(rep)), 10 ** len(pre)
    def e(h, l):
        return f"${h if h > 1 else ''}x - {l if l > 1 else ''}x$"
    if R.random() < 0.6:
        pool = [e(h, l) for h in (10, 100, 1000, 10000) for l in (1, 10, 100) if h > l]
        ch, ans = mc(e(hi, lo), pool)
        return P("보통", f"순환소수 $x = {dtex(ip, pre, rep)}$를 분수로 나타낼 때, 가장 편리한 식은?", ans, choices=ch,
                 hint="소수점 아래가 똑같이 되풀이되는 두 수를 만들어 빼면 소수 부분이 없어져요.",
                 explanation=f"{hi}x = {dplain(*expand(v * hi), 6)}, {lo if lo > 1 else ''}x = {dplain(*expand(v * lo), 6)} → 빼면 소수 부분이 없어져요.",
                 steps=[f"순환마디 앞까지 옮기면 [[{lo}]]x", f"순환마디 하나 뒤까지 옮기면 [[{hi}]]x"])
    diff = v * hi - v * lo
    need(diff.denominator == 1)
    return P("보통", f"순환소수 $x = {dtex(ip, pre, rep)}$일 때, ${hi}x - {lo if lo > 1 else ''}x$의 값을 구하시오.", int(diff),
             hint="두 수의 소수 부분이 같아서 빼면 정수만 남아요.",
             explanation=f"{hi}x - {lo if lo > 1 else ''}x = {int(diff)} → x = {int(diff)}/{hi - lo} = {fpl(v)}",
             steps=[f"{hi}x = {dplain(*expand(v * hi), 6)}", f"빼면 [[{int(diff)}]]"])


@kind(U, "순환소수를 기약분수로", 16)
def _():
    v, ip, pre, rep = rand_repeating(3, 2, 3)
    whole, cut = int(str(ip) + pre + rep), int(str(ip) + pre)
    den = int("9" * len(rep) + "0" * len(pre))
    return P("보통" if not pre else "어려움", f"순환소수 ${dtex(ip, pre, rep)}$를 기약분수로 나타내시오.", fpl(v),
             hint="분모는 순환마디 숫자 개수만큼 9, 그 뒤에 순환하지 않는 자리 수만큼 0. 분자는 (전체 수) - (순환하지 않는 부분)",
             explanation=f"({whole} - {cut}) / {den} = {whole - cut}/{den}" + (f" = {fpl(v)}" if fpl(v) != f"{whole - cut}/{den}" else ""),
             steps=[f"분자: {whole} - {cut} = [[{whole - cut}]]", f"분모: [[{den}]]", f"기약분수: [[{fpl(v)}]]"])


@kind(U, "두 분수 사이의 유한소수", 10)
def _():
    t_ = R.choice([3, 7, 9, 11])
    d = 2 ** R.randint(0, 3) * 5 ** R.randint(0, 1) * t_
    need(14 <= d <= 120)
    lo = F(R.randint(1, 4), R.choice([3, 4, 5, 6, 8]))
    hi = F(R.randint(2, 7), R.choice([3, 4, 5, 6, 8]))
    need(F(1, 10) < lo < hi < 1 and hi - lo > F(1, 4))
    ks = [k for k in range(1, d) if lo < F(k, d) < hi]
    cnt = sum(1 for k in ks if only25(F(k, d).denominator))
    need(ks and cnt >= 1)
    return P("어려움", f"두 분수 ${ftex(lo)}$과 ${ftex(hi)}$ 사이에 있는 분수 중에서 분모가 {d}이고 분자가 자연수인 분수 가운데, 유한소수로 나타낼 수 있는 것의 개수를 구하시오.", cnt,
             hint=f"분모 {d}로 통분해 분자의 범위를 구하고, 유한소수가 되려면 분자가 무엇의 배수인지 찾아요.",
             explanation=f"{ks[0]} ≤ a ≤ {ks[-1]}, {d} = {factor_str(d)} → a는 {t_}의 배수 → {cnt}개",
             steps=[f"분자 a의 범위: [[{ks[0]}]] ≤ a ≤ [[{ks[-1]}]]", f"a는 [[{t_}]]의 배수", f"개수: [[{cnt}]]"])


@kind(U, "순환소수에 곱해서 유한소수 만들기", 12)
def _():
    v, ip, pre, rep = rand_repeating(2, 2, 1)
    m = rest25(v.denominator)
    need(1 < m <= 99)
    return P("어려움", f"순환소수 ${dtex(ip, pre, rep)}$에 어떤 자연수 $x$를 곱하면 유한소수가 된다. $x$의 값이 될 수 있는 가장 작은 자연수를 구하시오.", m,
             hint="먼저 순환소수를 기약분수로 바꾸고, 분모에서 2와 5가 아닌 부분을 없애요.",
             explanation=f"{dplain(ip, pre, rep, 6)} = {fpl(v)}, 분모 {v.denominator} = {factor_str(v.denominator)} → x = {m}",
             steps=[f"기약분수: [[{fpl(v)}]]", f"가장 작은 x = [[{m}]]"])


FACTS = [
    ("모든 순환소수는 유리수이다.", True), ("모든 유한소수는 유리수이다.", True),
    ("기약분수의 분모에 2와 5 이외의 소인수가 있으면 유한소수로 나타낼 수 없다.", True),
    ("정수가 아닌 유리수는 유한소수 또는 순환소수로 나타낼 수 있다.", True),
    ("순환소수는 분수로 나타낼 수 있다.", True), ("유한소수는 분모가 10의 거듭제곱인 분수로 나타낼 수 있다.", True),
    ("무한소수는 모두 유리수이다.", False), ("순환소수 중에는 유리수가 아닌 것도 있다.", False),
    ("분모에 3이 있는 분수는 항상 순환소수로 나타내어진다.", False), ("유한소수로 나타낼 수 없는 분수는 유리수가 아니다.", False),
    ("$0.\\dot{9}$는 1보다 작다.", False), ("모든 무한소수는 순환소수이다.", False), ("순환소수는 모두 무리수이다.", False),
]


@kind(U, "유리수와 소수의 관계", 8)
def _():
    want = R.random() < 0.5
    ch, ans = pick(R.sample(FACTS, len(FACTS)), want)
    return P("보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="유한소수와 순환소수는 분수로 나타낼 수 있어요 → 유리수. 순환하지 않는 무한소수는 유리수가 아니에요.",
             explanation="정수가 아닌 유리수 = 유한소수 또는 순환소수. 순환소수·유한소수는 모두 유리수예요.",
             steps=["유한소수, 순환소수는 모두 [[유리수]]"])


# ═════════════════════ 2. 단항식의 계산 ═════════════════════
U = "2. 단항식의 계산"


def law_statements():
    out = []
    v = R.choice("axy")
    m, n = R.randint(2, 7), R.randint(2, 6)
    out += [(f"${v}^{{{m}}} \\times {v}^{{{n}}} = {v}^{{{m + n}}}$", True), (f"${v}^{{{m}}} \\times {v}^{{{n}}} = {v}^{{{m * n}}}$", m * n == m + n)]
    m, n = R.randint(2, 6), R.randint(2, 5)
    out += [(f"$({v}^{{{m}}})^{{{n}}} = {v}^{{{m * n}}}$", True), (f"$({v}^{{{m}}})^{{{n}}} = {v}^{{{m + n}}}$", m * n == m + n)]
    m, n = R.randint(6, 12), R.randint(2, 5)
    out += [(f"${v}^{{{m}}} \\div {v}^{{{n}}} = {v}^{{{m - n}}}$", True)]
    if m % n == 0 and m // n != m - n:
        out += [(f"${v}^{{{m}}} \\div {v}^{{{n}}} = {v}^{{{m // n}}}$", False)]
    out += [(f"${v}^{{{n}}} \\div {v}^{{{n}}} = 0$", False), (f"${v}^{{{n}}} \\div {v}^{{{n}}} = 1$", True),
            (f"${v}^{{{n}}} \\div {v}^{{{n + 2}}} = \\frac{{1}}{{{v}^{{2}}}}$", True), (f"${v}^{{{n}}} \\div {v}^{{{n + 2}}} = {v}^{{2}}$", False)]
    k, m, n = R.randint(2, 4), R.randint(2, 4), R.randint(2, 3)
    out += [(f"$({k}{v}^{{{m}}})^{{{n}}} = {k ** n}{v}^{{{m * n}}}$", True), (f"$({k}{v}^{{{m}}})^{{{n}}} = {k}{v}^{{{m * n}}}$", False),
            (f"$(-{v}^{{{m}}})^{{{2 * n}}} = -{v}^{{{2 * m * n}}}$", False), (f"$(-{v}^{{{m}}})^{{{2 * n + 1}}} = -{v}^{{{m * (2 * n + 1)}}}$", True)]
    m, n, k = R.randint(1, 4), R.randint(2, 4), R.randint(2, 4)
    xm = "x" if m == 1 else f"x^{{{m}}}"
    out += [(f"$\\left(\\frac{{{xm}}}{{y^{{{n}}}}}\\right)^{{{k}}} = \\frac{{x^{{{m * k}}}}}{{y^{{{n * k}}}}}$", True),
            (f"$\\left(\\frac{{{xm}}}{{y^{{{n}}}}}\\right)^{{{k}}} = \\frac{{x^{{{m * k}}}}}{{y^{{{n}}}}}$", False),
            (f"$\\left(-\\frac{{a}}{{b^{{{n}}}}}\\right)^{{{2 * k}}} = -\\frac{{a^{{{2 * k}}}}}{{b^{{{2 * k * n}}}}}$", False)]
    return [(t_.replace("^{1}", ""), v_) for t_, v_ in out]


@kind(U, "지수법칙", 14)
def _():
    want = R.random() < 0.5
    ch, ans = pick(law_statements(), want)
    return P("보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="곱하면 지수를 더하고, 거듭제곱의 거듭제곱은 지수를 곱하고, 나누면 지수를 빼요. 음수의 짝수 제곱은 양수!",
             explanation="aᵐ×aⁿ = aᵐ⁺ⁿ, (aᵐ)ⁿ = aᵐⁿ, aᵐ÷aⁿ = aᵐ⁻ⁿ (m>n), aⁿ÷aⁿ = 1, (ab)ⁿ = aⁿbⁿ 를 하나씩 확인해요.",
             steps=["곱셈: 지수를 [[더한다]]", "거듭제곱의 거듭제곱: 지수를 [[곱한다]]"])


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
    return P("보통", f"${s} = {base}^{{x}}$일 때, 자연수 $x$의 값을 구하시오.", tot,
             hint=f"모든 수를 {base}의 거듭제곱으로 바꾼 뒤, 곱하면 지수를 더하고 나누면 빼요.",
             explanation=", ".join(f"{b0} = {base}{str(lg[b0]).translate(SUP) if lg[b0] > 1 else ''}" for b0 in dict.fromkeys(b0 for b0, _ in terms)) + f" → x = {tot}",
             steps=[f"{b0}{str(abs(e)).translate(SUP) if abs(e) > 1 else ''} = {base}^[[{lg[b0] * abs(e)}]]" for b0, e in terms] + [f"x = [[{tot}]]"])


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
                 steps=[f"b = [[{n}]]", f"a = [[{m}]]", f"c = [[{c * n}]]", f"a + b + c = [[{m + n + c * n}]]"])
    c, m, k = R.randint(2, 4), R.randint(1, 3), R.randint(2, 3)
    inner = (-Q(c) * y ** m / ((c + 1) * x)) ** k
    right = sp.simplify(inner ** 2)
    ch, ans = mono_mc(right, (x, y), [-right, sp.simplify(inner * 2), sp.simplify(Q(c) ** (2 * k) * y ** (2 * m * k) / ((c + 1) * x ** (2 * k)))])
    ym = "y" if m == 1 else f"y^{{{m}}}"
    return P("어려움", f"$\\left\\{{\\left(-\\frac{{{c}{ym}}}{{{c + 1}x}}\\right)^{{{k}}}\\right\\}}^{{2}}$을 간단히 하면?", ans, choices=ch,
             hint="분자·분모에 각각 지수를 분배하고, 음수의 짝수 제곱은 양수예요.",
             explanation=f"전체 지수 {k}×2 = {2 * k}, 짝수 제곱이라 부호 + → {mono(right, (x, y), False)}",
             steps=[f"전체 지수: [[{2 * k}]]", "부호: [[+]]"])


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
                 steps=[f"{k}개 → {k} = 2^[[{lg}]]", f"2^{lg} × 2^{e} = 2^[[{e + lg}]]"])
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
                 steps=[f"분자 = {base}^[[{top + 1}]]", f"분모 = {base}^[[{2 * e2 + 1}]]", f"답: [[{res}]]"])
    k = base
    s = " + ".join([f"{base}^{{{e}}}"] * k)
    return P("보통", f"${s}$을 {base}의 거듭제곱으로 나타내면 ${base}^{{\\square}}$이다. $\\square$ 안에 알맞은 수를 구하시오.", e + 1,
             hint=f"{base}^{e}를 {base}번 더하면 {base} × {base}^{e} 이에요.",
             explanation=f"{base} × {base}{str(e).translate(SUP)} = {base}{str(e + 1).translate(SUP)}",
             steps=[f"{base} × {base}^{e} = {base}^[[{e + 1}]]"])


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
                 steps=[f"{big} = {base}^[[{k}]]", f"{big}ˣ = A^[[{k}]]"])
    e = R.randint(2, 6)
    ch, ans = mc(f"$\\frac{{1}}{{A^{{{k}}}}}$", [f"$\\frac{{1}}{{A^{{{j}}}}}$" for j in range(2, 3 * k + 2) if j != k] + [f"$A^{{{k}}}$"])
    return P("어려움", f"${base}^{{{e}}} = A$라 할 때, $\\frac{{1}}{{{big}^{{{e}}}}}$을 $A$를 사용하여 나타내면?", ans, choices=ch,
             hint=f"{big}을 {base}의 거듭제곱으로 바꿔요.",
             explanation=f"{big}{str(e).translate(SUP)} = {base}{str(k * e).translate(SUP)} = ({base}{str(e).translate(SUP)}){str(k).translate(SUP)} = A{str(k).translate(SUP)}",
             steps=[f"{big}^{e} = {base}^[[{k * e}]]", f"= A^[[{k}]]"])


@kind(U, "n자리 자연수", 12)
def _():
    e2, e5 = R.randint(2, 12), R.randint(2, 12)
    need(e2 != e5)
    extra = R.choice([1, 1, 3, 7, 9, 11, 3 ** 2])
    N = 2 ** e2 * 5 ** e5 * extra
    m = min(e2, e5)
    rest = N // 10 ** m
    q = f"2^{{{e2}}} \\times 5^{{{e5}}}" + (f" \\times {extra}" if extra != 1 else "")
    return P("어려움", f"${q}$은 몇 자리 자연수인지 구하시오.", len(str(N)),
             hint="2와 5를 같은 지수끼리 묶어 10의 거듭제곱을 만들어요.",
             explanation=f"= {rest} × 10{str(m).translate(SUP)} → ({len(str(rest))}자리) + {m} = {len(str(N))}자리",
             steps=[f"2와 5를 묶으면 10^[[{m}]]", f"남은 수: [[{rest}]]", f"자릿수: [[{len(str(N))}]]"])


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
    return P("보통", q, ans, choices=ch, hint=hint, explanation=ex, steps=[f"답: {Pl(ans_, g)}"])


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
             steps=[f"어떤 식 A = {mono(A_, g, False)}", f"바른 계산: {mono(right, g, False)}"])


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
             steps=([f"어떤 식 = ({fmt(Bw, (x,), False)}) + ({fmt(A_, (x,), False)}) = {fmt(orig, (x,), False)}",
                     f"바른 계산 = ({fmt(orig, (x,), False)}) + ({fmt(A_, (x,), False)}) = {fmt(right, (x,), False)}"] if mode == "add_sub" else
                    [f"어떤 식 = ({fmt(Bw, (x,), False)}) - ({fmt(A_, (x,), False)}) = {fmt(orig, (x,), False)}",
                     f"바른 계산 = ({fmt(orig, (x,), False)}) - ({fmt(A_, (x,), False)}) = {fmt(right, (x,), False)}"]))


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
    st = []
    for _ in range(5):
        g = R.choice([(a, b), (x, y)])
        mm = nz(-4, 4) * R.choice(g) * R.choice([1, 1, g[0], g[1]])
        pp = nz(-4, 4) * R.choice(g) + nz(-5, 5) * R.choice([1, g[1], g[0]])
        need(len(sp.Add.make_args(sp.expand(pp))) == 2)
        right = sp.expand(mm * pp)
        ok = R.random() < 0.5
        shown = right if ok else R.choice(poly_wrongs(right, g))
        st.append((f"${T(mm, g)}({fmt(pp, g)}) = {fmt(shown, g)}$", sp.expand(shown - right) == 0))
    want = R.random() < 0.5
    ch, ans = pick(st, want)
    return P("보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="괄호 안의 각 항에 빠짐없이 곱하고, 부호를 꼭 확인해요.",
             explanation="각 식을 분배법칙으로 전개해서 오른쪽과 비교해요.", steps=["괄호 안의 [[모든]] 항에 곱한다"])


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
    div_lines = ["나눗셈은 역수의 곱셈: 각 항을 따로 나눠요"] + [f"{fmt(t_, g, False)} ÷ ({mono(dv, g, False)}) = {fmt(sp.expand(t_ / dv), g, False)}" for t_ in sorted(sp.Add.make_args(num_), key=lambda t_: -sp.Poly(t_, *g).total_degree())]
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
                    f"빼면: {Pl(S)}", f"x = {xv}, y = {yv}를 넣으면 (음수는 괄호): [[{val_}]]"])


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
             steps=[f"거듭제곱 먼저: ({k}x)² = [[{k * k}]]x²", f"앞: ({fmt(num1, (x, y), False)}) ÷ {k * k}x² = {Pl(sp.expand(num1 / (k * x) ** 2))}",
                    f"뒤: ({fmt(p2, (x, y), False)}) × ({m}/y) = {Pl(sp.expand(p2 * m / y))}",
                    f"더하면: {Pl(S)}"])


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
    return P("보통", q, ans, choices=ch, hint="곱한 것은 나누고, 나눈 것은 곱해서 □를 구해요.", explanation=ex, steps=[f"□ = {fmt(BX, g, False)}"])


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
        return P("어려움", q, ans, choices=ch, hint=hint, explanation=ex, steps=[f"넓이 = {Pl(S, g)}"])
    ch, ans = poly_mc(S, g, extra)
    return P("보통", q, ans, choices=ch, hint=hint, explanation=ex, steps=[f"답: {fmt(S, g, False)}"])


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
        s = f"한 개에 {p_}원인 과자 $x$개와 {q_}원짜리 음료수 한 개의 값은 {c}원{'이하이다' if rel == '<=' else '미만이다'}."
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
    return P("쉬움", f"다음 문장을 부등식으로 나타내면? \"{s}\"", ans, choices=ch,
             hint="크지 않다 = 이하(≤), 작지 않다 = 이상(≥), 미만은 <, 초과는 >",
             explanation="문장을 앞에서부터 차례로 식으로 옮겨요: " + right.strip("$").replace("\\geq", "≥").replace("\\leq", "≤"),
             steps=["작지 않다 = [[이상]]", "크지 않다 = [[이하]]"])


@kind(U, "부등식의 해", 12)
def _():
    xv = nz(-4, 5)
    cand = []
    for _ in range(40):
        c1, c0, rhs = nz(-5, 5), R.randint(-8, 8), R.randint(-10, 10)
        rel = R.choice(list(REL))
        cand.append((f"${lin(c1, 0, c0)} {REL[rel]} {rhs}$", holds(c1 * xv + c0, rel, rhs)))
    want = R.random() < 0.6
    ch, ans = pick(cand, want)
    return P("쉬움", f"다음 부등식 중 $x = {xv}$가 해{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint=f"x 자리에 {xv}를 넣어서 부등식이 참이 되는지 하나씩 확인해요.",
             explanation=f"x = {xv}를 대입해서 참·거짓을 따져 보면 답은 {ans}예요.",
             steps=[f"x = [[{xv}]]를 대입", "참이 되면 [[해]]"])


@kind(U, "부등식의 성질", 14)
def _():
    k = R.randint(2, 6)
    big = R.random() < 0.5  # a > b 또는 a < b
    base = ">" if big else "<"
    opp = "<" if big else ">"
    st = [(f"${k}a {base} {k}b$", True), (f"$-{k}a {opp} -{k}b$", True), (f"$a - {k} {base} b - {k}$", True),
          (f"$\\frac{{a}}{{{k}}} {base} \\frac{{b}}{{{k}}}$", True), (f"${k} - a {opp} {k} - b$", True), (f"$-\\frac{{a}}{{{k}}} {opp} -\\frac{{b}}{{{k}}}$", True),
          (f"$-\\frac{{a}}{{{k}}} {base} -\\frac{{b}}{{{k}}}$", False), (f"$-{k}a + 1 {base} -{k}b + 1$", False), (f"$a + {k} {opp} b + {k}$", False),
          (f"${k} - 2a {base} {k} - 2b$", False), (f"${k}a - 1 {opp} {k}b - 1$", False), (f"$-{k}a {base} -{k}b$", False)]
    want = R.random() < 0.5
    ch, ans = pick(st, want)
    return P("보통", f"$a {base} b$일 때, 다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="양변에 같은 음수를 곱하거나 나누면 부등호 방향이 바뀌어요. 더하고 빼는 것은 그대로예요.",
             explanation="음수를 곱하거나 나눈 식은 부등호 방향이 바뀌고, 나머지는 그대로예요.",
             steps=["음수를 곱하면 부등호 방향이 [[바뀐다]]", "같은 수를 더하면 방향이 [[그대로]]"])


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
             steps=[f"x = {lo}일 때 [[{v1}]]", f"x = {hi}일 때 [[{v2}]]"])


@kind(U, "일차부등식 찾기", 8)
def _():
    pool = []
    for _ in range(12):
        p_, q_, r_ = nz(-5, 5), R.randint(-6, 6), R.randint(-6, 6)
        pool += [(f"${lin(p_, 0, q_)} > {lin(p_, 0, r_)}$", False), (f"$x^{{2}} + {lin(p_, 0, 0)} > x^{{2}} {sgn(q_ or 1)}$".replace("+ -", "- "), True),
                 (f"${lin(p_, 0, q_)} \\leq {r_}$", True), (f"${lin(p_, 0, q_)} = {lin(r_ or 2, 0, 0)}$", False), (f"$x^{{2}} + {abs(q_) + 1} > {lin(p_, 0, 0)}$", False),
                 (f"$\\frac{{{abs(p_)}}}{{x}} > {r_}$", False), (f"${abs(p_)}(x {sgn(q_)}) \\geq {abs(p_)}x$", False)]
    pool = [(s.replace("x^{2} + -", "x^{2} - "), t_) for s, t_ in pool]
    want = R.random() < 0.6
    ch, ans = pick(pool, want)
    return P("보통", f"다음 중 일차부등식{'인' if want else '이 아닌'} 것은?", ans, choices=ch,
             hint="모든 항을 왼쪽으로 옮겨 정리했을 때 (x에 대한 일차식) > 0 꼴(또는 <, ≥, ≤)이 되는지 봐요.",
             explanation="정리했을 때 x²이 지워지고 일차식만 남으면 일차부등식, x가 없어지거나 등식이면 아니에요.",
             steps=["우변을 [[좌변]]으로 옮겨 정리", "(일차식) > 0 꼴이면 [[일차부등식]]"])


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
             steps=[f"({rhs} - a) ÷ ({c1}) = {k}", f"a = [[{aval}]]"])


@kind(U, "계수가 문자인 일차부등식", 10)
def _():
    sign_ = R.choice(["<", ">"])
    k = nz(-6, 6)
    rel = R.choice(list(REL))
    res = rel if sign_ == ">" else FLIP[rel]
    m = nz(-5, 5)
    # a x + m a  rel  k a  ->  a(x + m) rel k a -> x + m res k -> x res k - m
    rr = k - m
    right = f"$x {REL[res]} {rr}$"
    wr = [f"$x {REL[FLIP[res]]} {rr}$", f"$x {REL[res]} {-rr}$", f"$x {REL[FLIP[res]]} {-rr}$", f"$x {REL[res]} {k + m}$", f"$x {REL[res]} {k}$"]
    ch, ans = mc(right, wr)
    return P("어려움", f"$a {sign_} 0$일 때, $x$에 대한 일차부등식 $ax {sgn(m)}a {REL[rel]} {k}a$의 해는?".replace("+ 1a", "+ a").replace("- 1a", "- a").replace(" 1a", " a").replace("-1a", "-a"), ans, choices=ch,
             hint="문자로 나눌 때 그 문자가 양수인지 음수인지 꼭 확인해요. 음수면 방향이 바뀌어요.",
             explanation=f"양변을 a로 나누면" + (" (a < 0이라 방향이 바뀜)" if sign_ == "<" else "") + f" x {sgn(m)} {PL[res]} {k} → x {PL[res]} {rr}",
             steps=["음수로 나누면 부등호 방향이 [[바뀐다]]", f"x {PL[res]} [[{rr}]]"])


def word_ineq(level, concept, q, ans_, hint, ex, steps):
    return P(level, q, ans_, hint=hint, explanation=ex, steps=steps)


@kind(U, "일차부등식의 활용: 수", 12)
def _():
    if R.random() < 0.5:
        p_, q_, r_, s_ = R.randint(3, 7), R.randint(1, 9), R.randint(2, 4), R.randint(5, 30)
        need(p_ > r_)
        n = max(k for k in range(1, 500) if p_ * k + q_ < r_ * k + s_) if any(p_ * k + q_ < r_ * k + s_ for k in range(1, 500)) else None
        need(n)
        return word_ineq("보통", "", f"자연수 $x$를 {p_}배 한 수에 {q_}를 더하면, $x$를 {r_}배 한 수에 {s_}를 더한 것보다 작다. 이러한 자연수 $x$ 중 가장 큰 수를 구하시오.", n,
                         f"부등식을 세워요: {p_}x + {q_} < {r_}x + {s_}", f"{p_ - r_}x < {s_ - q_} → 가장 큰 자연수 {n}",
                         [f"{p_ - r_}x < [[{s_ - q_}]]", f"가장 큰 자연수: [[{n}]]"])
    odd = R.random() < 0.5
    k, lim = R.randint(2, 4), R.randint(30, 90)
    # 연속하는 두 짝수/홀수: 작은 수 n, 큰 수 n+2, (작은 수)×k + (큰 수) <= lim, 가장 큰 두 수
    cands = [n for n in range(1, 200) if n % 2 == (1 if odd else 0) and n * k + (n + 2) <= lim]
    need(cands)
    n = max(cands)
    return word_ineq("보통", "", f"연속하는 두 {'홀수' if odd else '짝수'}가 있다. 작은 수의 {k}배와 큰 수의 합이 {lim} 이하일 때, 큰 수가 될 수 있는 가장 큰 수를 구하시오.", n + 2,
                     f"작은 수를 x로 놓으면 큰 수는 x + 2", f"{k}x + (x + 2) ≤ {lim} → x ≤ {fpl(F(lim - 2, k + 1))} → 작은 수 {n}, 큰 수 {n + 2}",
                     [f"{k + 1}x + 2 ≤ {lim}", f"작은 수: [[{n}]]", f"큰 수: [[{n + 2}]]"])


@kind(U, "일차부등식의 활용: 최대 개수", 12)
def _():
    p1, p2 = R.choice([(700, 1000), (900, 1200), (800, 1500), (1200, 1600), (500, 900), (1500, 2000)])
    tot = R.randint(10, 25)
    money = R.randint(p1 * tot // 100 + 1, p2 * tot // 100 - 1) * 100
    k = max(k for k in range(0, tot + 1) if p2 * k + p1 * (tot - k) <= money)
    need(0 < k < tot)
    items = R.choice([("공책", "볼펜"), ("사과", "배"), ("연필", "색연필"), ("빵", "케이크 조각"), ("엽서", "스티커북")])
    return word_ineq("보통", "", f"한 개에 {p1}원인 {josa(items[0], '과')} 한 개에 {p2}원인 {josa(items[1], '을')} 합하여 {tot}개를 사려고 한다. 전체 가격을 {money}원 이하로 할 때, {josa(items[1], '은')} 최대 몇 개까지 살 수 있는지 구하시오.",
                     k, f"{josa(items[1], '을')} x개라 하면 {josa(items[0], '은')} ({tot} - x)개예요.",
                     f"{p2}x + {p1}({tot} - x) ≤ {money} → {p2 - p1}x ≤ {money - p1 * tot} → x ≤ {fpl(F(money - p1 * tot, p2 - p1))}",
                     [f"{p2 - p1}x ≤ [[{money - p1 * tot}]]", f"최대 [[{k}]]개"])


@kind(U, "일차부등식의 활용: 도형", 12)
def _():
    if R.random() < 0.5:
        t_, bt = R.randint(2, 8), R.randint(4, 12)
        S = R.randint(10, 80)
        h = min(h for h in range(1, 300) if (t_ + bt) * h / 2 >= S)
        return word_ineq("보통", "", f"윗변의 길이가 {t_} cm, 아랫변의 길이가 {bt} cm인 사다리꼴의 넓이가 {S} cm² 이상이 되게 하려면, 높이는 최소 몇 cm이어야 하는지 구하시오. (단, 높이는 자연수)",
                         h, "사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2", f"({t_} + {bt}) × h ÷ 2 ≥ {S} → h ≥ {fpl(F(2 * S, t_ + bt))} → {h}",
                         [f"{t_ + bt}h ÷ 2 ≥ {S}", f"h ≥ [[{fpl(F(2 * S, t_ + bt))}]]", f"최소 [[{h}]] cm"])
    d, P_ = R.randint(3, 15), R.randint(40, 120)
    w = max(w for w in range(1, 200) if 2 * (w + w + d) <= P_)
    return word_ineq("보통", "", f"가로의 길이가 세로의 길이보다 {d} cm 더 긴 직사각형의 둘레의 길이가 {P_} cm 이하일 때, 세로의 길이는 최대 몇 cm인지 구하시오. (단, 길이는 자연수)",
                     w, "세로를 x cm라 하면 가로는 (x + d) cm, 둘레 = 2 × (가로 + 세로)",
                     f"2(x + x + {d}) ≤ {P_} → 4x ≤ {P_ - 2 * d} → x ≤ {fpl(F(P_ - 2 * d, 4))}",
                     [f"4x ≤ [[{P_ - 2 * d}]]", f"최대 [[{w}]] cm"])


@kind(U, "일차부등식의 활용: 추가 요금", 12)
def _():
    base_n = R.choice([4, 5, 10])
    p1 = R.choice([2000, 2500, 3000, 4000])
    p2 = p1 - R.choice([500, 800, 1000, 1200])
    avg = R.randint(p2 // 100 + 1, p1 // 100 - 1) * 100
    cands = [n for n in range(base_n + 1, 500) if base_n * p1 + (n - base_n) * p2 < n * avg]
    need(cands)
    n = min(cands)
    place = R.choice(["체험관", "수영장", "박물관", "놀이 시설"])
    return word_ineq("어려움", "", f"어느 {place}의 입장료는 {base_n}명까지는 한 사람에 {p1}원이고, {base_n}명을 넘으면 넘은 사람은 한 사람에 {p2}원이다. 한 사람의 평균 입장료가 {avg}원보다 적어지려면 최소 몇 명이 입장해야 하는지 구하시오.",
                     n, f"x명이 입장하면 전체 요금은 {base_n * p1} + {p2}(x - {base_n})원",
                     f"{base_n * p1} + {p2}(x - {base_n}) < {avg}x → x > {fpl(F(base_n * (p1 - p2), avg - p2))} → {n}명",
                     [f"{base_n * p1} + {p2}(x - {base_n}) < {avg}x", f"최소 [[{n}]]명"])


@kind(U, "일차부등식의 활용: 예금액", 12)
def _():
    a0, b0 = R.randint(1, 8) * 10000, R.randint(2, 10) * 10000
    da, db = R.randint(2, 8) * 1000, R.randint(1, 4) * 1000
    k = R.choice([1, 2])
    cands = [m for m in range(1, 200) if a0 + da * m > k * (b0 + db * m)]
    need(cands and da > k * db)
    m = min(cands)
    need(2 <= m <= 24)
    who = R.choice([("지수", "동생"), ("민호", "누나"), ("서연", "오빠")])
    cond = "많아지는" if k == 1 else "2배보다 많아지는"
    return word_ineq("어려움", "", f"현재 {who[0]}의 통장에는 {a0}원, {who[1]}의 통장에는 {b0}원이 있다. 다음 달부터 {josa(who[0], '는')} 매달 {da}원씩, {josa(who[1], '는')} 매달 {db}원씩 예금한다면, {who[0]}의 예금액이 처음으로 {who[1]}의 예금액{'보다' if k == 1 else '의'} {cond} 것은 몇 개월 후인지 구하시오.".replace("예금액보다 많아지는", "예금액보다 많아지는"),
                     m, f"x개월 후 {who[0]}: {a0} + {da}x, {who[1]}: {b0} + {db}x",
                     f"{a0} + {da}x > {k if k > 1 else ''}({b0} + {db}x) → x > {fpl(F(k * b0 - a0, da - k * db))} → {m}개월 후",
                     [f"{a0} + {da}x > {k if k > 1 else ''}({b0} + {db}x)", f"[[{m}]]개월 후"])


@kind(U, "일차부등식의 활용: 거리, 속력, 시간", 12)
def _():
    if R.random() < 0.5:
        v1, v2 = R.choice([(2, 3), (3, 4), (2, 4), (3, 5), (4, 6)])
        tt = F(R.randint(2, 8), 2)
        dmax = tt * v1 * v2 / (v1 + v2)
        need(dmax.denominator <= 5)
        h, mi = int(tt), int((tt - int(tt)) * 60)
        tstr = f"{h}시간" + (f" {mi}분" if mi else "")
        return word_ineq("어려움", "", f"어느 지점까지 갈 때는 시속 {v1} km로, 돌아올 때는 같은 길을 시속 {v2} km로 걸어서 {tstr} 이내로 다녀오려고 한다. 최대 몇 km 떨어진 지점까지 다녀올 수 있는지 구하시오.",
                         fpl(dmax), f"시간 = 거리 ÷ 속력. {tstr} = {fpl(tt)}시간",
                         f"x/{v1} + x/{v2} ≤ {fpl(tt)} → x ≤ {fpl(dmax)}", [f"x/{v1} + x/{v2} ≤ [[{fpl(tt)}]]", f"최대 [[{fpl(dmax)}]] km"])
    D = R.randint(3, 12)
    vw, vr = R.choice([(3, 6), (4, 8), (3, 9), (4, 12), (2, 6)])
    tt = F(R.randint(2, 6), 2)
    # 걷다가 뛰기: 걸은 거리 x, 뛴 거리 D - x, 시간 x/vw + (D-x)/vr <= tt -> x <= ...
    xmax = (tt - F(D, vr)) / (F(1, vw) - F(1, vr))
    need(0 < xmax < D and xmax.denominator <= 4)
    h, mi = int(tt), int((tt - int(tt)) * 60)
    tstr = f"{h}시간" + (f" {mi}분" if mi else "")
    return word_ineq("어려움", "", f"집에서 {D} km 떨어진 공원까지 가는데, 처음에는 시속 {vw} km로 걷다가 도중에 시속 {vr} km로 뛰어서 {tstr} 이내에 도착하려고 한다. 걸은 거리는 최대 몇 km인지 구하시오.",
                     fpl(xmax), f"걸은 거리 x km, 뛴 거리 ({D} - x) km. 시간 = 거리 ÷ 속력",
                     f"x/{vw} + ({D} - x)/{vr} ≤ {fpl(tt)} → x ≤ {fpl(xmax)}", [f"x/{vw} + ({D} - x)/{vr} ≤ [[{fpl(tt)}]]", f"최대 [[{fpl(xmax)}]] km"])


@kind(U, "일차부등식의 활용: 유리한 방법 선택", 12)
def _():
    if R.random() < 0.5:
        price = R.choice([600, 800, 1000, 1200, 1500, 2000])
        disc = R.choice([10, 20, 25, 30])
        fare = R.choice([1000, 1200, 1500, 2000, 2500])
        cands = [n for n in range(1, 500) if price * n * (100 - disc) / 100 + fare < price * n]
        n = min(cands)
        item = R.choice(["아이스크림", "음료수", "공책", "과자"])
        return word_ineq("어려움", "", f"동네 가게에서 한 개에 {price}원인 {josa(item, '을')} 할인 매장에서는 {disc}% 할인된 가격에 판다. 할인 매장에 다녀오는 왕복 교통비가 {fare}원일 때, {josa(item, '을')} 최소 몇 개 이상 사야 할인 매장에서 사는 것이 유리한지 구하시오.",
                         n, f"x개를 산다면: 동네 가게 {price}x원, 할인 매장 {price * (100 - disc) // 100}x + {fare}원",
                         f"{price * (100 - disc) / 100:g}x + {fare} < {price}x → x > {fpl(F(fare * 100, price * disc))} → {n}개",
                         [f"{price * (100 - disc) / 100:g}x + {fare} < {price}x", f"최소 [[{n}]]개"])
    fee, per_m, per = R.choice([(10000, 3000, 5000), (20000, 2000, 4000), (15000, 2500, 4000), (12000, 1000, 2500)])
    cands = [n for n in range(1, 200) if fee + per_m * n < per * n]
    n = min(cands)
    return word_ineq("어려움", "", f"어느 수영장의 1회 입장료는 {per}원이다. 연회원은 가입비 {fee}원을 내고 1회에 {per_m}원만 내면 된다. 한 해에 최소 몇 회 이상 이용해야 연회원이 유리한지 구하시오.",
                     n, f"x회 이용하면: 일반 {per}x원, 연회원 {fee} + {per_m}x원",
                     f"{fee} + {per_m}x < {per}x → x > {fpl(F(fee, per - per_m))} → {n}회", [f"{fee} + {per_m}x < {per}x", f"최소 [[{n}]]회"])


# ═════════════════════ 5. 연립일차방정식 ═════════════════════
U = "5. 연립일차방정식"


def eq_tex(a1, b1, c1):
    return f"{lin(a1, b1)} = {c1}"


@kind(U, "미지수가 2개인 일차방정식", 8)
def _():
    p_, q_, r_ = nz(-5, 5), nz(-5, 5), R.randint(-9, 9)
    pool = [(f"${lin(p_, q_)} = {r_}$", True), (f"${lin(p_, 0, r_)} = {lin(q_, 0, 0)}$", False), (f"$x + y^{{2}} = {abs(r_) + 1}$", False),
            (f"$xy {sgn(abs(r_) + 1)} = 0$", False), (f"$\\frac{{1}}{{x}} + y = {abs(q_)}$", False), (f"$x {sgn(q_)}y = {q_}y {sgn(r_)}$", False),
            (f"${abs(p_)}(x + y) = {abs(p_)}x {sgn(r_)}$", False), (f"$x^{{2}} + y = x^{{2}} {sgn(q_)}x$", True), (f"${lin(p_, q_, 0)} = {lin(p_, 0, r_)}$", False),
            (f"$\\frac{{x}}{{{abs(p_) + 1}}} - y = {r_}$", True)]
    want = R.random() < 0.6
    ch, ans = pick(pool, want)
    return P("쉬움", f"다음 중 미지수가 2개인 일차방정식{'인' if want else '이 아닌'} 것은?", ans, choices=ch,
             hint="정리했을 때 ax + by + c = 0 (a ≠ 0, b ≠ 0) 꼴인지 봐요. 한 문자가 지워지면 아니에요.",
             explanation="모든 항을 왼쪽으로 옮겨 정리했을 때 x와 y가 모두 남고 차수가 1인 등식이어야 해요.",
             steps=["정리: ax + by + c = 0, a ≠ 0, [[b ≠ 0]]"])


@kind(U, "일차방정식의 해", 12)
def _():
    A1, B1 = nz(-4, 4), nz(-4, 4)
    sols = [(i, j) for i in range(-6, 7) for j in range(-6, 7)]
    C1 = A1 * R.randint(-4, 4) + B1 * R.randint(-4, 4)
    good = [(i, j) for i, j in sols if A1 * i + B1 * j == C1]
    bad = [(i, j) for i, j in sols if A1 * i + B1 * j != C1]
    need(len(good) >= 4)
    want = R.random() < 0.5
    pool = [(f"$({i}, {j})$", True) for i, j in R.sample(good, min(6, len(good)))] + [(f"$({i}, {j})$", False) for i, j in R.sample(bad, 6)]
    ch, ans = pick(pool, want)
    return P("쉬움", f"다음 중 일차방정식 ${eq_tex(A1, B1, C1)}$의 해{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint="x, y 값을 넣어서 등식이 성립하는지 하나씩 확인해요.",
             explanation=f"각 순서쌍을 대입해 좌변이 {C1}가 되는지 확인해요.", steps=[f"좌변 = [[{C1}]]이면 해"])


@kind(U, "x, y가 자연수인 해", 12)
def _():
    A1, B1 = R.randint(1, 5), R.randint(1, 5)
    C1 = R.randint(8, 40)
    sols = [(i, j) for i in range(1, 50) for j in range(1, 50) if A1 * i + B1 * j == C1]
    need(1 <= len(sols) <= 8)
    return P("보통", f"$x$, $y$의 값이 자연수일 때, 일차방정식 ${eq_tex(A1, B1, C1)}$의 해의 개수를 구하시오.", len(sols),
             hint="y(또는 x)에 1, 2, 3, ...을 차례로 넣어 다른 문자도 자연수가 되는 것만 찾아요.",
             explanation="해: " + ", ".join(f"({i}, {j})" for i, j in sols),
             steps=["해: " + ", ".join(f"({i}, {j})" for i, j in sols), f"개수: [[{len(sols)}]]"])


@kind(U, "해가 주어질 때 계수 구하기", 12)
def _():
    px, py = nz(-5, 5), nz(-5, 5)
    A1, av = nz(-5, 5), nz(-6, 6)
    if R.random() < 0.5:
        C1 = A1 * px - av * py
        q = f"일차방정식 ${A1}x - ay = {C1}$의 한 해가 $({px}, {py})$일 때, 수 $a$의 값을 구하시오."
        ex = f"{A1}×({px}) - a×({py}) = {C1} → a = {av}"
    else:
        C1 = av * px + A1 * py
        q = f"일차방정식 $ax {sgn(A1)}y = {C1}$의 한 해가 $({px}, {py})$일 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y")
        ex = f"a×({px}) + ({A1})×({py}) = {C1} → a = {av}"
    return P("보통", q, av, hint="해를 방정식에 대입하면 등식이 성립해요.", explanation=ex, steps=[f"x = {px}, y = {py} 대입", f"a = [[{av}]]"])


@kind(U, "연립방정식의 해", 10)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    sys_ = []
    for _ in range(30):
        a1, b1, a2, b2 = (nz(-3, 3) for _ in range(4))
        if a1 * b2 == a2 * b1:
            continue
        c1, c2 = a1 * sx + b1 * sy, a2 * sx + b2 * sy + R.choice([0, 0, 1, -1, 2])
        sys_.append((f"$\\begin{{cases}} {eq_tex(a1, b1, c1)} \\\\ {eq_tex(a2, b2, c2)} \\end{{cases}}$", a2 * sx + b2 * sy == c2))
    ch, ans = pick(sys_, True)
    return P("보통", f"다음 연립방정식 중 $x = {sx}$, $y = {sy}$가 해인 것은?", ans, choices=ch,
             hint="두 식 모두에 대입해서 둘 다 성립해야 해요.", explanation="두 방정식을 동시에 만족시키는 것을 찾아요.",
             steps=["두 식 [[모두]] 성립해야 해"])


@kind(U, "연립방정식의 계수가 문자인 경우", 12)
def _():
    sx, sy = nz(-4, 4), nz(-4, 4)
    av, bv = nz(-5, 5), nz(-5, 5)
    k1, k2 = nz(-4, 4), nz(-4, 4)
    c1, c2 = av * sx + k1 * sy, bv * sx + k2 * sy
    combo = R.choice([("a + b", av + bv), ("a - b", av - bv), ("ab", av * bv)])
    return P("보통", f"연립방정식 $\\begin{{cases}} ax {sgn(k1)}y = {c1} \\\\ bx {sgn(k2)}y = {c2} \\end{{cases}}$의 해가 $x = {sx}$, $y = {sy}$일 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y"),
             combo[1], hint="해를 두 식에 각각 대입하면 a, b에 대한 일차방정식이 돼요.",
             explanation=f"{sx}a + ({k1 * sy}) = {c1} → a = {av}, {sx}b + ({k2 * sy}) = {c2} → b = {bv}",
             steps=[f"a = [[{av}]]", f"b = [[{bv}]]", f"{combo[0]} = [[{combo[1]}]]"])


@kind(U, "연립방정식의 해가 문자인 경우", 12)
def _():
    p_, q_ = nz(-3, 3), nz(-3, 3)
    m, bv = nz(-4, 4), nz(-5, 5)
    c1 = p_ * m + q_ * bv
    r_ = nz(-3, 3)
    c2 = R.randint(-9, 9)
    aval = F(c2 - r_ * bv, m)
    need(aval.denominator == 1)
    return P("어려움", f"연립방정식 $\\begin{{cases}} {eq_tex(p_, q_, c1)} \\\\ ax {sgn(r_)}y = {c2} \\end{{cases}}$의 해가 $x = {m}$, $y = b$일 때, 수 $a$, $b$에 대하여 $a + b$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y"),
             fpl(aval + bv), hint="a가 없는 식에 먼저 대입해서 b를 구해요.",
             explanation=f"{p_}×({m}) + ({q_})b = {c1} → b = {bv}, ({m})a + ({r_})×({bv}) = {c2} → a = {fpl(aval)}",
             steps=[f"b = [[{bv}]]", f"a = [[{fpl(aval)}]]", f"a + b = [[{fpl(aval + bv)}]]"])


def sys_q(level, concept, txt, sx, sy, hint, exp, word="연립방정식", steps=None):
    return P(level, f"{word} ${txt}$를 푸시오. (예: x=1, y=2)", f"x={sx}, y={sy}", hint=hint, explanation=exp,
             steps=steps or [f"x = [[{sx}]]", f"y = [[{sy}]]"])


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
              f"괄호를 풀면: {lin(cx, 0, b2 * k, tex=False)} = {c}", f"{lin(cx, 0, tex=False)} = {c - b2 * k} → x = [[{sx}]]",
              f"x = {sx}를 ①에 넣으면: y = {m}×({sx}) + ({k}) = [[{sy}]]"]
    else:
        # x = my + k 꼴
        k2 = sx - m * sy
        need(b2 + a2 * m != 0)
        txt = f"\\begin{{cases}} x = {fx(m, k2, 'y')} \\\\ {eq_tex(a2, b2, c)} \\end{{cases}}"
        cy = b2 + a2 * m
        st = [f"① x = {fx(m, k2, 'y', tex=False)}", f"② {lin(a2, b2, tex=False)} = {c}",
              f"①을 ②의 x 자리에 넣으면: {a2}({fx(m, k2, 'y', tex=False)}) {sgn(b2)}y = {c}",
              f"괄호를 풀면: {lin(cy, 0, a2 * k2, 'y', 'x', tex=False)} = {c}", f"{lin(cy, 0, 0, 'y', 'x', tex=False)} = {c - a2 * k2} → y = [[{sy}]]",
              f"y = {sy}를 ①에 넣으면: x = {m}×({sy}) + ({k2}) = [[{sx}]]"]
    return sys_q("보통", "", txt, sx, sy, "한 식을 다른 식의 문자 자리에 그대로 넣어요.", f"대입해서 풀면 x = {sx}, y = {sy}", steps=st)


@kind(U, "연립방정식의 풀이: 가감법", 14)
def _():
    sx, sy = nz(-6, 6), nz(-6, 6)
    a1, b1, a2, b2 = nz(-5, 5), nz(-5, 5), nz(-5, 5), nz(-5, 5)
    need(a1 * b2 - a2 * b1 != 0 and abs(a1) + abs(b1) > 2)
    txt = f"\\begin{{cases}} {eq_tex(a1, b1, a1 * sx + b1 * sy)} \\\\ {eq_tex(a2, b2, a2 * sx + b2 * sy)} \\end{{cases}}"
    return sys_q("보통", "", txt, sx, sy, "없앨 문자의 계수의 절댓값을 같게 만든 뒤 더하거나 빼요.", f"가감법으로 풀면 x = {sx}, y = {sy}",
                 steps=elim_steps(a1, b1, a1 * sx + b1 * sy, a2, b2, a2 * sx + b2 * sy, sx, sy))


@kind(U, "괄호가 있는 연립방정식", 10)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    p_, q_, r_ = nz(-3, 3, (0, 1)), nz(-4, 4), nz(-3, 3)
    # p(x + q) + r y = c1  ,  a2 x + b2 y = c2
    c1 = p_ * (sx + q_) + r_ * sy
    a2, b2 = nz(-4, 4), nz(-4, 4)
    need(p_ * b2 - a2 * r_ != 0)
    txt = f"\\begin{{cases}} {'-' if p_ == -1 else p_}({lin(1, 0, q_)}) {sgn(r_)}y = {c1} \\\\ {eq_tex(a2, b2, a2 * sx + b2 * sy)} \\end{{cases}}".replace("+ 1y", "+ y").replace("- 1y", "- y")
    a1_, c1_ = p_, c1 - p_ * q_
    return sys_q("보통", "", txt, sx, sy, "괄호를 먼저 풀어서 ax + by = c 꼴로 정리해요.", f"정리해서 풀면 x = {sx}, y = {sy}",
                 steps=elim_steps(a1_, r_, c1_, a2, b2, a2 * sx + b2 * sy, sx, sy,
                                  pre=[f"첫째 식의 괄호를 풀고 수를 옮기면: {lin(a1_, r_, tex=False)} = {c1_}"]))


@kind(U, "계수가 소수 또는 분수인 연립방정식", 14)
def _():
    sx, sy = nz(-6, 6), nz(-6, 6)
    a1, b1 = nz(-5, 5), nz(-5, 5)
    v1 = F(a1 * sx + b1 * sy, 10)
    m, n = R.choice([(2, 3), (3, 4), (2, 5), (4, 3), (3, 2), (6, 4)])
    s_ = R.choice([1, -1])
    v2 = F(sx, m) + s_ * F(sy, n)
    need(a1 * s_ * m - b1 * n != 0 or True)
    need(F(a1, 10) * F(s_, n) - F(b1, 10) * F(1, m) != 0)
    dec = f"{a1 / 10:g}x {('+ ' + format(b1 / 10, 'g')) if b1 > 0 else ('- ' + format(-b1 / 10, 'g'))}y = {float(v1):g}"
    frac = f"\\frac{{x}}{{{m}}} {'+' if s_ > 0 else '-'} \\frac{{y}}{{{n}}} = {ftex(v2)}"
    txt = f"\\begin{{cases}} {dec} \\\\ {frac} \\end{{cases}}" if R.random() < 0.5 else f"\\begin{{cases}} {frac} \\\\ {dec} \\end{{cases}}"
    L_ = m * n // math.gcd(m, n)
    fa, fb, fc = L_ // m, s_ * (L_ // n), v2 * L_
    need(fc.denominator == 1)
    pre = [f"소수 식의 양변에 10을 곱하면: {lin(a1, b1, tex=False)} = {a1 * sx + b1 * sy}",
           f"분수 식의 양변에 분모 {m}, {n}의 최소공배수 {L_}를 곱하면: {lin(fa, fb, tex=False)} = {int(fc)}"]
    return sys_q("어려움", "", txt, sx, sy, "소수는 10을, 분수는 분모의 최소공배수를 곱해 정수 계수로 바꿔요.", f"정수 계수로 바꿔 풀면 x = {sx}, y = {sy}",
                 steps=elim_steps(a1, b1, a1 * sx + b1 * sy, fa, fb, int(fc), sx, sy, pre=pre))


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
                                  pre=[f"A = C: {lin(a1, b1, k1, tex=False)} = {Cv}", f"B = C: {lin(a2, b2, k2, tex=False)} = {Cv}", "수를 오른쪽으로 옮겨 정리하면"]))


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
    return P("어려움", f"두 연립방정식 $\\begin{{cases}} {e1} \\\\ {f1} \\end{{cases}}$, $\\begin{{cases}} {e2} \\\\ {f2} \\end{{cases}}$의 해가 서로 같을 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.",
             combo[1], hint="문자 a, b가 없는 두 식끼리 먼저 연립해서 해를 구해요.",
             explanation=f"{lin(p1, q1, tex=False)} = {p1 * sx + q1 * sy}, {lin(p2, q2, tex=False)} = {p2 * sx + q2 * sy} → x = {sx}, y = {sy} → a = {av}, b = {bv}",
             steps=[f"x = [[{sx}]], y = [[{sy}]]", f"a = [[{av}]]", f"b = [[{bv}]]", f"{combo[0]} = [[{combo[1]}]]"])


def word_sys(level, q, ans_, hint, ex, steps):
    return P(level, q, ans_, hint=hint, explanation=ex, steps=steps)


@kind(U, "연립방정식의 활용: 수", 14)
def _():
    if R.random() < 0.5:
        s_ = R.randint(20, 90)
        k, d = R.randint(2, 5), R.randint(-6, 8)
        sm = [(i, s_ - i) for i in range(1, s_) if s_ - i == k * i + d and s_ - i > i]
        need(sm)
        sml, bg = sm[0]
        return word_sys("보통", f"합이 {s_}인 두 자연수가 있다. 큰 수는 작은 수의 {k}배보다 {abs(d)}만큼 {'크다' if d >= 0 else '작다'}. 큰 수를 구하시오.", bg,
                        "작은 수 x, 큰 수 y로 놓고 두 식을 세워요.", f"x + y = {s_}, y = {k}x {sgn(d)} → x = {sml}, y = {bg}",
                        [f"x + y = [[{s_}]]", f"작은 수: [[{sml}]]", f"큰 수: [[{bg}]]"])
    t_, u = R.randint(1, 9), R.randint(1, 9)
    need(t_ != u)
    diff = 9 * (u - t_)
    word = "크다" if diff > 0 else "작다"
    return word_sys("어려움", f"각 자리 숫자의 합이 {t_ + u}인 두 자리 자연수가 있다. 십의 자리 숫자와 일의 자리 숫자를 바꾼 수는 처음 수보다 {abs(diff)}만큼 {word}. 처음 수를 구하시오.",
                    10 * t_ + u, "십의 자리 x, 일의 자리 y이면 처음 수는 10x + y, 바꾼 수는 10y + x예요.",
                    f"x + y = {t_ + u}, (10y + x) - (10x + y) = {diff} → x = {t_}, y = {u}",
                    [f"x + y = [[{t_ + u}]]", f"y - x = [[{u - t_}]]", f"처음 수: [[{10 * t_ + u}]]"])


@kind(U, "연립방정식의 활용: 개수와 가격", 14)
def _():
    if R.random() < 0.5:
        p1, p2 = R.choice([(2, 3), (3, 4), (3, 5), (4, 6), (2, 5)])
        n1, n2 = R.randint(3, 20), R.randint(3, 20)
        tot, sc = n1 + n2, p1 * n1 + p2 * n2
        return word_sys("보통", f"어느 시험에서 {p1}점짜리와 {p2}점짜리 문제를 합하여 {tot}개를 모두 맞혀 {sc}점을 받았다. {p2}점짜리 문제는 몇 개인지 구하시오.", n2,
                        f"{p1}점짜리 x개, {p2}점짜리 y개로 놓아요.", f"x + y = {tot}, {p1}x + {p2}y = {sc} → x = {n1}, y = {n2}",
                        [f"x + y = [[{tot}]]", f"{p1}x + {p2}y = [[{sc}]]", f"{p2}점짜리: [[{n2}]]개"])
    p1, p2 = R.choice([(3000, 2000), (5000, 3000), (4000, 2500), (6000, 3500), (2000, 1200)])
    n1, n2 = R.randint(2, 15), R.randint(2, 15)
    tot, money = n1 + n2, p1 * n1 + p2 * n2
    who = R.choice([("어른", "청소년"), ("어른", "어린이")])
    return word_sys("보통", f"어느 미술관의 입장료는 {who[0]} {p1}원, {who[1]} {p2}원이다. {who[0]}과 {who[1]}을 합하여 {tot}명이 입장하고 {money}원을 냈을 때, {who[1]}은 몇 명인지 구하시오.", n2,
                    f"{who[0]} x명, {who[1]} y명으로 놓아요.", f"x + y = {tot}, {p1}x + {p2}y = {money} → x = {n1}, y = {n2}",
                    [f"x + y = [[{tot}]]", f"{who[1]}: [[{n2}]]명"])


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
    return word_sys("보통", f"현재 {who}의 나이는 {first}, {yrs}년 후에는 {who}의 나이가 조카의 나이의 {m}배{'보다 ' + str(extra) + '살 많아진다' if extra else '가 된다'}. 현재 조카의 나이를 구하시오.",
                    child, f"현재 {who} x살, 조카 y살. {yrs}년 후에는 둘 다 {yrs}살씩 많아져요.",
                    f"x = {k}y {sgn(d)}, x + {yrs} = {m}(y + {yrs}) {sgn(extra)} → y = {child}, x = {adult}",
                    [f"x = {k}y {sgn(d)}", f"조카: [[{child}]]살"])


@kind(U, "연립방정식의 활용: 도형", 12)
def _():
    if R.random() < 0.6:
        h = R.randint(3, 20)
        k, d = R.randint(2, 3), R.randint(-5, 6)
        w = k * h + d
        need(w > 0 and w != h)
        P_ = 2 * (w + h)
        rel = f"세로의 길이의 {k}배보다 {abs(d)} cm {'길' if d > 0 else '짧'}" if d else f"세로의 길이의 {k}배"
        return word_sys("보통", f"둘레의 길이가 {P_} cm인 직사각형이 있다. 가로의 길이가 {rel}{'을' if d else '일'} 때, 이 직사각형의 넓이를 구하시오.".replace("배일 때", "배일 때").replace("길을 때", "길 때").replace("짧을 때", "짧을 때"),
                        w * h, "가로 x cm, 세로 y cm로 놓고 둘레와 길이 관계로 두 식을 세워요.",
                        f"2(x + y) = {P_}, x = {k}y {sgn(d)} → x = {w}, y = {h} → 넓이 {w * h}",
                        [f"x + y = [[{P_ // 2}]]", f"세로 = [[{h}]]", f"넓이 = [[{w * h}]] cm²"])
    bt = R.randint(6, 16)
    d = R.randint(1, bt - 2)
    tp = bt - d
    hgt = R.randint(2, 10)
    S = (tp + bt) * hgt
    need(S % 2 == 0)
    S //= 2
    return word_sys("보통", f"윗변의 길이가 아랫변의 길이보다 {d} cm 짧고 높이가 {hgt} cm인 사다리꼴의 넓이가 {S} cm²일 때, 아랫변의 길이를 구하시오.", bt,
                    "윗변 x cm, 아랫변 y cm로 놓아요. 사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2",
                    f"x = y - {d}, (x + y) × {hgt} ÷ 2 = {S} → y = {bt}", [f"x + y = [[{tp + bt}]]", f"아랫변: [[{bt}]] cm"])


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
    return word_sys("어려움", f"{place}에 갈 때는 시속 {v1} km로 걷고, 올 때는 갈 때보다 {extra} km 더 먼 길을 시속 {v2} km로 걸었더니 모두 {tstr}이 걸렸다. 갈 때 걸은 거리는 몇 km인지 구하시오.",
                    d1, "갈 때 x km, 올 때 y km. 시간 = 거리 ÷ 속력",
                    f"y = x + {extra}, x/{v1} + y/{v2} = {fpl(tt)} → x = {d1}", [f"y = x + [[{extra}]]", f"x/{v1} + y/{v2} = [[{fpl(tt)}]]", f"갈 때 거리: [[{d1}]] km"])


@kind(U, "연립방정식의 활용: 계단 오르기", 10)
def _():
    up, down = R.choice([(2, 1), (3, 1), (3, 2), (4, 1)])
    w, l = R.randint(3, 15), R.randint(2, 12)
    pos = up * w - down * l
    need(pos > 0)
    game = R.choice(["동전을 던져 앞면이 나오면", "주사위를 던져 짝수가 나오면"])
    other = "뒷면이 나오면" if "동전" in game else "홀수가 나오면"
    return word_sys("어려움", f"{game} {up}계단 올라가고, {other} {down}계단 내려가기로 했다. {w + l}번 던진 뒤 처음 위치보다 {pos}계단 위에 있었다. {game.split('나오면')[0].split('던져 ')[1]}는 몇 번 나왔는지 구하시오.".replace("앞면이 는", "앞면은").replace("짝수가 는", "짝수는"),
                    w, "올라간 횟수 x, 내려간 횟수 y로 놓아요.", f"x + y = {w + l}, {lin(up, -down, tex=False)} = {pos} → x = {w}, y = {l}",
                    [f"x + y = [[{w + l}]]", f"{lin(up, -down, tex=False)} = [[{pos}]]", f"답: [[{w}]]번"])


# ═════════════════════ 6. 일차함수와 그 그래프 ═════════════════════
U = "6. 일차함수와 그 그래프"
FUNC_FACTS = [("자연수 $x$보다 작은 짝수 $y$", False), ("자연수 $x$의 약수 $y$", False), ("자연수 $x$를 3으로 나눈 나머지 $y$", True),
              ("한 변의 길이가 $x$ cm인 정사각형의 둘레 $y$ cm", True), ("절댓값이 $x$인 수 $y$", False), ("$x$보다 큰 자연수 $y$", False),
              ("자연수 $x$의 약수의 개수 $y$", True), ("시속 $x$ km로 2시간 동안 간 거리 $y$ km", True), ("자연수 $x$의 배수 $y$", False),
              ("둘레가 $x$ cm인 직사각형의 넓이 $y$ cm²", False), ("$x$살인 학생의 키 $y$ cm", False), ("1개에 500원인 사탕 $x$개의 값 $y$원", True)]


@kind(U, "함수의 뜻", 8)
def _():
    want = R.random() < 0.5
    ch, ans = pick(R.sample(FUNC_FACTS, len(FUNC_FACTS)), want)
    return P("보통", f"다음 중 $y$가 $x$의 함수{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint="x 하나에 y가 꼭 하나씩만 정해지면 함수예요. 두 개 이상 나오거나 정해지지 않으면 함수가 아니에요.",
             explanation="x = 4 같은 수를 넣어서 y가 하나로 정해지는지 확인해요.", steps=["x 하나에 y가 [[하나]]씩 대응하면 함수"])


@kind(U, "함숫값", 12)
def _():
    if R.random() < 0.5:
        k_ = nz(-24, 24)
        x1, x2 = nz(-6, 6), nz(-6, 6)
        need(x1 != x2 and k_ % x1 == 0 and k_ % x2 == 0)
        v = F(k_, x1) + F(k_, x2)
        ftx = f"\\frac{{{k_}}}{{x}}" if k_ > 0 else f"-\\frac{{{-k_}}}{{x}}"
    else:
        k_ = F(nz(-6, 6), R.choice([1, 2, 3]))
        x1, x2 = nz(-6, 6), nz(-6, 6)
        need(x1 != x2)
        v = k_ * x1 + k_ * x2
        ftx = fx(k_, 0)
        need(v.denominator == 1)
    c1, c2 = R.choice([(1, 1), (1, -1), (2, 1)])
    v = (F(k_, x1) * c1 + F(k_, x2) * c2) if "frac{" in ftx and "x}" in ftx else (k_ * x1 * c1 + k_ * x2 * c2)
    need(F(v).denominator <= 6)
    expr = f"{'2' if c1 == 2 else ''}f({x1}) {'+' if c2 > 0 else '-'} f({x2})"
    return P("쉬움", f"함수 $f(x) = {ftx}$에 대하여 ${expr}$의 값을 구하시오.", fpl(v),
             hint="x 자리에 그 수를 넣어 계산해요.", explanation=f"x = {x1}, {x2}를 차례로 넣어 계산하면 {fpl(v)}",
             steps=[f"{expr} = [[{fpl(v)}]]"])


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
                 steps=[f"a = [[{av}]]", f"f({x2}) = [[{fpl(v2)}]]"])
    av = nz(-6, 6)
    x1, x2 = nz(-5, 5), nz(-5, 5)
    need(x1 != x2)
    return P("보통", f"함수 $f(x) = ax$에 대하여 $f({x1}) = {av * x1}$일 때, $f({x2})$의 값을 구하시오.", av * x2,
             hint=f"먼저 f({x1}) = {av * x1} 에서 a를 구해요.", explanation=f"{x1}a = {av * x1} → a = {av}, f({x2}) = {av * x2}",
             steps=[f"a = [[{av}]]", f"f({x2}) = [[{av * x2}]]"])


LIN_POOL = [("$y = 3x - 1$", True), ("$y = \\frac{2}{x}$", False), ("$y = x^{2} + 1$", False), ("$y = 5$", False), ("$y = x(x - 2) - x^{2}$", True),
            ("$y = 2(x + 1) - 2x$", False), ("$y = -\\frac{x}{4}$", True), ("$y = \\frac{1}{x} + 1$", False), ("$xy = 6$", False),
            ("$y = 0.5x + 2$", True), ("$y - x = x + 3$", True), ("$y = x^{2} - x(x + 1)$", True), ("$y = 3 - x^{2}$", False)]


@kind(U, "일차함수의 뜻", 8)
def _():
    want = R.random() < 0.6
    ch, ans = pick(R.sample(LIN_POOL, len(LIN_POOL)), want)
    return P("보통", f"다음 중 $y$가 $x$에 대한 일차함수{'인' if want else '가 아닌'} 것은?", ans, choices=ch,
             hint="정리해서 y = ax + b (a ≠ 0) 꼴이면 일차함수예요. 괄호는 먼저 풀어 봐요.",
             explanation="괄호를 풀고 y = (x에 대한 일차식) 꼴인지 확인해요. x²이 지워지면 일차함수일 수 있어요.",
             steps=["정리해서 y = ax + b, [[a ≠ 0]]"])


@kind(U, "일차함수의 함숫값", 12)
def _():
    m, c = nz(-5, 5), R.randint(-9, 9)
    av = nz(-6, 6)
    target = m * av + c
    if R.random() < 0.5:
        return P("쉬움", f"일차함수 $f(x) = {fx(m, c)}$에 대하여 $f(a) = {target}$일 때, $a$의 값을 구하시오.", av,
                 hint="x 자리에 a를 넣고 방정식을 풀어요.", explanation=f"{m}a {sgn(c)} = {target} → a = {av}",
                 steps=[f"{m}a = [[{target - c}]]", f"a = [[{av}]]"])
    p_ = nz(-4, 4)
    q_ = R.randint(-9, 9)
    k = nz(-4, 4)
    # f(x) = px + q 에서 f(k) = t 일 때 p
    t_ = p_ * k + q_
    return P("쉬움", f"일차함수 $f(x) = ax {sgn(q_)}$에 대하여 $f({k}) = {t_}$일 때, 수 $a$의 값을 구하시오.", p_,
             hint="x 자리에 수를 넣으면 a에 대한 방정식이 돼요.", explanation=f"{k}a {sgn(q_)} = {t_} → a = {p_}",
             steps=[f"{k}a = [[{t_ - q_}]]", f"a = [[{p_}]]"])


@kind(U, "일차함수의 그래프의 평행이동", 12)
def _():
    m, c0, sh = nz(-5, 5), R.randint(-6, 6), nz(-7, 7)
    right = f"$y = {fx(m, c0 + sh)}$"
    wr = [f"$y = {fx(-m, c0 + sh)}$", f"$y = {fx(m, c0 - sh)}$", f"$y = {fx(m + sh, c0)}$", f"$y = {fx(m, -(c0 + sh))}$", f"$y = {fx(c0 + sh, m)}$"]
    ch, ans = mc(right, wr)
    return P("쉬움", f"일차함수 $y = {fx(m, c0)}$의 그래프를 $y$축의 방향으로 ${sh}$만큼 평행이동한 그래프의 식은?", ans, choices=ch,
             hint="y축 방향으로 k만큼 평행이동하면 상수항에 k를 더해요. 기울기는 그대로예요.",
             explanation=f"y = {fx(m, c0, tex=False)} {sgn(sh)} → y = {fx(m, c0 + sh, tex=False)}", steps=[f"상수항: {c0} + ({sh}) = [[{c0 + sh}]]"])


@kind(U, "평행이동한 그래프 위의 점", 12)
def _():
    m, c, sh, px = nz(-5, 5), R.randint(-6, 6), nz(-6, 6), nz(-4, 4)
    yy = m * px + c + sh
    if R.random() < 0.5:
        return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프를 $y$축의 방향으로 ${sh}$만큼 평행이동한 그래프가 점 $({px}, k)$를 지날 때, $k$의 값을 구하시오.", yy,
                 hint="먼저 평행이동한 식을 구한 뒤 x에 대입해요.", explanation=f"y = {fx(m, c + sh, tex=False)}, x = {px} → k = {yy}",
                 steps=[f"평행이동한 식: y = {fx(m, c + sh, tex=False)}", f"k = [[{yy}]]"])
    return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프를 $y$축의 방향으로 $p$만큼 평행이동하였더니 점 $({px}, {yy})$를 지났다. $p$의 값을 구하시오.", sh,
             hint="평행이동한 식 y = (기울기)x + (상수항 + p)에 점을 대입해요.", explanation=f"{yy} = {m}×({px}) {sgn(c)} + p → p = {sh}",
             steps=[f"p = [[{sh}]]"])


@kind(U, "x절편과 y절편", 12)
def _():
    m = F(nz(-6, 6), R.choice([1, 1, 2, 3, 4]))
    c = nz(-9, 9)
    xi = F(-c) / m
    need(xi.denominator <= 4)
    combo = R.choice([("p + q", xi + c), ("p - q", xi - c), ("pq", xi * c)])
    return P("쉬움", f"일차함수 $y = {fx(m, c)}$의 그래프의 $x$절편을 $p$, $y$절편을 $q$라 할 때, ${combo[0]}$의 값을 구하시오.", fpl(combo[1]),
             hint="x절편: y = 0일 때 x의 값, y절편: x = 0일 때 y의 값",
             explanation=f"p = {fpl(xi)}, q = {c} → {combo[0]} = {fpl(combo[1])}",
             steps=[f"x절편 p = [[{fpl(xi)}]]", f"y절편 q = [[{c}]]", f"{combo[0]} = [[{fpl(combo[1])}]]"])


@kind(U, "절편을 이용하여 미지수 구하기", 12)
def _():
    m = F(nz(-6, 6), R.choice([1, 2, 3]))
    xi = nz(-8, 8)
    k = -m * xi
    need(k.denominator == 1)
    if R.random() < 0.5:
        return P("보통", f"일차함수 $y = {fx(m, 0)} + k$의 그래프의 $x$절편이 ${xi}$일 때, 수 $k$의 값을 구하시오.", fpl(k),
                 hint=f"x절편이 {xi}이면 점 ({xi}, 0)을 지나요.", explanation=f"0 = {fpl(m)} × ({xi}) + k → k = {fpl(k)}",
                 steps=[f"0 = {fpl(m * xi)} + k", f"k = [[{fpl(k)}]]"])
    c = nz(-9, 9)
    m2 = F(-c, xi)
    need(m2.denominator <= 3 and m2 != 0)
    return P("보통", f"일차함수 $y = ax {sgn(c)}$의 그래프의 $x$절편이 ${xi}$일 때, 수 $a$의 값을 구하시오.", fpl(m2),
             hint=f"점 ({xi}, 0)을 대입해요.", explanation=f"0 = {xi}a {sgn(c)} → a = {fpl(m2)}", steps=[f"a = [[{fpl(m2)}]]"])


@kind(U, "일차함수의 그래프의 기울기", 12)
def _():
    m = F(nz(-6, 6), R.choice([1, 2, 3, 4]))
    if R.random() < 0.5:
        dx = R.choice([m.denominator * k for k in (1, 2, 3, 4)])
        dy = m * dx
        return P("쉬움", f"일차함수 $y = {fx(m, nz(-5, 5))}$의 그래프에서 $x$의 값이 {dx}만큼 증가할 때, $y$의 값의 증가량을 구하시오.", fpl(dy),
                 hint="(y의 값의 증가량) = (기울기) × (x의 값의 증가량)", explanation=f"{fpl(m)} × {dx} = {fpl(dy)}",
                 steps=[f"기울기 = [[{fpl(m)}]]", f"증가량 = [[{fpl(dy)}]]"])
    x1 = R.randint(-4, 3)
    x2 = x1 + R.randint(1, 6)
    dy = m * (x2 - x1)
    need(dy.denominator == 1)
    return P("쉬움", f"일차함수 $y = {fx(m, nz(-5, 5))}$의 그래프에서 $x$의 값이 ${x1}$에서 ${x2}$까지 증가할 때, $y$의 값의 증가량을 구하시오.", fpl(dy),
             hint="x의 값의 증가량을 먼저 구한 뒤 기울기를 곱해요.", explanation=f"x 증가량 {x2 - x1}, × {fpl(m)} = {fpl(dy)}",
             steps=[f"x 증가량 = [[{x2 - x1}]]", f"y 증가량 = [[{fpl(dy)}]]"])


@kind(U, "두 점을 지나는 직선의 기울기", 14)
def _():
    x1, x2 = nz(-6, 6), nz(-6, 6)
    need(x1 != x2)
    sl = F(nz(-5, 5), R.choice([1, 1, 2]))
    y2 = R.randint(-9, 9)
    y1 = y2 + sl * (x1 - x2)
    need(y1.denominator == 1)
    y1 = int(y1)
    mode = R.random()
    if mode < 0.4:
        return P("보통", f"두 점 $({x1}, {y1})$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 기울기를 구하시오.", fpl(sl),
                 hint="기울기 = (y의 값의 차) ÷ (x의 값의 차)", explanation=f"({y2} - ({y1})) ÷ ({x2} - ({x1})) = {fpl(sl)}",
                 steps=[f"y의 차: [[{y2 - y1}]]", f"x의 차: [[{x2 - x1}]]", f"기울기 = [[{fpl(sl)}]]"])
    return P("보통", f"두 점 $({x1}, k)$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 기울기가 ${ftex(sl)}$일 때, $k$의 값을 구하시오.", y1,
             hint="기울기 = (y의 값의 차) ÷ (x의 값의 차) 로 식을 세워요.", explanation=f"({y2} - k) ÷ ({x2} - ({x1})) = {fpl(sl)} → k = {y1}",
             steps=[f"{y2} - k = {fpl(sl)} × {x2 - x1}", f"k = [[{y1}]]"])


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
             steps=[f"x절편 = [[{fpl(xi)}]]", f"y절편 = [[{c}]]", f"넓이 = [[{fpl(S)}]]"])


@kind(U, "세 점이 한 직선 위에 있을 조건", 12)
def _():
    x1, x2, x3 = R.sample(range(-6, 7), 3)
    sl = F(nz(-4, 4), R.choice([1, 2]))
    y1 = R.randint(-6, 6)
    y2, y3 = y1 + sl * (x2 - x1), y1 + sl * (x3 - x1)
    need(y2.denominator == 1 and y3.denominator == 1)
    return P("어려움", f"세 점 $({x1}, {y1})$, $({x2}, {int(y2)})$, $({x3}, k)$가 한 직선 위에 있을 때, $k$의 값을 구하시오.", int(y3),
             hint="어느 두 점을 골라도 기울기가 같아요.", explanation=f"기울기 {fpl(sl)} → k = {y1} + ({fpl(sl)}) × ({x3} - ({x1})) = {int(y3)}",
             steps=[f"기울기 = [[{fpl(sl)}]]", f"k = [[{int(y3)}]]"])


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
    st = [(f"{'오른쪽 위' if m > 0 else '오른쪽 아래'}로 향하는 직선이다.", True), (f"{'오른쪽 아래' if m > 0 else '오른쪽 위'}로 향하는 직선이다.", False),
          (f"$y$절편은 ${c}$이다.", True), (f"$y$절편은 ${-c}$이다.", False), (f"$x$절편은 ${ftex(xi)}$이다.", True), (f"$x$절편은 ${ftex(-xi)}$이다.", -xi == xi),
          (f"$y = {fx(m, 0)}$의 그래프와 평행하다.", True), (f"$y = {fx(-m, c)}$의 그래프와 평행하다.", False),
          (f"제{miss}사분면을 지나지 않는다.", True), (f"제{miss}사분면을 지난다.", False),
          (f"$x$의 값이 {dx}만큼 증가할 때 $y$의 값은 {fpl(abs(m * dx))}만큼 {'증가' if m > 0 else '감소'}한다.", True),
          (f"$x$의 값이 {dx}만큼 증가할 때 $y$의 값은 {fpl(abs(m * dx))}만큼 {'감소' if m > 0 else '증가'}한다.", False),
          (f"점 $({px}, {fpl(py)})$를 지난다.".replace("{", "{"), True), (f"점 $({px}, {fpl(py + 1)})$를 지난다.", False)]
    want = R.random() < 0.5
    ch, ans = pick(st, want)
    return P("보통", f"일차함수 $y = {fx(m, c)}$의 그래프에 대한 설명으로 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
             hint="기울기가 양수면 오른쪽 위, 음수면 오른쪽 아래. y절편으로 y축과 만나는 곳을 봐요.",
             explanation=f"기울기 {fpl(m)}, y절편 {c}, x절편 {fpl(xi)} → 제{', '.join(map(str, sorted(quads)))}사분면을 지나요.",
             steps=[f"기울기 = [[{fpl(m)}]]", f"y절편 = [[{c}]]"])


@kind(U, "y = ax + b의 그래프와 a, b의 부호", 8)
def _():
    sa, sb = R.choice([1, -1]), R.choice([1, -1])
    quads = ({1, 3} if sa > 0 else {2, 4}) | ({2} if (sa > 0 and sb > 0) else {4} if (sa > 0 and sb < 0) else {1} if (sa < 0 and sb > 0) else {3})
    def s(v):
        return "> 0" if v > 0 else "< 0"
    right = f"$a {s(sa)}$, $b {s(sb)}$"
    wr = [f"$a {s(p_)}$, $b {s(q_)}$" for p_ in (1, -1) for q_ in (1, -1) if (p_, q_) != (sa, sb)] + ["$a = 0$, $b > 0$", "$a = 0$, $b < 0$"]
    ch, ans = mc(right, wr)
    if R.random() < 0.5:
        q = f"일차함수 $y = ax + b$의 그래프가 제{', '.join(map(str, sorted(quads)))}사분면을 지날 때, $a$, $b$의 부호는?"
    else:
        q = f"일차함수 $y = ax + b$의 그래프가 {'오른쪽 위' if sa > 0 else '오른쪽 아래'}로 향하고, $y$축과 원점보다 {'위' if sb > 0 else '아래'}쪽에서 만날 때, $a$, $b$의 부호는?"
    return P("어려움", q, ans, choices=ch, hint="오른쪽 위로 향하면 a > 0, y절편이 원점보다 위면 b > 0",
             explanation=f"기울기 a {s(sa)}, y절편 b {s(sb)}", steps=[f"a [[{s(sa)}]]", f"b [[{s(sb)}]]"])


@kind(U, "일차함수의 그래프의 평행과 일치", 12)
def _():
    k = nz(-4, 4, (0,))
    m = nz(-8, 8)
    need(m % k == 0)
    if R.random() < 0.5:
        c1, c2 = R.randint(-6, 6), R.randint(-6, 6)
        need(c1 != c2)
        return P("보통", f"두 일차함수 $y = {k if k not in (1, -1) else ('-' if k < 0 else '')}ax {sgn(c1)}$, $y = {fx(m, c2)}$의 그래프가 평행할 때, 수 $a$의 값을 구하시오.", m // k,
                 hint="두 직선이 평행하면 기울기가 같고 y절편은 달라요.", explanation=f"{k}a = {m} → a = {m // k}",
                 steps=[f"{k}a = [[{m}]]", f"a = [[{m // k}]]"])
    return _coincide(nz(-6, 6), nz(-9, 9))


def _coincide(av, bv):
    c2 = R.randint(-9, 9)
    # y = a x + (bv)  와  y = av x + (b + c2) 가 일치 -> a = av, bv = b + c2 -> b = bv - c2
    bval = bv - c2
    combo = R.choice([("a + b", av + bval), ("ab", av * bval), ("a - b", av - bval)])
    return P("보통", f"두 일차함수 $y = ax {sgn(bv)}$와 $y = {fx(av, 0)} + b {sgn(c2)}$의 그래프가 일치할 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.".replace(" + 0$", "$"),
             combo[1], hint="일치하면 기울기도 같고 y절편도 같아요.",
             explanation=f"a = {av}, b {sgn(c2)} = {bv} → b = {bval} → {combo[0]} = {combo[1]}",
             steps=[f"a = [[{av}]]", f"b = [[{bval}]]", f"{combo[0]} = [[{combo[1]}]]"])


def line_mc(m, c, q, hint, ex, level="보통"):
    right = f"$y = {fx(m, c)}$"
    wr = [f"$y = {fx(-m, c)}$", f"$y = {fx(m, -c)}$" if c else f"$y = {fx(m, 2)}$", f"$y = {fx(m, c + 1)}$", f"$y = {fx(m, c - 1)}$",
          f"$y = {fx(m * 2, c)}$", f"$y = {fx(c, m)}$" if c and c != m else f"$y = {fx(m, c + 3)}$", f"$y = {fx(-m, -c)}$"]
    ch, ans = mc(right, wr)
    return P(level, q, ans, choices=ch, hint=hint, explanation=ex, steps=[f"기울기 = [[{fpl(m)}]]", f"y절편 = [[{fpl(c)}]]"])


@kind(U, "일차함수의 식 구하기: 기울기와 y절편", 12)
def _():
    dx = R.randint(1, 4)
    m = F(nz(-4, 4), R.choice([1, 1, 2]))
    dy = m * dx
    need(dy.denominator == 1 and dy != 0)
    c = nz(-7, 7)
    if R.random() < 0.5:
        q = f"$x$의 값이 {dx}만큼 증가할 때 $y$의 값은 {abs(int(dy))}만큼 {'증가' if dy > 0 else '감소'}하고, $y$절편이 ${c}$인 직선을 그래프로 하는 일차함수의 식은?"
    else:
        m0 = R.choice([1, 2, -1, 3])
        q = f"일차함수 $y = {fx(m, m0)}$의 그래프와 평행하고, $y$절편이 ${c}$인 직선을 그래프로 하는 일차함수의 식은?"
        need(m0 != c)
    return line_mc(m, c, q, "기울기 = (y의 증가량) ÷ (x의 증가량), 평행하면 기울기가 같아요.", f"기울기 {fpl(m)}, y절편 {c} → y = {fx(m, c, tex=False)}")


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
    return line_mc(m, c, q, "기울기를 먼저 구하고, y = ax + b에 점을 대입해 b를 구해요.", f"기울기 {fpl(m)}, {py} = {fpl(m)} × ({px}) + b → b = {fpl(c)}")


@kind(U, "일차함수의 식 구하기: 두 점", 12)
def _():
    x1, x2 = nz(-5, 5), nz(-5, 5)
    need(x1 != x2)
    m = F(nz(-4, 4), R.choice([1, 1, 2]))
    y1 = R.randint(-6, 6)
    y2 = y1 + m * (x2 - x1)
    c = y1 - m * x1
    need(y2.denominator == 1 and c.denominator == 1)
    return line_mc(m, c, f"두 점 $({x1}, {y1})$, $({x2}, {int(y2)})$를 지나는 일차함수의 그래프의 식은?",
                   "두 점으로 기울기를 구한 뒤 한 점을 대입해 y절편을 구해요.", f"기울기 {fpl(m)}, {y1} = {fpl(m)} × ({x1}) + b → b = {fpl(c)}")


@kind(U, "일차함수의 식 구하기: x절편과 y절편", 12)
def _():
    xi, yi = nz(-6, 6), nz(-6, 6)
    m = F(-yi, xi)
    return line_mc(m, yi, f"$x$절편이 ${xi}$, $y$절편이 ${yi}$인 직선을 그래프로 하는 일차함수의 식은?",
                   f"두 점 ({xi}, 0), (0, {yi})를 지나요. 기울기 = -(y절편) ÷ (x절편)", f"기울기 ({yi} - 0) ÷ (0 - ({xi})) = {fpl(m)}, y절편 {yi}")


@kind(U, "일차함수의 활용: 온도, 높이, 길이", 12)
def _():
    mode = R.randint(0, 2)
    if mode == 0:
        h0, v = R.choice([90, 120, 150, 180, 200, 240]), R.choice([2, 3, 4, 5, 6])
        target = R.randint(1, h0 // v - 1) * v
        tt = (h0 - target) // v
        return P("보통", f"지면에서 {h0} m 높이에 떠 있던 열기구가 1초에 {v} m씩 일정하게 내려온다. 내려오기 시작한 지 몇 초 후에 지면에서 높이가 {target} m가 되는지 구하시오.", tt,
                 hint=f"x초 후 높이를 y m라 하면 y = {h0} - {v}x", explanation=f"{target} = {h0} - {v}x → x = {tt}", steps=[f"y = {h0} - [[{v}]]x", f"x = [[{tt}]]"])
    if mode == 1:
        t0, dm, dt = R.choice([80, 90, 70, 60]), R.choice([2, 3, 4, 5]), R.choice([3, 4, 5, 6])
        mins = R.randint(2, 6) * dm
        tv = t0 - F(dt, dm) * mins
        need(tv > 20)
        return P("보통", f"{t0} °C의 물을 실온에 두면 {dm}분마다 {dt} °C씩 일정하게 온도가 내려간다. {mins}분 후의 물의 온도는 몇 °C인지 구하시오.", fpl(tv),
                 hint=f"1분에 {fpl(F(dt, dm))} °C씩 내려가요.", explanation=f"y = {t0} - {fpl(F(dt, dm))}x, x = {mins} → y = {fpl(tv)}",
                 steps=[f"1분에 [[{fpl(F(dt, dm))}]] °C", f"y = [[{fpl(tv)}]]"])
    L0, burn = R.choice([20, 24, 30, 36]), F(R.randint(1, 4), R.choice([1, 2, 5]))
    mins = R.randint(2, 12) * burn.denominator
    rem = L0 - burn * mins
    need(rem > 0)
    return P("보통", f"길이가 {L0} cm인 양초에 불을 붙이면 1분에 {fpl(burn)} cm씩 일정하게 짧아진다. 불을 붙인 지 {mins}분 후 양초의 길이는 몇 cm인지 구하시오.".replace("1/", "1/"), fpl(rem),
             hint=f"x분 후 길이 y = {L0} - {fpl(burn)}x", explanation=f"y = {L0} - {fpl(burn)} × {mins} = {fpl(rem)}", steps=[f"y = [[{fpl(rem)}]]"])


@kind(U, "일차함수의 활용: 물의 양", 12)
def _():
    w0 = R.choice([20, 30, 40, 50, 80, 100])
    dm, dv = R.choice([(2, 3), (3, 5), (4, 6), (5, 8), (1, 2), (3, 4)])
    fill = R.random() < 0.5
    mins = R.randint(2, 8) * dm
    v = w0 + (1 if fill else -1) * F(dv, dm) * mins
    need(v > 0)
    return P("보통", f"물이 {w0} L 들어 있는 수조{'에' if fill else '에서'} {dm}분에 {dv} L씩 일정하게 물을 {'넣는다' if fill else '빼낸다'}. {mins}분 후 수조에 들어 있는 물의 양은 몇 L인지 구하시오.", fpl(v),
             hint=f"1분에 {fpl(F(dv, dm))} L씩 {'늘어나요' if fill else '줄어들어요'}.",
             explanation=f"y = {w0} {'+' if fill else '-'} {fpl(F(dv, dm))}x, x = {mins} → y = {fpl(v)}", steps=[f"1분에 [[{fpl(F(dv, dm))}]] L", f"y = [[{fpl(v)}]]"])


@kind(U, "일차함수의 활용: 거리, 속력, 시간", 12)
def _():
    D, v = R.choice([(240, 80), (300, 60), (180, 60), (270, 90), (200, 50), (150, 60), (360, 80)])
    tt = F(R.randint(1, 2 * D // v - 1), 2)
    rem = D - v * tt
    need(rem > 0)
    h, mi = int(tt), int((tt - int(tt)) * 60)
    tstr = (f"{h}시간" if h else "") + (f" {mi}분" if mi else "")
    place = R.choice(["할머니 댁", "캠핑장", "바닷가", "수목원"])
    return P("보통", f"{D} km 떨어진 {place}까지 시속 {v} km로 갈 때, 출발한 지 {tstr.strip()} 후 {place}까지 남은 거리는 몇 km인지 구하시오.", fpl(rem),
             hint=f"x시간 후 남은 거리 y = {D} - {v}x", explanation=f"{tstr.strip()} = {fpl(tt)}시간, y = {D} - {v} × {fpl(tt)} = {fpl(rem)}",
             steps=[f"시간: [[{fpl(tt)}]]시간", f"y = [[{fpl(rem)}]]"])


@kind(U, "일차함수의 활용: 도형", 10)
def _():
    W, H = R.randint(6, 16), R.randint(4, 12)
    sp_ = R.choice([1, 2])
    S = R.randint(4, H * W // 2 - 1)
    tt = F(2 * S, H * sp_)
    need(0 < tt * sp_ < W and tt.denominator <= 2)
    return P("어려움", f"가로 {W} cm, 세로 {H} cm인 직사각형 ABCD에서 점 P가 꼭짓점 B를 출발하여 변 BC 위를 C 쪽으로 1초에 {sp_} cm씩 움직인다. 삼각형 ABP의 넓이가 {S} cm²가 되는 것은 출발한 지 몇 초 후인지 구하시오. (단, AB = {H} cm, BC = {W} cm)",
             fpl(tt), hint=f"x초 후 BP = {sp_}x cm, 삼각형 ABP의 넓이 y = {H} × {sp_}x ÷ 2",
             explanation=f"y = {fpl(F(H * sp_, 2))}x = {S} → x = {fpl(tt)}", steps=[f"y = [[{fpl(F(H * sp_, 2))}]]x", f"x = [[{fpl(tt)}]]"])


# ═════════════════════ 7. 일차함수와 일차방정식의 관계 ═════════════════════
U = "7. 일차함수와 일차방정식"


@kind(U, "일차함수와 일차방정식의 관계", 12)
def _():
    A1, B1, C1 = nz(-5, 5), nz(-5, 5), R.randint(-9, 9)
    m, c = F(-A1, B1), F(-C1, B1)
    right = f"$y = {fx(m, c)}$"
    wr = [f"$y = {fx(-m, c)}$", f"$y = {fx(m, -c)}$", f"$y = {fx(-m, -c)}$", f"$y = {fx(F(B1, A1) if True else 0, c)}$" if F(B1, A1) != m else f"$y = {fx(m, c + 1)}$",
          f"$y = {fx(A1, C1)}$", f"$y = {fx(m, c + 2)}$"]
    ch, ans = mc(right, wr)
    return P("쉬움", f"일차방정식 ${lin(A1, B1, C1)} = 0$의 그래프와 같은 직선을 그래프로 하는 일차함수는?", ans, choices=ch,
             hint="y에 대하여 풀어요: y = ... 꼴로", explanation=f"{B1}y = {lin(-A1, 0, -C1, tex=False)} → y = {fx(m, c, tex=False)}",
             steps=[f"기울기 = [[{fpl(m)}]]", f"y절편 = [[{fpl(c)}]]"])


@kind(U, "일차방정식의 그래프 위의 점", 12)
def _():
    A1, B1 = nz(-5, 5), nz(-5, 5)
    px, py = nz(-5, 5), nz(-5, 5)
    C1 = -(A1 * px + B1 * py)
    if R.random() < 0.5:
        return P("쉬움", f"점 $({px}, k)$가 일차방정식 ${lin(A1, B1, C1)} = 0$의 그래프 위에 있을 때, $k$의 값을 구하시오.", py,
                 hint="점의 좌표를 대입해요.", explanation=f"({A1})×({px}) + ({B1})k {sgn(C1)} = 0 → k = {py}", steps=[f"k = [[{py}]]"])
    return P("보통", f"일차방정식 $ax {sgn(B1)}y {sgn(C1)} = 0$의 그래프가 점 $({px}, {py})$를 지날 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y").replace(" + 0 =", " ="),
             A1, hint="점의 좌표를 대입해 a에 대한 방정식을 풀어요.", explanation=f"({px})a + ({B1})×({py}) {sgn(C1)} = 0 → a = {A1}", steps=[f"a = [[{A1}]]"])


@kind(U, "직선의 방정식 구하기", 12)
def _():
    A1, B1 = nz(-5, 5), R.choice([1, 1, 2, -1])
    m = F(-A1, B1)
    xi = nz(-5, 5)
    c = -m * xi
    need(c.denominator == 1)
    # 정답을 ax + by + c = 0 꼴(정수)로: B1 y = -A1 x + B1 c  -> A1 x + B1 y - B1 c = 0
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
             steps=[f"기울기 = [[{fpl(m)}]]", f"y절편 = [[{fpl(c)}]]"])


@kind(U, "좌표축에 평행한 직선", 14)
def _():
    mode = R.random()
    if mode < 0.4:
        p1, p2 = (nz(-6, 6), nz(-6, 6)), (nz(-6, 6), nz(-6, 6))
        d1 = R.choice(["x축에 수직인", "y축에 평행한"])
        d2 = R.choice(["y축에 수직인", "x축에 평행한"])
        ans_ = p1[0] + p2[1]
        return P("쉬움", f"점 $({p1[0]}, {p1[1]})$을 지나고 ${d1[0]}$축{d1[2:]} 직선과 점 $({p2[0]}, {p2[1]})$을 지나고 ${d2[0]}$축{d2[2:]} 직선의 교점의 좌표를 $(p, q)$라 할 때, $p + q$의 값을 구하시오.", ans_,
                 hint="x축에 수직(y축에 평행)인 직선은 x = (수), y축에 수직(x축에 평행)인 직선은 y = (수)",
                 explanation=f"x = {p1[0]}, y = {p2[1]} → 교점 ({p1[0]}, {p2[1]}) → p + q = {ans_}",
                 steps=[f"x = [[{p1[0]}]]", f"y = [[{p2[1]}]]", f"p + q = [[{ans_}]]"])
    k1, k2 = nz(-4, 4, (0, 1)), nz(-4, 4)
    need(k1 != k2)
    c1, c2 = R.randint(-8, 8), R.randint(-8, 8)
    av = F(c2 - c1, k1 - k2)
    need(av.denominator == 1)
    ya, yb = R.randint(-6, 6), R.randint(-6, 6)
    need(ya != yb)
    axis = R.choice(["$y$축에 평행할", "$x$축에 수직일"])
    return P("보통", f"두 점 $({lin(k1, 0, c1, 'a')}, {ya})$, $({lin(k2, 0, c2, 'a')}, {yb})$를 지나는 직선이 {axis} 때, $a$의 값을 구하시오.",
             int(av), hint="y축에 평행(x축에 수직)한 직선 위의 점은 x좌표가 모두 같아요.",
             explanation=f"{lin(k1, 0, c1, 'a', tex=False)} = {lin(k2, 0, c2, 'a', tex=False)} → a = {int(av)}", steps=[f"a = [[{int(av)}]]"])


@kind(U, "ax + by + c = 0의 그래프와 부호", 8)
def _():
    sa, sb = R.choice([1, -1]), R.choice([1, -1])
    def s(v):
        return "> 0" if v > 0 else "< 0"
    # ax + by - 1 = 0: x절편 1/a, y절편 1/b
    right = f"$a {s(sa)}$, $b {s(sb)}$"
    wr = [f"$a {s(p_)}$, $b {s(q_)}$" for p_ in (1, -1) for q_ in (1, -1) if (p_, q_) != (sa, sb)] + ["$a = 0$, $b > 0$", "$a > 0$, $b = 0$"]
    ch, ans = mc(right, wr)
    return P("어려움", f"일차방정식 $ax + by - 1 = 0$의 그래프의 $x$절편이 {'양수' if sa > 0 else '음수'}, $y$절편이 {'양수' if sb > 0 else '음수'}일 때, $a$, $b$의 부호는?", ans, choices=ch,
             hint="x절편 = 1/a, y절편 = 1/b", explanation=f"1/a {s(sa)}, 1/b {s(sb)} → a {s(sa)}, b {s(sb)}", steps=["x절편 = [[1/a]]", "y절편 = [[1/b]]"])


@kind(U, "연립방정식의 해와 그래프의 교점", 14)
def _():
    sx, sy = nz(-5, 5), nz(-5, 5)
    a1, b1, a2, b2 = (nz(-4, 4) for _ in range(4))
    need(a1 * b2 - a2 * b1 != 0)
    c1, c2 = -(a1 * sx + b1 * sy), -(a2 * sx + b2 * sy)
    combo = R.choice([("p + q", sx + sy), ("pq", sx * sy), ("p - q", sx - sy)])
    if R.random() < 0.7:
        return P("보통", f"두 일차방정식 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$의 그래프의 교점의 좌표를 $(p, q)$라 할 때, ${combo[0]}$의 값을 구하시오.", combo[1],
                 hint="두 그래프의 교점 = 연립방정식의 해", explanation=f"연립해서 풀면 x = {sx}, y = {sy}",
                 steps=[f"p = [[{sx}]]", f"q = [[{sy}]]", f"{combo[0]} = [[{combo[1]}]]"])
    av = nz(-5, 5)
    c2b = -(av * sx + b2 * sy)
    return P("어려움", f"두 일차방정식 ${lin(a1, b1, c1)} = 0$, $ax {sgn(b2)}y {sgn(c2b)} = 0$의 그래프의 교점의 $x$좌표가 ${sx}$일 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y").replace(" + 0 =", " ="),
             av, hint="a가 없는 식에 x좌표를 넣어 y좌표를 먼저 구해요.", explanation=f"교점 ({sx}, {sy}) → ({sx})a + ({b2})×({sy}) {sgn(c2b)} = 0 → a = {av}",
             steps=[f"교점의 y좌표 = [[{sy}]]", f"a = [[{av}]]"])


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
    else:
        c0 = R.randint(-6, 6)
        need(c0 != c)
        q = f"두 직선 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$의 교점을 지나고, 직선 $y = {fx(m, c0)}$와 평행한 직선의 방정식은?"
    return line_mc(F(m), F(c), q, "먼저 연립해서 교점을 구한 뒤 y = (기울기)x + b에 대입해요.", f"교점 ({sx}, {sy}) → b = {c}", level="어려움")


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
             steps=[f"밑변 = [[{abs(c1 - c2)}]]", f"높이 = [[{abs(sx)}]]", f"넓이 = [[{fpl(S)}]]"])


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
             explanation=f"교점 ({sx}, {sy}) → ({sx})a + ({b3})×({sy}) {sgn(c3)} = 0 → a = {int(av)}", steps=[f"교점: ([[{sx}]], [[{sy}]])", f"a = [[{int(av)}]]"])


@kind(U, "연립방정식의 해의 개수와 그래프", 14)
def _():
    p_, q_, r_ = nz(-4, 4), nz(-4, 4), nz(-6, 6)
    k = nz(-3, 3, (0, 1))
    if R.random() < 0.5:  # 해가 무수히 많다: 둘째 식 = 첫째 식 × k
        av = q_
        bval = r_ * k
        # 첫째: p x + a y = r (a = q_), 둘째: (kp) x + (kq) y = b
        combo = R.choice([("a + b", av + bval), ("ab", av * bval), ("a - b", av - bval)])
        return P("어려움", f"연립방정식 $\\begin{{cases}} {lin(p_, 0, 0)} + ay = {r_} \\\\ {lin(k * p_, k * q_, 0)} = b \\end{{cases}}$의 해가 무수히 많을 때, 수 $a$, $b$에 대하여 ${combo[0]}$의 값을 구하시오.",
                     combo[1], hint=f"해가 무수히 많으면 두 직선이 일치해요: 첫 식에 {k}를 곱하면 둘째 식과 같아야 해요.",
                     explanation=f"첫 식 × {k}: {lin(k * p_, 0, 0, tex=False)} + {k}ay = {k * r_} → {k}a = {k * q_}, b = {k * r_} → a = {av}, b = {bval}",
                     steps=[f"a = [[{av}]]", f"b = [[{bval}]]", f"{combo[0]} = [[{combo[1]}]]"])
    av = k * p_
    c2 = R.randint(-9, 9)
    need(c2 != k * r_)
    return P("어려움", f"연립방정식 $\\begin{{cases}} {lin(p_, q_, 0)} = {r_} \\\\ ax {sgn(k * q_)}y = {c2} \\end{{cases}}$의 해가 없을 때, 수 $a$의 값을 구하시오.".replace("+ 1y", "+ y").replace("- 1y", "- y"),
             av, hint="해가 없으면 두 직선이 평행해요: x, y의 계수의 비는 같고 상수항의 비는 달라요.",
             explanation=f"{p_} : {q_} = a : {k * q_} → a = {av} (상수항 {r_} : {c2}는 비가 달라 평행)",
             steps=[f"a = [[{av}]]"])


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
