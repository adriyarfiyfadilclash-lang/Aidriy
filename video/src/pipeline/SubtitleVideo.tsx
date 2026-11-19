import { Audio } from "@remotion/media";
import { AbsoluteFill, type CalculateMetadataFunction, Sequence, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Icon } from "../promo/icons";
import type { ImageSet } from "../adriy/Adri";
import { StyleCharacter } from "../adriy/StyleCharacter";
import { Caption, DEFAULT_STYLE, type Style, StyledFrame, type Token, Visual } from "./styles";

// Data dibuat oleh ../pipeline/pipeline.py di public/media/<media>/:
//   meta.json   durasi, ukuran, judul, ada/tidaknya video latar
//   pages.json  subtitle per halaman, tiap kata dengan waktu mulai dan selesai (ms)
//   style.json  style visual (salinan pipeline/styles/<id>.json); kalau tidak ada, dipakai "bersih"
// Subtitle dibaca dari file, jadi tidak bisa diedit langsung di Studio: ubah transcript.json lalu
// jalankan ulang pipeline dengan --from pages.

type Page = { startMs: number; durationMs: number; tokens: Token[] };
type Meta = { duration: number; title: string; hasVideo: boolean; source: string };

export type SubtitleVideoProps = {
  media: string;
  pages: Page[];
  meta: Meta | null;
  style: Style | null;
};

export const calculateSubtitleMetadata: CalculateMetadataFunction<SubtitleVideoProps> = async ({ props }) => {
  const base = `media/${props.media}`;
  const [meta, pages, style] = await Promise.all([
    fetch(staticFile(`${base}/meta.json`)).then((r) => r.json() as Promise<Meta>),
    fetch(staticFile(`${base}/pages.json`)).then((r) => r.json() as Promise<Page[]>),
    fetch(staticFile(`${base}/style.json`)).then((r) => (r.ok ? (r.json() as Promise<Style>) : null)).catch(() => null),
  ]);
  return { durationInFrames: Math.ceil(meta.duration * 30), props: { ...props, meta, pages, style } };
};

const CaptionPage: React.FC<{ page: Page; style: Style; index: number }> = ({ page, style, index }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return <Caption style={style} tokens={page.tokens} nowMs={page.startMs + (frame / fps) * 1000} index={index} />;
};

const AppBrand: React.FC<{ title: string }> = ({ title }) => (
  <AbsoluteFill style={{ alignItems: "center", paddingTop: 260, gap: 40, fontFamily: "Plus Jakarta Sans" }}>
    <div style={{ width: 200, height: 200, borderRadius: 56, background: "#FFFFFF", display: "flex", alignItems: "center", justifyContent: "center" }}>
      <Icon name="feather" size={130} color="#2563EB" />
    </div>
    <div style={{ color: "#DCE8FD", fontSize: 52, fontWeight: 700, textAlign: "center", padding: "0 90px" }}>{title}</div>
  </AbsoluteFill>
);

export const SubtitleVideo: React.FC<SubtitleVideoProps> = ({ media, pages, meta, style }) => {
  const { fps } = useVideoConfig();
  if (!meta) return null;
  const st = style ?? DEFAULT_STYLE;
  const base = `media/${media}`;
  const showTitle = st.id !== "bersih";
  return (
    <AbsoluteFill>
      <StyledFrame style={st} title={showTitle ? meta.title : undefined}>
        {/* gambar/klip asli memakai bingkai style; tanpa gambar, ADRI tampil dengan penyelesaian khas style itu */}
        {meta.hasVideo ? <Visual style={st} src={`${base}/${meta.source}`} kind="video" /> : st.id === "bersih" ? <AppBrand title={meta.title} /> : <StyleCharacter styleId={st.id} captionBottom={st.subtitle.position} />}
      </StyledFrame>
      <Audio name="Audio" src={staticFile(`${base}/audio.wav`)} />
      {pages.map((p, i) => (
        <Sequence key={i} name={`Subtitle ${i + 1}`} from={Math.round((p.startMs / 1000) * fps)} durationInFrames={Math.max(1, Math.round((p.durationMs / 1000) * fps))} layout="none">
          <CaptionPage page={p} style={st} index={i} />
        </Sequence>
      ))}
    </AbsoluteFill>
  );
};

// ---------------------------------------------------------------- pratinjau style (gambar diam)
export type StylePreviewProps = {
  styleId: string; // file public/styles/<styleId>.json (disalin oleh pipeline.py --preview-styles)
  style: Style | null;
  title: string;
  caption: string;
  active: number; // indeks kata yang menyala
  visual: string; // gambar contoh di public/; kosong = ADRI dengan penyelesaian khas style
  real?: boolean; // ADRI dari gambar semi-realistis (public/adri/santai/peta.json) bila ada
  imageSet?: ImageSet | null;
};

export const calculateStylePreviewMetadata: CalculateMetadataFunction<StylePreviewProps> = async ({ props }) => {
  const style = await fetch(staticFile(`styles/${props.styleId}.json`)).then((r) => (r.ok ? (r.json() as Promise<Style>) : null)).catch(() => null);
  const imageSet = props.real ? await fetch(staticFile("adri/santai/peta.json")).then((r) => (r.ok ? (r.json() as Promise<ImageSet>) : null)).catch(() => null) : null;
  return { props: { ...props, style, imageSet } };
};

export const StylePreview: React.FC<StylePreviewProps> = ({ style: loaded, title, caption, active, visual, imageSet }) => {
  const style = loaded ?? DEFAULT_STYLE;
  const words = caption.split(" ");
  const tokens = words.map((w, i) => ({ text: (i ? " " : "") + w, fromMs: i === active ? 0 : -2, toMs: i === active ? 10_000 : -1 }));
  return (
    <AbsoluteFill>
      {/* latar diambil dari detik ke-2, saat animasinya (grafik, rute, uap, dll.) sudah berjalan */}
      <Sequence from={-60} layout="none">
        <StyledFrame style={style} title={title}>
          {visual ? <Visual style={style} src={visual} kind="image" /> : <StyleCharacter styleId={style.id} captionBottom={style.subtitle.position} imageSet={imageSet} />}
        </StyledFrame>
      </Sequence>
      <Caption style={style} tokens={tokens} nowMs={500} index={0} settled />
    </AbsoluteFill>
  );
};
