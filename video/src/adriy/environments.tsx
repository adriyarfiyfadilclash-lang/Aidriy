import { AbsoluteFill } from "remotion";
import { Layer } from "./camera";
import { FLOOR, H, rand, useScene, W } from "./motion";
import type { Brand } from "./scenes";

// Latar adaptif Adriy.project. Latar mengikuti topik dan suasana adegan, tidak satu warna untuk seluruh video:
//   cerah     putih + biru muda     penjelasan ringan, fakta sehari-hari
//   arsip     krem, cokelat, kertas sejarah, penemuan, cerita masa lalu
//   alam      hijau, turquoise      alam, lingkungan
//   sains     cyan, ruang lab       eksperimen, sains
//   hangat    oranye, kuning        penemuan, kejutan, energi, momen penting
//   pastel    ungu, pink            psikologi, kreativitas, topik ringan
//   langit    biru langit + awan    langit, cuaca, atmosfer
//   dapur     krem hangat           makanan, memasak
//   kota      siang, biru muda      kota, kendaraan
//   data      putih minimal         angka (angka jadi fokus)
//   banding   dua kondisi           perbandingan
//   bahaya    merah muda + merah    peringatan saja
//   minimal   putih elegan          kesimpulan
//   brand     biru brand            pembuka/penutup brand
//   malam     biru gelap            malam, ruang angkasa, dramatis
//   teknologi biru gelap digital    teknologi
// Warna brand (biru, navy, kuning) tetap jadi aksen di semua latar.
// Lapisan 1 = gradasi + tekstur (diam). Lapisan 2 = ilustrasi lingkungan (kedalaman 0.3).

export type Env =
  | "cerah" | "arsip" | "alam" | "sains" | "hangat" | "pastel" | "langit" | "dapur" | "kota" | "data" | "banding" | "bahaya"
  | "minimal" | "brand" | "malam" | "teknologi"
  // latar style tematik
  | "bawahlaut" | "antariksa" | "sabana" | "jurnal" | "lab"
  // nama lama (tetap diterima)
  | "umum" | "terang" | "sejarah" | "psikologi" | "fakta";

const ALIAS: Record<string, Env> = { umum: "cerah", terang: "cerah", sejarah: "arsip", psikologi: "pastel", fakta: "hangat" };
export const canonEnv = (e: Env): Env => ALIAS[e] ?? e;

type Look = { sky: [string, string, string]; ink: string; accent: string; glow: string; floor: string; light: boolean };

