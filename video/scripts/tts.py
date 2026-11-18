"""Buat suara (voiceover) dari teks dengan VoxCPM2 (OpenBMB, Apache 2.0).

Contoh:
  python scripts/tts.py "Halo, ini NotesByAdriy." public/voiceover.wav
  python scripts/tts.py "Teks..." public/vo.wav --reference suaraku.wav   # tiru suara dari rekaman

Hasil WAV bisa dipakai di Remotion: <Audio src={staticFile("voiceover.wav")} />
Model (~5 GB) diunduh otomatis dari Hugging Face saat pertama dijalankan.
"""
import argparse
import time

import soundfile as sf
from voxcpm import VoxCPM


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("text", help="Teks yang akan diucapkan")
    p.add_argument("output", help="File WAV keluaran, misalnya public/voiceover.wav")
    p.add_argument("--reference", help="Rekaman suara untuk ditiru (voice cloning)")
    p.add_argument("--cfg", type=float, default=2.0, help="Kekuatan guidance (default 2.0)")
    p.add_argument("--steps", type=int, default=10, help="Langkah inferensi; lebih kecil = lebih cepat (default 10)")
    p.add_argument("--device", default=None, help="cpu, cuda, atau mps (default: otomatis)")
    args = p.parse_args()

    start = time.time()
    model = VoxCPM.from_pretrained("openbmb/VoxCPM2", load_denoiser=False, optimize=False, device=args.device)
    print(f"Model dimuat dalam {time.time() - start:.0f} detik")

    start = time.time()
    wav = model.generate(
        text=args.text,
        reference_wav_path=args.reference,
        cfg_value=args.cfg,
        inference_timesteps=args.steps,
    )
    sr = model.tts_model.sample_rate
    sf.write(args.output, wav, sr)
    print(f"Tersimpan: {args.output} ({len(wav) / sr:.1f} detik audio, dibuat dalam {time.time() - start:.0f} detik)")


if __name__ == "__main__":
    main()
