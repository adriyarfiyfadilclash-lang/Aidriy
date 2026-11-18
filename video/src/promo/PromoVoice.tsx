import { Audio } from "@remotion/media";
import { linearTiming, springTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { ALL_FORMATS, Input, UrlSource } from "mediabunny";
import { type CalculateMetadataFunction, staticFile, useVideoConfig } from "remotion";
import { FeatureScene } from "./FeatureScene";
import { HookScene } from "./HookScene";
import { IntroScene } from "./IntroScene";
import { OutroScene } from "./OutroScene";

// Voiceover dibuat dengan: python scripts/tts_batch.py scripts/promo-dialog.json public/vo
const VO_FILES = ["01-hook", "02-intro", "03-catatan", "04-scan", "05-ai", "06-sinkron", "07-outro"];
const TRANSITION = 15; // frame, sama untuk semua transisi
const LEAD = 18; // jeda sebelum voiceover mulai (setelah transisi selesai)
const TAIL = 14; // jeda setelah voiceover selesai

export type PromoVoiceProps = {
  sceneDurations: number[];
};

const getAudioDuration = async (src: string) => {
  const input = new Input({ formats: ALL_FORMATS, source: new UrlSource(src) });
  const seconds = await input.computeDuration();
  input.dispose();
  return seconds;
};

// Durasi tiap scene mengikuti panjang voiceover-nya, jadi kalau suara dibuat ulang,
// video ikut menyesuaikan tanpa mengubah kode.
export const calculatePromoVoiceMetadata: CalculateMetadataFunction<PromoVoiceProps> = async ({ props }) => {
  const fps = 30;
  const seconds = await Promise.all(VO_FILES.map((f) => getAudioDuration(staticFile(`vo/${f}.wav`))));
  const sceneDurations = seconds.map((s, i) => LEAD + Math.ceil(s * fps) + (i === seconds.length - 1 ? 2 * fps : TAIL));
  const total = sceneDurations.reduce((a, b) => a + b, 0) - TRANSITION * (sceneDurations.length - 1);
  return { durationInFrames: total, props: { ...props, sceneDurations } };
};

export const PromoVoice: React.FC<PromoVoiceProps> = ({ sceneDurations }) => {
  const { fps } = useVideoConfig();
  const [dHook, dIntro, dCatatan, dScan, dAi, dSinkron, dOutro] = sceneDurations;

  return (
    <TransitionSeries>
      <TransitionSeries.Sequence name="Hook" durationInFrames={dHook}>
        <HookScene
          name="Hook"
          headline="Catatan di mana‑mana?"
          subline="Ide hilang, journal berserakan, berkas tercecer."
          style={{ backgroundColor: "#0E1218", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="SFX kertas remas" src={staticFile("sfx/kertas-remas.wav")} volume={0.6} />
        <Audio name="SFX kertas lepas" src={staticFile("sfx/kertas-lepas.wav")} from={0.3 * fps} volume={0.5} />
        <Audio name="VO Ongky: hook" src={staticFile("vo/01-hook.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Intro" durationInFrames={dIntro}>
        <IntroScene
          name="Intro"
          title="NotesByAdriy"
          tagline="Second Brain AI"
          style={{ background: "radial-gradient(circle at 50% 35%, #3B82F6 0%, #2563EB 45%, #0B2A6B 100%)", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="VO Adriy: intro" src={staticFile("vo/02-intro.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-bottom" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur Catatan" durationInFrames={dCatatan}>
        <FeatureScene
          name="Fitur Catatan"
          step="FITUR 01"
          title="Catatan pintar"
          description="Markdown, checklist, dan gambar di antara teks."
          icon="note"
          accent="#2563EB"
          accentSoft="#E6EFFE"
          style={{ backgroundColor: "#F6F8FB", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="SFX kertas lepas" src={staticFile("sfx/kertas-lepas.wav")} volume={0.5} />
        <Audio name="VO Adriy: catatan" src={staticFile("vo/03-catatan.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur Scan" durationInFrames={dScan}>
        <FeatureScene
          name="Fitur Scan"
          step="FITUR 02"
          title="Pindai teks"
          description="Foto dokumen, teksnya langsung jadi catatan (OCR)."
          icon="scan"
          accent="#16A35A"
          accentSoft="#E3F7EC"
          style={{ backgroundColor: "#F6F8FB", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="SFX kertas lepas" src={staticFile("sfx/kertas-lepas.wav")} volume={0.5} />
        <Audio name="VO Ongky: scan" src={staticFile("vo/04-scan.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur AI" durationInFrames={dAi}>
        <FeatureScene
          name="Fitur AI"
          step="FITUR 03"
          title="Asisten AI"
          description="Rapikan format dan tanya apa saja dengan API key-mu sendiri."
          icon="ai"
          accent="#7C4DFF"
          accentSoft="#F0E9FE"
          style={{ backgroundColor: "#F6F8FB", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="SFX kertas lepas" src={staticFile("sfx/kertas-lepas.wav")} volume={0.5} />
        <Audio name="VO Adriy: AI" src={staticFile("vo/05-ai.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur Sinkron" durationInFrames={dSinkron}>
        <FeatureScene
          name="Fitur Sinkron"
          step="FITUR 04"
          title="Sinkron antar HP"
          description="Catatan, ide, journal, dan berkas dicadangkan dengan aman."
          icon="sync"
          accent="#F07A16"
          accentSoft="#FFEEDF"
          style={{ backgroundColor: "#F6F8FB", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="SFX kertas lepas" src={staticFile("sfx/kertas-lepas.wav")} volume={0.5} />
        <Audio name="VO Ongky: sinkron" src={staticFile("vo/06-sinkron.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Outro" durationInFrames={dOutro}>
        <OutroScene
          name="Outro"
          slogan={"Your Knowledge.\nOne Intelligent Space."}
          cta="Unduh Gratis"
          note="Offline-first · Data tetap di HP kamu"
          style={{ background: "radial-gradient(circle at 50% 40%, #3B82F6 0%, #2563EB 50%, #0B2A6B 100%)", justifyContent: "center", alignItems: "center" }}
        />
        <Audio name="VO Adriy: outro" src={staticFile("vo/07-outro.wav")} from={0.6 * fps} />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  );
};
