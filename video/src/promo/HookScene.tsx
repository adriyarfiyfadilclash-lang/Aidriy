import { Easing, Interactive, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { fontFamily } from "./fonts";

const Chip: React.FC<{ label: string; color: string }> = ({ label, color }) => (
  <div
    style={{
      padding: "24px 44px",
      borderRadius: 999,
      backgroundColor: "#1C222C",
      border: `3px solid ${color}`,
      color,
      fontSize: 48,
      fontWeight: 700,
    }}
  >
    {label}
  </div>
);

const HookSceneInner: React.FC<{ headline: string; subline: string }> = ({ headline, subline }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <div style={{ fontFamily, width: "100%", padding: "0 90px", display: "flex", flexDirection: "column", alignItems: "center", gap: 64 }}>
      <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 28, height: 260, alignContent: "center" }}>
        <Interactive.Div
          name="Chip Ide"
          style={{
            translate: interpolate(frame, [0, 0.7 * fps], ["-260px -120px", "0px 0px"], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.spring({ damping: 14 }),
            }),
            rotate: interpolate(frame, [0, 2.5 * fps], ["-12deg", "-4deg"], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
            opacity: interpolate(frame, [0, 0.3 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          }}
        >
          <Chip label="💡 Ide" color="#FFD25E" />
        </Interactive.Div>
        <Interactive.Div
          name="Chip Journal"
          style={{
            translate: interpolate(frame, [0.15 * fps, 0.85 * fps], ["240px -160px", "0px 0px"], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.spring({ damping: 14 }),
            }),
            rotate: interpolate(frame, [0, 2.5 * fps], ["10deg", "5deg"], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
            opacity: interpolate(frame, [0.15 * fps, 0.45 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          }}
        >
          <Chip label="📓 Journal" color="#FF8A8E" />
        </Interactive.Div>
        <Interactive.Div
          name="Chip Berkas"
          style={{
            translate: interpolate(frame, [0.3 * fps, 1 * fps], ["0px 220px", "0px 0px"], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.spring({ damping: 14 }),
            }),
            rotate: interpolate(frame, [0, 2.5 * fps], ["-6deg", "2deg"], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
            opacity: interpolate(frame, [0.3 * fps, 0.6 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          }}
        >
          <Chip label="📎 Berkas" color="#7CA7FF" />
        </Interactive.Div>
      </div>
      <Interactive.Div
        name="Hook headline"
        style={{
          color: "#E8ECF2",
          fontSize: 104,
          fontWeight: 800,
          lineHeight: 1.1,
          textAlign: "center",
          letterSpacing: -2,
          opacity: interpolate(frame, [0.6 * fps, 1.1 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          translate: interpolate(frame, [0.6 * fps, 1.1 * fps], ["0px 50px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {headline}
      </Interactive.Div>
      <Interactive.Div
        name="Hook subline"
        style={{
          color: "#9AA4B2",
          fontSize: 52,
          fontWeight: 500,
          lineHeight: 1.35,
          textAlign: "center",
          opacity: interpolate(frame, [1 * fps, 1.5 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
        }}
      >
        {subline}
      </Interactive.Div>
    </div>
  );
};

export const HookScene = Interactive.create({
  Component: HookSceneInner,
  layout: "absolute-fill",
  schema: {
    ...Interactive.backgroundSchema,
    headline: { type: "string", default: "Catatan di mana‑mana?", description: "Judul" },
    subline: { type: "string", default: "Ide hilang, journal berserakan, berkas tercecer.", description: "Subjudul" },
  },
});
