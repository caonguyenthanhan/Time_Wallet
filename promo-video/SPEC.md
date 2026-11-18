# Time Wallet — Production Spec (`SPEC.md`)

```text
GOAL:    Viewer finishes feeling calm clarity — Time Wallet tracks 24 hours honestly (measured, timed, untimed, planned) with Wally AI assisting under user control, and clicks to try/star the app.
OUTPUT:  HyperFrames, 1920 x 1080, 30 fps
TIME:    42.0 s x 30 fps = 1260 frames; ~90 BPM calm underscore (20 frames/beat; phrase cuts at 7.0s, 14.5s, 23.0s, 31.0s, 37.5s)
LOOK:    ground #F5F7F3 (scenes 1–4, 6) and deep forest #103F35 (scene 5 payoff) with 2% static SVG paper grain, zero full-screen dark voids;
         ink #172B27 (light ground) / #F5F7F3 (dark ground), muted #5A7268 / #B6D4C4;
         one primary accent #176B57 (plus bright mint #258F76 & soft mint #DCF3E8) rationed to the active focus, one keyword per headline, and the brand wallet mark;
         display face Be Vietnam Pro 700/800 at 54–88 px (-0.025em tracking), body Be Vietnam Pro 400/600 at 28–36 px, tabular numbers JetBrains Mono 500/700 at 28–64 px;
         medium: tactile daylight ledger paper + physical Android device frame (#1b1d22 bezel, 42 px radius, layered 0 36px 80px rgba(16,63,53,0.16) contact shadow) + 2 px #DDE8E0 hairline card surfaces (#FFFFFF);
         light: soft top-left studio key, crisp 2% grain overlay (data-layout-ignore), 0% neon glow.
OBJECTS:
  1. App Mark: real `assets/brand/app-icon.png` + `Time Wallet` wordmark (never redrawn from memory).
  2. Wally Mascot: real `assets/brand/wally-hero.webp` (transparent 3D robot with calendar & checklist).
  3. Physical Phone Prop: dark graphite Android frame (`border-radius: 44px`, `9px` bezel, subtle camera punch-hole) holding real `assets/screens/*.png` captures (`01_tong-quan`, `02_dong-ho`, `03b_wally-proposals`, `06a_lich-trinh-ngay`, `05_phan-tich`, `01b_wally-coach-ai`).
  4. Zoomed Interactive Product Modules (1.7–1.8x scale beside phone): 24h Wallet Hero Card (`14h 50`), Live Timer Ring (`00:00 -> 00:45`), Wally Chat + 3 Proposal Cards with `v3` SVG CategoryBadges + `Xác nhận toàn bộ` tick, Schedule Grid (`KH 1h -> TT 1h 12p (+12p)` + `Chưa gắn giờ`), 7-day Bar Chart (`106h 15`) + Interactive 2D Donut (`Ngủ 6h 30 · 44%`).
MOTION:
  - enter: power4.out / expo.out (0.45–0.70 s = 14–21 frames);
  - blur-title-in: opacity 0->1 + blur(10px)->0px (0.65 s = 20 frames, power3.out, dead still once landed);
  - ui-pop: back.out(1.4) on scale/y (0.36 s = 11 frames, 50 ms stagger, never bounce on opacity);
  - exit: power3.in (0.28–0.35 s = 8–11 frames);
  - camera/morph: power2.inOut (0.45–0.60 s).
SHOTS:
  1. [0–210f | 0.0–7.0s]   hook       — Hero: "Time Wallet" fades in cleanly at center-left while `VÍ THỜI GIAN HÔM NAY` bar fills to `14h 50` and `Dự kiến 2h 40` stays separated; cursor clicks `Đồng hồ` tile (6.75s) -> morph into Timer. Sound: pop (2.6s), click-soft (6.75s).
  2. [210–435f | 7.0–14.5s] timer     — Hero: `02_dong-ho.png` phone + 1.8x Timer Ring; tag pills pop, cursor clicks `▶ Bắt đầu đo` (8.8s), timer sweeps `00:00 -> 00:45` as `ĐÃ ĐO` locks; morph into Wally. Sound: whoosh-short (7.1s), click-soft (8.8s), pop (12.2s).
  3. [435–690f | 14.5–23.0s] wally-ai — Hero: `wally-hero.webp` + `03b_wally-proposals.png` phone + zoomed chat box typing Vietnamese sentence -> 3 dots pulse -> 3 proposal cards pop -> cursor clicks `Xác nhận toàn bộ` -> SVG tick draws -> whip to Schedule. Sound: typing (16.0s), pop (19.0s), click-soft (20.4s), chime (20.8s).
  4. [690–930f | 23.0–31.0s] schedule — Hero: `06a_lich-trinh-ngay.png` phone + 1.7x Schedule Grid; mode pill glides `Ngày -> 3 ngày -> Tuần -> Ngày`, `Đọc sách (KH)` 60p vs `Thực tế` expands to `72p (+12p)`, `Chưa gắn giờ` tray highlights -> emerald colour flood into Insights. Sound: whoosh-short (23.05s), click-soft (24.8s), pop (28.45s).
  5. [930–1125f | 31.0–37.5s] insights — Hero: Deep forest `#103F35` stage + `05_phan-tich.png` + `106h 15` 7-day bars grow + 2D Donut draws & `Ngủ 44%` slice pops out + `✦ Wally AI Coach` badge -> donut circle match-cuts to End Card. Sound: pop (33.8s), chime (35.2s).
  6. [1125–1260f | 37.5–42.0s] endcard — Hero: `#F5F7F3` ground; real `app-icon.png` + `wally-hero.webp` land center-top, `"Time Wallet — Ví Thời Gian"` wipes in, `"Thời gian của bạn. Lựa chọn của bạn."` / `"Your time. Your choices. Your life."` + platform pill hold dead still for 2.6 s. Sound: chime (37.85s), BGM fade-out (39.8–42.0s).
BANNED:  dark void floating mini-cards; purple/blue gradients; glassmorphism blur cards; neon/glow/bloom/particles; serif + italic + 01/02 mono labels; three identical cards in a row; breathing/floating idle loops on UI or titles; post-land title drift; docked corner logo; invented UI screens; clipped text under 26 px.
QA:      Frames 0s (non-empty thumbnail), 1.2s (fade-in title settled), 4.5s (14h50 bar + Dự kiến split), 10.5s (live timer ticking), 19.5s (3 proposal cards + Wally), 27.5s (KH vs TT +12p diff), 34.5s (dark forest 7-day bars + 2D donut pop), 40.5s (end card hold); plus cut strips at 6.9–7.1s, 14.4–14.6s, 22.9–23.1s, 30.9–31.1s, 37.4–37.6s, and 360px phone readability sheet.
```
