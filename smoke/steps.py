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
    report_edit_bar("with keyboard")
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
    sleep(1)
    show("keyboard_closed", full=True)
    location_test()
    tap(rid="IV_diary_save")
    sleep(3)
    show("after_save")


def report_edit_bar(label):
    """Says whether the buttons of the edit bar are inside the screen (not hidden by the keyboard)."""
    size = adb("shell", "wm", "size").strip().split(":")[-1].strip()
    h = int(size.split("x")[-1]) if "x" in size else 0
    for rid in ("IV_diary_bold", "IV_diary_checklist", "IV_diary_save"):
        n = find(rid=rid)
        if n is None:
            log("   EDIT BAR %s: %s not in the screen tree" % (label, rid))
        else:
            kb = adb("shell", "dumpsys", "input_method").count("mInputShown=true")
            log("   EDIT BAR %s: %s at y=%d (screen height %d, keyboard shown=%s)" % (label, rid, n["cy"], h, kb > 0))


def location_test():
    """The permission dialog of the location, then a fake position from the emulator."""
    if not tap(rid="IV_diary_location"):
        return
    sleep(2)
    show("location_permission_dialog", full=True)
    for text in ("While using the app", "Only this time", "Allow", "ALLOW"):
        n = find(text=text)
        if n:
            tap_xy(n["cx"], n["cy"])
            break
    adb("emu", "geo", "fix", "-3.7038", "40.4168")  # Madrid
    sleep(6)
    show("location_after", full=True)
    # the place name can be typed by hand
    dismiss_dialogs = find(text="Ok", contains=False)
    if dismiss_dialogs:
        tap_xy(dismiss_dialogs["cx"], dismiss_dialogs["cy"])


def camera_test():
    """The camera button must open the camera app of the phone (or say there is none), never crash."""
    if not tap(rid="IV_diary_photo"):
        return
    sleep(2)
    show("photo_sheet", full=True)
    if tap(rid="IV_diary_photo_add_a_photo"):
        sleep(4)
        show("camera_app", full=True)
        key(4)
        sleep(1)
    # make sure we are back in the app
    n = find(rid="IV_diary_save")
    if n is None:
        key(4)
        sleep(1)


def calendar_test():
    """The calendar page (page-curl effect) draws and can be flipped."""
    if not tap(rid="But_diary_topbar_calendar"):
        return
    sleep(2)
    show("calendar", full=True)
    adb("shell", "input", "swipe", "900", "1100", "150", "1100", "400")
    sleep(2)
    show("calendar_flipped")


def open_entries():
    calendar_test()
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


def picker_save():
    """Android's 'save as' window: press Save."""
    show("picker_save", full=True)
    for rid in ("com.android.documentsui:id/container_save", "com.android.documentsui:id/save"):
        n = find(rid=rid)
        if n:
            tap_xy(n["cx"], n["cy"])
            return True
    return tap(text="Save", contains=False)


def backup_test():
    key(4)
    sleep(1)
    dismiss_intro()
    if not tap(rid="IV_main_setting"):
        return
    sleep(2)
    if not tap(rid="IV_main_setting_backup"):
        show("no_backup_button", full=True)
        return
    sleep(2)
    show("backup_screen", full=True)
    tap(rid="TV_backup_export_src")
    sleep(3)
    picker_save()
    sleep(2)
    show("backup_after_save_as", full=True)
    tap(rid="But_backup_export")
    sleep(5)
    show("backup_exported", full=True)
    # import the same file
    tap(rid="TV_backup_import_src")
    sleep(3)
    show("picker_open", full=True)
    n = find(text=".zip")
    if n:
        tap_xy(n["cx"], n["cy"])
        sleep(2)
        show("backup_import_chosen", full=True)
        tap(rid="But_backup_import")
        sleep(5)
        show("backup_imported", full=True)


step("launch", launch)
step("write an entry", write_entry)
step("open the entries", open_entries)
step("settings", settings)
step("backup", backup_test)


def camera_last():
    """Back to the main screen -> topic -> new diary -> camera button (the emulator has no real camera app)."""
    for _ in range(3):
        key(4)
        sleep(1)
    dismiss_intro()
    launch_n = find(text="DIARY")
    if launch_n:
        tap_xy(launch_n["cx"], launch_n["cy"])
        sleep(3)
        dismiss_intro()
        tap(rid="But_diary_topbar_diary")
        sleep(2)
        camera_test()


step("camera", camera_last)
log("== done")
