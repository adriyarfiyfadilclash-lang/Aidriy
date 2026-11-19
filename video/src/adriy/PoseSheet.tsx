import { AbsoluteFill, type CalculateMetadataFunction, Sequence, staticFile } from "remotion";
import { Adri, type Pose } from "./Adri";
import type { Brand } from "./scenes";

// Lembar pose ADRI (gambar diam): semua pose berdampingan untuk memeriksa konsistensi identitas.
const ALL: Pose[] = ["sapa", "jelaskan", "tunjuk", "angkat", "jalan", "kaget", "pikir", "pegang", "sentuh", "lihat", "ajak", "senang", "tanya", "banding", "rayakan", "simpulkan", "tunjukkan"];

export type PoseSheetProps = { styleId: string; brand: Brand | null; at: number };

export const calculatePoseSheetMetadata: CalculateMetadataFunction<PoseSheetProps> = async ({ props }) => {
  const brand = await fetch(staticFile(`styles/${props.styleId}.json`)).then((r) => (r.ok ? (r.json() as Promise<Brand>) : null)).catch(() => null);
  return { props: { ...props, brand } };
};

const Box: React.FC = () => (
  <g>
    <rect x={-60} y={-50} width={120} height={90} rx={14} fill="#FFB547" />
    <rect x={-60} y={-50} width={120} height={22} rx={10} fill="#E09A30" />
  </g>
);

export const PoseSheet: React.FC<PoseSheetProps> = ({ brand, at }) => {
  if (!brand) return <AbsoluteFill style={{ background: "#300" }} />;
  return (
    <AbsoluteFill style={{ background: "#132457" }}>
      <Sequence from={-Math.round(at * 30)} layout="none">
        {ALL.map((p, i) => (
          <div key={p} style={{ position: "absolute", left: (i % 4) * 270, top: Math.floor(i / 4) * 380 + 10, width: 270, height: 370, display: "flex", flexDirection: "column", alignItems: "center" }}>
            <Adri spec={brand.character} pose={p} size={320} talking={p === "jelaskan"} holding={p === "pegang" ? <Box /> : undefined} seed={i} />
            <div style={{ color: "#fff", fontFamily: "Plus Jakarta Sans", fontWeight: 700, fontSize: 24 }}>{p}</div>
          </div>
        ))}
      </Sequence>
    </AbsoluteFill>
  );
};
