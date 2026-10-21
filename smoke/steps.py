#!/usr/bin/env python3
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from ui import *

os.makedirs(OUT, exist_ok=True)
log("== launch")
adb("shell", "am", "start", "-n", PKG + "/com.kiminonawa.mydiary.init.InitActivity")
sleep(10)
snap("launch")
dump("launch")
log("== done")