export const envLook = (env: Env, b: Brand): Look => {
  const p = b.palette;
  const navy = p.bg;
  const L: Record<string, Look> = {
    cerah: { sky: ["#FFFFFF", "#EEF5FF", "#D9E9FF"], ink: navy, accent: p.primary, glow: "#BFD9FF", floor: "#D2E3FA", light: true },
    arsip: { sky: ["#FBF3E2", "#F1E3C6", "#E2CDA4"], ink: "#3A2A18", accent: "#A5581C", glow: "#FFE2A8", floor: "#DCC49A", light: true },
    alam: { sky: ["#EFFFF8", "#C9F2E2", "#9BE0C8"], ink: "#0B3B36", accent: "#0E9F84", glow: "#FFF3B0", floor: "#8FD3B6", light: true },
    sains: { sky: ["#F0FCFF", "#CDF3FA", "#A3E4EF"], ink: "#07324A", accent: "#0A9DB5", glow: "#E0FBFF", floor: "#A9DEE8", light: true },
    hangat: { sky: ["#FFF0CC", "#FFC970", "#FF9F4A"], ink: navy, accent: navy, glow: "#FFFFFF", floor: "#F19A43", light: true },
    pastel: { sky: ["#FBF5FF", "#EEDDFC", "#F9D8EA"], ink: "#2D1E50", accent: "#7B4FE0", glow: "#FFE4F2", floor: "#E2CBF5", light: true },
    langit: { sky: ["#6FBDFF", "#A9D9FF", "#FFF0D2"], ink: navy, accent: navy, glow: "#FFF6C2", floor: "#8CCB8F", light: true },
    dapur: { sky: ["#FFF7EC", "#FBE6CB", "#F2CFA3"], ink: "#3A2414", accent: "#C2571A", glow: "#FFFFFF", floor: "#C99668", light: true },
    kota: { sky: ["#F2F8FF", "#D6E8FF", "#B9D5FA"], ink: navy, accent: p.primary, glow: "#FFFFFF", floor: "#9DB9DE", light: true },
    data: { sky: ["#FFFFFF", "#F6F8FD", "#EDF1FA"], ink: navy, accent: p.primary, glow: "#E3ECFF", floor: "#E3E9F5", light: true },
    banding: { sky: ["#F7FAFF", "#EEF3FB", "#E6EDF8"], ink: navy, accent: p.primary, glow: "#FFFFFF", floor: "#D8E2F1", light: true },
    bahaya: { sky: ["#FFF6F5", "#FFE1DE", "#FFC9C4"], ink: "#4A1214", accent: "#D63A3F", glow: "#FFFFFF", floor: "#F4B5B0", light: true },
    minimal: { sky: ["#FFFFFF", "#F7F8FC", "#EEF1F8"], ink: navy, accent: p.primary, glow: "#FFFFFF", floor: "#EEF1F8", light: true },
    brand: { sky: ["#4D84FF", "#2F6BFF", "#1F3FB8"], ink: "#FFFFFF", accent: p.accent, glow: "#FFFFFF", floor: "#1C3AA8", light: false },
    malam: { sky: [p.bg2, p.bg, "#050A1C"], ink: p.ink, accent: p.accent, glow: p.primary, floor: "#0E1B42", light: false },
    teknologi: { sky: ["#1B2C8F", "#0C1450", "#050A2A"], ink: p.ink, accent: "#5CE1FF", glow: "#7A5CFF", floor: "#0A1240", light: false },
    bawahlaut: { sky: ["#2BB3D9", "#0E6E9E", "#063A5C"], ink: "#FFFFFF", accent: "#FFD166", glow: "#B8F1FF", floor: "#E8D3A2", light: false },
    antariksa: { sky: ["#241A5C", "#0D0B2E", "#03020F"], ink: "#FFFFFF", accent: p.accent, glow: "#8E7BFF", floor: "#5E5A6E", light: false },
    sabana: { sky: ["#FFE8B8", "#F9D48A", "#E8B464"], ink: "#3A2A10", accent: "#B5541C", glow: "#FFF4D6", floor: "#C9A15A", light: true },
    jurnal: { sky: ["#F6EBD3", "#EBDCB9", "#D9C397"], ink: "#33240F", accent: "#8C3B12", glow: "#FFF3D6", floor: "#C8A877", light: true },
    lab: { sky: ["#FFFFFF", "#EAF7FB", "#D4EEF5"], ink: "#0A2E3D", accent: "#0A9DB5", glow: "#E9FBFF", floor: "#CFE3EA", light: true },
  };
  return L[canonEnv(env)] ?? L.cerah;
};

