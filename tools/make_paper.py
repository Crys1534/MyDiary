#!/usr/bin/env python3
"""
Makes the texture of the notebook sheet (Settings -> "Notebook sheet"): a small square of paper that the app repeats
like tiles. It is made here from random noise (nothing is downloaded or copied), and it is seamless: the right edge
continues into the left edge and the bottom into the top, so no seams are seen when it is repeated.

    python3 tools/make_paper.py

writes  app/src/main/res/drawable-nodpi/paper_tile.jpg        (light paper, cream)
        app/src/main/res/drawable-night-nodpi/paper_tile.jpg  (dark paper, used in dark mode)
(needs numpy, scipy and pillow)
"""
import os

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

SIZE = 512
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")


def periodic_noise(rng, sigma):
    """Soft noise whose edges match (it wraps around), with mean 0 and deviation 1."""
    noise = gaussian_filter(rng.standard_normal((SIZE, SIZE)), sigma, mode="wrap")
    return (noise - noise.mean()) / noise.std()


def fibers(rng, count):
    """Short thin strokes in every direction, like the fibers of paper (they wrap around the edges too)."""
    layer = np.zeros((SIZE, SIZE))
    for _ in range(count):
        x = rng.uniform(0, SIZE)
        y = rng.uniform(0, SIZE)
        angle = rng.uniform(0, np.pi)
        length = rng.uniform(6, 22)
        strength = rng.choice([-1.0, 1.0]) * rng.uniform(0.35, 1.0)
        steps = int(length * 2)
        curve = rng.uniform(-0.04, 0.04)
        for i in range(steps):
            t = i / 2.0
            a = angle + curve * t
            px = int(round(x + np.cos(a) * t)) % SIZE
            py = int(round(y + np.sin(a) * t)) % SIZE
            layer[py, px] += strength
    return gaussian_filter(layer, 0.6, mode="wrap")


def make(base, seed, mottling, grain, fiber_amount, fiber_count):
    rng = np.random.default_rng(seed)
    broad = periodic_noise(rng, 85)      # very large, very soft blotches
    cloud = periodic_noise(rng, 7)       # cloudy mottling
    fine = periodic_noise(rng, 0.7)      # grain of the paper
    fiber = fibers(rng, fiber_count)
    fiber = fiber / max(abs(fiber).max(), 1e-6)
    luminosity = 1.0 + mottling * (0.55 * broad + 0.45 * cloud) + grain * fine + fiber_amount * fiber
    rgb = np.array(base, dtype=float)[None, None, :] * luminosity[:, :, None]
    # the paper is a little warmer where it is brighter (a touch of yellow in the light parts)
    warm = (luminosity - 1.0) * 0.35
    rgb[:, :, 2] *= (1.0 - warm * 0.6)
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))


def save(image, folder):
    path = os.path.join(RES, folder)
    os.makedirs(path, exist_ok=True)
    out = os.path.join(path, "paper_tile.jpg")
    image.save(out, quality=90, optimize=True)
    print("wrote", os.path.normpath(out), os.path.getsize(out) // 1024, "KB")


if __name__ == "__main__":
    # light paper: warm cream
    save(make((250, 246, 233), seed=11, mottling=0.022, grain=0.016, fiber_amount=0.05, fiber_count=2300),
         "drawable-nodpi")
    # dark paper: charcoal with a brown tint (the same texture, a little lighter fibers)
    save(make((43, 41, 37), seed=23, mottling=0.08, grain=0.05, fiber_amount=0.15, fiber_count=2300),
         "drawable-night-nodpi")
