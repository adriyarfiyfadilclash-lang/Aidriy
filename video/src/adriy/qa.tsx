import { useEffect, useState } from "react";
import { continueRender, delayRender, useCurrentFrame } from "remotion";

// Probe pemeriksa visual (hanya aktif saat prop `qa` = true, dipakai oleh scripts/qa.mjs).
// Setelah frame tergambar, probe mengukur semua elemen bertanda data-qa (text, caption, adri, object)
// lalu mengirim hasilnya lewat console.log("QA:...") supaya bisa dianalisis di Node:
// posisi akhir setelah semua transformasi kamera, opacity efektif, blur dari filter, teks yang
// terpotong (scrollWidth > clientWidth), nomor adegan pemiliknya, dan apakah subtitle berada paling atas.
export const QAProbe: React.FC = () => {
  const frame = useCurrentFrame();
  const [handle] = useState(() => delayRender("qa-probe"));
  useEffect(() => {
    const id = requestAnimationFrame(() => {
      const els = Array.from(document.querySelectorAll<HTMLElement | SVGElement>("[data-qa]"));
      const items = els.map((el) => {
        const r = el.getBoundingClientRect();
        let op = 1;
        let blur = 0;
        let n: Element | null = el;
        while (n) {
          const cs = getComputedStyle(n);
          op *= parseFloat(cs.opacity || "1");
          const m = /blur\(([\d.]+)px\)/.exec(cs.filter);
          if (m) blur = Math.max(blur, parseFloat(m[1]));
          n = n.parentElement;
        }
        const scene = el.closest("[data-scene]");
        const kind = el.getAttribute("data-qa");
        let topmost: boolean | null = null;
        if (kind === "caption" && r.width > 0) {
          const hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
          topmost = !!hit && !!hit.closest("[data-qa=caption]");
        }
        const html = el instanceof HTMLElement;
        return {
          kind,
          wide: el.hasAttribute("data-qa-wide"),
          scene: scene ? Number(scene.getAttribute("data-scene")) : -1,
          x: r.x, y: r.y, w: r.width, h: r.height,
          op, blur, topmost,
          overflow: html ? el.scrollWidth > el.clientWidth + 2 && getComputedStyle(el).overflow === "hidden" : false,
          text: (el.textContent ?? "").replace(/\s+/g, " ").trim().slice(0, 48),
        };
      });
      console.log("QA:" + JSON.stringify({ frame, items }));
      continueRender(handle);
    });
    return () => cancelAnimationFrame(id);
  }, [frame, handle]);
  return null;
};
