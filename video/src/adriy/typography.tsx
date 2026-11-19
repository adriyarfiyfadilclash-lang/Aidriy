import { ap, CAPTION_BAND, EASE, SAFE_X, useScene } from "./motion";
import type { Brand, Token } from "./scenes";

// Tipografi kinetik Adriy.project: judul muncul per kata (mask dari bawah), kata kunci diberi warna
// aksen + ukuran lebih besar + stabilo, dan subtitle selalu di pita bawah (CAPTION_BAND) yang tidak
// pernah dipakai konten, jadi tidak menutupi karakter atau informasi.

const norm = (w: string) => w.toLowerCase().replace(/[^\p{L}\p{N}%-]/gu, "");

// ---------------------------------------------------------------- stabilo
export const Marker: React.FC<{ children: React.ReactNode; at: number; brand: Brand; color?: string; ink?: string }> = ({ children, at, brand, color, ink }) => {
  const { frame, fps } = useScene();
  const p = ap(frame, fps, at, 0.45);
  return (
    <span style={{ position: "relative", whiteSpace: "nowrap", color: p > 0.5 ? (ink ?? brand.palette.bg) : undefined, display: "inline-block" }}>
      <span style={{ position: "absolute", left: -10, right: -10, top: "12%", bottom: "4%", background: color ?? brand.palette.accent, borderRadius: 10, transformOrigin: "left center", scale: `${p} 1`, zIndex: -1, rotate: "-1deg" }} />
      {children}
    </span>
  );
};

export const Kicker: React.FC<{ text?: string; at: number; color: string; align?: "left" | "center" | "right" }> = ({ text, at, color, align = "left" }) => {
  const { frame, fps } = useScene();
  const p = ap(frame, fps, at, 0.45);
  if (!text) return null;
  // garis pendek tumbuh dulu, lalu teks menyusul dari samping
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 18, justifyContent: align === "center" ? "center" : align === "right" ? "flex-end" : "flex-start" }}>
      <div style={{ width: 56 * p, height: 6, borderRadius: 3, background: color }} />
      <div style={{ fontFamily: "Plus Jakarta Sans", fontWeight: 800, fontSize: 32, letterSpacing: 5, color, opacity: p, translate: `${(1 - p) * -24}px 0px`, textTransform: "uppercase", clipPath: `inset(0 ${(1 - p) * 100}% 0 0)` }}>{text}</div>
    </div>
  );
};

// Judul kinetik: tiap kata naik dari balik mask berurutan; kata yang ada di `highlight` diberi warna
// aksen, sedikit lebih besar, dan stabilo. `mode="line"` = per kalimat (semua kata bersamaan).
export const KineticTitle: React.FC<{
  text?: string;
  highlight?: string;
  at: number;
  brand: Brand;
  size?: number;
  color: string;
  align?: "left" | "center" | "right";
  mode?: "word" | "line";
  marker?: boolean;
  maxWidth?: number;
}> = ({ text, highlight, at, brand, size = 76, color, align = "left", mode = "word", marker = true, maxWidth }) => {
  const { frame, fps } = useScene();
  if (!text) return null;
  const words = text.split(" ").filter(Boolean);
  const keys = new Set((highlight ?? "").split(" ").map(norm).filter(Boolean));
  // sorot frasa highlight di tempat ia muncul (bukan setiap kata yang kebetulan sama)
  const hw = (highlight ?? "").split(" ").map(norm).filter(Boolean);
  const nw = words.map(norm);
  let at0 = -1;
  for (let i = 0; hw.length && i + hw.length <= nw.length; i++) if (hw.every((h, j) => nw[i + j] === h)) { at0 = i; break; }
  const isKey = (i: number) => (at0 >= 0 ? i >= at0 && i < at0 + hw.length : keys.has(nw[i]) && nw.indexOf(nw[i]) === i);
  const gap = words.length > 8 ? 0.06 : 0.09;
  return (
    <div style={{ fontFamily: brand.typography.family, fontWeight: 800, fontSize: size, lineHeight: 1.08, letterSpacing: -1.5, color, textAlign: align, maxWidth, display: "flex", flexWrap: "wrap", columnGap: size * 0.26, rowGap: size * 0.06, justifyContent: align === "center" ? "center" : align === "right" ? "flex-end" : "flex-start" }}>
      {words.map((w, i) => {
        const t0 = mode === "word" ? at + i * gap : at;
        const p = ap(frame, fps, t0, 0.5, EASE.out);
        const key = isKey(i);
        const inner = (
          <span style={{ display: "inline-block", translate: `0px ${(1 - p) * 105}%`, rotate: `${(1 - p) * 4}deg`, color: key ? (marker ? undefined : brand.palette.accent) : undefined, fontSize: key ? size * 1.14 : size }}>
            {key && marker ? <Marker at={t0 + 0.35} brand={brand}>{w}</Marker> : w}
          </span>
        );
        return (
          <span key={i} style={{ display: "inline-block", overflow: key && marker ? "visible" : "hidden", paddingBottom: size * 0.08, opacity: key && marker ? Math.min(1, p * 1.6) : 1 }}>
            {inner}
          </span>
        );
      })}
    </div>
  );
};

// ---------------------------------------------------------------- subtitle
// Selalu di pita bawah. Kata yang sedang diucapkan berwarna aksen; kata kunci (highlight) ditebalkan
// dengan garis bawah aksen. Kalau isi halaman sama dengan teks yang sudah tampil di layar (judul),
// subtitle disembunyikan supaya tidak dobel.
export const SceneCaption: React.FC<{ tokens: Token[]; nowMs: number; brand: Brand; tone: "dark" | "blue" | "light"; highlight?: string; onScreen?: string }> = ({ tokens, nowMs, brand, tone, highlight, onScreen }) => {
  if (!tokens.length) return null;
  const said = tokens.map((t) => norm(t.text)).filter(Boolean);
  if (onScreen) {
    const shown = new Set(onScreen.split(" ").map(norm));
    if (said.length && said.filter((w) => shown.has(w)).length / said.length >= 0.8) return null;
  }
  const keys = new Set((highlight ?? "").split(" ").map(norm).filter(Boolean));
  const light = tone === "light";
  return (
    <div style={{ position: "absolute", left: SAFE_X, right: SAFE_X, top: CAPTION_BAND.top, height: CAPTION_BAND.bottom - CAPTION_BAND.top, display: "flex", alignItems: "flex-start", justifyContent: "center", textAlign: "center" }}>
      <span
        style={{
          fontFamily: brand.typography.family,
          fontWeight: brand.typography.caption.weight,
          fontSize: brand.typography.caption.size,
          lineHeight: 1.45,
          letterSpacing: -0.5,
          color: light ? brand.palette.bg : "#FFFFFF",
          background: light ? "rgba(255,255,255,0.9)" : "rgba(8,14,32,0.62)",
          padding: "6px 22px",
          borderRadius: 18,
          boxDecorationBreak: "clone",
          WebkitBoxDecorationBreak: "clone",
        }}
      >
        {tokens.map((t, i) => {
          const active = nowMs >= t.fromMs && nowMs < t.toMs;
          const key = keys.has(norm(t.text));
          return (
            <span key={i} style={{ whiteSpace: "pre-wrap", color: active ? brand.palette.accent : undefined, fontWeight: key ? 800 : undefined, textDecoration: key ? `underline ${brand.palette.accent} 5px` : undefined, textUnderlineOffset: 10 }}>
              {t.text}
            </span>
          );
        })}
      </span>
    </div>
  );
};
