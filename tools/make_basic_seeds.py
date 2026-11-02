"""기본 씨앗(중2-1 수학) 만들기: app/src/main/assets/seeds_basic.json

문제는 숫자를 바꿔 가며 코드로 만들고, 정답은 파이썬이 분수로 정확히 계산한다 (AI가 쓰지 않음 → 정답이 틀릴 일 없음).
형식은 마클리 문제 은행(/api/bank/export)과 같아서 앱의 SeedImport가 그대로 읽는다.

    python3 tools/make_basic_seeds.py
"""
from __future__ import annotations

import json
import math
import random
from fractions import Fraction
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/seeds_basic.json"
MARKS = "①②③④⑤"
rng = random.Random(20261102)
problems_by_unit: dict[str, list[dict]] = {}
counter = [0]


def add(unit, concept, level, question, answer, *, choices=None, hint="", explanation="", steps=None):
    counter[0] += 1
    p = {
        "bank_id": f"basic2_{counter[0]:03d}",
        "concept": concept,
        "type": "multiple_choice" if choices else "short_answer",
        "level": level,
        "question": question,
        "choices": choices or [],
        "answer": answer,
        "hint": hint,
        "explanation": explanation,
        "steps": steps or [],
    }
    problems_by_unit.setdefault(unit, []).append(p)


def mc(correct: str, wrong: list[str]) -> tuple[list[str], str]:
    """보기 5개를 섞고 정답 원문자를 돌려준다."""
    opts = list(dict.fromkeys([correct] + wrong))[:5]
    assert len(opts) == 5, opts
    rng.shuffle(opts)
    return opts, MARKS[opts.index(correct)]


def tex(f: Fraction) -> str:
    if f.denominator == 1:
        return str(f.numerator)
    sign = "-" if f < 0 else ""
    return f"{sign}\\frac{{{abs(f.numerator)}}}{{{f.denominator}}}"


def plain(f: Fraction) -> str:
    return str(f.numerator) if f.denominator == 1 else f"{f.numerator}/{f.denominator}"


def only_2_5(n: int) -> bool:
    for p in (2, 5):
        while n % p == 0:
            n //= p
    return n == 1


