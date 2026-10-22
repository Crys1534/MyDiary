#!/usr/bin/env python3
import os, sys, subprocess
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


def keyboard_shown():
    out = adb("shell", "dumpsys", "input_method")
    return "mInputShown=true" in out


def close_keyboard():
    """Presses BACK only while the keyboard is open (a BACK without keyboard would leave the diary)."""
    for _ in range(3):
        if not keyboard_shown():
            return True
        key(4)
        sleep(0.7)
    return not keyboard_shown()


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


def restart(label=None):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", PKG + "/com.kiminonawa.mydiary.init.InitActivity")
    sleep(7)
    if label:
        show(label, full=True)   # before the intro dialogs are closed
    dismiss_intro()
    sleep(1)


def alive(label):
    pid = adb("shell", "pidof", PKG).strip()
    log("   APP %s after '%s'" % ("ALIVE (pid %s)" % pid if pid else "IS DEAD", label))
    return bool(pid)


def search_test():
    """Typing in the search box of the main page must not close the app."""
    restart()
    if not tap(rid="EDT_main_topic_search"):
        show("no_search_box", full=True)
        return
    type_text("a")
    sleep(2)
    show("search_a", full=True)
    alive("search a")
    type_text("bc")
    sleep(2)
    show("search_abc")
    alive("search abc")
    for _ in range(3):
        key(67)  # delete
    sleep(2)
    show("search_cleared")
    alive("search cleared")


def drag_test():
    """Long press + drag a topic to another place."""
    restart()
    topics = [n for n in nodes() if n["rid"].endswith("TV_topic_title")]
    log("   topics found: %s" % [t["text"] for t in topics])
    if len(topics) < 3:
        show("no_topics", full=True)
        return
    a, b = topics[0], topics[2]
    show("before_drag")
    adb("shell", "input", "draganddrop", str(a["cx"]), str(a["cy"]), str(b["cx"]), str(b["cy"] + 40), "1500")
    sleep(3)
    show("after_drag", full=True)
    alive("drag")


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
    close_keyboard()
    show("title_tags_typed")
    # first text box
    tap(rid="LL_diary_item_content")
    sleep(1)
    type_text("hola mundo bonito")
    show("text_typed", full=True)
    report_edit_bar("with keyboard")
    # long press on the first word to select it, close the keyboard (the word must stay marked),
    # then bold + italic with the buttons
    n = find(text="hola mundo bonito")
    if n:
        x = n["b"][0] + 70
        adb("shell", "input", "swipe", str(x), str(n["cy"]), str(x), str(n["cy"]), "900")
        sleep(1)
        show("word_selected")
        # the keyboard covers the edit bar: close it (the word must stay marked)
        log("   keyboard closed: %s" % close_keyboard())
        sleep(1)
        show("word_selected_keyboard_closed")
        tap(rid="IV_diary_bold")
        sleep(1)
        show("bold_done")
        tap(rid="IV_diary_italic")
        sleep(1)
        show("italic_done")
    list_test()
    # checklist (keyboard closed first, the edit bar is under it)
    close_keyboard()
    sleep(1)
    tap(rid="IV_diary_checklist")
    sleep(1)
    type_text("comprar pan")
    key(66)  # enter -> next line
    type_text("llamar a mama")
    show("checklist_typed", full=True)
    close_keyboard()
    sleep(1)
    show("keyboard_closed", full=True)
    photos_test()
    audio_test()
    location_test()
    tap(rid="IV_diary_save")
    sleep(3)
    show("after_save")
    db_check()


def list_menu(item):
    """The list button of the edit bar opens a small menu: tap one of its items."""
    close_keyboard()
    if not tap(rid="IV_diary_list"):
        log("   NO LIST BUTTON")
        show("no_list_button", full=True)
        return False
    sleep(1)
    n = find(text=item, contains=False)
    if not n:
        log("   menu item not found: %s" % item)
        show("list_menu_missing", full=True)
        return False
    tap_xy(n["cx"], n["cy"])
    sleep(1)
    return True


