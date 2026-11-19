// Pemeriksa visual dan teknis Adriy.project (gambar diam, bukan video).
//
//   node scripts/qa.mjs --media <nama>          periksa video dari public/media/<nama>/scenes.json
//   node scripts/qa.mjs --samples               periksa contoh semua jenis adegan (ScenePreview)
//   opsi: --out <folder> (default out/qa), --bundle <folder bundle jadi>
//
// Untuk tiap adegan diambil beberapa frame: tepat setelah transisi masuk selesai, di tengah, dan
// menjelang akhir. Komponen QAProbe mengukur elemen bertanda data-qa di browser, lalu skrip ini memeriksa:
//   - teks/objek/karakter/subtitle keluar layar atau terpotong
//   - teks terpotong di dalam kotaknya
//   - subtitle bertabrakan dengan karakter, objek, atau teks
//   - teks tertutup karakter, teks saling bertabrakan
//   - elemen adegan sebelumnya tertinggal setelah transisi selesai
//   - blur yang tidak disengaja di tengah adegan
//   - urutan lapisan (subtitle harus paling atas)
//   - komposisi berat sebelah
//   - transisi terlalu panjang, jenis komposisi berulang (dari scenes.json)
import { bundle } from "@remotion/bundler";
import { openBrowser, renderStill, selectComposition } from "@remotion/renderer";
import fs from "node:fs";
import path from "node:path";

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith("--") ? [...acc, [a.slice(2), all[i + 1] && !all[i + 1].startsWith("--") ? all[i + 1] : true]] : acc), []),
);
const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const out = path.resolve(root, args.out ?? "out/qa");
fs.mkdirSync(out, { recursive: true });
const W = 1080, H = 1920, FPS = 30;

const serveUrl = args.bundle ? path.resolve(args.bundle) : await bundle({ entryPoint: path.join(root, "src/index.ts"), onProgress: () => {} });
const browser = await openBrowser("chrome", { logLevel: "error" });

// ---------------------------------------------------------------- daftar frame yang diperiksa
const jobs = [];
let plan = [];
if (args.media) {
  plan = JSON.parse(fs.readFileSync(path.join(root, "public/media", args.media, "scenes.json"), "utf8"));
  plan.forEach((s, i) => {
    const enterEnd = s.start + (i ? Math.max(0, plan[i - 1].end - s.start) : 0);
    const exitStart = plan[i + 1] ? plan[i + 1].start : s.end;
    const at = (t) => Math.min(Math.round(t * FPS), Math.round(s.end * FPS) - 1);
    if (i) jobs.push({ name: `${String(i + 1).padStart(2, "0")}-${s.type}-transisi`, comp: "AdriyVideo", props: { media: args.media, qa: true }, frame: Math.round(((s.start + enterEnd) / 2) * FPS), scene: i, phase: "transisi" });
    jobs.push({ name: `${String(i + 1).padStart(2, "0")}-${s.type}-masuk`, comp: "AdriyVideo", props: { media: args.media, qa: true }, frame: at(enterEnd) + 1, scene: i, phase: "masuk" });
    jobs.push({ name: `${String(i + 1).padStart(2, "0")}-${s.type}-tengah`, comp: "AdriyVideo", props: { media: args.media, qa: true }, frame: at((enterEnd + exitStart) / 2 + 0.6), scene: i, phase: "tengah" });
    jobs.push({ name: `${String(i + 1).padStart(2, "0")}-${s.type}-akhir`, comp: "AdriyVideo", props: { media: args.media, qa: true }, frame: at(exitStart - 0.1), scene: i, phase: "akhir" });
  });
} else {
  const types = ["karakter", "objek", "infografis", "diagram", "perbandingan", "tipografi", "sinematik", "angka", "grafik", "lingkaran", "timeline", "peta", "alur", "potongan", "persentase", "minimalis"];
  for (const t of types) for (const at of [1.2, 3.2, 5.0]) jobs.push({ name: `${t}-${at}`, comp: "ScenePreview", props: { type: t, at, qa: true }, frame: 0, scene: 0, phase: at < 1.5 ? "masuk" : "tengah" });
  jobs.push({ name: "objek-full-3.2", comp: "ScenePreview", props: { type: "objek", at: 3.2, qa: true, extra: { layout: "full" } }, frame: 0, scene: 0, phase: "tengah" });
}

