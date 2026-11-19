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
  // Set gambar semi-realistis (dibuat scripts/adri_aset.py, dimuat dari public/adri/<set>/peta.json bila ada)
  imageSet?: ImageSet | null;
  outfitSets?: Record<string, ImageSet>; // dimuat dari imageSets
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
  outfit?: Outfit; // kostum tambahan sesuai style video; identitas (wajah, rambut, kacamata, hoodie) tetap
  rods?: boolean; // tongkat wayang (tuding) dari tiap tangan, untuk style wayang
  closeup?: boolean; // bingkai dekat: pakai gambar close-up (bila ada set gambar)
};

// ADRI dari set gambar semi-realistis. Pose berganti dengan pudar silang; diam = napas + goyang halus;
// berjalan = 4 bingkai langkah bergantian; close-up saat bicara = mulut bergerak (5 bentuk mulut) dan berkedip.
// Gambar tidak dicerminkan (tulisan ADRIY di sweater akan terbalik).
const AdriImage: React.FC<{ set: ImageSet; cues: PoseCue[]; size: number; talking: boolean; closeup: boolean; seed: number }> = ({ set, cues, size, talking, closeup, seed }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const lib = closeup ? set.dekat : set.penuh;
  const idx = Math.max(0, cues.filter((c) => c.at * fps <= frame).length - 1);
  const cur = cues[idx];
  const prev = cues[Math.max(0, idx - 1)];
  const fade = idx === 0 ? 1 : interpolate(frame, [cur.at * fps, cur.at * fps + 6], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const pickImg = (p: Pose): PoseImage | null => {
    const list = lib[p] ?? lib.netral;
    if (!list?.length) return null;
    if (p === "jalan") return list[Math.floor((frame + seed) / 7) % list.length];
    const mouths = closeup ? set.bicara : set.bicaraPenuh ?? [];
    if (talking && p === "jelaskan" && mouths.length) {
      // sinkron bibir sederhana: bentuk mulut berganti tiap 3-4 frame, pola tetap (deterministik)
      const k = Math.floor((frame + seed) / 3.5);
      return mouths[Math.floor(Math.abs(Math.sin(k * 12.9898 + 3.1)) * 43758.5) % mouths.length];
    }
    if (closeup && set.kedip && p === "jelaskan" && (frame + seed * 31) % 110 < 4) return set.kedip;
    return list[0];
  };
  const walking = cur.pose === "jalan";
  const breathe = 1 + Math.sin((frame + seed * 17) / 24) * 0.008;
  const sway = walking ? -Math.abs(Math.sin((frame + seed) * 0.45)) * 8 : Math.sin((frame + seed * 7) / 40) * 1.2;
  const layer = (img: PoseImage | null, op: number) => {
    if (!img || op <= 0) return null;
    // tinggi sebanding dengan tinggi baris lembar asal: orang berdiri ~ size, duduk/jongkok lebih pendek
    // badan penuh: tinggi sebanding dengan tinggi baris lembar asal (berdiri ~ size, duduk/jongkok lebih pendek).
    // close-up: lebar bahu sama untuk semua gambar (potongan lembar ekspresi dan bentuk mulut berbeda-beda)
    const ww0 = closeup ? size * 0.5 : size * (img.h / (img.rowH * 0.97)) * (img.w / img.h);
    const hh = ww0 * (img.h / img.w);
    const ww = ww0;
    const bottom = closeup ? size * 0.15 : 0; // close-up: dada di tepi bawah layar
    return (
      <Img src={staticFile(img.file)} style={{ position: "absolute", left: 250 * (size / 1180) - ww / 2, top: size - bottom - hh, width: ww, height: hh, opacity: op }} />
    );
  };
  const a = pickImg(prev.pose);
  const b = pickImg(cur.pose);
  return (
    <div style={{ position: "relative", width: 500 * (size / 1180), height: size, transformOrigin: "50% 100%", scale: `${breathe}`, rotate: `${sway * 0.15}deg`, translate: `0px ${walking ? sway : 0}px` }}>
      {!closeup && <div style={{ position: "absolute", left: "50%", bottom: -size * 0.01, width: size * 0.28, height: size * 0.03, translate: "-50% 0", borderRadius: "50%", background: "rgba(0,0,0,0.18)" }} />}
      {fade < 1 && layer(a, 1 - fade)}
      {layer(b, fade)}
    </div>
  );
};

// Muat set gambar ADRIY bila ada (file tidak masuk git; tanpa file, ADRI vector yang dipakai).
// imageSets: outfit lain (travel, arkeolog, ...) yang dipilih per video lewat SceneData.outfit
export const withImageSet = async <T extends { character: CharacterSpec & { imageSetPath?: string; imageSets?: Record<string, string> } }>(brand: T | null): Promise<T | null> => {
  const path = brand?.character.imageSetPath;
  if (!brand || !path) return brand;
  const load = (p: string) => fetch(staticFile(p)).then((r) => (r.ok ? (r.json() as Promise<ImageSet>) : null)).catch(() => null);
  const set = await load(path);
  const names = Object.keys(brand.character.imageSets ?? {});
  const loaded = await Promise.all(names.map((n) => load(brand.character.imageSets![n])));
  const outfits = Object.fromEntries(names.map((n, i) => [n, loaded[i]]).filter(([, v]) => v));
  return { ...brand, character: { ...brand.character, imageSet: set, outfitSets: outfits } };
};

export type PoseImage = { file: string; w: number; h: number; rowH: number };
export type ImageSet = { penuh: Partial<Record<Pose | "netral", PoseImage[]>>; dekat: Partial<Record<Pose | "netral", PoseImage[]>>; bicara: PoseImage[]; kedip: PoseImage | null; bicaraPenuh?: PoseImage[] };

// Kostum per style. Hanya menambah aksesori di atas ADRI, tidak mengganti wajah, rambut, atau hoodie.
export type Outfit = "koki" | "pelancong" | "atlet" | "peci" | "bisnis";

// Warna ADRI bawaan (sama dengan character.colors di pipeline/styles/adriy.json), untuk style lain
export const ADRI_SPEC: CharacterSpec = {
  colors: { skin: "#C68A62", skinShade: "#A9714D", hair: "#1B1716", hoodie: "#1E2A55", hoodieAccent: "#2F6BFF", shirt: "#F5F7FB", glasses: "#1B1716" },
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

const RIM = "rgba(255,255,255,0.22)"; // garis cahaya tipis supaya siluet terbaca di latar gelap
const LINE = "#0B1230"; // garis tepi gelap: bentuk tetap jelas di latar terang

// Arah lengan: 0 derajat = lurus ke bawah.
const dir = (deg: number): [number, number] => [-Math.sin((deg * Math.PI) / 180), Math.cos((deg * Math.PI) / 180)];

// Tangan digambar menyambung dari pergelangan (titik 0,0), menjulur ke +y. Telapak menutupi pangkal jari,
// jadi jari dan ibu jari selalu menempel. `thumb` = sisi ibu jari (+1 kanan, -1 kiri).
const HandShape: React.FC<{ hand: Hand; c: CharacterSpec["colors"]; thumb: 1 | -1 }> = ({ hand, c, thumb }) => {
  const sk = { fill: c.skin, stroke: c.skinShade, strokeWidth: 3, strokeLinejoin: "round" as const };
  const fingerX = (i: number) => (-15 + i * 9.6) * thumb;
  if (hand === "fist")
    return (
      <g>
        <rect x={-19} y={-4} width={38} height={38} rx={14} {...sk} />
        {[0, 1, 2].map((i) => (
          <path key={i} d={`M${(-10 + i * 9) * thumb} 22 v10`} stroke={c.skinShade} strokeWidth={3} strokeLinecap="round" />
        ))}
        {/* ibu jari melintang di depan kepalan */}
        <rect x={-6} y={-6} width={12} height={24} rx={6} transform={`translate(${12 * thumb} 10) rotate(${55 * thumb})`} {...sk} />
      </g>
    );
  const lens = hand === "point" ? [12, 12, 12, 46] : [22, 27, 26, 21];
  return (
    <g>
      {/* jari (pangkalnya tertutup telapak) */}
      {lens.map((L, i) => {
        const x = fingerX(3 - i); // jari telunjuk paling dekat ibu jari
        const curled = hand === "point" && i < 3;
        return <rect key={i} x={x - 4.6} y={curled ? 20 : 22} width={9.2} height={curled ? 14 : L + 8} rx={4.6} {...sk} />;
      })}
      {/* ibu jari */}
      <rect x={-5} y={-4} width={11} height={27} rx={5.5} transform={`translate(${15 * thumb} 6) rotate(${-38 * thumb})`} {...sk} />
      {/* telapak */}
      <rect x={-18} y={-4} width={36} height={33} rx={13} {...sk} />
      {/* garis telapak tipis supaya terbaca sebagai tangan, bukan bulatan */}
      <path d={`M${-9 * thumb} 14 q${9 * thumb} 6 ${16 * thumb} 0`} stroke={c.skinShade} strokeWidth={2.5} fill="none" strokeLinecap="round" opacity={0.7} />
    </g>
  );
};

// Lengan satu kesatuan: bahu -> siku -> pergelangan digambar sebagai satu garis tebal dengan sambungan bulat,
// jadi tidak ada celah di siku; tangan ditempel tepat di ujung lengan dan sedikit masuk ke manset.
const Arm: React.FC<{ x: number; y: number; a1: number; a2: number; c: CharacterSpec["colors"]; hand: Hand; thumb: 1 | -1; rod?: boolean }> = ({ x, y, a1, a2, c, hand, thumb, rod }) => {
  const U = 104;
  const F = 100;
  const [d1x, d1y] = dir(a1);
  const [d2x, d2y] = dir(a1 + a2);
  const ex = x + U * d1x, ey = y + U * d1y;
  const wx = ex + F * d2x, wy = ey + F * d2y;
  const pts = `${x},${y} ${ex},${ey} ${wx},${wy}`;
  const cuff = `${wx - 18 * d2x},${wy - 18 * d2y} ${wx},${wy}`;
  return (
    <g>
      {/* tuding wayang: tongkat lurus dari tangan ke bawah */}
      {rod && <line x1={wx} y1={wy} x2={wx + 20} y2={wy + 560} stroke="#111" strokeWidth={7} strokeLinecap="round" />}
      <polyline points={pts} fill="none" stroke={RIM} strokeWidth={50} strokeLinecap="round" strokeLinejoin="round" />
      <polyline points={pts} fill="none" stroke={LINE} strokeWidth={46} strokeLinecap="round" strokeLinejoin="round" />
      <polyline points={pts} fill="none" stroke={c.hoodie} strokeWidth={38} strokeLinecap="round" strokeLinejoin="round" />
      {/* lipatan siku */}
      <path d={`M${ex - 8 * d1y} ${ey + 8 * d1x} l${16 * d1y} ${-16 * d1x}`} stroke={LINE} strokeWidth={3} opacity={0.35} strokeLinecap="round" />
      <polyline points={cuff} fill="none" stroke={c.hoodieAccent} strokeWidth={38} strokeLinecap="butt" />
      <g transform={`translate(${wx} ${wy}) rotate(${a1 + a2}) scale(1.28)`}>
        <HandShape hand={hand} c={c} thumb={thumb} />
      </g>
    </g>
  );
};

// Kaki: celana dari pinggul sampai pergelangan, sepatu menempel di ujungnya
const Leg: React.FC<{ x: number; a: number; c: CharacterSpec["colors"] }> = ({ x, a, c }) => (
  <g transform={`translate(${x} 830) rotate(${a})`}>
    <rect x={-27} y={-10} width={54} height={318} rx={24} fill={c.pants ?? "#16203F"} stroke={LINE} strokeWidth={4} />
    <path d={`M${a >= 0 ? -38 : -50} 330 Q${a >= 0 ? -36 : -48} 296 ${a >= 0 ? 4 : -8} 296 Q${a >= 0 ? 52 : 40} 296 ${a >= 0 ? 58 : 46} 330 Z`} fill={c.shoes ?? "#F5F7FB"} stroke={LINE} strokeWidth={4} strokeLinejoin="round" />
    <rect x={a >= 0 ? -40 : -52} y={326} width={100} height={12} rx={6} fill={c.hoodieAccent} stroke={LINE} strokeWidth={3} />
  </g>
);

export const Adri: React.FC<Props> = (props) =>
  props.spec.imageSet && !props.outfit && !props.rods ? (
    <AdriImage set={props.spec.imageSet} cues={typeof props.pose === "string" ? [{ at: 0, pose: props.pose }] : [...props.pose].sort((x, y) => x.at - y.at)} size={props.size} talking={!!props.talking} closeup={!!props.closeup} seed={props.seed ?? 0} />
  ) : (
    <AdriVector {...props} />
  );

const AdriVector: React.FC<Props> = ({ spec, pose, size, talking = false, facing = "right", look, holding, aim, seed = 0, outfit, rods }) => {
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
          {outfit === "pelancong" && <rect x={96} y={520} width={308} height={300} rx={60} fill="#B5835A" stroke={LINE} strokeWidth={4} />}
          {/* badan */}
          <path d="M120 840 C116 600 150 470 250 470 C350 470 384 600 380 840 Z" fill={c.hoodie} stroke={LINE} strokeWidth={4} />
          <rect x={118} y={812} width={264} height={30} rx={14} fill={c.hoodieAccent} opacity={0.9} />
          <path d="M158 820 C152 620 172 530 202 495" stroke={c.hoodieAccent} strokeWidth={10} fill="none" strokeLinecap="round" />
          <path d="M342 820 C348 620 328 530 298 495" stroke={c.hoodieAccent} strokeWidth={10} fill="none" strokeLinecap="round" />
          <circle cx={250} cy={650} r={30} fill={c.hoodieAccent} />
          <text x={250} y={662} textAnchor="middle" fontFamily="Plus Jakarta Sans" fontWeight={800} fontSize={34} fill="#fff">A</text>
          {outfit === "koki" && (
            // celemek putih dengan tali leher dan saku
            <g>
              <path d="M200 488 L214 560 M300 488 L286 560" stroke="#FFFFFF" strokeWidth={8} strokeLinecap="round" />
              <path d="M180 560 H320 Q334 700 344 836 H156 Q166 700 180 560 Z" fill="#FFFFFF" stroke={LINE} strokeWidth={4} strokeLinejoin="round" />
              <rect x={214} y={690} width={72} height={56} rx={10} fill="none" stroke="#D9480F" strokeWidth={4} />
            </g>
          )}
          {outfit === "pelancong" && (
            // tali ransel di kedua bahu
            <g>
              <path d="M178 500 Q170 650 186 790" stroke="#8A5A36" strokeWidth={22} fill="none" strokeLinecap="round" />
              <path d="M322 500 Q330 650 314 790" stroke="#8A5A36" strokeWidth={22} fill="none" strokeLinecap="round" />
              <rect x={170} y={640} width={30} height={14} rx={4} fill="#E9C46A" /><rect x={300} y={640} width={30} height={14} rx={4} fill="#E9C46A" />
            </g>
          )}
          {outfit === "bisnis" && (
            // dasi dan kartu identitas bertali
            <g>
              <path d="M240 486 L260 486 L266 512 L256 600 L250 612 L244 600 L234 512 Z" fill="#B91C1C" stroke={LINE} strokeWidth={3} strokeLinejoin="round" />
              <path d="M212 488 Q206 600 214 700 M288 488 Q296 600 286 700" stroke="#2ED47A" strokeWidth={5} fill="none" />
              <rect x={196} y={694} width={108} height={70} rx={10} fill="#FFFFFF" stroke={LINE} strokeWidth={3} />
              <rect x={196} y={694} width={108} height={20} rx={8} fill="#2F6BFF" />
              <circle cx={222} cy={738} r={12} fill="#C68A62" /><rect x={242} y={730} width={50} height={8} rx={4} fill="#9AA6BF" />
            </g>
          )}
          {outfit === "atlet" && (
            // nomor punggung di dada
            <text x={250} y={790} textAnchor="middle" fontFamily="Plus Jakarta Sans" fontWeight={800} fontSize={86} fill="#FFE600" stroke={LINE} strokeWidth={4}>10</text>
          )}
          <Arm x={146} y={505} a1={lA[0]} a2={lA[1]} c={c} hand={face.lh} thumb={-1} rod={rods} />
          {/* leher: masuk ke kerah di bawah dan tertutup rahang di atas */}
          <rect x={220} y={392} width={60} height={96} rx={24} fill={c.skin} stroke={c.skinShade} strokeWidth={3} />
          <path d="M222 420 Q250 436 278 420" stroke={c.skinShade} strokeWidth={8} fill="none" opacity={0.5} />
          <path d="M205 470 L250 556 L295 470" fill={c.shirt} stroke={LINE} strokeWidth={3} strokeLinejoin="round" />
          <g transform={`translate(0 ${headY}) rotate(${tilt} 250 330)`}>
            {/* telinga digambar dulu, lalu kepala menutupi sisi dalamnya: telinga menempel */}
            {[[146 + turn * 0.6, -1], [354 + turn * 0.4, 1]].map(([ex, sd], i) => (
              <g key={i}>
                <ellipse cx={ex} cy={312} rx={22} ry={30} fill={c.skin} stroke={c.skinShade} strokeWidth={3} />
                <path d={`M${ex + sd * 6} 296 q${sd * 8} 14 0 30`} stroke={c.skinShade} strokeWidth={4} fill="none" strokeLinecap="round" />
              </g>
            ))}
            <ellipse cx={250} cy={300} rx={112} ry={128} fill={c.skin} stroke={c.skinShade} strokeWidth={4} />
            {/* rambut: satu bentuk yang mengikuti kepala (pelipis sampai atas telinga), jambul menyatu di kanan atas */}
            <path d="M140 300 C126 222 150 150 212 126 C254 110 304 110 338 130 C372 150 384 204 366 296 L354 300 C352 258 340 228 316 212 C282 198 232 200 204 214 C178 228 162 256 156 302 Z" fill={c.hair} />
            {/* jambul menyamping ke kanan, menyatu dengan rambut (bukan sanggul di atas kepala) */}
            <path d="M196 140 C228 108 296 96 350 116 C380 128 392 152 380 182 C360 162 322 152 280 156 C246 158 218 154 196 140 Z" fill={c.hair} />
            <path d="M236 132 C270 114 318 114 350 128" stroke="#FFFFFF" strokeOpacity={0.16} strokeWidth={6} fill="none" strokeLinecap="round" />
            {outfit === "peci" && (
              // peci hitam duduk di atas rambut
              <g>
                <path d="M160 206 L170 128 Q250 104 330 128 L340 206 Q250 190 160 206 Z" fill="#141414" stroke={LINE} strokeWidth={4} strokeLinejoin="round" />
                <path d="M168 190 Q250 176 332 190" stroke="#3A3A3A" strokeWidth={4} fill="none" />
              </g>
            )}
            {outfit === "koki" && (
              // topi koki: pita bawah + kembungan atas
              <g>
                {[[190, 96, 56], [250, 72, 66], [312, 96, 56]].map(([x, y, r], i) => <circle key={i} cx={x} cy={y} r={r} fill="#FFFFFF" stroke={LINE} strokeWidth={4} />)}
                <rect x={172} y={120} width={156} height={78} rx={14} fill="#FFFFFF" stroke={LINE} strokeWidth={4} />
              </g>
            )}
            {outfit === "pelancong" && (
              // topi bucket: mahkota + pinggiran lebar
              <g>
                <ellipse cx={250} cy={196} rx={150} ry={30} fill="#C9A26B" stroke={LINE} strokeWidth={4} />
                <path d="M170 196 Q176 112 250 108 Q324 112 330 196 Z" fill="#D9B47A" stroke={LINE} strokeWidth={4} strokeLinejoin="round" />
                <path d="M172 176 Q250 162 328 176" stroke="#8A5A36" strokeWidth={10} fill="none" />
              </g>
            )}
            {outfit === "atlet" && (
              // ikat kepala melingkar di dahi
              <path d="M146 200 Q250 176 356 200 L360 226 Q250 202 142 228 Z" fill="#E11D48" stroke={LINE} strokeWidth={3} />
            )}
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
              {/* gagang kacamata menyambung ke telinga */}
              <path d={`M181 288 L${150 - turn * 0.4} 300`} stroke={c.glasses} strokeWidth={5} strokeLinecap="round" />
              <path d={`M319 288 L${352 - turn * 0.6} 300`} stroke={c.glasses} strokeWidth={5} strokeLinecap="round" />
              <path d="M250 300 Q262 330 254 342 Q246 348 238 342" fill={c.skinShade} opacity={0.35} />
              <path d="M250 304 Q262 332 252 342 Q245 346 239 341" stroke={c.skinShade} strokeWidth={5} fill="none" strokeLinecap="round" strokeLinejoin="round" />
              <g transform="translate(250 372)">{mouth}</g>
              <ellipse cx={184} cy={346} rx={16} ry={9} fill="#E0796B" opacity={0.35} />
              <ellipse cx={316} cy={346} rx={16} ry={9} fill="#E0796B" opacity={0.35} />
            </g>
          </g>
          {holding && <g transform={`translate(250 ${interpolate(w, [0, 1], [700, 640])})`}>{holding}</g>}
          <Arm x={354} y={505} a1={rA[0] + wave} a2={rA[1]} c={c} hand={face.rh} thumb={1} rod={rods} />
        </g>
      </g>
    </svg>
  );
};
