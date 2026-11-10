// Scene markup for the NeuroPad explainer video.
// Every scene is a 1920x1080 HTML page. Timings use CSS variables:
//   --dur  the scene length in seconds (set by render.js from the voiceover)
//   --d    the delay of one element, usually a fraction of --dur
const path = require('path');
const SHOTS = 'file://' + path.resolve(__dirname, '..', 'docs', 'screenshots');

const LOGO = `
<svg viewBox="0 0 108 108" class="logo" aria-hidden="true">
  <defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#7C5CFF"/><stop offset="0.55" stop-color="#4F46E5"/><stop offset="1" stop-color="#0EA5C9"/>
  </linearGradient></defs>
  <rect width="108" height="108" rx="26" fill="url(#lg)"/>
  <circle cx="54" cy="54" r="38" fill="#fff" fill-opacity="0.08"/>
  <g transform="translate(54 55) scale(0.86) translate(-54 -55)" fill="none" stroke-linecap="round">
    <path fill="#fff" fill-rule="evenodd" d="${'M38,42h32a14,14 0,0 1,14 14l2,10a6,6 0,0 1,-11 4l-5,-8h-32l-5,8a6,6 0,0 1,-11 -4l2,-10a14,14 0,0 1,14 -14zM38.4,47h3.2v3.4h3.4v3.2h-3.4v3.4h-3.2v-3.4h-3.4v-3.2h3.4zM65,55m-2.8,0a2.8,2.8 0,1 0,5.6 0a2.8,2.8 0,1 0,-5.6 0M71.5,49.5m-2.8,0a2.8,2.8 0,1 0,5.6 0a2.8,2.8 0,1 0,-5.6 0'}"/>
    <path class="wave1" stroke="#fff" stroke-width="3.4" d="M15,45q-5,10 0,20M93,45q5,10 0,20"/>
    <path class="wave2" stroke="#fff" stroke-opacity="0.6" stroke-width="3.4" d="M7,40q-8,15 0,30M101,40q8,15 0,30"/>
  </g>
</svg>`;

// A plain gamepad glyph (no waves) for diagrams.
const PAD = (cls = '') => `
<svg viewBox="20 36 68 40" class="pad ${cls}" aria-hidden="true"><path fill="currentColor" fill-rule="evenodd"
 d="M38,42h32a14,14 0,0 1,14 14l2,10a6,6 0,0 1,-11 4l-5,-8h-32l-5,8a6,6 0,0 1,-11 -4l2,-10a14,14 0,0 1,14 -14zM38.4,47h3.2v3.4h3.4v3.2h-3.4v3.4h-3.2v-3.4h-3.4v-3.2h3.4zM65,55m-2.8,0a2.8,2.8 0,1 0,5.6 0a2.8,2.8 0,1 0,-5.6 0M71.5,49.5m-2.8,0a2.8,2.8 0,1 0,5.6 0a2.8,2.8 0,1 0,-5.6 0"/></svg>`;

const CHECK = `<svg viewBox="0 0 24 24" class="ico"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const CROSS = `<svg viewBox="0 0 24 24" class="ico"><path d="M6.5 6.5l11 11M17.5 6.5l-11 11" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round"/></svg>`;

const d = (f) => `style="--d:calc(var(--dur) * ${f})"`;
const ds = (s) => `style="--d:${s}"`;

