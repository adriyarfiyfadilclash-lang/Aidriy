import { createContext, useContext } from "react";
import { AbsoluteFill, Img, interpolate, staticFile } from "remotion";
import "../promo/fonts"; // Plus Jakarta Sans
import { Adri, type CharacterSpec, type Look, type Pose, type PoseCue } from "./Adri";
import { Camera, Layer, pathAt, type Shot } from "./camera";
import { canonEnv, type Env, Environment, envLook } from "./environments";
import { BarChart, barGeometry, Callout, CountUp, Cutaway, cutawayLayers, type Datum, Donut, type Event, FlowChart, type Ink, MapView, type Pin, Timeline, timelineX, Waffle } from "./infographics";
import { ap, clamp, EASE, FLOOR, H, rand, SceneLength, sp, useScene, W } from "./motion";
import { SceneFrame, type Transition } from "./transitions";
import { Kicker, KineticTitle, Marker, SceneCaption, type SubtitleMode, type SubtitleZone } from "./typography";

// Pustaka adegan Adriy.project (lihat pipeline/styles/ADRIY-STANDARD.md).
// Tiap adegan = SceneFrame (transisi masuk/keluar) > Camera (shot bertujuan) > Environment (latar topik)
// > lapisan isi + ADRI berbadan penuh yang berdiri di lantai (FLOOR). Konten tidak pernah masuk ke
// pita subtitle (CAPTION_BAND), dan tiap jenis adegan punya komposisi sendiri.

export { Marker, SceneCaption, SceneLength };

export type Brand = {
  palette: { bg: string; bg2: string; ink: string; accent: string; muted: string; primary: string; secondary: string; negative: string; paper: string };
  typography: { family: string; caption: { size: number; weight: number } };
  character: CharacterSpec;
  motion: { enter: number; exit: number; stagger: number; cameraZoom: [number, number]; parallax: { back: number; mid: number; front: number } };
  outro?: { tagline: string; slogan: string };
};

export type Token = { text: string; fromMs: number; toMs: number };

export type SceneType =
  | "karakter" | "objek" | "infografis" | "diagram" | "perbandingan" | "tipografi" | "sinematik" | "angka"
  | "grafik" | "lingkaran" | "timeline" | "peta" | "alur" | "potongan" | "persentase" | "minimalis" | "lingkungan";

export type SceneData = {
  type: SceneType;
  start: number; // detik, waktu film
  end: number;
  transition: Transition | string;
  camera?: string; // lama; kamera sekarang dipilih per jenis adegan, atau lewat `shot`
  tone: "dark" | "blue" | "light";
  env?: Env; // latar topik; tanpa ini dipilih dari tone
  shot?: Shot; // mengganti kamera bawaan adegan
  side?: "left" | "right";
  pose?: Pose;
  kicker?: string;
  title?: string;
  text?: string;
  highlight?: string;
  points?: string[]; // "ikon:label"
  steps?: string[];
  left?: { label: string; value: string; note?: string; win?: boolean };
  right?: { label: string; value: string; note?: string; win?: boolean };
  value?: number;
  unit?: string;
  label?: string;
  max?: number;
  visual?: string; // gambar di public/
  icon?: string; // ikon objek utama (dari isi seluruh kalimat), dipakai bila tidak ada gambar
  data?: Datum[]; // grafik, lingkaran
  events?: Event[]; // timeline
  pins?: Pin[]; // peta
  decision?: string; // alur: pertanyaan percabangan
  layers?: string[]; // potongan: lapisan dari luar ke dalam
  outro?: boolean;
  layout?: "full" | "presenter" | "closeup"; // objek: penuh layar / presenter + objek / close-up terpotong; karakter: closeup
  altShot?: boolean; // pakai kamera pengganti (adegan sebelumnya berkamera sama)
  subtitle?: SubtitleMode; // gaya subtitle untuk adegan ini
  adriExit?: "left" | "right"; // ADRI berjalan keluar di akhir adegan
  adri?: boolean; // false = adegan tanpa ADRI (objek/lingkungan/grafik jadi fokus)
  subtitleZone?: SubtitleZone; // posisi subtitle: bawah (pita) atau atas
  phase?: "pembuka" | "eksplorasi" | "penjelasan" | "menarik" | "kesimpulan" | "penutup"; // babak cerita
  mood?: string; // suasana adegan (dari perencana)
};

