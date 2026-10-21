#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from ui import *

os.makedirs(OUT, exist_ok=True)


def show(label):
    """Screenshot + list of the visible things (text / id), to read without looking at the picture."""
    fn = snap(label)
    xml = dump(fn)
    log("-- %s" % fn)
    for n in nodes(xml):
        if n["rid"].startswith("com.android.systemui") or n["rid"] in ("", "android:id/content"):
            if not n["text"] and not n["desc"]:
                continue
        if n["text"] or n["desc"] or n["rid"]:
            log("   [%s] text=%r desc=%r %s click=%s @%d,%d" % (n["rid"].split("/")[-1], n["text"][:60], n["desc"][:40], n["cls"].split(".")[-1], n["clickable"], n["cx"], n["cy"]))


def dismiss_intro():
    """Closes the first-run dialogs (compat warning, release note, showcase 'Next')."""
    for _ in range(10):
        if tap(rid="android:id/button1"):
            continue
        n = find(text="Ok", contains=False)
        if n:
            tap_xy(n["cx"], n["cy"])
            continue
        n = find(text="Next", contains=False)
        if n:
            tap_xy(n["cx"], n["cy"])
            continue
        break


log("== launch")
adb("shell", "am", "start", "-n", PKG + "/com.kiminonawa.mydiary.init.InitActivity")
sleep(10)
show("launch")
dismiss_intro()
sleep(1)
show("main")

log("== open the diary topic")
if tap(text="DIARY"):
    sleep(3)
    show("diary_topic")
    dismiss_intro()
    show("diary_topic2")
log("== done")