const CSS = `
:root{--bg:#0A0E18;--card:#131A2A;--card2:#1A2236;--line:#26304A;--text:#E8ECF5;--muted:#98A2B8;
  --purple:#7C5CFF;--cyan:#22D3EE;--green:#34D399;--red:#F87171;}
*{box-sizing:border-box}
html,body{margin:0;width:1920px;height:1080px;overflow:hidden;background:var(--bg)}
body{font-family:Inter,sans-serif;color:var(--text);-webkit-font-smoothing:antialiased;
  background:radial-gradient(1100px 760px at 88% -12%, #2B1F6B88, transparent 70%),
             radial-gradient(1000px 700px at -8% 112%, #0E5E7A66, transparent 70%), var(--bg)}
.grid{position:absolute;inset:0;background-image:linear-gradient(#ffffff06 1px,transparent 1px),linear-gradient(90deg,#ffffff06 1px,transparent 1px);
  background-size:64px 64px;mask-image:radial-gradient(ellipse at center,#000 30%,transparent 75%)}
.out{position:absolute;inset:0;animation:fadeout .5s ease-in both;animation-delay:calc(var(--dur) * 1s - .5s)}
.stage{position:absolute;inset:0;padding:110px 140px;animation:fadein .6s ease-out both}
@keyframes fadein{from{opacity:0}}
@keyframes fadeout{to{opacity:0}}
@keyframes up{from{opacity:0;transform:translateY(28px)}}
@keyframes pop{0%{opacity:0;transform:scale(.6)}70%{transform:scale(1.06)}100%{opacity:1;transform:scale(1)}}
@keyframes slideL{from{opacity:0;transform:translateX(-40px)}}
.in{animation:up .8s cubic-bezier(.2,.7,.2,1) both;animation-delay:calc(var(--d,0) * 1s)}
.pop{animation:pop .7s cubic-bezier(.2,.7,.2,1) both;animation-delay:calc(var(--d,0) * 1s)}
.slide{animation:slideL .7s cubic-bezier(.2,.7,.2,1) both;animation-delay:calc(var(--d,0) * 1s)}
.eyebrow{font-size:26px;letter-spacing:.22em;font-weight:600;color:var(--cyan);text-transform:uppercase;margin:0 0 18px}
h1{font-size:76px;line-height:1.05;font-weight:800;letter-spacing:-.02em;margin:0 0 22px}
h2{font-size:44px;font-weight:700;margin:0 0 12px}
p.lead{font-size:34px;line-height:1.45;color:var(--muted);margin:0;max-width:1300px}
.grad{background:linear-gradient(90deg,#9D86FF,#22D3EE);-webkit-background-clip:text;background-clip:text;color:transparent}
.card{background:linear-gradient(180deg,var(--card2),var(--card));border:1.5px solid var(--line);border-radius:32px;padding:34px 38px}
.row{display:flex;gap:28px;align-items:center}
.ico{width:1em;height:1em;display:inline-block;vertical-align:-.12em}
.chip{display:inline-flex;align-items:center;gap:14px;padding:16px 26px;border-radius:999px;font-size:28px;font-weight:600;
  background:#1B2438;border:1.5px solid var(--line)}
.chip.good{color:var(--green);background:#0F2A26;border-color:#1F5A4B}
.chip.bad{color:var(--red);background:#2C1418;border-color:#5C2630}
.logo{width:150px;height:150px;filter:drop-shadow(0 20px 50px #5B47E066)}
.pad{color:#fff}
.wave1{animation:wave 1.4s ease-in-out infinite}
.wave2{animation:wave 1.4s ease-in-out infinite;animation-delay:.2s}
@keyframes wave{0%,100%{opacity:.25}50%{opacity:1}}
.phone{width:440px;height:860px;border-radius:56px;border:12px solid #1C2336;background:#0A0E18;overflow:hidden;position:relative;
  box-shadow:0 40px 120px #000a, 0 0 0 1.5px #2F3A57}
.phone img{width:100%;display:block;will-change:transform;animation:scroll linear both;animation-delay:calc(var(--d0,1.6) * 1s);
  animation-duration:calc((var(--dur) - var(--d0,1.6) - 2.2) * 1s)}
@keyframes scroll{to{transform:translateY(calc(-100% + 836px))}}
.wide{border-radius:36px;border:10px solid #1C2336;overflow:hidden;box-shadow:0 40px 120px #000a,0 0 0 1.5px #2F3A57}
.wide img{display:block;width:100%}
.callout{display:flex;gap:26px;align-items:flex-start;margin:0 0 34px}
.callout .n{flex:none;width:58px;height:58px;border-radius:18px;display:grid;place-items:center;font-weight:800;font-size:28px;
  background:linear-gradient(135deg,#7C5CFF,#22D3EE)}
.callout h3{font-size:36px;margin:4px 0 6px;font-weight:700}
.callout p{font-size:27px;color:var(--muted);margin:0;line-height:1.4}
.ken{animation:ken linear both;animation-duration:calc(var(--dur) * 1s)}
@keyframes ken{from{transform:scale(1)}to{transform:scale(1.045)}}
`;

