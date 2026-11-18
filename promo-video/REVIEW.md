# Review Note — Time Wallet Product Intro (`promo-video`)

## 1. Creative Calls & Alternatives Not Built
- **Chosen Storyline — Option A ("24 Honest Hours"):** Built a `42.0 s` (`1920×1080`, `30 fps`) 6-scene product showcase (`hook` → `timer` → `wally-ai` → `schedule` → `insights` → `endcard`) in **Vietnamese** with clean English secondary lines (`Your time. Your choices. Your life.`), using local MusicGen ambient BGM (`assets/bgm/track.wav`, `-13.3 LUFS` source mixed at `0.44` base volume with 6-lane dynamic carve/ducking via `carve.mjs` `--strength 0.6`), 5 measured offline SFX clips across lanes 2–3, and 6 Vietnamese neural voiceover clips (`edge-tts` `vi-VN-HoaiMyNeural`) grouped in `<hf-audio-group id="voiceover">` on lane 4.
- **Ground & Brand Fidelity:** Pulled light/dark palette tokens directly from `app/src/main/java/com/timewallet/mvp/ui/theme/Theme.kt` (`#F5F7F3`, `#172B27`, `#176B57`, `#103F35`, `#258F76`, `#DCF3E8`), bundled local Vietnamese-complete fonts (`Be Vietnam Pro` 400–800 and `JetBrains Mono` 500/700), official app mark (`assets/brand/app-icon-mark.png`), Wally mascot (`assets/brand/wally-hero.webp`), and 5 authentic product screenshots (`assets/screens/`).
- **Storylines Not Built:**
  - *Option B ("Meet Wally, Your Time Coach")*: character-led continuous horizontal pan with Wally driving every UI card—kept Wally instead focused as the natural-language assistant (`wally-ai`) and endcard companion so authentic phone screens remain the visual proof.
  - *Option C ("Zero Fake Scores — The Honest Ledger")*: high-contrast kinetic geometric ledger grid without phone frames—not chosen because real Android screenshots communicate trustworthiness and shipping completeness better for a product intro.

## 2. Verification Scope (Measured vs. Visual vs. Unheard)
- **Measured (`hyperframes check --json`, latest report `84d028bc-1b1a-4793-81c0-e6270b58abd3`):**
  - `lint`: `0` errors, `0` warnings across 7 HTML files.
  - `runtime`: `0` errors, `0` warnings.
  - `layout`: `0` overlap/clipping findings across 9 base + overlap pass samples (`42 s`, face check `0`).
  - `contrast`: `71/71` elements passed across 5 contrast samples.
  - `craft`: `0` errors, `0` warnings across 336 samples (`0.125 s` step).
  - `audio` (static composition, 6 voiceover clips + SFX + carve lanes): `0` findings.
- **Visually Inspected (`hyperframes snapshot --review`, session `474852ee-6acf-414a-b993-367f5cf10c10`):**
  - Visual compositions unchanged after final audio-only pass; inspected `review-beats.jpg` (12 beat panels), `review-phone.jpg` (360 px legibility simulation), `review-seam-05.jpg` (37.3–37.7 s cut), and full-res `beat-10-at-34.25s.png`, alongside previous pass sheets `review-seam-01..04.jpg`.
  - Verified fixes from review pass 1→2: eliminated dimmed/blank cut flashes at seams (`7.0 s`, `14.5 s`, `23.0 s`, `37.5 s`), prevented two-line wrapping on `Th 4..Th 3` weekly bars, resolved the `dead_stretch` / `line_unread` findings in `insights.html` (`#ins-coach-action` sibling pill `Lên lịch →`), and enlarged the `insights` donut center ring (`r=104`, `stroke-width=26`, `250×250` box) so `"6h 30 / Ngủ 44%"` clears the arcs without touching strokes.
- **What Was Measured on Rendered MP4 (`renders/time-wallet-intro.mp4`):**
  - Delivery export (`1920×1080`, `30 fps`, H.264 `yuv420p` + AAC `48 kHz` stereo, `42.000000 s`, `7,090,884 bytes`).
  - Mixed audio measured via `hyperframes audio renders/time-wallet-intro.mp4 --json`: `0` warnings, detected as `voice` (`voiced: 0.4905`), onset at `0.416 s`, `-13.2 LUFS` integrated (`-12.3 LUFS` short-term max), `-1 dBTP` true peak, `0` clipping samples, fade-out closing cleanly (`tailEnd: 40.989 s`, silence `41.2–42.0 s`).
  - Previously extracted and visually inspected 3 decoded MP4 frames (`3.5 s` hook, `35.8 s` insights, `39.8 s` endcard) matching browser captures (video track visually identical).
- **What Was NOT Checked By Ear:**
  - All audio cues (`voice-01..06.wav`, `click-soft.mp3`, `pop.mp3`, `whoosh-short.mp3`, `typing.mp3`, `chime.mp3`, and local `musicgen-small` `track.wav` with 6-lane carve) were **measured numerically** (`hyperframes audio --json` + `carve.mjs` + `check`), **not heard** aloud. Human ear verification should confirm Vietnamese pronunciation naturalness (`vi-VN-HoaiMyNeural`) and ducking smoothness in the rendered MP4.

## 3. Weakest Shot & Future Iteration
- **`hook` → `timer` first beat (`7.0 s`–`7.3 s` in `review-seam-01.jpg` / `beat-03`)** & **`schedule` → `insights` palette transition (`31.0 s`)**:
  - At `7.0 s` (`timer` entry), the phone shell holds the frame alone for `0.15 s` while the headline `"Đo trực tiếp từ phút thực"` begins its left-to-right `clipPath` wipe. With more time, starting the eyebrow/headline reveal slightly tighter (`0.04 s`) would give the right half of frame `7.0 s`–`7.15 s` immediate counter-weight alongside the phone.
  - At `31.0 s`, the `schedule` → `insights` crossfade passes briefly through an emerald wash before `#ins-bars-card` enters at `31.08 s`. A matched shape/emerald-card expansion from the Schedule header could make the light-to-dark shift feel physically continuous rather than an iris/fade.

## 4. Warnings Kept On Purpose
- **None.** The final `hyperframes check --json` run reported `0` errors and `0` warnings across every category (`lint`, `runtime`, `layout`, `contrast`, `craft`, `audio`).
