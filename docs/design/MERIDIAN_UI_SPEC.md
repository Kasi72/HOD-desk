# Meridian UI — Implementation Specification for Psychiatry HOD Desk (Android, Jetpack Compose)

**Audience:** the coding agent (ChatGPT Codex) or developer implementing the UI.
**Goal:** reproduce the Meridian design exactly — every colour, size, radius, shadow and spacing value below is final.
**Reference source:** `docs/design/meridian/*.dc.html` holds the original design files, one per screen. They are HTML with inline styles, so every number in this spec can be cross-checked there. If this spec and an HTML file ever disagree, the HTML file wins.

---

## 0. Ground rules for the implementer

1. **Do not change app logic.** Repositories, ViewModels, DAOs, import/consent/redaction/extraction pipelines and the calendar write flow stay as they are. This is a presentation-layer rewrite only.
2. **Units:** the design was drawn on a 390-wide phone. **1 design px = 1 dp. 1 font px = 1 sp.** Letter-spacing given in `em` maps to Compose `TextUnit.em` directly (e.g. `-0.02em` → `(-0.02).em`).
3. **Light theme only for v1.** The design is light. Section 21 gives derived dark tokens; ship them only if asked.
4. **Never use Material ripple, Material elevation or default Material colours** on any Meridian component. Depth comes from the dual-shadow system in Section 4.
5. **Fonts are bundled** in `res/font` (the app is offline and privacy-first). Do not use downloadable Google Fonts.
6. **Accessibility is mandatory:** touch targets ≥ 44 dp, every icon-only button has a `contentDescription`, and status is always shown as a word plus a colour, never colour alone.
7. Build order: tokens → shadow modifiers → components → screens (Section 22). Compile after each step.

---

## 1. Design concept (read once)

Meridian is a **tactile porcelain workspace**: a warm off-white ground with light falling from the **top-left**.

- **Raised** surfaces (two shadows: a white highlight top-left, a warm umber shade bottom-right) are things you **press** or **read as a card**.
- **Inset** (sunken) surfaces are things you **type into**, **select inside**, or that are **currently selected** (the selected tab, the selected segment).
- **Dark ink panels** carry the most important summary on a screen (Secretary brief, voice capture, proposal bar, profile card).

**The three-layer rule** (the core of the product, enforced everywhere):

| Layer | Meaning | Visual |
|---|---|---|
| **Source** | What the letter/message/call actually said | Warm *paper* card `#FBF7EF`, **serif** text, amber highlight marks, mono reference numbers |
| **AI reading** | What the on-device model inferred | **Dashed indigo** border, indigo sparkle icon, pale indigo fill. Never solid. |
| **Approved** | What the HOD decided | **Solid teal**. Teal is used only for approved work and the single primary action on a screen. |

An AI suggestion must never use solid teal styling.

---

## 2. Colour tokens

Create `ui/theme/MeridianColors.kt` holding an `object MeridianColors`. All values are ARGB hex. Where a colour has opacity, the alpha is already folded into the 8-digit hex.

### 2.1 Neutrals and surfaces

| Token | Hex | Use |
|---|---|---|
| `ground` | `#FFECE9E4` | Screen background everywhere |
| `porcelain` | `#FFF3F1ED` | Raised surface fill (cards, buttons, tab bar) |
| `porcelainBorder` | `#BFFFFFFF` | 1 dp border on raised surfaces (white 75%) |
| `well` | `#FFE5E1DB` | Inset surface fill (search, segmented track, selected tab, icon wells) |
| `trackOff` | `#FFDEDAD3` | Switch track off, progress ring track |
| `hairline` | `#1A544A3C` | Row dividers inside grouped cards (umber 10%) |
| `ink` | `#FF15181E` | Primary text, dark panels |
| `ink2` | `#FF3D434E` | Strong secondary text, icons in wells |
| `text2` | `#FF4A505B` | Secondary body text |
| `text3` | `#FF5A6170` | Labels, captions, inactive tab labels |
| `text4` | `#FF6B7180` | Hour labels, muted numbers |
| `chevron` | `#FF9AA3B2` | Chevrons, arrows between layer chips |
| `disabledText` | `#FF8A8F99` | Disabled control text |

### 2.2 Dark panels

| Token | Hex | Use |
|---|---|---|
| `panelTop` | `#FF262B34` | Dark panel gradient start (160°) |
| `panelBottom` | `#FF15181E` | Dark panel gradient end |
| `panelBrief` | `#FF1A1E25` | Secretary brief solid fill |
| `panelWell` | `#FF12151A` | Inset tiles inside dark panels |
| `onPanel` | `#FFFFFFFF` | Primary text on dark |
| `onPanel2` | `#FFB8BFCB` | Secondary text on dark |
| `onPanel3` | `#FF8A93A3` | Tertiary text on dark, mono hour labels |
| `onPanel4` | `#FF9AA3B2` | Timestamps on dark |
| `onPanelChip` | `#FFDDE1E8` | Chip text on dark |
| `captureTop` | `#FF2A303A` | Capture button / selected day gradient start |

### 2.3 Approved (teal)

| Token | Hex | Use |
|---|---|---|
| `teal` | `#FF0E6B63` | Approved state, primary links, selected-tab icon |
| `tealPressed` | `#FF0A524C` | Link pressed |
| `tealBtnTop` | `#FF158A7E` | Primary button gradient start (170°) |
| `tealBtnBottom` | `#FF0B5E57` | Primary button gradient end |
| `tealBright` | `#FF17A493` | Status dot, glow |
| `tealBright2` | `#FF1DA392` | Event fill gradient start, progress |
| `tealBright3` | `#FF1DB3A1` | Avatar / voice mic gradient start |
| `tealSwitchA` | `#FF12907F` | Switch on, gradient left |
| `tealSwitchB` | `#FF2BC4B0` | Switch on, gradient right |
| `mint` | `#FF7FDCCD` | Accent on dark (time highlights, capture "+") |
| `mintLight` | `#FF5DD6C5` | "Add 1" button gradient start |
| `tealSoft` | `#FFDCEEEA` | Next-step card, "Approved" chip background |
| `tealOnTeal` | `#FFD2F1EC` | Secondary text on teal |
| `tealLabelOnTeal` | `#FFBFEDE6` | Caps label on teal |
| `tealInk` | `#FF072521` | Text on mint button |

### 2.4 AI (indigo)

| Token | Hex | Use |
|---|---|---|
| `ai` | `#FF463FBF` | AI text, icons |
| `aiDeep` | `#FF2E2A85` | Proposed event title |
| `aiSoft` | `#FFECEBFB` | AI chip / button fill |
| `aiCard` | `#FFF7F6FE` | AI reading card fill |
| `aiProposal` | `#FFF4F3FE` | Proposed calendar block fill |
| `aiDash` | `#FFA9A4EE` | 1 dp dashed border (chips, buttons) |
| `aiDashCard` | `#FFB4AFF0` | 1.5 dp dashed border (AI card) |
| `aiDashEvent` | `#FF8F89EA` | 1.5 dp dashed border (proposed block); progress gradient start |
| `aiOnDark` | `#FFBDB8F7` | "Secretary brief" label on dark |
| `aiOnDarkDash` | `#FFA49EF5` | Proposed block in dark ribbon |
| `aiDivider` | `#1F463FBF` | Divider inside AI card (indigo 12%) |

### 2.5 Source (paper)

| Token | Hex | Use |
|---|---|---|
| `paper` | `#FFFBF7EF` | Source card / excerpt fill |
| `paperBorder` | `#FFEFE5D2` | Source card 1 dp border |
| `paperText` | `#FF2B2418` | Serif source text |
| `paperLabel` | `#FF6B5430` | Source labels, mono refs |
| `paperChip` | `#FFF5EEE2` | Source layer chip fill |
| `paperBtn` | `#FFF3EBDC` | "Open original" button fill |
| `paperBtnText` | `#FF5C4626` | "Open original" text |
| `mark` | `#FFF8DE9F` | Highlight behind supporting words |
| `markText` | `#FF3D2C0A` | Highlighted text colour |
| `bronze` | `#FF8C7048` | Section numerals, spacing bars |
| `bronzeLight` | `#FFC9B693` | Spacing bar gradient start |
| `railSource` | `#FFD6C29C` | Provenance dot / rail start |

