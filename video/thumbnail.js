// YouTube thumbnail with the real GameSir G8+ MFi (G8 Galileo MFi) controller.
//
//   node thumbnail.js        -> thumbnail.png (1280x720)
//
// The controller photo is GameSir's official product image. It is downloaded into build/
// (not committed) and trimmed to its outline. Needs Playwright (Chromium) and Python Pillow.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');

const BUILD = path.join(__dirname, 'build');
const PHOTO_URL = 'https://cdn.shopify.com/s/files/1/2241/8433/files/gamesir-g8-plus-mfi-type-c-mobile-controller-apple-certified-7300381.png?v=1782903322';
const RAW = path.join(BUILD, 'g8plus-mfi-raw.png');
const PHOTO = path.join(BUILD, 'g8plus-mfi.png');

const LOGO = fs.readFileSync(path.join(__dirname, '..', 'docs', 'logo.svg'), 'utf8')
  .replace(/width="216" height="216"/, 'class="logo"');

function getPhoto() {
  fs.mkdirSync(BUILD, { recursive: true });
  if (!fs.existsSync(RAW)) execFileSync('curl', ['-sSL', '--fail', '-o', RAW, PHOTO_URL]);
  // Keep the transparency and crop to the controller.
  execFileSync('python3', ['-I', '-c', `
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGBA')
im.crop(im.getbbox()).save(sys.argv[2])`, RAW, PHOTO]);
}

const html = () => `<!doctype html><html><head><meta charset="utf-8"><style>
*{box-sizing:border-box}
html,body{margin:0;width:1920px;height:1080px;overflow:hidden}
body{font-family:Inter,sans-serif;color:#fff;-webkit-font-smoothing:antialiased;position:relative;
  background:radial-gradient(900px 700px at 70% 55%, #4F46E588, transparent 70%),
             radial-gradient(900px 600px at 0% 100%, #0E7490aa, transparent 70%),
             linear-gradient(135deg,#0B0F1E,#140F2E 55%,#0A1424)}
.grid{position:absolute;inset:0;background-image:linear-gradient(#ffffff08 2px,transparent 2px),linear-gradient(90deg,#ffffff08 2px,transparent 2px);
  background-size:80px 80px;mask-image:radial-gradient(ellipse at 65% 55%,#000 25%,transparent 70%)}
.pad{position:absolute;left:770px;top:420px;width:1020px;transform:rotate(-7deg);
  filter:drop-shadow(0 50px 70px #000c) drop-shadow(0 0 60px #7C5CFF55)}
.waves{position:absolute;left:640px;top:300px;width:1280px;height:700px;transform:rotate(-7deg)}
.waves path{fill:none;stroke-linecap:round}
.top{position:absolute;left:90px;top:70px}
.model{display:inline-block;font-size:62px;font-weight:900;letter-spacing:.01em;padding:12px 30px;border-radius:18px;
  background:#fff;color:#111;box-shadow:0 12px 40px #0008}
h1{margin:26px 0 0;font-size:170px;line-height:.9;font-weight:900;letter-spacing:-.04em;text-shadow:0 10px 40px #000c}
h1 .r{color:#38E1FF;text-shadow:0 0 40px #22D3EE88,0 10px 40px #000c}
.sub{margin-top:22px;font-size:58px;font-weight:800;color:#C9D1E3;text-shadow:0 6px 24px #000}
.fixed{position:absolute;left:90px;bottom:80px;display:flex;align-items:center;gap:22px;padding:22px 44px 22px 30px;border-radius:28px;
  background:linear-gradient(135deg,#34D399,#10B981);color:#05251A;font-size:104px;font-weight:900;letter-spacing:-.01em;
  transform:rotate(-3deg);box-shadow:0 20px 60px #10B98166,0 0 0 8px #0B0F1E}
.fixed svg{width:96px;height:96px}
.noroot{position:absolute;left:600px;bottom:104px;font-size:48px;font-weight:800;padding:14px 28px;border-radius:999px;
  background:#0B0F1Ecc;border:3px solid #ffffff40}
.brand{position:absolute;right:70px;top:66px;display:flex;align-items:center;gap:20px;font-size:52px;font-weight:800}
.logo{width:96px;height:96px}
</style></head><body><div class="grid"></div>
<svg class="waves" viewBox="0 0 1280 700">
  <g stroke="#22D3EE" stroke-width="18"><path d="M110 240q-70 130 0 260"/><path d="M1170 240q70 130 0 260"/></g>
  <g stroke="#9D86FF" stroke-width="16" opacity=".8"><path d="M45 190q-95 180 0 360"/><path d="M1235 190q95 180 0 360"/></g>
</svg>
<img class="pad" src="file://${PHOTO}">
<div class="top">
  <div class="model">GameSir G8+ MFi</div>
  <h1>NO <span class="r">RUMBLE</span><br>ON ANDROID?</h1>
</div>
<div class="fixed"><svg viewBox="0 0 24 24"><path d="M4.5 12.5l5 5L19.5 7" fill="none" stroke="currentColor" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/></svg>FIXED</div>
<div class="noroot">No root</div>
<div class="brand">${LOGO}NeuroPad</div>
</body></html>`;

(async () => {
  getPhoto();
  const file = path.join(BUILD, 'thumbnail.html');
  fs.writeFileSync(file, html());
  const browser = await chromium.launch();
  const p = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  await p.goto('file://' + file);
  await p.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map((i) => i.decode())); });
  await p.screenshot({ path: path.join(BUILD, 'thumb_1080.png') });
  await browser.close();
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', path.join(BUILD, 'thumb_1080.png'), '-vf', 'scale=1280:720:flags=lanczos', path.join(__dirname, 'thumbnail.png')]);
  console.log('thumbnail.png written');
})().catch((e) => { console.error(e); process.exit(1); });
