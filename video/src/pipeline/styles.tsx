import { Video } from "@remotion/media";
import { loadFont } from "@remotion/fonts";
import { AbsoluteFill, Easing, Img, interpolate, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import "../promo/fonts"; // memuat Plus Jakarta Sans untuk style bersih

// Style dibaca dari pipeline/styles/<id>.json (disalin pipeline ke public/media/<nama>/style.json).
// Semua tampilan dibuat dengan CSS dan SVG biasa, tanpa WebGL, supaya render tetap andal di
// mesin tanpa GPU dan bisa dipakai juga di HyperFrames nanti.

export type Style = {
  id: string;
  label: string;
  font: { family: string; file: string; weight: number };
  titleFont?: { family: string; file: string };
  palette: { bg: string; bg2: string; ink: string; accent: string; muted: string; strip?: string };
  background: {
    kind: "gradient" | "wayang" | "koran" | "horor" | "anak";
    moon?: { x: number; y: number };
    lightningEvery?: number;
    shapes?: number;
    lamp?: { x: number; y: number; flicker: number };
    gunungan?: boolean;
    border?: boolean;
    masthead?: string;
    edition?: string;
    columns?: boolean;
  };
  visual: { filter: string; blend: string; frame: "none" | "kelir" | "torn" | "fade" | "sticker"; halftone?: boolean };
  overlays: ("grain" | "vignette" | "paper" | "fog" | "flicker" | "scanlines")[];
  subtitle: {
    size: number;
    case: "none" | "upper";
    box: "none" | "glow" | "strip" | "bubble";
    position: number;
    entrance: "pop" | "flicker" | "stamp" | "creep" | "bounce";
    letterSpacing: number;
  };
};

// Font semua style dimuat sekali di awal (file kecil, tersimpan lokal di public/fonts/).
for (const [family, file, weight] of [
  ["Cinzel", "fonts/Cinzel-latin.woff2", "400 900"],
  ["Playfair Display", "fonts/PlayfairDisplay-latin.woff2", "400 900"],
  ["Creepster", "fonts/Creepster-latin.woff2", "400"],
  ["Special Elite", "fonts/SpecialElite-latin.woff2", "400"],
  ["Fredoka", "fonts/Fredoka-latin.woff2", "300 700"],
] as const) {
  loadFont({ family, url: staticFile(file), weight });
}

export const DEFAULT_STYLE: Style = {
  id: "bersih",
  label: "Bersih",
  font: { family: "Plus Jakarta Sans", file: "fonts/PlusJakartaSans-latin.woff2", weight: 800 },
  palette: { bg: "#0B2A6B", bg2: "#2563EB", ink: "#FFFFFF", accent: "#FFD25E", muted: "#DCE8FD" },
  background: { kind: "gradient" },
  visual: { filter: "none", blend: "normal", frame: "none" },
  overlays: [],
  subtitle: { size: 84, case: "none", box: "none", position: 520, entrance: "pop", letterSpacing: -1 },
};

// ---------------------------------------------------------------- tekstur (SVG noise, deterministik)
const noiseUrl = (freq: number, seed: number, alpha: number) =>
  `url("data:image/svg+xml;utf8,${encodeURIComponent(
    `<svg xmlns='http://www.w3.org/2000/svg' width='360' height='360'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='${freq}' numOctaves='3' seed='${seed}' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 ${alpha} 0'/></filter><rect width='100%' height='100%' filter='url(#n)'/></svg>`,
  )}")`;

// Angka "acak" yang selalu sama untuk frame yang sama (render harus deterministik, tanpa Math.random).
const rand = (n: number) => {
  const x = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return x - Math.floor(x);
};

