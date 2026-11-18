import { Img, staticFile, useCurrentFrame } from "remotion";

// ADRI, presenter utama Adriy.project.
// Versi vector 2D modern (datar). Untuk versi semi-realistis, isi character.assets di
// pipeline/styles/adriy.json dengan PNG transparan per pose; komponen ini lalu memakai gambar itu.

export type Pose = "sapa" | "jelaskan" | "tunjuk" | "pikir" | "kaget" | "senang";

export type CharacterSpec = {
  colors: { skin: string; skinShade: string; hair: string; hoodie: string; hoodieAccent: string; shirt: string; glasses: string };
  assets?: Partial<Record<Pose, string>>;
};

type Props = {
  spec: CharacterSpec;
  pose: Pose;
  size: number; // tinggi dalam piksel
  talking?: boolean; // mulut bergerak (saat narasi berjalan)
  facing?: "left" | "right";
  seed?: number; // supaya kedipan tidak serempak antar adegan
};

// Lengan: dua ruas (lengan atas + bawah) yang berputar dari bahu dan siku.
const Arm: React.FC<{ x: number; y: number; a1: number; a2: number; c: CharacterSpec["colors"]; hand?: "open" | "point" | "fist" }> = ({ x, y, a1, a2, c, hand = "open" }) => (
  <g transform={`translate(${x} ${y}) rotate(${a1})`}>
    <rect x={-17} y={0} width={34} height={92} rx={17} fill={c.hoodie} />
    <g transform={`translate(0 84) rotate(${a2})`}>
      <rect x={-15} y={0} width={30} height={82} rx={15} fill={c.hoodie} />
      <rect x={-15} y={64} width={30} height={14} rx={6} fill={c.hoodieAccent} />
      <g transform="translate(0 92)">
        {hand === "point" ? (
          <>
            <circle r={17} fill={c.skin} />
            <rect x={-6} y={6} width={12} height={34} rx={6} fill={c.skin} />
          </>
        ) : hand === "fist" ? (
          <circle r={17} fill={c.skin} />
        ) : (
          <>
            <ellipse rx={19} ry={22} fill={c.skin} />
            <rect x={14} y={-14} width={11} height={22} rx={5} fill={c.skin} transform="rotate(-30)" />
          </>
        )}
      </g>
    </g>
  </g>
);

// Sudut lengan [bahu, siku] per pose: kiri (layar) dan kanan (layar).
const POSES: Record<Pose, { l: [number, number]; r: [number, number]; lh?: "open" | "point" | "fist"; rh?: "open" | "point" | "fist"; brow: number; mouth: "smile" | "o" | "grin" | "flat"; eyes: "normal" | "up" | "wide" | "happy"; tilt: number }> = {
  sapa: { l: [12, -8], r: [-150, -25], rh: "open", brow: 0, mouth: "smile", eyes: "normal", tilt: -2 },
  jelaskan: { l: [40, -95], r: [-40, 95], lh: "open", rh: "open", brow: -2, mouth: "smile", eyes: "normal", tilt: 0 },
  tunjuk: { l: [12, -8], r: [-100, 10], rh: "point", brow: -3, mouth: "smile", eyes: "normal", tilt: 2 },
  pikir: { l: [14, -10], r: [-28, 140], rh: "fist", brow: 4, mouth: "flat", eyes: "up", tilt: -4 },
  kaget: { l: [150, 25], r: [-150, -25], lh: "open", rh: "open", brow: 8, mouth: "o", eyes: "wide", tilt: 0 },
  senang: { l: [150, 30], r: [-150, -30], lh: "fist", rh: "fist", brow: 2, mouth: "grin", eyes: "happy", tilt: 3 },
};