def list_test():
    """Bullets, numbered list and quote: Enter continues the list, Enter on an empty line ends it."""
    n = find(text="hola mundo bonito")
    if not n:
        log("   list test: text box not found")
        return
    tap_xy(n["b"][2] - 30, n["cy"])  # the end of the text
    sleep(1)
    key(66)
    if list_menu("Bullets"):
        show("list_bullets_menu_done", full=True)
        type_text("uno")
        key(66)
        type_text("dos")
        key(66)
        key(66)  # empty bullet + Enter -> the list ends
        type_text("sin lista")
        key(66)
    if list_menu("Numbered list"):
        type_text("primero")
        key(66)
        type_text("segundo")
        key(66)
        type_text("tercero")
        key(66)
        key(66)
        type_text("fin numeros")
        key(66)
    if list_menu("Quote"):
        type_text("una cita de prueba bastante larga para que se rompa en varias lineas y se vea el margen")
    sleep(1)
    show("lists_typed", full=True)
    close_keyboard()
    sleep(1)
    show("lists_keyboard_closed")
    alive("lists")


def make_png(path, w, h, base):
    """A plain picture with a big colored frame and stripes, to see how it is cut (no extra libraries)."""
    import zlib, struct
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            if x < 30 or y < 30 or x >= w - 30 or y >= h - 30:
                raw.extend(b"\x00\x00\x00")  # black frame: if it is cut, the frame disappears
            else:
                stripe = (x * 6 // w)
                r, g, b = base
                raw.extend(bytes([(r + stripe * 30) % 256, (g + (y * 255 // h)) % 256, (b + stripe * 10) % 256]))

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


AUDIO_LINKS = [
    ("mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
    # a real m4a with the index (moov) at the END of the file, like the recorders make them
    ("m4a", "https://raw.githubusercontent.com/Crys1534/MyDiary/master/smoke/test-audio.m4a"),
    # not an audio: a web page (like Google Drive when the file is not shared) -> a clear error
    ("page", "https://example.com/"),
]


def audio_times():
    return [n["text"] for n in nodes() if n["rid"].endswith("TV_diary_audio_time")]


def audio_messages():
    """Toasts / texts of the audio errors that are on the screen right now."""
    words = ("could not", "link gave", "did not let", "too big", "format")
    return [n["text"][:90] for n in nodes() if n["text"] and any(w in n["text"] for w in words)]


def add_audio(url, tag):
    close_keyboard()
    sleep(1)
    if not tap(rid="IV_diary_audio"):
        show("no_audio_button_%s" % tag, full=True)
        return False
    sleep(2)
    show("audio_dialog_%s" % tag, full=True)
    type_text(url)
    tap(rid="android:id/button1")
    sleep(2)
    show("audio_added_%s" % tag, full=True)
    return True


def play_audios(tag, wait):
    """Presses play on the NEWEST audio (the lowest one: new audios are added at the end of the entry),
    waits, and prints what it says every 2 seconds (download percentage, time, errors)."""
    # go to the bottom of the entry, where the new audio is (and let the scroll stop before touching)
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1700", "540", "700", "300")
        sleep(1)
    sleep(2)
    plays = [n for n in nodes() if n["rid"].endswith("IV_diary_audio_play") and n["cy"] < 2180]
    log("   audios on the screen: %d, before: %s" % (len(plays), audio_times()))
    if not plays:
        show("audio_no_play_%s" % tag, full=True)
        return
    n = max(plays, key=lambda p: p["cy"])
    log("   tapping the lowest play button @%d,%d" % (n["cx"], n["cy"]))
    tap_xy(n["cx"], n["cy"])
    waited = 0
    while waited < wait:
        sleep(2)
        waited += 2
        log("   t+%ds (%s): %s %s" % (waited, tag, audio_times(), audio_messages()))
    show("audio_playing_%s" % tag, full=True)
    alive("audio %s" % tag)
    tap_xy(n["cx"], n["cy"])  # pause (or nothing if it ended / failed)
    sleep(1)


def audio_test():
    """The audio button: paste a link, the audio appears with a play button, play it for a few seconds."""
    for tag, url in AUDIO_LINKS:
        if not add_audio(url, tag):
            return
        play_audios(tag, 20 if tag == "mp3" else 10)


def pick_photo(name, index=1):
    """Adds one picture from the phone gallery to the diary that is open.
    The Android photo picker has no file names: the pictures are chosen by their place in the grid
    (newest first; place 0 is a screenshot, 1 and 2 are the test pictures)."""
    if not tap(rid="IV_diary_photo"):
        return False
    sleep(2)
    show("photo_sheet_%s" % name, full=True)
    if not tap(rid="IV_diary_photo_select_a_photo"):
        return False
    sleep(3)
    show("photo_chooser_%s" % name, full=True)
    for label in ("Files", "Documents", "Photos"):
        n = find(text=label, contains=False)
        if n:
            tap_xy(n["cx"], n["cy"])
            sleep(3)
            break
    # the "Always / Just once" question of the system
    n = find(text="Just once", contains=False)
    if n:
        tap_xy(n["cx"], n["cy"])
        sleep(3)
    show("photo_picker_%s" % name, full=True)
    thumbs = [n for n in nodes() if n["rid"].endswith("icon_thumbnail")]
    log("   picker thumbnails: %d" % len(thumbs))
    if thumbs:
        pick = thumbs[min(index, len(thumbs) - 1)]
        tap_xy(pick["cx"], pick["cy"])
        sleep(2)
        # the picker may ask to confirm the choice
        # the button says "Add (1)" in the Android photo picker
        for label in ("Add (", "Add", "Select", "Done"):
            n = find(text=label, contains=label == "Add (")
            if n:
                tap_xy(n["cx"], n["cy"])
                break
    elif not tap(text=name):
        show("photo_picker_notfound_%s" % name, full=True)
        key(4)
        return False
    sleep(5)
    show("photo_added_%s" % name, full=True)
    return True


def photos_test():
    """Two pictures (one wide, one tall) go into the diary; the second one becomes the cover with its star."""
    os.makedirs("%s/img" % OUT, exist_ok=True)
    make_png("%s/img/foto_ancha.png" % OUT, 900, 600, (200, 30, 30))
    make_png("%s/img/foto_alta.png" % OUT, 600, 900, (20, 60, 200))
    adb("shell", "mkdir", "-p", "/sdcard/Pictures")
    for name in ("foto_ancha.png", "foto_alta.png"):
        subprocess.run(["adb", "push", "%s/img/%s" % (OUT, name), "/sdcard/Pictures/%s" % name], capture_output=True)
        adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
            "-d", "file:///sdcard/Pictures/%s" % name)
    sleep(4)
    pick_photo("foto_ancha", 2)
    pick_photo("foto_alta", 1)
    stars = [n for n in nodes() if n["rid"].endswith("IV_diary_photo_thumbnail")]
    log("   stars found: %d" % len(stars))
    show("photos_in_diary", full=True)
    if len(stars) >= 2:
        tap_xy(stars[1]["cx"], stars[1]["cy"])
        sleep(2)
        show("second_star_pressed")
    # scroll to the top to see the cover
    adb("shell", "input", "swipe", "540", "500", "540", "1900", "300")
    sleep(1)
    show("cover_with_photo", full=True)


def pull_db(dest_dir):
    """Copies the database of the app (debug build) to the computer, with its -wal / -shm files."""
    os.makedirs(dest_dir, exist_ok=True)
    got = []
    for name in ("mydiary.db", "mydiary.db-wal", "mydiary.db-shm"):
        r = subprocess.run(["adb", "exec-out", "run-as", PKG, "cat", "databases/" + name], capture_output=True)
        if r.stdout:
            with open("%s/%s" % (dest_dir, name), "wb") as f:
                f.write(r.stdout)
            got.append(name)
    return "%s/mydiary.db" % dest_dir if "mydiary.db" in got else None


def db_check():
    """Prints the saved entries (to see the saved formats, the tags and the chosen cover photo)."""
    import sqlite3
    path = pull_db("%s/db" % OUT)
    if not path:
        log("   DB check: could not copy the database")
        return
    try:
        con = sqlite3.connect(path)
        for row in con.execute("select item_ref_diary_id, diary_item_type, diary_item_content from diary_item_entry_v2 order by item_ref_diary_id, diary_item_position"):
            log("   DB item: %r" % (row,))
        for row in con.execute("select _id, diary_title, diary_tags, diary_location, diary_thumbnail from diary_entry_v2"):
            log("   DB entry: %r" % (row,))
        con.close()
    except Exception as e:
        log("   DB check failed: %r" % (e,))


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
        alive("viewer")
        # open the first photo: the complete picture must be seen (no cut)
        photo = find(rid="SDV_diary_new_photo")
        if photo:
            tap_xy(photo["cx"], photo["cy"])
            sleep(4)
            show("photo_opened", full=True)
            alive("photo opened")
            key(4)
            sleep(2)
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
    return tap(text="save")


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
step("search", search_test)
step("drag topics", drag_test)
step("relaunch", restart)
step("write an entry", write_entry)
step("open the entries", open_entries)
def seed_db():
    """Adds many entries (2024-2026) to the diary topic, straight into the database, to try the scroll bar."""
    import sqlite3, datetime
    adb("shell", "am", "force-stop", PKG)
    sleep(2)
    path = pull_db("%s/seed" % OUT)
    if not path:
        log("   seed: could not copy the database")
        return False
    con = sqlite3.connect(path)
    # the topic that already has the sample entry is the one called DIARY (the one the steps open)
    topic = con.execute("select diary_ref_topic_id from diary_entry_v2 order by _id limit 1").fetchone()
    log("   seed: topics = %s" % con.execute("select _id, topic_name, topic_type from topic_entry").fetchall())
    if not topic:
        topic = con.execute("select _id from topic_entry where topic_type = 1 order by _id limit 1").fetchone()
    if not topic:
        log("   seed: no diary topic")
        return False
    topic_id = topic[0]
    start = datetime.datetime(2024, 1, 5, 12, 0)
    for i in range(60):
        when = start + datetime.timedelta(days=i * 17)
        title = "Nota de prueba %d" % (i + 1)
        text = "Texto numero %d de la prueba" % (i + 1)
        if i == 16:
            text = "Hoy vi una mariposa muy bonita en el jardin"
        if i == 33:
            title = "Mariposa azul"
        if i == 45:
            text = "La CANCION favorita de la ma\u00f1ana"
        cur = con.execute("insert into diary_entry_v2 (diary_time, diary_title, diary_mood, diary_weather, diary_attachment, diary_ref_topic_id, diary_location, diary_tags, diary_thumbnail) values (?,?,?,?,?,?,?,?,?)",
                          (int(when.timestamp() * 1000), title, 0, 0, 0, topic_id, "", ",prueba," if i % 3 == 0 else "", ""))
        con.execute("insert into diary_item_entry_v2 (diary_item_type, diary_item_position, diary_item_content, item_ref_diary_id) values (0, 0, ?, ?)", (text, cur.lastrowid))
    # a second diary (to try move / copy)
    color = con.execute("select topic_color from topic_entry where _id = ?", (topic_id,)).fetchone()
    cur = con.execute("insert into topic_entry (topic_name, topic_type, topic_color) values (?,?,?)",
                      ("Otro diario", 1, color[0] if color else -12627531))
    other_topic = cur.lastrowid
    con.execute("insert into topic_order (topic_order_order_in_parent, topic_order_ref_topic_id) values (?,?)", (0, other_topic))
    log("   seed: second diary 'Otro diario' = topic %s" % other_topic)
    # "a day like today": the same day, one and two years ago
    now = datetime.datetime.now().replace(hour=12, minute=0, second=0, microsecond=0)
    for years, title, text in ((1, "Recuerdo de hace un a\u00f1o", "Hace un a\u00f1o escribi esto en la playa"),
                               (2, "Recuerdo de hace dos a\u00f1os", "Dos a\u00f1os atras fue un dia muy bonito")):
        try:
            when = now.replace(year=now.year - years)
        except ValueError:
            when = now.replace(year=now.year - years, day=28)
        cur = con.execute("insert into diary_entry_v2 (diary_time, diary_title, diary_mood, diary_weather, diary_attachment, diary_ref_topic_id, diary_location, diary_tags, diary_thumbnail) values (?,?,?,?,?,?,?,?,?)",
                          (int(when.timestamp() * 1000), title, 0, 0, 0, topic_id, "", "", ""))
        con.execute("insert into diary_item_entry_v2 (diary_item_type, diary_item_position, diary_item_content, item_ref_diary_id) values (0, 0, ?, ?)", (text, cur.lastrowid))
    # entries of the last days, with a photo each (for the month calendar)
    global SEED_PHOTOS
    SEED_PHOTOS = []
    for k, days in enumerate((1, 2, 4)):
        when = now - datetime.timedelta(days=days)
        name = "seedphoto%d.png" % k
        cur = con.execute("insert into diary_entry_v2 (diary_time, diary_title, diary_mood, diary_weather, diary_attachment, diary_ref_topic_id, diary_location, diary_tags, diary_thumbnail) values (?,?,?,?,?,?,?,?,?)",
                          (int(when.timestamp() * 1000), "Foto del dia %d" % (k + 1), 0, 0, 1, topic_id, "", "", ""))
        con.execute("insert into diary_item_entry_v2 (diary_item_type, diary_item_position, diary_item_content, item_ref_diary_id) values (1, 0, ?, ?)", (name, cur.lastrowid))
        con.execute("insert into diary_item_entry_v2 (diary_item_type, diary_item_position, diary_item_content, item_ref_diary_id) values (0, 1, ?, ?)", (" Texto de la foto %d" % (k + 1), cur.lastrowid))
        SEED_PHOTOS.append((topic_id, cur.lastrowid, name, k))
    con.commit()
    con.execute("pragma wal_checkpoint(truncate)")
    con.execute("pragma journal_mode=delete")
    con.commit()
    con.close()
    # put it back: one single file, no -wal / -shm
    adb("shell", "run-as", PKG, "rm", "-f", "databases/mydiary.db-wal", "databases/mydiary.db-shm")
    # one single string for the phone shell (otherwise the quotes of sh -c are lost and nothing is written)
    with open(path, "rb") as f:
        r = subprocess.run(["adb", "shell", "run-as %s sh -c 'cat > databases/mydiary.db'" % PKG],
                           stdin=f, capture_output=True, text=True)
    log("   seed push: rc=%s err=%r" % (r.returncode, (r.stderr or "")[:200]))
    size = adb("shell", "run-as", PKG, "ls", "-l", "databases/").strip()
    log("   seed: databases/ = %s" % size.replace("\n", " | ")[:300])
    log("   seed: 60 entries added to topic %s" % topic_id)
    # the photos of the entries with a photo go to the folder of each entry: diary/topic/entry/
    for topic, diary, name, k in SEED_PHOTOS:
        local = "%s/%s" % (OUT, name)
        make_png(local, 400, 300, ((60 + k * 70) % 200, 40 + k * 50, 160 - k * 40))
        folder = "/sdcard/Android/data/%s/files/diary/%s/%s" % (PKG, topic, diary)
        adb("shell", "mkdir", "-p", folder)
        out = subprocess.run(["adb", "push", local, folder + "/" + name], capture_output=True, text=True)
        log("   seed photo: %s -> rc=%s %s" % (folder + "/" + name, out.returncode, (out.stderr or out.stdout).strip()[:120]))
    return True


def seed_and_scroll():
    """The list of entries with many entries: month names, the year chip and the scroll bar with its label."""
    if not seed_db():
        return
    # the first time the app is opened in a day, the memories of this day (other years) are shown by themselves
    restart("after_seed_first_open")
    n = find(text="DIARY")
    if not n:
        show("seed_no_diary_topic", full=True)
        return
    tap_xy(n["cx"], n["cy"])
    sleep(3)
    dismiss_intro()
    tap(rid="But_diary_topbar_entries")
    sleep(2)
    show("seed_entries_list", full=True)
    alive("seed list")
    rv = find(rid="RecyclerView_entries")
    if not rv:
        return
    x1, y1, x2, y2 = rv["b"]
    # a small scroll: the handle of the scroll bar appears on the right side
    adb("shell", "input", "swipe", str(x2 // 2), str(y2 - 200), str(x2 // 2), str(y2 - 500), "250")
    sleep(0.3)
    show("seed_scrolled_handle")
    # drag the handle (at the right edge, near the top) down along the edge
    proc = subprocess.Popen(["adb", "shell", "input", "swipe", str(x2 - 12), str(y1 + 45), str(x2 - 12), str(y1 + (y2 - y1) // 2), "2500"])
    sleep(1.5)
    show("seed_dragging_handle")
    proc.wait()
    sleep(1)
    show("seed_after_drag")
    alive("seed drag")


def global_search_test():
    """Search in the main page: a word that is in the text of a note and a word that is in a title."""
    restart()
    for word in ("mariposa", "cancion"):
        if not tap(rid="EDT_main_topic_search"):
            return
        type_text(word)
        sleep(3)
        show("search_notes_%s" % word, full=True)
        alive("search %s" % word)
        if word == "mariposa":
            n = find(text="Mariposa azul") or find(text="mariposa")
            if n:
                tap_xy(n["cx"], n["cy"])
                sleep(4)
                show("search_opened_note", full=True)
                alive("search open note")
                key(4)
                sleep(1)
                key(4)
                sleep(2)
        # clear the box
        tap(rid="EDT_main_topic_search")
        for _ in range(len(word) + 1):
            key(67)
        sleep(1)
    # leave the app inside the topic, like the steps that follow expect
    restart()
    n = find(text="DIARY")
    if n:
        tap_xy(n["cx"], n["cy"])
        sleep(3)
        dismiss_intro()


def open_diary_topic():
    restart()
    n = find(text="DIARY")
    if not n:
        show("no_diary_topic", full=True)
        return False
    tap_xy(n["cx"], n["cy"])
    sleep(3)
    dismiss_intro()
    tap(rid="But_diary_topbar_entries")
    sleep(2)
    return True


def today_test():
    """The "a day like today" card of the main page, the list of memories, and opening one."""
    restart()
    show("main_today_card", full=True)
    if not tap(rid="LL_main_today"):
        log("   NO TODAY CARD")
        return
    sleep(2)
    show("today_dialog", full=True)
    n = find(text="Recuerdo de hace un a\u00f1o")
    if n:
        tap_xy(n["cx"], n["cy"])
        sleep(4)
        show("today_opened_entry", full=True)
        alive("today open")
        key(4)
        sleep(1)
        key(4)
        sleep(2)


def tap_menu_item(prefix):
    n = find(text=prefix)
    if not n:
        log("   menu item not found: %s" % prefix)
        show("menu_item_missing", full=True)
        return False
    tap_xy(n["cx"], n["cy"])
    sleep(1)
    return True


def move_copy_test():
    """Inside an entry: copy it to the other diary, then move it there (the photos go with it)."""
    if not open_diary_topic():
        return
    show("mc_entries", full=True)
    n = find(text="Prueba negrita")
    if not n:
        log("   mc: entry not found")
        return
    tap_xy(n["cx"], n["cy"])
    sleep(3)
    show("mc_viewer", full=True)
    if not tap(rid="IV_diary_move"):
        log("   NO MOVE BUTTON")
        return
    sleep(1)
    show("mc_menu", full=True)
    if tap_menu_item("Copy to") and find(text="Otro diario"):
        show("mc_copy_list", full=True)
        tap_menu_item("Otro diario")
        sleep(2)
        show("mc_copied", full=True)
    # now move the original
    if tap(rid="IV_diary_move"):
        sleep(1)
        if tap_menu_item("Move to"):
            show("mc_move_list", full=True)
            tap_menu_item("Otro diario")
            sleep(3)
            show("mc_after_move", full=True)
            alive("move")
    import sqlite3
    path = pull_db("%s/mc" % OUT)
    if path:
        con = sqlite3.connect(path)
        for row in con.execute("select d._id, d.diary_title, t.topic_name, d.diary_ref_topic_id from diary_entry_v2 d join topic_entry t on t._id = d.diary_ref_topic_id where d.diary_title = 'Prueba negrita'"):
            log("   mc DB: %r" % (row,))
        for row in con.execute("select item_ref_diary_id, diary_item_type, substr(diary_item_content, 1, 30) from diary_item_entry_v2 where item_ref_diary_id in (select _id from diary_entry_v2 where diary_title = 'Prueba negrita') order by item_ref_diary_id, diary_item_position"):
            log("   mc DB item: %r" % (row,))
        con.close()
    log("   mc files: %s" % adb("shell", "ls", "-R", "/sdcard/Android/data/%s/files/diary" % PKG).replace("\n", " | ")[:1500])
    # the other diary: the entry (and its copy) must be there with their photos
    restart()
    n = find(text="Otro diario")
    if n:
        tap_xy(n["cx"], n["cy"])
        sleep(3)
        dismiss_intro()
        tap(rid="But_diary_topbar_entries")
        sleep(2)
        show("mc_other_diary_list", full=True)
        n = find(text="Prueba negrita")
        if n:
            tap_xy(n["cx"], n["cy"])
            sleep(4)
            show("mc_other_viewer", full=True)
            alive("other viewer")


def calendar_month_test():
    """The month view of the calendar: days with a photo show the picture."""
    if not open_diary_topic():
        return
    if not tap(rid="But_diary_topbar_calendar"):
        return
    sleep(2)
    if not tap(rid="FAB_calendar_change_mode"):
        show("no_calendar_fab", full=True)
        return
    sleep(5)
    show("calendar_month", full=True)
    alive("calendar month")


step("seed and scroll", seed_and_scroll)
step("today", today_test)
step("calendar month", calendar_month_test)
step("move and copy", move_copy_test)
step("global search", global_search_test)
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
