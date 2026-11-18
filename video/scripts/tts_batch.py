"""Buat banyak voiceover sekaligus dari file JSON (model dimuat sekali).

Contoh:
  python scripts/tts_batch.py scripts/promo-dialog.json public/vo

Format JSON: [{"id": "01-hook", "voice": "adriy", "text": "..."}, ...]
"voice" adalah nama profil di voices/ (lihat scripts/tts.py). Hasil: public/vo/<id>.wav
"""
import json
import sys
import time
from pathlib import Path

import soundfile as sf
from voxcpm import VoxCPM

ROOT = Path(__file__).resolve().parent.parent


def voice_args(name: str) -> dict:
    d = ROOT / "voices" / name
    if not d.is_dir():
        sys.exit(f"Profil suara tidak ditemukan: {d}")
    args = {}
    if (d / "prompt.wav").exists() and (d / "prompt.txt").exists():
        args["prompt_wav_path"] = str(d / "prompt.wav")
        args["prompt_text"] = (d / "prompt.txt").read_text(encoding="utf-8").strip()
    if (d / "reference.wav").exists():
        args["reference_wav_path"] = str(d / "reference.wav")
    return args


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    lines = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)

    model = VoxCPM.from_pretrained("openbmb/VoxCPM2", load_denoiser=False, optimize=False)
    sr = model.tts_model.sample_rate
    for line in lines:
        start = time.time()
        wav = model.generate(text=line["text"], **voice_args(line["voice"]))
        out = out_dir / f'{line["id"]}.wav'
        sf.write(out, wav, sr)
        print(f'{out} [{line["voice"]}] {len(wav) / sr:.2f}s ({time.time() - start:.0f}s)', flush=True)


if __name__ == "__main__":
    main()