export const Adri: React.FC<Props> = ({ spec, pose, size, talking = false, facing = "right", seed = 0 }) => {
  const frame = useCurrentFrame();
  const scale = size / 760;
  const flip = facing === "left" ? -1 : 1;

  if (spec.assets?.[pose]) {
    const bob = Math.sin((frame + seed * 13) / 22) * 6;
    return <Img src={staticFile(spec.assets[pose]!)} style={{ height: size, translate: `0px ${bob}px`, scale: `${flip} 1` }} />;
  }

  const c = spec.colors;
  const p = POSES[pose];
  const breathe = 1 + Math.sin((frame + seed * 17) / 24) * 0.012;
  const blink = (frame + seed * 31) % 110 < 4; // berkedip kira-kira tiap 3,7 detik
  const mouthOpen = talking ? Math.abs(Math.sin((frame + seed) * 0.55)) * (0.6 + 0.4 * Math.abs(Math.sin(frame * 0.17))) : 0;
  const wave = pose === "sapa" ? Math.sin(frame / 5) * 14 : 0;

  const eye = (cx: number) =>
    blink ? (
      <path d={`M${cx - 13} 0 Q${cx} 6 ${cx + 13} 0`} stroke={c.hair} strokeWidth={5} fill="none" strokeLinecap="round" />
    ) : p.eyes === "happy" ? (
      <path d={`M${cx - 13} 4 Q${cx} -10 ${cx + 13} 4`} stroke={c.hair} strokeWidth={6} fill="none" strokeLinecap="round" />
    ) : (
      <g>
        <ellipse cx={cx} cy={0} rx={p.eyes === "wide" ? 12 : 10} ry={p.eyes === "wide" ? 14 : 12} fill="#fff" />
        <circle cx={cx + (p.eyes === "up" ? 2 : 3)} cy={p.eyes === "up" ? -5 : 1} r={6.5} fill={c.hair} />
        <circle cx={cx + 5} cy={p.eyes === "up" ? -7 : -2} r={2} fill="#fff" />
      </g>
    );

  const mouth =
    p.mouth === "o" ? (
      <ellipse cx={0} cy={0} rx={13} ry={16} fill="#6B2A2A" />
    ) : talking ? (
      <path d={`M-22 -4 Q0 ${4 + mouthOpen * 22} 22 -4 Q0 ${2 + mouthOpen * 8} -22 -4 Z`} fill="#6B2A2A" />
    ) : p.mouth === "grin" ? (
      <path d="M-26 -6 Q0 24 26 -6 Z" fill="#fff" stroke="#6B2A2A" strokeWidth={5} strokeLinejoin="round" />
    ) : p.mouth === "flat" ? (
      <path d="M-16 0 Q2 4 16 -2" stroke="#6B2A2A" strokeWidth={6} fill="none" strokeLinecap="round" />
    ) : (
      <path d="M-22 -4 Q0 16 22 -4" stroke="#6B2A2A" strokeWidth={6} fill="none" strokeLinecap="round" />
    );

  return (
    <svg width={500 * scale} height={size} viewBox="0 0 500 760" style={{ overflow: "visible", scale: `${flip} 1` }}>
      <g transform={`translate(250 760) scale(${breathe}) translate(-250 -760)`}>
        {/* badan */}
        <path d="M110 760 C110 560 150 470 250 470 C350 470 390 560 390 760 Z" fill={c.hoodie} />
        <path d="M205 470 L250 560 L295 470 Z" fill={c.shirt} />
        <path d="M150 760 C150 600 170 520 200 495" stroke={c.hoodieAccent} strokeWidth={10} fill="none" strokeLinecap="round" />
        <path d="M350 760 C350 600 330 520 300 495" stroke={c.hoodieAccent} strokeWidth={10} fill="none" strokeLinecap="round" />
        <circle cx={250} cy={640} r={30} fill={c.hoodieAccent} />
        <text x={250} y={652} textAnchor="middle" fontFamily="Plus Jakarta Sans" fontWeight={800} fontSize={34} fill="#fff">A</text>
        {/* lengan di belakang kepala (kiri layar) */}
        <Arm x={150} y={500} a1={p.l[0]} a2={p.l[1]} c={c} hand={p.lh} />
        {/* leher + kepala */}
        <rect x={222} y={400} width={56} height={80} rx={24} fill={c.skinShade} />
        <g transform={`rotate(${p.tilt} 250 330)`}>
          <ellipse cx={250} cy={300} rx={112} ry={128} fill={c.skin} />
          <ellipse cx={140} cy={310} rx={18} ry={26} fill={c.skinShade} />
          <ellipse cx={360} cy={310} rx={18} ry={26} fill={c.skinShade} />
          {/* rambut: jambul ke kanan */}
          <path d="M132 270 C120 170 190 120 262 128 C330 132 380 170 372 262 C360 220 330 200 300 196 C320 176 300 160 270 168 C230 176 190 190 168 230 C156 246 146 262 132 270 Z" fill={c.hair} />
          <path d="M258 128 C300 92 360 110 372 160 C340 140 300 140 270 160 Z" fill={c.hair} />
          {/* alis */}
          <path d={`M190 ${252 - p.brow} Q212 ${242 - p.brow} 232 ${252 - p.brow}`} stroke={c.hair} strokeWidth={10} fill="none" strokeLinecap="round" />
          <path d={`M268 ${252 - p.brow} Q290 ${242 - p.brow} 310 ${252 - p.brow}`} stroke={c.hair} strokeWidth={10} fill="none" strokeLinecap="round" />
          {/* mata + kacamata */}
          <g transform="translate(0 292)">
            {eye(211)}
            {eye(289)}
          </g>
          <circle cx={211} cy={292} r={30} fill="none" stroke={c.glasses} strokeWidth={5} />
          <circle cx={289} cy={292} r={30} fill="none" stroke={c.glasses} strokeWidth={5} />
          <path d="M241 290 Q250 284 259 290" stroke={c.glasses} strokeWidth={5} fill="none" />
          {/* hidung + mulut */}
          <path d="M250 312 Q258 334 246 340" stroke={c.skinShade} strokeWidth={6} fill="none" strokeLinecap="round" />
          <g transform="translate(250 372)">{mouth}</g>
          <ellipse cx={184} cy={346} rx={16} ry={9} fill="#E0796B" opacity={0.35} />
          <ellipse cx={316} cy={346} rx={16} ry={9} fill="#E0796B" opacity={0.35} />
        </g>
        {/* lengan di depan (kanan layar) */}
        <Arm x={350} y={500} a1={p.r[0] + wave} a2={p.r[1]} c={c} hand={p.rh} />
      </g>
    </svg>
  );
};
