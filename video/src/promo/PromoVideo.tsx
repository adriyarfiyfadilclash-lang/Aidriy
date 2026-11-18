import { linearTiming, springTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { FeatureScene } from "./FeatureScene";
import { HookScene } from "./HookScene";
import { IntroScene } from "./IntroScene";
import { OutroScene } from "./OutroScene";

// 20 detik @30fps: 6 scene x 90 + outro 150 = 690 frame, dikurangi 6 transisi x 15 = 600 frame.
export const PromoVideo: React.FC = () => {
  return (
    <TransitionSeries>
      <TransitionSeries.Sequence name="Intro" durationInFrames={90}>
        <IntroScene
          name="Intro"
          title="NotesByAdriy"
          tagline="Second Brain AI"
          style={{ background: "radial-gradient(circle at 50% 35%, #3B82F6 0%, #2563EB 45%, #0B2A6B 100%)", justifyContent: "center", alignItems: "center" }}
        />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Hook" durationInFrames={90}>
        <HookScene
          name="Hook"
          headline="Catatan di mana‑mana?"
          subline="Ide hilang, journal berserakan, berkas tercecer."
          style={{ backgroundColor: "#0E1218", justifyContent: "center", alignItems: "center" }}
        />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-bottom" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur Catatan" durationInFrames={90}>
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
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur Scan" durationInFrames={90}>
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
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur AI" durationInFrames={90}>
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
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={slide({ direction: "from-right" })} timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Fitur Sinkron" durationInFrames={90}>
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
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
      <TransitionSeries.Sequence name="Outro" durationInFrames={150}>
        <OutroScene
          name="Outro"
          slogan={"Your Knowledge.\nOne Intelligent Space."}
          cta="Unduh Gratis"
          note="Offline-first · Data tetap di HP kamu"
          style={{ background: "radial-gradient(circle at 50% 40%, #3B82F6 0%, #2563EB 50%, #0B2A6B 100%)", justifyContent: "center", alignItems: "center" }}
        />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  );
};