function page(id, inner) {
  return `<!doctype html><html><head><meta charset="utf-8"><title>${id}</title><style>${CSS}</style></head>
<body><div class="grid"></div><div class="out"><div class="stage">${inner}</div></div></body></html>`;
}

const node = (label, sub, icon, extra = '', attrs = '') => `
<div class="card node ${extra}" ${attrs}>
  <div class="nicon">${icon}</div><div class="nl">${label}</div><div class="ns">${sub}</div></div>`;

const scenes = {
  '01-intro': () => `
<style>.c{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
.c .logo{width:230px;height:230px;margin-bottom:46px}
.c h1{font-size:132px;margin-bottom:24px}.c p.lead{font-size:44px;max-width:1400px}
.tags{margin-top:56px;display:flex;gap:22px;justify-content:center}</style>
<div class="c">
  <div class="pop" ${ds(0.3)}>${LOGO}</div>
  <h1 class="in" ${ds(0.7)}>Neuro<span class="grad">Pad</span></h1>
  <p class="lead in" ${ds(1.1)}>Rumble for USB gamepads on Android.</p>
  <div class="tags">
    <span class="chip in" ${d(0.55)}>Free &amp; open source</span>
    <span class="chip good in" ${d(0.62)}>${CHECK} No root needed</span>
    <span class="chip in" ${d(0.69)}>Android 7.0+</span>
  </div>
</div>`,

  '02-problem': () => `
<style>.flow{margin-top:90px;display:flex;align-items:center;justify-content:space-between}
.node{width:400px;text-align:center;padding:44px 30px}
.nicon{height:120px;display:grid;place-items:center;color:#fff}.nicon .pad{width:170px}
.nl{font-size:40px;font-weight:700;margin-top:18px}.ns{font-size:26px;color:var(--muted);margin-top:8px}
.arrow{flex:1;position:relative;height:120px;display:grid;place-items:center}
.arrow svg{width:100%;height:40px}
.x{position:absolute;width:96px;height:96px;border-radius:50%;background:#2C1418;border:3px solid var(--red);color:var(--red);
  display:grid;place-items:center;font-size:56px}
.status{margin-top:80px;display:flex;gap:28px;justify-content:center}.status .chip{font-size:34px;padding:20px 34px}
.box{width:170px;height:120px;border-radius:26px;border:4px solid #3B4766;display:grid;place-items:center;font-size:36px;font-weight:800;color:var(--muted)}</style>
<div class="eyebrow in" ${ds(0.2)}>The problem</div>
<h1 class="in" ${ds(0.4)}>The buttons work. <span style="color:var(--red)">The rumble doesn't.</span></h1>
<div class="flow">
  ${node('Game', 'asks for rumble', `<div class="box">GAME</div>`, 'in', ds(1.2))}
  <div class="arrow in" ${ds(1.6)}><svg viewBox="0 0 300 40" preserveAspectRatio="none"><path d="M10 20H280" stroke="#7C5CFF" stroke-width="5"/><path d="M272 8l16 12-16 12" fill="none" stroke="#7C5CFF" stroke-width="5"/></svg></div>
  ${node('Android', 'USB host', `<div class="box" style="color:var(--green);border-color:#1F5A4B">OS</div>`, 'in', ds(1.9))}
  <div class="arrow in" ${d(0.42)}><svg viewBox="0 0 300 40" preserveAspectRatio="none"><path d="M10 20H280" stroke="#5C2630" stroke-width="5" stroke-dasharray="14 12"/></svg><div class="x pop" ${d(0.5)}>${CROSS}</div></div>
  ${node('USB gamepad', 'motors stay silent', PAD(), 'in', d(0.3))}
</div>
<div class="status">
  <span class="chip good in" ${d(0.18)}>${CHECK} Buttons pass through</span>
  <span class="chip bad in" ${d(0.55)}>${CROSS} Rumble never arrives</span>
</div>`,

  '03-fix': () => `
<style>.fl{margin-top:70px;display:flex;align-items:center;justify-content:space-between}
.node{width:330px;text-align:center;padding:38px 22px}
.nicon{height:110px;display:grid;place-items:center}.nicon .pad{width:150px}.nicon .logo{width:110px;height:110px}
.nl{font-size:34px;font-weight:700;margin-top:16px}.ns{font-size:24px;color:var(--muted);margin-top:8px;line-height:1.35}
.link{flex:1;height:120px;position:relative}
.link svg{position:absolute;left:0;width:100%;height:24px}
.flow1{stroke-dasharray:14 12;animation:dash 0.8s linear infinite}
.flow2{stroke-dasharray:14 12;animation:dash 0.8s linear infinite reverse}
@keyframes dash{to{stroke-dashoffset:-26}}
.legend{margin-top:70px;display:flex;gap:28px;justify-content:center}
.virt{border-color:#5B47E0;box-shadow:0 0 0 1.5px #5B47E0, 0 20px 80px #5B47E033}
.lbl{position:absolute;left:0;right:0;text-align:center;font-size:22px;font-weight:600}</style>
<div class="eyebrow in" ${ds(0.2)}>How NeuroPad fixes it</div>
<h1 class="in" ${ds(0.4)} style="font-size:68px">A <span class="grad">virtual controller</span> Android fully supports</h1>
<div class="fl">
  ${node('USB gamepad', 'your real pad', PAD(), 'in', d(0.42))}
  <div class="link in" ${d(0.5)}>
    <div class="lbl" style="top:2px;color:var(--cyan)">buttons</div>
    <svg viewBox="0 0 200 24" preserveAspectRatio="none" style="top:34px"><path class="flow1" d="M0 12H200" stroke="#22D3EE" stroke-width="5"/></svg>
    <svg viewBox="0 0 200 24" preserveAspectRatio="none" style="top:66px"><path class="flow2" d="M0 12H200" stroke="#9D86FF" stroke-width="5"/></svg>
    <div class="lbl" style="top:94px;color:#9D86FF">rumble</div></div>
  ${node('NeuroPad', 'the bridge', LOGO, 'in', ds(1.4))}
  <div class="link in" ${d(0.25)}>
    <svg viewBox="0 0 200 24" preserveAspectRatio="none" style="top:34px"><path class="flow1" d="M0 12H200" stroke="#22D3EE" stroke-width="5"/></svg>
    <svg viewBox="0 0 200 24" preserveAspectRatio="none" style="top:66px"><path class="flow2" d="M0 12H200" stroke="#9D86FF" stroke-width="5"/></svg></div>
  ${node('Virtual controller', 'Xbox or PlayStation', PAD(), 'in virt', d(0.2))}
  <div class="link in" ${d(0.3)}>
    <svg viewBox="0 0 200 24" preserveAspectRatio="none" style="top:34px"><path class="flow1" d="M0 12H200" stroke="#22D3EE" stroke-width="5"/></svg>
    <svg viewBox="0 0 200 24" preserveAspectRatio="none" style="top:66px"><path class="flow2" d="M0 12H200" stroke="#9D86FF" stroke-width="5"/></svg></div>
  ${node('Games', 'see a pad that can rumble', `<div style="width:170px;height:110px;border-radius:26px;border:4px solid #3B4766;display:grid;place-items:center;font-size:36px;font-weight:800;color:var(--muted)">GAME</div>`, 'in', d(0.32))}
</div>
<div class="legend">
  <span class="chip in" ${d(0.62)} ><span style="color:var(--cyan)">&#9679;</span> Buttons: gamepad &rarr; games</span>
  <span class="chip in" ${d(0.7)}><span style="color:#9D86FF">&#9679;</span> Rumble: games &rarr; gamepad motors</span>
  <span class="chip good in" ${d(0.8)}>within a few milliseconds</span>
</div>`,

  '04-fixed': () => `
<style>.wrap{display:flex;gap:90px;align-items:center;height:100%}
.big{flex:none;width:420px;height:420px;border-radius:50%;display:grid;place-items:center;color:#0A0E18;font-size:250px;
  background:radial-gradient(circle at 35% 30%,#6EE7B7,#10B981);box-shadow:0 0 0 26px #10B98122,0 0 0 56px #10B98111,0 30px 120px #10B98155}
.dev{display:flex;flex-direction:column;gap:26px;margin-top:40px}
.dev .card{padding:28px 34px}.dev h3{font-size:34px;margin:0 0 8px}.dev p{font-size:26px;color:var(--muted);margin:0 0 16px}
.dev .chip{font-size:24px;padding:10px 20px;margin-right:10px}</style>
<div class="wrap">
  <div class="big pop" ${ds(0.4)}>${CHECK}</div>
  <div>
    <div class="eyebrow in" ${ds(0.2)}>Confirmed on real hardware</div>
    <h1 class="in" ${ds(0.5)}>The problem is <span style="color:var(--green)">fixed.</span></h1>
    <div class="dev">
      <div class="card in" ${d(0.18)}><h3>GameSir G8 Plus (MFi)</h3><p>Nothing Phone (2) &middot; Android 16</p>
        <span class="chip good in" ${d(0.36)}>${CHECK} Input</span><span class="chip good in" ${d(0.42)}>${CHECK} Virtual controller</span><span class="chip good in" ${d(0.48)}>${CHECK} Rumble</span></div>
      <div class="card in" ${d(0.6)}><h3>Razer Kishi Ultra</h3><p style="margin:0">Works &middot; user report</p></div>
    </div>
  </div>
</div>`,

  '05-home': () => `
<style>.wrap{display:flex;gap:110px;align-items:center;height:100%}.wrap .phone{flex:none}</style>
<div class="wrap">
  <div class="phone in" ${ds(0.3)}><img style="--d0:3" src="${SHOTS}/home.png"></div>
  <div>
    <div class="eyebrow in" ${ds(0.4)}>Inside the app</div>
    <h1 class="in" ${ds(0.6)} style="font-size:64px;margin-bottom:56px">The Home screen</h1>
    <div class="callout slide" ${d(0.12)}><div class="n">1</div><div><h3>One-tap bridge</h3><p>A big power button starts and stops it.</p></div></div>
    <div class="callout slide" ${d(0.26)}><div class="n">2</div><div><h3>Status checklist</h3><p>System access, USB gamepad, virtual controller.</p></div></div>
    <div class="callout slide" ${d(0.44)}><div class="n">3</div><div><h3>Appear to games as&hellip;</h3><p>Pick the controller games should see.</p></div></div>
    <div class="callout slide" ${d(0.58)}><div class="n">4</div><div><h3>Live rumble meters</h3><p>Heavy and light motor, plus a test for each.</p></div></div>
  </div>
</div>`,

  '06-profiles': () => {
    const p = [['Xbox 360', 'USB', '045E:028E', 'Most compatible &middot; default'], ['Xbox One', 'Bluetooth', '045E:02FD', 'Xbox Wireless Controller'],
      ['Xbox Series', 'Bluetooth', '045E:0B13', 'Xbox Series X|S'], ['DualShock 4', 'Bluetooth', '054C:05C4', 'PlayStation prompts'], ['DualSense', 'USB', '054C:0CE6', 'PS5 controller']];
    return `
<style>.cards{margin-top:80px;display:grid;grid-template-columns:repeat(5,1fr);gap:26px}
.cards .card{padding:36px 30px;min-height:330px;display:flex;flex-direction:column}
.cards .pad{width:110px;color:#C9BEFF;margin-bottom:26px}
.cards h3{font-size:36px;margin:0 0 10px}.cards .bus{font-size:24px;color:var(--cyan);font-weight:600}
.cards .vid{font-family:'DejaVu Sans Mono',monospace;font-size:22px;color:var(--muted);margin-top:auto;padding-top:20px}
.cards .note{font-size:22px;color:var(--muted);margin-top:8px}
.hint{margin-top:56px;text-align:center}</style>
<div class="eyebrow in" ${ds(0.2)}>Five controller profiles</div>
<h1 class="in" ${ds(0.4)}>Games see the controller <span class="grad">you choose</span></h1>
<div class="cards">${p.map((x, i) => `<div class="card in" ${d((0.12 + i * 0.09).toFixed(2))}>${PAD()}<h3>${x[0]}</h3><div class="bus">${x[1]}</div><div class="note">${x[3]}</div><div class="vid">${x[2]}</div></div>`).join('')}</div>
<div class="hint"><span class="chip in" ${d(0.7)}>No rumble in a game? Try another profile.</span></div>`;
  },

  '07-controller': () => `
<style>.wide{width:1200px;margin:40px auto 0}.chips{margin-top:40px;display:flex;gap:24px;justify-content:center}</style>
<div class="eyebrow in" ${ds(0.2)}>Controller tab</div>
<h1 class="in" ${ds(0.4)} style="font-size:64px">See every input, live</h1>
<div class="wide in" ${ds(0.8)}><img class="ken" src="${SHOTS}/controller_landscape.png"></div>
<div class="chips">
  <span class="chip in" ${d(0.1)}>Sticks &middot; triggers &middot; buttons</span>
  <span class="chip in" ${d(0.4)}>Default &middot; Nintendo &middot; Custom layout</span>
  <span class="chip good in" ${d(0.72)}>~0.2 ms per packet</span>
</div>`,

  '08-connection': () => `
<style>.wrap{display:flex;gap:100px;align-items:center;height:100%}.wrap .phone{flex:none}
.modes{display:flex;flex-direction:column;gap:22px;margin-top:40px}
.modes .card{padding:26px 32px}.modes h3{font-size:34px;margin:0 0 6px}.modes p{font-size:25px;color:var(--muted);margin:0}
.modes .tag{float:right;font-size:20px;font-weight:700;padding:6px 14px;border-radius:999px;background:#241C4F;color:#B9A8FF}</style>
<div class="wrap">
  <div class="phone in" ${ds(0.3)}><img style="--d0:3" src="${SHOTS}/connection.png"></div>
  <div style="flex:1">
    <div class="eyebrow in" ${ds(0.4)}>No root needed</div>
    <h1 class="in" ${ds(0.6)} style="font-size:64px">Three ways to set it up</h1>
    <div class="modes">
      <div class="card in" ${d(0.2)}><span class="tag">Android 11+</span><h3>Wireless debugging</h3><p>Pair once on the phone. No computer needed.</p></div>
      <div class="card in" ${d(0.42)}><span class="tag">Any Android</span><h3>One-click PC installer</h3><p>Double-click on Windows, macOS or Linux.</p></div>
      <div class="card in" ${d(0.56)}><span class="tag">Optional</span><h3>Root</h3><p>Grant superuser access. No Wi-Fi or PC.</p></div>
    </div>
    <div style="margin-top:34px"><span class="chip good in" ${d(0.72)}>${CHECK} Only talks to 127.0.0.1 &middot; nothing leaves your phone</span></div>
  </div>
</div>`,

  '09-settings': () => `
<style>.wide{width:1200px;margin:40px auto 0}.chips{margin-top:40px;display:flex;gap:22px;justify-content:center;flex-wrap:wrap}</style>
<div class="eyebrow in" ${ds(0.2)}>Settings</div>
<h1 class="in" ${ds(0.4)} style="font-size:64px">Tune the rumble to your pad</h1>
<div class="wide in" ${ds(0.8)}><img class="ken" src="${SHOTS}/settings_landscape.png"></div>
<div class="chips">
  <span class="chip in" ${d(0.12)}>Strength 4.0x</span>
  <span class="chip in" ${d(0.3)}>Shortest rumble 80 ms</span>
  <span class="chip in" ${d(0.5)}>Ignore system vibration</span>
  <span class="chip in" ${d(0.7)}>Mirror phone vibration</span>
</div>`,

  '10-fixes': () => {
    const f = [['Long rumbles were cut off after 0.5 s', 0.2], ['Rumble went missing after a notification', 0.36],
      ['Launcher taps and keyboard clicks shook the gamepad', 0.5], ['Short effects felt weak and late', 0.68], ['Long rumbles stuttered', 0.8]];
    return `
<style>.list{margin-top:60px;display:flex;flex-direction:column;gap:22px;max-width:1500px}
.item{display:flex;align-items:center;gap:30px;padding:26px 34px}
.item .t{font-size:34px;flex:1}.item .t s{text-decoration-color:#F8717199;text-decoration-thickness:3px}
.item .ok{color:var(--green);font-size:42px}
.badge{font-size:22px;font-weight:700;letter-spacing:.12em;padding:10px 18px;border-radius:12px;background:#0F2A26;color:var(--green);border:1.5px solid #1F5A4B}</style>
<div class="eyebrow in" ${ds(0.2)}>NeuroPad 2.0 &middot; open-source rewrite</div>
<h1 class="in" ${ds(0.4)}>What's <span style="color:var(--green)">fixed</span></h1>
<div class="list">${f.map(([t, x]) => `<div class="card item slide" ${d(x)}><span class="ok">${CHECK}</span><span class="t">${t}</span><span class="badge pop" ${d((x + 0.04).toFixed(2))}>FIXED</span></div>`).join('')}</div>`;
  },

  '11-controllers': () => {
    const b = [['GameSir', 'G8 Plus (MFi), G8 Galileo, X2 Pro, X3 Pro, T4 Pro, Cyclone 2'], ['Razer', 'Kishi Ultra, Kishi V2 Pro, Kishi V3 Pro, Wolverine V2 / V3'],
      ['8BitDo', 'Ultimate (wired / 2.4G), Ultimate C, Pro 2, SN30 Pro'], ['Flydigi', 'Vader 3 / 3 Pro / 4 Pro, Apex 4'],
      ['Microsoft', 'Xbox 360, Xbox One, Xbox Series X|S, Elite'], ['Others', 'PowerA, PDP, Logitech F710 and other XInput pads']];
    return `
<style>.g{margin-top:60px;display:grid;grid-template-columns:repeat(3,1fr);gap:26px}
.g .card{padding:30px 34px;min-height:190px}.g h3{font-size:38px;margin:0 0 12px}.g p{font-size:25px;color:var(--muted);margin:0;line-height:1.45}
.foot{margin-top:46px;display:flex;gap:24px;justify-content:center}</style>
<div class="eyebrow in" ${ds(0.2)}>Supported controllers</div>
<h1 class="in" ${ds(0.4)}>XInput USB gamepads <span class="grad">with rumble motors</span></h1>
<div class="g">${b.map((x, i) => `<div class="card in" ${d((0.3 + i * 0.07).toFixed(2))}><h3>${x[0]}</h3><p>${x[1]}</p></div>`).join('')}</div>
<div class="foot"><span class="chip good in" ${d(0.78)}>Mode switch? Set it to Xbox / XInput</span></div>`;
  },

  '12-install': () => `
<style>.steps{margin-top:60px;display:grid;grid-template-columns:repeat(3,1fr);gap:30px}
.steps .card{padding:40px 38px;min-height:330px}.steps .n{width:70px;height:70px;border-radius:22px;display:grid;place-items:center;font-size:34px;font-weight:800;
  background:linear-gradient(135deg,#7C5CFF,#22D3EE);margin-bottom:28px}
.steps h3{font-size:40px;margin:0 0 14px}.steps p{font-size:27px;color:var(--muted);margin:0;line-height:1.45}
.url{margin-top:50px;display:flex;justify-content:center}
.url div{font-family:'DejaVu Sans Mono',monospace;font-size:30px;padding:22px 36px;border-radius:20px;background:#0E1424;border:1.5px solid #3B3170;color:#C9BEFF}</style>
<div class="eyebrow in" ${ds(0.2)}>Get NeuroPad</div>
<h1 class="in" ${ds(0.4)}>Up and running in three steps</h1>
<div class="steps">
  <div class="card in" ${d(0.1)}><div class="n">1</div><h3>Download</h3><p>Grab the latest release from GitHub.</p></div>
  <div class="card in" ${d(0.3)}><div class="n">2</div><h3>Install</h3><p>One-click installer with a PC, or the APK on Android 11+.</p></div>
  <div class="card in" ${d(0.62)}><div class="n">3</div><h3>Play</h3><p>Plug in the gamepad, open NeuroPad, tap the power button.</p></div>
</div>
<div class="url in" ${d(0.18)}><div>github.com/abdullah-amr871/NeuroPad-Virtual-GamePad-Simulation</div></div>`,

  '13-outro': () => `
<style>.c{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
.c .logo{width:170px;height:170px;margin-bottom:34px}
.tags{display:flex;gap:20px;justify-content:center;margin:26px 0 60px}
.sup{display:flex;gap:30px;justify-content:center}
.sup .card{width:430px;padding:30px}.sup h3{font-size:34px;margin:0 0 8px}.sup p{font-size:24px;color:var(--muted);margin:0;font-family:'DejaVu Sans Mono',monospace}
.thanks{margin-top:64px;font-size:56px;font-weight:800}</style>
<div class="c">
  <div class="pop" ${ds(0.3)}>${LOGO}</div>
  <h1 class="in" ${ds(0.5)} style="font-size:96px">Neuro<span class="grad">Pad</span></h1>
  <div class="tags">
    <span class="chip in" ${ds(0.9)}>Free</span><span class="chip in" ${ds(1.0)}>Open source</span>
    <span class="chip in" ${ds(1.1)}>No ads</span><span class="chip in" ${ds(1.2)}>No account</span>
  </div>
  <div class="sup">
    <div class="card in" ${d(0.22)}><h3>&#9733; Star on GitHub</h3><p style="font-family:Inter">helps others find it</p></div>
    <div class="card in" ${d(0.36)}><h3>Ko-fi</h3><p>ko-fi.com/abdullahamr871</p></div>
    <div class="card in" ${d(0.42)}><h3>PayPal</h3><p>paypal.me/abdullahamr871</p></div>
  </div>
  <div class="thanks in" ${d(0.82)}><span class="grad">Thanks for watching!</span></div>
</div>`,
};

// Thumbnail (1280x720, rendered at 1920x1080 and scaled down).
const thumbnail = () => `
<style>.stage{padding:0}.t{position:absolute;left:120px;top:150px;width:1000px}
.t h1{font-size:150px;line-height:.95;margin:0 0 30px;letter-spacing:-.03em}
.t .sub{font-size:52px;font-weight:700;color:var(--muted)}
.fixed{position:absolute;left:120px;bottom:120px;display:flex;gap:22px}
.fixed .chip{font-size:44px;padding:22px 40px}
.art{position:absolute;right:110px;top:200px;width:620px;height:620px;display:grid;place-items:center}
.art .logo{width:560px;height:560px}</style>
<div class="t"><h1>USB gamepad<br><span class="grad">rumble</span> on<br>Android</h1></div>
<div class="fixed"><span class="chip good">${CHECK} FIXED</span><span class="chip">No root</span></div>
<div class="art">${LOGO}</div>`;

module.exports = { scenes, page, thumbnail };
