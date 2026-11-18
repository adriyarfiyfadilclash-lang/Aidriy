import { Composition, Folder, Still } from "remotion";
import { HelloWorld } from "./HelloWorld";
import { PromoVideo } from "./promo/PromoVideo";
import { calculatePromoVoiceMetadata, PromoVoice } from "./promo/PromoVoice";
import { AdriyVideo, calculateAdriyMetadata } from "./adriy/AdriyVideo";
import { calculateScenePreviewMetadata, ScenePreview } from "./adriy/ScenePreview";
import { calculateStylePreviewMetadata, calculateSubtitleMetadata, StylePreview, SubtitleVideo } from "./pipeline/SubtitleVideo";
import { IntroScene } from "./promo/IntroScene";
import { HookScene } from "./promo/HookScene";
import { FeatureScene } from "./promo/FeatureScene";
import { OutroScene } from "./promo/OutroScene";

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Promo"
        component={PromoVideo}
        durationInFrames={600}
        fps={30}
        width={1080}
        height={1920}
      />
      <Composition
        id="PromoSuara"
        component={PromoVoice}
        durationInFrames={900}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={{ sceneDurations: [120, 120, 120, 120, 120, 120, 150], voice: true, sfx: true }}
        calculateMetadata={calculatePromoVoiceMetadata}
      />
      <Composition
        id="Subtitle"
        component={SubtitleVideo}
        durationInFrames={300}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={{ media: "kaca-mobil", pages: [], meta: null, style: null }}
        calculateMetadata={calculateSubtitleMetadata}
      />
      <Composition
        id="AdriyVideo"
        component={AdriyVideo}
        durationInFrames={300}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={{ media: "kaca-mobil", scenes: [], pages: [], meta: null, brand: null }}
        calculateMetadata={calculateAdriyMetadata}
      />
      <Still
        id="ScenePreview"
        component={ScenePreview}
        width={1080}
        height={1920}
        defaultProps={{ type: "karakter", styleId: "adriy", brand: null, at: 2.6, caption: "Uap air berubah jadi titik-titik kecil di kaca" }}
        calculateMetadata={calculateScenePreviewMetadata}
      />
      <Still
        id="StylePreview"
        component={StylePreview}
        width={1080}
        height={1920}
        defaultProps={{
          styleId: "wayang",
          style: null,
          title: "Runtuhnya Majapahit",
          caption: "Pada abad ke-15, kerajaan besar itu mulai goyah",
          active: 3,
          visual: "style-preview/tokoh.png",
        }}
        calculateMetadata={calculateStylePreviewMetadata}
      />
      <Folder name="Promo-Scenes">
        <Composition
          id="IntroScene"
          component={IntroScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
          defaultProps={{
            title: "NotesByAdriy",
            tagline: "Second Brain AI",
            style: {
              background: "radial-gradient(circle at 50% 35%, #3B82F6 0%, #2563EB 45%, #0B2A6B 100%)",
              justifyContent: "center",
              alignItems: "center",
            },
          }}
        />
        <Composition
          id="HookScene"
          component={HookScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
          defaultProps={{
            headline: "Catatan di mana‑mana?",
            subline: "Ide hilang, journal berserakan, berkas tercecer.",
            style: { backgroundColor: "#0E1218", justifyContent: "center", alignItems: "center" },
          }}
        />
        <Composition
          id="FeatureScene"
          component={FeatureScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
          defaultProps={{
            step: "FITUR 01",
            title: "Catatan pintar",
            description: "Markdown, checklist, dan gambar di antara teks.",
            icon: "note",
            accent: "#2563EB",
            accentSoft: "#E6EFFE",
            style: { backgroundColor: "#F6F8FB", justifyContent: "center", alignItems: "center" },
          }}
        />
        <Composition
          id="OutroScene"
          component={OutroScene}
          durationInFrames={150}
          fps={30}
          width={1080}
          height={1920}
          defaultProps={{
            slogan: "Your Knowledge.\nOne Intelligent Space.",
            cta: "Unduh Gratis",
            note: "Offline-first · Data tetap di HP kamu",
            style: {
              background: "radial-gradient(circle at 50% 40%, #3B82F6 0%, #2563EB 50%, #0B2A6B 100%)",
              justifyContent: "center",
              alignItems: "center",
            },
          }}
        />
      </Folder>
      <Composition
        id="HelloWorld"
        component={HelloWorld}
        durationInFrames={90}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={{ title: "NotesByAdriy" }}
      />
    </>
  );
};
