"""유형 씨앗 독립 검산: app/src/main/assets/seeds_types.json

만든 코드(make_type_seeds.py)를 쓰지 않고, 화면에 나오는 문제 글(LaTeX)을 직접 읽어 sympy로 다시 풀어서
저장된 정답과 맞는지 본다. 읽을 수 있는 꼴의 문제만 검산하고, 몇 개를 검산했는지 유형별로 알려 준다.

    python3 tools/verify_type_seeds.py
"""
from __future__ import annotations

import json
import re
import sys
from fractions import Fraction as F
from pathlib import Path

import sympy as sp
from sympy.parsing.sympy_parser import (implicit_multiplication_application, parse_expr,
                                        standard_transformations, convert_xor, rationalize)

SRC = Path(__file__).resolve().parent.parent / "app/src/main/assets/seeds_types.json"
MARKS = "①②③④⑤"
x, y, a, b, A, S = sp.symbols("x y a b A S")
TR = standard_transformations + (implicit_multiplication_application, convert_xor, rationalize)
LOC = {"x": x, "y": y, "a": a, "b": b, "A": A, "S": S}


def braced(s: str, i: int) -> tuple[str, int]:
    """s[i] == '{' 일 때 짝이 맞는 '}'까지의 안쪽과 다음 위치."""
    assert s[i] == "{", (s, i)
    depth, j = 0, i
    while True:
        if s[j] == "{":
            depth += 1
        elif s[j] == "}":
            depth -= 1
            if depth == 0:
                return s[i + 1:j], j + 1
        j += 1


def tex2py(t: str) -> str:
    t = t.replace("\\left", "").replace("\\right", "").replace("\\{", "(").replace("\\}", ")").replace("[", "(").replace("]", ")")
    t = t.replace("\\dfrac", "\\frac").replace("\\times", "*").replace("\\div", "/").replace("\\cdot", "*").replace("\\square", "S")
    while "\\frac" in t:
        i = t.index("\\frac")
        n_, j = braced(t, i + 5)
        d_, k = braced(t, j)
        t = t[:i] + f"(({n_})/({d_}))" + t[k:]
    while "^{" in t:
        i = t.index("^{")
        e_, j = braced(t, i + 1)
        t = t[:i] + f"**({e_})" + t[j:]
    t = t.replace("{", "(").replace("}", ")")
    return t


def top_split(t: str, seps: list[str]) -> list[str]:
    """괄호 바깥에서만 seps로 나눈다 (나눈 기호도 함께 돌려준다)."""
    out, depth, cur, i = [], 0, "", 0
    while i < len(t):
        ch = t[i]
        if ch in "({[":
            depth += 1
        elif ch in ")}]":
            depth -= 1
        hit = next((sp_ for sp_ in seps if depth == 0 and t.startswith(sp_, i)), None)
        if hit:
            out += [cur, hit]
            cur = ""
            i += len(hit)
            continue
        cur += ch
        i += 1
    return out + [cur]


def textbook(t: str) -> str:
    """교과서 읽기: 항(+, -로 나뉨) 안에서 × ÷ 뒤의 단항식은 통째로 하나 (÷ 3x^2y 는 3x^2y로 나눔)."""
    parts = top_split(t, [" + ", " - "])
    res = ""
    for k, piece in enumerate(parts):
        if piece in (" + ", " - "):
            res += piece
            continue
        fs = top_split(piece, ["\\times", "\\div"])
        res += "".join(f if f in ("\\times", "\\div") else "{" + f.strip() + "}" for f in fs)
    return res


def E(t: str):
    return parse_expr(tex2py(textbook(t)), local_dict=LOC, transformations=TR)


def mathparts(q: str) -> list[str]:
    return re.findall(r"\$(.+?)\$", q)


def num(s: str) -> F:
    return F(s)


def choice_index(ans: str) -> int:
    return MARKS.index(ans)


REL = {"\\geq": ">=", "\\leq": "<=", ">": ">", "<": "<"}


def rel_split(t: str):
    for k in ("\\geq", "\\leq", ">", "<"):
        if k in t:
            l_, r_ = t.split(k, 1)
            return l_, REL[k], r_
    return None


def ineq(l_, op, r_):
    return {">": l_ > r_, "<": l_ < r_, ">=": l_ >= r_, "<=": l_ <= r_}[op]


def solve_ineq(t: str):
    l_, op, r_ = rel_split(t)
    return sp.solve_univariate_inequality(ineq(E(l_), op, E(r_)), x, relational=False)


def dotted_value(t: str) -> F:
    """'1.2\\dot{4}\\dot{5}' 같은 순환소수 -> 분수 (긴 소수로 펼친 뒤 가장 가까운 분수)."""
    ip, frac_part = t.split(".", 1)
    dots = [m.start() for m in re.finditer(r"\\dot\{", frac_part)]
    plain = re.sub(r"\\dot\{(\d)\}", r"\1", frac_part)
    # 점 위치를 평문 기준으로 다시 계산
    idx, pos, marks = 0, 0, []
    k = 0
    while k < len(frac_part):
        if frac_part.startswith("\\dot{", k):
            marks.append(pos)
            k += 6
            pos += 1
        else:
            k += 1
            pos += 1
    pre, rep = plain[:marks[0]], plain[marks[0]:marks[-1] + 1]
    s = pre + rep * 40
    return (int(ip) + F(int(s), 10 ** len(s))).limit_denominator(10 ** 7)


