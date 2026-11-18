import { Easing, Interactive, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { fontFamily } from "./fonts";
import { Icon } from "./icons";

const OutroSceneInner: React.FC<{ slogan: string; cta: string; note: string }> = ({ slogan, cta, note }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <div style={{ fontFamily, width: "100%", padding: "0 90px", display: "flex", flexDirection: "column", alignItems: "center", gap: 60 }}>
      <Interactive.Div
        name="Outro logo"
        style={{
          display: "flex",
          alignItems: "center",
          gap: 28,
          scale: interpolate(frame, [0, 0.6 * fps], [0.6, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 14 }),
            output: "perceptual-scale",
          }),
          opacity: interpolate(frame, [0, 0.3 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
        }}
      >
        <div style={{ width: 150, height: 150, borderRadius: 44, backgroundColor: "#FFFFFF", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Icon name="feather" size={100} color="#2563EB" />
        </div>
        <div style={{ color: "#FFFFFF", fontSize: 84, fontWeight: 800, letterSpacing: -2 }}>NotesByAdriy</div>
      </Interactive.Div>
      <Interactive.Div
        name="Slogan"
        style={{
          color: "#FFFFFF",
          fontSize: 80,
          fontWeight: 800,
          lineHeight: 1.1,
          textAlign: "center",
          letterSpacing: -2,
          whiteSpace: "pre-line",
          opacity: interpolate(frame, [0.4 * fps, 0.9 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          translate: interpolate(frame, [0.4 * fps, 0.9 * fps], ["0px 50px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {slogan}
      </Interactive.Div>
      <Interactive.Div
        name="CTA button"
        style={{
          marginTop: 30,
          padding: "40px 80px",
          borderRadius: 999,
          backgroundColor: "#FFFFFF",
          color: "#2563EB",
          fontSize: 56,
          fontWeight: 800,
          boxShadow: "0 30px 60px rgba(11, 42, 107, 0.35)",
          scale: interpolate(frame, [1 * fps, 1.5 * fps, 2.5 * fps, 3 * fps, 3.5 * fps], [0, 1, 1, 1.06, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.34, 1.56, 0.64, 1),
          }),
        }}
      >
        {cta}
      </Interactive.Div>
      <Interactive.Div
        name="Outro note"
        style={{
          color: "#DCE8FD",
          fontSize: 46,
          fontWeight: 500,
          textAlign: "center",
          opacity: interpolate(frame, [1.4 * fps, 1.9 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
        }}
      >
        {note}
      </Interactive.Div>
    </div>
  );
};

export const OutroScene = Interactive.create({
  Component: OutroSceneInner,
  layout: "absolute-fill",
  schema: {
    ...Interactive.backgroundSchema,
    slogan: { type: "string", default: "Your Knowledge.\nOne Intelligent Space.", description: "Slogan" },
    cta: { type: "string", default: "Unduh Gratis", description: "Tombol ajakan" },
    note: { type: "string", default: "Offline-first · Data tetap di HP kamu", description: "Catatan kecil" },
  },
});