// ---------------------------------------------------------------- ikon (Material Icons, 24x24)
const GLYPHS: Record<string, string> = {
  drop: "M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.98 3.8 8.2 8 8.2s8-3.22 8-8.2c0-3.32-2.67-7.25-8-11.8z",
  suhu: "M15 13V5c0-1.66-1.34-3-3-3S9 3.34 9 5v8c-1.21.91-2 2.37-2 4 0 2.76 2.24 5 5 5s5-2.24 5-5c0-1.63-.79-3.09-2-4zm-4-8c0-.55.45-1 1-1s1 .45 1 1h-1v1h1v2h-1v1h1v2h-2V5z",
  angin: "M14.5 17c0 1.65-1.35 3-3 3s-3-1.35-3-3h2c0 .55.45 1 1 1s1-.45 1-1-.45-1-1-1H2v-2h9.5c1.65 0 3 1.35 3 3zM19 6.5C19 4.57 17.43 3 15.5 3S12 4.57 12 6.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S16.33 8 15.5 8H2v2h13.5c1.93 0 3.5-1.57 3.5-3.5zm-.5 4.5H2v2h16.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5v2c1.93 0 3.5-1.57 3.5-3.5S20.43 11 18.5 11z",
  mobil: "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z",
  ide: "M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z",
  cek: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
  grafik: "M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z",
  waktu: "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
  awas: "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z",
  bintang: "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
  matahari: "M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z",
  kereta: "M12 2c-4 0-8 .5-8 4v9.5C4 17.43 5.57 19 7.5 19L6 20.5v.5h2.23l2-2H14l2 2h2v-.5L16.5 19c1.93 0 3.5-1.57 3.5-3.5V6c0-3.5-3.58-4-8-4zM7.5 17c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm3.5-7H6V6h5v4zm2 0V6h5v4h-5zm3.5 7c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
  makan: "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z",
};
export const Glyph: React.FC<{ name: string; size: number; color: string }> = ({ name, size, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24">
    <path d={GLYPHS[name] ?? GLYPHS.bintang} fill={color} />
  </svg>
);
const splitPoint = (p: string) => (p.includes(":") ? { icon: p.split(":")[0], label: p.slice(p.indexOf(":") + 1) } : { icon: "cek", label: p });

// Gaya subtitle bawaan per jenis adegan (naskah bisa mengganti lewat `subtitle`)
export const SUBTITLE_DEFAULT: Record<SceneType, SubtitleMode> = {
  karakter: "kinetik", objek: "standar", infografis: "standar", diagram: "minimalis", perbandingan: "standar", tipografi: "minimalis",
  sinematik: "tanpa", angka: "kata", grafik: "minimalis", lingkaran: "minimalis", timeline: "minimalis", peta: "minimalis",
  alur: "minimalis", potongan: "standar", persentase: "kata", minimalis: "minimalis", lingkungan: "standar",
};
// Subtitle di atas bila komposisinya menaruh judul/karakter di bawah (wide shot, close-up karakter)
export const subtitleZoneOf = (s: SceneData): SubtitleZone =>
  s.subtitleZone ?? (s.type === "lingkungan" || (s.type === "karakter" && s.layout === "closeup") ? "atas" : "bawah");
export const subtitleOf = (s: SceneData): SubtitleMode => s.subtitle ?? (s.type === "objek" && s.layout === "full" ? "tanpa" : SUBTITLE_DEFAULT[s.type]);

// ---------------------------------------------------------------- dasar adegan
type SP = { s: SceneData; brand: Brand; caption: React.ReactNode };

// latar bawaan per jenis (dipakai juga oleh subtitle, supaya warna teks mengikuti latar sebenarnya)
const TYPE_ENV: Partial<Record<SceneType, Env>> = { timeline: "arsip", potongan: "sains", minimalis: "minimal", perbandingan: "banding" };
export const envOf = (s: SceneData): Env => s.env ?? TYPE_ENV[s.type] ?? ({ dark: "malam", blue: "brand", light: "cerah" } as const)[s.tone];
export const isLight = (s: SceneData, b: Brand) => envLook(envOf(s), b).light;
const inkOf = (s: SceneData, b: Brand): Ink => {
  const lk = envLook(envOf(s), b);
  return { ink: lk.ink, accent: lk.accent, primary: b.palette.primary, secondary: b.palette.secondary, negative: b.palette.negative, bg: lk.light ? "#FFFFFF" : b.palette.bg, font: b.typography.family };
};

// Stage: transisi + kamera + latar + isi. `hud` tidak ikut kamera (judul pada adegan yang kameranya berpindah jauh).
// Lima lapisan:
//   1 latar (gradasi + tekstur, diam)          2 dekorasi/lingkungan (kedalaman 0.3)
//   3 objek utama (1.0)                        4 karakter (1.06)
//   5 teks, subtitle, penekanan (tidak ikut kamera)
// `alt` = kamera pengganti bila adegan sebelumnya memakai jenis kamera yang sama (diisi perencana: altShot).
const Stage: React.FC<{ s: SceneData; brand: Brand; shot: Shot; alt?: Shot; focus?: [number, number]; caption: React.ReactNode; children: React.ReactNode; front?: React.ReactNode; hud?: React.ReactNode; floor?: boolean }> = ({ s, brand, shot, alt, focus, caption, children, front, hud, floor }) => (
  <SceneFrame enter={s.transition} focus={focus} accent={envLook(envOf(s), brand).accent} tint={envLook(envOf(s), brand).sky[1]} seed={Math.round(s.start * 7)}>
    <AdriVisible.Provider value={s.adri !== false}>
    <Camera shot={s.shot ?? (s.altShot && alt ? alt : shot)}>
      <Environment env={envOf(s)} brand={brand} floor={floor} />
      <Layer depth={1}>{children}</Layer>
      {front && <Layer depth={1.06}>{front}</Layer>}
    </Camera>
    {hud && <AbsoluteFill style={{ fontFamily: brand.typography.family }}>{hud}</AbsoluteFill>}
    </AdriVisible.Provider>
    {caption}
  </SceneFrame>
);

// ADRI berbadan penuh, kaki di FLOOR. Ukuran mengikuti fungsi adegan (bukan angka bebas), jadi ADRI
// tidak berubah ukuran sembarangan antaradegan:
//   kecil  = pendamping infografis/diagram/peta/linimasa
//   sedang = presenter yang menjelaskan objek/angka/grafik
//   besar  = adegan utama: pembuka, ekspresi, penekanan, penutup
//   jauh   = sosok kecil di wide shot lingkungan atau sinematik (jarak kamera, bukan ukuran berbeda)
export const ADRI_SIZE = { jauh: 320, kecil: 500, sedang: 800, besar: 1150 } as const;
// Adegan bisa tanpa ADRI (objek, lingkungan, atau grafik jadi fokus): Stage mengisi context ini dari s.adri.
const AdriVisible = createContext(true);
export type AdriRole = keyof typeof ADRI_SIZE;
const ADRI_W = 500 / 1180;
const Presenter: React.FC<{
  brand: Brand; x: number; role: AdriRole; pose: Pose | PoseCue[]; facing: "left" | "right"; look?: Look; holding?: React.ReactNode;
  walkFrom?: number; walkLen?: number; at?: number; seed: number; talking?: boolean; floor?: number;
  pointAt?: [number, number] | null; // titik di layar yang ditunjuk (koordinat lapisan isi)
  exitTo?: "left" | "right"; // berjalan keluar di akhir adegan
  moveTo?: { at: number; x: number; len?: number }; // berpindah posisi di tengah adegan (berjalan)
  closeup?: boolean; // bingkai dekat: ADRI besar dari pinggang ke atas (kaki di bawah layar, sengaja)
}> = ({ brand, x, role, pose, facing, look, holding, walkFrom, walkLen = 0.9, at = 0, seed, talking = true, floor: floor0 = FLOOR, pointAt, exitTo, moveTo, closeup }) => {
  const { frame, fps, len } = useScene();
  const visible = useContext(AdriVisible);
  const size = closeup ? 1900 : ADRI_SIZE[role];
  const floor = closeup ? floor0 + 760 : floor0;
  if (!visible) return null;
  const cues: PoseCue[] = typeof pose === "string" ? [{ at: 0, pose }] : pose;
  const top = floor - size * (1162 / 1180);
  const scale = size / 1180;
  let cx = x;
  let allCues = cues;
  let face = facing;
  let talk = talking;
  let opacity = 1;
  let rise = 0;
  if (walkFrom !== undefined) {
    // masuk dengan berjalan dari samping, lalu berpose
    const k = interpolate(frame, [at * fps, (at + walkLen) * fps], [0, 1], { ...clamp, easing: EASE.soft });
    cx = walkFrom + (x - walkFrom) * k;
    allCues = [{ at: 0, pose: "jalan" }, ...cues.map((c) => ({ ...c, at: Math.max(c.at, at + walkLen) }))];
    talk = talking && frame > (at + walkLen) * fps;
  } else {
    // muncul dengan spring (naik sedikit dari lantai)
    const p = sp(frame, fps, at, { damping: 15 });
    opacity = Math.min(1, p * 1.5);
    rise = (1 - Math.min(p, 1)) * 60;
  }
  if (moveTo) {
    // berpindah: berjalan ke x baru (menghadap arah jalan), lalu melanjutkan pose
    const L = moveTo.len ?? 0.9;
    const end = moveTo.at + L;
    const k = interpolate(frame / fps, [moveTo.at, end], [0, 1], { ...clamp, easing: EASE.inOut });
    if (k > 0) {
      cx += (moveTo.x - x) * k;
      const before = allCues.filter((c) => c.at < moveTo.at);
      const after = allCues.filter((c) => c.at >= end);
      const resume: PoseCue[] = after[0]?.at === end ? [] : [{ at: end, pose: before[before.length - 1]?.pose ?? "jelaskan" }];
      allCues = [...before, { at: moveTo.at, pose: "jalan" }, ...resume, ...after];
      if (k < 1) {
        face = moveTo.x > x ? "right" : "left";
        talk = false;
      }
    }
  }
  if (exitTo) {
    // keluar: 0,9 dtk terakhir berbalik dan berjalan ke luar layar
    const t0 = len / fps - 0.9;
    const k = interpolate(frame / fps, [t0, t0 + 0.9], [0, 1], { ...clamp, easing: EASE.inOut });
    if (k > 0) {
      cx += (exitTo === "right" ? 1 : -1) * k * 700;
      allCues = [...allCues.filter((c) => c.at < t0), { at: t0, pose: "jalan" }];
      face = exitTo;
      talk = false;
    }
  }
  // titik layar -> koordinat lokal ADRI (memperhitungkan cermin saat menghadap kiri)
  const left = cx - (size * ADRI_W) / 2;
  let aim: [number, number] | null = null;
  if (pointAt) {
    const lx = (pointAt[0] - left) / scale;
    const ly = (pointAt[1] - top) / scale;
    aim = [face === "left" ? 500 - lx : lx, ly];
  }
  return (
    <div data-qa="adri" {...(closeup ? { "data-qa-wide": "1" } : {})} style={{ position: "absolute", left, top, width: size * ADRI_W, height: size, opacity, translate: `0px ${rise}px` }}>
      <Adri spec={brand.character} pose={allCues} size={size} talking={talk} facing={face} look={look} holding={holding} aim={aim} seed={seed} closeup={closeup} />
    </div>
  );
};

// Bentuk bukan-kartu untuk ikon: heksagon, belah ketupat, tetes, segitiga membulat (berganti-ganti).
const SHAPES = [
  "M50 2 L93 26 L93 74 L50 98 L7 74 L7 26 Z",
  "M50 0 L100 50 L50 100 L0 50 Z",
  "M50 0 C80 30 96 52 96 66 A46 34 0 0 1 4 66 C4 52 20 30 50 0 Z",
  "M50 6 Q58 6 62 14 L96 80 Q100 92 88 94 L12 94 Q0 92 4 80 L38 14 Q42 6 50 6 Z",
];
const ShapeBadge: React.FC<{ i: number; size: number; color: string; icon: string; rot?: number }> = ({ i, size, color, icon, rot = 0 }) => (
  <div style={{ position: "relative", width: size, height: size, rotate: `${rot}deg` }}>
    <svg width={size} height={size} viewBox="0 0 100 100" style={{ position: "absolute", inset: 0, overflow: "visible", filter: "drop-shadow(0 14px 22px rgba(0,0,0,0.28))" }}>
      <path d={SHAPES[i % SHAPES.length]} fill={color} />
    </svg>
    <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", rotate: `${-rot}deg`, paddingTop: i % 4 >= 2 ? size * 0.16 : 0 }}>
      <Glyph name={icon} size={size * 0.5} color="#FFFFFF" />
    </div>
  </div>
);

const Visual: React.FC<{ s: SceneData; k: Ink; size: number }> = ({ s, k, size }) =>
  s.visual ? (
    <Img src={staticFile(s.visual)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
  ) : (
    <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", background: `linear-gradient(135deg, ${k.primary}, ${k.secondary})` }}>
      <Glyph name={s.icon ?? splitPoint(s.points?.[0] ?? "ide:").icon} size={size} color="#FFFFFF" />
    </div>
  );

const seedOf = (s: SceneData, n = 0) => Math.round(s.start * 10) + n;

// Judul tidak selalu di atas: pos "bawah" = di atas lantai (wide shot), "kiri" = tengah kiri (close-up objek)
const Head: React.FC<{ s: SceneData; brand: Brand; k: Ink; size?: number; top?: number; maxWidth?: number; pos?: "atas" | "bawah" | "kiri" }> = ({ s, brand, k, size = 64, top = 150, maxWidth, pos = "atas" }) => (
  <div style={{ position: "absolute", left: 80, right: pos === "kiri" ? 560 : 80, top: pos === "bawah" ? undefined : pos === "kiri" ? 560 : top, bottom: pos === "bawah" ? H - FLOOR + 40 : undefined, fontFamily: brand.typography.family }}>
    <Kicker text={s.kicker} at={0.1} color={k.accent} />
    <div style={{ height: 16 }} />
    <KineticTitle text={s.title} highlight={s.highlight} at={0.2} brand={brand} size={size} color={k.ink} maxWidth={maxWidth} />
  </div>
);

// ================================================================ 1. Karakter
// ADRI berjalan masuk dan berbicara langsung ke penonton; judul besar di sisi berlawanan; kamera menekankan karakter.
export const SceneKarakter: React.FC<SP> = (props) => (props.s.layout === "closeup" ? <SceneKarakterDekat {...props} /> : <SceneKarakterPresenter {...props} />);
const SceneKarakterPresenter: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const side = s.side ?? "right";
  const ax = side === "right" ? 770 : 310;
  const pose = s.pose ?? "jelaskan";
  const cues: PoseCue[] = [{ at: 0, pose }, ...(pose === "sapa" ? [{ at: 2.6, pose: "ajak" as Pose }] : pose === "jelaskan" ? [{ at: 2.8, pose: "angkat" as Pose }] : [])];
  const textX = side === "right" ? 80 : 560;
  const pts = (s.points ?? []).slice(0, 1); // satu ikon pendamping saja: fokus tetap pada ADRI dan judul
  return (
    <Stage s={s} brand={brand} shot={{ kind: "emphasis", x: ax, y: 520 }} alt={{ kind: "reveal", x: ax, y: 520, from: 1.35 }} focus={[ax, 600]} caption={caption}
      hud={<>
      <div style={{ position: "absolute", left: textX, top: 260, width: 440, fontFamily: brand.typography.family }}>
        <Kicker text={s.kicker} at={0.35} color={k.accent} />
        <div style={{ height: 22 }} />
        <KineticTitle text={s.title} highlight={s.highlight} at={0.5} brand={brand} size={s.title && s.title.length > 40 ? 70 : 84} color={k.ink} />
        {s.outro && (
          <div style={{ marginTop: 40, fontWeight: 800, fontSize: 40, letterSpacing: 6, color: k.ink, clipPath: `inset(0 ${(1 - ap(frame, fps, 1.4, 0.6)) * 100}% 0 0)` }}>
            ADRIY<span style={{ color: k.accent }}>.</span>PROJECT
          </div>
        )}
      </div>
      </>}
      front={<Presenter brand={brand} x={ax} role="besar" pose={cues} facing={side === "right" ? "left" : "right"} look="penonton" walkFrom={side === "right" ? 1350 : -270} walkLen={0.9} seed={seedOf(s)} exitTo={s.adriExit} />}
    >
      {/* pita diagonal di belakang judul */}
      <div style={{ position: "absolute", left: side === "right" ? -120 : 480, top: 520, width: 760, height: 120, background: k.accent, opacity: 0.12, rotate: "-8deg", transformOrigin: "left", scale: `${ap(frame, fps, 0.3, 0.7)} 1` }} />
      {/* ikon melayang, bukan kartu */}
      {pts.map((pt, i) => {
        const p = sp(frame, fps, 1.1 + i * 0.25, { damping: 12 });
        const x = side === "right" ? 110 + i * 220 : 640 + i * 200;
        return (
          <div key={i} style={{ position: "absolute", left: x, top: 1000 + i * 130 + Math.sin(frame / 22 + i) * 12, scale: `${Math.min(1, p)}` }}>
            <ShapeBadge i={i + seedOf(s)} size={150} color={i ? k.secondary : k.primary} icon={splitPoint(pt).icon} rot={Math.sin(frame / 30 + i) * 6} />
          </div>
        );
      })}
    </Stage>
  );
};

// ================================================================ 2. Objek
// Objek besar dengan label penunjuk ke bagian-bagiannya; ADRI menyentuh objek; kamera mendekat ke objek.
export const SceneObjek: React.FC<SP> = (props) =>
  props.s.layout === "full" ? <SceneObjekFull {...props} /> : props.s.layout === "closeup" ? <SceneObjekDekat {...props} /> : <SceneObjekPresenter {...props} />;
const SceneObjekPresenter: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const pop = sp(frame, fps, 0.15, { damping: 13 });
  const pts = (s.points ?? []).slice(0, 3);
  const O = { x: 650, y: 720, r: 250 };
  const spots: [number, number][] = [[560, 600], [800, 700], [640, 880]];
  return (
    <Stage s={s} brand={brand} shot={{ kind: "focus", x: O.x, y: O.y, at: 1.6, zoom: 1.08 }} alt={{ kind: "reveal", x: O.x, y: O.y }} focus={[O.x, O.y]} caption={caption} hud={<Head s={s} brand={brand} k={k} size={66} />}
      front={<Presenter brand={brand} x={200} role="sedang" pose={[{ at: 0, pose: "lihat" }, { at: 1.1, pose: "sentuh" }]} facing="right" look="atas" walkFrom={-260} walkLen={0.8} at={0.2} pointAt={[O.x - O.r * 0.72, O.y + 30]} seed={seedOf(s, 1)} />}
    >
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <ellipse cx={O.x} cy={O.y} rx={O.r + 70} ry={O.r * 0.5} fill="none" stroke={k.accent} strokeWidth={4} strokeDasharray="10 16" transform={`rotate(${-18 + frame * 0.2} ${O.x} ${O.y})`} opacity={0.5 * Math.min(1, pop)} />
      </svg>
      <div data-qa="object" style={{ position: "absolute", left: O.x - O.r, top: O.y - O.r, width: O.r * 2, height: O.r * 2, borderRadius: "44% 56% 52% 48% / 48% 44% 56% 52%", overflow: "hidden", scale: `${0.6 + 0.4 * Math.min(pop, 1.05)}`, rotate: `${Math.sin(frame / 40) * 2}deg`, translate: `0px ${Math.sin(frame / 30) * 10}px`, boxShadow: "0 40px 80px rgba(0,0,0,0.35)" }}>
        <Visual s={s} k={k} size={260} />
      </div>
      {pts.map((pt, i) => (
        <Callout key={i} from={spots[i]} to={[460, 1110 + i * 100]} label={splitPoint(pt).label || splitPoint(pt).icon} at={1.0 + i * 0.4} k={k} width={540} align="right" />
      ))}
    </Stage>
  );
};