const Overlays: React.FC<{ style: Style }> = ({ style }) => {
  const frame = useCurrentFrame();
  return (
    <>
      {style.overlays.includes("fog") && (
        // dua lapis kabut bergerak berlawanan arah, pelan
        <>
          <AbsoluteFill style={{ backgroundImage: noiseUrl(0.004, 11, 0.9), backgroundSize: "1400px 1400px", backgroundPosition: `${frame * 0.9}px 0px`, mixBlendMode: "screen", opacity: 0.22 }} />
          <AbsoluteFill style={{ backgroundImage: noiseUrl(0.007, 23, 0.9), backgroundSize: "900px 900px", backgroundPosition: `${-frame * 1.6}px ${frame * 0.3}px`, mixBlendMode: "screen", opacity: 0.16 }} />
        </>
      )}
      {style.overlays.includes("scanlines") && (
        <AbsoluteFill
          style={{
            backgroundImage: "repeating-linear-gradient(0deg, rgba(0,0,0,0.28) 0px, rgba(0,0,0,0.28) 2px, transparent 2px, transparent 5px)",
            backgroundPosition: `0px ${(frame * 3) % 5}px`,
            opacity: 0.6,
          }}
        />
      )}
      {style.overlays.includes("flicker") && (
        // lampu yang tidak stabil: kadang meredup sesaat
        <AbsoluteFill style={{ background: "#000", opacity: rand(Math.floor(frame / 2)) > 0.9 ? 0.35 : rand(frame) * 0.06 }} />
      )}
      {style.overlays.includes("paper") && (
        <AbsoluteFill style={{ backgroundImage: noiseUrl(0.012, 7, 0.35), mixBlendMode: "multiply", opacity: 0.55 }} />
      )}
      {style.overlays.includes("grain") && (
        // butiran film: pola noise digeser tiap 2 frame
        <AbsoluteFill
          style={{
            backgroundImage: noiseUrl(0.9, 3, 0.5),
            backgroundPosition: `${(Math.floor(frame / 2) * 137) % 360}px ${(Math.floor(frame / 2) * 89) % 360}px`,
            mixBlendMode: "overlay",
            opacity: 0.35,
          }}
        />
      )}
      {style.overlays.includes("vignette") && (
        <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 42%, transparent 45%, rgba(0,0,0,0.85) 100%)" }} />
      )}
    </>
  );
};

// ---------------------------------------------------------------- latar
const Gunungan: React.FC<{ color: string; line: string; size: number }> = ({ color, line, size }) => (
  // gunungan (kayon) sederhana: bentuk daun meruncing dengan pohon hayat di dalamnya
  <svg width={size} height={size * 1.5} viewBox="0 0 200 300">
    <path d="M100 6 C150 70 196 150 186 230 C180 270 150 292 100 294 C50 292 20 270 14 230 C4 150 50 70 100 6 Z" fill={color} stroke={line} strokeWidth="4" />
    <path d="M100 60 L100 270 M100 120 C80 110 62 118 52 136 M100 120 C120 110 138 118 148 136 M100 170 C76 158 54 168 42 190 M100 170 C124 158 146 168 158 190 M100 220 C80 210 64 218 56 236 M100 220 C120 210 136 218 144 236" stroke={line} strokeWidth="4" fill="none" strokeLinecap="round" />
    <path d="M70 270 L130 270 L124 250 L76 250 Z" fill={line} />
  </svg>
);

const BatikBorder: React.FC<{ color: string }> = ({ color }) => (
  // bingkai motif kawung: empat lingkaran kecil berulang di tepi layar
  <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
    <defs>
      <pattern id="kawung" width="48" height="48" patternUnits="userSpaceOnUse">
        <ellipse cx="24" cy="10" rx="7" ry="10" fill="none" stroke={color} strokeWidth="2.5" />
        <ellipse cx="24" cy="38" rx="7" ry="10" fill="none" stroke={color} strokeWidth="2.5" />
        <ellipse cx="10" cy="24" rx="10" ry="7" fill="none" stroke={color} strokeWidth="2.5" />
        <ellipse cx="38" cy="24" rx="10" ry="7" fill="none" stroke={color} strokeWidth="2.5" />
        <circle cx="24" cy="24" r="2.5" fill={color} />
      </pattern>
      <mask id="tepi">
        <rect width="1080" height="1920" fill="white" />
        <rect x="56" y="56" width="968" height="1808" rx="24" fill="black" />
      </mask>
    </defs>
    <rect width="1080" height="1920" fill="url(#kawung)" mask="url(#tepi)" opacity="0.8" />
    <rect x="56" y="56" width="968" height="1808" rx="24" fill="none" stroke={color} strokeWidth="4" />
  </svg>
);