### 2.6 Status

| Status | Background | Text |
|---|---|---|
| Overdue | `#FFF9E3E1` | `#FFA3221B` |
| Needs review / Confirm / Due soon / Clarify | `#FFF8EBD6` | `#FF8E4504` |
| Found | `#FFDFF1E6` | `#FF13653A` |
| Delegated | `#FFE1E9F6` | `#FF1F4E96` |
| Neutral (Exact words, Not stated) | `#FFE5E1DB` | `#FF3D434E` |
| Clash block | `#FFFCEFEE`, dashed border `#FFD9817A` | title `#FF8C1D17`, label `#FFA3221B` |

Other: `badgeRed #FFC2372E`; `nowRed #FFFF7A6B` (the now-line in the dark ribbon); delegate avatar gradient `#FF3366B0 → #FF1F4E96`; office-staff avatar gradient `#FF8C7048 → #FF6B5430`.

Gradient angles: CSS `160deg` means "towards bottom, slightly right". In Compose use `Brush.linearGradient(colors, start = Offset(w*0.33f, 0f), end = Offset(w*0.67f, h))`. For `170deg` use start `(w*0.41f, 0)` and end `(w*0.59f, h)`. For `90deg` use `Brush.horizontalGradient`.

---

## 3. Typography

### 3.1 Fonts to bundle (`res/font/`)

| File | Source (Google Fonts, OFL licence) | Weights |
|---|---|---|
| `jakarta_regular.ttf`, `jakarta_medium.ttf`, `jakarta_semibold.ttf`, `jakarta_bold.ttf`, `jakarta_extrabold.ttf` | Plus Jakarta Sans | 400, 500, 600, 700, 800 |
| `sourceserif_regular.ttf`, `sourceserif_medium.ttf`, `sourceserif_italic.ttf` | Source Serif 4 (static, optical size *Text*) | 400, 500, 400 italic |
| `plexmono_regular.ttf`, `plexmono_medium.ttf` | IBM Plex Mono | 400, 500 |
| `notosanstamil_regular.ttf`, `notosanstamil_semibold.ttf` | Noto Sans Tamil | 400, 600 |

Tamil fallback: Compose `FontFamily` has no per-glyph fallback list. Rely on the Android system fallback, which covers Tamil out of the box. For any field known to hold Tamil (Library results, source quotes in Tamil), set `fontFamily = NotoSansTamil` explicitly.

Global text setting: `PlatformTextStyle(includeFontPadding = false)` and `LineHeightStyle(alignment = Center, trim = None)` on every style.

### 3.2 Type scale (`MeridianType`)

| Style | Family / weight | Size (sp) | Line height | Letter spacing | Use |
|---|---|---|---|---|---|
| `display` | Serif 500 | 40 | 40 | -0.02em | Screen titles: Today, Tasks, October, Library, Settings, Department, Privacy, On-device AI |
| `sheetTitle` | Serif 500 | 30 | 34 | -0.015em | "Capture" |
| `taskTitle` | Serif 500 | 28 | 33 (1.18) | -0.015em | Task detail title |
| `section` | Serif 500 | 24 | 28 | -0.01em | "Must act" |
| `brief` | Serif 400 | 22 | 28.6 (1.3) | 0 | Secretary brief sentence |
| `navTitle` | Serif 500 | 21 | 26 | 0 | "Review" in nav bar; privacy hero line (21, lh 1.2) |
| `aiAction` | Jakarta 800 | 18 | 23.8 (1.32) | -0.015em | AI suggested action |
| `cardTitle` | Jakarta 700 | 16 | 21.1 (1.32) | -0.01em | Must-act card titles |
| `rowTitle` | Jakarta 700 | 15 | 19.8 | -0.005em | Task rows, team names, list rows |
| `body` | Jakarta 600 | 14.5 | 19.5 | 0 | List row labels |
| `bodyMedium` | Jakarta 500 | 15 | 21.75 (1.45) | 0 | Next-step text |
| `meta` | Jakarta 500 | 12 | 16 | 0 | Due times, subtitles |
| `metaSmall` | Jakarta 500 | 11.5 | 15 | 0 | Footnotes |
| `label` | Jakarta 700, UPPERCASE | 10.5 | 14 | 0.12em | Eyebrows, section labels (colour `text3` unless stated) |
| `tab` | Jakarta 700 | 10.5 | 13 | 0 | Tab bar labels |
| `pill` | Jakarta 700 | 11.5 (11 in rows) | 14 | 0 | Status pills |
| `button` | Jakarta 800 | 16 (primary) / 14.5 (secondary) / 13.5 (small) | 20 | -0.005em | Buttons |
| `stat` | Jakarta 800 | 26 | 28 | -0.03em | Counts in the brief |
| `source` | Serif 400 | 15.5 | 24 (1.55) | 0 | Source quotes (Review) |
| `sourceSmall` | Serif 400 | 14 | 21 (1.5) | 0 | Excerpts (Library, Task provenance) |
| `mono` | Plex Mono 400 | 10.5–11 | 14 | 0 | GO / Rc. numbers, times, counts like "01" |

---

## 4. Depth system (shadows): the most important section

Compose `Modifier.shadow` can't draw two coloured shadows or inner shadows. Implement these three modifiers in `ui/theme/Neu.kt` with `android.graphics.Paint.setShadowLayer`.

> **Blur mapping:** CSS blur radius `B` → `setShadowLayer(radius = B / 2)`. All offsets map 1:1 in dp. Requires hardware-accelerated shadow layers (API 28+). If `minSdk < 28`, wrap the whole call in `if (Build.VERSION.SDK_INT >= 28)` and otherwise fall back to `Modifier.shadow(4.dp, shape)`.

### 4.1 Shadow specs (exact)

| Name | Shadow 1 (highlight, top-left) | Shadow 2 (shade, bottom-right) | Fill | Border |
|---|---|---|---|---|
| **Card** (`rz`) | dx -6, dy -6, blur 14, `#E6FFFFFF` (white 90%) | dx 7, dy 9, blur 22, `#26544A3C` (umber 15%) | `porcelain` | 1 dp `porcelainBorder` |
| **Raised small** (`rzs`) | dx -3, dy -3, blur 8, `#F2FFFFFF` (white 95%) | dx 4, dy 5, blur 11, `#24544A3C` (umber 14%) | `porcelain` | 1 dp `porcelainBorder` |
| **Inset** (`in`) | inner dx 3, dy 3, blur 7, `#2B544A3C` (umber 17%) | inner dx -3, dy -3, blur 7, `#D9FFFFFF` (white 85%) | `well` | none |
| **Inset (selected tab)** | inner 3,3 blur 7 `#2E544A3C` (18%) | inner -3,-3 blur 7 `#D9FFFFFF` | `well` | none |
| **Inset small** (icon wells, checkbox) | inner 2,2 blur 5 (4 for checkbox) `#2B544A3C` (22% for checkbox `#38544A3C`) | inner -2,-2 blur 5 (4) `#D9FFFFFF` (90% for checkbox `#E6FFFFFF`) | `well` | none |
| **Dark hero** (brief, profile, privacy hero, voice card) | dx -6, dy -6, blur 14, `#D9FFFFFF` | dx 10, dy 14–16, blur 28–30, `#5215181E` (ink 32%) | dark | none |
| **Dark well** (stat tiles, ribbon, chips on dark) | inner 2,2 blur 5 `#73000000` (45%) | inner -2,-2 blur 5 `#0DFFFFFF` (5%) | `panelWell` | none |
| **Primary button** | dx -5, dy -5, blur 12, `#D9FFFFFF` | dx 7, dy 10, blur 20, `#590E6B63` (teal 35%) | teal gradient 170° | top inner line 1 dp `#33FFFFFF` |
| **Capture button** | dx -5, dy -5, blur 12, `#D9FFFFFF` | dx 7, dy 10, blur 20, `#5915181E` | ink gradient 160° `captureTop → ink` | top inner line 1 dp `#1FFFFFFF` |

### 4.2 Reference implementation