// ================================================================ 3. Infografis
// Poin-poin naik di sepanjang jalur melengkung; ADRI melihat ke atas mengikuti; kamera naik.
export const SceneInfografis: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const pts = (s.points ?? []).slice(0, 4);
  const n = Math.max(pts.length, 1);
  const nodes = pts.map((_, i) => ({ x: 470 + Math.sin(i * 1.3) * 50, y: 1260 - i * (700 / Math.max(n - 1, 1)) }));
  const cols = [k.primary, k.secondary, k.accent, k.negative];
  const d = nodes.map((p, i) => (i ? `S${p.x - 120} ${p.y + 110} ${p.x} ${p.y}` : `M${p.x} ${p.y}`)).join(" ");
  const draw = ap(frame, fps, 0.6, 0.4 + n * 0.35, EASE.inOut);
  return (
    <Stage s={s} brand={brand} shot={{ kind: "track", from: [540, 1100], to: [540, 760], zoom: 1.05 }} alt={{ kind: "panV", distance: 160 }} focus={[200, 1100]} caption={caption} hud={<Head s={s} brand={brand} k={k} size={68} />}
      front={
        // ADRI menunjuk tiap poin tepat saat poin itu muncul, lalu kembali menjelaskan
        <Presenter brand={brand} x={180} role="sedang" facing="right" look="atas" at={0.3} seed={seedOf(s, 3)}
          pose={[{ at: 0, pose: "lihat" }, ...pts.map((_, i) => ({ at: 0.7 + i * 0.45, pose: "tunjuk" as Pose })), { at: 0.9 + n * 0.45, pose: "jelaskan" }]}
          pointAt={nodes.length ? [nodes[Math.max(0, Math.min(n - 1, Math.floor((frame / fps - 0.7) / 0.45)))].x - 60, nodes[Math.max(0, Math.min(n - 1, Math.floor((frame / fps - 0.7) / 0.45)))].y] : null} />
      }
    >
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <path d={d} fill="none" stroke={k.ink} strokeOpacity={0.35} strokeWidth={6} strokeLinecap="round" pathLength={1} strokeDasharray={`${draw} 1`} />
      </svg>
      {pts.map((pt, i) => {
        const p = sp(frame, fps, 0.7 + i * 0.35, { damping: 12 });
        const { icon, label } = splitPoint(pt);
        const t = ap(frame, fps, 0.85 + i * 0.35, 0.45);
        return (
          <div key={i}>
            <div style={{ position: "absolute", left: nodes[i].x - 75, top: nodes[i].y - 75, scale: `${Math.min(1, p)}` }}>
              <ShapeBadge i={i} size={150} color={cols[i % cols.length]} icon={icon} rot={Math.sin(frame / 28 + i) * 5} />
            </div>
            <div data-qa="text" style={{ position: "absolute", left: nodes[i].x + 105, top: nodes[i].y - 50, width: 1000 - nodes[i].x - 105, fontFamily: brand.typography.family, fontWeight: 800, fontSize: 46, lineHeight: 1.12, color: k.ink, clipPath: `inset(0 ${(1 - t) * 100}% 0 0)` }}>
              {label}
            </div>
          </div>
        );
      })}
    </Stage>
  );
};

