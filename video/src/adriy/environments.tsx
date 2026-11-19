import { AbsoluteFill } from "remotion";
import { Layer } from "./camera";
import { FLOOR, H, rand, useScene, W } from "./motion";
import type { Brand } from "./scenes";

// Latar sesuai topik. Semuanya berangkat dari navy/biru brand, lalu diberi aksen dan lingkungan
// sesuai isi (sains, sejarah, teknologi, ...), supaya tidak selalu biru yang sama.
// Lapisan 1 = langit/gradasi + tekstur (diam), lapisan 2 = dekorasi dan lingkungan (kedalaman 0.3).
// Lapisan 3-5 (objek, karakter, teks) ada di scenes.tsx. Partikel hanya untuk lingkungan yang cocok.

export type Env = "umum" | "sains" | "sejarah" | "teknologi" | "alam" | "kota" | "data" | "terang" | "psikologi" | "fakta" | "banding" | "minimal";

type Look = { sky: [string, string, string]; ink: string; accent: string; glow: string; floor: string; light: boolean };

export const envLook = (env: Env, b: Brand): Look => {
  const p = b.palette;
  const L: Record<Env, Look> = {
    umum: { sky: [p.bg2, p.bg, "#070E22"], ink: p.ink, accent: p.accent, glow: p.primary, floor: "#0E1B42", light: false },
    sains: { sky: ["#0F4C6B", "#0B2C55", "#081A3A"], ink: p.ink, accent: p.secondary, glow: "#3FE0D0", floor: "#0A2948", light: false },
    sejarah: { sky: ["#3C2E5C", "#1C1F4A", "#0F1430"], ink: "#FFF3DE", accent: p.accent, glow: "#E8A24A", floor: "#2A2040", light: false },
    teknologi: { sky: ["#1B2C8F", "#0C1450", "#050A2A"], ink: p.ink, accent: "#5CE1FF", glow: "#7A5CFF", floor: "#0A1240", light: false },
    alam: { sky: ["#1F6E8C", "#174E7A", "#0E2F55"], ink: p.ink, accent: p.accent, glow: "#7ED957", floor: "#123E3A", light: false },
    kota: { sky: ["#2F6BFF", "#1F3FB8", "#14286E"], ink: "#FFFFFF", accent: p.accent, glow: "#FFFFFF", floor: "#132457", light: false },
    data: { sky: ["#152A66", "#0B1530", "#081024"], ink: p.ink, accent: p.accent, glow: p.primary, floor: "#0E1B42", light: false },
    terang: { sky: ["#FFFFFF", "#EEF3FC", "#DCE6F8"], ink: p.bg, accent: p.primary, glow: p.accent, floor: "#D2DDF3", light: true },
    psikologi: { sky: ["#1D2B5E", "#141E45", "#0D1433"], ink: p.ink, accent: "#B7A6FF", glow: "#B7A6FF", floor: "#141E45", light: false },
    fakta: { sky: ["#FFD27A", "#FFB547", "#F29A2E"], ink: p.bg, accent: p.bg, glow: "#FFFFFF", floor: "#E8962A", light: true },
    banding: { sky: [p.bg2, p.bg, "#070E22"], ink: p.ink, accent: p.accent, glow: p.primary, floor: "#0E1B42", light: false },
    minimal: { sky: ["#0E1A3D", "#0B1530", "#0B1530"], ink: p.ink, accent: p.accent, glow: p.primary, floor: "#0B1530", light: false },
  };
  return L[env];
};

