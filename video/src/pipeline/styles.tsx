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
    kind: "gradient" | "wayang" | "koran" | "horor" | "anak" | "tekno" | "olahraga" | "religi" | "kuliner" | "travel" | "bisnis" | "sains";
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
  visual: { filter: string; blend: string; frame: "none" | "kelir" | "torn" | "fade" | "sticker" | "hud" | "slant" | "arch" | "plate" | "postcard" | "card" | "blueprint"; halftone?: boolean };
  overlays: ("grain" | "vignette" | "paper" | "fog" | "flicker" | "scanlines")[];
  subtitle: {
    size: number;
    case: "none" | "upper";
    box: "none" | "glow" | "strip" | "bubble" | "terminal" | "bar" | "tag" | "ticket" | "lowerthird" | "note";
    position: number;
    entrance: "pop" | "flicker" | "stamp" | "creep" | "bounce" | "glitch" | "slam" | "fade" | "slide" | "wipe" | "type";
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
  ["Orbitron", "fonts/Orbitron-latin.woff2", "400 900"],
  ["JetBrains Mono", "fonts/JetBrainsMono-latin.woff2", "100 800"],
  ["Anton", "fonts/Anton-latin.woff2", "400"],
  ["Amiri", "fonts/Amiri-latin.woff2", "700"],
  ["Lora", "fonts/Lora-latin.woff2", "400 700"],
  ["Pacifico", "fonts/Pacifico-latin.woff2", "400"],
  ["Poppins", "fonts/Poppins-latin.woff2", "800"],
  ["Caveat", "fonts/Caveat-latin.woff2", "400 700"],
  ["Montserrat", "fonts/Montserrat-latin.woff2", "100 900"],
  ["Inter", "fonts/Inter-latin.woff2", "100 900"],
  ["Space Grotesk", "fonts/SpaceGrotesk-latin.woff2", "300 700"],
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
  const tf = style.titleFont?.family ?? style.font.family; // font judul (bisa beda dari subtitle)
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
  if (bg.kind === "tekno") {
    // grid neon dalam perspektif yang bergerak maju, garis sirkuit, cahaya ungu-biru
    return (
      <AbsoluteFill style={{ background: `radial-gradient(ellipse at 50% 20%, ${p.bg2} 0%, ${p.bg} 70%)` }}>
        <div style={{ position: "absolute", left: -540, right: -540, bottom: 0, height: 900, transform: "perspective(700px) rotateX(58deg)", transformOrigin: "50% 100%" }}>
          <div
            style={{
              position: "absolute",
              inset: 0,
              backgroundImage: `linear-gradient(${p.accent}AA 3px, transparent 3px), linear-gradient(90deg, ${p.accent}AA 3px, transparent 3px)`,
              backgroundSize: "90px 90px",
              backgroundPosition: `0px ${(frame * 3) % 90}px`,
              maskImage: "linear-gradient(to top, #000 30%, transparent 100%)",
              WebkitMaskImage: "linear-gradient(to top, #000 30%, transparent 100%)",
            }}
          />
        </div>
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0, opacity: 0.35 }}>
          {[0, 1, 2, 3, 4].map((i) => {
            const y = 160 + i * 60;
            const len = 260 + rand(i) * 300;
            const dash = (frame * 6 + i * 80) % 900;
            return (
              <g key={i} stroke={i % 2 ? p.muted : p.accent} strokeWidth="3" fill="none">
                <path d={`M0 ${y} H${len} L${len + 40} ${y + 40} H1080`} strokeDasharray="60 840" strokeDashoffset={-dash} />
                <circle cx={len} cy={y} r="6" fill={i % 2 ? p.muted : p.accent} />
              </g>
            );
          })}
        </svg>
        {title && (
          <div style={{ position: "absolute", top: 120, left: 70, right: 70, fontFamily: tf, fontWeight: 900, fontSize: 70, lineHeight: 1.1, color: p.ink, letterSpacing: 2, textTransform: "uppercase", textShadow: `0 0 24px ${p.accent}` }}>
            {title}
            <div style={{ marginTop: 18, height: 6, width: 220, background: `linear-gradient(90deg, ${p.accent}, ${p.muted})` }} />
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "olahraga") {
    // garis diagonal yang bergeser cepat + garis kecepatan
    return (
      <AbsoluteFill style={{ background: `linear-gradient(135deg, ${p.bg} 0%, ${p.bg2} 100%)`, overflow: "hidden" }}>
        <AbsoluteFill
          style={{
            backgroundImage: `repeating-linear-gradient(-60deg, transparent 0px, transparent 70px, ${p.accent}22 70px, ${p.accent}22 110px)`,
            backgroundPosition: `${(frame * 14) % 400}px 0px`,
          }}
        />
        {Array.from({ length: 14 }, (_, i) => {
          const y = rand(i * 4) * 1920;
          const w = 200 + rand(i * 9) * 500;
          const x = ((rand(i * 2) * 1600 + frame * (30 + rand(i) * 40)) % 2200) - 600;
          return <div key={i} style={{ position: "absolute", top: y, left: x, width: w, height: 4 + rand(i * 6) * 6, background: i % 3 ? "#FFFFFF33" : `${p.accent}AA`, rotate: "-8deg" }} />;
        })}
        <div style={{ position: "absolute", left: -80, right: -80, top: 0, height: 26, background: p.muted, rotate: "-3deg" }} />
        {title && (
          <div style={{ position: "absolute", top: 150, left: 60, right: 60, fontFamily: tf, fontSize: 120, lineHeight: 0.95, color: p.ink, textTransform: "uppercase", fontStyle: "italic", rotate: "-4deg", textShadow: `8px 8px 0 ${p.muted}` }}>
            {title}
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "religi") {
    // gradasi senja, pola bintang bersegi delapan yang samar, cahaya lembut yang perlahan berpendar
    const glow = 0.85 + Math.sin(frame / 45) * 0.15;
    return (
      <AbsoluteFill style={{ background: `linear-gradient(180deg, ${p.bg} 0%, #16403A 45%, ${p.bg2} 100%)` }}>
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0, opacity: 0.16 }}>
          <defs>
            <pattern id="bintang8" width="120" height="120" patternUnits="userSpaceOnUse">
              <g fill="none" stroke={p.accent} strokeWidth="2">
                <rect x="30" y="30" width="60" height="60" />
                <rect x="30" y="30" width="60" height="60" transform="rotate(45 60 60)" />
                <circle cx="60" cy="60" r="10" />
                <path d="M0 60 H30 M90 60 H120 M60 0 V30 M60 90 V120" />
              </g>
            </pattern>
          </defs>
          <rect width="1080" height="1920" fill="url(#bintang8)" />
        </svg>
        <AbsoluteFill style={{ background: `radial-gradient(ellipse 70% 40% at 50% 30%, ${p.accent}55 0%, transparent 70%)`, opacity: glow }} />
        {title && (
          <div style={{ position: "absolute", top: 170, left: 80, right: 80, textAlign: "center", fontFamily: tf, fontWeight: 700, fontSize: 84, lineHeight: 1.15, color: p.accent, textShadow: `0 0 30px ${p.accent}66` }}>
            {title}
            <div style={{ margin: "22px auto 0", width: 260, height: 2, background: `linear-gradient(90deg, transparent, ${p.accent}, transparent)` }} />
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "kuliner") {
    // taplak kotak-kotak samar, uap yang mengepul pelan dari tengah
    return (
      <AbsoluteFill style={{ background: `radial-gradient(ellipse at 50% 45%, ${p.bg} 0%, ${p.bg2} 100%)` }}>
        <AbsoluteFill
          style={{
            backgroundImage: `linear-gradient(90deg, ${p.accent}14 50%, transparent 50%), linear-gradient(${p.accent}14 50%, transparent 50%)`,
            backgroundSize: "120px 120px",
          }}
        />
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
          {[0, 1, 2].map((i) => {
            const t = (frame / 90 + i / 3) % 1;
            const x = 440 + i * 100;
            const y = 560 - t * 240;
            return (
              <path
                key={i}
                d={`M${x} ${y + 200} C${x - 40} ${y + 140}, ${x + 40} ${y + 80}, ${x} ${y} S${x - 30} ${y - 80}, ${x} ${y - 120}`}
                stroke="#FFFFFF"
                strokeWidth="14"
                strokeLinecap="round"
                fill="none"
                opacity={Math.sin(t * Math.PI) * 0.55}
              />
            );
          })}
        </svg>
        {title && (
          <div style={{ position: "absolute", top: 150, left: 60, right: 60, textAlign: "center", fontFamily: tf, fontSize: 104, lineHeight: 1.15, color: p.accent, textShadow: `0 6px 0 ${p.ink}22` }}>
            {title}
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "travel") {
    // peta: garis kontur, rute putus-putus yang tergambar, pesawat kecil, kompas
    const progress = (frame % 180) / 180;
    const route = "M120 1720 C300 1500 220 1300 460 1260 S820 1120 760 900 S920 600 980 520";
    const total = 1700;
    return (
      <AbsoluteFill style={{ background: `linear-gradient(180deg, ${p.bg} 0%, #CDEBF7 45%, ${p.bg2} 100%)` }}>
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <path key={i} d={`M-50 ${1300 + i * 90} C300 ${1240 + i * 90}, 700 ${1380 + i * 90}, 1130 ${1290 + i * 90}`} stroke={p.muted} strokeOpacity="0.18" strokeWidth="3" fill="none" />
          ))}
          {/* rute putus-putus samar, lalu garis rute yang tergambar sedikit demi sedikit di atasnya */}
          <path d={route} stroke={p.ink} strokeOpacity="0.25" strokeWidth="6" strokeDasharray="22 18" fill="none" strokeLinecap="round" />
          <path d={route} stroke={p.accent} strokeWidth="8" fill="none" strokeLinecap="round" strokeDasharray={total} strokeDashoffset={total * (1 - progress)} />
          <circle cx="120" cy="1720" r="16" fill={p.accent} />
          <circle cx="980" cy="520" r="16" fill={p.ink} />
          <g transform="translate(930 1640)">
            <circle r="70" fill="none" stroke={p.ink} strokeOpacity="0.5" strokeWidth="3" />
            <path d="M0 -60 L14 0 L0 60 L-14 0 Z" fill={p.ink} fillOpacity="0.5" />
            <path d="M0 -60 L14 0 L-14 0 Z" fill={p.accent} />
            <text y="-78" textAnchor="middle" fontSize="28" fill={p.ink} fillOpacity="0.6" fontFamily="Poppins">U</text>
          </g>
        </svg>
        {title && (
          <div style={{ position: "absolute", top: 140, left: 60, right: 60, textAlign: "center", fontFamily: tf, fontWeight: 700, fontSize: 110, lineHeight: 1.0, color: p.ink, rotate: "-3deg" }}>
            {title}
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "bisnis") {
    // grafik batang yang tumbuh, garis tren naik, ticker angka di bawah
    const bars = [0.35, 0.5, 0.42, 0.62, 0.58, 0.78, 0.9];
    const grow = interpolate(frame, [0, 1.2 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.16, 1, 0.3, 1) });
    const ticker = "IHSG +1,24%   USD/IDR 15.620   EMAS +0,8%   BTC +2,1%   OMZET +38%   ";
    return (
      <AbsoluteFill style={{ background: `linear-gradient(160deg, ${p.bg} 0%, ${p.bg2} 100%)` }}>
        <AbsoluteFill style={{ backgroundImage: "linear-gradient(#FFFFFF0D 2px, transparent 2px)", backgroundSize: "100% 120px" }} />
        <div style={{ position: "absolute", left: 80, right: 80, bottom: 120, height: 520, display: "flex", alignItems: "flex-end", gap: 26, opacity: 0.22 }}>
          {bars.map((h, i) => (
            <div key={i} style={{ flex: 1, height: `${h * 100 * grow}%`, background: i === bars.length - 1 ? p.accent : "#FFFFFF", borderRadius: "10px 10px 0 0" }} />
          ))}
        </div>
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0, opacity: 0.5 }}>
          <path d="M80 1650 L250 1560 L420 1600 L590 1450 L760 1480 L930 1300" stroke={p.accent} strokeWidth="6" fill="none" strokeDasharray="1200" strokeDashoffset={1200 * (1 - grow)} />
        </svg>
        <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 70, background: "#00000055", overflow: "hidden", display: "flex", alignItems: "center" }}>
          <div style={{ whiteSpace: "nowrap", fontFamily: "Inter", fontWeight: 700, fontSize: 30, color: p.accent, translate: `${-((frame * 4) % 1200)}px 0px` }}>
            {ticker.repeat(4)}
          </div>
        </div>
        {title && (
          <div style={{ position: "absolute", top: 150, left: 80, right: 80, fontFamily: tf, fontWeight: 800, fontSize: 82, lineHeight: 1.08, color: p.ink, letterSpacing: -1.5 }}>
            {title}
            <div style={{ marginTop: 22, height: 8, width: 160, background: p.accent, borderRadius: 4 }} />
          </div>
        )}
      </AbsoluteFill>
    );
  }
  if (bg.kind === "sains") {
    // cetak biru: grid tipis, atom dengan elektron yang mengorbit, rumus samar
    const t = frame / 30;
    return (
      <AbsoluteFill style={{ background: `radial-gradient(ellipse at 50% 35%, ${p.bg2} 0%, ${p.bg} 100%)` }}>
        <AbsoluteFill
          style={{
            backgroundImage: "linear-gradient(#FFFFFF1A 1px, transparent 1px), linear-gradient(90deg, #FFFFFF1A 1px, transparent 1px), linear-gradient(#FFFFFF0D 1px, transparent 1px), linear-gradient(90deg, #FFFFFF0D 1px, transparent 1px)",
            backgroundSize: "120px 120px, 120px 120px, 24px 24px, 24px 24px",
          }}
        />
        <svg width="1080" height="1920" style={{ position: "absolute", inset: 0 }}>
          <g transform="translate(880 1640)" opacity="0.6">
            {[0, 60, 120].map((r, i) => (
              <g key={i} transform={`rotate(${r})`}>
                <ellipse rx="130" ry="44" fill="none" stroke={p.muted} strokeWidth="3" />
                <circle cx={130 * Math.cos(t * (1.6 + i * 0.4) + i)} cy={44 * Math.sin(t * (1.6 + i * 0.4) + i)} r="10" fill={p.accent} />
              </g>
            ))}
            <circle r="18" fill={p.accent} />
          </g>
          <text x="70" y="1700" fontFamily="Space Grotesk" fontSize="40" fill="#FFFFFF" fillOpacity="0.18">E = mc²</text>
          <text x="70" y="1780" fontFamily="Space Grotesk" fontSize="34" fill="#FFFFFF" fillOpacity="0.14">λ = c / f</text>
          <text x="70" y="1850" fontFamily="Space Grotesk" fontSize="34" fill="#FFFFFF" fillOpacity="0.12">F = G·m₁m₂ / r²</text>
        </svg>
        {title && (
          <div style={{ position: "absolute", top: 150, left: 80, right: 80, fontFamily: tf, fontWeight: 700, fontSize: 80, lineHeight: 1.08, color: p.ink, letterSpacing: -1 }}>
            <div style={{ fontSize: 28, letterSpacing: 6, color: p.accent, marginBottom: 14 }}>FAKTA SAINS · NO. 01</div>
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
  if (v.frame === "hud") {
    // bingkai HUD: sudut siku menyala, garis pemindai turun-naik, label kecil
    const c = style.palette.accent;
    const scan = (Math.sin(frame / 25) * 0.5 + 0.5) * 100;
    const corner = (pos: React.CSSProperties, r: string) => (
      <div style={{ position: "absolute", width: 70, height: 70, borderColor: c, borderStyle: "solid", borderWidth: 0, ...pos, [r]: 6 } as React.CSSProperties} />
    );
    return (
      <div style={{ position: "absolute", left: 90, right: 90, top: 400, height: 820 }}>
        <div style={{ position: "absolute", inset: 18, overflow: "hidden", boxShadow: `0 0 40px ${c}44` }}>
          {media({ filter: v.filter })}
          <div style={{ position: "absolute", left: 0, right: 0, top: `${scan}%`, height: 3, background: c, boxShadow: `0 0 18px ${c}` }} />
          <AbsoluteFill style={{ background: `linear-gradient(180deg, ${c}10, transparent 30%, transparent 70%, ${c}18)` }} />
        </div>
        {corner({ left: 0, top: 0, borderLeftWidth: 6 }, "borderTopWidth")}
        {corner({ right: 0, top: 0, borderRightWidth: 6 }, "borderTopWidth")}
        {corner({ left: 0, bottom: 0, borderLeftWidth: 6 }, "borderBottomWidth")}
        {corner({ right: 0, bottom: 0, borderRightWidth: 6 }, "borderBottomWidth")}
        <div style={{ position: "absolute", top: -44, right: 0, fontFamily: "JetBrains Mono", fontSize: 26, color: c, letterSpacing: 2 }}>● REC {String(Math.floor(frame / 30)).padStart(2, "0")}:{String(frame % 30).padStart(2, "0")}</div>
      </div>
    );
  }
  if (v.frame === "slant") {
    // potongan miring seperti grafik siaran olahraga
    const clip = "polygon(8% 0, 100% 0, 92% 100%, 0 100%)";
    return (
      <div style={{ position: "absolute", left: 40, right: 40, top: 520, height: 800, rotate: "-3deg" }}>
        <div style={{ position: "absolute", inset: 0, clipPath: clip, background: style.palette.accent, translate: "18px 18px" }} />
        <div style={{ position: "absolute", inset: 0, clipPath: clip, overflow: "hidden" }}>{media({ filter: v.filter })}</div>
      </div>
    );
  }
  if (v.frame === "arch") {
    // bingkai lengkung dengan garis emas tipis
    const radius = "430px 430px 28px 28px";
    return (
      <div style={{ position: "absolute", left: 160, right: 160, top: 520, height: 840 }}>
        <div style={{ position: "absolute", inset: -14, borderRadius: radius, border: `3px solid ${style.palette.accent}`, opacity: 0.8 }} />
        <div style={{ position: "absolute", inset: 0, borderRadius: radius, overflow: "hidden", boxShadow: "0 30px 60px rgba(0,0,0,0.35)" }}>{media({ filter: v.filter })}</div>
      </div>
    );
  }
  if (v.frame === "plate") {
    // piring bundar: pinggiran piring, bayangan lembut, berputar sangat pelan
    return (
      <div style={{ position: "absolute", left: 120, top: 470, width: 840, height: 840, borderRadius: "50%", background: "#FFFFFF", boxShadow: "0 40px 70px rgba(59,36,22,0.28), inset 0 0 0 26px #F4EEE6", padding: 70 }}>
        <div style={{ width: "100%", height: "100%", borderRadius: "50%", overflow: "hidden", rotate: `${frame * 0.05}deg`, boxShadow: "inset 0 0 30px rgba(0,0,0,0.25)" }}>{media({ filter: v.filter })}</div>
      </div>
    );
  }
  if (v.frame === "postcard") {
    // kartu pos: bingkai putih, perangko bergerigi, cap pos melingkar
    const p = style.palette;
    return (
      <div style={{ position: "absolute", left: 100, right: 100, top: 520, height: 760, background: p.strip ?? "#fff", padding: 26, rotate: "2deg", boxShadow: "0 26px 40px rgba(29,53,87,0.3)" }}>
        <div style={{ width: "100%", height: "100%", overflow: "hidden" }}>{media({ filter: v.filter })}</div>
        <div style={{ position: "absolute", top: -30, right: 40, width: 130, height: 156, background: p.accent, border: `8px dotted ${p.strip ?? "#fff"}`, rotate: "6deg", display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontFamily: "Caveat", fontWeight: 700, fontSize: 40 }}>ID</div>
        <svg width="220" height="220" viewBox="0 0 220 220" style={{ position: "absolute", top: -10, right: 120, opacity: 0.55, rotate: "-14deg" }}>
          <circle cx="110" cy="110" r="86" fill="none" stroke={p.ink} strokeWidth="5" />
          <circle cx="110" cy="110" r="64" fill="none" stroke={p.ink} strokeWidth="3" />
          <text x="110" y="102" textAnchor="middle" fontFamily="Poppins" fontSize="24" fill={p.ink}>POS</text>
          <text x="110" y="132" textAnchor="middle" fontFamily="Poppins" fontSize="20" fill={p.ink}>NUSANTARA</text>
        </svg>
      </div>
    );
  }
  if (v.frame === "card") {
    // kartu seperti slide presentasi: bilah judul kecil dengan tiga titik
    const p = style.palette;
    return (
      <div style={{ position: "absolute", left: 80, right: 80, top: 480, height: 800, background: "#FFFFFF", borderRadius: 32, overflow: "hidden", boxShadow: "0 30px 60px rgba(0,0,0,0.35)" }}>
        <div style={{ height: 64, background: "#F1F4F9", display: "flex", alignItems: "center", gap: 14, padding: "0 26px" }}>
          {[p.accent, p.muted, "#C5CEDB"].map((c) => (
            <div key={c} style={{ width: 18, height: 18, borderRadius: 9, background: c }} />
          ))}
          <div style={{ marginLeft: 16, height: 14, width: 260, borderRadius: 7, background: "#D8DEE8" }} />
        </div>
        <div style={{ position: "absolute", top: 64, left: 0, right: 0, bottom: 0 }}>{media({ filter: v.filter })}</div>
      </div>
    );
  }
  if (v.frame === "blueprint") {
    // cetak biru: garis tepi tipis, garis dimensi, tanda silang di sudut, label FIG
    const p = style.palette;
    return (
      <div style={{ position: "absolute", left: 120, right: 120, top: 500, height: 780 }}>
        <div style={{ position: "absolute", inset: 0, border: `3px solid ${p.ink}`, padding: 14 }}>
          <div style={{ width: "100%", height: "100%", overflow: "hidden" }}>{media({ filter: v.filter })}</div>
        </div>
        <svg width="840" height="900" style={{ position: "absolute", left: 0, top: -60, overflow: "visible" }}>
          <g stroke={p.muted} strokeWidth="2" fill={p.muted} fontFamily="Space Grotesk" fontSize="24">
            <path d="M0 30 H840 M0 18 V42 M840 18 V42" />
            <text x="420" y="22" textAnchor="middle" stroke="none">840 px</text>
            <path d="M-30 60 V840 M-42 60 H-18 M-42 840 H-18" />
          </g>
          {[[0, 60], [840, 60], [0, 840], [840, 840]].map(([x, y], i) => (
            <path key={i} d={`M${x - 16} ${y} H${x + 16} M${x} ${y - 16} V${y + 16}`} stroke={p.accent} strokeWidth="3" />
          ))}
        </svg>
        <div style={{ position: "absolute", bottom: -50, left: 0, fontFamily: "Space Grotesk", fontSize: 26, letterSpacing: 4, color: p.muted }}>FIG. 1</div>
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
            : s.entrance === "glitch"
              ? {
                  // 6 frame pertama: bergeser acak dengan bayangan warna terpisah (RGB split), lalu diam
                  translate: frame < 6 ? `${(rand(frame * 5) - 0.5) * 40}px ${(rand(frame * 5 + 2) - 0.5) * 10}px` : "0px 0px",
                  opacity: frame < 6 && rand(frame * 7) > 0.7 ? 0.4 : 1,
                  textShadow: frame < 6 ? `-6px 0 ${p.accent}, 6px 0 ${p.muted}` : undefined,
                }
              : s.entrance === "slam"
                ? {
                    // menghantam dari besar ke ukuran normal
                    scale: interpolate(frame, [0, 0.12 * fps], [2.2, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.2, 0.8, 0.3, 1) }),
                    opacity: interpolate(frame, [0, 0.05 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
                    rotate: "-3deg",
                  }
                : s.entrance === "fade"
                  ? {
                      opacity: interpolate(frame, [0, 0.6 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
                      translate: `0px ${interpolate(frame, [0, 0.6 * fps], [24, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.16, 1, 0.3, 1) })}px`,
                    }
                  : s.entrance === "slide"
                    ? {
                        translate: `${interpolate(frame, [0, 0.3 * fps], [-1100, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.16, 1, 0.3, 1) })}px 0px`,
                        rotate: `${index % 2 ? 1.5 : -1.5}deg`,
                      }
                    : s.entrance === "wipe"
                      ? {
                          // terbuka dari kiri ke kanan
                          clipPath: `inset(0 ${interpolate(frame, [0, 0.35 * fps], [100, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.65, 0, 0.35, 1) })}% 0 0)`,
                        }
                      : s.entrance === "type"
                        ? {} // kata muncul satu per satu (diatur per kata di bawah)
                        : { scale: interpolate(frame, [0, 0.2 * fps], [0.85, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.34, 1.56, 0.64, 1) }) };

  const bubbleColors = [p.accent, p.muted, "#FFB547", "#4CD3A5"];
  const box: React.CSSProperties =
    s.box === "tag"
      ? { background: p.accent, color: "#FFFFFF", padding: "8px 34px", borderRadius: 60, boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone", boxShadow: `0 8px 0 ${p.ink}33` }
      : s.box === "ticket"
        ? { background: p.strip ?? "#fff", color: p.ink, padding: "8px 30px", border: `3px dashed ${p.accent}`, borderRadius: 14, boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone", boxShadow: "0 12px 24px rgba(29,53,87,0.22)" }
        : s.box === "lowerthird"
          ? { background: p.strip ?? "#fff", color: p.bg, padding: "8px 28px", borderLeft: `14px solid ${p.accent}`, boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone", boxShadow: "0 14px 30px rgba(0,0,0,0.3)" }
          : s.box === "note"
            ? { background: `${p.bg}CC`, color: p.ink, padding: "8px 24px", border: `2px dashed ${p.muted}`, boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone" }
            : s.box === "terminal"
      ? { background: `${p.bg}E6`, color: p.ink, padding: "10px 22px", border: `2px solid ${p.accent}`, boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone", boxShadow: `0 0 24px ${p.accent}44` }
      : s.box === "bar"
        ? { background: p.muted, color: p.ink, padding: "4px 28px", boxDecorationBreak: "clone", WebkitBoxDecorationBreak: "clone", boxShadow: `10px 10px 0 ${p.bg}` }
        : s.box === "bubble"
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
        lineHeight: s.box === "strip" ? 1.5 : s.box === "bubble" ? 1.7 : ["terminal", "bar", "tag", "ticket", "lowerthird", "note"].includes(s.box) ? 1.5 : 1.15,
        ...(s.box === "lowerthird" ? { textAlign: "left" as const } : {}),
        letterSpacing: s.letterSpacing,
        textTransform: s.case === "upper" ? "uppercase" : "none",
        ...enter,
      }}
    >
      <span style={box}>
        {tokens.map((t, i) => {
          const active = nowMs >= t.fromMs && nowMs < t.toMs;
          const hl: React.CSSProperties =
            s.box === "tag"
              ? active ? { color: p.ink, background: "#FFFFFF", borderRadius: 12 } : {}
              : s.box === "ticket"
                ? active ? { color: p.accent } : {}
                : s.box === "lowerthird"
                  ? active ? { color: p.accent } : {}
                  : s.box === "note"
                    ? active ? { color: p.accent, borderBottom: `4px solid ${p.accent}` } : {}
                    : s.box === "terminal"
              ? active ? { color: p.bg, background: p.accent } : {}
              : s.box === "bar"
                ? active ? { color: p.accent } : {}
                : s.box === "bubble"
              ? active ? { color: bubbleColors[index % bubbleColors.length] } : {}
              : s.entrance === "creep"
                ? active ? { color: p.accent, textShadow: `0 0 18px ${p.accent}, 0 0 4px ${p.accent}` } : {}
                : s.box === "strip"
              ? active ? { color: p.accent, textDecoration: `6px underline ${p.accent}`, textUnderlineOffset: 8 } : {}
              : { color: active ? p.accent : undefined };
          // "type": kata yang belum diucapkan disembunyikan (tetap memakan tempat, jadi teks tidak bergeser)
          const hidden = s.entrance === "type" && !settled && t.fromMs > nowMs ? { opacity: 0 } : {};
          return (
            <span key={i} style={{ whiteSpace: "pre-wrap", ...hl, ...hidden }}>
              {t.text}
            </span>
          );
        })}
        {s.box === "terminal" && <span style={{ color: p.accent, opacity: frame % 20 < 10 ? 1 : 0 }}>▍</span>}
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
