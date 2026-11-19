import { createContext, useContext } from "react";
import { Easing, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";

// Inti gerak Adriy.project. Semua waktu dihitung dari frame Remotion (deterministik, tanpa Math.random
// atau Date), dalam detik relatif terhadap awal adegan.

// ---------------------------------------------------------------- tata letak layar (1080 x 1920)
// Konten adegan selalu di dalam CONTENT; pita subtitle di bawahnya tidak pernah dipakai konten,
// jadi subtitle tidak menutupi karakter atau informasi.
export const W = 1080;
export const H = 1920;
export const SAFE_X = 80;
export const CONTENT = { top: 140, bottom: 1440 };
export const FLOOR = 1440; // kaki ADRI berpijak di sini
export const CAPTION_BAND = { top: 1480, bottom: 1760 };

// ---------------------------------------------------------------- durasi adegan
// useVideoConfig() memberi durasi seluruh video; durasi adegan dikirim lewat context.
export const SceneLength = createContext<number | null>(null);

export const useScene = () => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const len = useContext(SceneLength) ?? durationInFrames;
  return { frame, fps, len, t: frame / fps, sec: len / fps, p: Math.min(1, Math.max(0, frame / Math.max(1, len))) };
};

// ---------------------------------------------------------------- easing
export const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
export const EASE = {
  out: Easing.bezier(0.16, 1, 0.3, 1), // masuk: cepat lalu pelan
  in: Easing.bezier(0.7, 0, 0.84, 0), // keluar: pelan lalu cepat
  inOut: Easing.bezier(0.65, 0, 0.35, 1), // gerak kamera
  soft: Easing.bezier(0.33, 1, 0.68, 1),
  back: Easing.bezier(0.34, 1.56, 0.64, 1), // sedikit melewati lalu kembali
};

// Progres 0..1 untuk elemen yang mulai pada detik `at` selama `len` detik.
export const useAppear = (at: number, len = 0.5, easing = EASE.out) => {
  const { frame, fps } = useScene();
  return interpolate(frame, [at * fps, (at + len) * fps], [0, 1], { ...clamp, easing });
};

// Spring yang mulai pada detik `at`. damping tinggi = halus tanpa memantul.
export const useSpring = (at: number, config: { damping?: number; stiffness?: number; mass?: number } = {}) => {
  const { frame, fps } = useScene();
  return spring({ frame: frame - Math.round(at * fps), fps, config: { damping: 18, stiffness: 120, mass: 0.9, ...config } });
};

// Progres keluar 0..1 di akhir adegan.
export const useExit = (len = 0.35) => {
  const { frame, fps, len: L } = useScene();
  return interpolate(frame, [L - len * fps, L], [0, 1], { ...clamp, easing: EASE.in });
};

export const stagger = (i: number, base: number, gap = 0.09) => base + i * gap;

// ---------------------------------------------------------------- mask reveal
export const revealInset = (p: number, from: "left" | "right" | "top" | "bottom" = "left") => {
  const r = (1 - p) * 100;
  return { left: `inset(0 ${r}% 0 0)`, right: `inset(0 0 0 ${r}%)`, top: `inset(0 0 ${r}% 0)`, bottom: `inset(${r}% 0 0 0)` }[from];
};
export const revealCircle = (p: number, x: number, y: number) => `circle(${p * 150}% at ${x}px ${y}px)`;

// ---------------------------------------------------------------- motion blur selektif
// Blur searah gerak hanya saat elemen bergerak cepat: CSS blur sebanding dengan kecepatan.
// Dipakai pada transisi kamera (whip pan) dan hentakan, bukan pada gerak biasa.
export const speedBlur = (pxPerFrame: number, threshold = 12, maxBlur = 10) =>
  Math.abs(pxPerFrame) <= threshold ? 0 : Math.min(maxBlur, (Math.abs(pxPerFrame) - threshold) * 0.35);

// Angka "acak" yang selalu sama untuk masukan yang sama.
export const rand = (n: number) => {
  const x = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return x - Math.floor(x);
};

// Versi murni (bukan hook) dari useAppear: aman dipakai di dalam map/loop.
export const ap = (frame: number, fps: number, at: number, len = 0.5, easing = EASE.out) =>
  interpolate(frame, [at * fps, (at + len) * fps], [0, 1], { ...clamp, easing });
// Spring murni, mulai pada detik `at`.
export const sp = (frame: number, fps: number, at: number, config: { damping?: number; stiffness?: number; mass?: number } = {}) =>
  spring({ frame: frame - Math.round(at * fps), fps, config: { damping: 18, stiffness: 120, mass: 0.9, ...config } });

// Transisi keluar ditentukan oleh adegan berikutnya (kontinuitas): AdriyVideo mengisi context ini.
export const NextTransition = createContext<string | null>(null);
// Lama tumpang tindih dua adegan (detik): adegan baru masuk di atas adegan lama.
export const OVERLAP = 0.7;
// Lama transisi masuk/keluar yang sebenarnya (detik), dihitung AdriyVideo dari tumpang tindih adegan.
export const TransitionTiming = createContext<{ enter: number; exit: number } | null>(null);
// Nomor adegan (untuk pemeriksa QA: elemen adegan lama yang tertinggal).
export const SceneIndex = createContext<number>(0);