// ---------------------------------------------------------------- dekorasi per lingkungan (SVG 1080x1920)
const Deco: React.FC<{ env: Env; lk: Look; frame: number }> = ({ env, lk, frame }) => {
  const c = lk.light ? lk.accent : lk.ink;
  switch (env) {
    case "psikologi":
      // simbolik: profil kepala dengan satu lingkaran pikiran, garis tipis
      return (
        <g opacity={0.2}>
          <path d="M760 1300 C700 1240 690 1150 720 1080 C680 1050 690 1000 730 990 C720 900 780 800 890 790 C1000 780 1080 860 1080 960 V1300 Z" fill="none" stroke={lk.accent} strokeWidth={5} />
          <circle cx={900} cy={930} r={60 + Math.sin(frame / 40) * 6} fill="none" stroke={lk.accent} strokeWidth={4} strokeDasharray="6 12" />
          <path d="M80 420 C300 380 420 520 620 470" stroke={c} strokeWidth={3} fill="none" />
        </g>
      );
    case "fakta":
      // kontras: pita diagonal navy + titik halftone
      return (
        <g>
          <path d="M-100 1180 L1180 760 L1180 980 L-100 1400 Z" fill={lk.ink} opacity={0.1} />
          {Array.from({ length: 70 }, (_, i) => {
            const col = i % 10;
            const row = Math.floor(i / 10);
            return <circle key={i} cx={760 + col * 34} cy={160 + row * 34} r={3 + (col + row) * 0.5} fill={lk.ink} opacity={0.12} />;
          })}
        </g>
      );
    case "banding":
      // struktur terbagi: dua bidang dengan sudut berbeda
      return (
        <g>
          <path d="M0 0 H560 L480 1920 H0 Z" fill="#FFFFFF" opacity={0.03} />
          <path d="M560 0 H1080 V1920 H480 Z" fill={lk.glow} opacity={0.07} />
        </g>
      );
    case "minimal":
      // satu busur lembut, sisanya ruang kosong
      return <path d="M-200 1500 A900 900 0 0 1 1280 1300" fill="none" stroke={lk.accent} strokeWidth={3} opacity={0.18} />;
    case "sains":
      // ruang eksperimen: labu, tabung reaksi, molekul, orbit atom
      return (
        <g opacity={0.22}>
          <g transform="translate(560 220)">
            <path d="M120 980 V860 H100 V840 H200 V860 H180 V980 L250 1130 Q260 1160 230 1160 H70 Q40 1160 50 1130 Z" fill="none" stroke={lk.glow} strokeWidth={6} strokeLinejoin="round" />
            <path d="M70 1100 H230 L250 1130 Q260 1160 230 1160 H70 Q40 1160 50 1130 Z" fill={lk.glow} opacity={0.6} />
          </g>
          {[0, 60, 120].map((a) => (
            <ellipse key={a} cx={900} cy={1210} rx={150} ry={50} fill="none" stroke={lk.glow} strokeWidth={4} transform={`rotate(${a + frame * 0.3} 900 1210)`} />
          ))}
          <circle cx={900} cy={1210} r={20} fill={lk.glow} />
          {[[860, 600], [960, 680], [900, 760], [1000, 560]].map(([x, y], i, a) => (
            <g key={i}>
              {i > 0 && <line x1={a[i - 1][0]} y1={a[i - 1][1]} x2={x} y2={y} stroke={c} strokeWidth={6} />}
              <circle cx={x} cy={y} r={22 + (i % 2) * 10} fill={i % 2 ? lk.accent : c} />
            </g>
          ))}
        </g>
      );
    case "sejarah":
      // gulungan, pilar, dan pola batik kawung sederhana
      return (
        <g opacity={0.18}>
          {Array.from({ length: 6 }, (_, i) => (
            <g key={i} transform={`translate(${120 + i * 170} 220)`}>
              <ellipse cx={0} cy={-26} rx={18} ry={30} fill={lk.accent} />
              <ellipse cx={0} cy={26} rx={18} ry={30} fill={lk.accent} />
              <ellipse cx={-26} cy={0} rx={30} ry={18} fill={lk.accent} />
              <ellipse cx={26} cy={0} rx={30} ry={18} fill={lk.accent} />
            </g>
          ))}
          {[110, 970].map((x) => (
            <g key={x}>
              <rect x={x - 40} y={700} width={80} height={740} fill={c} opacity={0.22} />
              <rect x={x - 64} y={670} width={128} height={40} fill={c} />
              <rect x={x - 64} y={1400} width={128} height={40} fill={c} />
            </g>
          ))}
        </g>
      );
    case "teknologi":
      // grid perspektif dan jalur sirkuit
      return (
        <g opacity={0.28}>
          {Array.from({ length: 14 }, (_, i) => (
            <line key={`v${i}`} x1={540} y1={980} x2={-800 + i * 205} y2={1920} stroke={lk.accent} strokeWidth={2} />
          ))}
          {Array.from({ length: 8 }, (_, i) => {
            const y = 1000 + Math.pow(((i + (frame / 40) % 1) / 8), 2) * 920;
            return <line key={`h${i}`} x1={0} y1={y} x2={1080} y2={y} stroke={lk.accent} strokeWidth={2} />;
          })}
          {[[80, 300, 300, 300, 300, 520], [1000, 420, 820, 420, 820, 260], [60, 780, 220, 780]].map((d, i) => (
            <g key={i}>
              <polyline points={d.join(" ")} fill="none" stroke={lk.glow} strokeWidth={5} />
              <circle cx={d[d.length - 2]} cy={d[d.length - 1]} r={12} fill={lk.glow} />
            </g>
          ))}
        </g>
      );
    case "alam":
      // bukit berlapis dan matahari
      return (
        <g>
          <circle cx={820} cy={420} r={150} fill="#FFE3A3" opacity={0.35} />
          <path d="M0 1100 C200 960 360 1000 540 1060 C720 1120 880 980 1080 1020 V1920 H0 Z" fill="#1E6B5C" opacity={0.55} />
          <path d="M0 1260 C240 1160 420 1220 620 1260 C820 1300 940 1200 1080 1220 V1920 H0 Z" fill="#17574B" opacity={0.8} />
        </g>
      );
    case "kota":
      // siluet gedung
      return (
        <g opacity={0.25}>
          {Array.from({ length: 11 }, (_, i) => {
            const h = 220 + rand(i + 3) * 420;
            return (
              <g key={i}>
                <rect x={i * 100 - 10} y={FLOOR - h} width={90} height={h} fill="#0E1B42" />
                {Array.from({ length: Math.floor(h / 70) }, (_, j) => (
                  <rect key={j} x={i * 100 + 12} y={FLOOR - h + 24 + j * 70} width={44} height={22} fill={lk.accent} opacity={rand(i * 9 + j) > 0.5 ? 0.9 : 0.15} />
                ))}
              </g>
            );
          })}
        </g>
      );
    case "data":
      // garis sumbu dan titik-titik grafik samar
      return (
        <g opacity={0.16}>
          {Array.from({ length: 9 }, (_, i) => (
            <line key={i} x1={0} y1={220 + i * 150} x2={1080} y2={220 + i * 150} stroke={c} strokeWidth={2} strokeDasharray="6 14" />
          ))}
          <polyline points="0,1200 180,1080 360,1140 540,900 720,960 900,700 1080,760" fill="none" stroke={lk.accent} strokeWidth={8} />
        </g>
      );
    case "terang":
      // bentuk geometris lembut (bukan lingkaran berulang): segitiga dan garis diagonal
      return (
        <g opacity={0.5}>
          <path d="M-60 300 L380 120 L200 520 Z" fill={`${lk.accent}22`} />
          <path d="M1140 1100 L760 1380 L1100 1520 Z" fill={`${lk.glow}33`} />
          {Array.from({ length: 6 }, (_, i) => (
            <line key={i} x1={700 + i * 60} y1={140} x2={1080} y2={520 - i * 60} stroke={`${lk.accent}30`} strokeWidth={4} />
          ))}
        </g>
      );
    default:
      // umum: garis kontur halus + bentuk A brand samar
      return (
        <g opacity={0.14}>
          {Array.from({ length: 7 }, (_, i) => (
            <path key={i} d={`M-40 ${260 + i * 130} C 300 ${200 + i * 130 + Math.sin(frame / 50 + i) * 30}, 700 ${330 + i * 130}, 1120 ${250 + i * 130}`} stroke={c} strokeWidth={3} fill="none" />
          ))}
          <path d="M760 1300 L900 900 L1040 1300 M805 1170 H995" stroke={lk.accent} strokeWidth={26} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </g>
      );
  }
};

