import { createContext, useContext } from "react";
import { AbsoluteFill, Easing, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import "../promo/fonts"; // Plus Jakarta Sans
import { Adri, type CharacterSpec, type Pose } from "./Adri";

// Pustaka adegan Adriy.project. Aturan yang diterapkan di sini (lihat pipeline/styles/ADRIY-STANDARD.md):
// - satu fokus visual per adegan, tiga lapis (belakang, tengah, depan) dengan parallax
// - kamera selalu bergerak pelan (zoom/geser), masuk dan keluar halus, transisi sesuai konteks
// - teks muncul bertahap, informasi penting disorot stabilo, subtitle maksimal 2 baris
// - tiap jenis adegan punya komposisi sendiri, jadi tidak ada "bingkai" yang dipakai berulang

export type Brand = {
  palette: { bg: string; bg2: string; ink: string; accent: string; muted: string; primary: string; secondary: string; negative: string; paper: string };
  typography: { family: string; caption: { size: number; weight: number } };
  character: CharacterSpec;
  motion: { enter: number; exit: number; stagger: number; cameraZoom: [number, number]; parallax: { back: number; mid: number; front: number } };
  outro?: { tagline: string; slogan: string };
};

export type Token = { text: string; fromMs: number; toMs: number };

export type SceneData = {
  type: "karakter" | "objek" | "infografis" | "diagram" | "perbandingan" | "tipografi" | "sinematik" | "angka";
  start: number; // detik, waktu film
  end: number;
  transition: "slide" | "fade" | "zoom" | "wipe";
  camera: "push" | "pull" | "pan-left" | "pan-right" | "rise";
  tone: "dark" | "blue" | "light";
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
};
export const Glyph: React.FC<{ name: string; size: number; color: string }> = ({ name, size, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24">
    <path d={GLYPHS[name] ?? GLYPHS.bintang} fill={color} />
  </svg>
);
const splitPoint = (p: string) => (p.includes(":") ? { icon: p.split(":")[0], label: p.slice(p.indexOf(":") + 1) } : { icon: "cek", label: p });

// ---------------------------------------------------------------- helper waktu
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const EASE_IN = Easing.bezier(0.16, 1, 0.3, 1);
const EASE_OUT = Easing.bezier(0.7, 0, 0.84, 0);
// Durasi adegan (frame). useVideoConfig() memberi durasi seluruh video, bukan adegan, jadi
// durasi adegan dikirim lewat context oleh AdriyVideo / pratinjau.
export const SceneLength = createContext<number | null>(null);
const useLocal = () => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const len = useContext(SceneLength);
  return { frame, fps, dur: len ?? durationInFrames, t: frame / fps };
};
// progres masuk elemen yang muncul pada detik `at` di dalam adegan
const useIn = (at: number, len = 0.5) => {
  const { frame, fps } = useLocal();
  return interpolate(frame, [at * fps, (at + len) * fps], [0, 1], { ...clamp, easing: EASE_IN });
};

// ---------------------------------------------------------------- sorotan stabilo
export const Marker: React.FC<{ children: React.ReactNode; at: number; brand: Brand }> = ({ children, at, brand }) => {
  const p = useIn(at, 0.45);
  return (
    <span style={{ position: "relative", whiteSpace: "nowrap", color: p > 0.5 ? brand.palette.bg : undefined }}>
      <span
        style={{
          position: "absolute",
          left: -10,
          right: -10,
          top: "12%",
          bottom: "4%",
          background: brand.palette.accent,
          borderRadius: 10,
          transformOrigin: "left center",
          scale: `${p} 1`,
          zIndex: -1,
          rotate: "-1deg",
        }}
      />
      {children}
    </span>
  );
};
// teks dengan kata/frasa tertentu disorot
const WithHighlight: React.FC<{ text: string; highlight?: string; at: number; brand: Brand }> = ({ text, highlight, at, brand }) => {
  if (!highlight || !text.toLowerCase().includes(highlight.toLowerCase())) return <>{text}</>;
  const i = text.toLowerCase().indexOf(highlight.toLowerCase());
  return (
    <>
      {text.slice(0, i)}
      <Marker at={at} brand={brand}>
        {text.slice(i, i + highlight.length)}
      </Marker>
      {text.slice(i + highlight.length)}
    </>
  );
};

// ---------------------------------------------------------------- lapisan + kamera + transisi
const TONES = (b: Brand) => ({
  dark: { bg: `radial-gradient(ellipse at 30% 20%, ${b.palette.bg2} 0%, ${b.palette.bg} 70%)`, ink: b.palette.ink, soft: b.palette.primary },
  blue: { bg: `radial-gradient(ellipse at 70% 25%, #4D84FF 0%, ${b.palette.primary} 45%, #1F3FB8 100%)`, ink: "#FFFFFF", soft: "#FFFFFF" },
  light: { bg: `radial-gradient(ellipse at 50% 30%, #FFFFFF 0%, #EAF0FB 75%)`, ink: b.palette.bg, soft: b.palette.primary },
});

const Shell: React.FC<{ s: SceneData; brand: Brand; mid: React.ReactNode; front?: React.ReactNode; caption?: React.ReactNode }> = ({ s, brand, mid, front, caption }) => {
  const { frame, fps, dur } = useLocal();
  const m = brand.motion;
  const tone = TONES(brand)[s.tone];
  // kamera: bergerak pelan sepanjang adegan
  const k = interpolate(frame, [0, dur], [0, 1], clamp);
  const [z0, z1] = m.cameraZoom;
  const cam = {
    push: { z: z0 + (z1 - z0) * k, x: 0, y: 0 },
    pull: { z: z1 - (z1 - z0) * k, x: 0, y: 0 },
    "pan-left": { z: (z0 + z1) / 2, x: 40 - 80 * k, y: 0 },
    "pan-right": { z: (z0 + z1) / 2, x: -40 + 80 * k, y: 0 },
    rise: { z: (z0 + z1) / 2, x: 0, y: 30 - 60 * k },
  }[s.camera];
  const layer = (f: number): React.CSSProperties => ({
    position: "absolute",
    inset: 0,
    scale: `${1 + (cam.z - 1) * f}`,
    translate: `${cam.x * f}px ${cam.y * f}px`,
  });
  // transisi masuk/keluar menurut konteks
  const e = interpolate(frame, [0, m.enter * fps], [0, 1], { ...clamp, easing: EASE_IN });
  const x = interpolate(frame, [dur - m.exit * fps, dur], [0, 1], { ...clamp, easing: EASE_OUT });
  const tr: React.CSSProperties =
    s.transition === "slide"
      ? { translate: `${(1 - e) * 100 - x * 25}% 0px`, opacity: 1 - x }
      : s.transition === "zoom"
        ? { scale: `${1.25 - 0.25 * e - 0.08 * x}`, opacity: Math.min(e * 1.4, 1) * (1 - x) }
        : s.transition === "wipe"
          ? { clipPath: `inset(0 ${(1 - e) * 100}% 0 0)`, opacity: 1 - x }
          : { opacity: e * (1 - x) };
  // partikel depan: titik-titik kecil yang melayang
  const dots = Array.from({ length: 10 }, (_, i) => {
    const r = Math.sin(i * 91.7) * 0.5 + 0.5;
    return { x: (i * 113) % 1000 + 40, y: ((i * 197 + frame * (0.6 + r)) % 2000) - 40, s: 6 + r * 10, o: 0.15 + r * 0.25 };
  });
  return (
    <AbsoluteFill style={{ ...tr, overflow: "hidden" }}>
      <AbsoluteFill style={{ background: tone.bg }} />
      {/* belakang: lingkaran lembut + grid titik */}
      <div style={layer(brand.motion.parallax.back)}>
        <div style={{ position: "absolute", width: 900, height: 900, left: -300, top: -200, borderRadius: "50%", background: `${tone.soft}22`, filter: "blur(40px)" }} />
        <div style={{ position: "absolute", width: 700, height: 700, right: -260, bottom: 200, borderRadius: "50%", background: `${brand.palette.accent}1C`, filter: "blur(50px)" }} />
        <AbsoluteFill style={{ backgroundImage: `radial-gradient(${tone.ink}1A 2px, transparent 2.5px)`, backgroundSize: "48px 48px" }} />
      </div>
      {/* tengah: isi adegan */}
      <div style={layer(brand.motion.parallax.mid)}>{mid}</div>
      {/* depan: partikel + elemen depan */}
      <div style={layer(brand.motion.parallax.front)}>
        {dots.map((d, i) => (
          <div key={i} style={{ position: "absolute", left: d.x, top: d.y, width: d.s, height: d.s, borderRadius: "50%", background: tone.ink, opacity: d.o }} />
        ))}
        {front}
      </div>
      {caption}
    </AbsoluteFill>
  );
};

// ---------------------------------------------------------------- subtitle (maks. 2 baris, plat gelap tipis supaya terbaca di HP)
export const SceneCaption: React.FC<{ tokens: Token[]; nowMs: number; brand: Brand; tone: SceneData["tone"] }> = ({ tokens, nowMs, brand, tone }) => {
  if (!tokens.length) return null;
  const light = tone === "light";
  return (
    <div style={{ position: "absolute", left: 80, right: 80, bottom: 200, textAlign: "center" }}>
      <span
        style={{
          fontFamily: brand.typography.family,
          fontWeight: brand.typography.caption.weight,
          fontSize: brand.typography.caption.size,
          lineHeight: 1.45,
          letterSpacing: -0.5,
          color: light ? brand.palette.bg : "#FFFFFF",
          background: light ? "rgba(255,255,255,0.86)" : "rgba(8,14,32,0.55)",
          padding: "6px 22px",
          borderRadius: 18,
          boxDecorationBreak: "clone",
          WebkitBoxDecorationBreak: "clone",
        }}
      >
        {tokens.map((t, i) => {
          const active = nowMs >= t.fromMs && nowMs < t.toMs;
          return (
            <span key={i} style={{ whiteSpace: "pre-wrap", color: active ? brand.palette.accent : undefined }}>
              {t.text}
            </span>
          );
        })}
      </span>
    </div>
  );
};

// ---------------------------------------------------------------- teks judul bertahap
const Kicker: React.FC<{ text?: string; at: number; color: string }> = ({ text, at, color }) => {
  const p = useIn(at, 0.4);
  if (!text) return null;
  return <div style={{ fontWeight: 700, fontSize: 34, letterSpacing: 4, color, opacity: p, translate: `${(1 - p) * -30}px 0px`, textTransform: "uppercase" }}>{text}</div>;
};
const Heading: React.FC<{ s: SceneData; brand: Brand; at: number; size?: number; color: string; align?: "left" | "center" }> = ({ s, brand, at, size = 76, color, align = "left" }) => {
  const p = useIn(at, 0.55);
  if (!s.title) return null;
  return (
    <div style={{ fontWeight: 800, fontSize: size, lineHeight: 1.08, letterSpacing: -1.5, color, opacity: p, translate: `0px ${(1 - p) * 40}px`, textAlign: align }}>
      <WithHighlight text={s.title} highlight={s.highlight} at={at + 0.5} brand={brand} />
    </div>
  );
};

const VisualOrGlyph: React.FC<{ s: SceneData; brand: Brand; size: number; fallback?: string }> = ({ s, brand, size, fallback = "ide" }) =>
  s.visual ? (
    <Img src={staticFile(s.visual)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
  ) : (
    <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", background: `linear-gradient(135deg, ${brand.palette.primary}, ${brand.palette.secondary})` }}>
      <Glyph name={splitPoint(s.points?.[0] ?? `${fallback}:`).icon} size={size} color="#FFFFFF" />
    </div>
  );

// ================================================================ 8 jenis adegan
type SP = { s: SceneData; brand: Brand; caption: React.ReactNode };

// 1. Karakter: ADRI besar di satu sisi, kartu topik di sisi lain
export const SceneKarakter: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame } = useLocal();
  const tone = TONES(brand)[s.tone];
  const side = s.side ?? "right";
  const enter = useIn(0.05, 0.7);
  const card = useIn(0.35, 0.6);
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <>
          <div style={{ position: "absolute", [side === "right" ? "left" : "right"]: 80, top: 260, width: 620, padding: 44, borderRadius: 40, background: s.tone === "light" ? "#FFFFFF" : "rgba(255,255,255,0.08)", border: `2px solid ${tone.ink}22`, opacity: card, translate: `0px ${(1 - card) * 60}px`, fontFamily: brand.typography.family }}>
            <Kicker text={s.kicker} at={0.45} color={brand.palette.accent} />
            <div style={{ height: 14 }} />
            <Heading s={s} brand={brand} at={0.6} size={70} color={tone.ink} />
          </div>
          {/* gelembung ikon kecil di sekitar ADRI: lingkungan yang "diajak bicara" */}
          {(s.points ?? []).slice(0, 2).map((pt, i) => {
            const pin = useIn(0.9 + i * 0.2, 0.5);
            const { icon } = splitPoint(pt);
            return (
              <div key={i} style={{ position: "absolute", [side]: 560 + i * 60, top: 900 + i * 200, width: 130, height: 130, borderRadius: 40, background: i ? brand.palette.secondary : brand.palette.primary, display: "flex", alignItems: "center", justifyContent: "center", scale: `${pin}`, rotate: `${Math.sin(frame / 25 + i) * 6}deg`, boxShadow: "0 20px 40px rgba(0,0,0,0.25)" }}>
                <Glyph name={icon} size={76} color="#FFFFFF" />
              </div>
            );
          })}
        </>
      }
      front={
        <div style={{ position: "absolute", [side]: -40, bottom: 330, translate: `${(1 - enter) * (side === "right" ? 300 : -300)}px 0px` }}>
          <Adri spec={brand.character} pose={s.pose ?? "jelaskan"} size={1050} talking facing={side === "right" ? "left" : "right"} seed={Math.round(s.start)} />
        </div>
      }
    />
  );
};

