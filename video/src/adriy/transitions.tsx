import { useContext } from "react";
import { AbsoluteFill, interpolate } from "remotion";
import { clamp, EASE, H, NextTransition, OVERLAP, speedBlur, useScene, W } from "./motion";

// Transisi antar adegan. Adegan baru masuk DI ATAS adegan lama selama OVERLAP detik (kontinuitas,
// bukan layar kosong), dan adegan lama keluar dengan gerak yang melengkapi transisi adegan berikutnya.
//   iris   = lingkaran terbuka dari objek fokus (berbasis objek)
//   shape  = bentuk belah ketupat brand membesar (shape transition)
//   morph  = gumpalan yang berubah bentuk sampai menutup layar
//   wipe   = sapuan diagonal dengan garis aksen
//   zoom   = menembus ke dalam (zoom-through)
//   whip   = kamera menyapu cepat dengan motion blur
//   push   = adegan baru mendorong adegan lama ke atas
//   slide  = geser horizontal (lama)
//   fade   = larut (hanya untuk lanjutan yang sangat halus)

export type Transition = "iris" | "shape" | "morph" | "wipe" | "zoom" | "whip" | "push" | "slide" | "fade";
export const TRANSITIONS: Transition[] = ["iris", "shape", "morph", "wipe", "zoom", "whip", "push", "slide", "fade"];

const blob = (p: number, cx: number, cy: number, seed: number) => {
  // poligon 24 titik; jari-jari bergelombang, makin besar sampai menutup layar
  const R = p * Math.hypot(W, H) * 1.05;
  const pts = Array.from({ length: 24 }, (_, i) => {
    const a = (i / 24) * Math.PI * 2;
    const wob = 1 + 0.18 * (1 - p) * Math.sin(a * 3 + seed) + 0.1 * (1 - p) * Math.sin(a * 5 - seed * 2);
    return `${cx + Math.cos(a) * R * wob}px ${cy + Math.sin(a) * R * wob}px`;
  });
  return `polygon(${pts.join(",")})`;
};

export const SceneFrame: React.FC<{ enter: string; focus?: [number, number]; accent: string; children: React.ReactNode; seed?: number }> = ({ enter, focus = [W / 2, H / 2], accent, children, seed = 0 }) => {
  const { frame, fps, len } = useScene();
  const next = useContext(NextTransition);
  const d = OVERLAP * fps;
  const e = interpolate(frame, [0, d], [0, 1], { ...clamp, easing: EASE.inOut });
  const ePrev = interpolate(frame - 1, [0, d], [0, 1], { ...clamp, easing: EASE.inOut });
  const entering = frame < d;
  const x = next ? interpolate(frame, [len - d, len], [0, 1], { ...clamp, easing: EASE.inOut }) : 0;
  const xPrev = next ? interpolate(frame - 1, [len - d, len], [0, 1], { ...clamp, easing: EASE.inOut }) : 0;

  const st: React.CSSProperties = {};
  const tf: string[] = [];
  const filters: string[] = [];
  let stripe: React.ReactNode = null;
  const [fx, fy] = focus;

  if (entering) {
    switch (enter as Transition) {
      case "iris":
      {
        const r = e * Math.hypot(W, H);
        st.clipPath = `circle(${r}px at ${fx}px ${fy}px)`;
        // cincin aksen di tepi bukaan
        stripe = <div style={{ position: "absolute", left: fx - r, top: fy - r, width: 2 * r, height: 2 * r, borderRadius: "50%", border: `16px solid ${accent}`, boxSizing: "border-box" }} />;
        break;
      }
      case "shape": {
        const r = e * Math.hypot(W, H) * 1.45;
        st.clipPath = `polygon(${fx}px ${fy - r}px, ${fx + r}px ${fy}px, ${fx}px ${fy + r}px, ${fx - r}px ${fy}px)`;
        tf.push(`scale(${1.08 - 0.08 * e})`);
        break;
      }
      case "morph":
        st.clipPath = blob(e, fx, fy, seed);
        break;
      case "wipe": {
        // garis diagonal dari kiri bawah ke kanan atas
        const off = (1 - e) * (W + H * 0.4);
        st.clipPath = `polygon(0px 0px, ${W - off + H * 0.4}px 0px, ${W - off}px ${H}px, 0px ${H}px)`;
        const edge = W - off;
        stripe = <div style={{ position: "absolute", left: edge - 30, top: -200, width: 30, height: H + 400, background: accent, rotate: `${Math.atan2(H * 0.4, H) * (180 / Math.PI)}deg`, transformOrigin: "bottom left", opacity: e < 1 ? 1 : 0 }} />;
        break;
      }
      case "zoom":
        // datang dari depan kamera (besar ke normal) sementara adegan lama terdorong masuk
        tf.push(`scale(${1.6 - 0.6 * e})`);
        st.opacity = Math.min(1, e * 1.8);
        filters.push(`blur(${(1 - e) * 8}px)`);
        break;
      case "whip": {
        const tx = (1 - e) * W;
        const v = ((1 - e) - (1 - ePrev)) * W;
        tf.push(`translateX(${tx}px)`);
        filters.push(`blur(${speedBlur(v, 10, 16)}px)`);
        break;
      }
      case "push":
        tf.push(`translateY(${(1 - e) * H}px)`);
        break;
      case "slide":
        tf.push(`translateX(${(1 - e) * W}px)`);
        break;
      default:
        st.opacity = e;
    }
  }
  let dim = 0;
  if (x > 0) {
    switch (next as Transition) {
      case "zoom":
        tf.push(`scale(${1 + 0.45 * x})`);
        filters.push(`blur(${x * 6}px)`);
        break;
      case "whip": {
        const v = (x - xPrev) * W;
        tf.push(`translateX(${-x * W}px)`);
        filters.push(`blur(${speedBlur(v, 10, 16)}px)`);
        break;
      }
      case "push":
        tf.push(`translateY(${-x * H * 0.3}px)`);
        dim = x * 0.5;
        break;
      case "slide":
        tf.push(`translateX(${-x * W * 0.3}px)`);
        dim = x * 0.5;
        break;
      case "fade":
        break;
      default:
        tf.push(`scale(${1 + 0.06 * x})`);
        dim = x * 0.35;
    }
  }
  return (
    <AbsoluteFill style={{ ...st, transform: tf.join(" ") || undefined, filter: filters.filter((f) => !f.includes("(0px)")).join(" ") || undefined, overflow: "hidden" }}>
      {children}
      {dim > 0 && <AbsoluteFill style={{ background: "#000", opacity: dim }} />}
      {entering && stripe}
    </AbsoluteFill>
  );
};