// ================================================================ 4. Diagram proses (horizontal)
// Langkah-langkah berderet di panggung lebar; kamera bergeser ke tiap langkah; ADRI berjalan mengikutinya.
export const SceneDiagram: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps, sec } = useScene();
  const k = inkOf(s, brand);
  const steps = (s.steps ?? []).slice(0, 5);
  const gap = 560;
  const X = (i: number) => 600 + i * gap;
  const Y = 900;
  const t0 = 0.9;
  const per = Math.max(1.0, (sec - t0 - 0.8) / Math.max(steps.length, 1));
  const keys = steps.map((_, i) => ({ t: i === 0 ? 0 : t0 + i * per, x: X(i), y: 900, zoom: 1 }));
  const cam = pathAt(keys, frame / fps);
  const camPrev = pathAt(keys, (frame - 2) / fps);
  const active = Math.max(0, keys.filter((kk) => frame / fps >= kk.t).length - 1);
  const moving = Math.abs(cam.x - camPrev.x) > 1;
  const span = Math.max(1, steps.length - 1) * gap;
  return (
    <Stage s={s} brand={brand} shot={{ kind: "path", keys }} focus={[X(0), Y]} caption={caption}
      hud={
        <div style={{ position: "absolute", left: 80, right: 80, top: 150 }}>
          <Kicker text={s.kicker} at={0.1} color={k.accent} />
          <div style={{ height: 16 }} />
          <KineticTitle text={s.title} highlight={s.highlight} at={0.2} brand={brand} size={66} color={k.ink} />
          <div style={{ display: "flex", gap: 12, marginTop: 28 }}>
            {steps.map((_, i) => (
              <div key={i} style={{ height: 10, width: i === active ? 90 : 36, borderRadius: 5, background: i <= active ? k.accent : `${k.ink}33` }} />
            ))}
          </div>
        </div>
      }
    >
      <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
        <line x1={X(0) - 300} x2={X(steps.length - 1) + 300} y1={Y} y2={Y} stroke={k.ink} strokeOpacity={0.18} strokeWidth={26} strokeLinecap="round" />
        <line x1={X(0)} x2={cam.x} y1={Y} y2={Y} stroke={k.accent} strokeWidth={12} strokeLinecap="round" />
        {Array.from({ length: 30 }, (_, i) => {
          const x = X(0) - 280 + ((i * 90 + frame * 6) % (span + 560));
          return x < cam.x ? <circle key={i} cx={x} cy={Y} r={5} fill={k.bg} /> : null;
        })}
      </svg>
      {steps.map((st, i) => {
        const p = sp(frame, fps, i === 0 ? 0.4 : keys[i].t - 0.5, { damping: 13 });
        const on = i === active;
        return (
          <div key={i} style={{ position: "absolute", left: X(i) - 220, top: Y - 360, width: 440, height: 320, fontFamily: brand.typography.family, textAlign: "center", scale: `${0.8 + 0.2 * Math.min(p, 1)}`, opacity: Math.min(1, p * 1.4) * (on ? 1 : 0.55) }}>
            <div style={{ fontWeight: 800, fontSize: 150, lineHeight: 1, letterSpacing: -6, color: on ? k.accent : "transparent", WebkitTextStroke: on ? undefined : `4px ${k.ink}` }}>{i + 1}</div>
            <div style={{ fontWeight: 800, fontSize: 50, lineHeight: 1.1, color: k.ink, marginTop: 14 }}>{st}</div>
            <div style={{ position: "absolute", left: 210, top: 320, width: 20, height: 40, borderRadius: 10, background: on ? k.accent : `${k.ink}55` }} />
          </div>
        );
      })}
      {/* ADRI berjalan di lantai, selalu sedikit di kiri tengah layar */}
      <Presenter brand={brand} x={cam.x - 250} role="kecil" pose={moving ? "jalan" : active % 2 ? "sentuh" : "tunjuk"} facing="right" look="atas" seed={seedOf(s, 5)} at={0.3} pointAt={moving ? null : [X(active) - 60, Y - 250]} />
    </Stage>
  );
};

// ================================================================ 5. Perbandingan
// Layar terbelah diagonal; dua sisi berbeda warna dan tinggi; ADRI di tengah berpikir lalu menunjuk pemenang.
export const ScenePerbandingan: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const winRight = !!s.right?.win;
  const split = ap(frame, fps, 0.1, 0.7, EASE.inOut);
  const vs = sp(frame, fps, 0.9, { damping: 9, stiffness: 160 });
  const side = (d: SceneData["left"], i: number) => {
    if (!d) return null;
    const p = ap(frame, fps, 0.4 + i * 0.3, 0.6);
    const c = d.win ? k.secondary : k.negative;
    const left = i === 0;
    return (
      <div data-qa="text" style={{ position: "absolute", left: left ? 80 : 620, top: left ? 430 : 600, width: 380, textAlign: left ? "left" : "right", fontFamily: brand.typography.family, opacity: p, translate: `${(1 - p) * (left ? -80 : 80)}px 0px` }}>
        <div style={{ fontWeight: 800, fontSize: 40, color: k.ink, opacity: 0.85 }}>{d.label}</div>
        <div style={{ fontWeight: 800, fontSize: d.value.length > 5 ? 100 : 132, letterSpacing: -4, color: c, margin: "14px 0", whiteSpace: "nowrap" }}>{d.value}</div>
        {d.note && <div style={{ fontWeight: 700, fontSize: 36, lineHeight: 1.2, color: k.ink, opacity: 0.85 }}>{d.note}</div>}
        {d.win && (
          <div style={{ marginTop: 22, display: "inline-flex", alignItems: "center", gap: 10, padding: "8px 20px", borderRadius: 30, background: c, color: "#fff", fontWeight: 800, fontSize: 30, scale: `${Math.min(1, sp(frame, fps, 1.8, { damping: 10 }))}` }}>
            <Glyph name="cek" size={34} color="#fff" /> LEBIH BAIK
          </div>
        )}
      </div>
    );
  };
  return (
    <Stage s={s} brand={brand} shot={{ kind: "perspective", from: winRight ? "right" : "left" }} alt={{ kind: "rack", from: winRight ? "left" : "right" }} focus={[540, 900]} caption={caption} hud={<Head s={s} brand={brand} k={k} maxWidth={760} />}
      front={<Presenter brand={brand} x={540} role="kecil" pose={[{ at: 0, pose: "banding" }, { at: 2.4, pose: "tunjuk" }]} facing={winRight ? "right" : "left"} look="penonton" at={0.5} seed={seedOf(s, 6)} moveTo={s.left?.win || s.right?.win ? { at: 1.5, x: winRight ? 640 : 440 } : undefined} pointAt={winRight ? [800, 760] : [280, 600]} />}
    >
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <polygon points={`0,0 ${620 * split},0 ${460 * split},${FLOOR} 0,${FLOOR}`} fill={s.left?.win ? k.secondary : k.negative} opacity={0.14} />
        <polygon points={`${W},0 ${W - (W - 620) * split},0 ${W - (W - 460) * split},${FLOOR} ${W},${FLOOR}`} fill={winRight ? k.secondary : k.primary} opacity={0.16} />
        <line x1={620} y1={0} x2={460} y2={FLOOR} stroke={k.accent} strokeWidth={6} pathLength={1} strokeDasharray={1} strokeDashoffset={1 - split} />
      </svg>
      {side(s.left, 0)}
      {side(s.right, 1)}
      {/* lencana VS berbentuk belah ketupat di garis belah */}
      <div style={{ position: "absolute", left: 540 - 72, top: 770, width: 144, height: 144, rotate: `${45 + Math.sin(frame / 20) * 3}deg`, background: k.accent, borderRadius: 26, scale: `${interpolate(Math.min(vs, 1.1), [0, 1], [2, 1])}`, opacity: Math.min(1, vs * 2), boxShadow: "0 16px 40px rgba(0,0,0,0.35)", display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div style={{ rotate: "-45deg", fontFamily: brand.typography.family, fontWeight: 800, fontSize: 52, color: k.bg }}>VS</div>
      </div>
    </Stage>
  );
};

// ================================================================ 6. Tipografi kinetik
export const SceneTipografi: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const text = s.text ?? s.title ?? "";
  const n = text.split(" ").length;
  const bar = ap(frame, fps, 0.1, 0.6);
  return (
    <Stage s={s} brand={brand} shot={{ kind: "drift" }} alt={{ kind: "panV", distance: 120 }} focus={[540, 800]} caption={caption} floor={false}>
      <div style={{ position: "absolute", left: 60, top: 200, fontFamily: "Georgia, serif", fontSize: 420, lineHeight: 1, color: k.accent, opacity: 0.22 * bar, translate: `${(1 - bar) * -60}px 0px` }}>“</div>
      <div style={{ position: "absolute", left: -40, top: 1250, width: 700 * bar, height: 26, background: k.accent, rotate: "-4deg", borderRadius: 13 }} />
      <div style={{ position: "absolute", left: 90, right: 90, top: 300, bottom: 560, display: "flex", alignItems: "center", fontFamily: brand.typography.family }}>
        <KineticTitle text={text} highlight={s.highlight} at={0.15} brand={brand} size={n > 7 ? 100 : 120} color={k.ink} />
      </div>
    </Stage>
  );
};