// 2. Objek: satu objek mengambang dengan label penunjuk; ADRI kecil menunjuk ke arahnya
export const SceneObjek: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame } = useLocal();
  const tone = TONES(brand)[s.tone];
  const pop = useIn(0.1, 0.7);
  const points = (s.points ?? []).slice(0, 3);
  // titik pada objek -> label; label diletakkan di luar objek dan tidak di sudut kanan bawah (tempat ADRI)
  const spots = [
    { x: 380, y: 600, lx: 80, ly: 400, ax: 280, ay: 470 },
    { x: 720, y: 640, lx: 600, ly: 380, ax: 800, ay: 450 },
    { x: 400, y: 1020, lx: 80, ly: 1230, ax: 280, ay: 1230 },
  ];
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <>
          <div style={{ position: "absolute", left: 80, right: 80, top: 130, fontFamily: brand.typography.family }}>
            <Kicker text={s.kicker} at={0.2} color={brand.palette.accent} />
            <Heading s={s} brand={brand} at={0.35} size={66} color={tone.ink} />
          </div>
          <div style={{ position: "absolute", left: 190, top: 470, width: 700, height: 700, borderRadius: "44% 56% 52% 48% / 48% 44% 56% 52%", overflow: "hidden", scale: `${0.7 + 0.3 * pop}`, opacity: pop, rotate: `${Math.sin(frame / 40) * 2}deg`, translate: `0px ${Math.sin(frame / 30) * 10}px`, boxShadow: "0 40px 80px rgba(0,0,0,0.35)" }}>
            <VisualOrGlyph s={s} brand={brand} size={320} />
          </div>
          <svg width="1080" height="1920" style={{ position: "absolute", inset: 0, overflow: "visible" }}>
            {points.map((_, i) => {
              const p = useIn(0.8 + i * 0.35, 0.5);
              const sp = spots[i];
              return (
                <g key={i} opacity={p}>
                  <circle cx={sp.x} cy={sp.y} r={18 + 8 * Math.sin(frame / 8 + i)} fill="none" stroke={brand.palette.accent} strokeWidth={5} />
                  <circle cx={sp.x} cy={sp.y} r={9} fill={brand.palette.accent} />
                  <path d={`M${sp.x} ${sp.y} L${sp.ax} ${sp.ay}`} stroke={brand.palette.accent} strokeWidth={4} strokeDasharray="600" strokeDashoffset={600 * (1 - p)} />
                </g>
              );
            })}
          </svg>
          {points.map((pt, i) => {
            const p = useIn(1.0 + i * 0.35, 0.45);
            const sp = spots[i];
            return (
              <div key={i} style={{ position: "absolute", left: sp.lx, top: sp.ly, width: 400, textAlign: "center", padding: "16px 24px", borderRadius: 20, background: s.tone === "light" ? "#FFFFFF" : brand.palette.paper, color: brand.palette.bg, fontFamily: brand.typography.family, fontWeight: 700, fontSize: 38, opacity: p, translate: `0px ${(1 - p) * 20}px`, boxShadow: "0 12px 30px rgba(0,0,0,0.25)" }}>
                {splitPoint(pt).label}
              </div>
            );
          })}
        </>
      }
      front={
        <div style={{ position: "absolute", right: -40, bottom: 330, opacity: useIn(0.5, 0.5) }}>
          <Adri spec={brand.character} pose="tunjuk" size={500} talking facing="left" seed={Math.round(s.start)} />
        </div>
      }
    />
  );
};

