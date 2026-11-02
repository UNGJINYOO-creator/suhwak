"""Two "devices" on one emulator: device A's data is saved and put back around device B, to check the family sync."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

OUT = sys.argv[1]
APP = "com.suhwak.app"
os.makedirs(OUT, exist_ok=True)
log = open(f"{OUT}/sync-check.txt", "w")


def mark(label):
    adb("shell", "log", "-t", "WBSync", "== " + label.replace(" ", "_"))


def say(*a):
    line = " ".join(str(x) for x in a)
    print(line, flush=True)
    log.write(line + "\n"); log.flush()


def adb(*args, stdin=None):
    return subprocess.run(["adb", *args], capture_output=True, input=stdin).stdout


def shot(name):
    time.sleep(1.5)
    with open(f"{OUT}/sync-{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", "ignore")
    try:
        return list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        return []


def tap(text, tries=7):
    for k in range(tries):
        # The shortest match is the button itself, not a help line that mentions it ("…💾 설정 저장을 누르면…").
        hits = [n for n in nodes() if text in (n.get("text") or "") + "|" + (n.get("content-desc") or "")]
        for n in sorted(hits, key=lambda n: len(n.get("text") or "") + len(n.get("content-desc") or ""))[:1]:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(1.5)
            return True
        # Maybe off screen: look further down first, then back up.
        if k < 3:
            adb("shell", "input", "swipe", "540", "1500", "540", "600", "600")
        else:
            adb("shell", "input", "swipe", "540", "600", "540", "1500", "600")
        time.sleep(2)
    seen = [n.get("text") for n in nodes() if n.get("text")]
    say("  (not found:", text + ") seen:", " / ".join(seen)[:400])
    shot("missing-" + re.sub(r"\W", "", text)[:12])
    return False


def typ(text):
    adb("shell", "input", "text", text)
    time.sleep(0.8)
    adb("shell", "input", "keyevent", "111")  # escape: hide the keyboard
    time.sleep(0.8)


def pref(file, key):
    raw = adb("exec-out", "run-as", APP, "cat", f"shared_prefs/{file}.xml").decode("utf-8", "ignore")
    m = re.search(r'name="%s"(?: value="([^"]*)")?>?([^<]*)' % re.escape(key), raw)
    if not m:
        return None
    return m.group(1) if m.group(1) is not None else m.group(2)


def start():
    adb("shell", "am", "force-stop", APP)
    adb("shell", "pm", "grant", APP, "android.permission.POST_NOTIFICATIONS")
    adb("shell", "am", "start", "-n", f"{APP}/.MainActivity")
    time.sleep(8)


def fresh_device():
    adb("shell", "pm", "clear", APP)
    start()
    for _ in range(5):
        texts = [n.get("text") for n in nodes()]
        if "복습·통계" in texts:
            break
        tap("건너뛰기", tries=1)
        time.sleep(3)


def save_device(name):
    adb("shell", "am", "force-stop", APP)
    data = adb("exec-out", "run-as", APP, "tar", "-cf", "-", "shared_prefs", "databases", "files")
    open(f"/tmp/{name}.tar", "wb").write(data)
    say(f"  saved device {name}: {len(data)} bytes")


def load_device(name):
    adb("shell", "pm", "clear", APP)
    adb("shell", "run-as", APP, "tar", "-xf", "-", stdin=open(f"/tmp/{name}.tar", "rb").read())


def dump(label):
    for f in ("sync", "study"):
        raw = adb("exec-out", "run-as", APP, "cat", f"shared_prefs/{f}.xml").decode("utf-8", "ignore")
        raw = re.sub(r'(tgToken">)[^<]*', r"\1…", raw)
        say(f"  [{label} {f}.xml]", " ".join(raw.split())[:700])


def remote_study(code):
    import json, urllib.request
    key = "AIzaSyDChZaflfeKcGBrxmrq4ILejJlhEp2Jvwk"
    try:
        req = urllib.request.Request(f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={key}", data=b"{}", headers={"Content-Type": "application/json"})
        tok = json.load(urllib.request.urlopen(req))["idToken"]
        url = f"https://firestore.googleapis.com/v1/projects/nakabocab/databases/(default)/documents/families/{code}/prefs/study"
        doc = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"Authorization": "Bearer " + tok})))
        data = doc["fields"]["data"]["stringValue"]
        say("  [server study]", re.sub(r'"tgToken":\["s","[^"]*"\]', '"tgToken":…', data)[:500], "updatedAt", doc["fields"]["updatedAt"])
    except Exception as e:
        say("  [server study] error", e)


def hashes(label):
    raw = adb("exec-out", "run-as", APP, "cat", "files/sync_hashes.json").decode("utf-8", "ignore")
    m = re.search(r'"bstudy":(\{.*?\})', raw)
    p = re.search(r'"pstudy":"([^"]*)"', raw)
    say(f"  [{label} hashes] pstudy={p.group(1)[:10] if p else None} bstudy=", re.sub(r'"tgToken":\["s","[^"]*"\]', '"tgToken":…', m.group(1))[:400] if m else None)


def wait_sync(before, label, limit=120):
    """Waits until the app finishes a sync that started after [before] (lastSync moves past it)."""
    for i in range(limit // 3):
        v = pref("sync", "lastSync")
        if v and int(v) > before:
            say(f"  {label}: synced after {i * 3}s")
            return
        time.sleep(3)
    say(f"  {label}: no sync finished within {limit}s")


def now_ms():
    return int(adb("shell", "date", "+%s").decode().strip() or 0) * 1000


def check(label, ok):
    say(("PASS " if ok else "FAIL ") + label)


say("== Device A: new family, Telegram settings, some study ==")
fresh_device()
tap("설정")
tap("기기 동기화")
tap("새 가족 코드 만들기")
time.sleep(8)
code = pref("sync", "code")
say("  family code:", code)
check("A made a family code", bool(code))
tap("텔레그램 알림")
tap("봇 토큰")
typ("TESTTOKEN123")
tap("받는 사람 1")
typ("111")
tap("설정 저장")
time.sleep(10)
shot("A-settings")
check("A saved the Telegram token", pref("study", "tgToken") == "TESTTOKEN123")
tap("홈")
if tap("오늘 학습 시작"):
    time.sleep(3)
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1300", "540", "300", "150")
        time.sleep(1.5)
    shot("A-study")
    adb("shell", "input", "keyevent", "4"); time.sleep(1)
    adb("shell", "input", "keyevent", "4"); time.sleep(1)
adb("shell", "input", "keyevent", "3")  # home key: the app syncs when it goes to the background
time.sleep(12)
save_device("A")

say("== Device B: joins with the code ==")
fresh_device()
tap("설정")
tap("기기 동기화")
tap("다른 기기에 나온 가족 코드")
typ(code or "")
t = now_ms()
tap("이 코드로 연결")
wait_sync(t, "B joining", 180)
shot("B-joined")
check("B got A's Telegram token", pref("study", "tgToken") == "TESTTOKEN123")
check("B got A's Telegram chat id", pref("study", "tgChat") == "111")
tap("홈")
time.sleep(2)
shot("B-home")
say("  B changes 하루 목표 to 10 words")
tap("설정")
tap("하루 목표")
tap("10개")
t = now_ms()
mark("B pressing save")
tap("설정 저장")
wait_sync(t, "B saving")
check("B saved 10 words", pref("study", "dailyWords") == "10")
dump("B after saving")
remote_study(code)
save_device("B")

say("== Back to device A: opens the app ==")
load_device("A")
hashes("A before opening")
t = now_ms()
start()
wait_sync(t, "A opening")
adb("shell", "input", "keyevent", "3")
time.sleep(10)
dump("A after reopening")
hashes("A after reopening")
remote_study(code)
if pref("study", "dailyWords") != "10":
    say("  one more open and close")
    start(); time.sleep(10); adb("shell", "input", "keyevent", "3"); time.sleep(12)
    dump("A after second open")
check("A got B's 하루 목표 (10)", pref("study", "dailyWords") == "10")
check("A still has its Telegram token", pref("study", "tgToken") == "TESTTOKEN123")
start()
shot("A-home-after")
say("== done ==")
