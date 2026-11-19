import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";

// ADRI, presenter utama Adriy.project. Identitas tetap: wajah oval sawo matang, rambut hitam berjambul
// ke kanan, kacamata bulat tipis, hoodie biru tua dengan garis biru terang dan logo A, kaos putih.
// Badan penuh (berdiri di lantai) supaya bisa berjalan dan tidak terpotong tak sengaja.
// Untuk versi semi-realistis, isi character.assets di pipeline/styles/adriy.json dengan PNG per pose.

export type Pose =
  | "sapa" // menyapa
  | "jelaskan" // berdiri menjelaskan
  | "tunjuk" // menunjuk objek
  | "angkat" // mengangkat tangan
  | "jalan" // berjalan
  | "kaget" // terkejut
  | "pikir" // berpikir
  | "pegang" // memegang objek
  | "sentuh" // berinteraksi dengan diagram
  | "lihat" // melihat ke arah informasi
  | "ajak" // bicara kepada penonton
  | "senang"
  | "tanya" // bertanya: telapak terbuka ke depan, alis naik, kepala miring
  | "banding" // membandingkan: dua telapak terbuka naik-turun seperti timbangan
  | "rayakan" // merayakan penemuan: tinju ke atas, melompat kecil
  | "simpulkan" // memberikan kesimpulan: satu jari ke atas, mengangguk
  | "tunjukkan"; // menunjukkan sesuatu: telapak terbuka mempersembahkan objek

export type Look = "maju" | "penonton" | "atas" | "bawah"; // maju = ke arah yang dihadap
export type PoseCue = { at: number; pose: Pose; look?: Look };

export type CharacterSpec = {
  colors: { skin: string; skinShade: string; hair: string; hoodie: string; hoodieAccent: string; shirt: string; glasses: string; pants?: string; shoes?: string };
  assets?: Partial<Record<Pose, string>>;
};

type Props = {
  spec: CharacterSpec;
  pose: Pose | PoseCue[]; // satu pose, atau urutan pose yang berganti mengikuti narasi
  size: number; // tinggi badan penuh dalam piksel
  talking?: boolean;
  facing?: "left" | "right";
  look?: Look;
  holding?: React.ReactNode; // elemen SVG yang dipegang (koordinat lokal, titik 0,0 = di antara tangan)
  aim?: [number, number] | null; // titik yang ditunjuk, dalam koordinat lokal ADRI (viewBox 500x1180, sebelum dicerminkan)
  seed?: number;
};

// Sudut lengan supaya lurus mengarah ke titik (dx, dy) dari bahu: lengan turun = 0 derajat.
const aimAngle = (dx: number, dy: number) => (Math.atan2(-dx, dy) * 180) / Math.PI;

type Hand = "open" | "point" | "fist";
type PoseDef = { l: [number, number]; r: [number, number]; lh: Hand; rh: Hand; brow: number; mouth: "smile" | "o" | "grin" | "flat"; eyes: "normal" | "wide" | "happy"; tilt: number; look: Look; legs: [number, number] };