// 3. Infografis: poin-poin dengan ikon yang muncul bertahap, dihubungkan garis
export const SceneInfografis: React.FC<SP> = ({ s, brand, caption }) => {
  const tone = TONES(brand)[s.tone];
  const pts = (s.points ?? []).slice(0, 4);
  const colors = [brand.palette.primary, brand.palette.secondary, brand.palette.accent, brand.palette.negative];
  const line = useIn(0.6, 0.4 + pts.length * 0.3);
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <div style={{ position: "absolute", left: 80, right: 80, top: 150, fontFamily: brand.typography.family }}>
          <Kicker text={s.kicker} at={0.15} color={brand.palette.accent} />
          <Heading s={s} brand={brand} at={0.3} size={70} color={tone.ink} />
          <div style={{ position: "relative", marginTop: 140 }}>
            <div style={{ position: "absolute", left: 76, top: 80, width: 6, height: `${(pts.length - 1) * 250 * line}px`, background: `${tone.ink}33`, borderRadius: 3 }} />
            {pts.map((pt, i) => {
              const p = useIn(0.7 + i * 0.32, 0.5);
              const { icon, label } = splitPoint(pt);
              return (
                <div key={i} style={{ display: "flex", alignItems: "center", gap: 40, height: 250, opacity: p, translate: `${(1 - p) * 80}px 0px` }}>
                  <div style={{ width: 158, height: 158, flex: "none", borderRadius: 46, background: colors[i % colors.length], display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 16px 30px rgba(0,0,0,0.22)" }}>
                    <Glyph name={icon} size={92} color="#FFFFFF" />
                  </div>
                  <div style={{ fontWeight: 700, fontSize: 54, lineHeight: 1.15, color: tone.ink, maxWidth: 520 }}>{label}</div>
                </div>
              );
            })}
          </div>
        </div>
      }
      front={
        <div style={{ position: "absolute", right: -90, bottom: 330, opacity: useIn(0.4, 0.5) }}>
          <Adri spec={brand.character} pose="jelaskan" size={620} talking facing="left" seed={Math.round(s.start) + 3} />
        </div>
      }
    />
  );
};

