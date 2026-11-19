import { useCurrentFrame } from "remotion";
import { Adri, ADRI_SPEC, type ImageSet, type Outfit, type Pose } from "./Adri";

// ADRI di style lain (wayang, koran, horor, anak, ...). Identitasnya tetap (wajah, rambut, kacamata,
// hoodie berlogo A); yang berubah adalah "penyelesaian" gambar dan kostumnya supaya menyatu dengan style:
//   siluet   wayang: bayangan hitam di kelir, disinari lampu blencong, lengkap dengan tuding
//   cetak    koran: hitam-putih seperti cetakan koran, digunting dengan pinggiran kertas
//   suram    horor: pucat, disinari bulan, ekspresi terkejut
//   stiker   anak: pinggiran putih tebal seperti stiker, bergoyang
//   garis    sains: warna asli dengan garis tepi putih tipis ala cetak biru, dan garis ukur
//   hologram teknologi: warna asli dengan garis tepi neon cyan
//   (karakter selalu padat dan berwarna asli: versi garis/tembus pandang terlihat seperti hantu)
//   komik    olahraga: garis tepi hitam tebal dan bayangan kuning
//   cahaya   religi: pendar keemasan lembut
//   biasa    bersih, bisnis, kuliner, travel: warna asli dengan bayangan lembut
// Gambar asli dari naskah (foto/klip) tetap memakai bingkai style; karakter ini dipakai bila tidak ada gambar.

type Finish = "siluet" | "cetak" | "suram" | "stiker" | "garis" | "hologram" | "komik" | "cahaya" | "biasa" | "hangat";
type Look = { finish: Finish; pose: Pose; outfit?: Outfit; size: number; facing?: "left" | "right"; tilt?: number; holding?: "piring" };

// Style yang punya set gambar berkostum sendiri (folder di repo aset). Style lain memakai "santai".
export const IMAGE_SET: Record<string, string> = { travel: "travel", bisnis: "formal", sains: "lab" };
export const imageSetPath = (styleId: string) => `adri/${IMAGE_SET[styleId] ?? "santai"}/peta.json`;

export const STYLE_CHARACTER: Record<string, Look> = {
  wayang: { finish: "siluet", pose: "banding", size: 900 },
  koran: { finish: "cetak", pose: "pikir", size: 820, tilt: -2 },
  horor: { finish: "suram", pose: "kaget", size: 880, facing: "left" },
  anak: { finish: "stiker", pose: "senang", size: 820 },
  sains: { finish: "garis", pose: "jelaskan", size: 860 },
  teknologi: { finish: "hologram", pose: "tunjuk", size: 880 },
  bisnis: { finish: "biasa", pose: "jelaskan", size: 880, outfit: "bisnis" },
  kuliner: { finish: "hangat", pose: "pegang", size: 880, outfit: "koki", holding: "piring" },
  travel: { finish: "hangat", pose: "sapa", size: 880, outfit: "pelancong" },
  olahraga: { finish: "komik", pose: "rayakan", size: 880, outfit: "atlet" },
  religi: { finish: "cahaya", pose: "jelaskan", size: 880, outfit: "peci" },
  bersih: { finish: "biasa", pose: "sapa", size: 800 },
};

const white = (r: number) => [`${r}px 0 0 #fff`, `-${r}px 0 0 #fff`, `0 ${r}px 0 #fff`, `0 -${r}px 0 #fff`].map((d) => `drop-shadow(${d})`).join(" ");
const black = (r: number) => [`${r}px 0 0 #0B0B0B`, `-${r}px 0 0 #0B0B0B`, `0 ${r}px 0 #0B0B0B`, `0 -${r}px 0 #0B0B0B`].map((d) => `drop-shadow(${d})`).join(" ");

