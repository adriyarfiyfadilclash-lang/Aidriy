import { Easing, Interactive, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { fontFamily } from "./fonts";
import { Icon } from "./icons";

const IntroSceneInner: React.FC<{ title: string; tagline: string }> = ({ title, tagline }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <div style={{ fontFamily, display: "flex", flexDirection: "column", alignItems: "center", gap: 56 }}>
      <Interactive.Div
        name="Logo"
        style={{
          width: 300,
          height: 300,
          borderRadius: 84,
          backgroundColor: "#FFFFFF",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          boxShadow: "0 40px 80px rgba(11, 42, 107, 0.35)",
          scale: interpolate(frame, [0, 0.8 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 12 }),
            output: "perceptual-scale",
          }),
          rotate: interpolate(frame, [0, 0.8 * fps], ["-25deg", "0deg"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 14 }),
          }),
        }}
      >
        <Icon name="feather" size={200} color="#2563EB" />
      </Interactive.Div>
      <Interactive.Div
        name="App name"
        style={{
          color: "#FFFFFF",
          fontSize: 120,
          fontWeight: 800,
          letterSpacing: -3,
          opacity: interpolate(frame, [0.5 * fps, 1.1 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [0.5 * fps, 1.1 * fps], ["0px 60px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {title}
      </Interactive.Div>
      <Interactive.Div
        name="Tagline"
        style={{
          color: "#DCE8FD",
          fontSize: 60,
          fontWeight: 500,
          opacity: interpolate(frame, [0.9 * fps, 1.5 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [0.9 * fps, 1.5 * fps], ["0px 40px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {tagline}
      </Interactive.Div>
    </div>
  );
};

export const IntroScene = Interactive.create({
  Component: IntroSceneInner,
  layout: "absolute-fill",
  schema: {
    ...Interactive.backgroundSchema,
    title: { type: "string", default: "NotesByAdriy", description: "Nama aplikasi" },
    tagline: { type: "string", default: "Second Brain AI", description: "Tagline" },
  },
});