// ================================================================ 7. Sinematik
// Lanskap berlapis dengan parallax kuat; ADRI kecil berjalan melintas, kamera mengikutinya.
export const SceneSinematik: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps, sec } = useScene();
  const k = inkOf(s, brand);
  const bars = ap(frame, fps, 0, 0.6);
  const ax = interpolate(frame / fps, [0, sec], [260, 760]);
  return (
    <SceneFrame enter={s.transition} focus={[ax, 1300]} accent={k.accent} seed={seedOf(s)}>
      <Camera shot={s.shot ?? { kind: "track", from: [260, 1200], to: [760, 1200], zoom: 1.12 }}>
        {s.visual ? (
          <Layer depth={0.4}>
            <Img src={staticFile(s.visual)} style={{ width: "100%", height: "100%", objectFit: "cover", scale: "1.15" }} />
          </Layer>
        ) : (
          <>
            <AbsoluteFill>
              <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
                <defs>
                  <linearGradient id="langit" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor="#2F6BFF" />
                    <stop offset="0.5" stopColor="#FFB547" />
                    <stop offset="1" stopColor="#FF7E6B" />
                  </linearGradient>
                </defs>
                <rect width={W} height={H} fill="url(#langit)" />
                <circle cx={680} cy={900} r={160} fill="#FFE3A3" opacity={0.95} />
              </svg>
            </AbsoluteFill>
            <Layer depth={0.3}>
              <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
                <path d="M-200 1150 L120 900 L380 1060 L640 820 L900 1020 L1280 860 V1920 H-200 Z" fill="#5B3A7A" opacity={0.85} />
              </svg>
            </Layer>
            <Layer depth={0.65}>
              <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
                <path d="M-300 1330 C0 1200 300 1260 540 1320 C780 1380 1000 1250 1380 1300 V1920 H-300 Z" fill="#2E2152" />
                {Array.from({ length: 9 }, (_, i) => (
                  <path key={i} d={`M${-200 + i * 180} ${1300 - rand(i) * 40} l30 -120 l30 120 Z`} fill="#1E1640" />
                ))}
              </svg>
            </Layer>
          </>
        )}
        <Layer depth={1}>
          <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
            <path d="M-400 1440 C0 1380 600 1400 1500 1440 V1920 H-400 Z" fill={s.visual ? "rgba(0,0,0,0.5)" : "#150F2E"} />
          </svg>
          <Presenter brand={brand} x={ax} role="kecil" pose="jalan" facing="right" look="maju" seed={seedOf(s, 7)} talking={false} />
        </Layer>
      </Camera>
      <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 45%, transparent 45%, rgba(0,0,0,0.6) 100%)" }} />
      {/* judul di langit, jauh dari pita subtitle */}
      <div style={{ position: "absolute", left: 80, right: 80, top: 300, fontFamily: brand.typography.family }}>
        <Kicker text={s.kicker} at={0.6} color={brand.palette.accent} />
        <div style={{ height: 16 }} />
        <KineticTitle text={s.title} highlight={s.highlight} at={0.75} brand={brand} size={88} color="#FFFFFF" />
      </div>
      <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 150 * bars, background: "#000" }} />
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 150 * bars, background: "#000" }} />
      {caption}
    </SceneFrame>
  );
};

// ================================================================ 8. Angka
// Angka raksasa menghitung naik dengan sinar di belakangnya; pengukur horizontal; ADRI terkejut.
export const SceneAngka: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const v = s.value ?? 0;
  const max = s.max ?? 100;
  const g = ap(frame, fps, 0.4, 1.3) * Math.min(1, v / max);
  const burst = ap(frame, fps, 1.5, 0.6);
  return (
    <Stage s={s} brand={brand} shot={{ kind: "focus", x: 330, y: 420, at: 0.3, zoom: 1.05 }} alt={{ kind: "reveal", x: 330, y: 420, from: 1.2 }} focus={[330, 400]} caption={caption}
      hud={<div style={{ position: "absolute", left: 80, top: 150 }}><Kicker text={s.kicker} at={0.1} color={k.accent} /></div>}
      front={<Presenter brand={brand} x={840} role="sedang" pose={[{ at: 0, pose: "lihat" }, { at: 1.6, pose: s.pose ?? "kaget" }]} facing="left" look="maju" at={0.2} seed={seedOf(s, 8)} talking={false} />}
    >
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        {Array.from({ length: 14 }, (_, i) => {
          const a = (i / 14) * Math.PI * 2 + frame / 200;
          return <line key={i} x1={330 + Math.cos(a) * 170} y1={390 + Math.sin(a) * 170} x2={330 + Math.cos(a) * (170 + 220 * burst)} y2={390 + Math.sin(a) * (170 + 220 * burst)} stroke={k.accent} strokeWidth={10} strokeLinecap="round" opacity={0.35 * (1 - burst * 0.4)} />;
        })}
      </svg>
      <div style={{ position: "absolute", left: 70, top: 320 }}>
        <CountUp value={v} at={0.4} unit={s.unit} size={v >= 1000 || (s.unit ?? "").length > 3 ? 190 : 280} color={k.ink} unitColor={k.accent} font={brand.typography.family} />
      </div>
      <div style={{ position: "absolute", left: 80, top: 620, width: 500, fontFamily: brand.typography.family }}>
        <KineticTitle text={s.label} highlight={s.highlight} at={1.1} brand={brand} size={50} color={k.ink} marker={false} />
      </div>
      {s.max !== undefined && (
      <div style={{ position: "absolute", left: 80, top: 920, width: 500 }}>
        <div style={{ height: 34, borderRadius: 17, background: `${k.ink}1F`, overflow: "hidden" }}>
          <div style={{ height: "100%", width: `${g * 100}%`, borderRadius: 17, background: `linear-gradient(90deg, ${k.primary}, ${k.accent})` }} />
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 12, fontFamily: brand.typography.family, fontWeight: 700, fontSize: 28, color: k.ink, opacity: 0.7 }}>
          <span>0</span>
          <span>{max.toLocaleString("id-ID", { maximumFractionDigits: 1 })}{s.unit}</span>
        </div>
      </div>
      )}
    </Stage>
  );
};

// ================================================================ 9. Grafik batang
// Batang tumbuh dengan spring; batang tertinggi disorot; ADRI menyentuhnya; kamera mendekat ke sana.
export const SceneGrafik: React.FC<SP> = ({ s, brand, caption }) => {
  const k = inkOf(s, brand);
  const data = (s.data ?? []).slice(0, 5);
  const box = { x: 90, y: 560, w: 620, h: 640 };
  const g = barGeometry(data.length ? data : [{ label: "", value: 1 }], box.x, box.y, box.w, box.h);
  const hi = data.reduce((m, d, i) => (d.value > (data[m]?.value ?? -Infinity) ? i : m), 0);
  const top = g[hi] ?? g[0];
  return (
    <Stage s={s} brand={brand} shot={{ kind: "focus", x: top.cx, y: top.top + 100, at: 2.0, zoom: 1.08 }} alt={{ kind: "establish" }} focus={[box.x + box.w / 2, box.y + box.h]} caption={caption} hud={<Head s={s} brand={brand} k={k} />}
      front={
        // ADRI berjalan mendekat ke grafik, lalu menyentuh batang tertinggi saat batangnya selesai tumbuh
        <Presenter brand={brand} x={890} role="sedang" pose={[{ at: 0, pose: "lihat" }, { at: 1.6, pose: "sentuh" }]} facing="left" look="maju" walkFrom={1180} walkLen={0.9} at={0.2} seed={seedOf(s, 9)} pointAt={[top.x + top.w, top.top + top.h * 0.3]} />
      }
    >
      <BarChart data={data} x={box.x} y={box.y} w={box.w} h={box.h} at={0.4} k={k} unit={s.unit} focus={hi} />
    </Stage>
  );
};

// ================================================================ 10. Lingkaran (donat)
// Kamera menjauh perlahan memperlihatkan keseluruhan; segmen tergambar satu per satu dengan label penunjuk.
export const SceneLingkaran: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const data = (s.data ?? []).slice(0, 5);
  const C = { x: 560, y: 720, r: 180 };
  const top = data[0];
  const total = data.reduce((a, d) => a + d.value, 0) || 1;
  return (
    <Stage s={s} brand={brand} shot={{ kind: "establish" }} alt={{ kind: "focus", x: C.x, y: C.y, at: 1.2, zoom: 1.08 }} focus={[C.x, C.y]} caption={caption} hud={<Head s={s} brand={brand} k={k} />}
      front={<Presenter brand={brand} x={190} role="kecil" pose={[{ at: 0, pose: "jelaskan" }, { at: 2.2, pose: "tunjuk" }]} facing="right" look="penonton" at={0.5} seed={seedOf(s, 10)} pointAt={[C.x - C.r - 20, C.y + 40]} />}
    >
      <Donut data={data} cx={C.x} cy={C.y} r={C.r} at={0.4} k={k} skipFirst />
      {top && (
        <div style={{ position: "absolute", left: C.x - 140, top: C.y - 66, width: 280, textAlign: "center", fontFamily: brand.typography.family, opacity: ap(frame, fps, 1.0, 0.5) }}>
          <div style={{ fontWeight: 800, fontSize: 76, color: k.ink, lineHeight: 1 }}>{Math.round((top.value / total) * 100)}%</div>
          <div style={{ fontWeight: 700, fontSize: 28, color: k.ink, opacity: 0.8, marginTop: 6 }}>{top.label}</div>
        </div>
      )}
    </Stage>
  );
};

