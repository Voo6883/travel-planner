/**
 * Generates the PWA icon set (PLAN §4.2.11, design system §9.2).
 *
 * The icons are **committed build inputs**, not build output: a manifest that points at a missing
 * file makes the app silently uninstallable, so they must exist in the repo. This script is how
 * they were produced, which is what keeps "where did the icon come from" answerable and lets the
 * mark be regenerated at a new size without a design tool.
 *
 *   npm run icons
 *
 * Design system §9.2: "Simple journey-pin mark; readable at 16 px; no text" on the `#0958D9`
 * theme colour, and for the maskable variant "all essential artwork inside the central 80% of
 * width and height". No text appears at any size — glyphs are illegible in a 16 px favicon and
 * would need translating in two locales besides.
 *
 * PNG is written by hand (zlib is in Node's standard library) rather than through an image
 * dependency: four small files do not justify a native `sharp` build in CI.
 */

import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const frontendRoot = join(here, '..');

/** Raw hex is confined to design-tokens.ts everywhere else; a PNG cannot read a CSS variable. */
const THEME_BLUE = [0x09, 0x58, 0xd9];
const MARK_WHITE = [0xff, 0xff, 0xff];

/** 4x4 supersampling. A pin outline at 192 px looks ragged without it, which §9.2's "readable at 16 px" rules out. */
const SAMPLES_PER_AXIS = 4;

function main() {
  const iconsDir = join(frontendRoot, 'public', 'icons');
  mkdirSync(iconsDir, { recursive: true });

  // `any` icons carry their own rounded tile: some launchers place the raw bitmap unmasked, and a
  // full-bleed square would read as an unfinished asset next to the platform's own icons.
  writeFileSync(join(iconsDir, 'icon-192.png'), renderIcon(192, 'rounded'));
  writeFileSync(join(iconsDir, 'icon-512.png'), renderIcon(512, 'rounded'));

  // Maskable is full-bleed on purpose: the platform clips it to its own shape, so any corner the
  // icon rounds itself is corner the launcher then rounds twice.
  writeFileSync(join(iconsDir, 'icon-maskable-512.png'), renderIcon(512, 'maskable'));

  // iOS ignores the manifest icon list for "Add to Home Screen" (design system §9.1 covers that
  // flow explicitly), so the Next.js `apple-icon` convention supplies a dedicated 180 px asset.
  writeFileSync(join(frontendRoot, 'src', 'app', 'apple-icon.png'), renderIcon(180, 'rounded'));

  process.stdout.write('PWA icons written to public/icons/ and src/app/apple-icon.png\n');
}

/**
 * @param {number} size Pixel width and height.
 * @param {'rounded' | 'maskable'} variant Tile shape and how much of it the mark may occupy.
 */
function renderIcon(size, variant) {
  // §9.2: essential artwork inside the central 80% for maskable. 0.62 leaves the mark clear of the
  // safe-zone edge rather than touching it, so an aggressive circular mask still cannot clip it.
  const markScale = variant === 'maskable' ? 0.62 : 0.74;
  const cornerRadius = variant === 'maskable' ? 0 : size * 0.22;
  const pixels = new Uint8Array(size * size * 4);

  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const { tile, mark } = sampleCoverage({ x, y }, { size, cornerRadius, markScale });
      writePixel(pixels, (y * size + x) * 4, tile, mark);
    }
  }

  return encodePng(size, size, pixels);
}

/** Anti-aliased coverage of the tile and of the pin mark at one pixel, each in `0..1`. */
function sampleCoverage(pixel, geometry) {
  const step = 1 / SAMPLES_PER_AXIS;
  let tileHits = 0;
  let markHits = 0;

  for (let sy = 0; sy < SAMPLES_PER_AXIS; sy += 1) {
    for (let sx = 0; sx < SAMPLES_PER_AXIS; sx += 1) {
      const px = pixel.x + (sx + 0.5) * step;
      const py = pixel.y + (sy + 0.5) * step;
      if (isInsideTile(px, py, geometry)) {
        tileHits += 1;
      }
      if (isInsideMark(px, py, geometry)) {
        markHits += 1;
      }
    }
  }

  const total = SAMPLES_PER_AXIS * SAMPLES_PER_AXIS;
  return { tile: tileHits / total, mark: markHits / total };
}