// 4. Diagram: langkah-langkah zig-zag dengan panah yang tergambar; langkah aktif disorot
export const SceneDiagram: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, fps, dur } = useLocal();
  const tone = TONES(brand)[s.tone];
  const steps = (s.steps ?? []).slice(0, 4);
  const gap = steps.length > 3 ? 280 : 360;
  const pos = steps.map((_, i) => ({ x: i % 2 ? 540 : 120, y: 560 + i * gap }));
  const active = Math.min(steps.length - 1, Math.floor((frame / Math.max(dur - 0.6 * fps, 1)) * steps.length));
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <>
          <div style={{ position: "absolute", left: 80, right: 80, top: 150, fontFamily: brand.typography.family }}>
            <Kicker text={s.kicker} at={0.1} color={brand.palette.accent} />
            <Heading s={s} brand={brand} at={0.25} size={68} color={tone.ink} />
          </div>
          <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
            <defs>
              <marker id="panah" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                <path d="M0 0 L10 5 L0 10 Z" fill={brand.palette.accent} />
              </marker>
            </defs>
            {pos.slice(1).map((b, i) => {
              const a = pos[i];
              const p = useIn(0.7 + i * 0.45, 0.45);
              const d = `M${a.x + 210} ${a.y + 175} C${a.x + 210} ${a.y + 260}, ${b.x + 210} ${b.y - 90}, ${b.x + 210} ${b.y}`;
              return <path key={i} d={d} stroke={brand.palette.accent} strokeWidth={6} fill="none" strokeDasharray="500" strokeDashoffset={500 * (1 - p)} markerEnd={p > 0.95 ? "url(#panah)" : undefined} />;
            })}
          </svg>
          {steps.map((st, i) => {
            const p = useIn(0.45 + i * 0.45, 0.45);
            const on = i === active;
            return (
              <div key={i} style={{ position: "absolute", left: pos[i].x, top: pos[i].y, width: 420, minHeight: 170, padding: "26px 30px", borderRadius: 30, background: on ? brand.palette.accent : s.tone === "light" ? "#FFFFFF" : "rgba(255,255,255,0.1)", color: on ? brand.palette.bg : tone.ink, border: `2px solid ${on ? brand.palette.accent : tone.ink + "33"}`, fontFamily: brand.typography.family, opacity: p, scale: `${(0.8 + 0.2 * p) * (on ? 1.04 : 1)}`, boxShadow: on ? `0 20px 40px ${brand.palette.accent}55` : "none" }}>
                <div style={{ fontWeight: 800, fontSize: 30, letterSpacing: 3, opacity: 0.8 }}>LANGKAH {i + 1}</div>
                <div style={{ fontWeight: 700, fontSize: 48, lineHeight: 1.15, marginTop: 6 }}>{st}</div>
              </div>
            );
          })}
        </>
      }
      front={
        // ADRI menunjuk ke langkah yang sedang dibahas
        <div style={{ position: "absolute", right: -50, bottom: 330, opacity: useIn(0.6, 0.5) }}>
          <Adri spec={brand.character} pose="tunjuk" size={480} talking facing="left" seed={Math.round(s.start) + 5} />
        </div>
      }
    />
  );
};

