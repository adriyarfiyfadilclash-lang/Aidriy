import { withImageSet } from "./Adri";
import { AbsoluteFill, type CalculateMetadataFunction, Sequence, staticFile } from "remotion";
import { NextTransition, OVERLAP, SceneIndex } from "./motion";
import { QAProbe } from "./qa";
import { type Brand, isLight, SCENES, SceneCaption, type SceneData, SceneLength, type SceneType, subtitleOf, subtitleZoneOf } from "./scenes";

// Gambar pratinjau satu jenis adegan (bukan video). Contoh isi memakai topik "Kenapa kaca mobil
// berkabut?" supaya semua jenis adegan bisa dibandingkan. Dengan `prev`, yang digambar adalah titik
// tengah transisi dari adegan `prev` ke adegan `type`.
export const SAMPLES: Record<SceneType, Omit<SceneData, "start" | "end">> = {
  karakter: { type: "karakter", transition: "slide", tone: "dark", env: "umum", side: "right", pose: "sapa", kicker: "Adri.project", title: "Kenapa kaca mobil tiba-tiba berkabut?", highlight: "berkabut", points: ["mobil:", "drop:"] },
  objek: { type: "objek", transition: "iris", tone: "light", env: "terang", kicker: "Lihat lebih dekat", title: "Embun di kaca depan", highlight: "Embun", points: ["drop:Titik air kecil", "suhu:Kaca lebih dingin", "angin:Udara lembap"] },
  infografis: { type: "infografis", transition: "morph", tone: "blue", env: "kota", kicker: "Penyebabnya", title: "Tiga hal yang membuat kabut", highlight: "kabut", points: ["suhu:Kaca lebih dingin dari udara", "drop:Udara kabin lembap", "angin:Sirkulasi udara kurang"] },
  diagram: { type: "diagram", transition: "whip", tone: "dark", env: "teknologi", kicker: "Cara mengatasi", title: "Hilangkan kabut dalam 3 langkah", highlight: "3 langkah", steps: ["Nyalakan AC", "Arahkan ke kaca", "Pakai udara luar"] },
  perbandingan: { type: "perbandingan", transition: "wipe", tone: "light", env: "terang", kicker: "Mana yang lebih cepat?", title: "Dilap vs dinyalakan AC", highlight: "AC", left: { label: "Dilap tangan", value: "±10 dtk", note: "kabut muncul lagi" }, right: { label: "AC + udara luar", value: "±60 dtk", note: "kabut hilang tuntas", win: true } },
  tipografi: { type: "tipografi", transition: "zoom", tone: "blue", env: "kota", text: "Bukan dilap, tapi hilangkan penyebabnya", highlight: "penyebabnya" },
  sinematik: { type: "sinematik", transition: "push", tone: "dark", kicker: "Ingat", title: "Pandangan jelas, perjalanan aman", highlight: "aman" },
  angka: { type: "angka", transition: "zoom", tone: "dark", env: "data", kicker: "Kelembapan kabin", value: 85, unit: "%", max: 100, label: "kelembapan saat kaca mulai berkabut", highlight: "berkabut", pose: "kaget" },
  grafik: { type: "grafik", transition: "wipe", tone: "dark", env: "data", kicker: "Waktu kabut hilang", title: "AC dengan udara luar paling cepat", highlight: "paling cepat", unit: "s", data: [{ label: "Dilap", value: 10 }, { label: "Kipas", value: 35 }, { label: "AC", value: 60 }, { label: "AC+luar", value: 90 }] },
  lingkaran: { type: "lingkaran", transition: "shape", tone: "dark", env: "sains", kicker: "Sumber uap air", title: "Dari mana uap di kabin?", highlight: "uap", data: [{ label: "Napas", value: 55 }, { label: "Baju basah", value: 25 }, { label: "Lainnya", value: 20 }] },
  timeline: { type: "timeline", transition: "slide", tone: "dark", env: "sejarah", kicker: "Sejarah defroster", title: "Dari lap ke pemanas kaca", highlight: "pemanas", events: [{ year: "1920", label: "Kaca dilap dengan kain" }, { year: "1939", label: "Kipas pertama di dasbor" }, { year: "1960", label: "Pemanas kaca belakang" }, { year: "2010", label: "AC otomatis anti-embun" }] },
  peta: { type: "peta", transition: "iris", tone: "dark", env: "alam", kicker: "Paling sering berkabut", title: "Kota dengan udara paling lembap", highlight: "lembap", pins: [{ u: 0.18, v: 0.42, label: "Medan" }, { u: 0.38, v: 0.74, label: "Jakarta" }, { u: 0.5, v: 0.4, label: "Pontianak" }, { u: 0.67, v: 0.48, label: "Palu" }] },
  alur: { type: "alur", transition: "push", tone: "light", env: "terang", kicker: "Cek cepat", title: "Kaca masih berkabut?", highlight: "berkabut", steps: ["Nyalakan AC", "Buka sirkulasi luar", "Kaca jernih"], decision: "Sudah jernih?" },
  potongan: { type: "potongan", transition: "morph", tone: "dark", env: "sains", kicker: "Di dalam kaca", title: "Lapisan kaca mobil", highlight: "Lapisan", layers: ["Kaca luar", "Lapisan PVB", "Kaca dalam", "Embun"] },
  lingkungan: { type: "lingkungan", transition: "envshift", tone: "light", env: "kota", kicker: "Pagi hari, hujan", title: "Kaca depan tiba-tiba buram", highlight: "buram" },
  minimalis: { type: "minimalis", transition: "smooth", tone: "dark", kicker: "Jadi intinya", title: "Hilangkan penyebabnya, bukan embunnya", highlight: "penyebabnya" },
  persentase: { type: "persentase", transition: "shape", tone: "dark", env: "umum", kicker: "Fakta", value: 72, label: "pengemudi menyalakan AC tanpa udara luar", highlight: "udara luar", pose: "angkat" },
};

