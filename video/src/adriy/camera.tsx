import { createContext, useContext } from "react";
import { AbsoluteFill, interpolate } from "remotion";
import { clamp, EASE, FLOOR, H, useScene, W } from "./motion";

// Kamera virtual Adriy.project. Tiap adegan memilih satu "shot" yang punya tujuan visual, lalu
// lapisan belakang/tengah/depan membaca kamera yang sama dengan kedalaman berbeda (parallax).

export type Shot =
  | { kind: "establish" } // menjauh perlahan: memperlihatkan keseluruhan ilustrasi
  | { kind: "focus"; x: number; y: number; at?: number; zoom?: number } // mendekat ke titik saat informasi penting
  | { kind: "track"; from: [number, number]; to: [number, number]; zoom?: number } // mengikuti objek yang bergerak
  | { kind: "pan"; distance: number; zoom?: number } // menyamping memperlihatkan proses (distance dalam px)
  | { kind: "emphasis"; x: number; y: number } // mendekat ke karakter dengan sedikit miring
  | { kind: "perspective"; from: "left" | "right" } // berputar sedikit, pindah sudut pandang
  | { kind: "drift" } // gerak sangat halus untuk adegan teks
  | { kind: "panV"; distance: number; zoom?: number } // menurun/menaik: memperlihatkan isi dari atas ke bawah
  | { kind: "reveal"; x: number; y: number; from?: number } // mulai dekat pada detail, lalu mundur memperlihatkan semuanya
  | { kind: "rack"; from: "left" | "right" } // bergeser antarlapisan: lapisan depan bergerak lebih jauh dari belakang
  | { kind: "path"; keys: { t: number; x: number; y: number; zoom?: number }[] }; // kamera berpindah antartitik (proses, linimasa, peta)

// Titik pandang kamera "path" pada detik t: diam di tiap titik, lalu berpindah dengan easing.
export const pathAt = (keys: { t: number; x: number; y: number; zoom?: number }[], t: number) => {
  const ks = [...keys].sort((a, b) => a.t - b.t);
  const MOVE = 0.8; // detik perpindahan menuju titik berikutnya
  let cur = ks[0];
  for (let i = 1; i < ks.length; i++) {
    const k = ks[i];
    if (t >= k.t) { cur = k; continue; }
    const s0 = k.t - MOVE;
    if (t > s0) {
      const f = EASE.inOut(Math.min(1, (t - s0) / MOVE));
      return { x: cur.x + (k.x - cur.x) * f, y: cur.y + (k.y - cur.y) * f, zoom: (cur.zoom ?? 1) + ((k.zoom ?? 1) - (cur.zoom ?? 1)) * f, moving: true };
    }
    break;
  }
  return { x: cur.x, y: cur.y, zoom: cur.zoom ?? 1, moving: false };
};

type Cam = { x: number; y: number; zoom: number; rot: number; tiltY: number; ox: number; oy: number };
const CameraCtx = createContext<Cam>({ x: 0, y: 0, zoom: 1, rot: 0, tiltY: 0, ox: W / 2, oy: H / 2 });