// 5. Perbandingan: dua kolom, lencana VS, yang unggul disorot
export const ScenePerbandingan: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame } = useLocal();
  const tone = TONES(brand)[s.tone];
  const vs = useIn(0.9, 0.3);
  const col = (d: SceneData["left"], i: number) => {
    const p = useIn(0.3 + i * 0.25, 0.55);
    if (!d) return null;
    const c = d.win ? brand.palette.secondary : brand.palette.negative;
    return (
      <div style={{ flex: 1, minHeight: 620, display: "flex", flexDirection: "column", justifyContent: "center", padding: "40px 26px", borderRadius: 36, background: s.tone === "light" ? "#FFFFFF" : "rgba(255,255,255,0.08)", border: `4px solid ${d.win ? c : tone.ink + "22"}`, textAlign: "center", opacity: p, translate: `${(1 - p) * (i ? 120 : -120)}px 0px`, boxShadow: d.win ? `0 24px 50px ${c}44` : "none" }}>
        <div style={{ fontWeight: 700, fontSize: 40, color: tone.ink, opacity: 0.85 }}>{d.label}</div>
        <div style={{ fontWeight: 800, fontSize: d.value.length > 5 ? 92 : 120, letterSpacing: -3, color: c, margin: "28px 0", whiteSpace: "nowrap" }}>{d.value}</div>
        {d.note && <div style={{ fontWeight: 600, fontSize: 36, lineHeight: 1.2, color: tone.ink, opacity: 0.8 }}>{d.note}</div>}
        {d.win && <div style={{ marginTop: 24, display: "inline-flex", alignItems: "center", gap: 10, padding: "8px 20px", borderRadius: 30, background: c, color: "#fff", fontWeight: 800, fontSize: 30, scale: `${useIn(1.3, 0.35)}` }}><Glyph name="cek" size={34} color="#fff" /> LEBIH BAIK</div>}
      </div>
    );
  };
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <div style={{ position: "absolute", left: 70, right: 70, top: 160, fontFamily: brand.typography.family }}>
          <Kicker text={s.kicker} at={0.1} color={brand.palette.accent} />
          <Heading s={s} brand={brand} at={0.2} size={68} color={tone.ink} />
          <div style={{ position: "relative", display: "flex", gap: 40, marginTop: 170 }}>
            {col(s.left, 0)}
            {col(s.right, 1)}
            <div style={{ position: "absolute", left: "50%", top: "50%", translate: "-50% -50%", width: 140, height: 140, borderRadius: "50%", background: brand.palette.accent, color: brand.palette.bg, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 56, scale: `${interpolate(vs, [0, 1], [2.2, 1])}`, opacity: vs, rotate: `${Math.sin(frame / 20) * 4}deg`, boxShadow: "0 16px 40px rgba(0,0,0,0.35)" }}>VS</div>
          </div>
        </div>
      }
    />
  );
};