// extra: menimpa isi contoh, misalnya {"layout": "full"} atau {"subtitle": "kinetik"} atau {"altShot": true}
export type ScenePreviewProps = { type: SceneType; styleId: string; brand: Brand | null; at: number; caption: string; prev?: SceneType | null; extra?: Partial<SceneData> | null; qa?: boolean; vector?: boolean };

export const calculateScenePreviewMetadata: CalculateMetadataFunction<ScenePreviewProps> = async ({ props }) => {
  const brand = await fetch(staticFile(`styles/${props.styleId}.json`)).then((r) => (r.ok ? (r.json() as Promise<Brand>) : null)).catch(() => null);
  return { props: { ...props, brand: props.vector ? brand : await withImageSet(brand) } };
};

const LEN = 180; // adegan contoh 6 detik

const One: React.FC<{ type: SceneType; brand: Brand; caption: string; next?: string; index?: number; extra?: Partial<SceneData> }> = ({ type, brand, caption, next, index = 0, extra }) => {
  const s: SceneData = { ...SAMPLES[type], ...extra, start: 0, end: 6 };
  const Comp = SCENES[type];
  const words = caption.split(" ");
  const tokens = words.map((w, i) => ({ text: (i ? " " : "") + w, fromMs: i === 2 ? 0 : -2, toMs: i === 2 ? 99_000 : -1 }));
  const cap = type === "tipografi" ? null : <SceneCaption tokens={tokens} nowMs={1} brand={brand} tone={isLight(s, brand) ? "light" : "dark"} highlight={s.highlight} mode={subtitleOf(s)} zone={subtitleZoneOf(s)} />;
  return (
    <NextTransition.Provider value={next ?? null}>
      <SceneIndex.Provider value={index}>
        <SceneLength.Provider value={LEN}>
          <Comp s={s} brand={brand} caption={cap} />
        </SceneLength.Provider>
      </SceneIndex.Provider>
    </NextTransition.Provider>
  );
};

export const ScenePreview: React.FC<ScenePreviewProps> = ({ type, brand, at, caption, prev, extra, qa }) => {
  if (!brand) return <AbsoluteFill style={{ background: "#300" }} />;
  if (prev) {
    // titik tengah transisi: adegan lama di akhir, adegan baru setengah masuk di atasnya
    const half = Math.round((OVERLAP * 30) / 2);
    return (
      <AbsoluteFill>
        <Sequence from={-(LEN - half)} layout="none">
          <One type={prev} brand={brand} caption={caption} next={SAMPLES[type].transition} />
        </Sequence>
        <Sequence from={-half} layout="none">
          <One type={type} brand={brand} caption={caption} index={1} extra={extra ?? undefined} />
        </Sequence>
        {qa && <QAProbe />}
              </AbsoluteFill>
    );
  }
  // diambil di detik `at` dari adegan 6 detik, saat semua elemen sudah masuk
  return (
    <Sequence from={-Math.round(at * 30)} layout="none">
      <One type={type} brand={brand} caption={caption} extra={extra ?? undefined} />
      {qa && <QAProbe />}
    </Sequence>
  );
};
