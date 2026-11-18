"""Buat suara (voiceover) dari teks dengan VoxCPM2 (OpenBMB, Apache 2.0).

Contoh:
  python scripts/tts.py "Halo, ini NotesByAdriy." public/voiceover.wav
  python scripts/tts.py "Teks..." public/vo.wav --reference suaraku.wav   # tiru suara dari rekaman
  python scripts/tts.py "Teks..." public/vo.wav --voice adriy             # pakai profil voices/adriy/

Profil suara (voices/<nama>/, tidak di-commit) berisi:
  prompt.wav + prompt.txt  klip 5-15 detik dan teks persisnya (paling menentukan kemiripan)
  reference.wav            klip lain 10-30 detik untuk warna suara (opsional)

Hasil WAV bisa dipakai di Remotion: <Audio src={staticFile("voiceover.wav")} />
Model (~5 GB) diunduh otomatis dari Hugging Face saat pertama dijalankan.
"""
import argparse
import time
from pathlib import Path

import soundfile as sf
from voxcpm import VoxCPM


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("text", help="Teks yang akan diucapkan")
    p.add_argument("output", help="File WAV keluaran, misalnya public/voiceover.wav")
    p.add_argument("--reference", help="Rekaman suara untuk ditiru (voice cloning)")
    p.add_argument("--voice", help="Nama profil suara di folder voices/, misalnya adriy")
    p.add_argument("--cfg", type=float, default=2.0, help="Kekuatan guidance (default 2.0)")
    p.add_argument("--steps", type=int, default=10, help="Langkah inferensi; lebih kecil = lebih cepat (default 10)")
    p.add_argument("--device", default=None, help="cpu, cuda, atau mps (default: otomatis)")
    args = p.parse_args()

    prompt_wav = prompt_text = None
    reference = args.reference
    if args.voice:
        voice_dir = Path(__file__).resolve().parent.parent / "voices" / args.voice
        if not voice_dir.is_dir():
            p.error(f"Profil suara tidak ditemukan: {voice_dir}")
        if (voice_dir / "prompt.wav").exists() and (voice_dir / "prompt.txt").exists():
            prompt_wav = str(voice_dir / "prompt.wav")
            prompt_text = (voice_dir / "prompt.txt").read_text(encoding="utf-8").strip()
        if reference is None and (voice_dir / "reference.wav").exists():
            reference = str(voice_dir / "reference.wav")

    start = time.time()
    model = VoxCPM.from_pretrained("openbmb/VoxCPM2", load_denoiser=False, optimize=False, device=args.device)
    print(f"Model dimuat dalam {time.time() - start:.0f} detik")

    start = time.time()
    wav = model.generate(
        text=args.text,
        prompt_wav_path=prompt_wav,
        prompt_text=prompt_text,
        reference_wav_path=reference,
        cfg_value=args.cfg,
        inference_timesteps=args.steps,
    )
    sr = model.tts_model.sample_rate
    sf.write(args.output, wav, sr)
    print(f"Tersimpan: {args.output} ({len(wav) / sr:.1f} detik audio, dibuat dalam {time.time() - start:.0f} detik)")


if __name__ == "__main__":
    main()
