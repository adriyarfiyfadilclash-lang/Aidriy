import { Audio } from "@remotion/media";
import { AbsoluteFill, type CalculateMetadataFunction, Sequence, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { NextTransition, SceneIndex, TransitionTiming } from "./motion";
import { QAProbe } from "./qa";
import { type Brand, isLight, SCENES, SceneCaption, type SceneData, SceneLength, subtitleOf, type Token } from "./scenes";

// Video Adriy.project dari data pipeline di public/media/<media>/:
//   scenes.json (rencana adegan dari pipeline.py), pages.json (subtitle), meta.json, style.json (brand)

type Page = { startMs: number; durationMs: number; tokens: Token[] };
type Meta = { duration: number; title: string };

export type AdriyVideoProps = { media: string; scenes: SceneData[]; pages: Page[]; meta: Meta | null; brand: Brand | null; qa?: boolean };

export const calculateAdriyMetadata: CalculateMetadataFunction<AdriyVideoProps> = async ({ props }) => {
  const base = `media/${props.media}`;
  const get = <T,>(f: string) => fetch(staticFile(`${base}/${f}`)).then((r) => r.json() as Promise<T>);
  const [scenes, pages, meta, brand] = await Promise.all([get<SceneData[]>("scenes.json"), get<Page[]>("pages.json"), get<Meta>("meta.json"), get<Brand>("style.json")]);
  return { durationInFrames: Math.ceil(meta.duration * 30), props: { ...props, scenes, pages, meta, brand } };
};

const SceneSlot: React.FC<{ s: SceneData; brand: Brand; pages: Page[] }> = ({ s, brand, pages }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const nowMs = s.start * 1000 + (frame / fps) * 1000;
  const page = pages.find((p) => nowMs >= p.startMs && nowMs < p.startMs + p.durationMs);
  const Comp = SCENES[s.type];
  // adegan tipografi sudah menampilkan kalimatnya sendiri: subtitle tidak dobel
  // subtitle selalu di pita bawah; disembunyikan bila sama dengan judul yang sedang tampil
  const caption = s.type === "tipografi" || !page ? null : (
    <SceneCaption tokens={page.tokens} nowMs={nowMs} brand={brand} tone={isLight(s, brand) ? "light" : "dark"} highlight={s.highlight} onScreen={s.title ?? s.text} mode={subtitleOf(s)} />
  );
  return <Comp s={s} brand={brand} caption={caption} />;
};

export const AdriyVideo: React.FC<AdriyVideoProps> = ({ media, scenes, pages, brand, qa }) => {
  const { fps } = useVideoConfig();
  if (!brand) return null;
  return (
    <AbsoluteFill style={{ background: brand.palette.bg }}>
      {scenes.map((s, i) => {
        const len = Math.max(1, Math.round((s.end - s.start) * fps));
        return (
          <Sequence key={i} name={`${i + 1}. ${s.type}`} from={Math.round(s.start * fps)} durationInFrames={len}>
            <NextTransition.Provider value={scenes[i + 1]?.transition ?? null}>
              {/* lama transisi = tumpang tindih nyata; adegan lama selesai tepat saat adegan baru utuh */}
              <TransitionTiming.Provider value={{ enter: i ? Math.max(0, scenes[i - 1].end - s.start) : 0, exit: scenes[i + 1] ? Math.max(0, s.end - scenes[i + 1].start) : 0 }}>
                <SceneIndex.Provider value={i}>
                  <SceneLength.Provider value={len}>
                    <SceneSlot s={s} brand={brand} pages={pages} />
                  </SceneLength.Provider>
                </SceneIndex.Provider>
              </TransitionTiming.Provider>
            </NextTransition.Provider>
          </Sequence>
        );
      })}
      <Audio name="Audio" src={staticFile(`media/${media}/audio.wav`)} />
      {qa && <QAProbe />}
    </AbsoluteFill>
  );
};