export const useCamera = (shot: Shot): Cam => {
  const { frame, len, fps } = useScene();
  const k = interpolate(frame, [0, len], [0, 1], { ...clamp, easing: EASE.inOut });
  switch (shot.kind) {
    case "establish":
      return { x: 0, y: 0, zoom: interpolate(k, [0, 1], [1.12, 1.0]), rot: 0, tiltY: 0, ox: W / 2, oy: H * 0.45 };
    case "focus": {
      // tenang dulu, lalu mendekat ke titik pada detik `at` dan bertahan di sana
      const at = (shot.at ?? 1.2) * fps;
      const f = interpolate(frame, [at, at + 0.9 * fps], [0, 1], { ...clamp, easing: EASE.inOut });
      return { x: 0, y: 0, zoom: 1 + 0.02 * k + ((shot.zoom ?? 1.22) - 1) * f, rot: 0, tiltY: 0, ox: shot.x, oy: shot.y };
    }
    case "track": {
      // titik pandang ikut bergerak dari `from` ke `to`
      const tx = interpolate(k, [0, 1], [shot.from[0], shot.to[0]]);
      const ty = interpolate(k, [0, 1], [shot.from[1], shot.to[1]]);
      // geser sebagian (60% horizontal, 30% vertikal) supaya titik itu mendekati tengah layar
      const z = shot.zoom ?? 1.1;
      // geser ke bawah tidak boleh: lantai (dan kaki ADRI) akan turun ke pita subtitle
      return { x: (W / 2 - tx) * 0.6, y: Math.min(0, (H / 2 - ty) * 0.3), zoom: z, rot: 0, tiltY: 0, ox: tx, oy: ty };
    }
    case "pan":
      return { x: -shot.distance * k, y: 0, zoom: shot.zoom ?? 1.0, rot: 0, tiltY: 0, ox: W / 2, oy: H / 2 };
    case "emphasis": {
      const f = interpolate(frame, [0.2 * fps, 1.4 * fps], [0, 1], { ...clamp, easing: EASE.out });
      return { x: 0, y: 0, zoom: 1 + 0.1 * f + 0.02 * k, rot: -1.2 * f, tiltY: 0, ox: shot.x, oy: shot.y };
    }
    case "perspective": {
      const s = shot.from === "left" ? 1 : -1;
      return { x: s * 40 * (1 - k), y: 0, zoom: 1.04, rot: 0, tiltY: s * 9 * (1 - k), ox: W / 2, oy: H / 2 };
    }
    case "panV":
      // kamera turun menyusuri isi: semua bergeser naik (tidak pernah turun ke pita subtitle)
      return { x: 0, y: -shot.distance * k, zoom: shot.zoom ?? 1.04, rot: 0, tiltY: 0, ox: W / 2, oy: H / 2 };
    case "reveal": {
      const f = interpolate(frame, [0.15 * fps, 1.6 * fps], [0, 1], { ...clamp, easing: EASE.inOut });
      return { x: (W / 2 - shot.x) * 0.5 * (1 - f), y: 0, zoom: interpolate(f, [0, 1], [shot.from ?? 1.3, 1.0]), rot: 0, tiltY: 0, ox: shot.x, oy: shot.y };
    }
    case "rack": {
      const s = shot.from === "left" ? 1 : -1;
      return { x: s * 90 * (1 - k), y: 0, zoom: 1.03, rot: 0, tiltY: 0, ox: W / 2, oy: H / 2 };
    }
    case "path": {
      const c = pathAt(shot.keys, frame / fps);
      return { x: W / 2 - c.x, y: 0, zoom: c.zoom, rot: 0, tiltY: 0, ox: c.x, oy: c.y };
    }
    default:
      return { x: 0, y: -20 * k, zoom: 1.0 + 0.03 * k, rot: 0, tiltY: 0, ox: W / 2, oy: H / 2 };
  }
};

// Lapisan dengan kedalaman: 0 = diam (langit jauh), 1 = ikut penuh kamera, >1 = lebih dekat dari bidang fokus.
export const Layer: React.FC<{ depth: number; children: React.ReactNode; style?: React.CSSProperties }> = ({ depth, children, style }) => {
  const c = useContext(CameraCtx);
  const z = 1 + (c.zoom - 1) * depth;
  return (
    <AbsoluteFill
      style={{
        transformOrigin: `${c.ox}px ${FLOOR}px`, // poros zoom di lantai: kaki tetap berpijak, subtitle tidak tertutup
        transform: `perspective(1600px) rotateY(${c.tiltY * depth}deg) translate(${c.x * depth}px, ${c.y * depth}px) scale(${z}) rotate(${c.rot * depth}deg)`,
        ...style,
      }}
    >
      {children}
    </AbsoluteFill>
  );
};

export const Camera: React.FC<{ shot: Shot; children: React.ReactNode }> = ({ shot, children }) => {
  const cam = useCamera(shot);
  return <CameraCtx.Provider value={cam}>{children}</CameraCtx.Provider>;
};
