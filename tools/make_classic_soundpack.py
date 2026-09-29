#!/usr/bin/env python3
"""Build the standalone Classic sound pack from the bundled original voices.

Requires numpy and PyAV. Only writes to soundpacks/c1a55c0d.
"""

from __future__ import annotations

import json
import shutil
from fractions import Fraction
from pathlib import Path

import av
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "soundpacks" / "官方默认包"
DEST = ROOT / "soundpacks" / "c1a55c0d"
RATE = 44_100
TAU = 2 * np.pi


def silence(seconds: float) -> np.ndarray:
    return np.zeros(round(seconds * RATE), dtype=np.float32)


def tone(freq: float, seconds: float, *, volume: float = 0.23,
         decay: float = 3.0, bright: bool = False) -> np.ndarray:
    t = np.arange(round(seconds * RATE), dtype=np.float64) / RATE
    envelope = np.minimum(t / 0.009, 1.0) * np.exp(-decay * t / seconds)
    wave = np.sin(TAU * freq * t)
    wave += (0.26 if bright else 0.12) * np.sin(TAU * 2 * freq * t)
    wave += 0.06 * np.sin(TAU * 3 * freq * t)
    return (volume * envelope * wave).astype(np.float32)


def put(track: np.ndarray, start: float, sound: np.ndarray) -> None:
    offset = round(start * RATE)
    end = min(offset + len(sound), len(track))
    track[offset:end] += sound[:end - offset]


def notes(length: float, events: list[tuple[float, float, float, float]],
          *, bright: bool = False) -> np.ndarray:
    track = silence(length)
    for start, frequency, duration, volume in events:
        put(track, start, tone(frequency, duration, volume=volume, bright=bright))
    return track


def short_effects() -> dict[str, np.ndarray]:
    rng = np.random.default_rng(0xC1A55C0D)
    deal = silence(0.75)
    for start in (0.04, 0.18, 0.32, 0.46):
        count = round(0.075 * RATE)
        noise = rng.normal(0, 1, count).astype(np.float32)
        # Two small, dry card shuffles rather than an unrelated spoken line.
        noise = np.concatenate(([0], np.diff(noise))).astype(np.float32)
        noise *= (0.025 * np.exp(-np.linspace(0, 5, count))).astype(np.float32)
        put(deal, start, noise)
    bid = notes(0.72, [(0.0, 587.33, 0.22, 0.20),
                       (0.16, 880.0, 0.38, 0.18)], bright=True)
    play = silence(0.42)
    put(play, 0.0, tone(392.0, 0.17, volume=0.22, decay=6, bright=True))
    put(play, 0.09, tone(523.25, 0.18, volume=0.16, decay=6, bright=True))
    win = notes(2.4, [(0.0, 523.25, 0.45, 0.17),
                      (0.25, 659.25, 0.45, 0.17),
                      (0.50, 783.99, 0.45, 0.17),
                      (0.78, 1046.5, 1.35, 0.19),
                      (0.78, 523.25, 1.35, 0.07)], bright=True)
    lose = notes(1.9, [(0.0, 523.25, 0.43, 0.17),
                       (0.34, 466.16, 0.43, 0.17),
                       (0.68, 392.0, 0.43, 0.17),
                       (1.02, 293.66, 0.72, 0.18)])
    return {"deal": deal, "bid": bid, "play": play, "win": win, "lose": lose}


def loop_bgm(*, rocket: bool) -> np.ndarray:
    # Eight bars, 120 BPM. The last beat decays into silence for a clean loop.
    beat = 0.5
    bars = 8
    duration = bars * 4 * beat
    track = silence(duration)
    roots = ([146.83, 174.61, 196.00, 130.81] if rocket else
             [110.00, 103.83, 98.00, 92.50])
    melody = ([0, 4, 7, 12, 7, 4, 9, 7] if rocket else
              [0, 3, 7, 3, 0, 3, 10, 7])
    for bar in range(bars):
        base = roots[bar % len(roots)]
        start = bar * 4 * beat
        for beat_idx in range(4):
            put(track, start + beat_idx * beat,
                tone(base, beat * 0.74, volume=0.105, decay=3.9))
            if rocket and beat_idx in (0, 2):
                put(track, start + beat_idx * beat,
                    tone(base * 2, beat * 0.45, volume=0.07, decay=4, bright=True))
        for step in range(8):
            semitones = melody[(step + bar) % len(melody)]
            freq = base * 2 * 2 ** (semitones / 12)
            put(track, start + step * beat / 2,
                tone(freq, beat * (0.33 if rocket else 0.43),
                     volume=0.09 if rocket else 0.075, decay=4.2,
                     bright=rocket))
    # Keep peak controlled and finish at zero so repeats do not click.
    fade = round(0.18 * RATE)
    track[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)
    return track


def write_vorbis(path: Path, samples: np.ndarray) -> None:
    samples = np.clip(samples, -0.95, 0.95)
    with av.open(str(path), mode="w", format="ogg") as container:
        stream = container.add_stream("vorbis", rate=RATE)
        stream.codec_context.bit_rate = 96_000
        stream.codec_context.options = {"strict": "-2"}
        # FFmpeg's bundled native Vorbis encoder requires two channels.
        stream.layout = "stereo"
        for offset in range(0, len(samples), 4096):
            mono = samples[offset:offset + 4096]
            block = np.ascontiguousarray(np.stack((mono, mono)))
            frame = av.AudioFrame.from_ndarray(block, format="fltp", layout="stereo")
            frame.sample_rate = RATE
            frame.pts = offset
            frame.time_base = Fraction(1, RATE)
            for packet in stream.encode(frame):
                container.mux(packet)
        for packet in stream.encode(None):
            container.mux(packet)


def main() -> None:
    source = json.loads((SOURCE / "pack.json").read_text(encoding="utf-8"))
    sounds = source["sounds"].copy()
    generated = short_effects()
    generated["bgm_clutch"] = loop_bgm(rocket=False)
    generated["bgm_rocket"] = loop_bgm(rocket=True)
    expected = (
        ["deal", "bid", "play", "bomb", "pass", "win", "lose",
         "bgm_waiting", "bgm_playing", "bgm_clutch", "bgm_rocket", "bgm_win", "bgm_lose"]
        + [f"dan{i}" for i in range(1, 16)]
        + [f"dui{i}" for i in range(1, 14)]
        + [f"tuple{i}" for i in range(1, 14)]
        + ["sandaiyi", "shunzi", "liandui", "feiji", "sidaier",
           "sidailiangdui", "wangzha"]
    )
    if set(sounds) | set(generated) != set(expected):
        raise ValueError("Source and generated audio do not cover the 61 game keys")
    DEST.mkdir(parents=True, exist_ok=True)
    for entries in sounds.values():
        for filename in entries:
            shutil.copyfile(SOURCE / filename, DEST / filename)
    for key, samples in generated.items():
        filename = f"{key}.ogg"
        write_vorbis(DEST / filename, samples)
        sounds[key] = [filename]
    manifest = {
        "version": 2,
        "name": "经典包",
        "author": "Crafty Cards",
        "description": "经典斗地主牌型语音与原创新增音效、残局及王炸配乐；覆盖全部 61 个音频键。",
        "sounds": {key: sounds[key] for key in expected},
    }
    (DEST / "pack.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Created {DEST} with {len(sounds)} audio keys")


if __name__ == "__main__":
    main()
