"""Periksa campuran sound effect dan voiceover dari dua render terpisah.

  npx remotion render PromoSuara out/stem-voice.wav --codec=wav --props='{"voice":true,"sfx":false}'
  npx remotion render PromoSuara out/stem-sfx.wav   --codec=wav --props='{"voice":false,"sfx":true}'
  python3 scripts/sfx_check.py out/stem-voice.wav out/stem-sfx.wav out/sfx-check.png

Untuk tiap bunyi di stem SFX dicetak: waktu mulai, waktu puncak, dan selisih kekerasannya dengan
suara orang di saat yang sama. Bunyi yang bersamaan dengan suara dan kurang dari 8 dB di bawahnya
ditandai TERLALU KERAS, karena bisa menutupi kata-kata.
"""
import subprocess
import sys

import numpy as np

SR = 48000
HOP = SR // 100  # 10 ms


def envelope_db(path: str) -> np.ndarray:
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32)
    n = len(x) // HOP
    rms = np.sqrt(np.mean(x[: n * HOP].reshape(n, HOP) ** 2, axis=1))
    return 20 * np.log10(rms + 1e-9)


def main() -> None:
    voice, sfx = envelope_db(sys.argv[1]), envelope_db(sys.argv[2])
    n = min(len(voice), len(sfx))
    voice, sfx = voice[:n], sfx[:n]
    active = sfx > -45
    events, i = [], 0
    while i < n:
        if active[i]:
            j = i
            while j < n and (active[j:j + 8].any()):  # gabungkan jeda < 80 ms
                j += 1
            events.append((i, j))
            i = j
        else:
            i += 1
    print(f"{'mulai':>7} {'puncak':>7} {'durasi':>6} {'SFX dB':>7} {'suara dB':>8}  status")
    bad = 0
    for a, b in events:
        seg_s = sfx[a:b]
        # Bandingkan dengan suara orang di sekitar bunyi (+-0,5 detik), bukan hanya di 10 ms yang sama:
        # telinga menilai kekerasan bunyi terhadap kalimat yang sedang diucapkan, bukan jeda antar-kata.
        near_v = voice[max(0, a - 50):min(n, b + 50)]
        peak = a + int(np.argmax(seg_s))
        s_db = float(np.max(seg_s))
        # Bersamaan dengan suara orang? Dicek pada durasi bunyinya sendiri (dengan toleransi 50 ms).
        overlap = voice[max(0, a - 5):min(n, b + 5)] > -35
        if overlap.mean() > 0.2:
            v_db = float(np.percentile(near_v[near_v > -35], 75))
            gap = v_db - s_db
            status = "TERLALU KERAS" if gap < 8 else f"aman ({gap:.0f} dB di bawah suara)"
            bad += gap < 8
            v_txt = f"{v_db:8.1f}"
        else:
            status, v_txt = "aman (tidak ada suara orang)", "       -"
        print(f"{a / 100:7.2f} {peak / 100:7.2f} {(b - a) / 100:6.2f} {s_db:7.1f} {v_txt}  {status}")
    print(f"\n{len(events)} bunyi, {bad} terlalu keras")

    if len(sys.argv) > 3:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        t = np.arange(n) / 100
        fig, ax = plt.subplots(figsize=(16, 3.2), dpi=110)
        ax.fill_between(t, -60, np.maximum(voice, -60), color="#2563EB", alpha=0.55, label="Voiceover")
        ax.fill_between(t, -60, np.maximum(sfx, -60), color="#F07A16", alpha=0.8, label="Sound effect")
        for a, b in events:
            ax.axvline(a / 100, color="#F07A16", lw=0.6, ls=":")
        ax.set_ylim(-60, 0); ax.set_xlim(0, n / 100)
        ax.set_xlabel("detik"); ax.set_ylabel("dB")
        ax.legend(loc="upper right"); ax.grid(alpha=0.25)
        fig.tight_layout(); fig.savefig(sys.argv[3])


if __name__ == "__main__":
    main()
