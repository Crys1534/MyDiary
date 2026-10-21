#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from ui import *

os.makedirs(OUT, exist_ok=True)


def show(label, full=False):
    """Screenshot + list of the visible things (text / id), to read without looking at the picture."""
    fn = snap(label)
    xml = dump(fn)
    log("-- %s" % fn)
    for n in nodes(xml):
        if n["rid"].startswith("com.android.systemui") or n["rid"].endswith("navigationBarBackground"):
            continue
        if not full and not (n["text"] or n["desc"]):
            continue
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


def step(name, fn):
    log("== " + name)
    try:
        fn()
    except Exception as e:  # keep going: the next steps may still work
        log("   STEP FAILED: %r" % (e,))


def launch():
    adb("shell", "am", "start", "-n", PKG + "/com.kiminonawa.mydiary.init.InitActivity")
    sleep(10)
    show("launch")
    dismiss_intro()
    sleep(1)
    show("main")


def write_entry():
    tap(text="DIARY")
    sleep(3)
    dismiss_intro()
    tap(rid="But_diary_topbar_diary")
    sleep(2)
    show("new_diary", full=True)
    # title
    tap(rid="EDT_diary_title")
    type_text("Prueba negrita")
    # tags
    if tap(rid="EDT_diary_tags"):
        type_text("\\#escuela \\#viaje")
    key(4)  # close keyboard
    show("title_tags_typed")
    # first text box
    tap(rid="LL_diary_item_content")
    sleep(1)
    type_text("hola mundo bonito")
    show("text_typed", full=True)
    # long press on a word to select it, then bold
    n = find(text="hola mundo bonito")
    if n:
        adb("shell", "input", "swipe", str(n["cx"]), str(n["cy"]), str(n["cx"]), str(n["cy"]), "900")
        sleep(1)
        show("word_selected")
        tap(rid="IV_diary_bold")
        sleep(1)
        show("bold_done")
    # checklist
    tap(rid="IV_diary_checklist")
    sleep(1)
    type_text("comprar pan")
    key(66)  # enter -> next line
    type_text("llamar a mama")
    show("checklist_typed", full=True)
    key(4)
    tap(rid="IV_diary_save")
    sleep(3)
    show("after_save")


def open_entries():
    tap(rid="But_diary_topbar_entries")
    sleep(2)
    show("entries")
    n = find(text="Prueba negrita")
    if n:
        tap_xy(n["cx"], n["cy"])
        sleep(3)
        show("viewer", full=True)
        # tick the first checkbox if visible
        c = find(rid="CheckBox") or None
        key(4)
        sleep(1)


def settings():
    key(4)
    sleep(1)
    key(4)
    sleep(2)
    dismiss_intro()
    tap(rid="IV_main_setting")
    sleep(2)
    show("main_settings", full=True)


step("launch", launch)
step("write an entry", write_entry)
step("open the entries", open_entries)
step("settings", settings)
log("== done")
