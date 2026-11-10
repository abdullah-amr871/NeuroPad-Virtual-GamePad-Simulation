"""Generate the voiceover for each scene with edge-tts.

Writes build/audio/<id>.mp3 and build/audio/<id>.json (sentence timings, seconds).
Usage: python3 tts.py [--ca-bundle PATH]
"""
import asyncio, json, ssl, sys, pathlib

import edge_tts
import edge_tts.communicate as communicate

VOICE = "en-US-AndrewNeural"
RATE = "+4%"
HERE = pathlib.Path(__file__).parent
OUT = HERE / "build" / "audio"

if "--ca-bundle" in sys.argv:  # for networks behind a TLS-inspecting proxy
    communicate._SSL_CTX = ssl.create_default_context(cafile=sys.argv[sys.argv.index("--ca-bundle") + 1])


async def one(scene):
    mp3 = OUT / f"{scene['id']}.mp3"
    cues = []
    with mp3.open("wb") as f:
        async for chunk in edge_tts.Communicate(scene["text"], VOICE, rate=RATE).stream():
            if chunk["type"] == "audio":
                f.write(chunk["data"])
            elif chunk["type"] == "SentenceBoundary":
                start = chunk["offset"] / 1e7
                cues.append({"start": start, "end": start + chunk["duration"] / 1e7, "text": chunk["text"]})
    (OUT / f"{scene['id']}.json").write_text(json.dumps(cues, indent=1))
    print(scene["id"], len(cues), "sentences")


async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for scene in json.loads((HERE / "narration.json").read_text()):
        done = OUT / f"{scene['id']}.json"
        if done.exists() and (OUT / f"{scene['id']}.mp3").stat().st_size > 0 and "--force" not in sys.argv:
            continue
        for attempt in range(4):
            try:
                await asyncio.wait_for(one(scene), 90)
                break
            except Exception as e:  # network hiccups: retry
                print(scene["id"], "retry", attempt + 1, repr(e))

asyncio.run(main())