```kotlin
// ui/theme/Neu.kt
data class NeuShadow(val dx: Dp, val dy: Dp, val blur: Dp, val color: Color)

fun Modifier.neuRaised(
    shape: Shape,
    fill: Color,
    light: NeuShadow,
    dark: NeuShadow,
    border: Color? = MeridianColors.porcelainBorder,
): Modifier = this
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        drawIntoCanvas { canvas ->
            for (s in listOf(dark, light)) {
                val p = Paint()
                p.asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = fill.toArgb()
                    setShadowLayer(s.blur.toPx() / 2f, s.dx.toPx(), s.dy.toPx(), s.color.toArgb())
                }
                canvas.drawPath(path, p)
            }
        }
    }
    .clip(shape)
    .background(fill)
    .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)

fun Modifier.neuInset(
    shape: Shape,
    fill: Color = MeridianColors.well,
    dark: NeuShadow,   // positive dx/dy
    light: NeuShadow,  // negative dx/dy
): Modifier = this
    .clip(shape)
    .background(fill)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val inner = Path().apply { addOutline(outline) }
        val pad = 60.dp.toPx()
        val frame = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-pad, -pad, size.width + pad, size.height + pad))
            addPath(inner)
        }
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipPath(inner)
            for (s in listOf(dark, light)) {
                val p = Paint()
                p.asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.BLACK
                    setShadowLayer(s.blur.toPx() / 2f, s.dx.toPx(), s.dy.toPx(), s.color.toArgb())
                }
                canvas.drawPath(frame, p)
            }
            canvas.restore()
        }
    }
```

Provide ready-made presets so screens never repeat numbers:

```kotlin
object Neu {
    fun Modifier.card(shape: Shape = RoundedCornerShape(24.dp)) = neuRaised(shape, MeridianColors.porcelain,
        NeuShadow((-6).dp, (-6).dp, 14.dp, Color(0xE6FFFFFF)), NeuShadow(7.dp, 9.dp, 22.dp, Color(0x26544A3C)))
    fun Modifier.raisedSmall(shape: Shape = RoundedCornerShape(16.dp)) = neuRaised(shape, MeridianColors.porcelain,
        NeuShadow((-3).dp, (-3).dp, 8.dp, Color(0xF2FFFFFF)), NeuShadow(4.dp, 5.dp, 11.dp, Color(0x24544A3C)))
    fun Modifier.inset(shape: Shape = RoundedCornerShape(18.dp)) = neuInset(shape,
        dark = NeuShadow(3.dp, 3.dp, 7.dp, Color(0x2B544A3C)), light = NeuShadow((-3).dp, (-3).dp, 7.dp, Color(0xD9FFFFFF)))
    fun Modifier.insetSmall(shape: Shape) = neuInset(shape,
        dark = NeuShadow(2.dp, 2.dp, 5.dp, Color(0x2B544A3C)), light = NeuShadow((-2).dp, (-2).dp, 5.dp, Color(0xD9FFFFFF)))
    fun Modifier.darkHero(shape: Shape = RoundedCornerShape(30.dp)) = /* neuRaised with panel fill + dark-hero shadows */
    fun Modifier.darkWell(shape: Shape) = neuInset(shape, MeridianColors.panelWell,
        NeuShadow(2.dp, 2.dp, 5.dp, Color(0x73000000)), NeuShadow((-2).dp, (-2).dp, 5.dp, Color(0x0DFFFFFF)))
}
```

**Shadow clearance:** shadows extend up to ~20 dp outside a card. Parents must not clip them. Never put `clip()` or `verticalScroll` padding of 0 directly around raised cards. Keep the 18 dp page gutter so shadows render inside the screen.

### 4.3 Shapes

Use plain `RoundedCornerShape`. The softness comes from the shadows.

| Token | Radius | Use |
|---|---|---|
| `pill` | 9 dp | Status pills, layer chips, why-chips |
| `chip` | 11–13 dp | Small buttons, segment items (13), icon wells (11–13) |
| `control` | 16 dp | Back/more buttons, segmented track (17), secondary buttons (16) |
| `field` | 18 dp | Search fields |
| `button` | 20 dp | Primary/secondary 58 dp buttons; Review approve |
| `card` | 24 dp | Cards, grouped lists, results |
| `tabBar` | 26 dp | Tab bar capsule, timeline card |
| `hero` | 28–30 dp | Dark panels (brief 30, profile 28, privacy 28) |
| `sheet` | 34 dp top corners | Capture bottom sheet |

---

## 5. Spacing

4-dp grid. Exact values used:

| Token | Value | Where |
|---|---|---|
| Page gutter | **18 dp** left/right | All screens (tab bar uses 14 dp) |
| Top-level screen top padding | 24 dp | Tab screens |
| Pushed screen header top padding | 16 dp, side 14 dp | Back-button screens |
| Header inner padding | 4 dp | Eyebrow/title block |
| Gap between sections (tab screens) | 16 dp (Today, Calendar, Library), 18 dp (Tasks) | |
| Gap between sections (pushed screens) | 20 dp | Settings, Department, Privacy, AI |
| Section label to card | 9 dp (label padding `0 8 9`) | |
| Card inner padding | 16–17 dp | |
| Bottom padding above tab bar | **124 dp** | Every tab screen's scroll content |
| Bottom action bar | padding 16–18 top, 30 bottom (+ navigation-bar inset) | Review, Task |

