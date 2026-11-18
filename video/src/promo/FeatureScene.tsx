import { Easing, Interactive, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { fontFamily } from "./fonts";
import { Icon, type IconName } from "./icons";

const FeatureSceneInner: React.FC<{
  step: string;
  title: string;
  description: string;
  icon: IconName;
  accent: string;
  accentSoft: string;
}> = ({ step, title, description, icon, accent, accentSoft }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <div style={{ fontFamily, width: "100%", padding: "0 90px", display: "flex", flexDirection: "column", gap: 44 }}>
      <Interactive.Div
        name="Step label"
        style={{
          color: accent,
          fontSize: 44,
          fontWeight: 800,
          letterSpacing: 6,
          opacity: interpolate(frame, [0, 0.4 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
        }}
      >
        {step}
      </Interactive.Div>
      <Interactive.Div
        name="Feature icon"
        style={{
          width: 240,
          height: 240,
          borderRadius: 64,
          backgroundColor: accentSoft,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          scale: interpolate(frame, [0, 0.6 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 12 }),
            output: "perceptual-scale",
          }),
        }}
      >
        <Icon name={icon} size={140} color={accent} />
      </Interactive.Div>
      <Interactive.Div
        name="Feature title"
        style={{
          color: "#0F141B",
          fontSize: 104,
          fontWeight: 800,
          lineHeight: 1.05,
          letterSpacing: -2,
          opacity: interpolate(frame, [0.25 * fps, 0.75 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          translate: interpolate(frame, [0.25 * fps, 0.75 * fps], ["0px 50px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {title}
      </Interactive.Div>
      <Interactive.Div
        name="Feature description"
        style={{
          color: "#5B6472",
          fontSize: 54,
          fontWeight: 500,
          lineHeight: 1.35,
          opacity: interpolate(frame, [0.55 * fps, 1.05 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          translate: interpolate(frame, [0.55 * fps, 1.05 * fps], ["0px 40px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {description}
      </Interactive.Div>
      <Interactive.Div
        name="Mock card"
        style={{
          marginTop: 40,
          padding: 56,
          borderRadius: 56,
          backgroundColor: "#FFFFFF",
          boxShadow: "0 30px 70px rgba(15, 20, 27, 0.10)",
          display: "flex",
          flexDirection: "column",
          gap: 30,
          opacity: interpolate(frame, [0.8 * fps, 1.2 * fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          translate: interpolate(frame, [0.8 * fps, 1.3 * fps], ["0px 120px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        <div style={{ height: 36, width: "55%", borderRadius: 18, backgroundColor: accent }} />
        <div
          style={{
            height: 26,
            borderRadius: 13,
            backgroundColor: "#E6EAF1",
            width: `${interpolate(frame, [1.1 * fps, 1.7 * fps], [0, 100], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.16, 1, 0.3, 1) })}%`,
          }}
        />
        <div
          style={{
            height: 26,
            borderRadius: 13,
            backgroundColor: "#E6EAF1",
            width: `${interpolate(frame, [1.25 * fps, 1.85 * fps], [0, 88], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.16, 1, 0.3, 1) })}%`,
          }}
        />
        <div
          style={{
            height: 26,
            borderRadius: 13,
            backgroundColor: "#E6EAF1",
            width: `${interpolate(frame, [1.4 * fps, 2 * fps], [0, 64], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: Easing.bezier(0.16, 1, 0.3, 1) })}%`,
          }}
        />
      </Interactive.Div>
    </div>
  );
};

export const FeatureScene = Interactive.create({
  Component: FeatureSceneInner,
  layout: "absolute-fill",
  schema: {
    ...Interactive.backgroundSchema,
    step: { type: "string", default: "FITUR 01", description: "Label" },
    title: { type: "string", default: "Catatan pintar", description: "Judul fitur" },
    description: { type: "string", default: "Markdown, checklist, gambar di antara teks.", description: "Deskripsi" },
    accent: { type: "color", default: "#2563EB", description: "Warna aksen" },
    accentSoft: { type: "color", default: "#E6EFFE", description: "Warna aksen lembut" },
  },
});
