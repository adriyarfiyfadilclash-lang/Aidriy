import { ap, EASE, sp, useScene } from "./motion";

// Pustaka infografis animasi Adriy.project. Semua digambar dengan SVG di kanvas 1080 lebar,
// semua bergerak (tumbuh, tergambar, menghitung naik), dan semua menerima warna dari adegan
// supaya mengikuti latar topik. Titik penting dikembalikan lewat fungsi *Geometry supaya ADRI
// dan kamera bisa mengarah ke sana.

export type Ink = { ink: string; accent: string; primary: string; secondary: string; negative: string; bg: string; font: string };
export type Datum = { label: string; value: number };

const fmt = (v: number, decimals = 0) => v.toLocaleString("id-ID", { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
const dec = (v: number) => (Number.isInteger(v) ? 0 : 1);
const series = (k: Ink) => [k.primary, k.secondary, k.accent, k.negative, "#A98BFF", "#5CE1FF"];

// ================================================================ Angka menghitung naik
export const CountUp: React.FC<{ value: number; at: number; len?: number; unit?: string; size: number; color: string; unitColor?: string; font: string }> = ({ value, at, len = 1.3, unit, size, color, unitColor, font }) => {
  const { frame, fps } = useScene();
  const p = ap(frame, fps, at, len, EASE.out);
  // sedikit membesar saat selesai menghitung (penekanan)
  const pop = sp(frame, fps, at + len - 0.1, { damping: 9, stiffness: 160 });
  return (
    <div style={{ fontFamily: font, fontWeight: 800, fontSize: size, letterSpacing: -size * 0.04, lineHeight: 1, color, scale: `${1 + 0.06 * Math.sin(Math.min(1, pop) * Math.PI)}`, whiteSpace: "nowrap" }}>
      {fmt(value * p, dec(value))}
      {unit && <span style={{ fontSize: size * 0.5, color: unitColor ?? color, marginLeft: size * 0.04 }}>{unit}</span>}
    </div>
  );
};

// ================================================================ Grafik batang
export const barGeometry = (data: Datum[], x: number, y: number, w: number, h: number) => {
  const max = Math.max(...data.map((d) => d.value), 1);
  const n = data.length;
  const slot = w / n;
  const bw = Math.min(150, slot * 0.62);
  return data.map((d, i) => {
    const bh = (d.value / max) * h;
    return { x: x + slot * i + (slot - bw) / 2, w: bw, top: y + h - bh, h: bh, cx: x + slot * i + slot / 2 };
  });
};

export const BarChart: React.FC<{ data: Datum[]; x: number; y: number; w: number; h: number; at: number; k: Ink; unit?: string; focus?: number }> = ({ data, x, y, w, h, at, k, unit = "", focus }) => {
  const { frame, fps } = useScene();
  const g = barGeometry(data, x, y, w, h);
  const base = ap(frame, fps, at, 0.5);
  const hi = focus ?? data.reduce((m, d, i) => (d.value > data[m].value ? i : m), 0);
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      {/* garis bantu */}
      {[0.25, 0.5, 0.75].map((f) => (
        <line key={f} x1={x} x2={x + w * base} y1={y + h - h * f} y2={y + h - h * f} stroke={k.ink} strokeOpacity={0.12} strokeWidth={2} strokeDasharray="8 10" />
      ))}
      {g.map((b, i) => {
        const p = sp(frame, fps, at + 0.25 + i * 0.16, { damping: 14, stiffness: 90 });
        const bh = b.h * Math.min(p, 1.04);
        const on = i === hi;
        const v = data[i].value * Math.min(1, p);
        return (
          <g key={i}>
            <rect x={b.x} y={y + h - bh} width={b.w} height={bh} rx={18} fill={on ? k.accent : series(k)[i % 2]} opacity={on ? 1 : 0.85} />
            {/* kilap di sisi kiri batang */}
            <rect x={b.x + 12} y={y + h - bh + 14} width={10} height={Math.max(0, bh - 28)} rx={5} fill="#fff" opacity={0.22} />
            <text x={b.cx} y={y + h - bh - 22} textAnchor="middle" fontFamily={k.font} fontWeight={800} fontSize={on ? 54 : 42} fill={on ? k.accent : k.ink} opacity={Math.min(1, p * 1.5)}>
              {fmt(v, dec(data[i].value))}
              {unit}
            </text>
            <text x={b.cx} y={y + h + 52} textAnchor="middle" fontFamily={k.font} fontWeight={700} fontSize={34} fill={k.ink} opacity={ap(frame, fps, at + 0.3 + i * 0.16, 0.4) * 0.9}>
              {data[i].label}
            </text>
          </g>
        );
      })}
      <line x1={x - 10} x2={x + (w + 20) * base} y1={y + h} y2={y + h} stroke={k.ink} strokeWidth={5} strokeLinecap="round" />
    </svg>
  );
};

// ================================================================ Diagram lingkaran (donat)
// skipFirst: label segmen pertama tidak digambar di luar (ditampilkan besar di tengah oleh adegan)
export const Donut: React.FC<{ data: Datum[]; cx: number; cy: number; r: number; at: number; k: Ink; unit?: string; skipFirst?: boolean }> = ({ data, cx, cy, r, at, k, unit = "%", skipFirst }) => {
  const { frame, fps } = useScene();
  const total = data.reduce((s, d) => s + d.value, 0) || 1;
  const sw = r * 0.42;
  const C = 2 * Math.PI * r;
  let acc = 0;
  const cols = series(k);
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      <circle cx={cx} cy={cy} r={r} fill="none" stroke={k.ink} strokeOpacity={0.1} strokeWidth={sw} />
      {data.map((d, i) => {
        const f = d.value / total;
        const start = acc;
        acc += f;
        const p = ap(frame, fps, at + 0.2 + i * 0.35, 0.6, EASE.inOut);
        const mid = (start + f / 2) * Math.PI * 2 - Math.PI / 2;
        // label di luar cincin dengan garis penunjuk siku
        const ex = cx + Math.cos(mid) * (r + sw / 2 + 20);
        const ey = cy + Math.sin(mid) * (r + sw / 2 + 20);
        const right = Math.cos(mid) >= 0;
        const lx = ex + (right ? 60 : -60);
        const lp = ap(frame, fps, at + 0.55 + i * 0.35, 0.4);
        return (
          <g key={i}>
            <circle cx={cx} cy={cy} r={r} fill="none" stroke={cols[i % cols.length]} strokeWidth={sw * (i === 0 ? 1.12 : 1)} strokeDasharray={`${C * f * p} ${C}`} strokeDashoffset={-C * start} transform={`rotate(-90 ${cx} ${cy})`} />
            <g opacity={skipFirst && i === 0 ? 0 : lp}>
              <polyline points={`${cx + Math.cos(mid) * (r + sw / 2 - 4)},${cy + Math.sin(mid) * (r + sw / 2 - 4)} ${ex},${ey} ${lx},${ey}`} fill="none" stroke={k.ink} strokeWidth={3} />
              <circle cx={lx} cy={ey} r={7} fill={cols[i % cols.length]} />
              <text x={lx + (right ? 16 : -16)} y={ey - 6} textAnchor={right ? "start" : "end"} fontFamily={k.font} fontWeight={800} fontSize={48} fill={cols[i % cols.length]}>
                {fmt(Math.round(f * 100 * lp))}
                {unit}
              </text>
              <text x={lx + (right ? 16 : -16)} y={ey + 34} textAnchor={right ? "start" : "end"} fontFamily={k.font} fontWeight={700} fontSize={30} fill={k.ink}>
                {d.label}
              </text>
            </g>
          </g>
        );
      })}
    </svg>
  );
};