// 6. Tipografi kinetik: kata-kata besar muncul satu per satu, kata kunci disorot
export const SceneTipografi: React.FC<SP> = ({ s, brand, caption }) => {
  const tone = TONES(brand)[s.tone];
  const words = (s.text ?? s.title ?? "").split(" ").filter(Boolean);
  const hl = (s.highlight ?? "").toLowerCase();
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <div style={{ position: "absolute", left: 90, right: 90, top: 0, bottom: 260, fontFamily: brand.typography.family, display: "flex", flexWrap: "wrap", gap: "14px 26px", alignContent: "center" }}>
          {words.map((w, i) => {
            const p = useIn(0.15 + i * 0.12, 0.45);
            const key = hl && hl.split(" ").includes(w.toLowerCase().replace(/[^\p{L}\p{N}-]/gu, ""));
            // kata kunci paling besar, tapi tetap muat di lebar layar (900 px)
            const fit = Math.floor(1500 / Math.max(w.length, 1));
            const size = Math.min(key ? 150 : i % 3 === 0 ? 112 : 96, fit);
            return (
              <span key={i} style={{ fontWeight: 800, fontSize: size, lineHeight: 1.0, letterSpacing: -3, color: tone.ink, opacity: p, translate: `0px ${(1 - p) * 60}px`, rotate: `${(1 - p) * (i % 2 ? 6 : -6)}deg`, display: "inline-block" }}>
                {key ? <Marker at={0.4 + i * 0.12} brand={brand}>{w}</Marker> : w}
              </span>
            );
          })}
        </div>
      }
    />
  );
};