// ================================================================ 11. Linimasa sejarah
// Linimasa panjang; kamera menyusuri tiap peristiwa; ADRI berjalan di bawahnya.
export const SceneTimeline: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps, sec } = useScene();
  const sx: SceneData = { ...s, env: s.env ?? "sejarah" };
  const k = inkOf(sx, brand);
  const ev = (s.events ?? []).slice(0, 5);
  const gap = 460;
  const x0 = 540;
  const Y = 1000;
  const t0 = 0.8;
  const per = Math.max(1.0, (sec - t0 - 0.8) / Math.max(ev.length, 1));
  const keys = ev.map((_, i) => ({ t: i === 0 ? 0 : t0 + i * per, x: timelineX(i, x0, gap), y: Y, zoom: 1 }));
  const cam = pathAt(keys.length ? keys : [{ t: 0, x: x0, y: Y }], frame / fps);
  const prev = pathAt(keys.length ? keys : [{ t: 0, x: x0, y: Y }], (frame - 2) / fps);
  const active = Math.max(0, keys.filter((kk) => frame / fps >= kk.t).length - 1);
  const moving = Math.abs(cam.x - prev.x) > 1;
  return (
    <Stage s={sx} brand={brand} shot={{ kind: "path", keys }} focus={[x0, Y]} caption={caption}
      hud={
        <div style={{ position: "absolute", left: 80, right: 80, top: 150 }}>
          <Kicker text={s.kicker} at={0.1} color={k.accent} />
          <div style={{ height: 16 }} />
          <KineticTitle text={s.title} highlight={s.highlight} at={0.2} brand={brand} size={64} color={k.ink} />
        </div>
      }
    >
      <Timeline events={ev} x0={x0} y={Y} gap={gap} at={0.3} k={k} active={active} />
      <Presenter brand={brand} x={cam.x - 300} role="kecil" pose={moving ? "jalan" : "tunjuk"} facing="right" look="atas" seed={seedOf(s, 11)} at={0.2} pointAt={moving ? null : [cam.x - 30, Y - 30]} />
    </Stage>
  );
};

// ================================================================ 12. Peta
// Peta digambar dari kiri; pin jatuh; rute tergambar; kamera mendekat ke pin terakhir.
export const ScenePeta: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const pins = (s.pins ?? []).slice(0, 5);
  const box = { x: 40, y: 560, w: 1000 };
  const lastPin = pins[pins.length - 1];
  const last = lastPin ? [box.x + lastPin.u * box.w, box.y + lastPin.v * box.w * 0.42] : [540, 760];
  const zoomAt = 1.2 + pins.length * 0.3;
  return (
    <Stage s={s} brand={brand} shot={{ kind: "path", keys: [{ t: 0, x: 540, y: 760, zoom: 1 }, { t: zoomAt + 0.8, x: 540 + (last[0] - 540) * 0.3, y: last[1], zoom: 1.1 }] }} focus={[540, 760]} caption={caption}
      hud={
        <>
          <div style={{ position: "absolute", left: 80, right: 80, top: 150 }}>
            <Kicker text={s.kicker} at={0.1} color={k.accent} />
            <div style={{ height: 16 }} />
            <KineticTitle text={s.title} highlight={s.highlight} at={0.2} brand={brand} size={64} color={k.ink} />
          </div>
          <div style={{ position: "absolute", left: 90, top: 1100, width: 90, height: 90, opacity: ap(frame, fps, 0.8, 0.5), rotate: `${Math.sin(frame / 30) * 8}deg` }}>
            <svg width={90} height={90} viewBox="0 0 100 100" style={{ overflow: "visible" }}>
              <path d="M50 4 L62 50 L50 96 L38 50 Z" fill={k.ink} opacity={0.4} />
              <path d="M50 4 L62 50 L38 50 Z" fill={k.accent} />
              <text x={50} y={-6} textAnchor="middle" fontSize={22} fontWeight={800} fill={k.ink}>U</text>
            </svg>
          </div>
          <Presenter brand={brand} x={880} role="kecil" pose={[{ at: 0, pose: "lihat" }, { at: 1.4, pose: "tunjuk" }]} facing="left" look="maju" at={0.6} seed={seedOf(s, 12)} pointAt={[last[0], last[1]]} />
        </>
      }
    >
      <MapView pins={pins} x={box.x} y={box.y} w={box.w} at={0.4} k={k} />
    </Stage>
  );
};

// ================================================================ 13. Diagram alir
export const SceneAlur: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps, sec } = useScene();
  const k = inkOf(s, brand);
  const nodes = (s.steps ?? []).slice(0, 4);
  const count = s.decision ? Math.max(1, nodes.length - 1) + 1 : nodes.length;
  const active = Math.min(count - 1, Math.floor((frame / fps / Math.max(sec - 0.6, 1)) * count));
  const flow = { x: 400, y: 500, w: 480, gap: 220 };
  const ay = flow.y + active * flow.gap;
  return (
    <Stage s={s} brand={brand} shot={{ kind: "track", from: [640, flow.y + 200], to: [640, flow.y + (count - 1) * flow.gap], zoom: 1.03 }} alt={{ kind: "panV", distance: 140 }} focus={[640, flow.y]} caption={caption} hud={<Head s={s} brand={brand} k={k} size={62} />}
      front={<Presenter brand={brand} x={160} role="kecil" pose={[{ at: 0, pose: "jelaskan" }, { at: 1.4, pose: "tunjuk" }]} facing="right" look="maju" at={0.4} seed={seedOf(s, 13)} pointAt={[flow.x - 40, ay]} />}
    >
      <FlowChart nodes={nodes} decision={s.decision} x={flow.x} y={flow.y} w={flow.w} gap={flow.gap} at={0.4} k={k} active={active} />
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        <path d={`M${flow.x - 30} ${ay} l-34 -22 v44 Z`} fill={k.accent} opacity={ap(frame, fps, 0.6, 0.3)} />
      </svg>
    </Stage>
  );
};

// ================================================================ 14. Potongan (bagian dalam objek)
export const ScenePotongan: React.FC<SP> = ({ s, brand, caption }) => {
  const sx: SceneData = { ...s, env: s.env ?? "sains" };
  const k = inkOf(sx, brand);
  const layers = (s.layers ?? s.points?.map((p) => splitPoint(p).label) ?? []).slice(0, 4);
  const O = { x: 620, y: 740, w: 560, h: 460 };
  const g = cutawayLayers(layers.length, O.x, O.y, O.w, O.h);
  return (
    <Stage s={sx} brand={brand} shot={{ kind: "focus", x: O.x, y: O.y, at: 1.0, zoom: 1.06 }} alt={{ kind: "reveal", x: O.x, y: O.y }} focus={[O.x, O.y]} caption={caption} hud={<Head s={sx} brand={brand} k={k} size={62} />}
      front={<Presenter brand={brand} x={170} role="kecil" pose={[{ at: 0, pose: "lihat" }, { at: 1.3, pose: "sentuh" }]} facing="right" look="maju" at={0.4} seed={seedOf(s, 14)} pointAt={[O.x - O.w * 0.3, O.y]} />}
    >
      <Cutaway layers={layers} cx={O.x} cy={O.y} w={O.w} h={O.h} at={0.3} k={k} />
      {layers.map((l, i) => (
        <Callout key={i} from={[g[i].cx + (g[i].w / 2) * 0.5, g[i].cy - (g[i].h / 2) * 0.45]} to={[430, 1080 + i * 88]} label={`${i + 1}. ${l}`} at={1.1 + i * 0.3} k={k} width={570} align="right" />
      ))}
    </Stage>
  );
};

// ================================================================ 15. Persentase
export const ScenePersentase: React.FC<SP> = ({ s, brand, caption }) => {
  const k = inkOf(s, brand);
  const v = Math.max(0, Math.min(100, s.value ?? 0));
  return (
    <Stage s={s} brand={brand} shot={{ kind: "focus", x: 380, y: 760, at: 1.6, zoom: 1.05 }} alt={{ kind: "emphasis", x: 380, y: 760 }} focus={[380, 780]} caption={caption}
      front={<Presenter brand={brand} x={870} role="sedang" pose={[{ at: 0, pose: "jelaskan" }, { at: 1.8, pose: s.pose ?? "angkat" }]} facing="left" look="penonton" at={0.3} seed={seedOf(s, 15)} />}
    >
      <div style={{ position: "absolute", left: 80, top: 150 }}>
        <Kicker text={s.kicker} at={0.1} color={k.accent} />
      </div>
      <div style={{ position: "absolute", left: 70, top: 210 }}>
        <CountUp value={v} at={0.4} len={1.4} unit="%" size={220} color={k.ink} unitColor={k.accent} font={brand.typography.family} />
      </div>
      <Waffle value={v} x={80} y={480} size={560} at={0.5} k={k} />
      <div style={{ position: "absolute", left: 80, top: 1080, width: 560, fontFamily: brand.typography.family }}>
        <KineticTitle text={s.label ?? s.title} highlight={s.highlight} at={1.2} brand={brand} size={46} color={k.ink} marker={false} />
      </div>
    </Stage>
  );
};

