#!/usr/bin/env python3
"""Синтез встроенных мелодий будильника (M4-T03, ADR-016 §2). Результат — CC0.

Зависимости: python3, numpy, системная libsndfile.so.1 (>= 1.0.29, с Vorbis).
Запуск:  python3 tools/sound/generate_builtin_sounds.py [выходной каталог]
По умолчанию пишет в core/alarm/src/main/res/raw. Детерминирован (фиксированный seed).
Нет Vorbis → пишет WAV 16 кГц моно и предупреждает (бюджет APK ≤ 3 МБ).
"""
import ctypes
import ctypes.util
import struct
import sys
import wave
from pathlib import Path

import numpy as np

SR = 32000
LOOP_SEC = 24.0
SF_FORMAT_OGG = 0x200000
SF_FORMAT_VORBIS = 0x0060
SFM_WRITE = 0x20
SFC_SET_VBR_ENCODING_QUALITY = 0x1300

rng = np.random.default_rng(20261009)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


class Track:
    """Буфер цикла: хвосты нот, вылезающие за конец, оборачиваются в начало → бесшовный loop."""

    def __init__(self, seconds=LOOP_SEC):
        self.n = int(seconds * SR)
        self.buf = np.zeros(self.n)

    def add(self, t0, sig):
        i = int(t0 * SR) % self.n
        end = i + len(sig)
        if end <= self.n:
            self.buf[i:end] += sig
        else:
            k = self.n - i
            self.buf[i:] += sig[:k]
            rest = sig[k:]
            self.buf[: len(rest)] += rest[: self.n]

    def finish(self, peak=0.85):
        x = self.buf
        return x / max(1e-9, np.abs(x).max()) * peak


def tvec(dur):
    return np.arange(int(dur * SR)) / SR


def env_perc(t, attack=0.005, decay=0.4):
    return np.minimum(t / attack, 1.0) * np.exp(-t / decay)


def tone_marimba(f, dur=1.2):
    t = tvec(dur)
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / 0.08)
    return s * env_perc(t, 0.004, 0.25)


def tone_bell(f, dur=4.0):
    t = tvec(dur)
    s = 0
    for ratio, amp, dec in [(1, 1, 1.6), (2.0, 0.6, 1.2), (2.76, 0.4, 0.9), (5.4, 0.25, 0.5), (8.93, 0.12, 0.3)]:
        s = s + amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t / dec)
    return s * np.minimum(t / 0.003, 1.0)


def tone_piano(f, dur=3.0):
    t = tvec(dur)
    s = 0
    for h in range(1, 7):
        s = s + (1.0 / h) * np.sin(2 * np.pi * f * h * t) * np.exp(-t / (1.4 / (1 + 0.6 * h)))
    return s * np.minimum(t / 0.004, 1.0)


def tone_square(f, dur=0.2, duty=0.5):
    t = tvec(dur)
    s = np.where((f * t) % 1.0 < duty, 1.0, -1.0)
    e = np.minimum(t / 0.003, 1.0) * np.minimum((dur - t) / 0.01, 1.0)
    return s * e * 0.5


def tone_pad(f, dur=4.0):
    t = tvec(dur)
    s = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * 2 * f * t * 1.002) + 0.25 * np.sin(2 * np.pi * 3 * f * t)
    e = np.minimum(t / 0.8, 1.0) * np.minimum((dur - t) / 1.2, 1.0)
    return s * e


PENT = [0, 2, 4, 7, 9]


