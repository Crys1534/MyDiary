#!/usr/bin/env python3
"""Small helpers to drive the app on the emulator with adb + uiautomator (used by the smoke test)."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = "smoke-out"
PKG = "com.kiminonawa.mydiary.fotos"
_counter = [0]


def adb(*args, check=False):
    r = subprocess.run(["adb"] + list(args), capture_output=True, text=True)
    return r.stdout


def sleep(s):
    time.sleep(s)


def snap(name):
    _counter[0] += 1
    fn = "%02d_%s" % (_counter[0], name)
    adb("shell", "screencap", "-p", "/sdcard/s.png")
    subprocess.run(["adb", "pull", "/sdcard/s.png", "%s/%s.png" % (OUT, fn)], capture_output=True)
    return fn


def dump(name=None):
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    out = adb("exec-out", "cat", "/sdcard/ui.xml")
    if name:
        with open("%s/%s.xml" % (OUT, name), "w", encoding="utf-8") as f:
            f.write(out)
    return out


def nodes(xml_text=None):
    xml_text = xml_text or dump()
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return []
    res = []
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        res.append({
            "text": n.get("text", ""),
            "rid": n.get("resource-id", ""),
            "desc": n.get("content-desc", ""),
            "cls": n.get("class", ""),
            "clickable": n.get("clickable") == "true",
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "b": (x1, y1, x2, y2),
        })
    return res


def find(text=None, rid=None, desc=None, contains=True):
    for n in nodes():
        if text is not None and (text.lower() in n["text"].lower() if contains else text == n["text"]):
            return n
        if rid is not None and n["rid"].endswith(rid):
            return n
        if desc is not None and desc.lower() in n["desc"].lower():
            return n
    return None


def tap_xy(x, y):
    adb("shell", "input", "tap", str(x), str(y))
    sleep(1.2)


def tap(text=None, rid=None, desc=None, contains=True):
    n = find(text=text, rid=rid, desc=desc, contains=contains)
    if n is None:
        print("NOT FOUND:", text, rid, desc)
        return False
    tap_xy(n["cx"], n["cy"])
    return True


def type_text(s):
    adb("shell", "input", "text", s.replace(" ", "%s"))
    sleep(0.8)


def key(code):
    adb("shell", "input", "keyevent", str(code))
    sleep(0.8)


def log(msg):
    print(msg, flush=True)
    with open("%s/steps.log" % OUT, "a", encoding="utf-8") as f:
        f.write(msg + "\n")
