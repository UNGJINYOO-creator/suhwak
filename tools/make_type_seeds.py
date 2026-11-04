"""유형 씨앗(중2-1 수학) 만들기: app/src/main/assets/seeds_types.json

중2-1 개념 문제집의 '필수 유형' 순서(7단원)를 따라, 유형마다 숫자를 새로 정해 문제를 만든다.
책의 문제나 글을 옮기지 않는다: 유형(무엇을 묻는가)만 따르고 수·식·상황은 모두 새로 정한다.
정답은 파이썬(분수 계산·sympy)이 계산하고, 객관식은 보기마다 참/거짓을 다시 확인한다 (AI가 정답을 쓰지 않음).
형식은 마클리 문제 은행(/api/bank/export)과 같아서 앱의 SeedImport가 그대로 읽는다.

    pip install sympy
    python3 tools/make_type_seeds.py
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
MARKS = "①②③④⑤"
rng = random.Random(20261104)
units: dict[str, list[dict]] = {}
count = [0]
x, y, a, b = sp.symbols("x y a b")

SUP = str.maketrans("0123456789-", "⁰¹²³⁴⁵⁶⁷⁸⁹⁻")


def add(unit, concept, level, question, answer, *, choices=None, hint="", explanation="", steps=None):
    count[0] += 1
    units.setdefault(unit, []).append({
        "bank_id": f"type21_{count[0]:03d}",
        "concept": concept,
        "type": "multiple_choice" if choices else "short_answer",
        "level": level,
        "question": question,
        "choices": choices or [],
        "answer": str(answer),
        "hint": hint,
        "explanation": explanation,
        "steps": steps or [],
    })


def mc(correct: str, wrong: list[str]) -> tuple[list[str], str]:
    """보기 5개(정답 1 + 오답 4)를 섞고 정답 원문자를 돌려준다."""
    wrong = [w for w in dict.fromkeys(wrong) if w != correct][:4]
    assert len(wrong) == 4, (correct, wrong)
    opts = [correct] + wrong
    rng.shuffle(opts)
    return opts, MARKS[opts.index(correct)]


def pick(truth: list[tuple[str, bool]], want: bool) -> tuple[list[str], str]:
    """'옳은 것은?'(want=True) / '옳지 않은 것은?'(want=False): 원하는 쪽이 정확히 하나인지 확인하고 섞는다."""
    hit = [s for s, t in truth if t == want]
    miss = [s for s, t in truth if t != want]
    assert len(hit) == 1 and len(miss) == 4, truth
    return mc(hit[0], miss)


def nz(lo, hi, *, skip=(0,)):
    return rng.choice([k for k in range(lo, hi + 1) if k not in skip])


# ───────── 식 쓰기 (교과서 꼴: 3x²y, -\frac{1}{2}a, x² + 2x - 1) ─────────

def term(c, mono, tex=True):
    """c: 계수(Fraction), mono: [(문자, 지수)] -> 부호 없는 절댓값 항과 부호."""
    letters = "".join(
        (f"{v}^{{{e}}}" if tex else f"{v}{str(e).translate(SUP)}") if e != 1 else v for v, e in mono if e
    )
    c = F(c)
    m = abs(c)
    if m.denominator != 1:
        coef = f"\\frac{{{m.numerator}}}{{{m.denominator}}}" if tex else f"{m.numerator}/{m.denominator}"
        if not tex and letters:
            coef = f"({m.numerator}/{m.denominator})"
        body = coef + letters
    else:
        body = ("" if m == 1 and letters else str(m.numerator)) + letters
    return ("-" if c < 0 else "+"), body


def fmt(expr, gens, tex=True):
    """sympy 식 -> 교과서 꼴 문자열 (높은 차수부터)."""
    expr = sp.expand(sp.sympify(expr))
    if expr == 0:
        return "0"
    poly = sp.Poly(expr, *gens)
    out = ""
    for monom, coef in poly.terms(order="grlex"):
        sign, body = term(F(int(sp.fraction(coef)[0]), int(sp.fraction(coef)[1])), list(zip([str(g) for g in gens], monom)), tex)
        if not out:
            out = ("-" if sign == "-" else "") + body
        else:
            out += f" {sign} {body}"
    return out


def mono_tex(expr, gens, tex=True):
    """단항식(분모에 문자가 올 수도 있음) -> 문자열. 예: 3x^2/y -> \\frac{3x^{2}}{y}"""
    expr = sp.simplify(expr)
    num, den = sp.fraction(sp.together(expr))
    if den.free_symbols:
        sgn = -1 if sp.Poly(num, *gens).LC() < 0 else 1
        n = fmt(sgn * num, gens, tex)
        d = fmt(den, gens, tex)
        return ("-" if sgn < 0 else "") + (f"\\frac{{{n}}}{{{d}}}" if tex else f"{n}/{d}")
    return fmt(expr, gens, tex)


def t(expr, gens=(x, y)):
    return fmt(expr, gens)


def p(expr, gens=(x, y)):
    return fmt(expr, gens, tex=False)


def ftex(f: F) -> str:
    f = F(f)
    if f.denominator == 1:
        return str(f.numerator)
    return ("-" if f < 0 else "") + f"\\frac{{{abs(f.numerator)}}}{{{f.denominator}}}"


def fpl(f: F) -> str:
    f = F(f)
    return str(f.numerator) if f.denominator == 1 else f"{f.numerator}/{f.denominator}"


def lin(cx, cy=0, c=0, vx="x", vy="y"):
    """ax + by + c 를 문자열로 (정수 계수)."""
    gens = sp.symbols(f"{vx} {vy}")
    return fmt(cx * gens[0] + cy * gens[1] + c, gens)


def fx(m, k, v="x"):
    """y = mx + k 의 오른쪽 (m, k는 Fraction)."""
    return fmt(sp.Rational(F(m).numerator, F(m).denominator) * sp.Symbol(v) + sp.Rational(F(k).numerator, F(k).denominator), (sp.Symbol(v),))


# ───────── 소수 ─────────

def only25(n: int) -> bool:
    for q in (2, 5):
        while n % q == 0:
            n //= q
    return n == 1


def rest25(n: int) -> int:
    for q in (2, 5):
        while n % q == 0:
            n //= q
    return n


def expand(fr: F):
    """양의 분수 -> (정수 부분, 순환하지 않는 부분, 순환마디)."""
    ip, r, den = fr.numerator // fr.denominator, fr.numerator % fr.denominator, fr.denominator
    digits, seen = [], {}
    while r and r not in seen:
        seen[r] = len(digits)
        r *= 10
        digits.append(str(r // den))
        r %= den
    if not r:
        return ip, "".join(digits), ""
    s = seen[r]
    return ip, "".join(digits[:s]), "".join(digits[s:])


def dval(ip: int, pre: str, rep: str) -> F:
    if not rep:
        return ip + (F(int(pre), 10 ** len(pre)) if pre else 0)
    whole = int(pre + rep)
    cut = int(pre) if pre else 0
    return ip + F(whole - cut, (10 ** len(rep) - 1) * 10 ** len(pre))


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


# ═════════════════════ 1. 유리수와 순환소수 ═════════════════════
U = "1. 유리수와 순환소수"

for want in (True, False):  # 유한소수와 무한소수 구분하기
    opts = []
    while len(opts) < 5:
        d = rng.choice([6, 8, 9, 12, 14, 15, 16, 18, 20, 24, 25, 28, 30, 35, 40, 45, 48, 50, 56, 60, 75, 80])
        n = rng.randint(1, d - 1)
        if math.gcd(n, d) != 1 and rng.random() < 0.6:
            pass
        opts.append((f"$\\frac{{{n}}}{{{d}}}$", only25(F(n, d).denominator)))
        opts = list({o[0]: o for o in opts}.values())
        if sum(1 for o in opts if o[1] == want) > 1:
            opts = [o for o in opts if o[1] != want] + [next(o for o in opts if o[1] == want)]
    if sum(1 for o in opts if o[1] == want) != 1:
        continue
    ch, ans = pick(opts, want)
    add(U, "유한소수와 무한소수 구분", "쉬움", f"다음 분수를 소수로 나타낼 때, {'유한소수' if want else '무한소수'}인 것은?", ans, choices=ch,
        hint="기약분수로 만든 뒤 분모의 소인수가 2와 5뿐이면 유한소수예요.",
        explanation="각 분수를 기약분수로 고치고 분모를 소인수분해해서, 2와 5 말고 다른 소인수가 있는지 봐요.",
        steps=["먼저 [[기약분수]]로 고친다", "분모의 소인수가 2와 5뿐이면 [[유한소수]]"])

for want in (True, False):  # 순환소수 표현하기 (1): 점 찍는 자리
    truth = []
    used = set()
    while len(truth) < 5:
        d = rng.choice([3, 6, 7, 9, 11, 12, 22, 27, 33, 37, 41, 44, 55, 74, 90, 99, 111, 135, 330, 333])
        n = rng.randint(1, 4 * d)
        fr = F(n, d)
        ip, pre, rep = expand(fr)
        if not rep or fr in used:
            continue
        used.add(fr)
        right = rng.random() < (0.35 if want else 0.75)
        if right:
            s = dtex(ip, pre, rep)
        else:
            cands = []
            digits = pre + rep * 3
            for pl in range(0, 3):
                for rl in range(1, 4):
                    if pl + rl <= len(digits):
                        cand = (digits[:pl], digits[pl:pl + rl])
                        if dval(ip, *cand) != fr:
                            cands.append(cand)
            pre2, rep2 = rng.choice(cands)
            s = dtex(ip, pre2, rep2)
        long_ = dplain(ip, pre, rep, 9).replace("…", r"\cdots")
        truth.append((f"${long_} = {s}$", right))
    if sum(1 for _, r in truth if r == want) != 1:
        # 원하는 쪽이 하나가 되도록 다시 고른다
        k = [i for i, (_, r) in enumerate(truth) if r == want]
        if not k:
            continue
        keep = k[0]
        truth = [truth[keep]] + [tr for i, tr in enumerate(truth) if tr[1] != want]
        if len(truth) != 5:
            continue
    ch, ans = pick(truth, want)
    add(U, "순환소수 표현하기", "보통", f"다음 중 순환소수의 표현이 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
        hint="되풀이되는 숫자 묶음(순환마디)의 처음과 끝 숫자 위에만 점을 찍어요.",
        explanation="소수점 아래에서 처음으로 되풀이가 시작되는 곳부터 한 묶음을 찾아, 그 처음과 끝에 점을 찍어요.",
        steps=["되풀이되는 묶음 = [[순환마디]]", "점은 순환마디의 [[처음]]과 끝 숫자 위에"])

for n, d in [(19, 55), (41, 27), (5, 12), (13, 22)]:  # 순환소수 표현하기 (2): 분수 -> 순환소수
    fr = F(n, d)
    ip, pre, rep = expand(fr)
    digits = pre + rep * 3
    wrong = []
    for pl in range(0, 3):
        for rl in range(1, 4):
            cand = (digits[:pl], digits[pl:pl + rl])
            if dval(ip, *cand) != fr:
                wrong.append(f"${dtex(ip, *cand)}$")
    rng.shuffle(wrong)
    ch, ans = mc(f"${dtex(ip, pre, rep)}$", wrong)
    add(U, "분수를 순환소수로", "보통", f"분수 $\\frac{{{n}}}{{{d}}}$을 순환소수로 바르게 나타낸 것은?", ans, choices=ch,
        hint="직접 나눗셈을 해서 같은 나머지가 다시 나오는 곳을 찾아요.",
        explanation=f"{n} ÷ {d} = {dplain(ip, pre, rep)} 이므로 순환마디는 {rep}예요.",
        steps=[f"{n} ÷ {d} = {dplain(ip, pre, rep)}", f"순환마디: [[{rep}]]"])

for fr, nth in [(F(5, 7), 70), (F(5, 37), 50), (F(17, 66), 40)]:  # 소수점 아래 n번째 자리
    ip, pre, rep = expand(fr)
    dg = digit_at(fr, nth)
    k = (nth - len(pre)) % len(rep)
    add(U, "소수점 아래 n번째 자리", "어려움",
        f"분수 $\\frac{{{fr.numerator}}}{{{fr.denominator}}}$을 소수로 나타낼 때, 소수점 아래 {nth}번째 자리의 숫자를 구하시오.", dg,
        hint="순환마디 숫자 개수로 나눈 나머지를 봐요. 순환하지 않는 자리가 있으면 먼저 빼요.",
        explanation=f"{dplain(ip, pre, rep)}: 순환마디 {rep}({len(rep)}개)" + (f", 앞의 {len(pre)}자리는 빼고 셈" if pre else "") + f" → {dg}",
        steps=[f"순환마디 {rep}: 숫자 [[{len(rep)}]]개"] + ([f"순환하지 않는 자리 {len(pre)}개를 빼면 {nth - len(pre)}번째"] if pre else [])
        + [f"{nth - len(pre)} ÷ {len(rep)}의 나머지: [[{k}]]", f"구하는 숫자: [[{dg}]]"])
ip, pre, rep = expand(F(24611, 99900))  # 순환소수로 주어진 꼴
add(U, "소수점 아래 n번째 자리", "어려움", f"순환소수 ${dtex(ip, pre, rep)}$의 소수점 아래 60번째 자리의 숫자를 구하시오.",
    digit_at(dval(ip, pre, rep), 60),
    hint="소수점 아래 첫째 자리부터 순환하는지 먼저 봐요.",
    explanation=f"{dplain(ip, pre, rep, 12)}: 앞의 {len(pre)}자리를 빼고 순환마디 {rep}({len(rep)}개)로 나머지를 구해요.",
    steps=[f"순환하지 않는 자리: [[{len(pre)}]]개", f"(60 - {len(pre)}) ÷ {len(rep)}의 나머지: [[{(60 - len(pre)) % len(rep)}]]",
           f"구하는 숫자: [[{digit_at(dval(ip, pre, rep), 60)}]]"])

for n, d in [(9, 40), (7, 250), (11, 80)]:  # 10의 거듭제곱을 이용하여 유한소수로
    e2, e5, dd = 0, 0, d
    while dd % 2 == 0:
        dd //= 2; e2 += 1
    while dd % 5 == 0:
        dd //= 5; e5 += 1
    k = max(e2, e5)
    aa = n * 10 ** k // d
    add(U, "10의 거듭제곱 이용", "보통",
        f"분수 $\\frac{{{n}}}{{{d}}}$를 $\\frac{{a}}{{10^{{n}}}}$ 꼴로 나타낼 때, 자연수 $a$, $n$에 대하여 $a + n$의 가장 작은 값을 구하시오.", aa + k,
        hint="분모를 2와 5의 거듭제곱으로 나타낸 뒤, 지수가 같아지도록 부족한 2나 5를 분모·분자에 곱해요.",
        explanation=f"{d} = 2{str(e2).translate(SUP) if e2 != 1 else ''}×5{str(e5).translate(SUP) if e5 != 1 else ''} → {n}/{d} = {aa}/10{str(k).translate(SUP)} 이므로 a + n = {aa} + {k} = {aa + k}",
        steps=[f"분모를 10{str(k).translate(SUP)}로 만들면 n = [[{k}]]", f"분자 a = [[{aa}]]", f"a + n = [[{aa + k}]]"])

for n, d in [(3, 2 ** 2 * 3 * 7 * 5), (7, 2 * 3 ** 2 * 5 * 7), (5, 2 ** 3 * 11 * 3)]:  # 유한소수가 되도록 미지수
    red = F(n, d)
    need = rest25(red.denominator)
    assert all(not only25((red * k).denominator) for k in range(1, need)) and only25((red * need).denominator)
    add(U, "유한소수가 되도록 미지수 정하기", "보통",
        f"분수 $\\frac{{{n}}}{{{d}}} \\times a$를 소수로 나타내면 유한소수가 될 때, $a$의 값이 될 수 있는 가장 작은 자연수를 구하시오.", need,
        hint="기약분수로 만든 뒤, 분모에서 2와 5가 아닌 소인수를 a가 없애 줘야 해요.",
        explanation=f"{n}/{d} = {fpl(red)} 이고 분모 {red.denominator}에서 2·5가 아닌 부분은 {need}이므로 a는 {need}의 배수 → 가장 작은 값 {need}",
        steps=[f"기약분수: [[{fpl(red)}]]", f"분모에서 2와 5가 아닌 부분: [[{need}]]", f"가장 작은 a = [[{need}]]"])
for d in (2 ** 2 * 5 * 7, 2 * 3 * 5 ** 2):
    cnt = sum(1 for k in range(10, 100) if only25(F(k, d).denominator))
    r = rest25(d)
    add(U, "유한소수가 되도록 미지수 정하기", "어려움",
        f"분수 $\\frac{{a}}{{{d}}}$를 소수로 나타내면 유한소수가 될 때, $a$의 값이 될 수 있는 두 자리 자연수의 개수를 구하시오.", cnt,
        hint="분모를 소인수분해해서 a가 무엇의 배수여야 하는지 찾아요.",
        explanation=f"{d}에서 2·5가 아닌 소인수의 곱은 {r}이므로 a는 {r}의 배수. 두 자리 {r}의 배수는 {cnt}개",
        steps=[f"a는 [[{r}]]의 배수", f"두 자리 자연수 중 {r}의 배수: [[{cnt}]]개"])

for num, den0 in [(21, 2 ** 2), (33, 2 * 5)]:  # 순환소수가 되도록 미지수
    pool = [3, 5, 6, 7, 9, 11, 12, 13, 14, 15, 18, 21, 22, 24, 25, 33]
    inf = [k for k in pool if not only25(F(num, den0 * k).denominator)]
    fin = [k for k in pool if only25(F(num, den0 * k).denominator)]
    good = rng.choice(inf)
    ch, ans = mc(str(good), [str(k) for k in rng.sample(fin, 4)])
    add(U, "순환소수가 되도록 미지수 정하기", "보통",
        f"분수 $\\frac{{{num}}}{{{den0} \\times x}}$를 소수로 나타내면 순환소수가 될 때, 다음 중 $x$의 값이 될 수 있는 것은?", ans, choices=ch,
        hint="보기마다 넣어 기약분수로 만든 뒤, 분모에 2와 5가 아닌 소인수가 남는지 봐요.",
        explanation=f"x = {good}이면 {num}/{den0 * good} = {fpl(F(num, den0 * good))} 이고 분모에 2·5가 아닌 소인수가 남아 순환소수예요.",
        steps=[f"x = {good}: 기약분수 [[{fpl(F(num, den0 * good))}]]", "분모에 2·5가 아닌 소인수가 남으면 [[순환소수]]"])

for ip, pre, rep in [(0, "1", "36"), (1, "", "27"), (0, "25", "3")]:  # 순환소수 -> 분수: 가장 편리한 식
    hi, lo = 10 ** (len(pre) + len(rep)), 10 ** len(pre)
    def e(h, l):
        return f"${h if h > 1 else ''}x - {l if l > 1 else ''}x$"
    pool = [e(h, l) for h in (10, 100, 1000, 10000) for l in (1, 10, 100) if h > l]
    ch, ans = mc(e(hi, lo), [w for w in rng.sample(pool, len(pool)) if w != e(hi, lo)])
    add(U, "순환소수를 분수로 (10의 거듭제곱)", "보통",
        f"순환소수 $x = {dtex(ip, pre, rep)}$를 분수로 나타낼 때, 가장 편리한 식은?", ans, choices=ch,
        hint="소수점 아래가 똑같이 되풀이되는 두 수를 만들어 빼면 소수 부분이 없어져요.",
        explanation=f"{hi}x와 {lo if lo > 1 else ''}x는 소수점 아래가 같아서 빼면 정수가 돼요.",
        steps=[f"순환마디 앞까지 옮기려면 [[{lo}]]x (1이면 x 그대로)", f"순환마디 하나 뒤까지 옮기려면 [[{hi}]]x"])

for ip, pre, rep in [(0, "", "48"), (0, "3", "6"), (2, "", "4"), (1, "2", "45"), (0, "", "135")]:  # 순환소수 -> 기약분수
    v = dval(ip, pre, rep)
    whole, cut = int(str(ip) + pre + rep), int(str(ip) + pre)
    den = int("9" * len(rep) + "0" * len(pre))
    add(U, "순환소수를 분수로", "보통" if not pre else "어려움",
        f"순환소수 ${dtex(ip, pre, rep)}$를 기약분수로 나타내시오.", fpl(v),
        hint="분모는 순환마디 숫자 개수만큼 9, 그 뒤에 순환하지 않는 자리 수만큼 0을 붙여요.",
        explanation=f"({whole} - {cut}) / {den} = {whole - cut}/{den} = {fpl(v)}",
        steps=[f"분자: {whole} - {cut} = [[{whole - cut}]]", f"분모: [[{den}]]", f"기약분수: [[{fpl(v)}]]"])

for lo, hi, d in [(F(1, 3), F(4, 5), 35), (F(1, 6), F(3, 4), 56)]:  # 두 분수 사이 유한소수 개수
    ks = [k for k in range(1, 4 * d) if lo < F(k, d) < hi]
    cnt = sum(1 for k in ks if only25(F(k, d).denominator))
    r = rest25(d)
    add(U, "두 분수 사이의 유한소수", "어려움",
        f"두 분수 ${ftex(lo)}$과 ${ftex(hi)}$ 사이에 있는 분수 중에서 분모가 {d}인 분수 $\\frac{{a}}{{{d}}}$를 유한소수로 나타낼 수 있는 것의 개수를 구하시오. (단, $a$는 자연수)", cnt,
        hint=f"먼저 분모를 {d}로 통분해 a의 범위를 구하고, 유한소수가 되려면 a가 무엇의 배수인지 찾아요.",
        explanation=f"{ks[0]} ≤ a ≤ {ks[-1]} 이고 a는 {r}의 배수 → {cnt}개",
        steps=[f"a의 범위: [[{ks[0]}]] ≤ a ≤ [[{ks[-1]}]]", f"a는 [[{r}]]의 배수", f"개수: [[{cnt}]]"])

for ip, pre, rep in [(0, "1", "6"), (0, "", "54"), (0, "2", "7")]:  # (순환소수) × x 가 유한소수
    v = dval(ip, pre, rep)
    need = rest25(v.denominator)
    assert only25((v * need).denominator) and all(not only25((v * k).denominator) for k in range(1, need))
    add(U, "순환소수에 곱해서 유한소수 만들기", "어려움",
        f"순환소수 ${dtex(ip, pre, rep)}$에 어떤 자연수 $x$를 곱하면 유한소수가 된다. $x$의 값이 될 수 있는 가장 작은 자연수를 구하시오.", need,
        hint="먼저 순환소수를 기약분수로 바꿔요.",
        explanation=f"{dplain(ip, pre, rep, 6)} = {fpl(v)}, 분모에서 2·5가 아닌 부분 {need}를 없애야 해요.",
        steps=[f"기약분수: [[{fpl(v)}]]", f"가장 작은 x = [[{need}]]"])

facts = [
    ("모든 순환소수는 유리수이다.", True), ("모든 유한소수는 유리수이다.", True),
    ("기약분수의 분모에 2와 5 이외의 소인수가 있으면 유한소수로 나타낼 수 없다.", True),
    ("정수가 아닌 유리수는 유한소수 또는 순환소수로 나타낼 수 있다.", True),
    ("무한소수는 모두 유리수이다.", False), ("순환소수 중에는 유리수가 아닌 것도 있다.", False),
    ("분모에 3이 있는 분수는 항상 순환소수로 나타내어진다.", False),
    ("유한소수로 나타낼 수 없는 분수는 유리수가 아니다.", False), ("$0.\\dot{9}$는 1보다 작다.", False),
]
for want in (True, False):
    tr = [f for f in facts if f[1] == want]
    fl = [f for f in facts if f[1] != want]
    pick5 = [rng.choice(tr)] + rng.sample(fl, 4)
    ch, ans = pick(pick5, want)
    add(U, "유리수와 소수의 관계", "보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
        hint="유한소수와 순환소수는 분수로 나타낼 수 있어요 → 유리수.",
        explanation="정수가 아닌 유리수는 유한소수나 순환소수이고, 순환소수·유한소수는 모두 유리수예요. 순환하지 않는 무한소수는 유리수가 아니에요.",
        steps=["유한소수, 순환소수는 모두 [[유리수]]"])

# ═════════════════════ 2. 단항식의 계산 ═════════════════════
U = "2. 단항식의 계산"


def law_statements():
    """(문장, 참/거짓) 후보들: 지수법칙."""
    out = []
    m, n = rng.randint(2, 6), rng.randint(2, 5)
    out += [(f"$a^{{{m}}} \\times a^{{{n}}} = a^{{{m + n}}}$", True), (f"$a^{{{m}}} \\times a^{{{n}}} = a^{{{m * n}}}$", m * n == m + n)]
    m, n = rng.randint(2, 5), rng.randint(2, 4)
    out += [(f"$(x^{{{m}}})^{{{n}}} = x^{{{m * n}}}$", True), (f"$(x^{{{m}}})^{{{n}}} = x^{{{m + n}}}$", m * n == m + n)]
    m, n = rng.randint(6, 10), rng.randint(2, 5)
    out += [(f"$x^{{{m}}} \\div x^{{{n}}} = x^{{{m - n}}}$", True), (f"$x^{{{m}}} \\div x^{{{n}}} = x^{{{m // n}}}$", m // n == m - n and m % n == 0)]
    out += [(f"$x^{{{n}}} \\div x^{{{n}}} = 0$", False), (f"$x^{{{n}}} \\div x^{{{n + 2}}} = \\frac{{1}}{{x^{{2}}}}$", True)]
    k, m, n = rng.randint(2, 4), rng.randint(2, 4), rng.randint(2, 3)
    out += [(f"$({k}x^{{{m}}})^{{{n}}} = {k ** n}x^{{{m * n}}}$", True), (f"$({k}x^{{{m}}})^{{{n}}} = {k}x^{{{m * n}}}$", False)]
    m, n, k = rng.randint(2, 4), rng.randint(2, 4), rng.randint(2, 4)
    out += [(f"$\\left(\\frac{{x^{{{m}}}}}{{y^{{{n}}}}}\\right)^{{{k}}} = \\frac{{x^{{{m * k}}}}}{{y^{{{n * k}}}}}$", True),
            (f"$\\left(-\\frac{{a}}{{b^{{{n}}}}}\\right)^{{{2 * k}}} = -\\frac{{a^{{{2 * k}}}}}{{b^{{{2 * k * n}}}}}$", False)]
    return out


for want in (True, False, True):  # 지수법칙 옳은 것/옳지 않은 것
    st = [s for s in law_statements()]
    tr = [s for s in st if s[1] == want]
    fl = [s for s in st if s[1] != want]
    if not tr or len(fl) < 4:
        continue
    ch, ans = pick([rng.choice(tr)] + rng.sample(fl, 4), want)
    add(U, "지수법칙", "보통", f"다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
        hint="곱하면 지수를 더하고, 거듭제곱의 거듭제곱은 지수를 곱하고, 나누면 지수를 빼요.",
        explanation="aᵐ×aⁿ = aᵐ⁺ⁿ, (aᵐ)ⁿ = aᵐⁿ, aᵐ÷aⁿ = aᵐ⁻ⁿ (m>n), (ab)ⁿ = aⁿbⁿ 를 하나씩 확인해요.",
        steps=["곱셈: 지수를 [[더한다]]", "거듭제곱의 거듭제곱: 지수를 [[곱한다]]"])

for base, terms in [(3, [(27, 2), (9, 3), (81, -1)]), (2, [(8, 3), (16, 2), (4, -3)]), (5, [(25, 3), (125, 1), (5, -2)])]:  # 지수의 합과 차
    lg = {base ** k: k for k in range(1, 6)}
    tot = sum(lg[b0] * e for b0, e in terms)
    s = f"{terms[0][0]}^{{{terms[0][1]}}}"
    for b0, e in terms[1:]:
        s += (f" \\times {b0}^{{{e}}}" if e > 0 else f" \\div {b0}^{{{-e}}}")
    add(U, "지수의 합과 차", "보통", f"${s} = {base}^{{x}}$일 때, 자연수 $x$의 값을 구하시오.", tot,
        hint=f"모든 수를 {base}의 거듭제곱으로 바꾼 뒤 지수끼리 더하고 빼요.",
        explanation=" , ".join(f"{b0} = {base}{str(lg[b0]).translate(SUP)}" for b0, _ in terms) + f" → x = {tot}",
        steps=[f"{b0}{str(abs(e)).translate(SUP)} = {base}^[[{lg[b0] * abs(e)}]]" for b0, e in terms] + [f"x = [[{tot}]]"])

for k, m, n in [(4, 3, 3), (2, 3, 5), (3, 2, 4)]:  # 지수의 분배: (kx^m)^n = Kx^M -> 문자
    add(U, "지수의 분배", "보통",
        f"$({k}x^{{a}})^{{{n}}} = {k ** n}x^{{{m * n}}}$일 때, 자연수 $a$의 값을 구하시오.", m,
        hint="(kxᵃ)ⁿ = kⁿ xᵃⁿ 이에요.", explanation=f"{k}{str(n).translate(SUP)} = {k ** n}, x의 지수 a × {n} = {m * n} → a = {m}",
        steps=[f"x의 지수: a × {n} = [[{m * n}]]", f"a = [[{m}]]"])
for c, m, n, k in [(2, 1, 3, 2), (3, 2, 1, 3)]:
    expr = (-sp.Rational(c) * y ** m / (sp.Rational(c + 1) * x)) ** k
    expr = expr ** 2
    right = mono_tex(sp.expand(expr), (x, y))
    sign_wrong = mono_tex(-sp.expand(expr), (x, y))
    w2 = mono_tex(sp.expand(((sp.Rational(c) * y ** m / (sp.Rational(c + 1) * x)) ** (k + 1)) ** 2), (x, y))
    w3 = mono_tex(sp.expand(sp.Rational(c) ** (2 * k) * y ** (2 * m * k) / ((c + 1) * x ** (2 * k))), (x, y))
    w4 = mono_tex(sp.expand(sp.Rational(c) * y ** (m * k * 2) / (sp.Rational(c + 1) * x ** (2 * k))), (x, y))
    ch, ans = mc(f"${right}$", [f"${sign_wrong}$", f"${w2}$", f"${w3}$", f"${w4}$"])
    inner = f"\\left(-\\frac{{{c}{'y' if m == 1 else f'y^{{{m}}}'}}}{{{c + 1}x}}\\right)^{{{k}}}"
    add(U, "지수의 분배", "어려움", f"$\\left\\{{{inner}\\right\\}}^{{2}}$을 간단히 하면?", ans, choices=ch,
        hint="안쪽부터 차례로: 분자·분모에 각각 지수를 분배하고, 음수의 짝수 제곱은 양수예요.",
        explanation=f"지수를 곱하면 {k}×2 = {2 * k}제곱, 짝수 제곱이라 부호는 +",
        steps=[f"전체 지수: {k} × 2 = [[{2 * k}]]", "부호: [[+]]"])

for base, k, e in [(3, 3, 7), (2, 4, 6), (5, 5, 2)]:  # 거듭제곱의 합
    s = " + ".join([f"{base}^{{{e}}}"] * k)
    if k == base:
        add(U, "거듭제곱의 합", "보통", f"${s}$을 {base}의 거듭제곱으로 나타내면 ${base}^{{\\square}}$이다. $\\square$ 안에 알맞은 수를 구하시오.", e + 1,
            hint="같은 수를 여러 번 더하면 곱셈이에요: aᵐ + aᵐ + ... (a개) = a × aᵐ",
            explanation=f"{base}개를 더했으므로 {base} × {base}{str(e).translate(SUP)} = {base}{str(e + 1).translate(SUP)}",
            steps=[f"{s.replace('^{', '').replace('}', '')} = {base} × {base}^{e}".replace(f"{base}{e}", f"{base}^{e}"), f"= {base}^[[{e + 1}]]"])
    else:
        num = sp.Integer(k) * base ** e
        # 2^6 + 2^6 + 2^6 + 2^6 = 4 × 2^6 = 2^8
        lg = int(math.log2(k)) if base == 2 else None
        add(U, "거듭제곱의 합", "보통", f"${s}$을 {base}의 거듭제곱으로 나타내면 ${base}^{{\\square}}$이다. $\\square$ 안에 알맞은 수를 구하시오.", e + lg,
            hint="같은 수를 여러 번 더하면 곱셈이에요. 그 개수도 2의 거듭제곱으로 바꿔요.",
            explanation=f"{k} × 2{str(e).translate(SUP)} = 2{str(lg).translate(SUP)} × 2{str(e).translate(SUP)} = 2{str(e + lg).translate(SUP)}",
            steps=[f"{k}개를 더함 → {k} = 2^[[{lg}]]", f"2^{lg} × 2^{e} = 2^[[{e + lg}]]"])
        assert num == 2 ** (e + lg)
add(U, "거듭제곱의 합", "어려움",
    "$\\frac{3^{4} + 3^{4} + 3^{4}}{9^{2} + 9^{2} + 9^{2}} \\times 27 = 3^{\\square}$일 때, $\\square$ 안에 알맞은 자연수를 구하시오.", 3,
    hint="분자 = 3 × 3⁴, 분모 = 3 × 9² = 3 × 3⁴",
    explanation="(3×3⁴)/(3×3⁴) = 1, 1 × 27 = 3³",
    steps=["분자 = 3^[[5]]", "분모 = 3^[[5]]", "3^[[3]]"])
assert F(3 * 3 ** 4, 3 * 9 ** 2) * 27 == 3 ** 3

for base, big, k, inv in [(2, 16, 4, False), (3, 27, 3, True), (5, 125, 3, False)]:  # 문자를 사용하여 거듭제곱
    if not inv:
        ch, ans = mc(f"$A^{{{k}}}$", [f"$A^{{{k + 1}}}$", f"${k}A$", f"${k}A^{{{k}}}$", f"$A^{{{2 * k}}}$"])
        add(U, "문자를 사용하여 거듭제곱 나타내기", "보통", f"${base}^{{x}} = A$라 할 때, ${big}^{{x}}$을 $A$를 사용하여 나타내면? (단, $x$는 자연수)", ans, choices=ch,
            hint=f"{big}을 {base}의 거듭제곱으로 바꾼 뒤 지수의 순서를 바꿔요.",
            explanation=f"{big}ˣ = ({base}{str(k).translate(SUP)})ˣ = ({base}ˣ){str(k).translate(SUP)} = A{str(k).translate(SUP)}",
            steps=[f"{big} = {base}^[[{k}]]", f"{big}ˣ = A^[[{k}]]"])
    else:
        e = 5
        # 3^5 = A 라 할 때 1/27^5 = 1/(3^15) = 1/A^3
        ch, ans = mc(f"$\\frac{{1}}{{A^{{{k}}}}}$", [f"$\\frac{{1}}{{A^{{{k + 1}}}}}$", f"$\\frac{{1}}{{A^{{{k + 2}}}}}$", f"$\\frac{{1}}{{A^{{{3 * k}}}}}$", f"$\\frac{{1}}{{A^{{{k * e}}}}}$"])
        add(U, "문자를 사용하여 거듭제곱 나타내기", "어려움", f"${base}^{{{e}}} = A$라 할 때, $\\frac{{1}}{{{big}^{{{e}}}}}$을 $A$를 사용하여 나타내면?", ans, choices=ch,
            hint=f"{big}을 {base}의 거듭제곱으로 바꿔요.",
            explanation=f"{big}{str(e).translate(SUP)} = {base}{str(k * e).translate(SUP)} = ({base}{str(e).translate(SUP)}){str(k).translate(SUP)} = A{str(k).translate(SUP)}",
            steps=[f"{big}^{e} = {base}^[[{k * e}]]", f"= A^[[{k}]]"])

for e2, e5, extra in [(7, 4, 1), (5, 8, 3), (9, 6, 9)]:  # n자리 자연수
    N = 2 ** e2 * 5 ** e5 * extra
    nd = len(str(N))
    q = f"2^{{{e2}}} \\times 5^{{{e5}}}" + (f" \\times {extra}" if extra != 1 else "")
    add(U, "n자리 자연수", "어려움", f"${q}$은 몇 자리 자연수인지 구하시오.", nd,
        hint="2와 5를 같은 지수끼리 묶어 10의 거듭제곱을 만들어요.",
        explanation=f"{N} 이므로 {nd}자리 자연수",
        steps=[f"2와 5를 묶으면 10^[[{min(e2, e5)}]]", f"남은 수 × 10^{min(e2, e5)} = {N // 10 ** min(e2, e5)} × 10^{min(e2, e5)}",
               f"자릿수: [[{nd}]]"])

for (c1, a1, b1, k1), (c2, a2, b2), (c3, a3, b3) in [((-3, 1, 2, 2), (2, 3, 1), (F(-1, 6), 2, 3)),  # (단항식)×(단항식)
                                                    ((2, 2, 1, 3), (-3, 1, 2), (F(1, 12), 4, 3))]:
    e = (sp.Rational(c1) * x ** a1 * y ** b1) ** k1 * (c2 * x ** a2 * y ** b2) * (sp.Rational(c3.numerator, c3.denominator) * x ** a3 * y ** b3)
    e = sp.expand(e)
    # 분모에 들어가는 항을 만들기 위해 세 번째를 1/(...) 꼴로 쓴다
    third = f"\\left({'-' if c3 < 0 else ''}\\frac{{1}}{{{abs(c3.denominator)}x^{{{a3}}}y^{{{b3}}}}}\\right)"
    e = sp.expand((sp.Rational(c1) * x ** a1 * y ** b1) ** k1 * (c2 * x ** a2 * y ** b2) * (sp.Rational(c3.numerator, c3.denominator) / (x ** a3 * y ** b3)))
    A = sp.Poly(e, x, y).LC() if e.is_polynomial(x, y) else None
    num, den = sp.fraction(sp.together(e))
    assert not den.free_symbols, e
    (mon, coef), = sp.Poly(e, x, y).terms()
    B, C = mon
    add(U, "(단항식)×(단항식)", "어려움",
        f"$({t(sp.Rational(c1) * x ** a1 * y ** b1)})^{{{k1}}} \\times {('(' + t(c2 * x ** a2 * y ** b2) + ')') if c2 < 0 else t(c2 * x ** a2 * y ** b2)} \\times {third} = Ax^{{B}}y^{{C}}$일 때, $A + B + C$의 값을 구하시오. (단, $B$, $C$는 자연수)",
        int(coef) + B + C,
        hint="부호 → 계수 → 문자 순서로 따로 계산해요.",
        explanation=f"간단히 하면 {p(e)} 이므로 A = {coef}, B = {B}, C = {C}",
        steps=[f"A = [[{coef}]]", f"B = [[{B}]]", f"C = [[{C}]]", f"A + B + C = [[{int(coef) + B + C}]]"])

for (c, m, n, k), (dc, dm, dn) in [((2, 2, 3, 3), (F(4, 5), 3, 2)), ((3, 1, 2, 2), (F(3, 2), 1, 3))]:  # (단항식)÷(단항식)
    num_ = (c * a ** m * b ** n) ** k
    res = sp.expand(num_ / (sp.Rational(dc.numerator, dc.denominator) * a ** dm * b ** dn))
    (mon, coef), = sp.Poly(res, a, b).terms()
    add(U, "(단항식)÷(단항식)", "보통",
        f"$({t(c * a ** m * b ** n, (a, b))})^{{{k}}} \\div {ftex(dc)}{t(a ** dm * b ** dn, (a, b))} = {coef}a^{{x}}b^{{y}}$일 때, 자연수 $x$, $y$에 대하여 $x + y$의 값을 구하시오.",
        mon[0] + mon[1],
        hint="분수로 나누는 것은 역수를 곱하는 것과 같아요.",
        explanation=f"간단히 하면 {p(res, (a, b))} → x = {mon[0]}, y = {mon[1]}",
        steps=[f"({c}a^{m}b^{n})^{k} = {p(num_, (a, b))}", f"x = [[{mon[0]}]]", f"y = [[{mon[1]}]]", f"x + y = [[{mon[0] + mon[1]}]]"])

for (c1, a1, k1), (c2, a2, b2, k2), (dc, da, db) in [((-3, 2, 1), (1, 1, 2, 3), (F(1, 2), 2, 1)),  # 혼합 계산
                                                    ((2, 1, 2), (-1, 2, 1, 3), (F(2, 3), 3, 2))]:
    e1 = (sp.Rational(c1) * x ** a1) ** k1
    e2 = (sp.Rational(c2) * x ** a2 * y ** b2) ** k2
    d = sp.Rational(dc.numerator, dc.denominator) * x ** da * y ** db
    res = sp.expand(e1 * e2 / d)
    wrong = [sp.expand(-res), sp.expand(e1 * e2 * d), sp.expand(res * x), sp.expand(res / y), sp.expand(res * 2)]
    ch, ans = mc(f"${mono_tex(res, (x, y))}$", [f"${mono_tex(w, (x, y))}$" for w in wrong])
    q = (f"$({t(sp.Rational(c1) * x ** a1)})" + (f"^{{{k1}}}" if k1 > 1 else "") + f" \\times ({t(sp.Rational(c2) * x ** a2 * y ** b2)})^{{{k2}}} \\div {ftex(dc)}{t(x ** da * y ** db)}$을 계산하면?")
    add(U, "곱셈과 나눗셈의 혼합 계산", "보통", q, ans, choices=ch,
        hint="거듭제곱을 먼저 하고, 나눗셈은 역수의 곱셈으로 바꿔요.",
        explanation=f"계산하면 {p(res)}",
        steps=[f"({p(sp.Rational(c2) * x ** a2 * y ** b2)})^{k2} = {p(e2)}", f"결과: {p(res)}"])

for left, box, div, right in [((24, 2, 0), (F(2), 2, 1), (12, 3, 1), None), ((5, 1, 2), (F(3), 2, 0), (15, 2, 1), None)]:  # □ 안에 알맞은 식
    L = left[0] * a ** left[1] * b ** left[2]
    BX = sp.Rational(box[0].numerator, box[0].denominator) * a ** box[1] * b ** box[2]
    D = div[0] * a ** div[1] * b ** div[2]
    R = sp.expand(L * BX / D)
    wrong = [sp.expand(BX * a), sp.expand(BX / a) if box[1] else sp.expand(BX * b), sp.expand(BX * 2), sp.expand(-BX), sp.expand(BX * b)]
    wrong = [w for w in wrong if w != BX]
    ch, ans = mc(f"${t(BX, (a, b))}$", [f"${mono_tex(w, (a, b))}$" for w in wrong])
    add(U, "□ 안에 알맞은 식", "보통",
        f"${t(L, (a, b))} \\times \\square \\div {t(D, (a, b))} = {mono_tex(R, (a, b))}$일 때, $\\square$ 안에 알맞은 식은?", ans, choices=ch,
        hint="□ = (오른쪽) × (나눈 식) ÷ (곱한 식) 으로 거꾸로 계산해요.",
        explanation=f"□ = {p(R, (a, b))} × {p(D, (a, b))} ÷ {p(L, (a, b))} = {p(BX, (a, b))}",
        steps=[f"□ = ({p(R, (a, b))}) × ({p(D, (a, b))}) ÷ ({p(L, (a, b))})", f"□ = {p(BX, (a, b))}"])

base_, area = 4 * x * y ** 2, 20 * x ** 3 * y ** 5  # 도형에의 활용
h = sp.expand(area / base_)
ch, ans = mc(f"${t(h)}$", [f"${t(sp.expand(h * x))}$", f"${t(sp.expand(h / y))}$", f"${t(sp.expand(2 * h))}$", f"${t(sp.expand(h * y))}$"])
add(U, "도형에의 활용", "보통", f"밑변의 길이가 ${t(base_)}$인 평행사변형의 넓이가 ${t(area)}$일 때, 이 평행사변형의 높이는?", ans, choices=ch,
    hint="평행사변형의 넓이 = 밑변 × 높이", explanation=f"높이 = {p(area)} ÷ {p(base_)} = {p(h)}",
    steps=[f"높이 = {p(area)} ÷ {p(base_)}", f"= {p(h)}"])
w_, l_, vol = 2 * a * b, 3 * a ** 2, 18 * a ** 4 * b ** 3
hh = sp.expand(vol / (w_ * l_))
ch, ans = mc(f"${t(hh, (a, b))}$", [f"${t(sp.expand(hh * a), (a, b))}$", f"${t(sp.expand(hh * 2), (a, b))}$", f"${t(sp.expand(hh / b), (a, b))}$", f"${t(sp.expand(hh * 3), (a, b))}$"])
add(U, "도형에의 활용", "보통", f"가로의 길이가 ${t(w_, (a, b))}$, 세로의 길이가 ${t(l_, (a, b))}$인 직육면체의 부피가 ${t(vol, (a, b))}$일 때, 이 직육면체의 높이는?", ans, choices=ch,
    hint="직육면체의 부피 = 가로 × 세로 × 높이", explanation=f"높이 = {p(vol, (a, b))} ÷ ({p(w_, (a, b))} × {p(l_, (a, b))}) = {p(hh, (a, b))}",
    steps=[f"가로 × 세로 = {p(w_ * l_, (a, b))}", f"높이 = {p(hh, (a, b))}"])

for X, Y in [(2 * b / a, 6 * a ** 3 * b ** 2), (-3 * x / y ** 2, F(2, 3))]:  # 잘못 계산한 식
    if isinstance(Y, F):
        Y = sp.Rational(2, 3) * x * y ** 3
        gens = (x, y)
    else:
        gens = (a, b)
    A_ = sp.expand(Y * X)
    right = sp.expand(A_ * X)
    wrong = [sp.expand(A_ / X), sp.expand(Y), sp.expand(-right), sp.expand(right * gens[0]), sp.expand(right / gens[1])]
    ch, ans = mc(f"${mono_tex(right, gens)}$", [f"${mono_tex(w, gens)}$" for w in wrong if sp.simplify(w - right) != 0])
    add(U, "잘못 계산한 식 바로잡기", "어려움",
        f"어떤 식에 ${mono_tex(X, gens)}$를 곱해야 할 것을 잘못하여 나누었더니 ${mono_tex(Y, gens)}$가 되었다. 바르게 계산한 식은?", ans, choices=ch,
        hint="먼저 어떤 식 A를 구해요: A ÷ X = Y 이므로 A = Y × X",
        explanation=f"A = {mono_tex(Y, gens, False)} × {mono_tex(X, gens, False)} = {mono_tex(A_, gens, False)}, 바른 계산 A × X = {mono_tex(right, gens, False)}",
        steps=[f"어떤 식 A = {mono_tex(A_, gens, False)}", f"바른 계산: {mono_tex(right, gens, False)}"])

# ═════════════════════ 3. 다항식의 계산 ═════════════════════
U = "3. 다항식의 계산"


def poly_mc(right, gens, wrongs):
    return mc(f"${fmt(right, gens)}$", [f"${fmt(w, gens)}$" for w in wrongs if sp.expand(w - right) != 0])


for P, Q, k in [((1, 3, -2), (2, 1, -3), 3), ((2, -3, 5), (1, -2, 2), 2)]:  # 다항식의 덧셈과 뺄셈
    E1 = P[0] * a + P[1] * b + P[2]
    E2 = Q[0] * a + Q[1] * b + Q[2]
    R = sp.expand(E1 - k * E2)
    wr = [sp.expand(E1 + k * E2), sp.expand(E1 - k * Q[0] * a - k * Q[1] * b + k * Q[2]), sp.expand(E1 - k * Q[0] * a + k * Q[1] * b - k * Q[2]),
          sp.expand(E1 - Q[0] * a - k * Q[1] * b - k * Q[2]), sp.expand(R + 2 * b)]
    ch, ans = poly_mc(R, (a, b), wr)
    add(U, "다항식의 덧셈과 뺄셈", "쉬움", f"$({fmt(E1, (a, b))}) - {k}({fmt(E2, (a, b))})$를 계산하면?", ans, choices=ch,
        hint="괄호 앞의 수를 모든 항에 곱하고, 빼는 식은 부호를 모두 바꿔요.",
        explanation=f"동류항끼리 모으면 {fmt(R, (a, b), False)}",
        steps=[f"a의 계수: {P[0]} - {k}×({Q[0]}) = [[{P[0] - k * Q[0]}]]", f"b의 계수: {P[1]} - {k}×({Q[1]}) = [[{P[1] - k * Q[1]}]]",
               f"상수항: {P[2]} - {k}×({Q[2]}) = [[{P[2] - k * Q[2]}]]"])
for (p1, q1), (p2, q2) in [((F(-1, 4), F(1, 3)), (F(3, 4), F(1, 6))), ((F(3, 4), F(-2, 3)), (F(-1, 2), F(1, 6)))]:
    A_, B_ = p1 + p2, q1 + q2
    ex = lambda c, v: (ftex(c) if abs(c) != 1 else ("-" if c < 0 else "")) + v
    def two(c1, c2):
        s = ex(c1, "x")
        s += (" + " if c2 > 0 else " - ") + ex(abs(c2), "y")
        return s
    add(U, "다항식의 덧셈과 뺄셈", "보통",
        f"$\\left({two(p1, q1)}\\right) + \\left({two(p2, q2)}\\right) = ax + by$일 때, 수 $a$, $b$에 대하여 $ab$의 값을 구하시오.", fpl(A_ * B_),
        hint="x는 x끼리, y는 y끼리 통분해서 더해요.",
        explanation=f"a = {fpl(p1)} + ({fpl(p2)}) = {fpl(A_)}, b = {fpl(q1)} + ({fpl(q2)}) = {fpl(B_)}, ab = {fpl(A_ * B_)}",
        steps=[f"a = [[{fpl(A_)}]]", f"b = [[{fpl(B_)}]]", f"ab = [[{fpl(A_ * B_)}]]"])

for E1, E2, k in [(-2 * x ** 2 + 5 * x - 3, x ** 2 + x - 4, 3), (2 * x ** 2 - x + 7, -x ** 2 + 2 * x + 3, 3)]:  # 이차식의 덧셈과 뺄셈
    R = sp.expand(E1 - k * E2)
    po = sp.Poly(R, x)
    c2, c0 = po.coeff_monomial(x ** 2), po.coeff_monomial(1)
    add(U, "이차식의 덧셈과 뺄셈", "보통",
        f"$({fmt(E1, (x,))}) - {k}({fmt(E2, (x,))})$를 계산했을 때, $x^{{2}}$의 계수와 상수항의 합을 구하시오.", int(c2 + c0),
        hint="괄호를 풀 때 빼기 뒤의 모든 항의 부호를 바꿔요.",
        explanation=f"계산하면 {fmt(R, (x,), False)} → {c2} + ({c0}) = {c2 + c0}",
        steps=[f"x²의 계수: [[{c2}]]", f"상수항: [[{c0}]]", f"합: [[{c2 + c0}]]"])
E = sp.Rational(1, 3) * (x ** 2 - 3 * x + 2) + sp.Rational(1, 2) * (x ** 2 + x)
po = sp.Poly(sp.expand(E), x)
A2, B2, C2 = po.coeff_monomial(x ** 2), po.coeff_monomial(x), po.coeff_monomial(1)
val = A2 + 6 * B2 - 3 * C2
add(U, "이차식의 덧셈과 뺄셈", "어려움",
    "$\\frac{x^{2} - 3x + 2}{3} + \\frac{x^{2} + x}{2} = ax^{2} + bx + c$일 때, 수 $a$, $b$, $c$에 대하여 $a + 6b - 3c$의 값을 구하시오.", fpl(F(int(sp.fraction(val)[0]), int(sp.fraction(val)[1]))),
    hint="분모 3과 2의 최소공배수 6으로 통분해요.",
    explanation=f"a = {A2}, b = {B2}, c = {C2} → a + 6b - 3c = {val}",
    steps=[f"a = [[{A2}]]", f"b = [[{B2}]]", f"c = [[{C2}]]", f"a + 6b - 3c = [[{val}]]"])

for q, e in [("3a - [2b - \\{4a - (a - 3b)\\}]", 3 * a - (2 * b - (4 * a - (a - 3 * b)))),  # 여러 가지 괄호
             ("5x - [3y - \\{2x - (x - 4y)\\} + x]", 5 * x - (3 * y - (2 * x - (x - 4 * y)) + x))]:
    gens = (a, b) if "a" in q else (x, y)
    R = sp.expand(e)
    wr = [sp.expand(R + 2 * gens[1]), sp.expand(R - 2 * gens[0]), sp.expand(-R), sp.expand(R + 4 * gens[1] - 2 * gens[0]), sp.expand(R - 6 * gens[1])]
    ch, ans = poly_mc(R, gens, wr)
    add(U, "여러 가지 괄호가 있는 계산", "보통", f"${q}$를 계산하면?", ans, choices=ch,
        hint="( ) → { } → [ ] 순서로 안쪽 괄호부터 풀어요.",
        explanation=f"안쪽부터 풀면 {fmt(R, gens, False)}",
        steps=[f"결과: {fmt(R, gens, False)}"])
e = 4 * a - (3 * a ** 2 - (2 * a - 5 * a ** 2 - (a ** 2 - 4 * a)))
po = sp.Poly(sp.expand(e), a)
c2, c1 = po.coeff_monomial(a ** 2), po.coeff_monomial(a)
add(U, "여러 가지 괄호가 있는 계산", "어려움",
    "$4a - [3a^{2} - \\{2a - 5a^{2} - (a^{2} - 4a)\\}]$를 계산했을 때, $a^{2}$의 계수를 $p$, $a$의 계수를 $q$라 하자. $p + q$의 값을 구하시오.", int(c2 + c1),
    hint="안쪽 괄호부터 하나씩 풀고, 괄호 앞이 빼기면 부호를 모두 바꿔요.",
    explanation=f"계산하면 {fmt(sp.expand(e), (a,), False)} → p + q = {c2} + ({c1}) = {c2 + c1}",
    steps=[f"p = [[{c2}]]", f"q = [[{c1}]]", f"p + q = [[{c2 + c1}]]"])

for A_, Bw, add_mode in [(2 * x ** 2 - 3 * x + 5, x ** 2 + 4 * x - 1, True), (4 * x ** 2 - 1 + x, -5 * x ** 2 - 2 * x + 3, False)]:  # 바르게 계산한 식
    # add_mode: A를 더해야 할 것을 빼서 Bw가 됨 -> 어떤 식 = Bw + A, 바른 답 = Bw + 2A
    if add_mode:
        orig = sp.expand(Bw + A_); right = sp.expand(orig + A_)
        q = f"어떤 식에 ${fmt(A_, (x,))}$를 더해야 할 것을 잘못하여 뺐더니 ${fmt(Bw, (x,))}$가 되었다. 바르게 계산한 식은?"
        hint = "먼저 어떤 식을 구해요: (어떤 식) - A = B 이므로 (어떤 식) = B + A"
    else:
        orig = sp.expand(Bw - A_); right = sp.expand(orig - A_)
        q = f"어떤 식에서 ${fmt(A_, (x,))}$를 빼야 할 것을 잘못하여 더했더니 ${fmt(Bw, (x,))}$가 되었다. 바르게 계산한 식은?"
        hint = "먼저 어떤 식을 구해요: (어떤 식) + A = B 이므로 (어떤 식) = B - A"
    wr = [orig, sp.expand(Bw), sp.expand(-right), sp.expand(right + 2 * x), sp.expand(right - 2)]
    ch, ans = poly_mc(right, (x,), wr)
    add(U, "바르게 계산한 식 구하기", "어려움", q, ans, choices=ch, hint=hint,
        explanation=f"어떤 식 = {fmt(orig, (x,), False)}, 바른 계산 = {fmt(right, (x,), False)}",
        steps=[f"어떤 식 = {fmt(orig, (x,), False)}", f"바른 계산 = {fmt(right, (x,), False)}"])

e1 = sp.expand(-3 * x * (x + 2 * y - 4)); e2 = sp.expand((2 * x - 3 * y + 1) * 5 * y)  # (단항식)×(다항식)
c_a = sp.Poly(e1, x, y).coeff_monomial(x ** 2); c_b = sp.Poly(e2, x, y).coeff_monomial(x * y)
add(U, "(단항식)×(다항식)", "보통",
    "$-3x(x + 2y - 4)$를 전개한 식의 $x^{2}$의 계수를 $a$, $(2x - 3y + 1) \\times 5y$를 전개한 식의 $xy$의 계수를 $b$라 할 때, $ab$의 값을 구하시오.", int(c_a * c_b),
    hint="분배법칙으로 괄호 안의 모든 항에 곱해요.",
    explanation=f"{fmt(e1, (x, y), False)} → a = {c_a}, {fmt(e2, (x, y), False)} → b = {c_b}, ab = {c_a * c_b}",
    steps=[f"a = [[{c_a}]]", f"b = [[{c_b}]]", f"ab = [[{c_a * c_b}]]"])
st = []
for lt, lhs, shown, gens in [
    ("-2a(3a - b)", -2 * a * (3 * a - b), -6 * a ** 2 - 2 * a * b, (a, b)),
    ("x(x - 4y)", x * (x - 4 * y), x ** 2 - 4 * x * y, (x, y)),
    ("\\frac{1}{2}a(4a - 6)", sp.Rational(1, 2) * a * (4 * a - 6), 2 * a ** 2 - 6 * a, (a, b)),
    ("(3x - y) \\times 2y", (3 * x - y) * 2 * y, 6 * x * y - 2 * y, (x, y)),
    ("-b(2a^{2} - ab + b^{2})", -b * (2 * a ** 2 - a * b + b ** 2), -2 * a ** 2 * b - a * b ** 2 - b ** 3, (a, b)),
]:
    st.append((f"${lt} = {fmt(shown, gens)}$", sp.expand(lhs - shown) == 0))
ch, ans = pick(st, True)
add(U, "(단항식)×(다항식)", "보통", "다음 중 옳은 것은?", ans, choices=ch,
    hint="괄호 안의 각 항에 빠짐없이 곱하고, 부호를 꼭 확인해요.",
    explanation="각 식을 분배법칙으로 전개해서 오른쪽과 비교해요.",
    steps=["괄호 안의 [[모든]] 항에 곱한다"])

for num_, den_ in [(9 * x ** 2 * y - 6 * x * y ** 2 + 3 * x * y, sp.Rational(3, 4) * x * y), (9 * a ** 2 * b - 12 * a * b ** 2 + 3 * a * b, -sp.Rational(3, 2) * a * b)]:  # (다항식)÷(단항식)
    gens = (x, y) if num_.has(x) else (a, b)
    R = sp.expand(num_ / den_)
    po = sp.Poly(R, *gens)
    A1 = po.coeff_monomial(gens[0]); B1 = po.coeff_monomial(gens[1]); C1 = po.coeff_monomial(1)
    v = A1 - B1 - C1
    add(U, "(다항식)÷(단항식)", "보통",
        f"$({fmt(num_, gens)}) \\div \\left({mono_tex(den_, gens)}\\right) = a{gens[0]} + b{gens[1]} + c$일 때, 수 $a$, $b$, $c$에 대하여 $a - b - c$의 값을 구하시오.".replace("a(x", "a(x") if gens == (x, y) else
        f"$({fmt(num_, gens)}) \\div \\left({mono_tex(den_, gens)}\\right) = pa + qb + r$일 때, 수 $p$, $q$, $r$에 대하여 $p - q - r$의 값을 구하시오.",
        int(v), hint="분수로 나눌 때는 역수를 곱해요. 각 항을 따로 나눠요.",
        explanation=f"계산하면 {fmt(R, gens, False)}",
        steps=[f"결과: {fmt(R, gens, False)}", f"답: [[{v}]]"])

for xv, yv in [(-1, 2), (2, -1)]:  # 식의 값
    E = (8 * x * y ** 2 - 6 * x ** 2) / (2 * x) - (15 * x * y - 9 * x ** 2 * y) / (3 * y)
    S = sp.expand(sp.simplify(E))
    val_ = S.subs({x: xv, y: yv})
    add(U, "식의 값", "보통",
        f"$x = {xv}$, $y = {yv}$일 때, $\\frac{{8xy^{{2}} - 6x^{{2}}}}{{2x}} - \\frac{{15xy - 9x^{{2}}y}}{{3y}}$의 값을 구하시오.", int(val_),
        hint="먼저 식을 간단히 한 뒤 수를 대입해요. 음수는 괄호를 씌워요.",
        explanation=f"간단히 하면 {fmt(S, (x, y), False)}, 대입하면 {val_}",
        steps=[f"간단히: {fmt(S, (x, y), False)}", f"값: [[{val_}]]"])

E = (27 * x ** 3 - 9 * x ** 2 * y) / (3 * x) ** 2 + (2 * x * y - y ** 2) * (-sp.Rational(3, 1) / y)  # 혼합 계산
S = sp.expand(sp.simplify(E))
ch, ans = poly_mc(S, (x, y), [sp.expand(S + 6 * x), sp.expand(S - 2 * y), sp.expand(-S), sp.expand(S - 6 * y), sp.expand(S + 12 * x)])
add(U, "혼합된 식의 계산", "어려움",
    "$(27x^{3} - 9x^{2}y) \\div (3x)^{2} + (2xy - y^{2}) \\times \\left(-\\frac{3}{y}\\right)$를 계산하면?", ans, choices=ch,
    hint="거듭제곱 → 곱셈·나눗셈 → 덧셈·뺄셈 순서로 해요.",
    explanation=f"앞: {fmt(sp.expand((27 * x ** 3 - 9 * x ** 2 * y) / (9 * x ** 2)), (x, y), False)}, 뒤: {fmt(sp.expand((2 * x * y - y ** 2) * (-3 / y)), (x, y), False)} → {fmt(S, (x, y), False)}",
    steps=["(3x)² = [[9]]x²", f"결과: {fmt(S, (x, y), False)}"])

BX = 3 * a * b - 2 * b  # □ 안에 알맞은 식 (다항식)
L = -sp.Rational(4, 3) * a * b
R = sp.expand(L * (-9 * a ** 2 + 3 * a) / 1)
L, BX = -sp.Rational(3, 2) * a * b, -4 * a ** 2 + 2 * a * b
R = sp.expand(L * BX)
ch, ans = poly_mc(BX, (a, b), [sp.expand(-BX), sp.expand(BX * a), sp.expand(4 * a ** 2 + 2 * a * b), sp.expand(-4 * a + 2 * b), sp.expand(R / L * 2)])
add(U, "□ 안에 알맞은 식", "보통",
    f"$\\left(-\\frac{{3}}{{2}}ab\\right) \\times \\square = {fmt(R, (a, b))}$일 때, $\\square$ 안에 알맞은 식은?", ans, choices=ch,
    hint="□ = (오른쪽) ÷ (곱한 단항식)", explanation=f"□ = ({fmt(R, (a, b), False)}) ÷ (-3/2ab) = {fmt(BX, (a, b), False)}",
    steps=[f"□ = {fmt(BX, (a, b), False)}"])

top, bot, hgt = 4 * y, 8 * x * y - 2 * y, 2 * x ** 2 * y  # 도형: 사다리꼴
S = sp.expand((top + bot) * hgt / 2)
ch, ans = poly_mc(S, (x, y), [sp.expand((top + bot) * hgt), sp.expand(S + 2 * x ** 3 * y ** 2), sp.expand(bot * hgt / 2), sp.expand(S - 4 * x ** 3 * y ** 2)])
add(U, "도형에의 활용", "보통",
    f"윗변의 길이가 ${t(top)}$, 아랫변의 길이가 ${t(bot)}$, 높이가 ${t(hgt)}$인 사다리꼴의 넓이는?", ans, choices=ch,
    hint="사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2",
    explanation=f"({p(top)} + {p(bot)}) × {p(hgt)} ÷ 2 = {p(S)}",
    steps=[f"윗변 + 아랫변 = {p(sp.expand(top + bot))}", f"넓이 = {p(S)}"])
h_, S = 4 * a, 12 * a ** 2 + 8 * a * b  # 삼각형 밑변
base = sp.expand(2 * S / h_)
ch, ans = poly_mc(base, (a, b), [sp.expand(S / h_), sp.expand(base * a), sp.expand(base + 2 * b), sp.expand(base - 3 * a)])
add(U, "도형에의 활용", "보통", f"높이가 ${t(h_, (a, b))}$인 삼각형의 넓이가 ${t(S, (a, b))}$일 때, 이 삼각형의 밑변의 길이는?", ans, choices=ch,
    hint="삼각형의 넓이 = 밑변 × 높이 ÷ 2 이므로 밑변 = 넓이 × 2 ÷ 높이",
    explanation=f"({p(S, (a, b))}) × 2 ÷ {p(h_, (a, b))} = {p(base, (a, b))}",
    steps=[f"밑변 = {p(base, (a, b))}"])
W, H, eb, fc = 6 * a, 4 * b, 2 * a, 3 * b  # 색칠한 부분의 넓이 = 직사각형 - 세 직각삼각형
# A(0, H) 왼쪽 위, B(W, H) 오른쪽 위, C(W, 0) 오른쪽 아래, D(0, 0) 왼쪽 아래, E는 AB 위 (EB = eb), F는 BC 위 (FC = fc)
t_ade = (W - eb) * H / 2
t_ebf = eb * (H - fc) / 2
t_fcd = fc * W / 2
shaded = sp.expand(W * H - t_ade - t_ebf - t_fcd)
Ex, Ey, Fx, Fy = W - eb, H, W, fc
assert sp.expand(shaded - sp.Abs(Ex * Fy - Fx * Ey).subs({a: 1, b: 1}) / 2 * a * b) == 0  # 신발끈 공식으로 검산 (D가 원점)
ch, ans = poly_mc(shaded, (a, b), [sp.expand(2 * shaded), sp.expand(W * H - shaded), sp.expand(shaded + a * b), sp.expand(shaded - 2 * a * b)])
add(U, "도형에의 활용 (색칠한 부분)", "어려움",
    f"가로 ${t(W, (a, b))}$, 세로 ${t(H, (a, b))}$인 직사각형 ABCD가 있다. (A는 왼쪽 위, B는 오른쪽 위, C는 오른쪽 아래, D는 왼쪽 아래 꼭짓점) "
    f"변 AB 위에 $\\overline{{EB}} = {t(eb, (a, b))}$인 점 E, 변 BC 위에 $\\overline{{FC}} = {t(fc, (a, b))}$인 점 F를 잡을 때, 삼각형 DEF의 넓이는? (공책에 그림을 그려 봐요)",
    ans, choices=ch,
    hint="(삼각형 DEF) = (직사각형) - (삼각형 ADE) - (삼각형 EBF) - (삼각형 FCD)",
    explanation=f"{p(W * H, (a, b))} - {p(sp.expand(t_ade), (a, b))} - {p(sp.expand(t_ebf), (a, b))} - {p(sp.expand(t_fcd), (a, b))} = {p(shaded, (a, b))}",
    steps=[f"삼각형 ADE = {p(sp.expand(t_ade), (a, b))}", f"삼각형 EBF = {p(sp.expand(t_ebf), (a, b))}", f"삼각형 FCD = {p(sp.expand(t_fcd), (a, b))}",
           f"넓이 = {p(shaded, (a, b))}"])

# ═════════════════════ 4. 일차부등식 ═════════════════════
U = "4. 일차부등식"
REL = {">": ">", "<": "<", ">=": "\\geq", "<=": "\\leq"}
FLIP = {">": "<", "<": ">", ">=": "<=", "<=": ">="}
PL = {">": ">", "<": "<", ">=": "≥", "<=": "≤"}


def sol_text(sol) -> str:
    """sympy 구간 -> 'x ≤ 25/4' 같은 글."""
    lo, hi = sol.inf, sol.sup
    def q(v):
        v = sp.Rational(v)
        return str(v.p) if v.q == 1 else f"{v.p}/{v.q}"
    if lo == -sp.oo:
        return f"x {'<' if sol.right_open else '≤'} {q(hi)}"
    return f"x {'>' if sol.left_open else '≥'} {q(lo)}"


def holds(lhs, rel, rhs):
    return {">": lhs > rhs, "<": lhs < rhs, ">=": lhs >= rhs, "<=": lhs <= rhs}[rel]


sent = [  # 부등식으로 나타내기 (문장 -> 식), 정답 하나 + 흔한 실수
    ("어떤 수 $x$의 3배에서 5를 뺀 값은 10보다 작지 않다.", "$3x - 5 \\geq 10$", ["$3x - 5 > 10$", "$3(x - 5) \\geq 10$", "$3x - 5 \\leq 10$", "$5 - 3x \\geq 10$"]),
    ("한 개에 700원인 빵 $x$개와 1500원인 우유 한 개의 값은 6000원 이하이다.", "$700x + 1500 \\leq 6000$", ["$700x + 1500 < 6000$", "$700 + 1500x \\leq 6000$", "$700x + 1500 \\geq 6000$", "$(700 + 1500)x \\leq 6000$"]),
]
for s, right, wr in sent:
    ch, ans = mc(right, wr)
    add(U, "부등식으로 나타내기", "쉬움", f"다음 문장을 부등식으로 나타내면? \"{s}\"", ans, choices=ch,
        hint="크지 않다 = 이하(≤), 작지 않다 = 이상(≥), 미만은 <, 초과는 >",
        explanation=f"문장을 순서대로 식으로 옮기면 {right.strip('$')}".replace("\\geq", "≥").replace("\\leq", "≤"),
        steps=["작지 않다 = [[이상]]", "크지 않다 = [[이하]]"])

for xv in (3, -2):  # 부등식의 해
    cand = []
    while len(cand) < 12:
        c1, c0, rhs = nz(-4, 4), rng.randint(-6, 6), rng.randint(-8, 8)
        rel = rng.choice(list(REL))
        cand.append((f"${lin(c1, 0, c0)} {REL[rel]} {rhs}$", holds(c1 * xv + c0, rel, rhs)))
    tr = [c for c in cand if c[1]]
    fl = [c for c in cand if not c[1]]
    if not tr or len(fl) < 4:
        continue
    ch, ans = pick([tr[0]] + fl[:4], True)
    add(U, "부등식의 해", "쉬움", f"다음 부등식 중 $x = {xv}$가 해인 것은?", ans, choices=ch,
        hint=f"x 자리에 {xv}를 넣어서 부등식이 참이 되는지 하나씩 확인해요.",
        explanation=f"x = {xv}를 대입했을 때 참이 되는 것은 하나뿐이에요.",
        steps=[f"x = [[{xv}]]를 각 부등식에 대입", "참이 되면 [[해]]"])

for want in (True, False):  # 부등식의 성질 (a > b 일 때)
    st = []
    k = rng.choice([2, 3, 4, 5])
    st += [(f"${k}a > {k}b$", True), (f"$-{k}a < -{k}b$", True), (f"$a - {k} > b - {k}$", True), (f"$\\frac{{a}}{{{k}}} > \\frac{{b}}{{{k}}}$", True),
           (f"${k} - a < {k} - b$", True), (f"$-\\frac{{a}}{{{k}}} > -\\frac{{b}}{{{k}}}$", False), (f"$-{k}a + 1 > -{k}b + 1$", False),
           (f"$a + {k} < b + {k}$", False), (f"${k} - 2a > {k} - 2b$", False)]
    # 진위 확인 (a = 3, b = 1 로 대입해 검산)
    tr = [s for s in st if s[1] == want]; fl = [s for s in st if s[1] != want]
    ch, ans = pick([rng.choice(tr)] + rng.sample(fl, 4), want)
    add(U, "부등식의 성질", "보통", f"$a > b$일 때, 다음 중 {'옳은' if want else '옳지 않은'} 것은?", ans, choices=ch,
        hint="양변에 같은 음수를 곱하거나 나누면 부등호 방향이 바뀌어요. 더하고 빼는 것은 그대로예요.",
        explanation="음수를 곱하거나 나눈 식은 부등호 방향이 바뀌고, 나머지는 그대로예요.",
        steps=["음수를 곱하면 부등호 방향이 [[바뀐다]]", "같은 수를 더하면 방향이 [[그대로]]"])
for (lo, lor, hi, hir), (m, c) in [((-3, "<", 2, "<="), (2, -1)), ((-1, "<=", 4, "<"), (-3, 2))]:  # 식의 값의 범위
    v1, v2 = m * lo + c, m * hi + c
    if m > 0:
        right = f"${v1} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v2}$"
        wr = [f"${v1} {REL[hir]} {lin(m, 0, c)} {REL[lor]} {v2}$", f"${v1 - c} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v2 - c}$",
              f"${m * lo} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {m * hi}$", f"${v1 + 1} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v2 + 1}$",
              f"${v1 - 2} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v2 - 2}$", f"${v1} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v2 + 3}$"]
    else:
        right = f"${v2} {REL[hir]} {lin(m, 0, c)} {REL[lor]} {v1}$"
        wr = [f"${v2} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v1}$", f"${v1} {REL[lor]} {lin(m, 0, c)} {REL[hir]} {v2}$",
              f"${-v1} {REL[hir]} {lin(m, 0, c)} {REL[lor]} {-v2}$", f"${v2 + 1} {REL[hir]} {lin(m, 0, c)} {REL[lor]} {v1 + 1}$",
              f"${v2 - 2} {REL[hir]} {lin(m, 0, c)} {REL[lor]} {v1 - 2}$"]
    ch, ans = mc(right, wr)
    add(U, "식의 값의 범위", "보통", f"${lo} {REL[lor]} x {REL[hir]} {hi}$일 때, ${lin(m, 0, c)}$의 값의 범위는?", ans, choices=ch,
        hint="각 변에 같은 수를 곱하고 더해요. 음수를 곱하면 부등호 방향이 모두 바뀌어요!",
        explanation=f"각 변에 {m}을 곱하고 {c}를 더하면 {right.strip('$')}".replace("\\geq", "≥").replace("\\leq", "≤"),
        steps=[f"x = {lo}일 때 {m}x + ({c}) = [[{v1}]]", f"x = {hi}일 때 {m}x + ({c}) = [[{v2}]]"])

st = [("$2x - 1 > 2x + 3$", False), ("$x^{2} + 1 > x^{2} - 3x$", True), ("$3x - 4 \\leq 5$", True), ("$x + 3 = 2x$", False),
      ("$\\frac{1}{x} > 2$", False), ("$5 - x < 4 - x$", False), ("$4x \\geq 2(x - 1)$", True), ("$x^{2} \\leq 2x$", False)]
ch, ans = pick([st[1]] + [s for s in st if not s[1]][:4], True)
add(U, "일차부등식", "보통", "다음 중 일차부등식인 것은?", ans, choices=ch,
    hint="모든 항을 왼쪽으로 옮겨 정리했을 때 (일차식) > 0 꼴이 되는지 봐요.",
    explanation="정리했을 때 x²이 지워지고 일차식만 남으면 일차부등식이에요. x가 없어지거나 등식이면 아니에요.",
    steps=["우변을 [[좌변]]으로 옮겨 정리", "(일차식) > 0 꼴이면 [[일차부등식]]"])

for _ in range(2):  # 일차부등식의 풀이: 해 고르기
    k = rng.randint(-3, 6)
    c1, c2 = nz(-5, 6), nz(-5, 6)
    if c1 == c2:
        continue
    d0 = rng.randint(-8, 8)
    rel = rng.choice(list(REL))
    # c1 x + d0  rel  c2 x + e0, 해가 x ? k
    e0 = (c1 - c2) * k + d0
    coef = c1 - c2
    res_rel = rel if coef > 0 else FLIP[rel]
    right = f"$x {REL[res_rel]} {k}$"
    wr = [f"$x {REL[FLIP[res_rel]]} {k}$", f"$x {REL[res_rel]} {-k}$", f"$x {REL[res_rel]} {k + 2}$", f"$x {REL[FLIP[res_rel]]} {-k}$", f"$x {REL[res_rel]} {k - 1}$"]
    ch, ans = mc(right, wr)
    add(U, "일차부등식의 풀이", "보통", f"일차부등식 ${lin(c1, 0, d0)} {REL[rel]} {lin(c2, 0, e0)}$의 해는?", ans, choices=ch,
        hint="x항은 왼쪽, 수는 오른쪽으로 이항해요. x의 계수가 음수면 나눌 때 부등호 방향이 바뀌어요.",
        explanation=f"이항해서 정리하면 {lin(coef, 0, 0)} {PL[rel]} {e0 - d0}" + (f" → x {PL[res_rel]} {k}" if coef != 1 else ""),
        steps=[f"이항: {lin(coef, 0, 0)} {PL[rel]} [[{e0 - d0}]]", f"x {PL[res_rel]} [[{k}]]"])

for (p1, q1, c1), (p2, q2), rel, ask in [((-2, -3, 9), (4, -3), ">=", "max"), ((5, 1, 0), (2, -2), ">", "min")]:  # 괄호
    # p1(x+q1)+c1 rel p2(x+q2)  ... 예: -(x-2)+11 >= 3(x-4)
    X = sp.Symbol("x")
    L = p1 * (X + q1) + c1
    R = p2 * (X + q2)
    sol = sp.solve_univariate_inequality({">=": L >= R, ">": L > R}[rel], X, relational=False)
    ints = [n for n in range(-30, 31) if sp.Interval(-sp.oo, sp.oo).contains(n) and sol.contains(n)]
    val_ = max(ints) if ask == "max" else min(ints)
    def grp(pp, qq):
        inner = lin(1, 0, qq)
        return ("-" if pp == -1 else "" if pp == 1 else str(pp)) + f"({inner})"
    lhs = grp(p1, q1) + (f" + {c1}" if c1 > 0 else f" - {-c1}" if c1 < 0 else "")
    add(U, "괄호가 있는 일차부등식", "보통",
        f"일차부등식 ${lhs} {REL[rel]} {grp(p2, q2)}$를 만족시키는 {'가장 큰' if ask == 'max' else '가장 작은'} 정수 $x$의 값을 구하시오.", val_,
        hint="분배법칙으로 괄호를 먼저 풀어요.",
        explanation=f"풀면 {sol_text(sol)}, {'가장 큰' if ask == 'max' else '가장 작은'} 정수는 {val_}",
        steps=[f"괄호 풀기: {fmt(sp.expand(L), (X,), False)} {PL[rel]} {fmt(sp.expand(R), (X,), False)}", f"답: [[{val_}]]"])

for txt, L, R, rel, ask, hint in [  # 소수·분수 계수
    ("0.3x - 1.2 \\leq 0.5x + 0.4", sp.Rational(3, 10) * x - sp.Rational(6, 5), sp.Rational(1, 2) * x + sp.Rational(2, 5), "<=", "min", "양변에 10을 곱해 정수 계수로 만들어요."),
    ("\\frac{x - 1}{3} - \\frac{x + 2}{4} > 1", (x - 1) / 3 - (x + 2) / 4, sp.Integer(1), ">", "min", "분모 3과 4의 최소공배수 12를 양변에 곱해요."),
    ("0.2(x - 3) > \\frac{1}{2}x + 0.3", sp.Rational(1, 5) * (x - 3), sp.Rational(1, 2) * x + sp.Rational(3, 10), ">", "max", "소수는 분수로 바꾸고, 분모의 최소공배수를 곱해요."),
]:
    sol = sp.solve_univariate_inequality({">": L > R, "<=": L <= R}[rel], x, relational=False)
    ints = [n for n in range(-60, 61) if sol.contains(n)]
    val_ = min(ints) if ask == "min" else max(ints)
    add(U, "계수가 소수 또는 분수인 일차부등식", "어려움",
        f"일차부등식 ${txt}$를 만족시키는 {'가장 작은' if ask == 'min' else '가장 큰'} 정수 $x$의 값을 구하시오.", val_,
        hint=hint, explanation=f"정리하면 {sol_text(sol)} → {val_}",
        steps=[f"해: {sol_text(sol)}", f"답: [[{val_}]]"])

for c1, rhs, rel, k in [(-2, 7, ">=", -3), (2, 3, "<", 5)]:  # 해가 주어질 때 미지수 (c1 x + a rel rhs)
    # c1*x + a rel rhs → x rel' (rhs - a)/c1 = k → a = rhs - c1*k
    aval = rhs - c1 * k
    res_rel = rel if c1 > 0 else FLIP[rel]
    add(U, "해가 주어질 때 미지수 구하기", "보통",
        f"일차부등식 ${c1}x + a {REL[rel]} {rhs}$의 해가 $x {REL[res_rel]} {k}$일 때, 수 $a$의 값을 구하시오.", aval,
        hint="a를 수처럼 두고 부등식을 푼 뒤, 주어진 해와 비교해요.",
        explanation=f"x {PL[res_rel]} ({rhs} - a)/({c1}) 이므로 ({rhs} - a)/({c1}) = {k} → a = {aval}",
        steps=[f"({rhs} - a) ÷ ({c1}) = {k}", f"a = [[{aval}]]"])

for sign in ("<", ">"):  # 계수가 문자인 일차부등식: a<0 일 때 ax > 3a
    if sign == "<":
        right = "$x < 3$"
        wr = ["$x > 3$", "$x < -3$", "$x > -3$", "$x < \\frac{1}{3}$"]
        q = "$a < 0$일 때, $x$에 대한 일차부등식 $ax > 3a$의 해는?"
        ex = "a < 0 이므로 양변을 a로 나누면 부등호 방향이 바뀌어 x < 3"
    else:
        right = "$x \\geq -\\frac{2}{a}$"
        wr = ["$x \\leq -\\frac{2}{a}$", "$x \\geq \\frac{2}{a}$", "$x \\leq \\frac{2}{a}$", "$x \\geq -2a$"]
        q = "$a > 0$일 때, $x$에 대한 일차부등식 $ax + 5 \\geq 3$의 해는?"
        ex = "ax ≥ -2, a > 0 이므로 방향 그대로 x ≥ -2/a"
    ch, ans = mc(right, wr)
    add(U, "계수가 문자인 일차부등식", "어려움", q, ans, choices=ch,
        hint="문자로 나눌 때 그 문자가 양수인지 음수인지 꼭 확인해요.", explanation=ex,
        steps=["음수로 나누면 부등호 방향이 [[바뀐다]]"])

# 일차부등식의 활용 (수, 최대 개수, 도형, 추가 요금, 예금, 거리·속력·시간, 유리한 방법)
nmax = max(n for n in range(1, 200) if 4 * n + 3 < 2 * n + 15)
add(U, "활용: 수", "보통", "자연수 x를 4배 한 수에 3을 더하면, x를 2배 한 수에 15를 더한 것보다 작다. 이러한 자연수 x 중 가장 큰 수를 구하시오.", nmax,
    hint="부등식을 세워요: 4x + 3 < 2x + 15", explanation=f"4x + 3 < 2x + 15 → 2x < 12 → x < 6 → 가장 큰 자연수 {nmax}",
    steps=["4x + 3 < 2x + 15", "2x < [[12]]", f"가장 큰 자연수: [[{nmax}]]"])
pens = max(k for k in range(0, 21) if 1200 * k + 900 * (20 - k) <= 21000)  # 최대 개수
add(U, "활용: 최대 개수", "보통",
    "한 개에 900원인 공책과 한 개에 1200원인 펜을 합하여 20개를 사려고 한다. 전체 가격을 21000원 이하로 할 때, 펜은 최대 몇 개까지 살 수 있는지 구하시오.",
    pens, hint="펜을 x개라 하면 공책은 (20 - x)개예요.",
    explanation=f"1200x + 900(20 - x) ≤ 21000 → 300x ≤ 3000 → x ≤ 10",
    steps=["1200x + 900(20 - x) ≤ 21000", "300x ≤ [[3000]]", "펜은 최대 [[10]]개"])
assert pens == 10
hmin = min(h for h in range(1, 100) if (5 + 9) * h / 2 >= 56)
add(U, "활용: 도형", "보통", "윗변의 길이가 5 cm, 아랫변의 길이가 9 cm인 사다리꼴의 넓이가 56 cm² 이상이 되게 하려면, 높이는 몇 cm 이상이어야 하는지 구하시오.", hmin,
    hint="사다리꼴의 넓이 = (윗변 + 아랫변) × 높이 ÷ 2", explanation=f"(5 + 9) × h ÷ 2 ≥ 56 → 7h ≥ 56 → h ≥ 8",
    steps=["7h ≥ [[56]]", "h ≥ [[8]]"])
nmin = min(n for n in range(11, 200) if 10 * 2500 + (n - 10) * 1800 > n * 2200)
add(U, "활용: 추가 요금", "어려움",
    "어느 체험관의 입장료는 10명까지는 한 사람에 2500원이고, 10명을 넘으면 넘은 사람은 한 사람에 1800원이다. 한 사람의 평균 입장료가 2200원보다 적어지려면 최소 몇 명이 입장해야 하는지 구하시오.",
    min(n for n in range(11, 200) if 10 * 2500 + (n - 10) * 1800 < n * 2200),
    hint="x명이 입장한다고 하면 전체 요금은 25000 + 1800(x - 10)원이에요.",
    explanation="25000 + 1800(x - 10) < 2200x → 7000 < 400x → x > 17.5 → 18명",
    steps=["25000 + 1800(x - 10) < 2200x", "400x > [[7000]]", "최소 [[18]]명"])
assert min(n for n in range(11, 200) if 10 * 2500 + (n - 10) * 1800 < n * 2200) == 18
mo = min(m for m in range(1, 100) if 30000 + 4000 * m > 2 * (50000 + 1000 * m))
add(U, "활용: 예금액", "어려움",
    "현재 지수의 통장에는 30000원, 동생의 통장에는 50000원이 있다. 다음 달부터 지수는 매달 4000원씩, 동생은 매달 1000원씩 예금한다면, 지수의 예금액이 처음으로 동생의 예금액의 2배보다 많아지는 것은 몇 개월 후인지 구하시오.",
    mo, hint="x개월 후 지수: 30000 + 4000x, 동생: 50000 + 1000x",
    explanation=f"30000 + 4000x > 2(50000 + 1000x) → 2000x > 70000 → x > 35 → {mo}개월 후",
    steps=["30000 + 4000x > 2(50000 + 1000x)", "2000x > [[70000]]", f"[[{mo}]]개월 후"])
dmax = max(F(d, 10) for d in range(1, 200) if F(d, 10) / 2 + F(d, 10) / 3 <= F(5, 2))
add(U, "활용: 거리, 속력, 시간", "어려움",
    "호수 둘레길의 어느 지점까지 갈 때는 시속 2 km로, 돌아올 때는 같은 길을 시속 3 km로 걸어서 2시간 30분 이내로 다녀오려고 한다. 최대 몇 km 떨어진 지점까지 다녀올 수 있는지 구하시오.",
    fpl(dmax), hint="시간 = 거리 ÷ 속력. 2시간 30분 = 5/2시간",
    explanation=f"x/2 + x/3 ≤ 5/2 → 3x + 2x ≤ 15 → x ≤ 3",
    steps=["x/2 + x/3 ≤ [[5/2]]", "5x ≤ [[15]]", f"최대 [[{fpl(dmax)}]] km"])
assert dmax == 3
nn = min(n for n in range(1, 200) if 800 * n * 0.8 + 1500 < 800 * n)
add(U, "활용: 유리한 방법 선택", "어려움",
    "동네 가게에서 한 개에 800원인 아이스크림을 할인 마트에서는 20% 할인된 가격에 판다. 할인 마트에 다녀오는 데 왕복 교통비가 1500원이 든다면, 아이스크림을 최소 몇 개 이상 살 때 할인 마트에서 사는 것이 유리한지 구하시오.",
    nn, hint="x개를 산다고 하면: 동네 가게 800x원, 할인 마트 640x + 1500원",
    explanation=f"640x + 1500 < 800x → 160x > 1500 → x > 9.375 → {nn}개",
    steps=["640x + 1500 < 800x", "160x > [[1500]]", f"최소 [[{nn}]]개"])

# ═════════════════════ 5. 연립일차방정식 ═════════════════════
U = "5. 연립일차방정식"
st = [("$3x - y = 2$", True), ("$x + y^{2} = 4$", False), ("$2x + 3 = x - 1$", False), ("$xy + 2 = 0$", False), ("$\\frac{1}{x} + y = 1$", False),
      ("$x + 2y = 2y - 3$", False), ("$2(x + y) = x + 5$", True)]
ch, ans = pick([st[0]] + [s for s in st if not s[1]][:4], True)
add(U, "미지수가 2개인 일차방정식", "쉬움", "다음 중 미지수가 2개인 일차방정식은?", ans, choices=ch,
    hint="정리했을 때 ax + by + c = 0 (a ≠ 0, b ≠ 0) 꼴인지 봐요.",
    explanation="x와 y가 모두 남고 차수가 1인 등식이어야 해요. y가 지워지거나 제곱·곱·분모에 문자가 있으면 아니에요.",
    steps=["정리하면 ax + by + c = 0, a ≠ 0, [[b ≠ 0]]"])
A1, B1, C1 = 2, -3, 4
cand = [(5, 2), (2, 0), (-1, -2), (1, -1), (3, 1), (-4, -4), (8, 4)]
st = [(f"$({px}, {py})$", A1 * px + B1 * py == C1) for px, py in cand]
fl = [s for s in st if not s[1]]
tr = [s for s in st if s[1]]
ch, ans = pick([fl[0]] + tr[:4], False)
add(U, "일차방정식의 해", "쉬움", f"다음 중 일차방정식 ${lin(A1, B1)} = {C1}$의 해가 아닌 것은?", ans, choices=ch,
    hint="x, y 값을 넣어서 등식이 성립하는지 하나씩 확인해요.",
    explanation="대입해서 좌변이 4가 되지 않는 순서쌍이 해가 아니에요.",
    steps=["(x, y)를 대입해 좌변 = [[4]]인지 확인"])
for A1, B1, C1 in [(1, 3, 17), (2, 3, 24)]:  # 자연수 해 개수
    sols = [(i, j) for i in range(1, 50) for j in range(1, 50) if A1 * i + B1 * j == C1]
    add(U, "자연수인 해 구하기", "보통", f"$x$, $y$의 값이 자연수일 때, 일차방정식 ${lin(A1, B1)} = {C1}$의 해의 개수를 구하시오.", len(sols),
        hint="y에 1, 2, 3, ...을 차례로 넣어 x도 자연수가 되는 것을 찾아요.",
        explanation="해: " + ", ".join(f"({i}, {j})" for i, j in sols),
        steps=[f"해: " + ", ".join(f"({i}, {j})" for i, j in sols), f"개수: [[{len(sols)}]]"])
for (px, py), A1, C1, where in [((4, 2), 3, 10, "y"), ((-3, 2), 5, 4, "x")]:  # 한 해가 주어질 때 계수
    if where == "y":  # 2x - ay = 11
        aval = F(A1 * px - C1, py)
        q = f"일차방정식 ${A1}x - ay = {C1}$의 한 해가 $({px}, {py})$일 때, 수 $a$의 값을 구하시오."
        ex = f"{A1}×{px} - a×{py} = {C1} → a = {fpl(aval)}"
    else:  # ax + 4y = -2
        aval = F(C1 - A1 * py, px)
        q = f"일차방정식 $ax + {A1}y = {C1}$의 한 해가 $({px}, {py})$일 때, 수 $a$의 값을 구하시오."
        ex = f"a×({px}) + {A1}×{py} = {C1} → a = {fpl(aval)}"
    add(U, "해가 주어질 때 계수 구하기", "보통", q, fpl(aval), hint="해를 방정식에 대입하면 등식이 성립해요.", explanation=ex,
        steps=[f"x = {px}, y = {py}를 대입", f"a = [[{fpl(aval)}]]"])

for sx, sy in [(4, -1), (-2, 3)]:  # 연립방정식의 해 고르기
    sys_ = []
    while len(sys_) < 14:
        a1, b1, a2, b2 = nz(-3, 3), nz(-3, 3), nz(-3, 3), nz(-3, 3)
        if a1 * b2 == a2 * b1:
            continue
        c1 = a1 * sx + b1 * sy
        c2 = a2 * sx + b2 * sy + rng.choice([0, 0, 1, -1, 2])
        ok = a2 * sx + b2 * sy == c2
        sys_.append((f"$\\begin{{cases}} {lin(a1, b1)} = {c1} \\\\ {lin(a2, b2)} = {c2} \\end{{cases}}$", ok))
    tr = [s for s in sys_ if s[1]]; fl = [s for s in sys_ if not s[1]]
    if not tr or len(fl) < 4:
        continue
    ch, ans = pick([tr[0]] + fl[:4], True)
    add(U, "연립방정식의 해", "보통", f"다음 연립방정식 중 $x = {sx}$, $y = {sy}$가 해인 것은?", ans, choices=ch,
        hint="두 식 모두에 대입해서 둘 다 성립해야 해요.",
        explanation="두 방정식을 동시에 만족시키는 것을 찾아요.",
        steps=["두 식 [[모두]] 성립해야 해"])

for (sx, sy), (a1, b1), (a2, b2) in [((3, -1), (2, 1), (1, 1)), ((2, 1), (3, -1), (1, 2))]:  # 계수가 문자: ax + by 꼴
    # ax + 2y = c1, bx - y = c2 (해가 주어짐) -> a, b
    c1v, c2v = a1 * sx + 2 * sy, b1 * sx - sy
    add(U, "연립방정식의 계수가 문자", "보통",
        f"연립방정식 $\\begin{{cases}} ax + 2y = {c1v} \\\\ bx - y = {c2v} \\end{{cases}}$의 해가 $x = {sx}$, $y = {sy}$일 때, 수 $a$, $b$에 대하여 $a + b$의 값을 구하시오.",
        a1 + b1, hint="해를 두 식에 각각 대입해요.",
        explanation=f"{sx}a + ({2 * sy}) = {c1v} → a = {a1}, {sx}b - ({sy}) = {c2v} → b = {b1}",
        steps=[f"a = [[{a1}]]", f"b = [[{b1}]]", f"a + b = [[{a1 + b1}]]"])
for m in (2, 3):  # 해가 문자
    # 2x + y = 7 이고 해가 (m, b)  ->  b ; 다른 식 ax - y = 1 -> a
    bval = F(7 - 2 * m)
    aval = F(1 + bval, m)
    add(U, "연립방정식의 해가 문자", "어려움",
        f"연립방정식 $\\begin{{cases}} 2x + y = 7 \\\\ ax - y = 1 \\end{{cases}}$의 해가 $x = {m}$, $y = b$일 때, 수 $a$, $b$에 대하여 $ab$의 값을 구하시오.",
        fpl(aval * bval), hint="문자가 없는 식에 먼저 대입해서 b를 구해요.",
        explanation=f"{2 * m} + b = 7 → b = {fpl(bval)}, {m}a - {fpl(bval)} = 1 → a = {fpl(aval)}",
        steps=[f"b = [[{fpl(bval)}]]", f"a = [[{fpl(aval)}]]", f"ab = [[{fpl(aval * bval)}]]"])

def sys_add(level, concept, txt, sx, sy, hint, exp):
    add(U, concept, level, f"{'방정식' if concept.startswith('A = B') else '연립방정식'} ${txt}$를 푸시오. (예: x=1, y=2)", f"x={sx}, y={sy}", hint=hint, explanation=exp,
        steps=[f"x = [[{sx}]]", f"y = [[{sy}]]"])


for sx, sy in [(2, -3), (-1, 4)]:  # 대입법
    # y = 2x + k, 3x + 2y = c
    k = sy - 2 * sx
    c = 3 * sx + 2 * sy
    sys_add("보통", "연립방정식의 풀이 (대입법)", f"\\begin{{cases}} y = {fx(2, k)} \\\\ 3x + 2y = {c} \\end{{cases}}", sx, sy,
            "위의 식을 아래 식의 y에 그대로 넣어요.", f"3x + 2({fx(2, k).replace('$', '')}) = {c} → x = {sx}, y = {sy}")
for sx, sy, (a1, b1, a2, b2) in [(3, 1, (2, 3, 3, -2)), (-2, 5, (4, 1, 3, 2)), (1, -2, (5, -3, 2, 4))]:  # 가감법
    sys_add("보통", "연립방정식의 풀이 (가감법)",
            f"\\begin{{cases}} {lin(a1, b1)} = {a1 * sx + b1 * sy} \\\\ {lin(a2, b2)} = {a2 * sx + b2 * sy} \\end{{cases}}", sx, sy,
            "없앨 문자의 계수의 절댓값을 같게 만든 뒤 더하거나 빼요.", f"가감법으로 풀면 x = {sx}, y = {sy}")
for sx, sy in [(4, -2), (6, 3)]:  # 소수·분수 계수
    if sy < 0:
        v1 = F(3 * sx + 2 * sy, 10)
        txt = f"\\begin{{cases}} 0.3x + 0.2y = {float(v1):g} \\\\ \\frac{{x}}{{2}} - \\frac{{y}}{{4}} = {ftex(F(sx, 2) - F(sy, 4))} \\end{{cases}}"
    else:
        v1 = F(sx, 3) + F(sy, 2)
        txt = f"\\begin{{cases}} \\frac{{x}}{{3}} + \\frac{{y}}{{2}} = {ftex(v1)} \\\\ 0.1x - 0.4y = {float(F(sx, 10) - F(4 * sy, 10)):g} \\end{{cases}}"
    sys_add("어려움", "계수가 소수 또는 분수인 연립방정식", txt, sx, sy,
            "소수는 10, 100을, 분수는 분모의 최소공배수를 곱해 정수 계수로 바꿔요.", f"정수 계수로 바꿔 풀면 x = {sx}, y = {sy}")
sx, sy = 2, -1  # A = B = C 꼴
v = 2 * sx + sy + 3
assert v == 3 * sx - 2 * sy - 2 == 6
sys_add("어려움", "A = B = C 꼴의 방정식", f"2x + y + 3 = 3x - 2y - 2 = 6", sx, sy,
        "A = C, B = C 두 식을 연립해요.", "2x + y + 3 = 6, 3x - 2y - 2 = 6 을 풀면 x = 2, y = -1")
sx, sy = 3, -2  # 두 연립방정식의 해가 같을 때
# {x + y = 1, ax - y = 8} 과 {2x - y = 8, x + by = 7}
assert sx + sy == 1 and 2 * sx - sy == 8
aval = F(8 + sy, sx); bval = F(7 - sx, sy)
add(U, "두 연립방정식의 해가 같은 경우", "어려움",
    "두 연립방정식 $\\begin{cases} x + y = 1 \\\\ ax - y = 8 \\end{cases}$, $\\begin{cases} 2x - y = 8 \\\\ x + by = 7 \\end{cases}$의 해가 서로 같을 때, 수 $a$, $b$에 대하여 $a - b$의 값을 구하시오.",
    fpl(aval - bval), hint="문자가 없는 두 식 x + y = 1, 2x - y = 8 을 먼저 연립해요.",
    explanation=f"x = {sx}, y = {sy} → a = {fpl(aval)}, b = {fpl(bval)}",
    steps=[f"x = [[{sx}]], y = [[{sy}]]", f"a = [[{fpl(aval)}]]", f"b = [[{fpl(bval)}]]", f"a - b = [[{fpl(aval - bval)}]]"])

# 연립방정식의 활용
s_ = [(i, 36 - i) for i in range(1, 36) if 36 - i == 3 * i + 4][0]
add(U, "활용: 수", "보통", "합이 36인 두 자연수가 있다. 큰 수는 작은 수의 3배보다 4만큼 클 때, 큰 수를 구하시오.", s_[1],
    hint="작은 수 x, 큰 수 y로 놓고 두 식을 세워요.", explanation="x + y = 36, y = 3x + 4 → x = 8, y = 28",
    steps=["x + y = 36, y = 3x + [[4]]", "x = [[8]]", "큰 수 = [[28]]"])
two = [10 * t_ + u for t_ in range(1, 10) for u in range(10) if t_ + u == 11 and 10 * u + t_ == 10 * t_ + u + 27][0]
add(U, "활용: 자리 숫자", "어려움",
    "각 자리 숫자의 합이 11인 두 자리 자연수가 있다. 십의 자리 숫자와 일의 자리 숫자를 바꾼 수는 처음 수보다 27만큼 클 때, 처음 수를 구하시오.", two,
    hint="십의 자리 x, 일의 자리 y이면 처음 수는 10x + y예요.",
    explanation="x + y = 11, 10y + x = 10x + y + 27 → x = 4, y = 7 → 47",
    steps=["x + y = [[11]]", "y - x = [[3]]", f"처음 수: [[{two}]]"])
qs = [(i, 25 - i) for i in range(26) if 3 * i + 5 * (25 - i) == 95][0]
add(U, "활용: 개수와 점수", "보통", "어느 시험에서 3점짜리와 5점짜리 문제를 합하여 25개를 모두 맞혀 95점을 받았다. 5점짜리 문제는 몇 개인지 구하시오.", qs[1],
    hint="3점짜리 x개, 5점짜리 y개로 놓아요.", explanation="x + y = 25, 3x + 5y = 95 → x = 15, y = 10",
    steps=["x + y = 25", "3x + 5y = [[95]]", "5점짜리: [[10]]개"])
ag = [(m, c) for m in range(1, 100) for c in range(1, 100) if m == 4 * c and m + 8 == 2 * (c + 8) + 4][0]
add(U, "활용: 나이", "보통", "현재 삼촌의 나이는 조카의 나이의 4배이고, 8년 후에는 삼촌의 나이가 조카의 나이의 2배보다 4살 많아진다. 현재 조카의 나이를 구하시오.", ag[1],
    hint="현재 삼촌 x살, 조카 y살. 8년 후에는 둘 다 8살씩 많아져요.", explanation=f"x = 4y, x + 8 = 2(y + 8) + 4 → y = {ag[1]}, x = {ag[0]}",
    steps=["x = 4y", "x + 8 = 2(y + 8) + [[4]]", f"조카: [[{ag[1]}]]살"])
rc = [(w, h) for w in range(1, 60) for h in range(1, 60) if 2 * (w + h) == 46 and w == 2 * h - 1][0]
add(U, "활용: 도형", "보통", "둘레의 길이가 46 cm인 직사각형이 있다. 가로의 길이가 세로의 길이의 2배보다 1 cm 짧을 때, 이 직사각형의 넓이를 구하시오.", rc[0] * rc[1],
    hint="가로 x, 세로 y로 놓고 둘레와 길이 관계로 두 식을 세워요.", explanation=f"2(x + y) = 46, x = 2y - 1 → x = {rc[0]}, y = {rc[1]} → 넓이 {rc[0] * rc[1]}",
    steps=["x + y = [[23]]", "x = 2y - 1", f"세로 = [[{rc[1]}]]", f"넓이 = [[{rc[0] * rc[1]}]]"])
up = [u for u in range(1, 20) if F(u, 3) + F(u + 1, 4) == 2][0]
add(U, "활용: 거리, 속력, 시간", "어려움",
    "도서관에 갈 때는 시속 3 km로 걷고, 올 때는 갈 때보다 1 km 더 먼 길을 시속 4 km로 걸었더니 모두 2시간이 걸렸다. 갈 때 걸은 거리는 몇 km인지 구하시오.",
    up, hint="갈 때 x km, 올 때 y km. 시간 = 거리 ÷ 속력",
    explanation=f"y = x + 1, x/3 + y/4 = 2 → x = {up}",
    steps=["y = x + [[1]]", "x/3 + y/4 = [[2]]", f"갈 때 거리: [[{up}]] km"])
gw = [(w, l) for w in range(0, 21) for l in range(0, 21) if w + l == 15 and 2 * w - l == 12][0]  # 계단 (비기는 경우 없음)
add(U, "활용: 계단 오르기", "어려움",
    "동전 던지기 놀이에서 앞면이 나오면 2계단 올라가고, 뒷면이 나오면 1계단 내려가기로 했다. 동전을 15번 던진 뒤 처음 위치보다 12계단 위에 있었다. 앞면은 몇 번 나왔는지 구하시오.", gw[0],
    hint="앞면 x번, 뒷면 y번으로 놓아요.", explanation=f"x + y = 15, 2x - y = 12 → x = {gw[0]}, y = {gw[1]}",
    steps=["x + y = [[15]]", "2x - y = [[12]]", f"앞면: [[{gw[0]}]]번"])

# ═════════════════════ 6. 일차함수와 그 그래프 ═════════════════════
U = "6. 일차함수와 그 그래프"
st = [("자연수 $x$보다 작은 짝수 $y$", False), ("자연수 $x$의 약수 $y$", False), ("자연수 $x$를 3으로 나눈 나머지 $y$", True),
      ("한 변의 길이가 $x$ cm인 정사각형의 둘레 $y$ cm", True), ("절댓값이 $x$인 수 $y$", False), ("$x$보다 큰 자연수 $y$", False)]
ch, ans = pick([s for s in st if s[1]][:1] + [s for s in st if not s[1]][:4], True)
add(U, "함수의 뜻", "보통", "다음 중 $y$가 $x$의 함수인 것은?", ans, choices=ch,
    hint="x 하나에 y가 꼭 하나씩만 정해지면 함수예요. 두 개 이상 나오거나 없으면 함수가 아니에요.",
    explanation="x의 값 하나에 y의 값이 오직 하나씩 대응하는지 x = 4 같은 수를 넣어 확인해요.",
    steps=["x 하나에 y가 [[하나]]씩 대응하면 함수"])
for k_, x1, x2 in [(-12, 3, -4), (18, 2, -6)]:  # 함숫값 f(x) = k/x
    v = F(k_, x1) + F(k_, x2)
    ftx = f"\\frac{{{k_}}}{{x}}" if k_ > 0 else f"-\\frac{{{-k_}}}{{x}}"
    add(U, "함숫값", "쉬움", f"함수 $f(x) = {ftx}$에 대하여 $f({x1}) + f({x2})$의 값을 구하시오.",
        fpl(v), hint="x 자리에 수를 넣어요.", explanation=f"f({x1}) = {fpl(F(k_, x1))}, f({x2}) = {fpl(F(k_, x2))} → 합 {fpl(v)}",
        steps=[f"f({x1}) = [[{fpl(F(k_, x1))}]]", f"f({x2}) = [[{fpl(F(k_, x2))}]]", f"합: [[{fpl(v)}]]"])
add(U, "함숫값으로 미지수 구하기", "보통", "함수 $f(x) = \\frac{a}{x}$에 대하여 $f(-4) = 6$일 때, $f(3)$의 값을 구하시오.", fpl(F(-24, 3)),
    hint="먼저 f(-4) = 6 에서 a를 구해요.", explanation="a/(-4) = 6 → a = -24, f(3) = -24/3 = -8",
    steps=["a = [[-24]]", "f(3) = [[-8]]"])
st = [("$y = 3x - 1$", True), ("$y = \\frac{2}{x}$", False), ("$y = x^{2} + 1$", False), ("$y = 5$", False), ("$y = x(x - 2) - x^{2}$", True), ("$y = 2(x + 1) - 2x$", False)]
ch, ans = pick([st[4]] + [s for s in st if not s[1]][:4], True)
add(U, "일차함수의 뜻", "보통", "다음 중 $y$가 $x$에 대한 일차함수인 것은?", ans, choices=ch,
    hint="정리해서 y = ax + b (a ≠ 0) 꼴이면 일차함수예요. 괄호는 먼저 풀어 봐요.",
    explanation="x(x - 2) - x² = -2x 이므로 y = -2x 로 일차함수예요.",
    steps=["정리해서 y = ax + b, [[a ≠ 0]]"])
for m, c, target in [(3, -4, 11), (-2, 7, 15)]:  # 일차함수 함숫값으로 a 구하기
    av = F(target - c, m)
    add(U, "일차함수의 함숫값", "쉬움", f"일차함수 $f(x) = {fx(m, c)}$에 대하여 $f(a) = {target}$일 때, $a$의 값을 구하시오.", fpl(av),
        hint="x 자리에 a를 넣고 방정식을 풀어요.", explanation=f"{m}a {'+' if c >= 0 else '-'} {abs(c)} = {target} → a = {fpl(av)}",
        steps=[f"{m}a = [[{target - c}]]", f"a = [[{fpl(av)}]]"])
m0 = 3  # 평행이동
right = f"$y = {fx(m0, -4)}$"
ch, ans = mc(right, [f"$y = {fx(-m0, -4)}$", f"$y = {fx(m0, 4)}$", f"$y = {fx(-4, m0)}$", f"$y = {fx(m0 - 4, 0)}$"])
add(U, "일차함수의 그래프의 평행이동", "쉬움", f"일차함수 $y = {fx(m0, 0)}$의 그래프를 $y$축의 방향으로 $-4$만큼 평행이동한 그래프의 식은?", ans, choices=ch,
    hint="y축 방향으로 b만큼 평행이동하면 y = ax + b", explanation="y = 3x 에 -4 → y = 3x - 4",
    steps=["더하는 수: [[-4]]"])
for m, c, sh, px in [(5, -2, 4, 1), (-3, 1, -2, -2)]:  # 평행이동한 그래프 위의 점
    yy = m * px + c + sh
    add(U, "평행이동한 그래프 위의 점", "보통",
        f"일차함수 $y = {fx(m, c)}$의 그래프를 $y$축의 방향으로 ${sh}$만큼 평행이동한 그래프가 점 $({px}, k)$를 지날 때, $k$의 값을 구하시오.", yy,
        hint="먼저 평행이동한 식을 구한 뒤 x에 대입해요.", explanation=f"y = {fx(m, c + sh)}, x = {px} → k = {yy}",
        steps=[f"평행이동한 식: y = {fx(m, c + sh)}", f"k = [[{yy}]]"])
for m, c in [(F(2, 3), -4), (-3, 6)]:  # x절편, y절편
    xi = F(-c) / m
    add(U, "x절편과 y절편", "쉬움", f"일차함수 $y = {fx(m, c)}$의 그래프의 $x$절편을 $p$, $y$절편을 $q$라 할 때, $p + q$의 값을 구하시오.", fpl(xi + c),
        hint="x절편: y = 0일 때 x의 값, y절편: x = 0일 때 y의 값", explanation=f"p = {fpl(xi)}, q = {c} → p + q = {fpl(xi + c)}",
        steps=[f"x절편 p = [[{fpl(xi)}]]", f"y절편 q = [[{c}]]", f"p + q = [[{fpl(xi + c)}]]"])
kval = F(-6) / F(-1, 2)  # y = -1/2 x + k 의 x절편이 ... 다른 함수의 y절편과 같을 때
add(U, "절편으로 미지수 구하기", "보통",
    "일차함수 $y = -\\frac{1}{2}x + k$의 그래프의 $x$절편이 $-6$일 때, 수 $k$의 값을 구하시오.", fpl(F(-3)),
    hint="x절편이 -6이면 점 (-6, 0)을 지나요.", explanation="0 = -1/2 × (-6) + k → k = -3",
    steps=["0 = 3 + k", "k = [[-3]]"])
for m in (F(3, 4), F(-5, 2)):  # 기울기 (증가량)
    dx = 8 if m.denominator == 4 else 4
    dy = m * dx
    add(U, "일차함수의 그래프의 기울기", "쉬움",
        f"일차함수 $y = {fx(m, -1)}$의 그래프에서 $x$의 값이 {dx}만큼 증가할 때, $y$의 값의 증가량을 구하시오.", fpl(dy),
        hint="(y의 값의 증가량) = (기울기) × (x의 값의 증가량)", explanation=f"{fpl(m)} × {dx} = {fpl(dy)}",
        steps=[f"기울기 = [[{fpl(m)}]]", f"증가량 = [[{fpl(dy)}]]"])
for (x1, y1), (x2, y2), sl, ask in [((4, None), (-1, 9), -2, "k"), ((1, 6), (-2, None), 3, "a")]:  # 두 점과 기울기
    if y1 is None:
        y1v = y2 + sl * (x1 - x2)
        q = f"두 점 $({x1}, k)$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 기울기가 ${sl}$일 때, $k$의 값을 구하시오."
        ans_ = y1v
    else:
        y2v = y1 + sl * (x2 - x1)
        q = f"두 점 $({x1}, {y1})$, $({x2}, a)$를 지나는 일차함수의 그래프의 기울기가 ${sl}$일 때, $a$의 값을 구하시오."
        ans_ = y2v
    add(U, "두 점을 지나는 직선의 기울기", "보통", q, ans_,
        hint="기울기 = (y의 값의 차) ÷ (x의 값의 차)", explanation=f"기울기 식을 세워 풀면 {ans_}",
        steps=[f"답: [[{ans_}]]"])
for m, c in [(F(-1, 3), -4), (F(2, 3), 4)]:  # 삼각형 넓이
    xi = F(-c) / m
    S = abs(xi) * abs(c) / 2
    add(U, "그래프와 좌표축으로 둘러싸인 삼각형", "보통",
        f"일차함수 $y = {fx(m, c)}$의 그래프와 $x$축 및 $y$축으로 둘러싸인 삼각형의 넓이를 구하시오.", fpl(S),
        hint="x절편과 y절편을 구하면 직각삼각형의 두 변 길이가 나와요.", explanation=f"x절편 {fpl(xi)}, y절편 {c} → 넓이 {abs(xi)} × {abs(c)} ÷ 2 = {fpl(S)}",
        steps=[f"x절편 = [[{fpl(xi)}]]", f"y절편 = [[{c}]]", f"넓이 = [[{fpl(S)}]]"])
P1, P2, xk = (-3, 8), (1, 0), 4  # 세 점이 한 직선 위
sl = F(P2[1] - P1[1], P2[0] - P1[0])
yk = P2[1] + sl * (xk - P2[0])
add(U, "세 점이 한 직선 위에 있을 조건", "어려움",
    f"세 점 $({P1[0]}, {P1[1]})$, $({P2[0]}, {P2[1]})$, $({xk}, k)$가 한 직선 위에 있을 때, $k$의 값을 구하시오.", fpl(yk),
    hint="어느 두 점을 골라도 기울기가 같아요.", explanation=f"기울기 {fpl(sl)} → k = {P2[1]} + ({fpl(sl)}) × {xk - P2[0]} = {fpl(yk)}",
    steps=[f"기울기 = [[{fpl(sl)}]]", f"k = [[{fpl(yk)}]]"])
m, c = F(2, 3), -4  # 그래프의 성질
st = [("오른쪽 위로 향하는 직선이다.", True), (f"$x$절편은 6이다.", True), ("$y$절편은 $-4$이다.", True), (f"$y = \\frac{{2}}{{3}}x$의 그래프와 평행하다.", True),
      ("제2사분면을 지난다.", False), ("$x$의 값이 3만큼 증가할 때 $y$의 값은 2만큼 감소한다.", False), ("점 $(3, 2)$를 지난다.", False)]
assert F(6) * m + c == 0 and m * 3 + c != 2
ch, ans = pick([rng.choice([s for s in st if not s[1]])] + [s for s in st if s[1]], False)
add(U, "일차함수의 그래프의 성질", "보통", "일차함수 $y = \\frac{2}{3}x - 4$의 그래프에 대한 설명으로 옳지 않은 것은?", ans, choices=ch,
    hint="기울기가 양수면 오른쪽 위로, y절편이 음수면 y축 아래에서 만나요.",
    explanation="기울기 2/3 > 0, y절편 -4 → 제1, 3, 4사분면을 지나요. x가 3 증가하면 y는 2 증가해요.",
    steps=["기울기 > 0 이면 [[오른쪽 위]]로 향함"])
right = "제2사분면"  # a, b의 부호: a > 0, b < 0 → y = ax + b 는 제2사분면을 지나지 않음
ch, ans = mc("$a > 0$, $b < 0$", ["$a > 0$, $b > 0$", "$a < 0$, $b > 0$", "$a < 0$, $b < 0$", "$a = 0$, $b < 0$"])
add(U, "y = ax + b의 그래프와 a, b의 부호", "어려움", "일차함수 $y = ax + b$의 그래프가 제1, 3, 4사분면을 지날 때, $a$, $b$의 부호는?", ans, choices=ch,
    hint="오른쪽 위로 향하면 a > 0, y축과 원점 아래에서 만나면 b < 0",
    explanation="제1, 3, 4사분면을 지나는 직선은 오른쪽 위로 향하고 y절편이 음수예요.",
    steps=["기울기 a [[> 0]]", "y절편 b [[< 0]]"])
add(U, "그래프의 평행과 일치", "보통",
    "두 일차함수 $y = 2ax - 1$, $y = -6x + 3$의 그래프가 평행할 때, 수 $a$의 값을 구하시오.", -3,
    hint="두 직선이 평행하면 기울기가 같고 y절편은 달라요.", explanation="2a = -6 → a = -3",
    steps=["2a = [[-6]]", "a = [[-3]]"])
add(U, "그래프의 평행과 일치", "보통",
    "두 일차함수 $y = -2x + b$와 $y = ax - 7$의 그래프가 일치할 때, 수 $a$, $b$에 대하여 $a + b$의 값을 구하시오.", -9,
    hint="일치하면 기울기도 같고 y절편도 같아요.", explanation="a = -2, b = -7 → a + b = -9",
    steps=["a = [[-2]]", "b = [[-7]]", "a + b = [[-9]]"])


def line_mc(m, c, concept, q, hint, exp):
    right = f"$y = {fx(m, c)}$"
    wr = [f"$y = {fx(-m, c)}$", f"$y = {fx(m, -c)}$" if c else f"$y = {fx(m, 2)}$", f"$y = {fx(c, m)}$" if c and c != m else f"$y = {fx(m, c + 3)}$",
          f"$y = {fx(m, c + 1)}$", f"$y = {fx(m * 2, c)}$"]
    ch, ans = mc(right, wr)
    add(U, concept, "보통", q, ans, choices=ch, hint=hint, explanation=exp, steps=[f"기울기 = [[{fpl(m)}]]", f"y절편 = [[{fpl(c)}]]"])


line_mc(F(-2), -1, "식 구하기: 기울기와 y절편", "$x$의 값이 3만큼 증가할 때 $y$의 값은 6만큼 감소하고, $y$절편이 $-1$인 직선을 그래프로 하는 일차함수의 식은?",
        "기울기 = (y의 증가량) ÷ (x의 증가량)", "기울기 -6/3 = -2, y절편 -1 → y = -2x - 1")
c_ = 1 - F(-2) * 2  # 기울기 -2, 점 (2, 1)
line_mc(F(-2), c_, "식 구하기: 기울기와 한 점", "$x$의 값이 1에서 4까지 증가할 때 $y$의 값은 6만큼 감소하고, 점 $(2, 1)$을 지나는 직선을 그래프로 하는 일차함수의 식은?",
        "기울기를 먼저 구하고, y = ax + b에 점을 대입해 b를 구해요.", f"기울기 -6/3 = -2, 1 = -2 × 2 + b → b = {fpl(c_)}")
s2 = F(7 - 1, 5 - 2)
line_mc(s2, 1 - s2 * 2, "식 구하기: 두 점", "두 점 $(2, 1)$, $(5, 7)$을 지나는 일차함수의 그래프의 식은?",
        "두 점으로 기울기를 구한 뒤 한 점을 대입해요.", f"기울기 6/3 = 2, 1 = 2 × 2 + b → b = -3")
line_mc(F(-4, 3), 4, "식 구하기: x절편과 y절편",
        "$x$절편이 3, $y$절편이 4인 직선을 그래프로 하는 일차함수의 식은?",
        "두 점 (3, 0), (0, 4)를 지나요. 기울기 = -(y절편)/(x절편)", "기울기 (4 - 0)/(0 - 3) = -4/3, y절편 4")
# 활용
t_ = [s for s in range(0, 200) if 150 - 4 * s == 70][0]
add(U, "활용: 높이·온도", "보통", "지면에서 150 m 높이에 떠 있던 열기구가 1초에 4 m씩 일정하게 내려온다. 내려오기 시작한 지 몇 초 후에 지면에서 높이가 70 m가 되는지 구하시오.", t_,
    hint="x초 후 높이를 y m라 하면 y = 150 - 4x", explanation=f"70 = 150 - 4x → x = {t_}", steps=["y = 150 - [[4]]x", f"x = [[{t_}]]"])
w_ = 30 + F(6, 4) * 20
add(U, "활용: 물의 양", "보통", "물이 30 L 들어 있는 수조에 4분에 6 L씩 일정하게 물을 더 넣는다. 20분 후 수조에 들어 있는 물의 양은 몇 L인지 구하시오.", fpl(w_),
    hint="1분에 6/4 = 3/2 L씩 늘어나요.", explanation=f"y = 30 + 3/2 x, x = 20 → y = {fpl(w_)}", steps=["1분에 [[3/2]] L", f"y = [[{fpl(w_)}]]"])
rem = 240 - 80 * F(3, 2)
add(U, "활용: 거리, 속력, 시간", "보통", "240 km 떨어진 할머니 댁까지 기차를 타고 시속 80 km로 갈 때, 출발한 지 1시간 30분 후 할머니 댁까지 남은 거리는 몇 km인지 구하시오.", fpl(rem),
    hint="x시간 후 남은 거리 y = 240 - 80x", explanation=f"y = 240 - 80 × 3/2 = {fpl(rem)}", steps=["1시간 30분 = [[3/2]]시간", f"y = [[{fpl(rem)}]]"])
tt = F(30 * 2, 8)
add(U, "활용: 도형", "어려움",
    "가로 10 cm, 세로 8 cm인 직사각형 ABCD에서 점 P가 꼭짓점 B를 출발하여 변 BC 위를 C 쪽으로 1초에 1 cm씩 움직인다. 삼각형 ABP의 넓이가 30 cm²가 되는 것은 출발한 지 몇 초 후인지 구하시오. (단, AB = 8 cm, BC = 10 cm)",
    fpl(tt), hint="x초 후 BP = x cm, 삼각형 ABP의 넓이 y = 8 × x ÷ 2 = 4x",
    explanation=f"4x = 30 → x = {fpl(tt)}초", steps=["y = [[4]]x", f"x = [[{fpl(tt)}]]"])

# ═════════════════════ 7. 일차함수와 일차방정식의 관계 ═════════════════════
U = "7. 일차함수와 일차방정식"
right = "$y = \\frac{2}{3}x - 2$"
ch, ans = mc(right, ["$y = -\\frac{2}{3}x - 2$", "$y = \\frac{2}{3}x + 2$", "$y = \\frac{3}{2}x - 2$", "$y = 2x - 6$"])
add(U, "일차함수와 일차방정식의 관계", "쉬움", "일차방정식 $2x - 3y - 6 = 0$의 그래프와 같은 직선을 그래프로 하는 일차함수는?", ans, choices=ch,
    hint="y에 대하여 풀어요: y = ... 꼴로", explanation="-3y = -2x + 6 → y = 2/3 x - 2", steps=["-3y = -2x + [[6]]", "y = [[2/3]]x - 2"])
for (A1, B1, C1), (px, py), ask in [((4, 1, -5), (2, None), "y"), ((2, None, -6), (-1, 4), "a")]:  # 그래프 위의 점
    if ask == "y":
        val_ = -(A1 * px + C1) // B1
        add(U, "일차방정식의 그래프 위의 점", "쉬움", f"점 $({px}, k)$가 일차방정식 $4x + y - 5 = 0$의 그래프 위에 있을 때, $k$의 값을 구하시오.", val_,
            hint="점의 좌표를 대입해요.", explanation=f"8 + k - 5 = 0 → k = {val_}", steps=[f"k = [[{val_}]]"])
    else:
        av = F(-(A1 * px + C1), py)  # 2x + ay - 6 = 0 at (-1, 4): -2 + 4a - 6 = 0 → a = 2
        add(U, "일차방정식의 그래프 위의 점", "보통", f"일차방정식 $2x + ay - 6 = 0$의 그래프가 점 $({px}, {py})$를 지날 때, 수 $a$의 값을 구하시오.", fpl(av),
            hint="점의 좌표를 대입해 a에 대한 방정식을 풀어요.", explanation=f"2×({px}) + {py}a - 6 = 0 → a = {fpl(av)}", steps=[f"a = [[{fpl(av)}]]"])
assert -3 * (-1) + (-3) == 0  # 3x + y = 2 와 평행 (기울기 -3), x절편 -1 -> y = -3x - 3
ch, ans = mc("$3x + y + 3 = 0$", ["$3x - y + 3 = 0$", "$3x + y - 3 = 0$", "$x + 3y + 3 = 0$", "$3x + y + 1 = 0$"])
add(U, "직선의 방정식 구하기", "보통", "직선 $3x + y = 2$와 평행하고, $x$절편이 $-1$인 직선의 방정식은?", ans, choices=ch,
    hint="평행하면 기울기가 같아요. 기울기 -3, 점 (-1, 0)을 지나요.", explanation="y = -3x + b, 0 = 3 + b → b = -3 → 3x + y + 3 = 0",
    steps=["기울기 = [[-3]]", "y절편 = [[-3]]"])
add(U, "좌표축에 평행한 직선", "쉬움", "점 $(4, -2)$를 지나고 $x$축에 수직인 직선과 점 $(-1, 3)$을 지나고 $y$축에 수직인 직선의 교점의 좌표를 $(p, q)$라 할 때, $p + q$의 값을 구하시오.", 7,
    hint="x축에 수직인 직선은 x = (수), y축에 수직인 직선은 y = (수)", explanation="x = 4, y = 3 → 교점 (4, 3) → p + q = 7",
    steps=["x = [[4]]", "y = [[3]]", "p + q = [[7]]"])
av = [k for k in range(-20, 20) if 3 * k - 2 == k + 4][0]
add(U, "좌표축에 평행한 직선", "보통", "두 점 $(3a - 2, 5)$, $(a + 4, -1)$을 지나는 직선이 $y$축에 평행할 때, $a$의 값을 구하시오.", av,
    hint="y축에 평행한 직선 위의 점은 x좌표가 모두 같아요.", explanation=f"3a - 2 = a + 4 → a = {av}", steps=["3a - 2 = a + [[4]]", f"a = [[{av}]]"])
ch, ans = mc("$a > 0$, $b > 0$", ["$a > 0$, $b < 0$", "$a < 0$, $b > 0$", "$a < 0$, $b < 0$", "$a = 0$, $b > 0$"])
add(U, "ax + by + c = 0의 그래프와 부호", "어려움",
    "일차방정식 $ax + by - 1 = 0$의 그래프가 $x$절편과 $y$절편이 모두 양수인 직선일 때, $a$, $b$의 부호는?", ans, choices=ch,
    hint="x절편 = 1/a, y절편 = 1/b", explanation="1/a > 0, 1/b > 0 → a > 0, b > 0", steps=["x절편 = [[1/a]]", "y절편 = [[1/b]]"])
for (a1, b1, c1), (a2, b2, c2) in [((1, 3, -5), (2, -1, 4)), ((1, -2, 1), (3, 1, -11))]:  # 교점
    X, Y = sp.symbols("X Y")
    so = sp.solve([a1 * X + b1 * Y + c1, a2 * X + b2 * Y + c2], [X, Y])
    xs, ys = so[X], so[Y]
    add(U, "연립방정식의 해와 그래프", "보통",
        f"두 일차방정식 ${lin(a1, b1, c1)} = 0$, ${lin(a2, b2, c2)} = 0$의 그래프의 교점의 좌표를 $(p, q)$라 할 때, $p + q$의 값을 구하시오.", fpl(F(int(sp.fraction(xs + ys)[0]), int(sp.fraction(xs + ys)[1]))),
        hint="두 그래프의 교점 = 연립방정식의 해", explanation=f"연립해서 풀면 x = {xs}, y = {ys}",
        steps=[f"p = [[{xs}]]", f"q = [[{ys}]]", f"p + q = [[{xs + ys}]]"])
X, Y = sp.symbols("X Y")
so = sp.solve([2 * X + Y - 1, X - 3 * Y - 11], [X, Y])  # 교점 (2, -3)
right_c = so[Y] - 3 * so[X]  # 기울기 3
ch, ans = mc(f"$y = {fx(3, F(int(right_c)))}$", [f"$y = {fx(3, F(int(right_c)) + 3)}$", f"$y = {fx(-3, F(int(right_c)))}$", f"$y = {fx(3, -F(int(right_c)))}$", f"$y = {fx(F(1, 3), F(int(right_c)))}$"])
add(U, "두 직선의 교점을 지나는 직선", "어려움",
    "두 직선 $2x + y - 1 = 0$, $x - 3y - 11 = 0$의 교점을 지나고, 기울기가 3인 직선의 방정식은?", ans, choices=ch,
    hint="먼저 연립해서 교점을 구한 뒤 y = 3x + b에 대입해요.", explanation=f"교점 ({so[X]}, {so[Y]}) → b = {right_c}",
    steps=[f"교점: ([[{so[X]}]], [[{so[Y]}]])", f"y절편 = [[{right_c}]]"])
so = sp.solve([X + Y - 4, 2 * X - Y - 2], [X, Y])  # 두 직선과 y축으로 둘러싸인 넓이
S = sp.Rational(1, 2) * (4 - (-2)) * so[X]
add(U, "두 직선과 좌표축으로 둘러싸인 넓이", "어려움",
    "두 직선 $x + y = 4$, $2x - y = 2$와 $y$축으로 둘러싸인 삼각형의 넓이를 구하시오.", fpl(F(int(sp.fraction(S)[0]), int(sp.fraction(S)[1]))),
    hint="두 직선의 y절편(4와 -2)을 밑변으로, 교점의 x좌표를 높이로 봐요.", explanation=f"교점 ({so[X]}, {so[Y]}), 밑변 6, 높이 {so[X]} → 넓이 {S}",
    steps=["밑변 = [[6]]", f"높이 = [[{so[X]}]]", f"넓이 = [[{S}]]"])
so = sp.solve([X - Y + 1, 2 * X + Y - 7], [X, Y])  # 한 점에서 만나는 세 직선
av = F(int(-(3 * so[Y] - 1)), int(so[X])) if so[X] != 0 else None
# ax + 3y - 1 = 0 이 (2, 3) 을 지남: 2a + 9 - 1 = 0 → a = -4
av = F(-(3 * int(so[Y]) - 1), int(so[X]))
add(U, "한 점에서 만나는 세 직선", "어려움",
    "세 직선 $x - y + 1 = 0$, $2x + y - 7 = 0$, $ax + 3y - 1 = 0$이 한 점에서 만날 때, 수 $a$의 값을 구하시오.", fpl(av),
    hint="a가 없는 두 직선의 교점을 먼저 구하고, 세 번째 식에 대입해요.", explanation=f"교점 ({so[X]}, {so[Y]}) → {so[X]}a + {3 * so[Y]} - 1 = 0 → a = {fpl(av)}",
    steps=[f"교점: ([[{so[X]}]], [[{so[Y]}]])", f"a = [[{fpl(av)}]]"])
add(U, "해의 개수: 두 직선이 일치할 때", "어려움",
    "연립방정식 $\\begin{cases} 2x - ay = 4 \\\\ -6x + 9y = b \\end{cases}$의 해가 무수히 많을 때, 수 $a$, $b$에 대하여 $a + b$의 값을 구하시오.", -9,
    hint="해가 무수히 많으면 두 직선이 일치해요: 첫 식에 -3을 곱하면 둘째 식과 같아야 해요.", explanation="-6x + 3ay = -12 와 같아야 하므로 3a = 9 → a = 3, b = -12 → a + b = -9",
    steps=["a = [[3]]", "b = [[-12]]", "a + b = [[-9]]"])
add(U, "해의 개수: 두 직선이 평행할 때", "어려움",
    "연립방정식 $\\begin{cases} 2x - y = 3 \\\\ ax + 2y = 5 \\end{cases}$의 해가 없을 때, 수 $a$의 값을 구하시오.", -4,
    hint="해가 없으면 두 직선이 평행해요: 기울기는 같고 y절편은 달라요.", explanation="y = 2x - 3, y = -a/2 x + 5/2 → -a/2 = 2 → a = -4",
    steps=["첫 직선의 기울기 = [[2]]", "-a/2 = 2 → a = [[-4]]"])

# ───────── 검사: 빈 값, 보기 개수, 중복 ─────────
for u, ps in units.items():
    for q in ps:
        assert q["question"] and q["answer"] not in ("", "None"), q
        if q["type"] == "multiple_choice":
            assert len(q["choices"]) == 5 and len(set(q["choices"])) == 5 and q["answer"] in MARKS, q
        assert "nan" not in q["answer"] and "oo" not in q["answer"], q

pages = [{"page": i + 1, "unit": unit, "problems": probs} for i, (unit, probs) in enumerate(units.items())]
data = {"books": [{"book": TITLE, "title": TITLE, "pages": pages}]}
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
print({u: len(ps) for u, ps in units.items()}, "총", sum(len(ps) for ps in units.values()))