def repetend(num: int, den: int) -> tuple[str, str]:
    """num/den (0<num<den)의 소수: (순환하지 않는 부분, 순환마디)."""
    digits, seen, r = [], {}, num
    while r and r not in seen:
        seen[r] = len(digits)
        r *= 10
        digits.append(str(r // den))
        r %= den
    if not r:
        return "".join(digits), ""
    start = seen[r]
    return "".join(digits[:start]), "".join(digits[start:])


def dotted(pre: str, rep: str) -> str:
    """0.12\\dot{3}\\dot{4} 꼴 (순환마디 처음과 끝에 점)."""
    if len(rep) == 1:
        body = f"\\dot{{{rep}}}"
    else:
        body = f"\\dot{{{rep[0]}}}{rep[1:-1]}\\dot{{{rep[-1]}}}"
    return f"0.{pre}{body}"


# ───────────────────────── 1. 유리수와 순환소수 ─────────────────────────
U1 = "유리수와 순환소수"

for _ in range(4):  # 유한소수로 나타낼 수 있는 분수 고르기
    # 분모에 3·7이 섞여 있어도 분자와 약분되면 유한소수가 되는 함정을 일부러 섞는다
    d0 = rng.choice([8, 20, 25, 40, 50, 16, 80])
    t = rng.choice([1, 3, 7])
    k = rng.choice([k for k in range(1, d0) if math.gcd(k, d0) == 1])
    finite = (k * t, d0 * t)
    wrong = []
    while len(wrong) < 4:
        d = rng.choice([3, 6, 7, 9, 11, 12, 14, 15, 21, 30, 45, 66])
        n = rng.randint(1, d - 1)
        if not only_2_5(Fraction(n, d).denominator):
            wrong.append(f"$\\frac{{{n}}}{{{d}}}$")
    n, d = finite
    opts, ans = mc(f"$\\frac{{{n}}}{{{d}}}$", wrong)
    red = Fraction(n, d)
    add(U1, "유한소수 판별", "보통", "다음 중 유한소수로 나타낼 수 있는 것은?", ans, choices=opts,
        hint="먼저 기약분수로 만든 뒤, 분모의 소인수가 2와 5뿐인지 확인해요.",
        explanation=f"{n}/{d} = {plain(red)} 이고 분모 {red.denominator}의 소인수가 2와 5뿐이라 유한소수예요.",
        steps=[f"{n}/{d}를 기약분수로 하면 [[{plain(red)}]]", f"분모 {red.denominator}의 소인수는 2와 5뿐이므로 [[유한소수]]"])

for den in [7, 11, 27, 37, 13]:  # 순환마디
    num = rng.randint(1, den - 1)
    while math.gcd(num, den) != 1:
        num = rng.randint(1, den - 1)
    pre, rep = repetend(num, den)
    add(U1, "순환마디", "쉬움" if len(rep) <= 2 else "보통",
        f"분수 $\\frac{{{num}}}{{{den}}}$을 소수로 나타낼 때, 순환마디를 구하시오.", rep,
        hint="직접 나눗셈을 해서 같은 나머지가 다시 나올 때까지 계산해요.",
        explanation=f"{num}/{den} = 0.{pre}{rep}{rep}... 이므로 순환마디는 {rep}예요.",
        steps=[f"{num} ÷ {den} = 0.{pre}{rep}{rep[:2]}...", f"되풀이되는 부분: [[{rep}]]"])

for den, num, nth in [(7, 3, 50), (11, 4, 25), (37, 5, 40), (27, 2, 100)]:  # n번째 자리 숫자
    pre, rep = repetend(num, den)
    digit = rep[(nth - 1) % len(rep)]
    add(U1, "소수점 아래 n번째 자리", "어려움",
        f"분수 $\\frac{{{num}}}{{{den}}}$을 소수로 나타낼 때, 소수점 아래 {nth}번째 자리의 숫자를 구하시오.", digit,
        hint="순환마디의 숫자 개수로 자리 번호를 나눈 나머지를 이용해요.",
        explanation=f"순환마디 {rep}({len(rep)}개)에서 {nth}를 {len(rep)}로 나눈 나머지 {(nth - 1) % len(rep) + 1}번째 숫자 {digit}예요.",
        steps=[f"순환마디: {rep} (숫자 [[{len(rep)}]]개)", f"{nth} ÷ {len(rep)}의 나머지는 [[{nth % len(rep)}]]", f"구하는 숫자: [[{digit}]]"])

for num, den in [(5, 11), (4, 9), (7, 33), (13, 99), (1, 6), (7, 12)]:  # 순환소수를 분수로
    pre, rep = repetend(num, den)
    f = Fraction(num, den)
    add(U1, "순환소수를 분수로", "보통" if not pre else "어려움",
        f"순환소수 ${dotted(pre, rep)}$를 기약분수로 나타내시오.", plain(f),
        hint="순환마디 숫자 개수만큼 9를 쓰고(순환하지 않는 자리 수만큼 0을 붙여) 분모로 만들어요.",
        explanation=f"순환소수를 분수로 고친 뒤 약분하면 {plain(f)}예요.",
        steps=[f"분모: 9를 {len(rep)}개" + (f", 0을 {len(pre)}개" if pre else ""),
               f"기약분수로 나타내면 [[{plain(f)}]]"])

# ───────────────────────── 2. 식의 계산 ─────────────────────────
U2 = "식의 계산"
for _ in range(3):  # 지수법칙 곱
    a, m, n = rng.choice([2, 3, 5]), rng.randint(2, 6), rng.randint(2, 6)
    add(U2, "지수법칙", "쉬움", f"${a}^{{{m}}} \\times {a}^{{{n}}} = {a}^{{\\square}}$일 때, $\\square$ 안에 알맞은 수를 구하시오.", str(m + n),
        hint="밑이 같은 거듭제곱의 곱은 지수끼리 더해요.", explanation=f"{a}^{m} × {a}^{n} = {a}^({m}+{n}) = {a}^{m + n}",
        steps=[f"{a}^{m} × {a}^{n} = {a}^({m} + {n})", f"= {a}^[[{m + n}]]"])
for _ in range(3):  # 거듭제곱의 거듭제곱
    m, n = rng.randint(2, 5), rng.randint(2, 4)
    add(U2, "지수법칙", "쉬움", f"$(x^{{{m}}})^{{{n}}} = x^{{\\square}}$일 때, $\\square$ 안에 알맞은 수를 구하시오.", str(m * n),
        hint="거듭제곱의 거듭제곱은 지수끼리 곱해요.", explanation=f"(x^{m})^{n} = x^({m}×{n}) = x^{m * n}",
        steps=[f"(x^{m})^{n} = x^({m} × {n})", f"= x^[[{m * n}]]"])
for _ in range(4):  # 단항식의 곱셈·나눗셈
    a, b, p, q = rng.randint(2, 6), rng.randint(2, 6), rng.randint(1, 4), rng.randint(1, 4)
    if rng.random() < 0.5:
        add(U2, "단항식의 계산", "보통", f"$({a}x^{{{p}}}) \\times ({b}x^{{{q}}})$을 간단히 하면 $kx^{{n}}$입니다. $k + n$의 값을 구하시오.", str(a * b + p + q),
            hint="계수끼리 곱하고, 문자의 지수는 더해요.", explanation=f"{a}x^{p} × {b}x^{q} = {a * b}x^{p + q} 이므로 k + n = {a * b} + {p + q} = {a * b + p + q}",
            steps=[f"계수: {a} × {b} = [[{a * b}]]", f"지수: {p} + {q} = [[{p + q}]]", f"k + n = [[{a * b + p + q}]]"])
    else:
        c = a * b
        add(U2, "단항식의 계산", "보통", f"${c}x^{{{p + q}}} \\div {a}x^{{{p}}}$을 간단히 하면 $kx^{{n}}$입니다. $k + n$의 값을 구하시오.", str(b + q),
            hint="계수끼리 나누고, 문자의 지수는 빼요.", explanation=f"{c}x^{p + q} ÷ {a}x^{p} = {b}x^{q} 이므로 k + n = {b + q}",
            steps=[f"계수: {c} ÷ {a} = [[{b}]]", f"지수: {p + q} - {p} = [[{q}]]", f"k + n = [[{b + q}]]"])
for _ in range(4):  # 다항식의 덧셈·뺄셈
    a, b, c, d = (rng.choice([k for k in range(-6, 7) if k]) for _ in range(4))
    x, y = a - c, b - d

    def poly(px, py):
        s = ("" if px == 1 else "-" if px == -1 else str(px)) + "x" if px else ""
        if py:
            coef = "" if abs(py) == 1 else str(abs(py))
            s += (" + " if py > 0 else " - ") + coef + "y" if s else (("-" if py < 0 else "") + coef + "y")
        return s or "0"
    correct = poly(x, y)
    wrong = [poly(a + c, b + d), poly(x, b + d), poly(a + c, y), poly(-x, -y), poly(x, -y), poly(-x, y)]
    wrong = [w for w in dict.fromkeys(wrong) if w != correct][:4]
    if len(wrong) < 4:
        continue
    opts, ans = mc(f"${correct}$", [f"${w}$" for w in wrong])
    add(U2, "다항식의 덧셈과 뺄셈", "보통", f"$({poly(a, b)}) - ({poly(c, d)})$를 간단히 한 것은?", ans, choices=opts,
        hint="빼는 식의 괄호를 풀 때 각 항의 부호를 바꿔요.", explanation=f"괄호를 풀고 동류항끼리 계산하면 {correct}",
        steps=[f"x의 계수: {a} - ({c}) = [[{x}]]", f"y의 계수: {b} - ({d}) = [[{y}]]"])

# ───────────────────────── 3. 일차부등식 ─────────────────────────
U3 = "일차부등식"
for _ in range(5):
    a = rng.choice([2, 3, 4, 5, -2, -3])
    k = rng.randint(-4, 6)
    b = rng.randint(-9, 9)
    c = a * k + b
    rel = rng.choice([">", "<", "≥", "≤"])
    flip = {">": "<", "<": ">", "≥": "≤", "≤": "≥"}
    res_rel = rel if a > 0 else flip[rel]
    tex_rel = {">": ">", "<": "<", "≥": "\\geq", "≤": "\\leq"}
    lhs = f"{a}x" + (f" + {b}" if b > 0 else f" - {-b}" if b < 0 else "")
    correct = f"$x {tex_rel[res_rel]} {k}$"
    wrong = [f"$x {tex_rel[flip[res_rel]]} {k}$", f"$x {tex_rel[res_rel]} {-k}$", f"$x {tex_rel[res_rel]} {k + 1}$",
             f"$x {tex_rel[flip[res_rel]]} {-k}$", f"$x {tex_rel[res_rel]} {k - 1}$"]
    wrong = [w for w in dict.fromkeys(wrong) if w != correct][:4]
    opts, ans = mc(correct, wrong)
    add(U3, "일차부등식의 풀이", "보통" if a > 0 else "어려움", f"일차부등식 ${lhs} {tex_rel[rel]} {c}$의 해는?", ans, choices=opts,
        hint="상수항을 옮긴 뒤 x의 계수로 나눠요. 음수로 나누면 부등호 방향이 바뀌어요!",
        explanation=f"{a}x {rel} {c - b} 에서 양변을 {a}로 나누면 x {res_rel} {k}" + (" (음수로 나눠서 방향이 바뀜)" if a < 0 else ""),
        steps=[f"{a}x {rel} {c} - ({b}) = [[{c - b}]]", f"양변을 {a}로 나누면 x {res_rel} [[{k}]]"])
for _ in range(2):  # 부등식의 성질
    k = rng.choice([2, 3, 4])
    correct = f"$-{k}a < -{k}b$"
    # 정답은 하나뿐: 나머지는 모두 부등호 방향이 틀린 식
    opts, ans = mc(f"$-{k}a > -{k}b$", [correct, f"$a - {k} > b - {k}$", f"$\\frac{{a}}{{{k}}} > \\frac{{b}}{{{k}}}$", f"${k}a > {k}b$", f"$a + {k} > b + {k}$"])
    add(U3, "부등식의 성질", "쉬움", "$a < b$일 때, 다음 중 옳은 것은?", ans, choices=opts,
        hint="양변에 같은 음수를 곱하면 부등호 방향이 바뀌어요.", explanation=f"a < b의 양변에 -{k}를 곱하면 -{k}a > -{k}b",
        steps=[f"양변에 -{k}를 곱하면 부등호 방향이 [[바뀐다]]"])

# ───────────────────────── 4. 연립일차방정식 ─────────────────────────
U4 = "연립일차방정식"
for _ in range(6):
    x, y = rng.randint(-5, 6), rng.randint(-5, 6)
    a1, b1, a2, b2 = rng.choice([1, 2, 3]), rng.choice([1, 2, -1]), rng.choice([1, 2, -1, 3]), rng.choice([-1, -2, 1, 3])
    if a1 * b2 - a2 * b1 == 0:
        continue
    c1, c2 = a1 * x + b1 * y, a2 * x + b2 * y

    def eq(a, b, c):
        s = ("" if a == 1 else "-" if a == -1 else str(a)) + "x"
        s += (" + " if b > 0 else " - ") + ("" if abs(b) == 1 else str(abs(b))) + "y"
        return f"{s} = {c}"
    add(U4, "연립방정식의 풀이", "보통",
        f"연립방정식 $\\begin{{cases}} {eq(a1, b1, c1)} \\\\ {eq(a2, b2, c2)} \\end{{cases}}$의 해를 구하시오. (예: x=1, y=2)",
        f"x={x}, y={y}",
        hint="한 문자의 계수를 같게 만든 뒤 두 식을 더하거나 빼서 없애요 (가감법).",
        explanation=f"가감법으로 풀면 x = {x}, y = {y}예요. 두 식에 넣어서 확인해 봐요.",
        steps=[f"한 문자를 없애고 풀면 x = [[{x}]]", f"x를 대입하면 y = [[{y}]]"])
for _ in range(2):
    x, y = rng.randint(1, 6), rng.randint(1, 6)
    add(U4, "연립방정식의 풀이", "쉬움", f"연립방정식 $\\begin{{cases}} x + y = {x + y} \\\\ x - y = {x - y} \\end{{cases}}$에서 $x$의 값을 구하시오.", str(x),
        hint="두 식을 더하면 y가 없어져요.", explanation=f"두 식을 더하면 2x = {2 * x}, x = {x}",
        steps=[f"두 식을 더하면 2x = [[{2 * x}]]", f"x = [[{x}]]"])

# ───────────────────────── 5. 일차함수 ─────────────────────────
U5 = "일차함수"
for _ in range(4):  # 두 점을 지나는 직선의 기울기
    x1, y1 = rng.randint(-4, 4), rng.randint(-5, 5)
    x2 = x1 + rng.choice([1, 2, 3, 4])
    y2 = y1 + rng.choice([-6, -4, -3, -2, 2, 3, 4, 6])
    s = Fraction(y2 - y1, x2 - x1)
    add(U5, "기울기", "보통", f"두 점 $({x1}, {y1})$, $({x2}, {y2})$를 지나는 일차함수의 그래프의 기울기를 구하시오.", plain(s),
        hint="기울기 = (y의 값의 증가량) ÷ (x의 값의 증가량)", explanation=f"({y2} - ({y1})) ÷ ({x2} - ({x1})) = {plain(s)}",
        steps=[f"y의 증가량: {y2} - ({y1}) = [[{y2 - y1}]]", f"x의 증가량: {x2} - ({x1}) = [[{x2 - x1}]]", f"기울기 = [[{plain(s)}]]"])
for _ in range(3):  # 함숫값
    a, b, k = rng.choice([2, 3, -2, -3, 4]), rng.randint(-6, 6), rng.randint(-3, 4)
    add(U5, "함숫값", "쉬움", f"일차함수 $f(x) = {a}x {'+' if b >= 0 else '-'} {abs(b)}$에서 $f({k})$의 값을 구하시오.", str(a * k + b),
        hint="x 자리에 그 수를 넣어 계산해요.", explanation=f"f({k}) = {a} × ({k}) {'+' if b >= 0 else '-'} {abs(b)} = {a * k + b}",
        steps=[f"{a} × ({k}) = [[{a * k}]]", f"f({k}) = [[{a * k + b}]]"])
for _ in range(3):  # x절편
    a, b = rng.choice([2, 3, 4, -2, -4]), rng.choice([-8, -6, -4, 4, 6, 8, 12])
    xi = Fraction(-b, a)
    add(U5, "x절편과 y절편", "보통", f"일차함수 $y = {a}x {'+' if b >= 0 else '-'} {abs(b)}$의 그래프의 $x$절편을 구하시오.", plain(xi),
        hint="x절편은 y = 0일 때의 x의 값이에요.", explanation=f"0 = {a}x {'+' if b >= 0 else '-'} {abs(b)} 에서 x = {plain(xi)}",
        steps=[f"y = 0을 대입: {a}x = [[{-b}]]", f"x = [[{plain(xi)}]]"])
for _ in range(2):  # y절편·기울기 고르기
    a, b = rng.choice([2, 3, -2, -3, 5]), rng.choice([-4, -3, -1, 2, 5])
    correct = f"기울기 {a}, $y$절편 {b}"
    wrong = [f"기울기 {b}, $y$절편 {a}", f"기울기 {-a}, $y$절편 {b}", f"기울기 {a}, $y$절편 {-b}", f"기울기 {a}, $y$절편 {plain(Fraction(-b, a))}"]
    opts, ans = mc(correct, wrong)
    add(U5, "x절편과 y절편", "쉬움", f"일차함수 $y = {a}x {'+' if b >= 0 else '-'} {abs(b)}$의 그래프에 대한 설명으로 옳은 것은?", ans, choices=opts,
        hint="y = ax + b에서 a가 기울기, b가 y절편이에요.", explanation=f"y = {a}x + ({b}) 이므로 기울기 {a}, y절편 {b}",
        steps=[f"기울기 = [[{a}]]", f"y절편 = [[{b}]]"])

pages = [{"page": i + 1, "unit": unit, "problems": probs} for i, (unit, probs) in enumerate(problems_by_unit.items())]
data = {"books": [{"book": "기본 씨앗 중2-1", "title": "기본 씨앗 중2-1", "pages": pages}]}
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
print({u: len(p) for u, p in problems_by_unit.items()}, "총", sum(len(p) for p in problems_by_unit.values()))