// ---------------------------------------------------------------- render + ukur
const comps = {};
const results = [];
for (const j of jobs) {
  const key = j.comp + JSON.stringify(j.props);
  comps[key] ??= await selectComposition({ serveUrl, id: j.comp, inputProps: j.props, puppeteerInstance: browser, logLevel: "error" });
  let probe = null;
  await renderStill({
    composition: comps[key], serveUrl, inputProps: j.props, frame: j.frame, output: path.join(out, `${j.name}.png`), puppeteerInstance: browser, logLevel: "error",
    onBrowserLog: (log) => { if (log.text.startsWith("QA:")) probe = JSON.parse(log.text.slice(3)); },
  });
  results.push({ ...j, probe });
  process.stdout.write(".");
}
await browser.close({ silent: true });
// bundle sementara besar (menyalin public/): hapus setelah selesai kalau dibuat di sini
if (!args.bundle) fs.rmSync(serveUrl, { recursive: true, force: true });
process.stdout.write("\n");

// ---------------------------------------------------------------- analisis
const area = (r) => Math.max(0, r.w) * Math.max(0, r.h);
const inter = (a, b) => Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x)) * Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
const issues = [];
const add = (j, level, msg) => issues.push({ frame: j.name, level, msg });

for (const j of results) {
  if (!j.probe) { add(j, "galat", "probe QA tidak mengirim data"); continue; }
  const vis = j.probe.items.filter((e) => e.op > 0.05 && e.w > 2 && e.h > 2);
  const mine = vis.filter((e) => e.scene === j.scene || e.scene === -1 || j.comp === "ScenePreview");
  // elemen adegan lain yang masih terlihat setelah transisi selesai
  if (j.phase === "transisi") continue; // titik tengah transisi: hanya disimpan sebagai gambar untuk diperiksa mata
  if (j.comp === "AdriyVideo" && j.phase !== "akhir") {
    const left = vis.filter((e) => e.scene !== j.scene && e.scene !== -1);
    if (left.length) add(j, "masalah", `elemen adegan sebelumnya tertinggal: ${left.map((e) => `${e.kind} "${e.text}"`).slice(0, 3).join(", ")}`);
  }
  const settled = j.phase !== "masuk";
  // kotak ADRI lebih lebar dari badannya (ruang untuk lengan): ukur badan saja (24%-76% lebar)
  const body = (e) => (e.kind === "adri" ? { ...e, x: e.x + e.w * 0.24, w: e.w * 0.52, h: e.h * (1150 / 1180) } : e);
  for (const e0 of mine) {
    if (e0.wide) continue;
    const e = body(e0);
    const out = e.x < -4 || e.y < -4 || e.x + e.w > W + 4 || e.y + e.h > H + 4;
    if (out && settled) add(j, "masalah", `${e.kind} keluar layar/terpotong: "${e.text}" (${Math.round(e.x)},${Math.round(e.y)} ${Math.round(e.w)}x${Math.round(e.h)})`);
    else if (e.kind === "text" && settled && (e.x < 30 || e.x + e.w > W - 30)) add(j, "catatan", `teks terlalu dekat tepi: "${e.text}"`);
    if (e.overflow) add(j, "masalah", `teks terpotong di dalam kotaknya: "${e.text}"`);
    if (j.phase === "tengah" && e.blur > 0.5 && (e.kind === "text" || e.kind === "caption")) add(j, "masalah", `blur tidak sengaja (${e.blur}px) pada ${e.kind}: "${e.text}"`);
  }
  const caps = mine.filter((e) => e.kind === "caption");
  for (const c of caps) {
    if (c.topmost === false) add(j, "masalah", "urutan lapisan: subtitle tertutup elemen lain");
    for (const o of mine.filter((e) => e.kind !== "caption" && !e.wide).map(body)) {
      const f = inter(c, o) / Math.max(1, area(c));
      if (f > 0.02) add(j, "masalah", `subtitle bertabrakan dengan ${o.kind} "${o.text}" (${Math.round(f * 100)}%)`);
    }
  }
  const texts = mine.filter((e) => e.kind === "text");
  const adris = mine.filter((e) => e.kind === "adri");
  if (settled) {
    for (const t of texts) {
      for (const a of adris) {
        // kotak ADRI lebih lebar dari badannya: pakai 70% tengah kotak
        const f = inter(t, body(a)) / Math.max(1, area(t));
        if (f > 0.12) add(j, "masalah", `teks tertutup/bertabrakan dengan ADRI: "${t.text}" (${Math.round(f * 100)}%)`);
      }
    }
    // teks ditimpa objek utama (bukan objek lebar/sengaja terpotong)
    for (const t of texts)
      for (const o of mine.filter((e) => e.kind === "object" && !e.wide)) {
        const f = inter(t, o) / Math.max(1, area(t));
        if (f > 0.2 && !o.text.includes(t.text)) add(j, "masalah", `teks bertumpuk dengan objek: "${t.text}" (${Math.round(f * 100)}%)`);
      }
    for (let a = 0; a < texts.length; a++)
      for (let b = a + 1; b < texts.length; b++) {
        const f = inter(texts[a], texts[b]) / Math.max(1, Math.min(area(texts[a]), area(texts[b])));
        if (f > 0.25 && !texts[a].text.includes(texts[b].text) && !texts[b].text.includes(texts[a].text)) add(j, "masalah", `teks bertabrakan: "${texts[a].text}" dan "${texts[b].text}"`);
      }
    // keseimbangan: titik berat (berbobot luas, dibatasi) elemen utama
    const mass = mine.filter((e) => e.kind !== "caption" && !e.wide).map((e) => {
      const x0 = Math.max(0, e.x), x1 = Math.min(W, e.x + e.w), y0 = Math.max(0, e.y), y1 = Math.min(H, e.y + e.h);
      return { cx: (x0 + x1) / 2, m: Math.max(0, x1 - x0) * Math.max(0, y1 - y0) };
    });
    const M = mass.reduce((s, m) => s + m.m, 0);
    if (M > 0) {
      const cx = mass.reduce((s, m) => s + m.cx * m.m, 0) / M;
      if (cx < W * 0.24 || cx > W * 0.76) add(j, "catatan", `komposisi berat sebelah (titik berat x=${Math.round(cx)})`);
    }
  }
}
// pemeriksaan rencana
if (plan.length) {
  plan.forEach((s, i) => {
    if (!i) return;
    const ov = plan[i - 1].end - s.start;
    if (ov > 0.9) issues.push({ frame: `${i + 1}-${s.type}`, level: "masalah", msg: `transisi terlalu panjang (${ov.toFixed(2)} dtk)` });
    if (s.family && s.family === plan[i - 1].family) issues.push({ frame: `${i + 1}-${s.type}`, level: "catatan", msg: `komposisi '${s.family}' sama dengan adegan sebelumnya` });
  });
}

// hapus duplikat
const seen = new Set();
const uniq = issues.filter((x) => { const k = x.frame + x.msg; if (seen.has(k)) return false; seen.add(k); return true; });
fs.writeFileSync(path.join(out, "laporan.json"), JSON.stringify({ frames: results.map(({ probe, ...r }) => ({ ...r, elements: probe?.items.length ?? 0 })), issues: uniq }, null, 1));
const bad = uniq.filter((x) => x.level !== "catatan");
console.log(`${results.length} frame diperiksa, ${bad.length} masalah, ${uniq.length - bad.length} catatan`);
for (const x of uniq) console.log(`  [${x.level}] ${x.frame}: ${x.msg}`);
console.log(`gambar + laporan: ${path.relative(process.cwd(), out)}`);
