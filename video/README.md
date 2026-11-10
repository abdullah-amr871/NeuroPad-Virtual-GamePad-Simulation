# NeuroPad explainer video

A 3-minute YouTube video that explains NeuroPad and shows that USB gamepad rumble on
Android is fixed.

| File | What it is |
| :--- | :--- |
| `NeuroPad_explainer.mp4` | The video: 1920x1080, 30 fps, H.264 + AAC, ready to upload |
| `NeuroPad_explainer.srt` | English captions (upload under *Subtitles*) |
| `thumbnail.png` | 1280x720 thumbnail with the GameSir G8+ MFi (built by `thumbnail.js`) |
| `YOUTUBE.md` | Title, description, chapters and tags to paste into YouTube Studio |
| `narration.json` | The script, one entry per scene |
| `scenes.js` | The look of each scene (HTML/CSS) |
| `tts.py` | Generates the voiceover with Microsoft Edge neural voices (`edge-tts`) |
| `thumbnail.js` | Builds the thumbnail; downloads GameSir's product photo into `build/` (not committed) |
| `render.js` | Renders every frame with Chromium and assembles the video with ffmpeg |

## Rebuild

Change the script in `narration.json` or a scene in `scenes.js`, then:

```bash
pip install edge-tts
python3 tts.py --force        # voiceover -> build/audio (drop --force to keep existing lines)
node render.js                # frames + audio -> NeuroPad_explainer.mp4, .srt, thumbnail.png
node render.js --still 05-home 6   # preview one frame -> build/stills/05-home.png
node thumbnail.js             # thumbnail only
```

Needs Node with Playwright (Chromium) and ffmpeg. Scene lengths follow the voiceover, so the
picture always stays in sync with the narration. The app screenshots come from
`docs/screenshots`.