// ---------------------------------------------------------------- ilustrasi lingkungan (SVG 1080x1920)
// Diletakkan di tepi dan bagian bawah supaya area isi tetap lapang, dengan kontras rendah (lapisan pendukung).
const Cloud: React.FC<{ x: number; y: number; s: number; o: number }> = ({ x, y, s, o }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`} opacity={o}>
    <ellipse cx={0} cy={0} rx={90} ry={46} fill="#FFFFFF" />
    <ellipse cx={-70} cy={14} rx={60} ry={34} fill="#FFFFFF" />
    <ellipse cx={74} cy={12} rx={66} ry={36} fill="#FFFFFF" />
  </g>
);

const Deco: React.FC<{ env: Env; lk: Look; frame: number }> = ({ env, lk, frame }) => {
  const c = lk.ink;
  switch (canonEnv(env)) {
    case "langit":
      // matahari, awan yang berjalan pelan, cakrawala bukit
      return (
        <g>
          <circle cx={830} cy={330} r={120} fill="#FFE9A0" opacity={0.9} />
          <circle cx={830} cy={330} r={190} fill="#FFF3C4" opacity={0.35} />
          <Cloud x={((frame * 0.4) % 1400) - 200} y={520} s={1.1} o={0.9} />
          <Cloud x={((frame * 0.25 + 700) % 1400) - 200} y={760} s={0.8} o={0.8} />
          <Cloud x={((frame * 0.18 + 300) % 1400) - 200} y={260} s={0.6} o={0.7} />
          <path d="M-200 1330 C100 1240 300 1280 540 1320 C780 1360 960 1260 1280 1300 V1500 H-200 Z" fill="#9FD7A0" opacity={0.9} />
        </g>
      );
    case "arsip":
      // tekstur arsip: kertas bergaris, cap, potongan peta lama, garis rute samar
      return (
        <g opacity={0.5}>
          {Array.from({ length: 9 }, (_, i) => (
            <line key={i} x1={60} x2={1020} y1={980 + i * 46} y2={980 + i * 46} stroke={lk.accent} strokeOpacity={0.18} strokeWidth={2} />
          ))}
          <circle cx={930} cy={1250} r={70} fill="none" stroke={lk.accent} strokeWidth={6} strokeOpacity={0.35} />
          <circle cx={930} cy={1250} r={52} fill="none" stroke={lk.accent} strokeWidth={2} strokeOpacity={0.35} />
          <path d="M60 300 C140 260 200 330 280 290 C340 260 380 320 430 300" stroke={c} strokeOpacity={0.25} strokeWidth={4} fill="none" strokeDasharray="10 10" />
          <path d="M700 180 l40 -20 l30 30 l50 -10 l20 40 l-60 30 l-40 -10 Z" fill={lk.accent} opacity={0.15} />
        </g>
      );
    case "alam":
      // bukit berlapis, pohon, matahari
      return (
        <g>
          <circle cx={860} cy={380} r={110} fill="#FFF1A8" opacity={0.8} />
          <path d="M-100 1150 C200 1010 380 1060 560 1110 C760 1160 900 1030 1180 1070 V1500 H-100 Z" fill="#7FD1B0" opacity={0.7} />
          <path d="M-100 1290 C240 1190 420 1250 620 1290 C820 1330 960 1230 1180 1250 V1500 H-100 Z" fill="#4DB894" opacity={0.85} />
          {[[120, 1210], [210, 1235], [930, 1180], [1010, 1205]].map(([x, y], i) => (
            <g key={i}>
              <rect x={x - 6} y={y} width={12} height={46} fill="#3B7A57" />
              <circle cx={x} cy={y - 10} r={34} fill="#2F9E6E" />
            </g>
          ))}
        </g>
      );
    case "sains":
      // ruang eksperimen: meja lab, labu, tabung reaksi, molekul
      return (
        <g opacity={0.55}>
          <rect x={-40} y={1330} width={1160} height={22} rx={6} fill={lk.accent} opacity={0.35} />
          <g transform="translate(760 210)">
            <path d="M120 980 V860 H100 V840 H200 V860 H180 V980 L250 1100 Q260 1120 230 1120 H70 Q40 1120 50 1100 Z" fill="#FFFFFF" stroke={lk.accent} strokeWidth={6} strokeLinejoin="round" />
            <path d="M74 1060 H226 L250 1100 Q260 1120 230 1120 H70 Q40 1120 50 1100 Z" fill={lk.accent} opacity={0.55} />
          </g>
          {[0, 1, 2].map((i) => (
            <g key={i}>
              <rect x={80 + i * 46} y={1180 + (i % 2) * 20} width={30} height={150 - (i % 2) * 20} rx={15} fill="#FFFFFF" stroke={lk.accent} strokeWidth={5} />
              <rect x={84 + i * 46} y={1260} width={22} height={66 - (i % 2) * 20} rx={10} fill={[lk.accent, "#7ED957", "#FFB547"][i]} opacity={0.7} />
            </g>
          ))}
          {[[880, 560], [960, 640], [900, 720], [1000, 520]].map(([x, y], i, a) => (
            <g key={i}>
              {i > 0 && <line x1={a[i - 1][0]} y1={a[i - 1][1]} x2={x} y2={y} stroke={lk.accent} strokeWidth={5} />}
              <circle cx={x} cy={y} r={16 + (i % 2) * 8} fill={i % 2 ? "#FFB547" : lk.accent} />
            </g>
          ))}
        </g>
      );
    case "hangat":
      // sinar dari bawah: energi, kejutan, momen penting
      return (
        <g opacity={0.35}>
          {Array.from({ length: 12 }, (_, i) => {
            const a = -Math.PI / 2 + ((i - 6) / 12) * Math.PI + Math.sin(frame / 90) * 0.03;
            return <path key={i} d={`M540 1500 L${540 + Math.cos(a - 0.06) * 2200} ${1500 + Math.sin(a - 0.06) * 2200} L${540 + Math.cos(a + 0.06) * 2200} ${1500 + Math.sin(a + 0.06) * 2200} Z`} fill="#FFFFFF" opacity={0.35} />;
          })}
        </g>
      );
    case "pastel":
      // psikologi: manusia dan bentuk abstrak lembut (gumpalan pikiran, profil kepala)
      return (
        <g>
          <path d={`M${780 + Math.sin(frame / 60) * 10} 300 C980 260 1080 420 1000 540 C920 660 720 620 700 500 C680 380 640 320 ${780 + Math.sin(frame / 60) * 10} 300 Z`} fill="#F4C6E3" opacity={0.6} />
          <path d="M-60 900 C120 820 260 960 200 1080 C140 1200 -40 1160 -80 1040 Z" fill="#D8C4FA" opacity={0.6} />
          <path d="M760 1440 C700 1380 690 1290 720 1220 C680 1190 690 1140 730 1130 C720 1040 780 940 890 930 C1000 920 1080 1000 1080 1100 V1440 Z" fill="#FFFFFF" opacity={0.55} />
          <circle cx={900} cy={1080} r={44 + Math.sin(frame / 40) * 4} fill="none" stroke={lk.accent} strokeWidth={4} strokeDasharray="6 12" opacity={0.5} />
        </g>
      );
    case "dapur":
      // dapur: rak berisi stoples, ubin dinding, meja
      return (
        <g opacity={0.7}>
          {Array.from({ length: 6 }, (_, i) => (
            <line key={i} x1={0} x2={1080} y1={900 + i * 90} y2={900 + i * 90} stroke="#FFFFFF" strokeOpacity={0.7} strokeWidth={3} />
          ))}
          <rect x={620} y={360} width={420} height={16} rx={6} fill={lk.accent} opacity={0.5} />
          {([[660, "#F4A259"], [760, "#7FB069"], [860, "#E76F51"], [960, "#F6D55C"]] as [number, string][]).map(([x, col], i) => (
            <g key={i}>
              <rect x={x - 30} y={270} width={60} height={90} rx={14} fill="#FFFFFF" opacity={0.85} />
              <rect x={x - 30} y={300} width={60} height={60} rx={12} fill={col} opacity={0.75} />
            </g>
          ))}
          <rect x={-40} y={1330} width={1160} height={30} rx={8} fill={lk.accent} opacity={0.35} />
        </g>
      );
    case "kota":
      // kota siang: siluet gedung terang di kejauhan
      return (
        <g opacity={0.5}>
          {Array.from({ length: 11 }, (_, i) => {
            const h = 200 + rand(i + 3) * 380;
            return <rect key={i} x={i * 100 - 10} y={FLOOR - h} width={90} height={h} rx={6} fill="#FFFFFF" opacity={0.7} />;
          })}
        </g>
      );
    case "banding":
      // dua kondisi: kiri sejuk, kanan hangat
      return (
        <g>
          <path d="M0 0 H560 L480 1920 H0 Z" fill="#DCEAFF" opacity={0.8} />
          <path d="M560 0 H1080 V1920 H480 Z" fill="#FFE9CC" opacity={0.8} />
        </g>
      );
    case "bahaya":
      // garis peringatan diagonal di tepi bawah
      return (
        <g opacity={0.3}>
          {Array.from({ length: 14 }, (_, i) => (
            <path key={i} d={`M${i * 90 - 120} 1440 l60 -60 h40 l-60 60 Z`} fill={lk.accent} />
          ))}
        </g>
      );
    case "minimal":
      return <path d="M-200 1500 A900 900 0 0 1 1280 1300" fill="none" stroke={lk.accent} strokeWidth={3} opacity={0.18} />;
    case "brand":
      // logo A brand samar besar di sudut
      return <path d="M760 1300 L900 900 L1040 1300 M805 1170 H995" stroke="#FFFFFF" strokeWidth={26} fill="none" strokeLinecap="round" strokeLinejoin="round" opacity={0.1} />;
    case "malam":
      // bintang berkelip
      return (
        <g>
          {Array.from({ length: 40 }, (_, i) => (
            <circle key={i} cx={rand(i) * 1080} cy={rand(i + 50) * 1300} r={1.5 + rand(i + 90) * 2.5} fill="#FFFFFF" opacity={0.25 + 0.45 * Math.abs(Math.sin(frame / 30 + i))} />
          ))}
        </g>
      );
    case "bawahlaut":
      // berkas cahaya matahari dari permukaan, rumput laut bergoyang, terumbu di dasar
      return (
        <g>
          {Array.from({ length: 6 }, (_, i) => {
            const x = 120 + i * 170 + Math.sin(frame / 70 + i) * 30;
            return <path key={i} d={`M${x} -40 L${x + 70} -40 L${x + 260} 1500 L${x + 120} 1500 Z`} fill="#E6FBFF" opacity={0.07 + 0.04 * Math.sin(frame / 50 + i * 2)} />;
          })}
          {[60, 150, 930, 1020].map((x, i) => (
            <path key={i} d={`M${x} 1450 C${x - 30 + Math.sin(frame / 25 + i) * 25} 1300 ${x + 30 + Math.sin(frame / 30 + i) * 25} 1200 ${x + Math.sin(frame / 20 + i) * 35} ${1080 - i * 30}`} stroke={i % 2 ? "#2E8B57" : "#3DAA6B"} strokeWidth={18} fill="none" strokeLinecap="round" opacity={0.85} />
          ))}
          {[[230, 1430, 90, "#FF7F6E"], [340, 1440, 60, "#FFB36B"], [780, 1430, 100, "#E85D9A"], [880, 1445, 55, "#FF7F6E"]].map(([x, y, r, col], i) => (
            <g key={i}>
              {[0, 1, 2, 3].map((j) => <path key={j} d={`M${x} ${y} q${-40 + j * 27} ${-(r as number) * 0.7} ${-30 + j * 20} ${-(r as number)}`} stroke={col as string} strokeWidth={14} fill="none" strokeLinecap="round" />)}
            </g>
          ))}
        </g>
      );
    case "antariksa":
      // bintang berkelip, planet bercincin, nebula samar
      return (
        <g>
          <ellipse cx={300} cy={420} rx={420} ry={180} fill="#8E7BFF" opacity={0.12} transform="rotate(-20 300 420)" />
          {Array.from({ length: 70 }, (_, i) => (
            <circle key={i} cx={rand(i) * 1080} cy={rand(i + 70) * 1440} r={1 + rand(i + 140) * 2.6} fill="#FFFFFF" opacity={0.3 + 0.6 * Math.abs(Math.sin(frame / 40 + i))} />
          ))}
          <g transform={`translate(890 300) rotate(${frame * 0.05})`}>
            <circle r={95} fill="#E8A35C" />
            <path d="M-95 -10 Q0 -30 95 -10 M-90 25 Q0 5 90 25" stroke="#C7783A" strokeWidth={12} fill="none" opacity={0.6} />
            <ellipse rx={170} ry={36} fill="none" stroke="#F6D7A7" strokeWidth={10} transform="rotate(-18)" />
          </g>
          <circle cx={140} cy={1180} r={46} fill="#7FB3FF" opacity={0.85} />
        </g>
      );
    case "sabana":
      // matahari rendah, pohon akasia, rumput tinggi, bukit di kejauhan
      return (
        <g>
          <circle cx={760} cy={720} r={170} fill="#FFF1C9" opacity={0.85} />
          <path d="M-100 1150 C200 1080 420 1120 640 1100 C860 1080 980 1110 1180 1090 V1500 H-100 Z" fill="#D9B26A" opacity={0.8} />
          {[[200, 1130, 1], [880, 1100, 0.75]].map(([x, y, sc], i) => (
            <g key={i} transform={`translate(${x} ${y}) scale(${sc})`}>
              <path d="M0 0 L-6 -170 M-6 -120 L-70 -190 M-4 -140 L60 -200" stroke="#5A3B1C" strokeWidth={12} strokeLinecap="round" />
              <ellipse cx={-10} cy={-215} rx={170} ry={42} fill="#6B7F3A" />
            </g>
          ))}
          {Array.from({ length: 26 }, (_, i) => {
            const x = i * 44 - 20;
            return <path key={i} d={`M${x} 1440 q${8 + Math.sin(frame / 20 + i) * 8} -60 ${4 + Math.sin(frame / 22 + i) * 14} ${-110 - (i % 3) * 25}`} stroke={i % 2 ? "#B8923F" : "#A47E33"} strokeWidth={5} fill="none" />;
          })}
        </g>
      );
    case "jurnal":
      // halaman jurnal lapangan: garis buku, sketsa denah, catatan tangan, cap
      return (
        <g opacity={0.55}>
          <path d="M540 120 V1420" stroke={lk.accent} strokeOpacity={0.25} strokeWidth={4} />
          {Array.from({ length: 24 }, (_, i) => (
            <line key={i} x1={60} x2={1020} y1={200 + i * 52} y2={200 + i * 52} stroke="#6B8CB0" strokeOpacity={0.18} strokeWidth={2} />
          ))}
          <g transform="translate(700 170) rotate(6)" stroke={c} strokeOpacity={0.35} strokeWidth={3} fill="none">
            <rect width={260} height={180} strokeDasharray="10 8" />
            <path d="M30 140 L90 60 L150 110 L220 40" />
            <circle cx={90} cy={60} r={10} />
            <path d="M200 150 l14 -14 m0 14 l-14 -14" stroke="#B33A2A" strokeOpacity={0.6} />
          </g>
          <circle cx={170} cy={1290} r={64} fill="none" stroke={lk.accent} strokeWidth={5} strokeOpacity={0.4} />
          <text x={170} y={1300} textAnchor="middle" fontFamily="Georgia, serif" fontSize={26} fill={lk.accent} opacity={0.5}>EKSPEDISI</text>
        </g>
      );
    case "lab":
      // meja lab, rak tabung warna-warni, labu mendidih, rumus di papan
      return (
        <g opacity={0.6}>
          <rect x={-40} y={1330} width={1160} height={26} rx={6} fill={lk.accent} opacity={0.4} />
          <g transform="translate(40 1180)">
            <rect width={260} height={18} rx={4} fill="#9AB3BD" />
            {[0, 1, 2, 3, 4].map((i) => (
              <g key={i}>
                <rect x={18 + i * 48} y={-120} width={28} height={140} rx={14} fill="#FFFFFF" stroke="#7BA5B3" strokeWidth={4} />
                <rect x={21 + i * 48} y={-50} width={22} height={66} rx={10} fill={["#FF6B6B", "#FFD166", "#33D1A1", "#5C9DFF", "#B07BFF"][i]} opacity={0.8} />
              </g>
            ))}
          </g>
          <g transform="translate(820 1150)">
            <path d="M40 0 V60 L-10 170 Q-18 190 6 190 H134 Q158 190 150 170 L100 60 V0 Z" fill="#FFFFFF" stroke="#7BA5B3" strokeWidth={5} />
            <path d="M-2 140 H142 L150 170 Q158 190 134 190 H6 Q-18 190 -10 170 Z" fill="#33D1A1" opacity={0.75} />
            {[0, 1, 2].map((i) => {
              const p = ((frame / 40 + i / 3) % 1);
              return <circle key={i} cx={60 + i * 12} cy={160 - p * 140} r={6 + i * 2} fill="none" stroke="#33D1A1" strokeWidth={3} opacity={1 - p} />;
            })}
          </g>
          {/* rumus di bawah, jauh dari area judul */}
          <g fontFamily="Georgia, serif" fill={c} opacity={0.22} fontSize={30}>
            <text x={360} y={1250}>2H₂ + O₂ → 2H₂O</text>
            <text x={420} y={1300}>pH = 7</text>
          </g>
        </g>
      );
    case "teknologi":
      // perangkat dan antarmuka: jendela aplikasi, ponsel, jalur sirkuit
      return (
        <g opacity={0.3}>
          <rect x={700} y={200} width={340} height={220} rx={20} fill="none" stroke={lk.accent} strokeWidth={4} />
          <rect x={700} y={200} width={340} height={40} rx={20} fill={lk.accent} opacity={0.4} />
          {[0, 1, 2].map((i) => (
            <rect key={i} x={730} y={270 + i * 44} width={[240, 180, 260][i]} height={18} rx={9} fill={lk.ink} opacity={0.5} />
          ))}
          <rect x={60} y={1080} width={170} height={300} rx={28} fill="none" stroke={lk.accent} strokeWidth={4} />
          {[[300, 1380, 300, 1250, 480, 1250], [1040, 600, 900, 600, 900, 760]].map((d, i) => (
            <g key={i}>
              <polyline points={d.join(" ")} fill="none" stroke={lk.glow} strokeWidth={5} />
              <circle cx={d[d.length - 2]} cy={d[d.length - 1]} r={10} fill={lk.glow} />
            </g>
          ))}
        </g>
      );
    default:
      // cerah, data: hampir kosong supaya isi jadi fokus
      return null;
  }
};

// Tekstur per lingkungan
const Texture: React.FC<{ env: Env; lk: Look }> = ({ env, lk }) => {
  const e = canonEnv(env);
  if (e === "arsip" || e === "dapur" || e === "jurnal")
    // butiran kertas + vinyet hangat
    return (
      <>
        <svg width={W} height={H} style={{ position: "absolute", inset: 0, opacity: e === "arsip" || e === "jurnal" ? 0.22 : 0.1, mixBlendMode: "multiply" }}>
          <filter id={`kertas-${e}`}><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves={2} seed={4} /><feColorMatrix type="saturate" values="0" /></filter>
          <rect width={W} height={H} filter={`url(#kertas-${e})`} />
        </svg>
        <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 45%, transparent 55%, rgba(90,50,10,0.18) 100%)" }} />
      </>
    );
  if (e === "teknologi")
    return <AbsoluteFill style={{ backgroundImage: `linear-gradient(${lk.accent}14 2px, transparent 2px), linear-gradient(90deg, ${lk.accent}14 2px, transparent 2px)`, backgroundSize: "90px 90px", opacity: 0.6 }} />;
  if (e === "data")
    return <AbsoluteFill style={{ backgroundImage: `radial-gradient(${lk.ink}14 1.5px, transparent 2px)`, backgroundSize: "40px 40px", opacity: 0.5 }} />;
  if (e === "sains")
    return (
      <svg width={W} height={H} style={{ position: "absolute", inset: 0, opacity: 0.12 }}>
        <defs>
          <pattern id="heks" width={84} height={146} patternUnits="userSpaceOnUse">
            <path d="M42 0 L84 24 L84 73 L42 97 L0 73 L0 24 Z M42 97 V146" fill="none" stroke={lk.accent} strokeWidth={2} />
          </pattern>
        </defs>
        <rect width={W} height={H} fill="url(#heks)" />
      </svg>
    );
  return null;
};