// Tekstur per lingkungan (bukan grid yang sama di semua adegan)
const Texture: React.FC<{ env: Env; lk: Look }> = ({ env, lk }) => {
  if (env === "sejarah")
    // tekstur dokumenter: butiran kertas + vinyet hangat
    return (
      <>
        <svg width={W} height={H} style={{ position: "absolute", inset: 0, opacity: 0.16, mixBlendMode: "overlay" }}>
          <filter id="kertas"><feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves={2} seed={4} /><feColorMatrix type="saturate" values="0" /></filter>
          <rect width={W} height={H} filter="url(#kertas)" />
        </svg>
        <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 45%, transparent 55%, rgba(40,20,0,0.35) 100%)" }} />
      </>
    );
  if (env === "teknologi")
    return <AbsoluteFill style={{ backgroundImage: `linear-gradient(${lk.accent}14 2px, transparent 2px), linear-gradient(90deg, ${lk.accent}14 2px, transparent 2px)`, backgroundSize: "90px 90px", opacity: 0.6 }} />;
  if (env === "data")
    return <AbsoluteFill style={{ backgroundImage: `radial-gradient(${lk.ink}12 1.5px, transparent 2px)`, backgroundSize: "34px 34px", opacity: 0.6 }} />;
  if (env === "sains")
    // pola heksagon samar
    return (
      <svg width={W} height={H} style={{ position: "absolute", inset: 0, opacity: 0.08 }}>
        <defs>
          <pattern id="heks" width={84} height={146} patternUnits="userSpaceOnUse">
            <path d="M42 0 L84 24 L84 73 L42 97 L0 73 L0 24 Z M42 97 V146" fill="none" stroke={lk.ink} strokeWidth={2} />
          </pattern>
        </defs>
        <rect width={W} height={H} fill="url(#heks)" />
      </svg>
    );
  return null;
};