def pent(i, base=60):
    return base + PENT[i % 5] + 12 * (i // 5)


def sunrise():
    tr = Track()
    seq = [pent(i, 55) for i in range(0, 10)]
    for k in range(12):
        tr.add(k * 2.0, tone_pad(midi(seq[k % len(seq)]), 4.5) * (0.5 + 0.5 * k / 12))
    for k in range(48):
        tr.add(k * 0.5, tone_bell(midi(pent(k * 2 % 10, 72)), 2.0) * 0.12)
    return tr.finish()


def marimba():
    tr = Track()
    melody = [0, 2, 4, 2, 0, 4, 7, 4, 2, 4, 5, 4, 2, 0, 2, 4]
    step = 0.25
    for k in range(int(LOOP_SEC / step)):
        n = 60 + melody[k % len(melody)]
        tr.add(k * step, tone_marimba(midi(n)) * (1.0 if k % 4 == 0 else 0.7))
    return tr.finish()


def bells():
    tr = Track()
    notes = [76, 79, 83, 79, 81, 76, 74, 79]
    for k in range(int(LOOP_SEC / 0.75)):
        tr.add(k * 0.75, tone_bell(midi(notes[k % len(notes)] - 12 * (k % 8 == 0))) * 0.8)
    return tr.finish()


def piano():
    tr = Track()
    chords = [(48, 55, 64, 67), (45, 52, 60, 64), (41, 48, 57, 60), (43, 50, 59, 62)]
    for c in range(8):
        base = c * 3.0
        for j, n in enumerate(chords[c % 4]):
            tr.add(base + j * 0.18, tone_piano(midi(n), 3.4) * 0.8)
        for j in range(4):
            tr.add(base + 1.5 + j * 0.375, tone_piano(midi(chords[c % 4][j] + 12), 1.8) * 0.5)
    return tr.finish()


def chiptune():
    tr = Track()
    arps = [(0, 4, 7, 12), (-3, 0, 4, 9), (-7, -3, 0, 5), (-5, -1, 2, 7)]
    step = 0.125
    for k in range(int(LOOP_SEC / step)):
        a = arps[(k // 16) % 4]
        tr.add(k * step, tone_square(midi(72 + a[k % 4]), 0.11, 0.25))
        if k % 8 == 0:
            tr.add(k * step, tone_square(midi(48 + a[0]), 0.5, 0.5) * 0.8)
    return tr.finish()


def digital():
    tr = Track()
    for k in range(int(LOOP_SEC / 1.0)):
        for j in range(4):
            tr.add(k * 1.0 + j * 0.12, tone_square(1760.0, 0.07, 0.5))
    return tr.finish()


def ascend():
    tr = Track()
    scale = [0, 2, 4, 5, 7, 9, 11, 12]
    cyc = 4.0
    for c in range(int(LOOP_SEC / cyc)):
        for j, d in enumerate(scale):
            tr.add(c * cyc + j * 0.35, tone_marimba(midi(60 + d), 1.0) * (0.5 + 0.07 * j))
        tr.add(c * cyc + 2.9, tone_bell(midi(84), 1.1) * 0.7)
    return tr.finish()


def chimes():
    tr = Track()
    t = 0.0
    while t < LOOP_SEC:
        tr.add(t, tone_bell(midi(pent(int(rng.integers(0, 10)), 74)), 3.5) * rng.uniform(0.4, 0.9))
        t += rng.uniform(0.25, 0.9)
    return tr.finish()


def siren():
    tr = Track()
    t = tvec(LOOP_SEC)
    f = 700 + 500 * np.sin(2 * np.pi * t / 1.2 - np.pi / 2)
    phase = 2 * np.pi * np.cumsum(f) / SR
    tr.buf = np.sin(phase) + 0.3 * np.sin(2 * phase)
    return tr.finish(0.8)


SOUNDS = {
    "snd_sunrise": sunrise, "snd_marimba": marimba, "snd_bells": bells, "snd_piano": piano,
    "snd_chiptune": chiptune, "snd_digital": digital, "snd_ascend": ascend, "snd_chimes": chimes,
    "snd_siren": siren,
}


def load_sndfile():
    path = ctypes.util.find_library("sndfile") or "libsndfile.so.1"
    try:
        lib = ctypes.CDLL(path)
    except OSError:
        return None
    lib.sf_open.restype = ctypes.c_void_p
    lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.c_void_p]
    lib.sf_close.argtypes = [ctypes.c_void_p]
    lib.sf_writef_float.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_int64]
    lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
    return lib


class SfInfo(ctypes.Structure):
    _fields_ = [("frames", ctypes.c_int64), ("samplerate", ctypes.c_int), ("channels", ctypes.c_int),
                ("format", ctypes.c_int), ("sections", ctypes.c_int), ("seekable", ctypes.c_int)]


def write_vorbis(lib, path, x):
    info = SfInfo(0, SR, 1, SF_FORMAT_OGG | SF_FORMAT_VORBIS, 0, 0)
    h = lib.sf_open(str(path).encode(), SFM_WRITE, ctypes.byref(info))
    if not h:
        return False
    q = ctypes.c_double(0.3)
    lib.sf_command(h, SFC_SET_VBR_ENCODING_QUALITY, ctypes.byref(q), 8)
    data = np.ascontiguousarray(x, dtype=np.float32)
    lib.sf_writef_float(h, data.ctypes.data_as(ctypes.c_void_p), len(data))
    lib.sf_close(h)
    return True


def write_wav(path, x):
    n = int(len(x) * 16000 / SR)
    y = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(b"".join(struct.pack("<h", int(v * 32000)) for v in y))


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "core/alarm/src/main/res/raw")
    out.mkdir(parents=True, exist_ok=True)
    lib = load_sndfile()
    for name, fn in SOUNDS.items():
        x = fn()
        if lib is not None and write_vorbis(lib, out / f"{name}.ogg", x):
            print(f"{name}.ogg")
        else:
            print(f"WARNING: Vorbis недоступен, {name} записан как WAV", file=sys.stderr)
            write_wav(out / f"{name}.wav", x)


if __name__ == "__main__":
    main()