def check_one(q) -> tuple[bool | None, str]:
    """(맞음/틀림/검산 못 함, 설명)"""
    qt, ans, ch = q["question"], q["answer"], q["choices"]
    m = mathparts(qt)
    try:
        # A. 연립방정식 / A=B=C
        if qt.endswith("를 푸시오. (예: x=1, y=2)") or qt.endswith("을 푸시오. (예: x=1, y=2)"):
            body = m[0]
            if "cases" in body:
                inner = re.search(r"\\begin\{cases\}(.*)\\end\{cases\}", body).group(1)
                eqs = [e.split("=") for e in inner.split("\\\\")]
            else:
                p_ = body.split("=")
                eqs = [[p_[0], p_[2]], [p_[1], p_[2]]]
            sol = sp.solve([E(l_) - E(r_) for l_, r_ in eqs], [x, y], dict=True)
            want = dict(kv.split("=") for kv in ans.replace(" ", "").split(","))
            ok = len(sol) == 1 and sol[0][x] == int(want["x"]) and sol[0][y] == int(want["y"])
            return ok, f"sympy: {sol}"
        # B. 부등식: 가장 큰/작은 정수
        if "일차부등식" in qt and "정수 $x$의 값" in qt:
            sol = solve_ineq(m[0])
            ints = [k for k in range(-500, 501) if sol.contains(k)]
            v = max(ints) if "가장 큰" in qt else min(ints)
            return v == int(ans), f"sympy: {sol} → {v}"
        # H. 부등식의 해 고르기
        if qt.startswith("일차부등식 $") and qt.endswith("의 해는?"):
            sol = solve_ineq(m[0])
            goods = []
            for c in ch:
                l_, op, r_ = rel_split(c.strip("$"))
                cs = sp.solve_univariate_inequality(ineq(E(l_), op, E(r_)), x, relational=False)
                goods.append(cs == sol)
            return goods.count(True) == 1 and goods[choice_index(ans)], f"맞는 보기 {goods}"
        # C. 순환소수 -> 기약분수
        if qt.startswith("순환소수 $") and qt.endswith("기약분수로 나타내시오."):
            v = dotted_value(m[0])
            return v == num(ans), f"펼쳐서 {v}"
        # D. n번째 자리
        mm = re.search(r"소수점 아래 (\d+)번째 자리의 숫자", qt)
        if mm:
            n = int(mm.group(1))
            if qt.startswith("분수"):
                fr = F(int(re.search(r"\\frac\{(\d+)\}\{(\d+)\}", m[0]).group(1)), int(re.search(r"\\frac\{(\d+)\}\{(\d+)\}", m[0]).group(2)))
            else:
                fr = dotted_value(m[0])
            d = (fr.numerator * 10 ** n // fr.denominator) % 10
            return str(d) == ans, f"직접 나눗셈 {d}"
        # E. x절편 p, y절편 q
        if "$x$절편을 $p$, $y$절편을 $q$" in qt:
            rhs = E(m[0].split("=", 1)[1])
            p_, q_ = sp.solve(rhs, x)[0], rhs.subs(x, 0)
            val = {"p + q": p_ + q_, "p - q": p_ - q_, "pq": p_ * q_}[m[-1]]
            return sp.nsimplify(val) == sp.Rational(num(ans).numerator, num(ans).denominator), f"절편 {p_}, {q_}"
        # G. 좌표축과 삼각형 넓이
        if "$x$축 및 $y$축으로 둘러싸인 삼각형의 넓이" in qt:
            rhs = E(m[0].split("=", 1)[1])
            xi, yi = sp.solve(rhs, x)[0], rhs.subs(x, 0)
            v = abs(xi * yi) / 2
            return v == sp.Rational(num(ans).numerator, num(ans).denominator), f"{xi}, {yi} → {v}"
        # F. 두 점 기울기
        mm = re.search(r"두 점 \$\((-?\d+), (-?\d+)\)\$, \$\((-?\d+), (-?\d+)\)\$를 지나는 일차함수의 그래프의 기울기를 구하시오", qt)
        if mm:
            x1, y1, x2, y2 = map(int, mm.groups())
            return F(y2 - y1, x2 - x1) == num(ans), "기울기"
        # I. 연립방정식 중 해인 것
        mm = re.search(r"\$x = (-?\d+)\$, \$y = (-?\d+)\$[이가] 해인 것은", qt)
        if mm and "cases" in ch[0]:
            sx, sy = map(int, mm.groups())
            goods = []
            for c in ch:
                inner = re.search(r"\\begin\{cases\}(.*)\\end\{cases\}", c).group(1)
                goods.append(all(sp.simplify(E(l_) - E(r_)).subs({x: sx, y: sy}) == 0 for l_, r_ in (e.split("=") for e in inner.split("\\\\"))))
            return goods.count(True) == 1 and goods[choice_index(ans)], f"{goods}"
        # J. 부등식 중 x = k가 해인/아닌 것
        mm = re.search(r"다음 부등식 중 \$x = (-?\d+)\$[이가] 해(인|가 아닌) 것은", qt)
        if mm:
            xv, want = int(mm.group(1)), mm.group(2) == "인"
            goods = []
            for c in ch:
                l_, op, r_ = rel_split(c.strip("$"))
                goods.append(bool(ineq(E(l_).subs(x, xv), op, E(r_).subs(x, xv))) == want)
            return goods.count(True) == 1 and goods[choice_index(ans)], f"{goods}"
        # N. 유한소수/무한소수 고르기
        if "유한소수인 것은?" in qt or "무한소수인 것은?" in qt:
            want = "유한소수인" in qt
            goods = []
            for c in ch:
                mm2 = re.search(r"\\frac\{(\d+)\}\{(\d+)\}", c)
                d = F(int(mm2.group(1)), int(mm2.group(2))).denominator
                fin = all(p_ in (2, 5) for p_ in sp.factorint(d))
                goods.append(fin == want)
            return goods.count(True) == 1 and goods[choice_index(ans)], f"{goods}"
        # L. 옳은/옳지 않은 식 (등식 보기)
        if qt in ("다음 중 옳은 것은?", "다음 중 옳지 않은 것은?") and all(c.count("=") == 1 and c.startswith("$") and c.endswith("$") for c in ch):
            want = qt == "다음 중 옳은 것은?"
            goods = []
            for c in ch:
                l_, r_ = c.strip("$").split("=")
                goods.append((sp.simplify(E(l_) - E(r_)) == 0) == want)
            return goods.count(True) == 1 and goods[choice_index(ans)], f"{goods}"
        # K. 식 계산/간단히 (보기가 식)
        if (qt.endswith("를 계산하면?") or qt.endswith("을 계산하면?") or qt.endswith("간단히 하면?")) and len(m) == 1:
            v = E(m[0])
            goods = [sp.simplify(v - E(c.strip("$"))) == 0 for c in ch]
            return goods.count(True) == 1 and goods[choice_index(ans)], f"{goods}"
        # M. □ 안에 알맞은 식
        if "$\\square$ 안에 알맞은 식은?" in qt:
            l_, r_ = m[0].split("=")
            goods = [sp.simplify(E(l_).subs(S, E(c.strip("$"))) - E(r_)) == 0 for c in ch]
            return goods.count(True) == 1 and goods[choice_index(ans)], f"{goods}"
        # O. 지수의 합과 차 (밑^x)
        mm = re.match(r"\$(.+) = (\d)\^\{x\}\$일 때, 자연수 \$x\$의 값을 구하시오\.", qt)
        if mm:
            v = E(mm.group(1))
            base = int(mm.group(2))
            return sp.simplify(v - base ** int(ans)) == 0, f"{v}"
        # P. 두 일차방정식 교점
        mm = re.search(r"두 일차방정식 \$(.+?) = 0\$, \$(.+?) = 0\$의 그래프의 교점의 좌표를 \$\(p, q\)\$라 할 때, \$(.+?)\$의 값", qt)
        if mm:
            sol = sp.solve([E(mm.group(1)), E(mm.group(2))], [x, y], dict=True)[0]
            v = {"p + q": sol[x] + sol[y], "pq": sol[x] * sol[y], "p - q": sol[x] - sol[y]}[mm.group(3)]
            return v == int(ans), f"{sol}"
        # Q. 몇 자리 자연수
        if qt.endswith("은 몇 자리 자연수인지 구하시오."):
            v = E(m[0])
            return len(str(int(v))) == int(ans), f"{v}"
    except Exception as e:  # 읽지 못한 꼴
        return None, f"읽기 실패: {e!r}"
    return None, ""


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    per, bad, fails = {}, [], 0
    total = 0
    for pg in data["books"][0]["pages"]:
        for q in pg["problems"]:
            total += 1
            ok, why = check_one(q)
            c = q["concept"]
            per.setdefault(c, [0, 0, 0])
            if ok is None:
                per[c][2] += 1
                if why.startswith("읽기 실패"):
                    fails += 1
                    if fails <= 15:
                        print("  [못 읽음]", q["bank_id"], why[:120], "|", q["question"][:100])
            elif ok:
                per[c][0] += 1
            else:
                per[c][1] += 1
                bad.append((q, why))
    checked = sum(v[0] + v[1] for v in per.values())
    for c, (o, w, n) in per.items():
        if o + w:
            print(f"  {c}: 검산 {o + w}개, 틀림 {w}개")
    print(f"검산 {checked}/{total}문제, 틀림 {len(bad)}개, 읽기 실패 {fails}개")
    for q, why in bad[:30]:
        print("  [틀림]", q["bank_id"], q["question"][:140], "| 답", q["answer"], "|", q["choices"], "|", why[:200])
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
