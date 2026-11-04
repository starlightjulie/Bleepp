"""Synthesises Bleepp's built-in alert sounds into app/src/main/res/raw. Run: python tools/sounds/gen.py

Every sound is made here from plain sine waves, so they're original and covered by the project's licence.
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "res", "raw")


def tone(freq, length, level=0.8, attack=0.006, decay=None, harmonics=((1, 1.0),)):
    """One note. With decay set it rings out; without, it holds and stops cleanly."""
    n = int(RATE * length)
    total = sum(a for _, a in harmonics)
    samples = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * min(1.0, (length - t) / 0.012)
        if decay:
            env *= math.exp(-t / decay)
        v = sum(a * math.sin(2 * math.pi * freq * h * t) for h, a in harmonics) / total
        samples.append(level * env * v)
    return samples


def rest(length):
    return [0.0] * int(RATE * length)


def glide(start, end, length, level=0.8):
    n = int(RATE * length)
    phase = 0.0
    samples = []
    for i in range(n):
        t = i / RATE
        freq = start * (end / start) ** (t / length)
        phase += 2 * math.pi * freq / RATE
        env = min(1.0, t / 0.01) * min(1.0, (length - t) / 0.03)
        samples.append(level * env * math.sin(phase))
    return samples


def mix(*tracks):
    size = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(size)]


def delayed(track, seconds):
    return rest(seconds) + track


BELL = ((1, 1.0), (2.01, 0.5), (3.02, 0.25), (4.2, 0.12))


def bleep():
    return tone(1320, 0.11) + rest(0.05) + tone(1760, 0.16)


def pager():
    return (tone(2093, 0.09) + rest(0.06)) * 3 + rest(0.14) + (tone(2093, 0.09) + rest(0.06)) * 3


def sonar():
    return mix(tone(1046, 1.5, decay=0.42, harmonics=((1, 1.0), (2, 0.15))), delayed(tone(1046, 1.1, 0.25, decay=0.3), 0.42))


def harbour_bell():
    return mix(tone(587, 1.9, decay=0.6, harmonics=BELL), delayed(tone(587, 1.5, 0.6, decay=0.5, harmonics=BELL), 0.5))


def rising():
    return glide(520, 1560, 0.5) + rest(0.06) + tone(1560, 0.16)


def foghorn():
    return tone(165, 1.1, harmonics=((1, 1.0), (2, 0.6), (3, 0.35)), attack=0.08)


def drops():
    random.seed(7)
    out = []
    for f in (1480, 1110, 1660, 1245):
        out += tone(f, 0.15, decay=0.05) + rest(0.05)
    return out


def morse():
    dot, dash, gap = tone(880, 0.08), tone(880, 0.24), rest(0.08)
    return dash + gap + dot + gap + dot + gap + dot


SOUNDS = {
    "bleep": bleep,
    "pager": pager,
    "sonar": sonar,
    "harbour_bell": harbour_bell,
    "rising": rising,
    "foghorn": foghorn,
    "drops": drops,
    "morse": morse,
}

os.makedirs(OUT, exist_ok=True)
for name, make in SOUNDS.items():
    samples = make()
    peak = max(abs(s) for s in samples) or 1
    data = b"".join(struct.pack("<h", int(s / peak * 0.9 * 32767)) for s in samples)
    with wave.open(os.path.join(OUT, f"snd_{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    print(f"snd_{name}.wav  {len(samples) / RATE:.2f}s  {len(data) // 1024} KB")