// 7. Sinematik: gambar penuh layar dengan bilah film, dorongan kamera, vignette
export const SceneSinematik: React.FC<SP> = ({ s, brand, caption }) => {
  const { frame, dur } = useLocal();
  const bars = useIn(0, 0.6);
  const k = frame / Math.max(dur, 1);
  const kick = useIn(0.6, 0.6);
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <>
          <AbsoluteFill style={{ scale: `${1.08 + 0.08 * k}`, translate: `${-30 + 60 * k}px 0px` }}>
            {s.visual ? (
              <Img src={staticFile(s.visual)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            ) : (
              // tanpa gambar: lanskap berlapis (gunung jauh, gunung dekat, kabut)
              <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
                <defs>
                  <linearGradient id="langit" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor="#FFB547" />
                    <stop offset="0.45" stopColor="#FF7E6B" />
                    <stop offset="1" stopColor="#2A1E5C" />
                  </linearGradient>
                </defs>
                <rect width="1080" height="1920" fill="url(#langit)" />
                <circle cx="540" cy="820" r="170" fill="#FFE3A3" opacity="0.9" />
                <path d="M0 1150 L220 900 L420 1080 L640 820 L860 1040 L1080 880 V1920 H0 Z" fill="#5B3A7A" opacity="0.9" />
                <path d="M0 1350 L260 1120 L520 1300 L760 1100 L1080 1320 V1920 H0 Z" fill="#2E2152" />
              </svg>
            )}
          </AbsoluteFill>
          <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 45%, transparent 40%, rgba(0,0,0,0.65) 100%)" }} />
          <div style={{ position: "absolute", left: 80, right: 80, top: 1180, fontFamily: brand.typography.family, opacity: kick, translate: `0px ${(1 - kick) * 30}px` }}>
            <Kicker text={s.kicker} at={0.6} color={brand.palette.accent} />
            <Heading s={s} brand={brand} at={0.75} size={84} color="#FFFFFF" />
          </div>
        </>
      }
      front={
        <>
          <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 150 * bars, background: "#000" }} />
          <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 150 * bars, background: "#000" }} />
        </>
      }
    />
  );
};