// ================================================================ 2b. Objek penuh layar (fakta)
// Objek memenuhi layar dan dibuka dengan zoom reveal; label penunjuk di bawah; ADRI kecil bereaksi.
const SceneObjekFull: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const pop = sp(frame, fps, 0.1, { damping: 16 });
  const pts = (s.points ?? []).slice(0, 3);
  const O = { x: 540, y: 700, r: 370 };
  const spots: [number, number][] = [[400, 560], [700, 640], [520, 900]];
  return (
    <Stage s={s} brand={brand} shot={{ kind: "reveal", x: O.x, y: O.y, from: 1.35 }} alt={{ kind: "focus", x: O.x, y: O.y, at: 1.4, zoom: 1.06 }} focus={[O.x, O.y]} caption={caption} hud={<Head s={s} brand={brand} k={k} size={66} />}
      front={<Presenter brand={brand} x={930} role="kecil" pose={[{ at: 0, pose: "lihat" }, { at: 1.5, pose: s.pose ?? "kaget" }]} facing="left" look="atas" at={0.9} seed={seedOf(s, 2)} talking={false} />}
    >
      {/* sinar lembut di belakang objek */}
      <svg width={W} height={H} style={{ position: "absolute", inset: 0 }}>
        {Array.from({ length: 10 }, (_, i) => {
          const a = (i / 10) * Math.PI * 2 + frame / 260;
          return <path key={i} d={`M${O.x} ${O.y} L${O.x + Math.cos(a) * 700} ${O.y + Math.sin(a) * 700} L${O.x + Math.cos(a + 0.16) * 700} ${O.y + Math.sin(a + 0.16) * 700} Z`} fill={k.accent} opacity={0.06 * Math.min(1, pop)} />;
        })}
      </svg>
      <div data-qa="object" style={{ position: "absolute", left: O.x - O.r, top: O.y - O.r, width: O.r * 2, height: O.r * 2, borderRadius: "46% 54% 50% 50% / 52% 46% 54% 48%", overflow: "hidden", scale: `${0.85 + 0.15 * Math.min(pop, 1)}`, boxShadow: "0 50px 100px rgba(0,0,0,0.4)" }}>
        <Visual s={s} k={k} size={360} />
      </div>
      {pts.map((pt, i) => (
        <Callout key={i} from={spots[i]} to={[110, 1170 + i * 86]} label={splitPoint(pt).label || splitPoint(pt).icon} at={1.4 + i * 0.35} k={k} width={600} align="right" />
      ))}
    </Stage>
  );
};

// ================================================================ 16. Minimalis (kesimpulan)
// Ruang kosong yang luas, satu kalimat inti, satu garis aksen; ADRI kecil mengajak penonton.
export const SceneMinimalis: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const sx: SceneData = { ...s, env: s.env ?? "minimal" };
  const k = inkOf(sx, brand);
  const line = ap(frame, fps, 1.0, 0.8, EASE.inOut);
  const text = s.title ?? s.text ?? "";
  return (
    <Stage s={sx} brand={brand} shot={{ kind: "drift" }} alt={{ kind: "establish" }} focus={[540, 700]} caption={caption}
      hud={
        <div style={{ position: "absolute", left: 120, right: 120, top: 480, textAlign: "center" }}>
          <Kicker text={s.kicker} at={0.2} color={k.accent} align="center" />
          <div style={{ height: 34 }} />
          <KineticTitle text={text} highlight={s.highlight} at={0.4} brand={brand} size={text.length > 40 ? 76 : 92} color={k.ink} align="center" mode="line" />
          <div style={{ margin: "48px auto 0", width: 260 * line, height: 6, borderRadius: 3, background: k.accent }} />
        </div>
      }
      front={<Presenter brand={brand} x={540} role="kecil" pose={[{ at: 0, pose: "jelaskan" }, { at: 1.6, pose: s.pose ?? "simpulkan" }]} facing="right" look="penonton" at={0.8} seed={seedOf(s, 16)} />}
    >
      {null}
    </Stage>
  );
};

// ================================================================ 1b. Karakter close-up
// ADRI jadi fokus: bingkai dekat dari pinggang ke atas, kalimatnya muncul di gelembung bicara.
const SceneKarakterDekat: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const bub = sp(frame, fps, 0.5, { damping: 14 });
  const pose = s.pose ?? "tanya";
  return (
    <Stage s={s} brand={brand} shot={{ kind: "emphasis", x: 380, y: 700 }} alt={{ kind: "reveal", x: 380, y: 700, from: 1.25 }} focus={[380, 700]} caption={caption}
      hud={
        <div style={{ position: "absolute", left: 520, right: 60, top: 470, scale: `${Math.min(1, bub)}`, transformOrigin: "0% 100%" }}>
          {/* gelembung bicara dengan ekor ke arah kepala ADRI */}
          <div style={{ position: "relative", background: k.bg, borderRadius: 44, padding: "36px 38px", boxShadow: "0 24px 60px rgba(11,21,48,0.18)" }}>
            <Kicker text={s.kicker} at={0.6} color={k.accent} />
            <div style={{ height: 12 }} />
            <KineticTitle text={s.title} highlight={s.highlight} at={0.7} brand={brand} size={Math.min(s.title && s.title.length > 36 ? 58 : 68, Math.floor(420 / (0.6 * Math.max(...(s.title ?? "").split(" ").map((x) => x.length), 1))))} color={brand.palette.bg} />
            <svg width={80} height={60} style={{ position: "absolute", left: -46, bottom: 60 }}><path d="M80 0 L0 40 L80 60 Z" fill={k.bg} /></svg>
          </div>
        </div>
      }
      front={<Presenter brand={brand} x={300} role="besar" closeup pose={[{ at: 0, pose: "lihat" }, { at: 0.4, pose }, ...(pose === "tanya" ? [{ at: 2.4, pose: "pikir" as Pose }] : [])]} facing="right" look="penonton" at={0} seed={seedOf(s, 17)} exitTo={s.adriExit} />}
    >
      {null}
    </Stage>
  );
};

// ================================================================ 2c. Objek close-up
// Objek sangat besar dan sengaja terpotong di tepi kanan; teks minimal di kiri. Tanpa ADRI.
const SceneObjekDekat: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const pop = sp(frame, fps, 0.05, { damping: 18 });
  const O = { x: 980, y: 760, r: 520 };
  const lab = (s.points ?? []).map((p) => splitPoint(p).label).filter(Boolean)[0];
  return (
    <Stage s={{ ...s, adri: false }} brand={brand} shot={{ kind: "focus", x: O.x, y: O.y, at: 0.6, zoom: 1.06 }} alt={{ kind: "rack", from: "right" }} focus={[O.x, O.y]} caption={caption}
      hud={<Head s={s} brand={brand} k={k} pos="kiri" size={72} />}
    >
      <div data-qa="object" data-qa-wide="1" style={{ position: "absolute", left: O.x - O.r, top: O.y - O.r, width: O.r * 2, height: O.r * 2, borderRadius: "48% 52% 50% 50%", overflow: "hidden", translate: `${(1 - Math.min(pop, 1)) * 300}px 0px`, rotate: `${Math.sin(frame / 50) * 1.5}deg`, boxShadow: "0 50px 100px rgba(11,21,48,0.25)" }}>
        <Visual s={s} k={k} size={420} />
      </div>
      {lab && (
        <div data-qa="text" style={{ position: "absolute", left: 80, top: 1060, width: 380, fontFamily: brand.typography.family, fontWeight: 700, fontSize: 38, lineHeight: 1.2, color: k.ink, opacity: ap(frame, fps, 1.2, 0.5) }}>
          <span style={{ borderLeft: `6px solid ${k.accent}`, paddingLeft: 16, display: "inline-block" }}>{lab}</span>
        </div>
      )}
    </Stage>
  );
};

