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
  palette: { bg: string; bg2: string; ink: string; accent: string; muted: string; strip?: string };
  background: {
    kind: "gradient" | "wayang" | "koran";
    lamp?: { x: number; y: number; flicker: number };
    gunungan?: boolean;
    border?: boolean;
    masthead?: string;
    edition?: string;
    columns?: boolean;
  };
  visual: { filter: string; blend: string; frame: "none" | "kelir" | "torn"; halftone?: boolean };
  overlays: ("grain" | "vignette" | "paper")[];
  subtitle: {
    size: number;
    case: "none" | "upper";
    box: "none" | "glow" | "strip";
    position: number;
    entrance: "pop" | "flicker" | "stamp";
    letterSpacing: number;
  };
};

// Font semua style dimuat sekali di awal (file kecil, tersimpan lokal di public/fonts/).
for (const [family, file, weight] of [
  ["Cinzel", "fonts/Cinzel-latin.woff2", "400 900"],
  ["Playfair Display", "fonts/PlayfairDisplay-latin.woff2", "400 900"],
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

const Overlays: React.FC<{ style: Style }> = ({ style }) => {
  const frame = useCurrentFrame();
  return (
    <>
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
        : { scale: interpolate(frame, [0, 0.2 * fps], [0.85, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.34, 1.56, 0.64, 1) }) };

  const box: React.CSSProperties =
    s.box === "strip"
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
        lineHeight: s.box === "strip" ? 1.5 : 1.15,
        letterSpacing: s.letterSpacing,
        textTransform: s.case === "upper" ? "uppercase" : "none",
        ...enter,
      }}
    >
      <span style={box}>
        {tokens.map((t, i) => {
          const active = nowMs >= t.fromMs && nowMs < t.toMs;
          const hl: React.CSSProperties =
            s.box === "strip"
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
