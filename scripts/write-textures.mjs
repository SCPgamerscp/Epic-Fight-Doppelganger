import { deflateSync, crc32 } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

function png(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[(width * 4 + 1) * y] = 0;
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const o = (width * 4 + 1) * y + 1 + x * 4;
      raw[o] = rgba[i];
      raw[o + 1] = rgba[i + 1];
      raw[o + 2] = rgba[i + 2];
      raw[o + 3] = rgba[i + 3];
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const chunks = [
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ];
  return Buffer.concat(chunks);
}

function chunk(type, data) {
  const t = Buffer.from(type);
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}

function paint(size, fn) {
  const rgba = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const c = fn(x, y) || [0, 0, 0, 0];
      const i = (y * size + x) * 4;
      rgba[i] = c[0];
      rgba[i + 1] = c[1];
      rgba[i + 2] = c[2];
      rgba[i + 3] = c[3];
    }
  }
  return png(size, size, rgba);
}

function meat(x, y) {
  const flesh = [154, 58, 52, 255];
  const dark = [96, 28, 26, 255];
  const fat = [214, 186, 150, 255];
  const robe = [62, 92, 58, 255];
  if (x < 2 || y < 2 || x > 13 || y > 13) return [0, 0, 0, 0];
  if ((x + y) % 7 === 0) return dark;
  if (y > 10 && x > 4 && x < 11) return robe;
  if (x === 4 || x === 11) return fat;
  return flesh;
}

function elixir(x, y) {
  const glass = [180, 210, 214, 180];
  const soul = [110, 176, 186, 255];
  const dark = [18, 28, 32, 255];
  const cork = [92, 64, 42, 255];
  if (y < 2 && x > 5 && x < 10) return cork;
  if (y >= 2 && y < 4 && x > 6 && x < 9) return glass;
  if (x < 4 || x > 11) return [0, 0, 0, 0];
  if (y > 13) return [0, 0, 0, 0];
  if (x === 4 || x === 11 || y === 13) return [48, 62, 66, 255];
  if (y > 5) return (x + y) % 5 === 0 ? dark : soul;
  return glass;
}

function pack(x, y) {
  const bg = [9, 9, 11, 255];
  const soul = [143, 184, 192, 255];
  const bone = [236, 232, 225, 255];
  const red = [180, 35, 24, 255];
  if (y < 6) return red;
  if (Math.abs(x - 32) < 2) return soul;
  const dx = x - 32;
  const dy = y - 22;
  if (dx * dx + dy * dy < 18) return bone;
  if (Math.abs(dx) < 7 && y > 26 && y < 50) return bone;
  return bg;
}

const itemDir = join(root, "src/main/resources/assets/efdoppelganger/textures/item");
mkdirSync(itemDir, { recursive: true });
writeFileSync(join(itemDir, "villager_meat.png"), paint(16, meat));
writeFileSync(join(itemDir, "mirror_elixir.png"), paint(16, elixir));
writeFileSync(join(root, "src/main/resources/pack.png"), paint(64, pack));
console.log("textures written");
