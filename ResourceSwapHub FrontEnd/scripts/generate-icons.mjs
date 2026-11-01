// Regenerates the PWA/favicon PNG set from public/icons/resourceswaphub-mark.svg.
//   node scripts/generate-icons.mjs   (run from "ResourceSwapHub FrontEnd/")
import sharp from "sharp";
import { readFileSync } from "node:fs";

const mark = readFileSync("public/icons/resourceswaphub-mark.svg");

// Tile icons: the rounded square fills the whole canvas.
for (const [size, out] of [
  [192, "public/icons/icon-192.png"],
  [512, "public/icons/icon-512.png"],
  [180, "public/icons/apple-touch-icon.png"],
]) {
  await sharp(mark, { density: 72 * (size / 48) })
    .resize(size, size)
    .png()
    .toFile(out);
}

// Maskable icon: full-bleed background, mark kept inside the safe zone (~58%).
const inner = await sharp(mark, { density: 72 * (300 / 48) })
  .resize(300, 300)
  .png()
  .toBuffer();
await sharp({
  create: { width: 512, height: 512, channels: 4, background: "#2f6b58" },
})
  .composite([{ input: inner, gravity: "centre" }])
  .png()
  .toFile("public/icons/icon-maskable-512.png");

console.log("icons regenerated from resourceswaphub-mark.svg");
