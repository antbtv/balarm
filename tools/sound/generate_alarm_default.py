#!/usr/bin/env python3
"""Генерирует core/alarm/src/main/res/raw/alarm_default.wav — встроенный звук будильника (ADR-008 §2).

Собственный синтез, без сторонних сэмплов → CC0 (docs/LICENSES.md). Два тройных «бипа» на 880/1320 Гц
с мягкими фронтами, затем пауза; цикл бесшовный (начинается и кончается тишиной). Только stdlib.
"""
import math
import struct
import wave
from pathlib import Path

RATE = 16_000
AMPLITUDE = 0.8
OUT = Path(__file__).resolve().parents[2] / "core/alarm/src/main/res/raw/alarm_default.wav"


def beep(freq: float, seconds: float) -> list[float]:
    n = int(RATE * seconds)
    fade = int(RATE * 0.01)
    out = []
    for i in range(n):
        env = min(1.0, i / fade, (n - 1 - i) / fade)
        # основной тон + октава выше: звонче на маленьких динамиках
        s = 0.75 * math.sin(2 * math.pi * freq * i / RATE) + 0.25 * math.sin(4 * math.pi * freq * i / RATE)
        out.append(AMPLITUDE * env * s)
    return out


def silence(seconds: float) -> list[float]:
    return [0.0] * int(RATE * seconds)


def main() -> None:
    samples: list[float] = []
    for freq in (880.0, 1320.0):
        for _ in range(3):
            samples += beep(freq, 0.09) + silence(0.06)
        samples += silence(0.15)
    samples += silence(0.35)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print(f"{OUT} — {len(samples) / RATE:.2f} s, {OUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
