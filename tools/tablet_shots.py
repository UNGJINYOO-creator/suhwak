"""Walks the main screens on a running emulator and saves screenshots (used by the tablet-shots workflow)."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

OUT = sys.argv[1]
TAG = sys.argv[2]
APP = "com.suhwak.app"
os.makedirs(OUT, exist_ok=True)


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True).stdout


def shot(name):
    time.sleep(1.5)
    with open(f"{OUT}/{TAG}-{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))
    print("shot", name, flush=True)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", "ignore")
    try:
        return list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        return []


def tap(text, exact=False, tries=3):
    for _ in range(tries):
        for n in nodes():
            t = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
            if (text in t.split("|")) if exact else (text in t):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1.5)
                return True
        time.sleep(1.5)
    print("not found:", text, flush=True)
    return False


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.2)


def screen():
    size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size").decode())[-1]
    return int(size[0]), int(size[1])


def scroll():
    w, h = screen()
    adb("shell", "input", "swipe", str(w // 2), str(h * 3 // 4), str(w // 2), str(h // 4), "400")
    time.sleep(1)


def restart():
    adb("shell", "am", "force-stop", APP)
    adb("shell", "am", "start", "-n", f"{APP}/.MainActivity")
    time.sleep(5)


restart()
time.sleep(3)
shot("01-onboarding")
tap("다음", exact=True)
shot("02-onboarding2")
tap("건너뛰기")
time.sleep(4)
shot("03-home")
scroll(); shot("04-home-scrolled")
scroll(); shot("05-home-bottom")

restart()
if tap("Lv."):
    shot("06-level-dialog")

restart()
if tap("오늘의 낚시"):
    shot("07-fishing-guide")
    tap("건너뛰기", tries=1)
    shot("08-fishing")
    scroll(); shot("08b-fishing-scrolled")
    if tap("📖 도감"):
        shot("09-fishbook")

restart()
if tap("대양 원정"):
    shot("10-voyage-guide")
    tap("건너뛰기", tries=1)
    shot("11-voyage")
    scroll(); shot("12-voyage-scrolled")

restart()
if tap("오늘 학습 시작"):
    time.sleep(2)
    shot("13-study")
    w, h = screen()
    adb("shell", "input", "tap", str(w // 2), str(h // 2))
    shot("14-study-flip")

restart()
tap("복습·통계"); shot("16-review"); scroll(); shot("17-review-scrolled")
tap("설정", exact=True); shot("18-settings")
if tap("진도 초기화"):
    shot("19-settings-reset")
scroll(); shot("20-settings-bottom")