export const Environment: React.FC<{ env: Env; brand: Brand; floor?: boolean }> = ({ env, brand, floor = true }) => {
  const { frame } = useScene();
  const lk = envLook(env, brand);
  const [a, b, c] = lk.sky;
  // partikel dekat: bentuk berbeda per lingkungan (debu cahaya, gelembung, piksel)
  const moteCount = env === "sains" || env === "alam" || env === "teknologi" ? 9 : 0;
  const motes = Array.from({ length: moteCount }, (_, i) => {
    const r = rand(i + 1);
    return { x: ((i * 97 + r * 300) % 1040) + 20, y: (((i * 173 + frame * (0.4 + r * 0.9)) % (H + 80)) + H) % (H + 80) - 40, s: 5 + r * 12, o: 0.12 + r * 0.25 };
  }).map((m) => ({ ...m, y: H - m.y }));
  return (
    <>
      <AbsoluteFill style={{ background: `radial-gradient(ellipse 120% 80% at 30% 18%, ${a} 0%, ${b} 52%, ${c} 100%)` }} />
      {/* cahaya lembut yang bergeser sangat pelan (gerak paling halus = latar) */}
      {env !== "minimal" && <AbsoluteFill style={{ background: `radial-gradient(circle at ${70 + Math.sin(frame / 120) * 5}% ${30 + Math.cos(frame / 140) * 4}%, ${lk.glow}2A 0%, transparent 45%)` }} />}
      <Texture env={env} lk={lk} />
      <Layer depth={0.3}>
        <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
          <Deco env={env} lk={lk} frame={frame} />
        </svg>
      </Layer>
      {floor && (
        <Layer depth={0.6}>
          {/* lantai tempat ADRI berpijak: bidang dengan cahaya di tepi */}
          {/* sangat lebar supaya tidak habis saat kamera bergeser jauh (proses, linimasa) */}
          <div style={{ position: "absolute", left: -4000, right: -4000, top: FLOOR - 6, height: H - FLOOR + 200, background: `linear-gradient(180deg, ${lk.floor} 0%, ${c} 70%)` }} />
          <div style={{ position: "absolute", left: -200, right: -200, top: FLOOR - 6, height: 4, background: `linear-gradient(90deg, transparent, ${lk.accent}99, transparent)` }} />
        </Layer>
      )}
      <Layer depth={1.3}>
        {motes.map((m, i) => (
          <div key={i} style={{ position: "absolute", left: m.x, top: m.y, width: m.s, height: m.s, borderRadius: env === "teknologi" ? 2 : "50%", background: lk.light ? lk.accent : lk.ink, opacity: m.o }} />
        ))}
      </Layer>
    </>
  );
};