// Partikel yang menggambarkan fenomena, hanya untuk lingkungan yang membutuhkannya
const MOTE: Partial<Record<Env, { n: number; kind: "bubble" | "leaf" | "bit" | "steam"; dir: -1 | 1 }>> = {
  sains: { n: 9, kind: "bubble", dir: -1 }, bawahlaut: { n: 14, kind: "bubble", dir: -1 }, sabana: { n: 5, kind: "leaf", dir: 1 }, lab: { n: 6, kind: "bubble", dir: -1 }, alam: { n: 7, kind: "leaf", dir: 1 }, teknologi: { n: 9, kind: "bit", dir: -1 }, dapur: { n: 5, kind: "steam", dir: -1 },
};

export const Environment: React.FC<{ env: Env; brand: Brand; floor?: boolean }> = ({ env, brand, floor = true }) => {
  const { frame } = useScene();
  const e = canonEnv(env);
  const lk = envLook(e, brand);
  const [a, b, c] = lk.sky;
  const m = MOTE[e];
  const motes = Array.from({ length: m?.n ?? 0 }, (_, i) => {
    const r = rand(i + 1);
    const y = ((i * 173 + frame * (0.4 + r * 0.8)) % (H + 80)) - 40;
    return { x: ((i * 97 + r * 300) % 1040) + 20 + Math.sin(frame / 40 + i) * 14, y: m!.dir < 0 ? H - y : y, s: 6 + r * 12, o: 0.25 + r * 0.3 };
  });
  return (
    <>
      <AbsoluteFill style={{ background: e === "langit" ? `linear-gradient(180deg, ${a} 0%, ${b} 55%, ${c} 100%)` : `radial-gradient(ellipse 120% 80% at 30% 18%, ${a} 0%, ${b} 52%, ${c} 100%)` }} />
      {/* cahaya lembut yang bergeser sangat pelan (gerak paling halus = latar) */}
      {e !== "minimal" && e !== "data" && <AbsoluteFill style={{ background: `radial-gradient(circle at ${70 + Math.sin(frame / 120) * 5}% ${30 + Math.cos(frame / 140) * 4}%, ${lk.glow}40 0%, transparent 45%)` }} />}
      <Texture env={e} lk={lk} />
      <Layer depth={0.3}>
        <svg width={W} height={H} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
          <Deco env={e} lk={lk} frame={frame} />
        </svg>
      </Layer>
      {floor && (
        <Layer depth={0.6}>
          {/* lantai: sangat lebar supaya tidak habis saat kamera bergeser jauh */}
          <div style={{ position: "absolute", left: -4000, right: -4000, top: FLOOR - 6, height: H - FLOOR + 200, background: `linear-gradient(180deg, ${lk.floor} 0%, ${c} 75%)` }} />
          <div style={{ position: "absolute", left: -200, right: -200, top: FLOOR - 6, height: 4, background: `linear-gradient(90deg, transparent, ${lk.accent}88, transparent)` }} />
        </Layer>
      )}
      {m && (
        <Layer depth={1.2}>
          {motes.map((d, i) =>
            m.kind === "steam" ? (
              <div key={i} style={{ position: "absolute", left: 700 + (i % 3) * 60 + Math.sin(frame / 25 + i) * 20, top: 300 - ((frame * 0.8 + i * 60) % 200), width: 30, height: 60, borderRadius: 30, background: "#FFFFFF", opacity: 0.3 * (1 - ((frame * 0.8 + i * 60) % 200) / 200), filter: "blur(6px)" }} />
            ) : (
              <div key={i} style={{ position: "absolute", left: d.x, top: d.y, width: d.s, height: m.kind === "leaf" ? d.s * 0.6 : d.s, borderRadius: m.kind === "bit" ? 2 : m.kind === "leaf" ? "50% 0" : "50%", background: m.kind === "bubble" ? "transparent" : m.kind === "leaf" ? "#4DB894" : lk.accent, border: m.kind === "bubble" ? `2px solid ${e === "bawahlaut" ? "#E6FBFF" : lk.accent}` : undefined, opacity: d.o, rotate: m.kind === "leaf" ? `${frame * 2 + i * 40}deg` : undefined }} />
            ),
          )}
        </Layer>
      )}
    </>
  );
};
