#!/usr/bin/env python3
"""
Makes the small, quiet sound effects of the diary (notebook pages, pen, stamp...).

Nothing is downloaded or copied: every sound is made here from filtered noise and soft tones, so there is no
license to worry about.  Run it from the root of the project:

    python3 tools/make_sounds.py

and it writes app/src/main/res/raw/sfx_*.ogg  (needs numpy, scipy and ffmpeg).
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(1534)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def bandpass(x, lo, hi, order=3):
    hi = min(hi, SR / 2 - 100)
    sos = signal.butter(order, [lo, hi], btype="bandpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, hi, order=3):
    sos = signal.butter(order, hi, btype="lowpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, lo, order=2):
    sos = signal.butter(order, lo, btype="highpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def white(seconds):
    return rng.standard_normal(int(seconds * SR))


def smooth_env(n, attack, release):
    """0 -> 1 -> 0 envelope with a soft shape (times in seconds)."""
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    env = np.zeros(n)
    a = min(a, n)
    env[:a] = np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    rest = n - a
    if rest > 0:
        r = min(r, rest)
        env[a:a + r] = np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    return env


def decay(seconds, tau):
    t = t_axis(seconds)
    return np.exp(-t / tau)


def tone(freq, seconds, tau, phase=0.0):
    t = t_axis(seconds)
    return np.sin(2 * np.pi * freq * t + phase) * np.exp(-t / tau)


def crackle(seconds, density, lo=1800, hi=9000, strength=1.0, shape=None):
    """Small random clicks, like paper fibres: `density` clicks per second (can change in time with `shape`)."""
    n = int(seconds * SR)
    prob = np.full(n, density / SR)
    if shape is not None:
        prob = prob * np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(shape)), shape)
    hits = rng.random(n) < prob
    amps = rng.random(n) ** 2 * strength
    sig = np.where(hits, amps * rng.choice([-1.0, 1.0], n), 0.0)
    # every click rings a little in the high band
    ring = signal.lfilter([1.0], [1.0, -0.82], sig)
    return bandpass(ring, lo, hi, 2)


def at(buf, sound, start):
    """Adds `sound` into `buf` beginning at `start` seconds (the buffer grows if needed)."""
    s = int(start * SR)
    end = s + len(sound)
    if end > len(buf):
        buf = np.concatenate([buf, np.zeros(end - len(buf))])
    buf[s:end] += sound
    return buf


def thump(f0, f1, seconds, tau, noise_amount=0.15, noise_hi=700):
    t = t_axis(seconds)
    freq = f1 + (f0 - f1) * np.exp(-t / (seconds / 4))
    phase = 2 * np.pi * np.cumsum(freq) / SR
    body = np.sin(phase) * np.exp(-t / tau)
    body += noise_amount * lowpass(white(seconds), noise_hi) * np.exp(-t / (tau * 0.7))
    return body


def fade(x, ms=3):
    f = int(ms / 1000 * SR)
    f = min(f, len(x) // 2)
    if f > 0:
        x[:f] *= np.linspace(0, 1, f)
        x[-f:] *= np.linspace(1, 0, f)
    return x


def finish(x, peak=0.38):
    x = x - np.mean(x)
    x = highpass(x, 60, 1)
    x = fade(x, 4)
    m = np.max(np.abs(x))
    if m > 0:
        x = x / m * peak
    return x


# ---------------------------------------------------------------- the sounds

def page_flip():
    """A page of a notebook is turned: a soft whoosh, paper fibres, and the page landing."""
    d = 0.62
    n = int(d * SR)
    low = bandpass(white(d), 500, 2200)
    high = bandpass(white(d), 2500, 9500)
    x = np.linspace(0, 1, n)
    mix = np.clip((x - 0.1) / 0.5, 0, 1)
    body = low * (1 - mix) * 0.9 + high * mix
    body *= smooth_env(n, 0.09, 0.45) ** 1.3
    # the paper shakes: slow irregular wobble
    wobble = 0.75 + 0.25 * lowpass(white(d), 18)[:n] / 0.5
    body *= np.clip(wobble, 0.4, 1.2)
    out = body * 0.6
    out = at(out, crackle(0.34, 160, 2500, 9500, 0.9, [0.2, 1, 0.8, 0.3, 0.1]), 0.02)
    # landing
    land = thump(170, 90, 0.09, 0.025, 0.4, 1200) * 0.7
    out = at(out, land, 0.46)
    return finish(out)


def page_turn_soft():
    """A quicker, gentler page turn (for tabs and small things)."""
    d = 0.34
    n = int(d * SR)
    body = bandpass(white(d), 1200, 7500) * smooth_env(n, 0.05, 0.25) ** 1.5
    out = body * 0.55
    out = at(out, crackle(0.2, 140, 2500, 9000, 0.8, [0.3, 1, 0.5, 0.1]), 0.0)
    out = at(out, thump(190, 110, 0.06, 0.02, 0.35, 1400) * 0.35, 0.26)
    return finish(out, 0.3)


def paper_rustle():
    d = 0.3
    out = crackle(d, 380, 2000, 9500, 1.0, [0.3, 1, 0.7, 1, 0.4, 0.1])
    out += bandpass(white(d), 2500, 8000) * smooth_env(int(d * SR), 0.08, 0.2) * 0.12
    return finish(out, 0.3)


def paper_slide():
    d = 0.34
    n = int(d * SR)
    body = bandpass(white(d), 900, 4200) * smooth_env(n, 0.12, 0.22) ** 1.2
    out = body * 0.5 + crackle(d, 90, 2000, 7000, 0.35, [0.2, 0.8, 0.5, 0.1])
    return finish(out, 0.27)


def notebook_open():
    """The cover is lifted (a soft thump) and the first page falls."""
    out = thump(130, 80, 0.2, 0.06, 0.2, 900) * 0.8
    out = at(out, page_flip() * 0.9, 0.09)
    return finish(out, 0.4)


def notebook_close():
    out = thump(150, 70, 0.28, 0.08, 0.3, 1000) * 1.0
    out = at(out, crackle(0.15, 200, 2000, 8000, 0.6, [1, 0.5, 0.1]), 0.01)
    out = at(out, bandpass(white(0.12), 700, 2500) * smooth_env(int(0.12 * SR), 0.02, 0.1) * 0.35, 0.02)
    return finish(out, 0.4)


def pen_click():
    """A ballpoint pen: one clear click."""
    d = 0.1
    out = tone(2300, d, 0.007) * 0.8 + tone(3500, d, 0.005) * 0.5 + tone(1250, d, 0.012) * 0.4
    click = np.zeros(int(d * SR))
    c = highpass(white(0.004), 2500) * np.exp(-t_axis(0.004) / 0.0012)
    click[: len(c)] += c * 1.3
    out = out + click
    out = at(out, tone(900, 0.05, 0.01) * 0.25, 0.045)
    return finish(out, 0.33)


def pen_tick():
    d = 0.07
    out = tone(1750, d, 0.009) * 0.8 + tone(2650, d, 0.006) * 0.4
    c = highpass(white(0.003), 2000) * np.exp(-t_axis(0.003) / 0.001)
    out[: len(c)] += c * 0.9
    return finish(out, 0.28)


def pencil_tap():
    """A pencil tapping wood: a soft low tick."""
    d = 0.06
    out = tone(820, d, 0.012) * 0.9 + tone(1650, d, 0.007) * 0.35
    c = bandpass(white(0.004), 1200, 5000) * np.exp(-t_axis(0.004) / 0.0013)
    out[: len(c)] += c * 0.8
    return finish(out, 0.26)


def check_tick():
    """Ticking a box of the checklist: two tiny rising ticks."""
    out = pen_tick() * 0.7
    out = at(out, (tone(2300, 0.08, 0.012) * 0.8 + tone(3450, 0.08, 0.008) * 0.35), 0.06)
    return finish(out, 0.3)


def pen_write():
    """A pen scribbling a short line."""
    d = 0.5
    n = int(d * SR)
    out = np.zeros(n)
    strokes = [(0.02, 0.15), (0.2, 0.14), (0.38, 0.11)]
    for start, length in strokes:
        m = int(length * SR)
        s = int(start * SR)
        grain = bandpass(white(length), 2800, 9000)
        # the stroke is made of a fast scratchy rhythm
        rhythm = 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * rng.uniform(38, 55) * t_axis(length)))
        out[s:s + m] += grain * rhythm * smooth_env(m, 0.03, 0.1)
    return finish(out, 0.28)


def stamp_save():
    """Saved: a soft stamp on paper and a tiny bell."""
    out = thump(210, 95, 0.16, 0.045, 0.45, 1400) * 0.9
    out = at(out, crackle(0.08, 300, 2500, 9000, 0.5, [1, 0.2]), 0.0)
    bell = tone(1760, 0.5, 0.16) * 0.28 + tone(2637, 0.5, 0.1) * 0.09 + tone(3520, 0.5, 0.06) * 0.04
    out = at(out, bell, 0.07)
    return finish(out, 0.36)


def crumple():
    """Paper in a hand: many small cracks, getting rarer."""
    d = 0.7
    n = int(d * SR)
    out = crackle(d, 900, 1500, 9000, 1.0, [0.5, 1, 0.9, 0.7, 0.45, 0.25, 0.1])
    body = bandpass(white(d), 1200, 6000) * smooth_env(n, 0.06, 0.55) * 0.15
    # irregular bursts
    bursts = np.clip(lowpass(white(d), 14), -1, 1) * 0.5 + 0.6
    out = out * np.clip(bursts, 0.2, 1.4) + body
    return finish(out, 0.38)


def chime_star():
    """A very small glockenspiel note (the star of the cover)."""
    d = 0.7
    out = tone(1568, d, 0.2) * 0.8 + tone(3136, d, 0.1) * 0.22 + tone(4700, d, 0.05) * 0.08
    out += tone(1568 * 1.5, d, 0.14) * 0.12
    return finish(out, 0.3)


def photo_snap():
    """A photo is added: a small shutter and a print sliding."""
    out = np.zeros(int(0.6 * SR))
    c1 = bandpass(white(0.02), 900, 6000) * np.exp(-t_axis(0.02) / 0.004)
    out = at(out, c1 * 1.0, 0.0)
    out = at(out, tone(1400, 0.05, 0.01) * 0.3, 0.0)
    c2 = bandpass(white(0.025), 700, 5000) * np.exp(-t_axis(0.025) / 0.005)
    out = at(out, c2 * 0.9, 0.055)
    out = at(out, paper_slide() * 0.6, 0.14)
    return finish(out, 0.34)


def pop_soft():
    """A bubble: tags, places, little confirmations."""
    d = 0.14
    t = t_axis(d)
    freq = 520 + 520 * (1 - np.exp(-t / 0.02))
    phase = 2 * np.pi * np.cumsum(freq) / SR
    out = np.sin(phase) * np.exp(-t / 0.03)
    return finish(out, 0.26)


def tab_tick():
    d = 0.12
    out = bandpass(white(d), 600, 3500) * np.exp(-t_axis(d) / 0.016) * 0.8
    out = at(out, thump(240, 130, 0.05, 0.014, 0.0, 800) * 0.35, 0.0)
    out = at(out, crackle(0.05, 500, 2500, 8000, 0.5, [1, 0.2]), 0.0)
    return finish(out, 0.28)


def lock_click():
    """A little diary lock opening: two metal clicks."""
    out = np.zeros(int(0.5 * SR))
    for start, f, amp in [(0.0, 1500, 1.0), (0.11, 2100, 0.8)]:
        out = at(out, (tone(f, 0.08, 0.006) * 0.8 + tone(f * 2.1, 0.08, 0.004) * 0.4
                       + tone(f * 0.5, 0.08, 0.01) * 0.3) * amp, start)
        c = highpass(white(0.003), 3000) * np.exp(-t_axis(0.003) / 0.001)
        out = at(out, c * amp * 1.2, start)
    out = at(out, thump(160, 90, 0.08, 0.02, 0.2, 900) * 0.4, 0.12)
    return finish(out, 0.34)


def whoosh_close():
    """Closing a window: the page goes back."""
    d = 0.28
    n = int(d * SR)
    body = bandpass(white(d), 1500, 6000) * smooth_env(n, 0.03, 0.22)
    return finish(body * 0.5 + crackle(d, 120, 2500, 8000, 0.4, [1, 0.4, 0.1]), 0.26)


# ---------------------------------------------------------------- esoteric theme

def eso_purr():
    """A sleeping cat purrs: low, soft, rumbling at about 25 pulses per second."""
    d = 1.6
    n = int(d * SR)
    t = t_axis(d)
    rumble = lowpass(white(d), 260, 2) + 0.5 * np.sin(2 * np.pi * 52 * t)
    pulse = 0.55 + 0.45 * np.sin(2 * np.pi * 25 * t)
    out = rumble * pulse * smooth_env(n, 0.25, 1.2)
    return finish(out, 0.34)


def eso_bell():
    """A small mystic bell (crystal ball, moon): clear, with a long tail."""
    d = 2.0
    out = tone(880, d, 0.7) * 0.7 + tone(880 * 2.76, d, 0.35) * 0.3 + tone(880 * 5.4, d, 0.12) * 0.12
    out += tone(1320, d, 0.5) * 0.25
    return finish(out, 0.3)


def eso_shimmer():
    """Magic dust: a quick rising arpeggio of tiny bells."""
    out = np.zeros(int(1.4 * SR))
    for i, f in enumerate([1319, 1568, 1976, 2349, 2637]):
        out = at(out, (tone(f, 0.7, 0.18) * 0.6 + tone(f * 2, 0.7, 0.08) * 0.15) * (0.9 - i * 0.1), i * 0.07)
    return finish(out, 0.28)


def eso_candle_out():
    """A candle is blown out: a breath and a thin hiss."""
    d = 0.7
    n = int(d * SR)
    breath = bandpass(white(d), 500, 3500) * smooth_env(n, 0.04, 0.2) * 0.8
    hiss = highpass(white(d), 4000) * smooth_env(n, 0.2, 0.45) * 0.25
    return finish(breath + hiss, 0.3)


def eso_candle_on():
    """A match strikes and the flame catches."""
    out = np.zeros(int(0.9 * SR))
    scratch = bandpass(white(0.18), 1500, 7000) * smooth_env(int(0.18 * SR), 0.1, 0.06)
    out = at(out, scratch * 0.7, 0.0)
    whoomp = lowpass(white(0.35), 900) * smooth_env(int(0.35 * SR), 0.03, 0.3)
    out = at(out, whoomp * 0.9, 0.2)
    out = at(out, crackle(0.5, 40, 1500, 6000, 0.6, [1, 0.7, 0.2]), 0.3)
    return finish(out, 0.3)


def eso_cat_meow():
    """A very small, sleepy meow."""
    d = 0.55
    n = int(d * SR)
    t = t_axis(d)
    f = 520 + 380 * np.sin(np.pi * np.clip(t / d, 0, 1)) ** 1.5
    phase = 2 * np.pi * np.cumsum(f) / SR
    voice = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.3 * np.sin(3 * phase)
    voice = bandpass(voice, 400, 3200, 2) * smooth_env(n, 0.12, 0.38)
    return finish(voice, 0.3)


def _loop_fade(x, fade_s=1.5):
    """Makes a long sound loop without a click: the end is mixed into the beginning."""
    f = int(fade_s * SR)
    head = x[:f] * np.linspace(0, 1, f) + x[-f:] * np.linspace(1, 0, f)
    return np.concatenate([head, x[f:-f]])


def eso_parchment():
    """Old parchment turning: slow, dry, a little crackly."""
    d = 0.75
    n = int(d * SR)
    body = bandpass(white(d), 500, 3500) * smooth_env(n, 0.18, 0.5) * 0.35
    body += crackle(d, 160, 1500, 7000, 0.9, [0.3, 1, 0.7, 0.2])
    body += lowpass(white(d), 300) * smooth_env(n, 0.3, 0.4) * 0.15
    return finish(body, 0.34)


def eso_bowl():
    """A singing bowl: a soft strike and a long warm ring."""
    d = 3.2
    t = t_axis(d)
    out = np.zeros(int(d * SR))
    for f, a, tau in [(294, 1.0, 1.4), (294 * 2.71, 0.45, 0.8), (294 * 5.1, 0.15, 0.4), (297, 0.7, 1.3)]:
        out += a * np.sin(2 * np.pi * f * t) * np.exp(-t / tau)
    out *= (1 + 0.08 * np.sin(2 * np.pi * 3.1 * t))
    return finish(out, 0.32)


def eso_raven():
    """A raven calling, far away and very quiet: a rough 'kraa'."""
    out = np.zeros(int(1.0 * SR))
    for start, dur, f0 in [(0.0, 0.28, 520), (0.4, 0.22, 470)]:
        n = int(dur * SR)
        t = t_axis(dur)
        f = f0 * (1 - 0.35 * t / dur)
        phase = 2 * np.pi * np.cumsum(f) / SR
        rough = np.sign(np.sin(phase * 1.0)) * 0.4 + np.sin(phase) * 0.6
        rough *= 0.7 + 0.3 * np.sin(2 * np.pi * 70 * t)
        voice = bandpass(rough + 0.15 * white(dur), 350, 2600, 2) * smooth_env(n, 0.03, dur * 0.8)
        out = at(out, voice, start)
    return finish(lowpass(out, 3500), 0.22)


def eso_chimes():
    """Wind chimes: a few bright metal notes at random times."""
    out = np.zeros(int(3.0 * SR))
    notes = [1568, 1760, 2093, 2349, 2637, 1397]
    times = [0.0, 0.22, 0.5, 0.95, 1.2, 1.9]
    for i, (f, st) in enumerate(zip(notes, times)):
        amp = 0.5 + 0.4 * rng.random()
        out = at(out, (tone(f, 1.6, 0.45) + 0.3 * tone(f * 2.76, 1.6, 0.2)) * amp, st)
    return finish(out, 0.26)


def eso_whisper():
    """Far away whispers: breath-shaped noise that swells twice."""
    d = 2.2
    n = int(d * SR)
    t = t_axis(d)
    breath = bandpass(white(d), 1800, 6500, 2)
    voice = (0.5 + 0.5 * np.sin(2 * np.pi * 5 * t + 1.0)) ** 2 + 0.3
    swell = smooth_env(n, 0.8, 1.2) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.9 * t))
    return finish(breath * voice * swell, 0.2)


def eso_quill():
    """A quill scratching the paper: short dry scratches."""
    d = 0.32
    n = int(d * SR)
    s = bandpass(white(d), 2500, 8500, 2) * smooth_env(n, 0.05, 0.2)
    s *= np.clip(lowpass(white(d), 40) * 6 + 0.9, 0.2, 1.5)
    return finish(s, 0.26)


def eso_thunder():
    """Thunder far away: a long, low rumble."""
    d = 3.6
    n = int(d * SR)
    t = t_axis(d)
    rumble = lowpass(white(d), 140, 3) * 6
    rumble *= smooth_env(n, 0.5, 3.0) * (0.7 + 0.3 * np.sin(2 * np.pi * 1.7 * t))
    crack = bandpass(white(0.3), 600, 3000) * np.exp(-t_axis(0.3) / 0.08) * 0.5
    return finish(at(rumble, crack, 0.0), 0.4)


def eso_heartbeat():
    """A slow heartbeat: lub-dub."""
    out = np.zeros(int(1.1 * SR))
    out = at(out, thump(75, 45, 0.18, 0.05, 0.05, 150), 0.0)
    out = at(out, thump(65, 40, 0.16, 0.045, 0.05, 150) * 0.7, 0.24)
    return finish(out, 0.4)


def eso_ambient_music():
    """Calm music for the background: a warm pad and slow harp-like notes, 36 s that loop."""
    d = 36.0
    n = int(d * SR)
    t = t_axis(d)
    pad = np.zeros(n)
    chords = [(220.0, 261.63, 329.63), (196.0, 246.94, 293.66), (174.61, 220.0, 261.63), (196.0, 246.94, 329.63)]
    seg = n // len(chords)
    for i, ch in enumerate(chords):
        tt = t[:seg]
        env = np.sin(np.linspace(0, np.pi, seg)) ** 1.5
        part = sum(np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * f * 2.003 * tt) for f in ch) * env
        pad[i * seg:(i + 1) * seg] += part * 0.12
    out = pad
    scale = [440.0, 523.25, 587.33, 659.25, 783.99, 880.0]
    for k in range(34):
        f = scale[int(rng.integers(0, len(scale)))] * (0.5 if k % 5 == 0 else 1)
        out = at(out, (tone(f, 2.4, 0.7) + 0.25 * tone(f * 2, 2.4, 0.3)) * 0.22, k * 1.05 + rng.random() * 0.25)
    out = out[:n]
    return _loop_fade(finish(out, 0.3))


def eso_ambient_rain():
    """Soft rain with a few far thunders, 30 s that loop."""
    d = 30.0
    n = int(d * SR)
    rain = bandpass(white(d), 1500, 9000, 2) * 0.35 + lowpass(white(d), 600) * 0.25
    rain *= 0.8 + 0.2 * lowpass(white(d), 0.7) * 3
    for st in (7.0, 19.0):
        th = lowpass(white(3.4), 130, 3) * 6 * smooth_env(int(3.4 * SR), 0.4, 3.0)
        rain = at(rain, th * 0.5, st)
    return _loop_fade(finish(rain[:n], 0.3))


def eso_ambient_clock():
    """An old clock ticking softly, 8 s that loop (60 ticks per minute is one per second)."""
    d = 8.0
    out = np.zeros(int(d * SR))
    for k in range(int(d)):
        tk = bandpass(white(0.03), 800, 4500) * np.exp(-t_axis(0.03) / 0.006)
        out = at(out, tk + thump(300, 180, 0.03, 0.01, 0.0, 800) * 0.4, k * 1.0)
        out = at(out, (bandpass(white(0.025), 700, 3800) * np.exp(-t_axis(0.025) / 0.005)) * 0.6, k * 1.0 + 0.5)
    return finish(out[:int(d * SR)], 0.25)


SOUNDS = {
    "sfx_page_flip": page_flip,
    "sfx_page_soft": page_turn_soft,
    "sfx_rustle": paper_rustle,
    "sfx_slide": paper_slide,
    "sfx_notebook_open": notebook_open,
    "sfx_notebook_close": notebook_close,
    "sfx_pen_click": pen_click,
    "sfx_pen_tick": pen_tick,
    "sfx_pencil_tap": pencil_tap,
    "sfx_check": check_tick,
    "sfx_pen_write": pen_write,
    "sfx_save": stamp_save,
    "sfx_crumple": crumple,
    "sfx_star": chime_star,
    "sfx_photo": photo_snap,
    "sfx_pop": pop_soft,
    "sfx_tab": tab_tick,
    "sfx_lock": lock_click,
    "sfx_close": whoosh_close,
    "sfx_eso_purr": eso_purr,
    "sfx_eso_bell": eso_bell,
    "sfx_eso_shimmer": eso_shimmer,
    "sfx_eso_candle_out": eso_candle_out,
    "sfx_eso_candle_on": eso_candle_on,
    "sfx_eso_meow": eso_cat_meow,
    "sfx_eso_parchment": eso_parchment,
    "sfx_eso_bowl": eso_bowl,
    "sfx_eso_raven": eso_raven,
    "sfx_eso_chimes": eso_chimes,
    "sfx_eso_whisper": eso_whisper,
    "sfx_eso_quill": eso_quill,
    "sfx_eso_thunder": eso_thunder,
    "sfx_eso_heartbeat": eso_heartbeat,
    "eso_amb_music": eso_ambient_music,
    "eso_amb_rain": eso_ambient_rain,
    "eso_amb_clock": eso_ambient_clock,
}


def write_ogg(name, samples):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        ogg_path = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "3",
                        ogg_path], check=True)
    return ogg_path


def main():
    for name, fn in SOUNDS.items():
        samples = fn()
        path = write_ogg(name, samples)
        print("%-20s %.2fs  %6.1f KB" % (name, len(samples) / SR, os.path.getsize(path) / 1024))


if __name__ == "__main__":
    sys.exit(main())