// ================================================================ 17. Lingkungan (wide shot)
// Ilustrasi lingkungan memenuhi layar dan jadi fokus; judul di bawah, subtitle di atas; ADRI kecil di kejauhan
// (atau tidak ada). Untuk membuka cerita atau membawa penonton masuk ke topik.
const Hero: React.FC<{ env: Env; k: Ink; frame: number; s: SceneData }> = ({ env, k, frame, s }) => {
  const e = canonEnv(env);
  const t = frame / 30;
  switch (e) {
    case "langit":
      return (
        <g>
          {Array.from({ length: 16 }, (_, i) => {
            const a = (i / 16) * Math.PI * 2 + t * 0.15;
            return <line key={i} x1={540 + Math.cos(a) * 200} y1={700 + Math.sin(a) * 200} x2={540 + Math.cos(a) * 280} y2={700 + Math.sin(a) * 280} stroke="#FFD15C" strokeWidth={16} strokeLinecap="round" />;
          })}
          <circle cx={540} cy={700} r={170} fill="#FFD15C" />
          <g transform={`translate(${-260 + ((t * 60) % 1600)} 860) scale(1.6)`}><ellipse rx={90} ry={46} fill="#FFFFFF" /><ellipse cx={-70} cy={14} rx={60} ry={34} fill="#FFFFFF" /><ellipse cx={74} cy={12} rx={66} ry={36} fill="#FFFFFF" /></g>
        </g>
      );
    case "alam":
      return (
        <g transform={`rotate(${Math.sin(t * 1.2) * 1.2} 540 1300)`}>
          <rect x={510} y={820} width={60} height={480} rx={20} fill="#7A5230" />
          {[[540, 700, 230], [400, 820, 150], [690, 810, 160], [540, 560, 150]].map(([x, y, r], i) => (
            <circle key={i} cx={x + Math.sin(t * 1.5 + i) * 6} cy={y} r={r} fill={["#2F9E6E", "#3DB37D", "#279160", "#4CC48A"][i]} />
          ))}
        </g>
      );
    case "kota":
      return (
        <g>
          <rect x={-100} y={1180} width={1280} height={120} fill="#5C6B85" />
          {Array.from({ length: 8 }, (_, i) => <rect key={i} x={i * 160 - ((t * 80) % 160)} y={1234} width={80} height={12} rx={6} fill="#FFFFFF" />)}
          <g transform={`translate(${-300 + ((t * 260) % 1700)} 1120)`}>
            <rect x={0} y={0} width={300} height={90} rx={30} fill={k.primary} />
            <path d="M50 0 L90 -60 H220 L260 0 Z" fill={k.primary} />
            <path d="M100 -48 H170 V-4 H80 Z M180 -48 H212 L240 -4 H180 Z" fill="#CFE3FF" />
            <circle cx={70} cy={92} r={34} fill={k.ink} /><circle cx={230} cy={92} r={34} fill={k.ink} />
          </g>
        </g>
      );
    case "dapur":
      return (
        <g>
          <rect x={240} y={1060} width={600} height={60} rx={14} fill="#5B4636" />
          <path d="M300 820 H780 V1040 Q780 1070 750 1070 H330 Q300 1070 300 1040 Z" fill="#E76F51" />
          <rect x={280} y={800} width={520} height={40} rx={20} fill="#C2571A" />
          {[0, 1, 2].map((i) => {
            const p = (t * 0.6 + i / 3) % 1;
            return <path key={i} d={`M${440 + i * 80} ${780 - p * 260} q30 -40 0 -80 q-30 -40 0 -80`} stroke="#FFFFFF" strokeWidth={14} fill="none" strokeLinecap="round" opacity={0.6 * (1 - p)} />;
          })}
        </g>
      );
    case "sains":
      return (
        <g>
          <path d="M470 520 V720 L300 1080 Q280 1140 340 1140 H740 Q800 1140 780 1080 L610 720 V520 Z" fill="#FFFFFF" stroke={k.accent} strokeWidth={12} strokeLinejoin="round" />
          <path d="M360 980 H720 L780 1080 Q800 1140 740 1140 H340 Q280 1140 300 1080 Z" fill={k.accent} opacity={0.7} />
          <rect x={440} y={490} width={200} height={40} rx={18} fill={k.accent} />
          {[0, 1, 2, 3].map((i) => {
            const p = (t * 0.5 + i / 4) % 1;
            return <circle key={i} cx={480 + i * 40 + Math.sin(t * 3 + i) * 10} cy={1080 - p * 520} r={12 + i * 3} fill="none" stroke={k.accent} strokeWidth={4} opacity={1 - p} />;
          })}
        </g>
      );
    case "arsip":
      return (
        <g transform="rotate(-4 540 800)">
          <rect x={180} y={520} width={720} height={560} rx={12} fill="#F7E7C4" stroke="#A5581C" strokeWidth={6} />
          <rect x={150} y={500} width={60} height={600} rx={30} fill="#C98A4B" /><rect x={870} y={500} width={60} height={600} rx={30} fill="#C98A4B" />
          <path d="M280 620 C380 600 400 700 500 680 S640 600 700 720 S760 900 820 940" stroke="#3A2A18" strokeWidth={6} fill="none" strokeDasharray="18 14" pathLength={1} strokeDashoffset={0} style={{ clipPath: `inset(0 ${100 - Math.min(100, t * 60)}% 0 0)` }} />
          <path d="M800 920 l20 20 m0 -20 l-20 20" stroke="#D63A3F" strokeWidth={8} />
        </g>
      );
    case "pastel":
      return (
        <g>
          <path d="M260 1200 C200 1120 200 1010 250 940 C190 900 210 840 260 830 C250 700 330 600 460 600 C560 600 600 700 600 800 V1200 Z" fill="#7B4FE0" opacity={0.85} />
          <path d="M820 1200 C880 1120 880 1010 830 940 C890 900 870 840 820 830 C830 700 750 600 620 600 C520 600 480 700 480 800 V1200 Z" fill="#F08BC0" opacity={0.85} transform="translate(80 40)" />
          <circle cx={540} cy={460 + Math.sin(t * 2) * 10} r={70} fill="#FFFFFF" />
          <circle cx={460} cy={560} r={20} fill="#FFFFFF" /><circle cx={620} cy={570} r={14} fill="#FFFFFF" />
        </g>
      );
    case "malam":
      return (
        <g>
          <circle cx={600} cy={700} r={200} fill="#FFE9A0" />
          <circle cx={680} cy={640} r={180} fill={k.bg === "#FFFFFF" ? "#0B1530" : "#0B1530"} />
          <ellipse cx={380} cy={1040} rx={180} ry={40} fill="none" stroke={k.accent} strokeWidth={8} transform={`rotate(-14 380 1040)`} />
          <circle cx={380} cy={1040} r={90} fill={k.primary} />
        </g>
      );
    case "teknologi":
      return (
        <g>
          <rect x={360} y={480} width={360} height={720} rx={56} fill="#0B1530" stroke={k.accent} strokeWidth={8} />
          {[0, 1, 2].map((i) => {
            const p = sp(frame, 30, 0.5 + i * 0.35, { damping: 14 });
            return <rect key={i} x={400} y={580 + i * 170} width={280} height={130} rx={22} fill={[k.primary, k.secondary, k.accent][i]} opacity={0.85} transform={`translate(${(1 - Math.min(1, p)) * 300} 0)`} />;
          })}
        </g>
      );
    default:
      // lingkungan umum: objek utama dari ikon kalimat dalam bentuk besar
      return (
        <foreignObject x={290} y={480} width={500} height={500}>
          <ShapeBadge i={seedOf(s)} size={500} color={k.primary} icon={splitPoint(s.points?.[0] ?? "ide:").icon} />
        </foreignObject>
      );
  }
};

export const SceneLingkungan: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps } = useScene();
  const k = inkOf(s, brand);
  const e = envOf(s);
  const rise = sp(frame, fps, 0.1, { damping: 18 });
  return (
    <Stage s={s} brand={brand} shot={{ kind: "establish" }} alt={{ kind: "pan", distance: 80, zoom: 1.04 }} focus={[540, 800]} caption={caption}
      hud={<Head s={s} brand={brand} k={k} pos="bawah" size={70} maxWidth={760} />}
      front={<Presenter brand={brand} x={880} role="jauh" pose={[{ at: 0, pose: "lihat" }, { at: 1.8, pose: s.pose ?? "tunjukkan" }]} facing="left" look="atas" walkFrom={1150} walkLen={1.2} at={0.6} seed={seedOf(s, 18)} talking={false} />}
    >
      <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible", translate: `0px ${(1 - Math.min(rise, 1)) * 120}px`, opacity: Math.min(1, rise * 1.4) }}>
        {/* ilustrasi digeser ke atas: bagian bawah layar milik judul */}
        {/* ilustrasi diskalakan ke ruang di antara subtitle (atas) dan judul (bawah) */}
        <g data-qa="object" transform="translate(540 724) scale(0.85) translate(-540 -800)"><Hero env={e} k={k} frame={frame} s={s} /></g>
      </svg>
    </Stage>
  );
};

export const SCENES: Record<SceneType, React.FC<SP>> = {
  karakter: SceneKarakter,
  objek: SceneObjek,
  infografis: SceneInfografis,
  diagram: SceneDiagram,
  perbandingan: ScenePerbandingan,
  tipografi: SceneTipografi,
  sinematik: SceneSinematik,
  angka: SceneAngka,
  grafik: SceneGrafik,
  lingkaran: SceneLingkaran,
  timeline: SceneTimeline,
  peta: ScenePeta,
  alur: SceneAlur,
  potongan: ScenePotongan,
  persentase: ScenePersentase,
  minimalis: SceneMinimalis,
  lingkungan: SceneLingkungan,
};
