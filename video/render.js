// Renders the NeuroPad explainer video.
//
//   node render.js                 render every scene, then the final video
//   node render.js --still 05-home 6.5   write build/stills/05-home.png at t=6.5 s
//
// Needs: Playwright with Chromium, ffmpeg, and the voiceover in build/audio (python3 tts.py).
const fs = require('fs');
const path = require('path');
const { spawn, execFileSync } = require('child_process');
const { chromium } = require('playwright');
const { scenes, page, thumbnail } = require('./scenes');

const FPS = 30;
const LEAD = 0.6;   // silence before the voice starts in each scene
const TAIL = 0.9;   // silence after it ends
const WORKERS = Number(process.env.WORKERS || 4);
const BUILD = path.join(__dirname, 'build');
const narration = JSON.parse(fs.readFileSync(path.join(__dirname, 'narration.json'), 'utf8'));

const audioLen = (id) => Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0',
  path.join(BUILD, 'audio', `${id}.mp3`)]).toString().trim());

function writeHtml(id, dur) {
  fs.mkdirSync(path.join(BUILD, 'html'), { recursive: true });
  const file = path.join(BUILD, 'html', `${id}.html`);
  const html = page(id, id === 'thumbnail' ? thumbnail() : scenes[id]())
    .replace('<html>', `<html style="--dur:${dur.toFixed(3)}">`);
  fs.writeFileSync(file, html);
  return file;
}

async function openScene(browser, id, dur) {
  const p = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  await p.goto('file://' + writeHtml(id, dur));
  await p.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map((i) => i.decode().catch(() => {})));
    document.getAnimations().forEach((a) => a.pause());
  });
  return p;
}

const seek = (p, t) => p.evaluate((ms) => document.getAnimations().forEach((a) => { a.currentTime = ms; }), t * 1000);

async function renderScene(browser, id, dur) {
  const out = path.join(BUILD, 'clips', `${id}.mp4`);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  const p = await openScene(browser, id, dur);
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const done = new Promise((res, rej) => ff.on('close', (c) => (c ? rej(new Error(`ffmpeg ${c}`)) : res())));
  const frames = Math.round(dur * FPS);
  for (let i = 0; i < frames; i++) {
    await seek(p, i / FPS);
    const buf = await p.screenshot({ type: 'jpeg', quality: 93 });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  }
  ff.stdin.end();
  await done;
  await p.close();
  console.log(`  ${id}: ${frames} frames`);
}

function srtTime(s) {
  const ms = Math.round(s * 1000);
  const pad = (n, w = 2) => String(n).padStart(w, '0');
  return `${pad(Math.floor(ms / 3600000))}:${pad(Math.floor(ms / 60000) % 60)}:${pad(Math.floor(ms / 1000) % 60)},${pad(ms % 1000, 3)}`;
}

async function main() {
  const args = process.argv.slice(2);
  const browser = await chromium.launch();

  if (args[0] === '--still') {
    const [id, t] = [args[1], Number(args[2] || 5)];
    const dur = id === 'thumbnail' ? 10 : audioLen(id) + LEAD + TAIL;
    const p = await openScene(browser, id, dur);
    await seek(p, t);
    fs.mkdirSync(path.join(BUILD, 'stills'), { recursive: true });
    await p.screenshot({ path: path.join(BUILD, 'stills', `${id}.png`) });
    await browser.close();
    return;
  }

  // Scene timings come from the voiceover.
  const plan = narration.map((n) => ({ ...n, voice: audioLen(n.id) }));
  plan.forEach((s) => { s.dur = Math.round((s.voice + LEAD + TAIL) * FPS) / FPS; });

  // Frames, a few scenes at a time.
  const queue = [...plan];
  await Promise.all(Array.from({ length: WORKERS }, async () => {
    for (let s; (s = queue.shift());) await renderScene(browser, s.id, s.dur);
  }));

  await browser.close();
  // Thumbnail (real G8+ MFi photo), 1280x720.
  execFileSync('node', [path.join(__dirname, 'thumbnail.js')], { stdio: 'inherit' });

  // Video track: concatenate the clips.
  const list = path.join(BUILD, 'clips.txt');
  fs.writeFileSync(list, plan.map((s) => `file '${path.join(BUILD, 'clips', s.id + '.mp4')}'`).join('\n'));
  // Audio track: each voice line placed at its scene start + LEAD.
  let t = 0;
  const inputs = [];
  const filters = [];
  const cues = [];
  const chapters = [];
  plan.forEach((s, i) => {
    inputs.push('-i', path.join(BUILD, 'audio', `${s.id}.mp3`));
    const ms = Math.round((t + LEAD) * 1000);
    filters.push(`[${i + 1}:a]aresample=48000,adelay=${ms}|${ms}[a${i}]`);
    chapters.push(`${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, '0')} ${s.chapter}`);
    for (const c of JSON.parse(fs.readFileSync(path.join(BUILD, 'audio', `${s.id}.json`), 'utf8'))) {
      cues.push({ start: t + LEAD + c.start, end: t + LEAD + c.end, text: c.text });
    }
    t += s.dur;
  });
  filters.push(`${plan.map((_, i) => `[a${i}]`).join('')}amix=inputs=${plan.length}:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,apad[aout]`);

  const out = path.join(__dirname, 'NeuroPad_explainer.mp4');
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', list, ...inputs,
    '-filter_complex', filters.join(';'), '-map', '0:v', '-map', '[aout]', '-shortest',
    '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-movflags', '+faststart', out], { stdio: 'inherit' });

  fs.writeFileSync(path.join(__dirname, 'NeuroPad_explainer.srt'),
    cues.map((c, i) => `${i + 1}\n${srtTime(c.start)} --> ${srtTime(c.end)}\n${c.text}\n`).join('\n'));
  fs.writeFileSync(path.join(BUILD, 'chapters.txt'), chapters.join('\n') + '\n');
  console.log(`done: ${out} (${t.toFixed(1)} s)`);
  console.log(chapters.join('\n'));
}

main().catch((e) => { console.error(e); process.exit(1); });