Insets: use `WindowInsets.statusBars` on top of every screen (the 24/16 dp is added on top of it) and `WindowInsets.navigationBars` on the bottom (the tab bar's 20 dp bottom offset is added on top of it).

---

## 6. Icons

Stroke icons, Lucide style: 24×24 viewBox, `stroke-width 1.7` (2.2 for "+", 2.4 for small checks), round caps and joins, no fill. Default render size is 20 dp unless stated. Convert each path below to an `ImageVector` (Android Studio › Vector Asset, or hand-write `path { }` with `stroke = SolidColor(...)`, `strokeLineWidth = 1.7f`, `strokeLineCap = Round`, `strokeLineJoin = Round`). Tint with `LocalContentColor`.

| Name | SVG path data |
|---|---|
| search | `circle cx11 cy11 r7` + `M20 20l-3.5-3.5` |
| lock | `rect x5 y11 w14 h10 rx2` + `M8 11V7a4 4 0 0 1 8 0v4` |
| chevronRight | `m9 6 6 6-6 6` |
| chevronLeft | `m15 6-6 6 6 6` |
| sun (Today) | `circle 12,12 r4` + `M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4` |
| inbox (Review) | `M22 12h-6l-2 3h-4l-2-3H2` + `M5.5 5h13l3.5 7v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6z` |
| checkCircle (Tasks) | `circle 12,12 r9` + `m8.5 12 2.5 2.5 4.5-5` |
| calendar | `rect x3 y5 w18 h16 rx2` + `M3 10h18M8 3v4M16 3v4` |
| book (Library) | `M4 19V5a2 2 0 0 1 2-2h12v14H6a2 2 0 0 0-2 2Zm0 0a2 2 0 0 0 2 2h12` |
| plus | `M12 5v14M5 12h14` |
| sparkle (AI) | `M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z` |
| mic | `rect x9 y3 w6 h11 rx3` + `M5 11a7 7 0 0 0 14 0M12 18v3` |
| camera | `M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z` + `circle 12,13.5 r3.5` |
| phoneDevice (Screenshot) | `rect x7 y2 w10 h20 rx2` + `M11 18h2` |
| document | `M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z` + `M14 3v5h5M9 13h6M9 17h6` |
| chat (WhatsApp) | `M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.4A8 8 0 1 1 21 12Z` |
| phone (Call) | `M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1` |
| pen (Note) | `M4 20h4L19 9l-4-4L4 16z` + `m13.5 6.5 4 4` |
| shield | `M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z` (+ `m9 12 2 2 4-4` for shield-check) |
| clock | `circle 12,12 r9` + `M12 7v5l3 2` |
| people | `circle 9,8 r3.5` + `M2.5 20a6.5 6.5 0 0 1 13 0` + `M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6 6 0 0 1 3.5 6` |
| rank | `M4 6h16M4 12h10M4 18h6` |
| list | `M4 6h16M4 12h16M4 18h11` |
| pie (Keep free) | `circle 12,12 r9` + `M12 3v9h9` |
| help | `circle 12,12 r9` + `M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .8-1 1.5v.7M12 17h.01` |
| link | `M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1` + `M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1` |
| arrowUpRight | `M7 17 17 7M8 7h9v9` |
| check | `m6 12 4 4 8-8` |
| exclaim (Confirm) | `M12 8v5M12 16.5h.01` |
| more | three `circle r1.2` at (5,12) (12,12) (19,12) |
| close | `M6 6l12 12M18 6 6 18` |
| grip | `M8 9h8M8 15h8` |
| scan (OCR) | `M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M8 10h8M8 14h5` |

Do not substitute Material icons; their filled style breaks the look.

---

## 7. Components (build these in `ui/components/`)

Every press interaction uses the **press model**: on press, raised surfaces scale to `0.98` and swap their shadows for the *Inset small* spec. On release they animate back. Duration 120 ms, `FastOutSlowInEasing`. No ripple (`indication = null`).

### 7.1 `MeridianTabBar`
- Position: overlaid at the bottom; `left = right = 14 dp`, `bottom = 20 dp + navigationBars`.
- Row: capsule + capture button, gap **12 dp**, vertically centred.
- **Capsule:** Card shadow (§4.1), radius 26, padding 5, items gap 2, `weight(1f)`.
- **Tab item:** `weight(1f)`, height **54**, radius **19**, column, centred, gap 3. Icon 20 dp. Label style `tab`.
  - Inactive: colour `text3`, no background.
  - Selected: *Inset (selected tab)* background, label `ink`, icon `teal`.
- **Badge (Review):** absolute top 5, end 9; min width 17, height 17, horizontal padding 4, radius 9; fill `badgeRed`; text 10 sp bold white centred; shadow `0,2 blur 5 #66C2372E`. Show when the count is > 0; show "99+" above 99. Semantics: "Review, N waiting".
- **Capture button:** 64×64, radius 24, Capture button shadow and gradient (§4.1), "+" icon 26 dp, stroke 2.2, colour `mint`. Content description "Capture".
- Tab order: Today, Review, Tasks, Calendar, Library.
- The tab bar is hidden on Review, Task detail, Settings and every pushed screen.

### 7.2 Screen header (tab screens)
Row, bottom-aligned, `space-between`, padding `top 4, start 4, end 4`.
- Left column, gap 6: eyebrow (`label` style, `text3`), then title (`display`).
- Right (optional): avatar or action.

### 7.3 Pushed-screen header
Column, padding `top 16, start/end 14`, gap 14:
- **Back button:** 44×44, radius 16, *Raised small*, chevronLeft 20 dp `ink`, content description "Back to {previous}".
- Title: `display`, horizontal margin 8 dp.
- Optional description: 13.5 sp, line height 1.5, `text2`, margin 8.

Review and Task detail use a centred **nav row** instead: height 60, padding `top 16, horizontal 14`, back button (left), centre column (title `navTitle` + mono 10.5 `text3` subtitle, gap 2), right button (Raised small; "Later" text button with height 44, padding 0 14, 13.5 sp, weight 700, or a 44×44 "more" icon).

### 7.4 `MeridianCard` (grouped list container)
Card shadow, radius 24, `clip` inside for rows (shadow drawn outside the clip, per §4.2).

### 7.5 `ListRow` (Settings-style)
- Min height **56** (52 in Privacy-style rows and Task detail rows, 58–62 for two-line rows), horizontal padding 16, gap 12.
- Leading icon well: 34×34 (36 on the AI screen), radius 11 (12), *Inset small*, icon 17 dp, tint per row (see screens).
- Title `body` (14.5, 600) `weight(1f)`.
- Value: 13 sp, 500, `text2`.
- Chevron 16 dp `chevron`.
- Divider between rows: 1 dp `hairline`, full width (no inset).

### 7.6 `SectionLabel`
`label` style text, padding `start 8, end 8, bottom 9`. Optional trailing mono 11 count (`01`, `02` …) in `text3` on the right (Tasks).

### 7.7 Buttons

| Variant | Height | Radius | Fill | Text | Shadow |
|---|---|---|---|---|---|
| **Primary (large)** | 58 | 20 | teal gradient 170° `tealBtnTop → tealBtnBottom` | 16 sp, 800, white; optional leading check 18 dp stroke 2.4, gap 8 | Primary button |
| **Primary (medium)** | 50 | 17 | same | 14 sp, 800 | Primary button |
| **Primary (small)** | 40 | 13 | same | 13 sp, 800, padding 0 14 | `0,5 blur 12 #4D0E6B63` + top inner line |
| **Secondary** | 48 (row of three in Review), 58 (Task bar), 50 (Privacy) | 16 / 20 / 17 | `porcelain` | 14.5–15 sp, 700, `ink` | Raised small |
| **Destructive** | as secondary | as secondary | `porcelain` | colour `#FFA3221B` | Raised small |
| **AI action** ("Plan my day") | 42 | 15 | `aiSoft` + 1.5 dp dashed `aiDash` | 13 sp, 800, `ai`, leading sparkle 15 dp, gap 6, padding 0 14 | `4,5 blur 11 #1F463FBF` |
| **Pressed state** | — | — | fill darkens to the bottom gradient colour | — | inset `3,4 blur 8 #59000000` |
| **Disabled** | — | — | `well` with *Inset* | `disabledText` | none |

The **dashed border** is drawn as `drawRoundRect(color, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))`. The 1 dp variant uses dash 4, gap 3.

### 7.8 `StatusPill`
Height 24 (26 on Task detail), horizontal padding 9 (10), radius 9 (10), `pill` style. Colours from §2.6. Labels: Overdue, Needs review, Due in N days, Clarify, Blocked, Delegated, Done, Found, Confirm, Not stated.

### 7.9 `LayerChip` (provenance trail)
Height 24, padding 0 9, radius 9, 11.5 sp 600, gap 5.
- Source: fill `paperChip`, text `paperLabel` ("Letter · DME", "Call · 2:14 · Tamil").
- AI: fill `aiSoft`, text `ai`, **1 dp dashed `aiDash`** ("AI · 1 field to confirm").
- Approved: fill `tealSoft`, text `teal` ("Approved by you · 7 Oct").
- Between chips: chevronRight 12 dp `chevron`. The trail wraps; row gap 6.

### 7.10 `SegmentedControl` (sunken)
Track: *Inset*, radius 17, padding 4, gap 4. Item: `weight(1f)`, height 36, radius 13, 13 sp (12.5 for 4 items) 700, `text3`. Selected item: fill `porcelain`, shadow `-2,-2 blur 5 #E6FFFFFF` + `3,4 blur 8 #29544A3C`, text `ink`. Animate the selected thumb with `animateDpAsState` on its x offset, 200 ms.

### 7.11 `SearchField` (sunken)
Height 50–52, radius 18, *Inset*, padding start 16 end 8 (12 if no trailing), gap 10, search icon 20 `text3` (`teal` when focused), text 15 sp. Focused: add a focus ring outside the shape, 2 dp stroke `#590E6B63` (teal 35%) offset 2 dp. Trailing clear button: 34×34, radius 12, *Raised small*, close icon 15 dp. On Today the field is a non-editable button that opens Library; it shows a trailing language chip ("தமிழ் · EN": *Raised small*, radius 8, padding 3×7, mono 10.5 `ink2`).

### 7.12 `TexturedSwitch` (signature component)
- Track 58×34, radius 17.
  - **Off:** fill `trackOff`, inner shadows `3,3 blur 6 #40544A3C` and `-2,-2 blur 5 #E6FFFFFF`.
  - **On:** horizontal gradient `tealSwitchA → tealSwitchB`, inner shadows `3,3 blur 6 #59003C34` and `-2,-2 blur 5 #40FFFFFF`.
- **Knob:** 28 dp circle, inset 3 dp from the track edge; travel 24 dp (`x = 3` off, `x = 27` on); animate 180 ms `FastOutSlowInEasing`. Drawing order:
  1. Drop shadow `0,2 blur 5 #47281F1A`.
  2. Radial gradient centred at (35%, 30%), radius = knob size: `#FFFFFFFF → #FFE9E5DF`.
  3. **Texture:** clipped to the circle, draw diagonal lines at +45° and −45°, stroke 1.5 dp, spacing 3.5 dp, colour `#12544A3C` (umber 7%).
  4. 1 dp border `#FFD2CCC2`.
  5. Top highlight: 1 dp arc along the top edge, white.
- Semantics: `Modifier.toggleable(role = Role.Switch)`, with the row label as the content description. Disabled: alpha 0.45 and no knob shadow.

### 7.13 `CheckWell` (task completion)
44×44 touch target, centred 24 dp circle.
- Open: *Inset small* (checkbox values from §4.1).
- Overdue open: dark inner shadow colour becomes `#4DA3221B`.
- Done: 24 dp circle, teal gradient 160° `tealBright → teal`, white check 14 dp stroke 2.6, shadow `0,3 blur 7 #590E6B63`.
- Semantics: "{title}, done / not done", role Checkbox.

### 7.14 `IconWell`
Square, radius ≈ 0.33 × size, *Inset small*, icon at 0.47 × size. Sizes: 34 (settings rows), 36 (AI rows, Library results), 38 (Review nodes), 40 (Capture tiles).

### 7.15 `EvidenceText`
Source text in `source` / `sourceSmall` style, colour `paperText`. Supporting phrases get a `SpanStyle(background = mark, color = markText)`. Because spans can't have radius or padding, draw the marks as rounded rects behind the text (radius 4, horizontal padding 3) via `onTextLayout` → `getPathForRange`, then `drawBehind`. The excerpt container (Library) is `paper` fill, radius 14, padding 11×13.

### 7.16 `DarkPanel`
Gradient 160° `panelTop → panelBottom` (the brief uses solid `panelBrief`). Dark hero shadow. Inner tiles use *Dark well*, radius 15–16.

### 7.17 `ProgressRing` (AI screen)
104 dp disc. Outer ring: draw a full circle in `trackOff`, then an arc in `teal` from −90° with sweep `360 × fraction`, stroke width 11 dp, butt cap (the design is a filled conic; a stroke of 11 dp reproduces it). Disc shadow `4,6 blur 14 #2E544A3C` + `-4,-4 blur 10 #E6FFFFFF`. Inner disc 82 dp, *Inset*, fill `ground`, centred text: percentage 22 sp 800 -0.03em, then "indexed" mono 9.5 `text3`. Semantics: "Search index N percent complete".

### 7.18 `LinearMeter`
Track: *Inset*, height 6–8, radius = height / 2. Fill: horizontal gradient (Review progress `tealBright → teal`; Capture processing `aiDashEvent → ai` plus shadow `0,2 blur 6 #59463FBF`; Settings setup `tealBright2 → mint` on a dark-well track).

### 7.19 Hatching (fixed clinical time)
`drawBehind`: fill the shape, then clip and draw parallel lines at 135°. Light timeline: alternating 6 dp `#E2DED7` and 6 dp `#EAE7E1`. Dark ribbon: 3 dp `#26FFFFFF` and 4 dp `#0AFFFFFF`.

### 7.20 Bottom sheet (Capture)
Scrim: the underlying screen at 28% opacity on `#3A3E45`. Sheet: top offset 70 dp, fill `ground`, top radius 34, shadow `0,-10 blur 30 #40000000`, padding `10 18 28`, gap 18. Handle: 42×6, radius 3, *Inset*, centred.

### 7.21 Snackbars (status / undo)
Keep the existing behaviour (8 s status, 6 s undo). Restyle: fill dark panel gradient, radius 20, padding 14×16, text 13.5 sp `onPanel`, action 13.5 sp 800 `mint`, Dark hero shadow. Position: above the tab bar (bottom 96 dp + nav inset), or above the action bar on Review and Task detail.

### 7.22 Dialogs and action sheets
Card shadow, radius 28, fill `porcelain`, padding 22, title `navTitle` serif. Buttons are Secondary + Primary (medium) in a row, gap 10. Destructive confirm uses a Primary-sized button with fill `#FFA3221B` and a shade colour of the same red at 35%. Action sheets: a stack of Secondary buttons (height 54, radius 18) with a Cancel button below, separated by 10 dp.

---

## 8. Screen: Today (`Main.dc.html`)

Background `ground`. Scroll column, padding `top 24 (+statusBars), horizontal 18, bottom 124`, gap **16**.

1. **Header:** eyebrow "SUNDAY · 11 OCTOBER" (from the system date, uppercased, `label`, `text3`); title "Today" (`display`). Right: avatar button 46×46, radius 23, *Raised small*, text "HOD" 12.5 sp 800 `ink` → opens Settings.
2. **Search shortcut:** `SearchField` (non-editable, opens Library), height 50, placeholder 14.5 sp `text3` "Search letters, people, GO numbers", trailing language chip.
3. **On-device line:** row, padding 0 6, gap 8. Dot 8 dp `tealBright` with a 3 dp ring `#2E17A493` (amber `#8E4504` dot when AI is degraded). Text 12 sp 500 `text2`: "On this phone · {N} items indexed · encrypted".
4. **Secretary brief** (`DarkPanel`, solid `panelBrief`, radius 30, padding `22 18 18`, gap 16):
   - Row: label "SECRETARY BRIEF" (10.5, 700, 0.12em, colour `aiOnDark`) with a leading 24×24 square, radius 8, fill `#24BDB8F7`, sparkle 14 dp; right: mono 11 `onPanel4` current time.
   - Sentence: `brief` style, white; times and deadlines inside it coloured `mint`. Generated from the same data as Must act (keep the existing deterministic summary; no new AI).
   - **Day ribbon:** height 34, radius 12, *Dark well*. Spans 8:00–18:00 across the full width. Blocks sit 4 dp from top and bottom, radius 9:
     - fixed clinical blocks: hatch (dark variant) with label "OPD · fixed" 10.5 sp 700 `#C8CDD6`, padding-left 9;
     - proposed blocks: 1.5 dp dashed `aiOnDarkDash`;
     - meetings: vertical gradient `#3CC2B1 → tealBright`, shadow `0,2 blur 8 #7317A493`;
     - now line: 2 dp wide, extending 3 dp beyond top and bottom, `nowRed`, glow `0 0 8 #B3FF7A6B`.
   - Hour labels below, gap 8: mono 10 `onPanel3`, `8 10 12 2 4 6`, space-between, padding 0 2.
   - **Stats:** 3-column grid, gap 8. Tile: *Dark well*, radius 16, padding 12, gap 2; number `stat` white; label 11 sp `onPanel2` (Must act, To review, Waiting). Taps go to Review, Review, Tasks › Delegated.
   - Footnote 11 sp `onPanel3`: "From {t} tasks, {l} letters, {c} call(s) · AI approved nothing".
5. **Section header:** row, padding `top 8, horizontal 4`, baseline-aligned: "Must act" (`section`), and a "See all" link (13.5 sp 700 `teal`) → Tasks.
6. **Must-act cards** (max 4): Card shadow, radius 24, padding 17, gap 11, full-width clickable.
   - Row (gap 8): `StatusPill` + meta 12 sp `text2`.
   - Title `cardTitle`.
   - `LayerChip` trail.
   - Task → Task detail. Source → Review at that item.
   - Empty state: one card with the serif 21 title "Nothing needs you right now" and 13.5 sp `text2` body, centred, padding 28.
7. `MeridianTabBar`, Today selected.

## 9. Screen: Review (`Review.dc.html`)

Full screen, no tab bar. Background `ground`.

1. **Nav row** (§7.3): back → Today; centre "Review" + "1 of 5 · Letter"; right "Later" (skips to the next item).
2. **Progress meter:** margin `14 22 18`, height 6, *Inset*, fill = position / total (`tealBright → teal`).
3. **Body** (scroll), horizontal padding 18. **Rail:** a vertical 2 dp line at x = 36 dp from the screen edge (the centre of the node icons), from the first node to the last, vertical gradient `railSource → aiDash → #7FC9BE`.
4. **01 · From the source** (section, padding-bottom 18, gap 10):
   - Head row (gap 11): node 38×38, radius 13, *Raised small*, document icon 18 `paperLabel`; column with label "01 · FROM THE SOURCE" (`paperLabel`) and sender 12.5 sp `text2`.
   - Card: start margin 49 dp; fill `paper`, 1 dp `paperBorder`, radius 20, padding 15×16, gap 11, shadow `-4,-4 blur 10 #E6FFFFFF` + `5,7 blur 16 #246E5A32`.
     - Mono row 10.5 `paperLabel`: reference left ("Rc. No. 2291/E2/2026"), date and page/paragraph right ("9 Oct · p.1 ¶3"; audio shows a timestamp instead).
     - `EvidenceText` (15.5 serif) with marks.
     - Buttons: "Open original ↗" (height 34, radius 12, fill `paperBtn`, 12.5 sp 700 `paperBtnText`, arrow 13 dp) and "Full letter" (text only, 600).
5. **02 · AI reading — a suggestion:**
   - Node: 38×38, radius 13, fill `aiSoft`, 1.5 dp dashed `aiDash`, sparkle 18 `ai`. Label "02 · AI READING — A SUGGESTION" (`ai`); sub "On this phone · {k} of {n} details found word for word".
   - Card: start margin 49; fill `aiCard`, 1.5 dp dashed `aiDashCard`, radius 20, shadow `5,7 blur 16 #14463FBF`, clip.
     - Top block (padding `15 16 13`, gap 7): "Suggested action · 1 of 1" 11.5 sp 600 `text3`; action text `aiAction` (editable when Edit is pressed: the same text style inside an inset field); "Why: …" 12 sp `text2`.
     - **Fields** on a white background, top divider `aiDivider`. Each row: min height 48, padding 0 15, gap 10; key (width 60) 12.5 sp `text3`; value 14.5 sp 700 (`ink`, or `#8E4504` when Confirm); state pill height 24, radius 9, 11 sp 800 with a 13 dp icon:
       - **Found:** found colours + check icon.
       - **Confirm:** confirm colours + exclaim icon.
       - **Not stated:** neutral colours, no icon, value text "Not stated" in `text4`.
     - Related row: padding 11×16, top divider, link icon 14 `ai`, 12 sp `text2` text, right 10.5 sp 700 `ai` reason ("Same meaning").
6. **03 · Your decision:** node 38×38 with the teal gradient and check 18 white stroke 2.2, shadow `0,6 blur 14 #590E6B63`; label "03 · YOUR DECISION" (`teal`); sub "Nothing becomes a task until you approve".
7. **Action bar** (fixed bottom, padding `16 18 30` + nav inset, gap 12):
   - **Primary (large)** full width: "Approve as task" with check icon. While editing it reads "Approve edited action".
   - Row of three **Secondary** buttons (height 48, radius 16, gap 10): Edit, Delegate, Reject (Reject text `#A3221B`). While editing, Edit reads "Cancel edit".
   - Delegate and Reject open action sheets (§7.22) with the existing options.

## 10. Screen: Capture sheet (`Capture.dc.html`)

Bottom sheet (§7.20) over the current screen.

1. Handle.
2. Header row: column (gap 3) with eyebrow "ADD TO YOUR DESK" and title "Capture" (`sheetTitle`); right "Cancel" (*Raised small*, height 44, padding 0 16, radius 16, 13.5 sp 700).
3. **Voice card:** a button with the dark gradient, radius 26, padding 16, gap 14, Dark hero shadow.
   - Mic disc 60×60, gradient 160° `tealBright3 → teal`, ring `0 0 0 7 #1F7FDCCD`, inner top line, mic icon 26 white.
   - Text: "Speak a note" 17 sp 800 white; "Tamil or English · transcribed here" 12 sp `onPanel2`.
   - Waveform: 6 bars, 3 dp wide, radius 2, colour `mint`, heights 10/20/28/16/22/8, gap 3.
   - **Recording state:** the disc fill becomes `#C2372E` with a stop icon, the title becomes "Recording · m:ss", the sub becomes "Tap to stop and transcribe", and the bars animate (each bar's height loops between 40% and 100%, staggered 80 ms).
   - **Locked state:** lock icon in a grey disc; sub "Voice notes are off in Privacy".
4. **Tiles:** 3-column grid, gap 12. Tile height 108, padding 13, radius 22, Card shadow, column space-between. Icon well 40×40 (radius 13, *Inset*). Name 14 sp 700, sub 11 sp 500 `text3`.

   | Tile | Icon tint | Sub |
   |---|---|---|
   | Camera | `ink2` | Letter, notice |
   | Screenshot | `ai` | From gallery |
   | Document | `paperLabel` | PDF, Word |
   | WhatsApp | `#13653A` | Chat export |
   | Call | `#8E4504` | Recording |
   | Note | `#8E4504` | Type or paste |

   **Locked tile:** the whole tile is *Inset* instead of raised. The icon well has no fill and a 1.5 dp dashed `#BDB6AC` border with a lock icon `text4`. The name is `text2` and the sub is "Off in Privacy". A tap opens Privacy.
5. **Processing:** label "PROCESSING ON THIS PHONE" (padding 0 4); a Card (radius 22, padding 15×16, gap 10) per item: title 14.5 sp 700 + mono 11 duration on the right; `LinearMeter` (height 8, indigo); status 12 sp `text2`.
6. **Footer** (pinned to the bottom of the sheet with `weight` spacer, padding 0 4): shield icon 15 `teal`, text 12 sp line height 1.5 `text2`, with a "Privacy" link (700 teal).

## 11. Screen: Tasks (`Tasks.dc.html`)

Padding `24 18 124`, gap 18.
1. Header: eyebrow "{open} OPEN · {overdue} OVERDUE", title "Tasks".
2. `SegmentedControl`: "Mine · N", "Delegated · N", "Done".
3. Groups in order: Overdue (label colour `#A3221B`), Today, This week, Later, No date, Done today. Each group: `SectionLabel` with a two-digit mono count, then a Card (radius 24).
   - Row: padding `9 14 9 6`, gap 6; `CheckWell`; column with title (`rowTitle`) and meta (12 sp `text3`, margin-top 3; overdue meta `#A3221B`); optional pill (Clarify, Blocked, Delegated); chevron 16.
   - Divider `hairline` between rows.
   - The **Done** group's container is *Inset* instead of raised; titles `text4` with line-through.
4. Empty state per segment (keep the existing copy), styled as in §8.6.
5. Tab bar, Tasks selected.

## 12. Screen: Task detail (`Task.dc.html`)

No tab bar.
1. Nav row: back → Tasks; centre mono 10.5 `text3` "Task · {matter}"; right "more" button (44×44, *Raised small*) → action sheet with Delete (destructive).
2. Body padding `20 18 0`, gap 18:
   - Pills row (gap 6): state pill + due pill (height 26, radius 10).
   - Title `taskTitle` (padding 0 4).
   - **Owner card:** Card, radius 22, padding 14, gap 12. Avatar 46 circle with a gradient (the delegate colour), initials 14 sp 800 white, shadow `0,4 blur 10 #4D1F4E96`. Name 15 sp 800; role 12 sp `text3`. "Change" button: *Inset*, height 36, radius 12, 12.5 sp 700. When owned by me, show the "HOD" avatar in a teal gradient and "You".
   - **Next step:** fill `tealSoft`, radius 22, padding 16, gap 11, inner shadows `2,2 blur 5 #1A0E6B63` and `-2,-2 blur 5 #99FFFFFF`. Label "NEXT STEP" (`teal`); text `bodyMedium`; buttons: Primary (small) "Copy follow-up draft" and a *Raised small* button (height 40, radius 13, 13 sp 800 teal) "Remind me Mon". The context buttons stay as before (Clear blocker / Mark answered / Pick a time).
   - **Metadata card:** rows (min height 52, padding 0 16, gap 12): key 14.5 sp `text2`, value 14.5 sp 700, chevron. Rows: Due, Matter, Blocker ("Add" in teal when empty), Clarification.
   - **Provenance:** label "PROVENANCE"; Card radius 22, padding 16, gap 14. Each entry is a row (gap 11) with a 12 dp dot (margin-top 3):
     - Source dot `railSource`; label 11.5 sp 700 `paperLabel`; `sourceSmall` quote in curly quotes.
     - AI dot: 2 dp dashed `ai` ring; label `ai`; detail 13 sp `text2`.
     - Approved dot: `teal` filled with a 3 dp ring `#2E0E6B63`; label `teal`; mono 11 timestamp.
   - **History** (existing audit list) follows in the same style, as rows with 8 dp `chevron` dots and the newest dot `teal`.
3. **Action bar:** padding `18 18 30`, gap 12. Secondary "Reschedule" (`weight 1`, height 58, radius 20) and Primary "Mark done" (`weight 1.5`). When done: "Reopen" as Secondary.

## 13. Screen: Calendar (`Calendar.dc.html`)

Padding `24 18 124`, gap 16.
1. Header: eyebrow "WEEK {n} · {year}", title = month name; right: AI action button "Plan my day".
2. **Week strip:** *Inset*, radius 20, padding 5, gap 3. 7 day buttons, each `weight 1`, padding 8 vertical, radius 15, column gap 4: letter 10.5 sp 700 `text3`; number 16 sp 700. **Selected:** dark gradient (`captureTop → ink`), shadow `3,5 blur 10 #4D15181E`, letter `onPanel4`, number white. A day with a deadline shows a 5 dp `badgeRed` dot under the number (semantics "deadline"). Swiping the strip moves by a week.
3. Info row (padding 0 6): "{Weekday} · {free} free · {slack}% kept clear" 12.5 sp 500 `text2`; right "Protect time" 12.5 sp 800 `teal`.
4. **Timeline card:** Card, radius 26, padding `24 14 10 6`. Inner height = (endHour − startHour) × **52 dp** (8–18 → 520).
   - Hour rows: label column 40 dp, right-aligned, padding-end 8, mono 10 `text4`, shifted up 7 dp; a 1 dp dashed line `#29544A3C` across the rest.
   - Blocks: x starts at 46 dp, `top = (minutes − 480) / 60 × 52 + 2`, `height = duration × 52 − 4`, radius 14, padding 7×10, gap 1.
     - **Fixed:** *Inset* + light hatching; "OPD · fixed" 13 sp 800 `ink2`; "Never booked over" 11.5 sp `text3`.
     - **Proposed:** `aiProposal` + 1.5 dp dashed `aiDashEvent`; caption "PROPOSED · 1:30–2:30" 10 sp 800 0.08em `ai`; title 13 sp 700 `aiDeep`.
     - **Event:** gradient 160° `tealBright2 → teal`, shadow `0,6 blur 14 #4D0E6B63`; title 13 sp 800 white; sub 11 sp `tealOnTeal`.
     - **Clash proposal:** clash colours, caption "CLASH · PROPOSED".
     - Overlapping items split the width into lanes (keep the existing lane algorithm); lane gap 4 dp.
   - **Now line** (today only): a 9 dp red dot `#D93B2F` at x = 40 plus a 2 dp line to the right edge.
5. **Proposal bar** (only when proposals exist): overlaid, left/right 14, bottom 100 + nav inset. Dark gradient, radius 24, padding `14 14 14 18`, gap 10, shadow `8,14 blur 28 #5915181E`. Text: "{n} proposed · {c} clashes" 14 sp 800 white; "Nothing written until you add" 11.5 sp `onPanel2`. Buttons: "Discard" (height 42, radius 14, fill `#14FFFFFF`, 13 sp 700 white) and "Add {n−c}" (height 42, radius 14, gradient 160° `mintLight → tealBright2`, 13 sp 800 `tealInk`, shadow `0,4 blur 12 #731DA392`).
6. Tab bar, Calendar selected.

## 14. Screen: Library (`Library.dc.html`)

Padding `24 18 124`, gap 16.
1. Header: eyebrow "{N} ITEMS · ON THIS PHONE"; title "Library"; right "Select" (*Raised small*, height 40, radius 14, 13 sp 700). In select mode the title shows "{n} selected", the button reads "Done", and a row appears with Secondary "Select all" and Destructive "Delete {n}".
2. `SearchField` (editable, focus ring when focused, clear button when non-empty). Placeholder "Search in Tamil or English, GO numbers…".
3. `SegmentedControl` (4 items): All, Letters, WhatsApp, Calls.
4. Status line (padding 0 6) 12 sp 500 `text2`: "{n} results · meaning and exact words · Tamil + English" (keyword-only mode: "{n} results · exact words").
5. **Result cards:** Card, radius 24, padding 16, gap 10.
   - Head row (gap 11): `IconWell` 36 (tint by source type: Letter `paperLabel`, WhatsApp `#13653A`, Call `#8E4504`, Screenshot `ai`, Note `#8E4504`); column with title 14.5 sp 800 and mono 10.5 `text3` meta (reference · date; calls add "at mm:ss").
   - Excerpt: `EvidenceText` (`sourceSmall`) inside the paper box (fill `paper`, radius 14, padding 11×13). Query terms are marked. Tamil text uses Noto Sans Tamil with line height 1.6.
   - Why-chips (gap 6, wrap): "Exact words" (neutral), "Same meaning" (AI colours), "Same meaning · Tamil", "Document number" (neutral).
   - In select mode the icon well is replaced by a `CheckWell`, and a selected card's fill becomes `tealSoft`.
6. Tab bar, Library selected.

## 15. Screen: Settings (`Settings.dc.html`)

Pushed screen header (back → Today), title "Settings". Body padding 0 18, gap 20, bottom 30.
1. **Profile card:** dark gradient, radius 28, padding 18, gap 16, Dark hero shadow. Row (gap 13): disc 52 with the teal gradient, "HOD" 13 sp 800; column with "Department of Psychiatry" 16.5 sp 800 and the institution name 12.5 sp `onPanel2`; chevron `onPanel3`. Setup meter: row with "Setup {k} of 4 · {next step}" 12 sp `#E7EAF0` and mono `onPanel3` percentage; meter height 8 (dark-well track, gradient `tealBright2 → mint`). Hide the meter when 4 of 4.
2. Groups (label + Card with `ListRow`s). Icon-well tints:
   - **Work:** Fixed clinical time (clock, `ink2`, value "OPD +2"), Team (people, `#1F4E96`, "6 people"), Who outranks whom (rank, `ai`).
   - **Calendar:** Write events to (calendar, `#A3221B`, calendar name), Working hours (clock, `#13653A`), Keep free (pie, `ink2`, "30%").
   - **Privacy and security:** Privacy and data (lock, `teal`, "On this phone"), Clinical vault (shield, `ink`, "4 · locked"), Activity log (list, `#1F4E96`).
   - **Intelligence:** On-device AI (sparkle, `ai`, status dot + "Working" 700 `#13653A`), Follow-ups (chat, `#8E4504`, "2 drafts"), Secretary and Secretary Control Centre and Evaluation Lab if present in the app (sparkle, `ai`), How to import (help, `ink2`).
3. Footer: mono 10.5, line height 1.7, centred, `text3`: "Psychiatry HOD Desk {version}" / "Nothing leaves this phone."

## 16. Screen: Department (`Department.dc.html`)

Pushed header (back → Settings), title "Department", description "Planning works around these blocks; delegation suggests these people. Stored only on this phone."
- **Fixed clinical time** card. Rows: min height 58, padding 9×16. Column (gap 6): name 15 sp 700; **week chips** row (gap 4): 7 squares 17×17, radius 6, 9 sp 800. *On* chips use the dark gradient with white text and shadow `1,2 blur 3 #4D15181E`; *off* chips use `well` with inner shadow `1,1 blur 2 #33544A3C` and text `disabledText`. Right: time chip mono 11.5 `ink2`, *Inset*, padding 6×9, radius 10. Last row: "+ Add fixed block" (plus 17 dp stroke 2.2, 14 sp 800 `teal`, gap 8).
- **Team** card: avatar 40 circle with a gradient (teal / blue / bronze, cycling), initials 12.5 sp 800 white, shadow `0,4 blur 9 #3315181E`; name 15 sp 700; role · topics 12 sp `text3`. Last row: "+ Add team member".
- **Who outranks whom:** rows min height 52; mono 11 `text4` "01"–"0n" (width 22); name 14.5 sp 600; drag grip 36×36, radius 12, *Inset*, grip icon 16 `text4`. Keep the existing reorder logic and expose up/down as accessibility actions.
- **Venues** card (not in the mock; follow the Team pattern): name + travel-time chip.
- Edit dialogs follow §7.22.

## 17. Screen: Privacy and data (`Privacy.dc.html`)

Pushed header, title "Privacy".
1. **Hero:** dark gradient, radius 28, padding 18, gap 14. Row: 46×46 radius 15 teal-gradient tile with shield-check 22 white; "Everything stays on this phone" in serif 21 500 white. Three chips (3-column grid, gap 8): *Dark well*, radius 15, padding 11×10, 11.5 sp 600 `onPanelChip`: "Encrypted storage", "No cloud AI", "Patients never searchable".
2. **Imports** card: rows (min height 58, padding 6×16), label 14.5 sp 600 (master row 800) and `TexturedSwitch`. Rows: Allow sensitive imports; Calls and voice notes; WhatsApp and messages; Screenshots and camera; Documents and Gmail. When the master switch is off, the other switches are disabled. Footnote (padding `9 8 0`, 12 sp line height 1.5 `text2`): "A source that is off can’t be imported; its Capture tile shows a lock."
3. **Protection** card: Hide phone numbers and emails (switch). "Patient details out of search" with an *Inset* badge (height 30, radius 11, padding 0 10, lock 13 + "Always on" 11.5 sp 800 `teal`).
4. **Keep items for:** `SegmentedControl` 30 days / 6 months / 1 year / Custom. Footnote "Nothing is deleted automatically. Approved tasks keep their history."
5. Two buttons in a row (gap 10, height 50, radius 17, *Raised small*, 13.5 sp 800): "Activity log" and "Delete all data…" (`#A3221B`). Delete keeps the existing type-DELETE confirmation dialog.

## 18. Screen: On-device AI (`AI.dc.html`)

Pushed header, title "On-device AI".
1. **Health card:** Card, radius 28, padding 18, row gap 18: `ProgressRing` + column (gap 6): status line (8 dp dot + "Everything working" 12 sp 800 `#13653A`; degraded: amber dot + "Needs attention" `#8E4504`), headline 15 sp 800 "{indexed} of {total} items searchable", detail 12 sp `text2` "{clinical} clinical items kept out on purpose. Last check {time}."
2. **What runs here** card: rows min height 62, padding 9×16; icon well 36; title 14.5 sp 700; sub 12 sp `text3`; status "Ready" (dot style) or a button. Rows:
   - Smart search (search, `ai`);
   - Speech to text (mic, `#8E4504`);
   - Read text from images (scan, `ink2`);
   - Deeper understanding: icon well uses the dark gradient with a sparkle in `aiOnDark`. Trailing *Raised small* "Get" button (height 36, radius 13, 13 sp 800 `ai`), or "{p}%" mono while downloading, or "Installed" in Ready style.
   - Footnote: "AI only suggests. Nothing becomes a task or event until you approve it."
3. **Maintenance:** 2-column grid, gap 12, *Raised small* buttons (height 54, radius 18, 13 sp 800 `teal`): "Run self-check", "Rebuild index". Below them (margin-top 12): a full-width *Inset* row button (height 48, radius 17, padding 0 16, 13.5 sp 700 `ink2`, chevron) "Technical details", which expands the existing key/value list in an *Inset* box (mono 11, rows 32 dp).

## 19. Screens not in the mock

Apply the same patterns:
- **Activity log:** pushed header; a primary-medium "Export for review" button; day groups (SectionLabel = date) in Cards; rows with action text 14.5 sp 600 and mono 11 meta.
- **Clinical vault:** a dark hero explaining the boundary, then a Card list. Rows expand inline to show the evidence in the paper style.
- **How to import:** one Card per source (icon well + 15 sp 800 title + 13 sp `text2` body).
- **Secretary, Control Centre, Follow-ups, Matter file, Evaluation Lab:** Cards plus layer chips. AI proposals use the dashed AI style; confirm buttons are Primary.
- **Source detail sheet** (Library tap): bottom sheet (§7.20); title `navTitle`; a SegmentedControl with Actions / Evidence / Original; Evidence uses the paper card.

## 20. Motion

| Interaction | Spec |
|---|---|
| Press (any raised element) | scale 0.98 and shadow swap to inset-small, 120 ms `FastOutSlowInEasing` |
| Tab change | crossfade content 180 ms; selected-tab inset fades 150 ms |
| Segmented / switch thumb | 180–200 ms `FastOutSlowInEasing` |
| Push screen | slide in from the right 24 dp + fade, 220 ms; back reverses |
| Bottom sheet | slide up with spring (`dampingRatio 0.85`, `stiffness 380`); scrim fade 200 ms |
| Approve | button shows a check morph (scale 0.9 → 1, 160 ms); the card leaves with fade + 16 dp upward slide, 200 ms |
| Recording bars | infinite height loop, 600 ms per cycle, staggered |
| Reduced motion | when the system animator scale is 0, skip all of the above |

## 21. Dark theme (derived, optional, ship only if asked)

`ground #1B1E23`, `porcelain #22262C`, `well #191C21`, raised highlight `#0AFFFFFF` (-5,-5 blur 12), raised shade `#80000000` (6,8 blur 18), inset dark `#99000000`, inset light `#0FFFFFFF`, `ink #EEF0F3`, `text2 #B8BFCB`, `text3 #9AA3B2`, `paper #2A251C` with `paperText #EDE3CF`, `aiCard #22213A`, `tealSoft #173733`. Status backgrounds use the text colour at 18% alpha.

## 22. Implementation order (do these in sequence and compile after each)

1. Add the font files, `MeridianColors`, `MeridianType`, shapes and spacing tokens. Wire them into the app theme (`HodTheme` or equivalent). Remove Material colour usage from screens.
2. Implement `Neu.kt` (§4.2) and verify one raised card and one inset field on a test screen.
3. Build the components in §7, in this order: icons → `MeridianTabBar` → buttons → pills and chips → `SegmentedControl` → `SearchField` → `TexturedSwitch` → `CheckWell` → `IconWell` → `ListRow` → `EvidenceText` → `DarkPanel` → `ProgressRing` → meters → hatching → sheet → snackbar → dialogs.
4. Rebuild the screens in this order: Today → Review → Capture → Tasks → Task detail → Calendar → Library → Settings → Department → Privacy → On-device AI → the remaining screens (§19).
5. Run the acceptance checklist (§23) on a 390-dp-wide emulator (Pixel 7) and on a 360-dp device.

## 23. Acceptance checklist

- [ ] Every value in §2–§7 is a named token; no screen hard-codes a colour or radius.
- [ ] Raised cards show **both** shadows (white top-left and umber bottom-right), and none are clipped at screen edges.
- [ ] Selected tab and selected segment look **sunken**; the selected tab icon is teal.
- [ ] AI-derived content always has a **dashed indigo** border, and approved items are **solid teal**. No AI suggestion uses teal.
- [ ] Source quotes are serif on paper with amber marks, and the reference number is in mono.
- [ ] Switch knob shows the diagonal texture and slides 24 dp.
- [ ] Touch targets ≥ 44 dp; TalkBack reads every tab, switch, checkbox, icon button and pill.
- [ ] Text contrast ≥ 4.5:1 (all tokens above already pass on their intended backgrounds).
- [ ] Long Tamil and English strings wrap without clipping at 360 dp width and with font scale 1.3.
- [ ] Bottom actions and the tab bar sit above the gesture or 3-button navigation bar.
- [ ] No crash when switching tabs quickly or opening any card.
- [ ] Unit tests still pass (`./gradlew testDebugUnitTest`).

## 24. Prompt to give Codex

> Implement the Meridian UI exactly as specified in `docs/design/MERIDIAN_UI_SPEC.md`, using `docs/design/meridian/*.dc.html` as the visual source of truth for every number. Work in the order of Section 22 and compile after each step. Do not change repositories, ViewModels, DAOs, import, consent, redaction, extraction or calendar-write logic. Presentation layer only. Replace Material styling on all screens with the Meridian tokens and the `Neu.kt` shadow modifiers. When finished, run the Section 23 checklist and report any item that is not met.
