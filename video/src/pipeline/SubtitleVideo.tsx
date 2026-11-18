import { Audio, Video } from "@remotion/media";
import { AbsoluteFill, type CalculateMetadataFunction, Easing, interpolate, Sequence, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { fontFamily } from "../promo/fonts";
import { Icon } from "../promo/icons";

// Data dibuat oleh ../pipeline/pipeline.py di public/media/<media>/:
//   meta.json   durasi, ukuran, judul, ada/tidaknya video
//   pages.json  subtitle per halaman, tiap kata dengan waktu mulai dan selesai (ms)
// Subtitle dibaca dari file, jadi tidak bisa diedit langsung di Studio: ubah transcript.json lalu
// jalankan ulang pipeline dengan --from pages.

type Token = { text: string; fromMs: number; toMs: number };
type Page = { startMs: number; durationMs: number; tokens: Token[] };
type Meta = { duration: number; title: string; hasVideo: boolean; source: string };

export type SubtitleVideoProps = {
  media: string;
  pages: Page[];
  meta: Meta | null;
};

export const calculateSubtitleMetadata: CalculateMetadataFunction<SubtitleVideoProps> = async ({ props }) => {
  const base = `media/${props.media}`;
  const [meta, pages] = await Promise.all([
    fetch(staticFile(`${base}/meta.json`)).then((r) => r.json() as Promise<Meta>),
    fetch(staticFile(`${base}/pages.json`)).then((r) => r.json() as Promise<Page[]>),
  ]);
  return { durationInFrames: Math.ceil(meta.duration * 30), props: { ...props, meta, pages } };
};

const CaptionPage: React.FC<{ page: Page }> = ({ page }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const nowMs = page.startMs + (frame / fps) * 1000;
  return (
    <div
      style={{
        position: "absolute",
        left: 80,
        right: 80,
        bottom: 520,
        textAlign: "center",
        fontFamily,
        fontSize: 92,
        fontWeight: 800,
        lineHeight: 1.15,
        letterSpacing: -1,
        color: "#FFFFFF",
        textShadow: "0 6px 24px rgba(0,0,0,0.45)",
        scale: interpolate(frame, [0, 0.2 * fps], [0.85, 1], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
          easing: Easing.bezier(0.34, 1.56, 0.64, 1),
        }),
      }}
    >
      {page.tokens.map((t, i) => {
        const active = nowMs >= t.fromMs && nowMs < t.toMs;
        return (
          <span key={i} style={{ color: active ? "#FFD25E" : "#FFFFFF", whiteSpace: "pre" }}>
            {t.text}
          </span>
        );
      })}
    </div>
  );
};

export const SubtitleVideo: React.FC<SubtitleVideoProps> = ({ media, pages, meta }) => {
  const { fps } = useVideoConfig();
  if (!meta) return null;
  const base = `media/${media}`;
  return (
    <AbsoluteFill style={{ background: "radial-gradient(circle at 50% 30%, #3B82F6 0%, #2563EB 45%, #0B2A6B 100%)" }}>
      {meta.hasVideo ? (
        <Video name="Video sumber" src={staticFile(`${base}/${meta.source}`)} muted style={{ width: "100%", height: "100%", objectFit: "cover" }} />
      ) : (
        <AbsoluteFill style={{ alignItems: "center", paddingTop: 260, gap: 40, fontFamily }}>
          <div style={{ width: 200, height: 200, borderRadius: 56, background: "#FFFFFF", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Icon name="feather" size={130} color="#2563EB" />
          </div>
          <div style={{ color: "#DCE8FD", fontSize: 52, fontWeight: 700, textAlign: "center", padding: "0 90px" }}>{meta.title}</div>
        </AbsoluteFill>
      )}
      <Audio name="Audio" src={staticFile(`${base}/audio.wav`)} />
      {pages.map((p, i) => (
        <Sequence key={i} name={`Subtitle ${i + 1}`} from={Math.round((p.startMs / 1000) * fps)} durationInFrames={Math.max(1, Math.round((p.durationMs / 1000) * fps))} layout="none">
          <CaptionPage page={p} />
        </Sequence>
      ))}
    </AbsoluteFill>
  );
};