// ================================================================ Linimasa sejarah (horizontal, bisa lebih lebar dari layar)
export type Event = { year: string; label: string };
export const timelineX = (i: number, x0: number, gap: number) => x0 + i * gap;

export const Timeline: React.FC<{ events: Event[]; x0: number; y: number; gap: number; at: number; k: Ink; active?: number }> = ({ events, x0, y, gap, at, k, active }) => {
  const { frame, fps } = useScene();
  const span = (events.length - 1) * gap;
  const draw = ap(frame, fps, at, 0.6 + events.length * 0.45, EASE.inOut);
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      <line x1={x0 - 120} x2={x0 - 120 + (span + 240) * draw} y1={y} y2={y} stroke={k.ink} strokeOpacity={0.35} strokeWidth={8} strokeLinecap="round" />
      <line x1={x0} x2={x0 + span * draw} y1={y} y2={y} stroke={k.accent} strokeWidth={8} strokeLinecap="round" />
      {events.map((e, i) => {
        const cx = timelineX(i, x0, gap);
        const p = sp(frame, fps, at + 0.3 + i * 0.45, { damping: 11, stiffness: 140 });
        const on = i === active;
        const up = i % 2 === 0; // label bergantian atas/bawah supaya tidak bertabrakan
        return (
          <g key={i} opacity={Math.min(1, p)}>
            <rect x={cx - 22} y={y - 22} width={44} height={44} rx={10} transform={`rotate(45 ${cx} ${y})`} fill={on ? k.accent : k.bg} stroke={k.accent} strokeWidth={6} />
            <line x1={cx} x2={cx} y1={up ? y - 34 : y + 34} y2={up ? y - 34 - 70 * p : y + 34 + 70 * p} stroke={k.ink} strokeOpacity={0.5} strokeWidth={3} />
            <text x={cx} y={up ? y - 128 : y + 180} textAnchor="middle" fontFamily={k.font} fontWeight={800} fontSize={on ? 84 : 66} fill={on ? k.accent : k.ink} letterSpacing={-2}>
              {e.year}
            </text>
            <foreignObject x={cx - 190} y={up ? y - 128 - 86 - 140 : y + 196} width={380} height={140}>
              <div style={{ fontFamily: k.font, fontWeight: 700, fontSize: 34, lineHeight: 1.2, color: k.ink, textAlign: "center", opacity: 0.9, display: "flex", alignItems: up ? "flex-end" : "flex-start", justifyContent: "center", height: "100%" }}>{e.label}</div>
            </foreignObject>
          </g>
        );
      })}
    </svg>
  );
};