// Sudut [bahu, siku] lengan kiri layar (l) dan kanan layar (r), dalam derajat dari posisi lengan turun.
const POSES: Record<Pose, PoseDef> = {
  sapa: { l: [12, -8], r: [-150, -25], lh: "open", rh: "open", brow: 0, mouth: "smile", eyes: "normal", tilt: -2, look: "penonton", legs: [4, -4] },
  jelaskan: { l: [40, -95], r: [-40, 95], lh: "open", rh: "open", brow: -2, mouth: "smile", eyes: "normal", tilt: 0, look: "penonton", legs: [5, -3] },
  tunjuk: { l: [12, -8], r: [-100, 10], lh: "open", rh: "point", brow: -3, mouth: "smile", eyes: "normal", tilt: 2, look: "maju", legs: [3, -6] },
  angkat: { l: [12, -8], r: [-162, 4], lh: "open", rh: "open", brow: 3, mouth: "grin", eyes: "normal", tilt: -3, look: "penonton", legs: [4, -4] },
  jalan: { l: [0, -15], r: [0, 15], lh: "fist", rh: "fist", brow: 0, mouth: "smile", eyes: "normal", tilt: 0, look: "maju", legs: [0, 0] },
  kaget: { l: [150, 25], r: [-150, -25], lh: "open", rh: "open", brow: 9, mouth: "o", eyes: "wide", tilt: 0, look: "penonton", legs: [8, -8] },
  pikir: { l: [14, -10], r: [-28, 140], lh: "open", rh: "fist", brow: 5, mouth: "flat", eyes: "normal", tilt: -4, look: "atas", legs: [2, -5] },
  pegang: { l: [32, -110], r: [-32, 110], lh: "fist", rh: "fist", brow: 0, mouth: "smile", eyes: "normal", tilt: 0, look: "bawah", legs: [4, -4] },
  sentuh: { l: [14, -8], r: [-128, -28], lh: "open", rh: "point", brow: -2, mouth: "smile", eyes: "normal", tilt: 3, look: "maju", legs: [2, -7] },
  lihat: { l: [10, -6], r: [-10, 6], lh: "open", rh: "open", brow: 1, mouth: "smile", eyes: "normal", tilt: 4, look: "maju", legs: [3, -3] },
  ajak: { l: [12, -8], r: [-58, -68], lh: "open", rh: "open", brow: 0, mouth: "smile", eyes: "normal", tilt: -1, look: "penonton", legs: [4, -4] },
  senang: { l: [150, 30], r: [-150, -30], lh: "fist", rh: "fist", brow: 2, mouth: "grin", eyes: "happy", tilt: 3, look: "penonton", legs: [6, -6] },
  tanya: { l: [12, -8], r: [-62, -52], lh: "open", rh: "open", brow: 8, mouth: "flat", eyes: "normal", tilt: -7, look: "penonton", legs: [3, -5] },
  banding: { l: [62, -40], r: [-62, 40], lh: "open", rh: "open", brow: 3, mouth: "smile", eyes: "normal", tilt: 0, look: "penonton", legs: [6, -6] },
  rayakan: { l: [30, -70], r: [-168, -12], lh: "fist", rh: "fist", brow: 4, mouth: "grin", eyes: "happy", tilt: -4, look: "penonton", legs: [8, -8] },
  simpulkan: { l: [14, -10], r: [-150, -30], lh: "open", rh: "point", brow: 1, mouth: "smile", eyes: "normal", tilt: 2, look: "penonton", legs: [4, -4] },
  tunjukkan: { l: [12, -8], r: [-82, -8], lh: "open", rh: "open", brow: 2, mouth: "smile", eyes: "normal", tilt: 3, look: "maju", legs: [3, -6] },
};

const lerp = (a: number, b: number, w: number) => a + (b - a) * w;

const RIM = "rgba(255,255,255,0.22)"; // garis cahaya tipis supaya siluet terbaca di latar biru tua

const Arm: React.FC<{ x: number; y: number; a1: number; a2: number; c: CharacterSpec["colors"]; hand: Hand }> = ({ x, y, a1, a2, c, hand }) => (
  <g transform={`translate(${x} ${y}) rotate(${a1})`}>
    <rect x={-21} y={0} width={42} height={112} rx={21} fill={c.hoodie} stroke={RIM} strokeWidth={4} />
    <g transform={`translate(0 102) rotate(${a2})`}>
      <rect x={-19} y={0} width={38} height={98} rx={19} fill={c.hoodie} stroke={RIM} strokeWidth={4} />
      <rect x={-19} y={80} width={38} height={14} rx={6} fill={c.hoodieAccent} />
      <g transform="translate(0 110)">
        {hand === "point" ? (
          <>
            <circle r={20} fill={c.skin} />
            <rect x={-7} y={8} width={14} height={40} rx={7} fill={c.skin} />
          </>
        ) : hand === "fist" ? (
          <circle r={20} fill={c.skin} />
        ) : (
          <>
            <ellipse rx={22} ry={26} fill={c.skin} />
            <rect x={16} y={-16} width={12} height={26} rx={6} fill={c.skin} transform="rotate(-30)" />
          </>
        )}
      </g>
    </g>
  </g>
);

