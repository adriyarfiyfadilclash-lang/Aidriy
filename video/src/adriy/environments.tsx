import { AbsoluteFill } from "remotion";
import { Layer } from "./camera";
import { FLOOR, H, rand, useScene, W } from "./motion";
import type { Brand } from "./scenes";

// Latar sesuai topik. Semuanya berangkat dari navy/biru brand, lalu diberi aksen dan lingkungan
// sesuai isi (sains, sejarah, teknologi, ...), supaya tidak selalu biru yang sama.
// Tiga kedalaman: langit (diam), dekorasi jauh (0.25), partikel dekat (1.3).

export type Env = "umum" | "sains" | "sejarah" | "teknologi" | "alam" | "kota" | "data" | "terang";

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
  };
  return L[env];
};

// ---------------------------------------------------------------- dekorasi per lingkungan (SVG 1080x1920)
const Deco: React.FC<{ env: Env; lk: Look; frame: number }> = ({ env, lk, frame }) => {
  const c = lk.light ? lk.accent : lk.ink;
  switch (env) {
    case "sains":
      // molekul dan orbit atom
      return (
        <g opacity={0.22}>
          {[0, 60, 120].map((a) => (
            <ellipse key={a} cx={860} cy={360} rx={210} ry={70} fill="none" stroke={lk.glow} strokeWidth={4} transform={`rotate(${a + frame * 0.3} 860 360)`} />
          ))}
          <circle cx={860} cy={360} r={26} fill={lk.glow} />
          {[[140, 700], [260, 800], [180, 920], [320, 640]].map(([x, y], i, a) => (
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
              <rect x={x - 40} y={700} width={80} height={740} fill={c} opacity={0.4} />
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

export const Environment: React.FC<{ env: Env; brand: Brand; floor?: boolean }> = ({ env, brand, floor = true }) => {
  const { frame } = useScene();
  const lk = envLook(env, brand);
  const [a, b, c] = lk.sky;
  // partikel dekat: bentuk berbeda per lingkungan (debu cahaya, gelembung, piksel)
  const motes = Array.from({ length: 12 }, (_, i) => {
    const r = rand(i + 1);
    return { x: ((i * 97 + r * 300) % 1040) + 20, y: (((i * 173 + frame * (0.4 + r * 0.9)) % (H + 80)) + H) % (H + 80) - 40, s: 5 + r * 12, o: 0.12 + r * 0.25 };
  }).map((m) => ({ ...m, y: H - m.y }));
  return (
    <>
      <AbsoluteFill style={{ background: `radial-gradient(ellipse 120% 80% at 30% 18%, ${a} 0%, ${b} 52%, ${c} 100%)` }} />
      {/* cahaya lembut yang bergeser */}
      <AbsoluteFill style={{ background: `radial-gradient(circle at ${70 + Math.sin(frame / 80) * 8}% ${30 + Math.cos(frame / 90) * 6}%, ${lk.glow}33 0%, transparent 45%)` }} />
      {/* tekstur butiran halus */}
      <AbsoluteFill style={{ backgroundImage: `radial-gradient(${lk.ink}10 1.5px, transparent 2px)`, backgroundSize: "34px 34px", opacity: 0.6 }} />
      <Layer depth={0.25}>
        <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
          <Deco env={env} lk={lk} frame={frame} />
        </svg>
      </Layer>
      {floor && (
        <Layer depth={0.6}>
          {/* lantai tempat ADRI berpijak: bidang dengan cahaya di tepi */}
          <div style={{ position: "absolute", left: -200, right: -200, top: FLOOR - 6, height: H - FLOOR + 200, background: `linear-gradient(180deg, ${lk.floor} 0%, ${c} 70%)` }} />
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