const FILTER: Record<Finish, string> = {
  siluet: "brightness(0) drop-shadow(0 0 26px rgba(242,179,61,0.6))",
  cetak: "url(#adri-cetak) drop-shadow(0 16px 16px rgba(0,0,0,0.35))",
  suram: "saturate(0.2) brightness(0.62) contrast(1.15) drop-shadow(0 0 30px rgba(170,190,255,0.35))",
  stiker: `saturate(1.15) ${white(7)} drop-shadow(0 18px 16px rgba(43,35,80,0.28))`,
  garis: `${[`3px 0 0 #EAF4FF`, `-3px 0 0 #EAF4FF`, `0 3px 0 #EAF4FF`, `0 -3px 0 #EAF4FF`].map((d) => `drop-shadow(${d})`).join(" ")} drop-shadow(0 18px 20px rgba(0,0,0,0.35))`,
  hologram: `${[`3px 0 0 #22E6FF`, `-3px 0 0 #22E6FF`, `0 3px 0 #22E6FF`, `0 -3px 0 #22E6FF`].map((d) => `drop-shadow(${d})`).join(" ")} drop-shadow(0 0 16px rgba(34,230,255,0.55))`,
  komik: `${black(5)} drop-shadow(12px 12px 0 #FFE600)`,
  cahaya: "drop-shadow(0 0 30px rgba(243,201,122,0.6)) drop-shadow(0 20px 24px rgba(0,0,0,0.25))",
  biasa: "drop-shadow(0 22px 26px rgba(0,0,0,0.3))",
  hangat: "sepia(0.12) saturate(1.08) drop-shadow(0 20px 24px rgba(59,36,22,0.3))",
};

// Filter SVG: cetakan koran (abu-abu bertingkat + pinggiran kertas), garis cetak biru, hologram
const Filters: React.FC = () => (
  <svg width={0} height={0} style={{ position: "absolute" }}>
    <defs>
      <filter id="adri-cetak" x="-10%" y="-10%" width="120%" height="120%" colorInterpolationFilters="sRGB">
        <feColorMatrix in="SourceGraphic" type="saturate" values="0" result="g" />
        <feComponentTransfer in="g" result="p">
          <feFuncR type="discrete" tableValues="0.1 0.38 0.68 0.94" />
          <feFuncG type="discrete" tableValues="0.1 0.37 0.66 0.92" />
          <feFuncB type="discrete" tableValues="0.09 0.34 0.6 0.86" />
        </feComponentTransfer>
        <feMorphology in="SourceAlpha" operator="dilate" radius={12} result="d" />
        <feFlood floodColor="#F4EEDD" />
        <feComposite in2="d" operator="in" result="kertas" />
        <feMerge>
          <feMergeNode in="kertas" />
          <feMergeNode in="p" />
        </feMerge>
      </filter>
      {[["adri-garis", "#FFFFFF", 0.07], ["adri-holo", "#22E6FF", 0.16]].map(([id, col, fill]) => (
        <filter key={id as string} id={id as string} x="-5%" y="-5%" width="110%" height="110%" colorInterpolationFilters="sRGB">
          {/* tepi antarwarna (detail wajah, tangan, lipatan) jadi garis */}
          <feConvolveMatrix in="SourceGraphic" order={3} kernelMatrix="-1 -1 -1 -1 8 -1 -1 -1 -1" preserveAlpha="true" result="e" />
          <feColorMatrix in="e" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  3 3 3 0 0" result="ea" />
          <feMorphology in="ea" operator="dilate" radius={1} result="eb" />
          {/* garis luar siluet */}
          <feMorphology in="SourceAlpha" operator="dilate" radius={3} result="d" />
          <feComposite in="d" in2="SourceAlpha" operator="out" result="o" />
          <feMerge result="lines">
            <feMergeNode in="o" />
            <feMergeNode in="eb" />
          </feMerge>
          <feFlood floodColor={col as string} />
          <feComposite in2="lines" operator="in" result="lc" />
          <feFlood floodColor={col as string} floodOpacity={fill as number} />
          <feComposite in2="SourceAlpha" operator="in" result="isi" />
          <feMerge>
            <feMergeNode in="isi" />
            <feMergeNode in="lc" />
          </feMerge>
        </filter>
      ))}
    </defs>
  </svg>
);