const Leg: React.FC<{ x: number; a: number; c: CharacterSpec["colors"] }> = ({ x, a, c }) => (
  <g transform={`translate(${x} 830) rotate(${a})`}>
    <rect x={-27} y={0} width={54} height={300} rx={24} fill={c.pants ?? "#16203F"} />
    <ellipse cx={a >= 0 ? 10 : -10} cy={316} rx={46} ry={22} fill={c.shoes ?? "#F5F7FB"} />
    <rect x={-40} y={318} width={80} height={10} rx={5} fill={c.hoodieAccent} />
  </g>
);

export const Adri: React.FC<Props> = ({ spec, pose, size, talking = false, facing = "right", look, holding, aim, seed = 0 }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const cues: PoseCue[] = typeof pose === "string" ? [{ at: 0, pose }] : [...pose].sort((a, b) => a.at - b.at);
  const curIdx = Math.max(0, cues.filter((c) => c.at * fps <= frame).length - 1);
  const cur = cues[curIdx];
  const prev = cues[Math.max(0, curIdx - 1)];
  // perpindahan pose memakai spring, jadi tidak patah
  const w = curIdx === 0 ? 1 : spring({ frame: frame - Math.round(cur.at * fps), fps, config: { damping: 16, stiffness: 110 } });
  const A = POSES[prev.pose];
  const B = POSES[cur.pose];
  const flip = facing === "left" ? -1 : 1;
  const scale = size / 1180;

  if (spec.assets?.[cur.pose]) {
    const bob = Math.sin((frame + seed * 13) / 22) * 6;
    return <Img src={staticFile(spec.assets[cur.pose]!)} style={{ height: size, translate: `0px ${bob}px`, scale: `${flip} 1` }} />;
  }

  const c = spec.colors;
  const face = w > 0.5 ? B : A;
  const walking = cur.pose === "jalan";
  const step = Math.sin((frame + seed) * 0.32);
  const sway = walking ? 0 : Math.sin((frame + seed * 7) / 30) * 3; // lengan bergoyang halus saat diam
  const explain = cur.pose === "jelaskan" ? Math.sin((frame + seed) / 9) * 8 : 0; // tangan bergerak saat menjelaskan
  // membandingkan: kedua tangan naik-turun bergantian seperti timbangan
  const scales = cur.pose === "banding" ? Math.sin((frame + seed) / 14) * 16 * w : 0;
  const lA: [number, number] = walking ? [-step * 22, -12] : [lerp(A.l[0], B.l[0], w) + sway + scales, lerp(A.l[1], B.l[1], w) + explain];
  let rA: [number, number] = walking ? [step * 22, 12] : [lerp(A.r[0], B.r[0], w) - sway + scales, lerp(A.r[1], B.r[1], w) - explain];
  // menunjuk/menyentuh: tangan kanan diarahkan tepat ke target, tubuh condong ke sana
  const pointing = !!aim && (cur.pose === "tunjuk" || cur.pose === "sentuh");
  const aimW = pointing ? w : 0;
  if (pointing && aim) {
    const a = aimAngle(aim[0] - 354, aim[1] - 505);
    rA = [lerp(rA[0], a, aimW), lerp(rA[1], -4, aimW)];
  }
  const lean = pointing && aim ? Math.max(-4, Math.min(4, (aim[0] - 250) / 120)) * aimW : 0;
  // terkejut: tubuh tersentak ke belakang sebentar lalu kembali
  const startle = cur.pose === "kaget" ? Math.sin(Math.min(1, w) * Math.PI) * -5 : 0;
  // kepala: mengangguk kecil saat bicara, bergoyang sangat halus saat diam
  const nod = (talking ? Math.sin((frame + seed) * 0.21) * 1.8 : 0) + Math.sin((frame + seed * 5) / 45) * 1.2;
  const headY = talking ? Math.abs(Math.sin((frame + seed) * 0.21)) * -3 : 0;
  const wave = cur.pose === "sapa" ? Math.sin(frame / 5) * 14 * w : 0;
  const legs: [number, number] = walking ? [step * 20, -step * 20] : [lerp(A.legs[0], B.legs[0], w), lerp(A.legs[1], B.legs[1], w)];
  // merayakan: dua lompatan kecil setelah pose dimulai
  const hop = cur.pose === "rayakan" ? -Math.abs(Math.sin(Math.min(frame - cur.at * fps, fps * 1.2) / (fps * 0.6) * Math.PI)) * 40 : 0;
  const bob = walking ? -Math.abs(step) * 10 : hop;
  const breathe = 1 + Math.sin((frame + seed * 17) / 24) * 0.01;
  const blink = (frame + seed * 31) % 110 < 4;
  const mouthOpen = talking ? Math.abs(Math.sin((frame + seed) * 0.55)) * (0.6 + 0.4 * Math.abs(Math.sin(frame * 0.17))) : 0;
  // arah pandang: pupil dan wajah bergeser sedikit (kesan menoleh)
  const lk = look ?? cur.look ?? face.look;
  const [px0, py0, turn0] = { maju: [5, 1, 10], penonton: [0, 1, 0], atas: [2, -6, 3], bawah: [2, 5, 2] }[lk] as [number, number, number];
  // saat menunjuk, mata mengikuti target
  const px = pointing && aim ? lerp(px0, Math.max(-6, Math.min(6, (aim[0] - 250) / 40)), aimW) : px0;
  const py = pointing && aim ? lerp(py0, Math.max(-6, Math.min(6, (aim[1] - 290) / 60)), aimW) : py0;
  const turn = pointing ? lerp(turn0, 10, aimW) : turn0;
  const tilt = lerp(A.tilt, B.tilt, w) + nod;

  const eye = (cx: number) =>
    blink ? (
      <path d={`M${cx - 13} 0 Q${cx} 6 ${cx + 13} 0`} stroke={c.hair} strokeWidth={5} fill="none" strokeLinecap="round" />
    ) : face.eyes === "happy" ? (
      <path d={`M${cx - 13} 4 Q${cx} -10 ${cx + 13} 4`} stroke={c.hair} strokeWidth={6} fill="none" strokeLinecap="round" />
    ) : (
      <g>
        <ellipse cx={cx} cy={0} rx={face.eyes === "wide" ? 12 : 10} ry={face.eyes === "wide" ? 14 : 12} fill="#fff" />
        <circle cx={cx + px} cy={py} r={6.5} fill={c.hair} />
        <circle cx={cx + px + 2} cy={py - 3} r={2} fill="#fff" />
      </g>
    );
  const mouth =
    face.mouth === "o" ? (
      <ellipse cx={0} cy={0} rx={13} ry={16} fill="#6B2A2A" />
    ) : talking ? (
      <path d={`M-22 -4 Q0 ${4 + mouthOpen * 22} 22 -4 Q0 ${2 + mouthOpen * 8} -22 -4 Z`} fill="#6B2A2A" />
    ) : face.mouth === "grin" ? (
      <path d="M-26 -6 Q0 24 26 -6 Z" fill="#fff" stroke="#6B2A2A" strokeWidth={5} strokeLinejoin="round" />
    ) : face.mouth === "flat" ? (
      <path d="M-16 0 Q2 4 16 -2" stroke="#6B2A2A" strokeWidth={6} fill="none" strokeLinecap="round" />
    ) : (
      <path d="M-22 -4 Q0 16 22 -4" stroke="#6B2A2A" strokeWidth={6} fill="none" strokeLinecap="round" />
    );
  const brow = lerp(A.brow, B.brow, w);

  return (
    <svg width={500 * scale} height={size} viewBox="0 0 500 1180" style={{ overflow: "visible", scale: `${flip} 1` }}>
      {/* bayangan di lantai */}
      <ellipse cx={250} cy={1162} rx={150 - Math.abs(bob) * 2} ry={18} fill="#000" opacity={0.18} />
      <g transform={`translate(0 ${bob})`}>
        <Leg x={212} a={legs[0]} c={c} />
        <Leg x={288} a={legs[1]} c={c} />
        <g transform={`rotate(${lean + startle} 250 840) translate(250 840) scale(${breathe}) translate(-250 -840)`}>
          {/* badan */}
          <path d="M120 840 C116 600 150 470 250 470 C350 470 384 600 380 840 Z" fill={c.hoodie} stroke={RIM} strokeWidth={4} />
          <rect x={118} y={812} width={264} height={30} rx={14} fill={c.hoodieAccent} opacity={0.9} />
          <path d="M205 470 L250 560 L295 470 Z" fill={c.shirt} />
          <path d="M158 820 C152 620 172 530 202 495" stroke={c.hoodieAccent} strokeWidth={10} fill="none" strokeLinecap="round" />
          <path d="M342 820 C348 620 328 530 298 495" stroke={c.hoodieAccent} strokeWidth={10} fill="none" strokeLinecap="round" />
          <circle cx={250} cy={650} r={30} fill={c.hoodieAccent} />
          <text x={250} y={662} textAnchor="middle" fontFamily="Plus Jakarta Sans" fontWeight={800} fontSize={34} fill="#fff">A</text>
          <Arm x={146} y={505} a1={lA[0]} a2={lA[1]} c={c} hand={face.lh} />
          {/* leher + kepala */}
          <rect x={222} y={400} width={56} height={80} rx={24} fill={c.skinShade} />
          <g transform={`translate(0 ${headY}) rotate(${tilt} 250 330)`}>
            <ellipse cx={250} cy={300} rx={112} ry={128} fill={c.skin} />
            <ellipse cx={140 + turn * 0.6} cy={310} rx={18 - turn * 0.6} ry={26} fill={c.skinShade} />
            <ellipse cx={360 + turn * 0.4} cy={310} rx={18 + turn * 0.3} ry={26} fill={c.skinShade} />
            <path d="M132 270 C120 170 190 120 262 128 C330 132 380 170 372 262 C360 220 330 200 300 196 C320 176 300 160 270 168 C230 176 190 190 168 230 C156 246 146 262 132 270 Z" fill={c.hair} />
            <path d="M258 128 C300 92 360 110 372 160 C340 140 300 140 270 160 Z" fill={c.hair} />
            {/* fitur wajah bergeser ke arah pandang */}
            <g transform={`translate(${turn} 0)`}>
              <path d={`M190 ${252 - brow} Q212 ${242 - brow} 232 ${252 - brow}`} stroke={c.hair} strokeWidth={10} fill="none" strokeLinecap="round" />
              <path d={`M268 ${252 - brow} Q290 ${242 - brow} 310 ${252 - brow}`} stroke={c.hair} strokeWidth={10} fill="none" strokeLinecap="round" />
              <g transform="translate(0 292)">
                {eye(211)}
                {eye(289)}
              </g>
              <circle cx={211} cy={292} r={30} fill="none" stroke={c.glasses} strokeWidth={5} />
              <circle cx={289} cy={292} r={30} fill="none" stroke={c.glasses} strokeWidth={5} />
              <path d="M241 290 Q250 284 259 290" stroke={c.glasses} strokeWidth={5} fill="none" />
              <path d="M250 312 Q258 334 246 340" stroke={c.skinShade} strokeWidth={6} fill="none" strokeLinecap="round" />
              <g transform="translate(250 372)">{mouth}</g>
              <ellipse cx={184} cy={346} rx={16} ry={9} fill="#E0796B" opacity={0.35} />
              <ellipse cx={316} cy={346} rx={16} ry={9} fill="#E0796B" opacity={0.35} />
            </g>
          </g>
          {holding && <g transform={`translate(250 ${interpolate(w, [0, 1], [700, 640])})`}>{holding}</g>}
          <Arm x={354} y={505} a1={rA[0] + wave} a2={rA[1]} c={c} hand={face.rh} />
        </g>
      </g>
    </svg>
  );
};
