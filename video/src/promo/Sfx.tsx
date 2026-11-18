import { Audio } from "@remotion/media";
import { Easing, interpolate, staticFile, useVideoConfig } from "remotion";
import meta from "./sfx-meta.json";

// Ukuran tiap file dibuat dengan: python3 scripts/sfx_analyze.py <file...> > src/promo/sfx-meta.json
type SfxFile = keyof typeof meta;

// Target kekerasan per peran, dalam dBFS. Voiceover ada di sekitar -16 dBFS.
//  accent      bunyi yang berdiri sendiri, tidak bersamaan dengan suara orang (8 dB di bawah suara)
//  underVoice  bunyi yang terjadi saat orang bicara, jauh lebih pelan supaya kata tetap jelas
const TARGET_DB = { accent: -24, underVoice: -32 } as const;
// Batas hentakan terkeras per peran. underVoice: minimal 10 dB di bawah suara orang.
const PEAK_LIMIT_DB = { accent: -12, underVoice: -26 } as const;

/**
 * Sound effect yang diselaraskan ke gerakan di layar.
 * `at`     frame saat gerakan "mendarat" (frame lokal di dalam Sequence induknya)
 * `align`  "onset": bunyi mulai tepat di `at`; "peak": bagian paling keras jatuh tepat di `at`
 * `max`    potong setelah sekian detik, dengan fade-out singkat
 */
export const Sfx: React.FC<{
  name: string;
  file: SfxFile;
  at: number;
  role: keyof typeof TARGET_DB;
  align?: "onset" | "peak";
  max?: number;
  trimStart?: number;
}> = ({ name, file, at, role, align = "onset", max, trimStart = 0 }) => {
  const { fps } = useVideoConfig();
  const m = meta[file];
  const volume = Math.min(1, 10 ** ((TARGET_DB[role] - m.loudness) / 20), 10 ** ((PEAK_LIMIT_DB[role] - m.peakDb) / 20));
  const lead = align === "peak" ? Math.round(m.peak * fps) : 0;
  let from = at - lead;
  let trimBefore = Math.round((m.onset + trimStart) * fps);
  if (from < 0) {
    trimBefore -= from;
    from = 0;
  }
  const length = Math.max(1, Math.round(Math.min(max ?? m.duration, m.duration) * fps));
  const fade = Math.min(5, length - 1);

  return (
    <Audio
      name={name}
      src={staticFile(file)}
      from={from}
      trimBefore={trimBefore}
      durationInFrames={length}
      volume={(f) =>
        volume * interpolate(f, [length - fade - 1, length - 1], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })
      }
    />
  );
};

/** Frame saat animasi mencapai 90% geraknya, dihitung dari timing dan easing yang sama dengan scene. */
export const landFrame = (fps: number, startSec: number, endSec: number, easing: (t: number) => number) => {
  const start = Math.round(startSec * fps);
  const end = Math.round(endSec * fps);
  for (let f = start; f <= end; f++) {
    if (interpolate(f, [start, end], [0, 1], { easing, extrapolateLeft: "clamp", extrapolateRight: "clamp" }) >= 0.9) {
      return f;
    }
  }
  return end;
};

export const SPRING_12 = Easing.spring({ damping: 12 });
export const SPRING_14 = Easing.spring({ damping: 14 });
export const BEZIER_OUT = Easing.bezier(0.16, 1, 0.3, 1);
export const BEZIER_POP = Easing.bezier(0.34, 1.56, 0.64, 1);