/** Rounded square; `cornerRadius === 0` degenerates to the full-bleed maskable square. */
function isInsideTile(px, py, { size, cornerRadius }) {
  if (px < 0 || py < 0 || px > size || py > size) {
    return false;
  }
  if (cornerRadius <= 0) {
    return true;
  }
  const dx = Math.max(cornerRadius - px, px - (size - cornerRadius), 0);
  const dy = Math.max(cornerRadius - py, py - (size - cornerRadius), 0);
  return dx * dx + dy * dy <= cornerRadius * cornerRadius;
}

/**
 * The journey pin, in a unit box mapped onto the centred mark area.
 *
 * A teardrop is the union of the head circle and the triangle spanning the two points where the
 * tip's tangents touch that circle — that union is what gives the silhouette its continuous
 * shoulder instead of the visible seam a plain triangle leaves. The tangent points are computed
 * once below rather than eyeballed, so the shape stays exact at any size.
 */
function isInsideMark(px, py, { size, markScale }) {
  const extent = size * markScale;
  const origin = (size - extent) / 2;
  const u = (px - origin) / extent;
  const v = (py - origin) / extent;

  const headX = 0.5;
  const headY = 0.4;
  const headRadius = 0.26;
  const holeRadius = 0.105;
  const tipY = 0.92;

  const inHead = distanceSquared(u, v, headX, headY) <= headRadius * headRadius;
  const inBody = isInsideTriangle(u, v, tangentTriangle(headX, headY, headRadius, tipY));
  if (!inHead && !inBody) {
    return false;
  }
  // The hole is what keeps the pin legible at 16 px: a solid teardrop reads as an anonymous blob.
  return distanceSquared(u, v, headX, headY) > holeRadius * holeRadius;
}

/**
 * Vertices of the triangle formed by the tip and the two tangency points on the head circle.
 * With the tip directly below the centre, `cos(angle) = radius / distance` places both tangency
 * points by rotating the downward axis through that angle.
 */
function tangentTriangle(cx, cy, radius, tipY) {
  const distance = tipY - cy;
  const cos = radius / distance;
  const sin = Math.sqrt(Math.max(1 - cos * cos, 0));
  return [
    { x: cx, y: tipY },
    { x: cx + radius * sin, y: cy + radius * cos },
    { x: cx - radius * sin, y: cy + radius * cos },
  ];
}

function isInsideTriangle(px, py, [a, b, c]) {
  const d1 = cross(px, py, a, b);
  const d2 = cross(px, py, b, c);
  const d3 = cross(px, py, c, a);
  const hasNegative = d1 < 0 || d2 < 0 || d3 < 0;
  const hasPositive = d1 > 0 || d2 > 0 || d3 > 0;
  return !(hasNegative && hasPositive);
}

function cross(px, py, from, to) {
  return (px - to.x) * (from.y - to.y) - (from.x - to.x) * (py - to.y);
}

function distanceSquared(px, py, cx, cy) {
  return (px - cx) ** 2 + (py - cy) ** 2;
}

/** Composites mark over tile over transparency, straight (non-premultiplied) RGBA. */
function writePixel(pixels, offset, tileCoverage, markCoverage) {
  const alpha = Math.max(tileCoverage, markCoverage);
  if (alpha <= 0) {
    return;
  }
  const markShare = Math.min(markCoverage, alpha) / alpha;
  for (let channel = 0; channel < 3; channel += 1) {
    const base = THEME_BLUE[channel];
    const top = MARK_WHITE[channel];
    pixels[offset + channel] = Math.round(base + (top - base) * markShare);
  }
  pixels[offset + 3] = Math.round(alpha * 255);
}

// ---------------------------------------------------------------------------
// Minimal PNG encoder — 8-bit RGBA, no interlacing, filter type 0 on every row.
// ---------------------------------------------------------------------------

function encodePng(width, height, pixels) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 6; // colour type: truecolour with alpha
  // bytes 10-12 stay zero: deflate compression, adaptive filtering, no interlace.

  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y += 1) {
    raw[y * (stride + 1)] = 0; // per-scanline filter: none
    Buffer.from(pixels.buffer, y * stride, stride).copy(raw, y * (stride + 1) + 1);
  }

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([length, body, crc]);
}

const CRC_TABLE = buildCrcTable();

function buildCrcTable() {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

// Runs last: `CRC_TABLE` is a `const`, so calling `main()` above its initialiser is a temporal
// dead zone error rather than the hoisted-function behaviour the declarations above suggest.
main();
