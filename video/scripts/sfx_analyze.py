"""Ukur sound effect supaya bisa dipasang tepat di video.

  python3 scripts/sfx_analyze.py public/sfx/kertas-remas.wav public/mixkit/sfx/*.mp3 > src/promo/sfx-meta.json

Untuk tiap file dicatat:
  onset     detik hening di awal file (dipotong supaya bunyi tepat di frame yang dituju)
  peak      detik saat bunyi paling keras (titik "hantaman", untuk diselaraskan ke gerakan)
  duration  panjang bunyi setelah onset
  loudness  kekerasan rata-rata bagian yang berbunyi, dalam dBFS, diukur di 48 kHz supaya bunyi
            berfrekuensi tinggi (kertas, desis) tidak terukur terlalu pelan. Untuk menyamakan volume;
            dipakai RMS, bukan LUFS, karena LUFS tidak bisa mengukur bunyi di bawah 0,4 detik)
  peakDb    kekerasan 10 ms terkeras, dalam dBFS (supaya hentakan tajam, seperti ketukan tuts,
            tidak menutupi suara orang walaupun rata-ratanya pelan)
"""
import json
import subprocess
import sys

import numpy as np


def decode(path: str, sr: int = 48000) -> np.ndarray:
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"],
        capture_output=True, check=True,
    ).stdout
    return np.frombuffer(raw, dtype=np.float32)


def analyze(path: str, sr: int = 48000) -> dict:
    x = decode(path, sr)
    hop = sr // 100  # 10 ms
    env = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, max(len(x) - hop, 1), hop)])
    db = 20 * np.log10(env + 1e-9)
    loud = np.where(db > db.max() - 30)[0]  # mulai dihitung bunyi bila dalam 30 dB dari puncak
    onset = loud[0] * hop / sr if len(loud) else 0.0
    end = loud[-1] * hop / sr if len(loud) else len(x) / sr
    peak = int(np.argmax(env)) * hop / sr
    return {
        "onset": round(onset, 3),
        "peak": round(peak - onset, 3),
        "duration": round(end - onset, 3),
        "peakDb": round(float(db.max()), 1),
        "loudness": round(float(20 * np.log10(np.sqrt(np.mean(x[int(onset * sr):int(end * sr) + hop] ** 2)) + 1e-9)), 1),
    }


if __name__ == "__main__":
    out = {}
    for p in sys.argv[1:]:
        key = p.split("public/", 1)[-1]
        out[key] = analyze(p)
    print(json.dumps(out, indent=1))
