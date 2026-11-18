import { AbsoluteFill, type CalculateMetadataFunction, Sequence, staticFile } from "remotion";
import { type Brand, SCENES, SceneCaption, type SceneData, SceneLength } from "./scenes";

// Gambar pratinjau satu jenis adegan (bukan video). Contoh isi di bawah memakai topik
// "Kenapa kaca mobil berkabut?" supaya semua jenis adegan bisa dibandingkan.
export const SAMPLES: Record<SceneData["type"], Omit<SceneData, "start" | "end">> = {
  karakter: { type: "karakter", transition: "slide", camera: "push", tone: "dark", side: "right", pose: "sapa", kicker: "Adri.project", title: "Kenapa kaca mobil tiba-tiba berkabut?", highlight: "berkabut", points: ["mobil:", "drop:"] },
  objek: { type: "objek", transition: "zoom", camera: "pan-right", tone: "light", kicker: "Lihat lebih dekat", title: "Embun di kaca depan", highlight: "Embun", points: ["drop:Titik air kecil", "suhu:Kaca lebih dingin", "angin:Udara lembap"] },
  infografis: { type: "infografis", transition: "fade", camera: "rise", tone: "blue", kicker: "Penyebabnya", title: "Tiga hal yang membuat kabut", highlight: "kabut", points: ["suhu:Kaca lebih dingin dari udara", "drop:Udara kabin lembap", "angin:Sirkulasi udara kurang"] },
  diagram: { type: "diagram", transition: "wipe", camera: "pan-left", tone: "dark", kicker: "Cara mengatasi", title: "Hilangkan kabut dalam 3 langkah", highlight: "3 langkah", steps: ["Nyalakan AC", "Arahkan ke kaca depan", "Pakai udara luar"] },
  perbandingan: { type: "perbandingan", transition: "wipe", camera: "push", tone: "light", kicker: "Mana yang lebih cepat?", title: "Dilap vs dinyalakan AC", highlight: "AC", left: { label: "Dilap tangan", value: "±10 dtk", note: "kabut muncul lagi" }, right: { label: "AC + udara luar", value: "±60 dtk", note: "kabut hilang tuntas", win: true } },
  tipografi: { type: "tipografi", transition: "zoom", camera: "pull", tone: "blue", text: "Bukan dilap, tapi hilangkan penyebabnya", highlight: "penyebabnya" },
  sinematik: { type: "sinematik", transition: "fade", camera: "pan-right", tone: "dark", kicker: "Ingat", title: "Pandangan jelas, perjalanan aman", highlight: "aman" },
  angka: { type: "angka", transition: "zoom", camera: "push", tone: "dark", kicker: "Kelembapan kabin", value: 85, unit: "%", max: 100, label: "kelembapan saat kaca mulai berkabut", pose: "kaget" },
};

export type ScenePreviewProps = { type: SceneData["type"]; styleId: string; brand: Brand | null; at: number; caption: string };

export const calculateScenePreviewMetadata: CalculateMetadataFunction<ScenePreviewProps> = async ({ props }) => {
  const brand = await fetch(staticFile(`styles/${props.styleId}.json`)).then((r) => (r.ok ? (r.json() as Promise<Brand>) : null)).catch(() => null);
  return { props: { ...props, brand } };
};

export const ScenePreview: React.FC<ScenePreviewProps> = ({ type, brand, at, caption }) => {
  if (!brand) return <AbsoluteFill style={{ background: "#300" }} />;
  const s: SceneData = { ...SAMPLES[type], start: 0, end: 6 };
  const Comp = SCENES[type];
  const words = caption.split(" ");
  const tokens = words.map((w, i) => ({ text: (i ? " " : "") + w, fromMs: i === 2 ? 0 : -2, toMs: i === 2 ? 99_000 : -1 }));
  const cap = type === "tipografi" ? null : <SceneCaption tokens={tokens} nowMs={1} brand={brand} tone={s.tone} />;
  // diambil di detik `at` dari adegan 6 detik, saat semua elemen sudah masuk
  return (
    <Sequence from={-Math.round(at * 30)} layout="none">
      <SceneLength.Provider value={180}>
        <Comp s={s} brand={brand} caption={cap} />
      </SceneLength.Provider>
    </Sequence>
  );
};
