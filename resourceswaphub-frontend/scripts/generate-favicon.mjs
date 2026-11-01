// Regenerates public/favicon.ico (16/32/48 px) from public/icons/resourceswaphub-mark.svg.
//   node scripts/generate-favicon.mjs   (run from "resourceswaphub-frontend/")
import sharp from "sharp";
import { readFileSync, writeFileSync } from "node:fs";

const mark = readFileSync("public/icons/resourceswaphub-mark.svg");

// Chrome-favicons generally read the largest entry in the ICO container.
// PNG-encoded entries are valid inside .ico since Windows Vista.
const sizes = [16, 32, 48];
const pngs = await Promise.all(
  sizes.map(async (size) => ({
    size,
    data: await sharp(mark, { density: 72 * (size / 32) })
      .resize(size, size)
      .png()
      .toBuffer(),
  }))
);

const count = pngs.length;
const directorySize = 6 + 16 * count;
const chunks = [
  Buffer.alloc(6),
  ...pngs.map(() => Buffer.alloc(16)),
  ...pngs.map(({ data }) => data),
];

// ICONDIR
chunks[0].writeUInt16LE(0, 0);      // reserved
chunks[0].writeUInt16LE(1, 2);      // type: icon
chunks[0].writeUInt16LE(count, 4);  // image count

let offset = directorySize;
pngs.forEach(({ size, data }, i) => {
  const entry = chunks[1 + i];
  entry.writeUInt8(size === 16 ? size : size, 0);        // width (16/32 fit in one byte)
  entry.writeUInt8(size, 1);                             // height
  entry.writeUInt8(0, 2);                                // palette
  entry.writeUInt8(0, 3);                                // reserved
  entry.writeUInt16LE(1, 4);                             // color planes
  entry.writeUInt16LE(32, 6);                            // bits per pixel
  entry.writeUInt32LE(data.length, 8);                   // data size
  entry.writeUInt32LE(offset, 12);                       // data offset
  offset += data.length;
});

writeFileSync("public/favicon.ico", Buffer.concat(chunks));
console.log("favicon.ico regenerated (16/32/48 px) from resourceswaphub-mark.svg");