// 8. Angka: angka besar yang menghitung naik, cincin progres, ADRI bereaksi
export const SceneAngka: React.FC<SP> = ({ s, brand, caption }) => {
  const tone = TONES(brand)[s.tone];
  const count = useIn(0.3, 1.4);
  const value = (s.value ?? 0) * count;
  const max = s.max ?? 100;
  const ring = Math.min(1, (s.value ?? 0) / max) * count;
  const decimals = Number.isInteger(s.value ?? 0) ? 0 : 1;
  const R = 300;
  const C = 2 * Math.PI * R;
  return (
    <Shell
      s={s}
      brand={brand}
      caption={caption}
      mid={
        <>
          <div style={{ position: "absolute", left: 80, right: 80, top: 150, fontFamily: brand.typography.family, textAlign: "center" }}>
            <Kicker text={s.kicker} at={0.1} color={brand.palette.accent} />
          </div>
          <svg width="1080" height="1080" style={{ position: "absolute", left: 0, top: 300 }}>
            <circle cx="540" cy="540" r={R} fill="none" stroke={`${tone.ink}1F`} strokeWidth="44" />
            <circle cx="540" cy="540" r={R} fill="none" stroke={brand.palette.accent} strokeWidth="44" strokeLinecap="round" strokeDasharray={C} strokeDashoffset={C * (1 - ring)} transform="rotate(-90 540 540)" />
          </svg>
          <div style={{ position: "absolute", left: 0, right: 0, top: 730, textAlign: "center", fontFamily: brand.typography.family, color: tone.ink }}>
            <div style={{ fontWeight: 800, fontSize: 210, letterSpacing: -8, lineHeight: 1 }}>
              {value.toLocaleString("id-ID", { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}
              <span style={{ fontSize: 110, color: brand.palette.accent }}>{s.unit ?? ""}</span>
            </div>
            <div style={{ fontWeight: 700, fontSize: 48, lineHeight: 1.2, marginTop: 230, padding: "0 120px", opacity: useIn(1.2, 0.5) }}>{s.label}</div>
          </div>
        </>
      }
      front={
        <div style={{ position: "absolute", left: -70, bottom: 330, opacity: useIn(1.4, 0.5), scale: `${0.9 + 0.1 * useIn(1.4, 0.5)}` }}>
          <Adri spec={brand.character} pose={s.pose ?? "kaget"} size={560} facing="right" seed={Math.round(s.start) + 7} />
        </div>
      }
    />
  );
};

export const SCENES: Record<SceneData["type"], React.FC<SP>> = {
  karakter: SceneKarakter,
  objek: SceneObjek,
  infografis: SceneInfografis,
  diagram: SceneDiagram,
  perbandingan: ScenePerbandingan,
  tipografi: SceneTipografi,
  sinematik: SceneSinematik,
  angka: SceneAngka,
};