const Piring: React.FC = () => (
  <g transform="translate(0 -30)">
    <ellipse cx={0} cy={0} rx={120} ry={34} fill="#FFFFFF" stroke="#0B1230" strokeWidth={4} />
    <ellipse cx={0} cy={-6} rx={86} ry={20} fill="#F4EEE6" />
    <path d="M-60 -10 Q-30 -60 10 -40 Q50 -64 64 -12 Z" fill="#E9A13B" stroke="#0B1230" strokeWidth={3} />
    <circle cx={-20} cy={-34} r={9} fill="#7FB069" /><circle cx={22} cy={-38} r={8} fill="#E76F51" />
  </g>
);

// imageSet: pakai gambar ADRIY semi-realistis (kostum tidak bisa ditambahkan ke gambar, jadi tanpa kostum)
export const StyleCharacter: React.FC<{ styleId: string; captionBottom: number; seed?: number; imageSet?: ImageSet | null }> = ({ styleId, captionBottom, seed = 0, imageSet }) => {
  const frame = useCurrentFrame();
  const look = STYLE_CHARACTER[styleId] ?? STYLE_CHARACTER.bersih;
  // kaki ADRI berpijak di atas seluruh blok subtitle style itu (posisi = jarak tepi bawah subtitle dari bawah
  // layar; subtitle setinggi sampai 3 baris di atasnya), jadi subtitle tidak pernah menutupi karakter
  const floor = Math.min(1440, 1920 - captionBottom - 300);
  const size = Math.min(look.size, floor - 420);
  const w = size * (500 / 1180);
  // gerak khas style: stiker bergoyang, horor bergetar halus, hologram berkedip, wayang bergoyang di tuding
  const bob = look.finish === "stiker" ? Math.sin(frame / 12) * 10 : look.finish === "siluet" ? Math.sin(frame / 30) * 6 : 0;
  const rot = (look.tilt ?? 0) + (look.finish === "stiker" ? Math.sin(frame / 18) * 2 : look.finish === "siluet" ? Math.sin(frame / 40) * 1.5 : 0);
  const jx = look.finish === "suram" ? Math.sin(frame * 1.7) * 1.5 : 0;
  const op = 1;
  return (
    <>
      <Filters />
      <div
        data-qa="adri"
        style={{ position: "absolute", left: 540 - w / 2 + jx, top: floor - size * (1162 / 1180) + bob, width: w, height: size, rotate: `${rot}deg`, opacity: op, filter: FILTER[look.finish], transformOrigin: "50% 100%" }}
      >
        {/* gambar semi-realistis kecuali style yang butuh kostum (dasi, koki, pelancong, peci): kostum hanya ada di versi vector */}
        {imageSet && (!(look.outfit && look.outfit !== "atlet") || IMAGE_SET[styleId]) ? (
          <Adri spec={{ ...ADRI_SPEC, imageSet }} pose={look.pose} size={size} talking seed={seed} />
        ) : (
          <Adri spec={ADRI_SPEC} pose={look.pose} size={size} talking facing={look.facing ?? "right"} seed={seed} outfit={look.outfit} rods={look.finish === "siluet"} holding={look.holding === "piring" ? <Piring /> : undefined} />
        )}
      </div>
      {look.finish === "garis" && (
        // garis ukur tinggi seperti gambar teknik
        <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
          <g stroke="#EAF4FF" strokeWidth={2} fill="#EAF4FF" fontFamily="Space Grotesk" fontSize={24} opacity={0.75}>
            <path d={`M${540 + w / 2 + 50} ${floor - size} V${floor} M${540 + w / 2 + 38} ${floor - size} H${540 + w / 2 + 62} M${540 + w / 2 + 38} ${floor} H${540 + w / 2 + 62}`} />
            <text x={540 + w / 2 + 64} y={floor - size / 2} stroke="none">ADRI · FIG. 1</text>
          </g>
        </svg>
      )}
    </>
  );
};