const NewspaperColumns: React.FC<{ ink: string }> = ({ ink }) => {
  // blok "teks" abu-abu tipis dalam 3 kolom, seperti isi koran di belakang foto
  const cols = [0, 1, 2];
  return (
    <div style={{ position: "absolute", left: 70, right: 70, top: 1420, bottom: 90, display: "flex", gap: 28 }}>
      {cols.map((c) => (
        <div key={c} style={{ flex: 1, display: "flex", flexDirection: "column", gap: 14, borderLeft: c ? `2px solid ${ink}22` : "none", paddingLeft: c ? 14 : 0 }}>
          {Array.from({ length: 16 }, (_, i) => (
            <div key={i} style={{ height: 9, background: `${ink}2E`, width: `${78 + ((c * 7 + i * 13) % 22)}%` }} />
          ))}
        </div>
      ))}
    </div>
  );
};

export const Backdrop: React.FC<{ style: Style; title?: string }> = ({ style, title }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = style.palette;
  const bg = style.background;
  if (bg.kind === "wayang") {
    const lamp = bg.lamp ?? { x: 0.5, y: 0.4, flicker: 0.08 };
    // kedip lampu blencong: gabungan dua gelombang supaya tidak terasa berulang
    const f = 1 + lamp.flicker * (Math.sin(frame * 0.9) * 0.6 + Math.sin(frame * 0.37 + 1.3) * 0.4);
    return (
      <AbsoluteFill style={{ background: p.bg }}>
        <AbsoluteFill
          style={{
            background: `radial-gradient(ellipse ${62 * f}% ${48 * f}% at ${lamp.x * 100}% ${lamp.y * 100}%, #FFE2A6 0%, ${p.accent} 22%, ${p.bg2} 52%, ${p.bg} 100%)`,
            opacity: 0.92 * f,
          }}
        />
        {bg.border && <BatikBorder color={p.muted} />}
        {bg.gunungan && (
          <div style={{ position: "absolute", top: 120, left: 0, right: 0, display: "flex", justifyContent: "center", opacity: 0.9 }}>
            <Gunungan color={p.bg} line={p.accent} size={150} />
          </div>
        )}
        {title && (
          <div style={{ position: "absolute", top: 380, left: 90, right: 90, textAlign: "center", fontFamily: style.font.family, fontWeight: 700, fontSize: 46, letterSpacing: 6, color: p.ink, textTransform: "uppercase", textShadow: `0 0 24px ${p.accent}` }}>
            {title}
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "koran") {
    return (
      <AbsoluteFill style={{ background: `linear-gradient(180deg, ${p.bg} 0%, ${p.bg2} 100%)`, color: p.ink }}>
        <div style={{ position: "absolute", top: 70, left: 70, right: 70, textAlign: "center", fontFamily: style.font.family }}>
          <div style={{ borderTop: `6px solid ${p.ink}`, borderBottom: `2px solid ${p.ink}`, padding: "10px 0 6px", display: "flex", justifyContent: "space-between", fontSize: 26, fontWeight: 700, letterSpacing: 3 }}>
            <span>{bg.edition ?? ""}</span>
            <span>No. 01</span>
          </div>
          <div style={{ fontSize: 108, fontWeight: 900, lineHeight: 1.05, letterSpacing: -2, padding: "14px 0 10px" }}>{bg.masthead ?? "KABAR"}</div>
          <div style={{ borderTop: `2px solid ${p.ink}`, borderBottom: `6px solid ${p.ink}`, height: 14 }} />
          {title && <div style={{ fontSize: 44, fontWeight: 800, fontStyle: "italic", padding: "18px 10px 0", lineHeight: 1.15 }}>{title}</div>}
        </div>
        {bg.columns && <NewspaperColumns ink={p.ink} />}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "horor") {
    const moon = bg.moon ?? { x: 0.7, y: 0.16 };
    // kilat: dua kilatan cepat di tengah tiap siklus (bukan di frame 0, supaya awal video tidak langsung silau)
    const cycle = Math.round((bg.lightningEvery ?? 7.5) * fps);
    const c = (frame + Math.round(cycle / 2)) % cycle;
    const flash = c < 3 ? 0.55 : c >= 6 && c < 8 ? 0.35 : 0;
    const tf = style.titleFont?.family ?? style.font.family;
    return (
      <AbsoluteFill style={{ background: p.bg }}>
        <AbsoluteFill style={{ background: `radial-gradient(circle at ${moon.x * 100}% ${moon.y * 100}%, #DCE6EE 0%, #9FB3C2 3.5%, ${p.bg2}AA 14%, transparent 48%)` }} />
        {/* siluet pohon gundul di bawah, sederhana */}
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
          <path d="M0 1920 L0 1560 C120 1540 180 1600 260 1580 C360 1555 420 1610 540 1600 C660 1590 720 1540 830 1560 C930 1578 1000 1540 1080 1550 L1080 1920 Z" fill="#000" opacity="0.85" />
          <path d="M150 1600 L160 1300 M160 1420 L95 1330 M160 1380 L220 1290 M158 1340 L120 1260 M920 1580 L905 1250 M907 1380 L980 1300 M906 1330 L850 1240 M905 1290 L945 1210" stroke="#000" strokeWidth="14" strokeLinecap="round" fill="none" opacity="0.9" />
        </svg>
        {title && (
          <div style={{ position: "absolute", top: 230, left: 70, right: 70, textAlign: "center", fontFamily: tf, fontSize: 104, lineHeight: 1.05, color: p.accent, textShadow: `0 0 30px ${p.accent}88, 0 6px 0 #000` }}>
            {title}
          </div>
        )}
        <AbsoluteFill style={{ background: "#D8E4FF", opacity: flash, mixBlendMode: "screen" }} />
      </AbsoluteFill>
    );
  }
  if (bg.kind === "anak") {
    const colors = [p.accent, p.muted, "#FFB547", "#4CD3A5", "#5AA9FF", "#FF7A59"];
    const n = bg.shapes ?? 18;
    const tf = style.titleFont?.family ?? style.font.family;
    return (
      <AbsoluteFill style={{ background: `linear-gradient(160deg, ${p.bg} 0%, #FFD6E8 45%, ${p.bg2} 100%)` }}>
        {/* bentuk-bentuk ceria yang melayang naik dan bergoyang */}
        {Array.from({ length: n }, (_, i) => {
          const size = 50 + rand(i * 3) * 90;
          const x = rand(i * 7) * 1000;
          const speed = 0.6 + rand(i * 11) * 1.2;
          const y = 1960 - ((rand(i * 5) * 2100 + frame * speed * 2.2) % 2150);
          const wob = Math.sin(frame / 20 + i) * 18;
          const color = colors[i % colors.length];
          const kind = i % 3;
          return (
            <svg key={i} width={size} height={size} viewBox="0 0 100 100" style={{ position: "absolute", left: x + wob, top: y, rotate: `${frame * (i % 2 ? 0.8 : -0.8)}deg`, opacity: 0.75 }}>
              {kind === 0 && <circle cx="50" cy="50" r="44" fill={color} />}
              {kind === 1 && <path d="M50 4 L62 36 L96 38 L69 59 L79 93 L50 73 L21 93 L31 59 L4 38 L38 36 Z" fill={color} />}
              {kind === 2 && <rect x="14" y="14" width="72" height="72" rx="20" fill={color} />}
            </svg>
          );
        })}
        {title && (
          <div
            style={{
              position: "absolute",
              top: 200,
              left: 60,
              right: 60,
              textAlign: "center",
              fontFamily: tf,
              fontWeight: 700,
              fontSize: 96,
              lineHeight: 1.05,
              color: "#FFFFFF",
              WebkitTextStroke: `10px ${p.ink}`,
              paintOrder: "stroke fill",
              textShadow: `0 10px 0 ${p.ink}`,
              rotate: `${Math.sin(frame / 18) * 2}deg`,
            }}
          >
            {title}
          </div>
        )}
      </AbsoluteFill>
    );
  }
  return <AbsoluteFill style={{ background: `radial-gradient(circle at 50% 30%, ${p.bg2} 0%, ${p.bg} 100%)` }} />;
};

// ---------------------------------------------------------------- gambar / video latar
// Tepi sobek: titik-titik bergerigi yang sama setiap kali (tanpa Math.random) supaya render stabil.
const tornClip = (() => {
  const pts: string[] = [];
  const jag = (i: number) => ((i * 37) % 11) / 11;
  for (let i = 0; i <= 24; i++) pts.push(`${(i / 24) * 100}% ${jag(i) * 2.2}%`);
  for (let i = 0; i <= 30; i++) pts.push(`${100 - jag(i + 5) * 2.5}% ${(i / 30) * 100}%`);
  for (let i = 24; i >= 0; i--) pts.push(`${(i / 24) * 100}% ${100 - jag(i + 11) * 2.2}%`);
  for (let i = 30; i >= 0; i--) pts.push(`${jag(i + 17) * 2.5}% ${(i / 30) * 100}%`);
  return `polygon(${pts.join(",")})`;
})();

export const Visual: React.FC<{ style: Style; src: string; kind: "video" | "image" }> = ({ style, src, kind }) => {
  const frame = useCurrentFrame();
  const v = style.visual;
  const media = (css: React.CSSProperties) =>
    kind === "video" ? (
      <Video name="Latar" src={staticFile(src)} muted style={{ width: "100%", height: "100%", objectFit: "cover", ...css }} />
    ) : (
      <Img src={staticFile(src)} style={{ width: "100%", height: "100%", objectFit: "cover", ...css }} />
    );

  if (v.frame === "kelir") {
    // siluet di atas layar kelir: gambar digelapkan lalu "multiply" dengan cahaya lampu di belakangnya
    return (
      <div style={{ position: "absolute", left: 90, right: 90, top: 470, height: 860, mixBlendMode: v.blend as React.CSSProperties["mixBlendMode"] }}>
        {media({ filter: v.filter, objectFit: "contain" })}
      </div>
    );
  }
  if (v.frame === "torn") {
    // guntingan foto koran: hitam-putih, titik halftone, tepi sobek, sedikit miring dengan bayangan
    return (
      <div style={{ position: "absolute", left: 110, right: 110, top: 500, height: 820, rotate: "-2deg", filter: "drop-shadow(0 18px 22px rgba(0,0,0,0.35))" }}>
        <div style={{ position: "absolute", inset: 0, clipPath: tornClip, background: style.palette.strip ?? "#fff", padding: 18 }}>
          <div style={{ position: "relative", width: "100%", height: "100%", overflow: "hidden" }}>
            {media({ filter: v.filter })}
            {v.halftone && (
              <div
                style={{
                  position: "absolute",
                  inset: 0,
                  backgroundImage: "radial-gradient(circle, rgba(0,0,0,0.55) 1.6px, transparent 2.1px)",
                  backgroundSize: "7px 7px",
                  mixBlendMode: "multiply",
                }}
              />
            )}
          </div>
        </div>
      </div>
    );
  }
  if (v.frame === "fade") {
    // gambar memudar ke gelap di tepinya, dengan getaran halus seperti rekaman lama
    const jx = (rand(frame * 2) - 0.5) * 4;
    const jy = (rand(frame * 2 + 1) - 0.5) * 4;
    const mask = "radial-gradient(ellipse 58% 46% at 50% 50%, #000 55%, transparent 100%)";
    return (
      <div style={{ position: "absolute", left: 0, right: 0, top: 420, height: 1100, translate: `${jx}px ${jy}px`, maskImage: mask, WebkitMaskImage: mask }}>
        {media({ filter: v.filter })}
      </div>
    );
  }
  if (v.frame === "sticker") {
    // stiker: bingkai putih tebal, sudut membulat, miring dan bergoyang pelan
    return (
      <div
        style={{
          position: "absolute",
          left: 140,
          right: 140,
          top: 470,
          height: 780,
          padding: 22,
          background: "#FFFFFF",
          borderRadius: 64,
          boxShadow: `0 22px 0 ${style.palette.ink}22, 0 30px 50px rgba(0,0,0,0.18)`,
          rotate: `${-3 + Math.sin(frame / 22) * 2}deg`,
          translate: `0px ${Math.sin(frame / 15) * 10}px`,
        }}
      >
        <div style={{ width: "100%", height: "100%", borderRadius: 44, overflow: "hidden" }}>{media({ filter: v.filter })}</div>
      </div>
    );
  }
  return <AbsoluteFill>{media({ filter: v.filter })}</AbsoluteFill>;
};

// ---------------------------------------------------------------- subtitle
export type Token = { text: string; fromMs: number; toMs: number };

export const Caption: React.FC<{ style: Style; tokens: Token[]; nowMs: number; index: number; settled?: boolean }> = ({
  style,
  tokens,
  nowMs,
  index,
  settled,
}) => {
  // settled: tampilkan keadaan akhir animasi masuk (untuk gambar pratinjau)
  const frame = settled ? 60 : useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = style.subtitle;
  const p = style.palette;
  const enter =
    s.entrance === "flicker"
      ? { opacity: interpolate(frame % 4 < 2 ? frame : frame - 1, [0, 3, 4, 6, 8], [0, 0.7, 0.3, 0.9, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) }
      : s.entrance === "stamp"
        ? {
            scale: interpolate(frame, [0, 0.12 * fps], [1.35, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.2, 0.9, 0.3, 1.2) }),
            rotate: `${index % 2 ? 1.6 : -1.6}deg`,
          }
        : s.entrance === "creep"
          ? {
              // muncul samar dari kabur, lalu terus bergetar halus
              opacity: interpolate(frame, [0, 0.45 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
              filter: `blur(${interpolate(frame, [0, 0.45 * fps], [10, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })}px)`,
              translate: `${(rand(frame * 3) - 0.5) * 3}px ${(rand(frame * 3 + 1) - 0.5) * 3}px`,
            }
          : s.entrance === "bounce"
            ? {
                // memantul masuk lalu bergoyang lembut
                scale: interpolate(frame, [0, 0.3 * fps], [0.3, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.34, 1.9, 0.6, 1) }),
                rotate: `${Math.sin(frame / 10 + index) * 2}deg`,
              }
            : { scale: interpolate(frame, [0, 0.2 * fps], [0.85, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.34, 1.56, 0.64, 1) }) };

  const bubbleColors = [p.accent, p.muted, "#FFB547", "#4CD3A5"];
  const box: React.CSSProperties =
    s.box === "bubble"
      ? {
          background: p.strip ?? "#fff",
          color: p.ink,
          padding: "8px 30px",
          borderRadius: 40,
          border: `7px solid ${bubbleColors[index % bubbleColors.length]}`,
          boxDecorationBreak: "clone",
          WebkitBoxDecorationBreak: "clone",
          boxShadow: `0 10px 0 ${p.ink}`,
        }
      : s.box === "strip"
      ? { background: p.strip ?? "#fff", color: p.ink, padding: "6px 18px", boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone", boxShadow: "0 6px 14px rgba(0,0,0,0.25)" }
      : s.box === "glow"
        ? { color: p.ink, textShadow: `0 0 18px ${p.accent}, 0 0 42px ${p.accent}88, 0 4px 10px #000` }
        : { color: p.ink, textShadow: "0 6px 24px rgba(0,0,0,0.45)" };

  return (
    <div
      style={{
        position: "absolute",
        left: 80,
        right: 80,
        bottom: s.position,
        textAlign: "center",
        fontFamily: style.font.family,
        fontWeight: style.font.weight,
        fontSize: s.size,
        lineHeight: s.box === "strip" ? 1.5 : s.box === "bubble" ? 1.7 : 1.15,
        letterSpacing: s.letterSpacing,
        textTransform: s.case === "upper" ? "uppercase" : "none",
        ...enter,
      }}
    >
      <span style={box}>
        {tokens.map((t, i) => {
          const active = nowMs >= t.fromMs && nowMs < t.toMs;
          const hl: React.CSSProperties =
            s.box === "bubble"
              ? active ? { color: bubbleColors[index % bubbleColors.length] } : {}
              : s.entrance === "creep"
                ? active ? { color: p.accent, textShadow: `0 0 18px ${p.accent}, 0 0 4px ${p.accent}` } : {}
                : s.box === "strip"
              ? active ? { color: p.accent, textDecoration: `6px underline ${p.accent}`, textUnderlineOffset: 8 } : {}
              : { color: active ? p.accent : undefined };
          return (
            <span key={i} style={{ whiteSpace: "pre-wrap", ...hl }}>
              {t.text}
            </span>
          );
        })}
      </span>
    </div>
  );
};

export const StyledFrame: React.FC<{ style: Style; title?: string; children?: React.ReactNode }> = ({ style, title, children }) => (
  <AbsoluteFill>
    <Backdrop style={style} title={title} />
    {children}
    <Overlays style={style} />
  </AbsoluteFill>
);
