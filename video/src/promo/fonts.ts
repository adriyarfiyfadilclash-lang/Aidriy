import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

// Plus Jakarta Sans (variable font, lisensi OFL, lihat public/fonts/OFL.txt).
// Disimpan lokal supaya render tidak bergantung pada koneksi internet.
export const fontFamily = "Plus Jakarta Sans";

loadFont({
  family: fontFamily,
  url: staticFile("fonts/PlusJakartaSans-latin.woff2"),
  weight: "200 800",
});