// ================================================================ Peta (Indonesia, disederhanakan)
// Bentuk pulau disederhanakan dalam kotak 1000x420 (x: barat ke timur), lalu diskalakan.
const ISLANDS = [
  "M40 70 L120 40 L210 120 L290 200 L330 250 L300 270 L220 220 L140 160 L70 110 Z", // Sumatra
  "M320 300 L420 290 L520 300 L600 320 L560 335 L450 330 L340 322 Z", // Jawa
  "M610 330 L650 326 L700 334 L760 340 L720 352 L640 346 Z", // Nusa Tenggara
  "M380 120 L460 70 L560 80 L600 150 L560 230 L470 240 L400 200 Z", // Kalimantan
  "M640 130 L690 110 L700 170 L740 160 L760 190 L700 210 L690 260 L660 230 L650 180 Z", // Sulawesi
  "M790 170 L880 160 L960 200 L990 260 L940 280 L860 250 L800 220 Z", // Papua
  "M760 150 L775 140 L780 175 L765 185 Z", // Maluku
];
export const mapPoint = (u: number, v: number, x: number, y: number, w: number) => [x + u * w, y + v * (w * 0.42)] as const;

export type Pin = { u: number; v: number; label: string };
export const MapView: React.FC<{ pins: Pin[]; x: number; y: number; w: number; at: number; k: Ink; route?: boolean; land?: string }> = ({ pins, x, y, w, at, k, route = true, land }) => {
  const { frame, fps } = useScene();
  const s = w / 1000;
  const reveal = ap(frame, fps, at, 0.8, EASE.inOut);
  const pts = pins.map((p) => mapPoint(p.u, p.v, x, y, w));
  const routeD = pts.map((p, i) => (i ? `Q${(pts[i - 1][0] + p[0]) / 2} ${Math.min(pts[i - 1][1], p[1]) - 120} ${p[0]} ${p[1]}` : `M${p[0]} ${p[1]}`)).join(" ");
  const rp = ap(frame, fps, at + 0.6 + pins.length * 0.3, 0.9, EASE.inOut);
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      {/* laut: garis lintang/bujur samar */}
      {Array.from({ length: 6 }, (_, i) => (
        <line key={i} x1={x} x2={x + w} y1={y + i * 84 * s} y2={y + i * 84 * s} stroke={k.ink} strokeOpacity={0.08} strokeWidth={2} />
      ))}
      <g transform={`translate(${x} ${y}) scale(${s})`} style={{ clipPath: `inset(0 ${(1 - reveal) * 100}% 0 0)` }}>
        {ISLANDS.map((d, i) => (
          <path key={i} d={d} fill={land ?? k.secondary} opacity={0.9} stroke={k.ink} strokeOpacity={0.35} strokeWidth={3 / s} strokeLinejoin="round" />
        ))}
      </g>
      {route && pins.length > 1 && <path d={routeD} fill="none" stroke={k.accent} strokeWidth={6} strokeDasharray="16 12" pathLength={1000} strokeDashoffset={0} style={{ clipPath: `inset(0 ${(1 - rp) * 100}% 0 0)` }} />}
      {pins.map((p, i) => {
        const [px, py] = pts[i];
        const drop = sp(frame, fps, at + 0.5 + i * 0.3, { damping: 10, stiffness: 150 });
        const pulse = (frame / fps - (at + 0.8 + i * 0.3)) % 1.4;
        return (
          <g key={i} opacity={Math.min(1, drop * 2)}>
            {pulse > 0 && <circle cx={px} cy={py} r={14 + pulse * 40} fill="none" stroke={k.accent} strokeWidth={4} opacity={1 - pulse / 1.4} />}
            <g transform={`translate(${px} ${py - (1 - Math.min(drop, 1)) * 120})`}>
              <path d="M0 0 C-30 -40 -34 -58 -34 -70 A34 34 0 1 1 34 -70 C34 -58 30 -40 0 0 Z" fill={k.accent} stroke={k.bg} strokeWidth={4} />
              <circle cx={0} cy={-70} r={13} fill={k.bg} />
            </g>
            <text x={px} y={py + 56} textAnchor="middle" fontFamily={k.font} fontWeight={800} fontSize={36} fill={k.ink} stroke={k.bg} strokeWidth={8} paintOrder="stroke">
              {p.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
};

// ================================================================ Diagram alir (dengan percabangan)
export const FlowChart: React.FC<{ nodes: string[]; decision?: string; yes?: string; no?: string; x: number; y: number; w: number; gap: number; at: number; k: Ink; active: number }> = ({ nodes, decision, yes = "Ya", no = "Tidak", x, y, w, gap, at, k, active }) => {
  const { frame, fps } = useScene();
  const items = decision ? [...nodes.slice(0, Math.max(1, nodes.length - 1)), decision] : nodes;
  const tail = decision ? nodes.slice(-1)[0] : undefined;
  const cx = x + w / 2;
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      <defs>
        <marker id="flow-arrow" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="6" markerHeight="6" orient="auto">
          <path d="M0 0 L10 5 L0 10 Z" fill={k.accent} />
        </marker>
      </defs>
      {items.map((t, i) => {
        const cy = y + i * gap;
        const p = sp(frame, fps, at + i * 0.4, { damping: 14 });
        const isDec = decision && i === items.length - 1;
        const on = i === active;
        const a = ap(frame, fps, at + i * 0.4 + 0.25, 0.35);
        const fill = on ? k.accent : `${k.ink}14`;
        const tc = on ? k.bg : k.ink;
        return (
          <g key={i}>
            {i > 0 && <line x1={cx} x2={cx} y1={cy - gap + 70} y2={cy - gap + 70 + (gap - 150) * a} stroke={k.accent} strokeWidth={6} markerEnd={a > 0.95 ? "url(#flow-arrow)" : undefined} />}
            <g transform={`translate(${cx} ${cy}) scale(${Math.min(1, p)})`}>
              {isDec ? (
                <path d={`M0 -86 L${w * 0.34} 0 L0 86 L${-w * 0.34} 0 Z`} fill={fill} stroke={k.accent} strokeWidth={5} />
              ) : (
                <rect x={-w / 2} y={-62} width={w} height={124} rx={62} fill={fill} stroke={on ? k.accent : `${k.ink}30`} strokeWidth={4} />
              )}
              {/* ukuran huruf menyesuaikan lebar bentuk supaya teks tidak keluar */}
              <text x={0} y={14} textAnchor="middle" fontFamily={k.font} fontWeight={800} fontSize={Math.min(isDec ? 38 : 42, ((isDec ? w * 0.48 : w * 0.88) / Math.max(t.length, 1)) * 1.8)} fill={tc}>
                {t}
              </text>
            </g>
          </g>
        );
      })}
      {decision && tail && (() => {
        const cy = y + (items.length - 1) * gap;
        const a = ap(frame, fps, at + items.length * 0.4, 0.45);
        const dx = w * 0.34;
        return (
          <g opacity={a}>
            <path d={`M${cx + dx} ${cy} H${cx + dx + 90} V${cy + gap * 0.9}`} fill="none" stroke={k.secondary} strokeWidth={6} markerEnd="url(#flow-arrow)" />
            <text x={cx + dx + 20} y={cy - 18} fontFamily={k.font} fontWeight={800} fontSize={32} fill={k.secondary}>{yes}</text>
            <path d={`M${cx - dx} ${cy} H${cx - dx - 90} V${cy + gap * 0.9}`} fill="none" stroke={k.negative} strokeWidth={6} markerEnd="url(#flow-arrow)" />
            <text x={cx - dx - 20} y={cy - 18} textAnchor="end" fontFamily={k.font} fontWeight={800} fontSize={32} fill={k.negative}>{no}</text>
            <rect x={cx + dx - 60} y={cy + gap * 0.9 + 20} width={300} height={100} rx={24} fill={k.secondary} />
            <text x={cx + dx + 90} y={cy + gap * 0.9 + 82} textAnchor="middle" fontFamily={k.font} fontWeight={800} fontSize={36} fill={k.bg}>{tail}</text>
            <rect x={cx - dx - 240} y={cy + gap * 0.9 + 20} width={300} height={100} rx={24} fill={`${k.negative}33`} stroke={k.negative} strokeWidth={4} />
            <text x={cx - dx - 90} y={cy + gap * 0.9 + 82} textAnchor="middle" fontFamily={k.font} fontWeight={800} fontSize={36} fill={k.ink}>Ulangi</text>
          </g>
        );
      })()}
    </svg>
  );
};

// ================================================================ Persentase (waffle 10x10)
export const Waffle: React.FC<{ value: number; x: number; y: number; size: number; at: number; k: Ink }> = ({ value, x, y, size, at, k }) => {
  const { frame, fps } = useScene();
  const cell = size / 10;
  const filled = Math.round(value * ap(frame, fps, at, 1.4, EASE.out));
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      {Array.from({ length: 100 }, (_, i) => {
        // isi dari bawah ke atas, kiri ke kanan
        const col = i % 10;
        const row = 9 - Math.floor(i / 10);
        const on = i < filled;
        const appear = ap(frame, fps, at - 0.4 + (col + (9 - row)) * 0.02, 0.3);
        return <rect key={i} x={x + col * cell + 4} y={y + row * cell + 4} width={cell - 8} height={cell - 8} rx={cell * 0.22} fill={on ? k.accent : k.ink} opacity={(on ? 1 : 0.12) * appear} transform={on && i === filled - 1 ? `rotate(8 ${x + col * cell + cell / 2} ${y + row * cell + cell / 2})` : undefined} />;
      })}
    </svg>
  );
};

// ================================================================ Label dan penunjuk
export const Callout: React.FC<{ from: [number, number]; to: [number, number]; label: string; at: number; k: Ink; width?: number; align?: "left" | "right" }> = ({ from, to, label, at, k, width = 360, align }) => {
  const { frame, fps } = useScene();
  const p = ap(frame, fps, at, 0.45);
  const t = ap(frame, fps, at + 0.3, 0.4);
  const right = align ? align === "right" : to[0] >= from[0];
  const elbow: [number, number] = [to[0], from[1] + (to[1] - from[1]) * 0.0];
  const pulse = 1 + 0.25 * Math.sin(frame / 7);
  return (
    <>
      <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
        <circle cx={from[0]} cy={from[1]} r={16 * pulse * p} fill="none" stroke={k.accent} strokeWidth={4} />
        <circle cx={from[0]} cy={from[1]} r={9 * p} fill={k.accent} />
        <polyline points={`${from[0]},${from[1]} ${elbow[0]},${elbow[1]} ${to[0]},${to[1]}`} fill="none" stroke={k.accent} strokeWidth={4} pathLength={1} strokeDasharray={1} strokeDashoffset={1 - p} />
      </svg>
      <div style={{ position: "absolute", left: right ? to[0] : to[0] - width, top: to[1] - 34, width, textAlign: right ? "left" : "right", fontFamily: k.font, fontWeight: 800, fontSize: 38, lineHeight: 1.15, color: k.ink, opacity: t, translate: `${(1 - t) * (right ? 20 : -20)}px 0px` }}>
        <span style={{ borderBottom: `5px solid ${k.accent}`, paddingBottom: 4 }}>{label}</span>
      </div>
    </>
  );
};

// ================================================================ Potongan bagian dalam objek
// Cangkang kapsul terbelah dua, lalu lapisan dalamnya terlihat satu per satu dengan label.
export const cutawayLayers = (n: number, cx: number, cy: number, w: number, h: number) =>
  Array.from({ length: n }, (_, i) => {
    const f = 1 - (i + 1) / (n + 1.2);
    return { w: w * f, h: h * f, cx, cy, edge: [cx + (w * f) / 2 - 10, cy - (h * f) / 2 + 30 + i * 30] as [number, number] };
  });

export const Cutaway: React.FC<{ layers: string[]; cx: number; cy: number; w: number; h: number; at: number; k: Ink; shell?: string }> = ({ layers, cx, cy, w, h, at, k, shell }) => {
  const { frame, fps } = useScene();
  const open = ap(frame, fps, at + 0.4, 0.8, EASE.inOut);
  const g = cutawayLayers(layers.length, cx, cy, w, h);
  const cols = [k.primary, k.secondary, k.accent, k.negative];
  const halfShift = open * w * 0.32;
  return (
    <svg width={1080} height={1920} style={{ position: "absolute", inset: 0, overflow: "visible" }}>
      <defs>
        <clipPath id="cut-left"><rect x={cx - w} y={cy - h} width={w} height={h * 2} /></clipPath>
        <clipPath id="cut-right"><rect x={cx} y={cy - h} width={w} height={h * 2} /></clipPath>
      </defs>
      {/* lapisan dalam */}
      {g.map((l, i) => {
        const p = ap(frame, fps, at + 0.9 + i * 0.3, 0.45);
        return <ellipse key={i} cx={l.cx} cy={l.cy} rx={(l.w / 2) * p} ry={(l.h / 2) * p} fill={cols[i % cols.length]} opacity={0.92} stroke={k.bg} strokeWidth={6} />;
      })}
      {/* cangkang: dua belahan yang bergeser keluar */}
      {(["cut-left", "cut-right"] as const).map((id, i) => (
        <g key={id} clipPath={`url(#${id})`} transform={`translate(${(i ? 1 : -1) * halfShift} 0) rotate(${(i ? 1 : -1) * open * 6} ${cx} ${cy})`}>
          <ellipse cx={cx} cy={cy} rx={w / 2} ry={h / 2} fill={shell ?? k.ink} opacity={0.18 + 0.82 * (1 - open)} stroke={shell ?? k.ink} strokeWidth={10} />
          <ellipse cx={cx} cy={cy} rx={w / 2 - 26} ry={h / 2 - 26} fill="none" stroke={k.bg} strokeOpacity={0.3} strokeWidth={6} strokeDasharray="20 14" />
        </g>
      ))}
    </svg>
  );
};
