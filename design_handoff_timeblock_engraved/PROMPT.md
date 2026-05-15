# Redesign `TimeBlockCard` with the "Engraved" treatment

You are updating an existing React + Tailwind app. The component
`frontend/src/components/TimeBlockCard.tsx` renders a single block on the
day timeline. **Only the paint changes.** All behavior — drag-to-move, resize,
swipe-to-complete, selection wiring, the inspector rail, light/dark mode —
must keep working exactly as today.

The new visual language is **Engraved**: a paper-depth metaphor where blocks
sit *into* or *out of* the page depending on state. The existing `h-2` resize
handles at the top and bottom of every editable block are restyled as
recessed paper grooves with twin ink rules — making the resize affordance a
deliberate part of every state, not a flat gray strip.

---

## 1 · Tokens to add

Add the following to the `@theme` block in `frontend/src/index.css`:

```css
/* Engraved paper depth */
--color-paper-soft:    #ede8d8;  /* resting fill */
--color-paper-deep:    #e8e3d4;  /* draft + drag-ghost fill (pressed) */
--color-paper-raised:  #fefcf5;  /* selected & dragging-card fill (lifted) */
--color-paper-rule:    rgba(50, 40, 20, 0.45);   /* twin-rule on grooves (rest) */
--color-paper-rule-ink:#1f2426;                   /* twin-rule on grooves (selected) */
--color-paper-groove-bg:        rgba(50, 40, 20, 0.05);
--color-paper-groove-bg-strong: rgba(50, 40, 20, 0.10);

/* Shared shadows (light mode) */
--shadow-engrave-press:   inset 0 2px 5px rgba(50,40,20,0.12), inset 0 -1px 0 rgba(255,255,255,0.60);
--shadow-engrave-rest:    inset 0 1px 2px rgba(50,40,20,0.08), inset 0 -1px 0 rgba(255,255,255,0.70);
--shadow-engrave-raise:   0 1px 0 rgba(255,255,255,0.90) inset, 0 -1px 0 rgba(80,70,50,0.08) inset,
                          0 10px 24px rgba(50,40,20,0.10), 0 2px 4px rgba(50,40,20,0.06);
--shadow-engrave-drag:    0 1px 0 rgba(255,255,255,0.90) inset,
                          0 28px 48px rgba(50,40,20,0.18), 0 4px 10px rgba(50,40,20,0.10);

/* Groove inset shadow used inside the resize bands */
--shadow-groove-inner: inset 0 1px 2px rgba(50,40,20,0.12),
                       inset 0 -1px 0 rgba(255,255,255,0.65);
```

Inside `html.dark { ... }`, add the dark-mode mappings:

```css
--color-paper-soft:    #1c1917;
--color-paper-deep:    #14110f;
--color-paper-raised:  #292524;
--color-paper-rule:    rgba(255, 250, 240, 0.30);
--color-paper-rule-ink:#f5f5f4;
--color-paper-groove-bg:        rgba(0, 0, 0, 0.30);
--color-paper-groove-bg-strong: rgba(0, 0, 0, 0.45);

--shadow-engrave-press:   inset 0 2px 5px rgba(0,0,0,0.45), inset 0 -1px 0 rgba(255,255,255,0.04);
--shadow-engrave-rest:    inset 0 1px 2px rgba(0,0,0,0.35), inset 0 -1px 0 rgba(255,255,255,0.04);
--shadow-engrave-raise:   0 1px 0 rgba(255,255,255,0.05) inset, 0 -1px 0 rgba(0,0,0,0.30) inset,
                          0 10px 24px rgba(0,0,0,0.40), 0 2px 4px rgba(0,0,0,0.25);
--shadow-engrave-drag:    0 1px 0 rgba(255,255,255,0.05) inset,
                          0 28px 48px rgba(0,0,0,0.55), 0 4px 10px rgba(0,0,0,0.30);
--shadow-groove-inner: inset 0 1px 2px rgba(0,0,0,0.45),
                       inset 0 -1px 0 rgba(255,255,255,0.04);
```

(Light-mode `--color-on-surface` / `--color-on-surface-variant` and existing
`--color-planned`, `--color-actual` are reused unchanged.)

---

## 2 · State recipes for `TimeBlockCard`

Each state below maps to the existing branches in `TimeBlockCard.tsx`'s
`shellClassName` builder. **Keep the function structure**; only swap
class strings.

### A. Empty draft (no task type yet)

Today this case lives in the draft-block render path (the dashed gray
placeholder when the inspector is in create mode). Restyle as:

- Fill: `bg-paper-deep`
- Border: `1px dashed rgba(80,70,50,0.25)` (use an arbitrary value class:
  `border border-dashed border-[rgba(80,70,50,0.25)] dark:border-[rgba(255,250,240,0.18)]`)
- Inner shadow: `--shadow-engrave-press` (pressed-into-paper)
- Border radius: `rounded-md` (6px)
- No outer shadow
- Body text: italicized "waiting for a name" placeholder in `text-paper-rule`
- Time label right-aligned, mono, also `text-paper-rule`

### B. Created · idle (resting)

The default `data-block` state, **not** selected, **not** dragging.

- Fill: `bg-paper-soft`
- Border: none
- Inner shadow: `--shadow-engrave-rest`
- Border radius: `rounded-md`
- No outer shadow
- Title: `text-[12.5px] font-medium text-on-surface`
- Time: `text-[10.5px] font-mono text-on-surface-variant`, e.g. `09:00 → 09:45`
  (use `→` instead of `–` to match the engraved language)

### C. Selected (inspector open on this block)

- Fill: `bg-paper-raised`
- Border: none
- Composite shadow: `--shadow-engrave-raise` (extruded card, sits above lane)
- Border radius: `rounded-md`
- **Lane accent stripe** at left edge — preserves the planned/actual signal:
  ```
  absolute top-2 bottom-2 left-0 w-[2px] rounded-[2px]
    bg-on-surface  // OR bg-planned / bg-actual based on lane (see §3)
  ```
- Title: `text-[13.5px] font-semibold text-on-surface`
- Time: `text-[10.5px] font-mono text-on-surface-variant`
- Optional micro-meta in the top-right: `· selected` in uppercase mono 9.5px,
  letter-spacing `0.1em`, color `text-on-surface-variant`. Skip if the block
  is < 60min.

### D. Press · hold (drag-move in progress)

While `dragKind === 'move'`:

- Keep the existing **ghost** at the original position. Restyle the ghost as:
  - `bg-paper-deep` with `--shadow-engrave-press` (deeper press than draft)
  - `rounded-md`
  - No border, no text — just the recessed footprint
- The **lifted card** (the one following the pointer):
  - `bg-paper-raised`
  - `rounded-md`
  - Shadow: `--shadow-engrave-drag`
  - Rotation: `rotate-[-1.2deg]` (only while dragging — not when settled)
  - Same lane accent stripe at left edge as Selected
  - Same content layout as Selected
  - z-index: 30 (existing convention is fine)

While `dragKind === 'complete'` (swipe-right): keep the existing semantics,
but swap the armed/disarmed background and border colors to:
- Disarmed: `bg-paper-raised` with `1px solid rgba(80,70,50,0.25)` (`dark:` token-aware)
- Armed (past commit threshold): `bg-tertiary-container/55` (existing token, fine) + `--shadow-engrave-drag`

While `dragKind === 'resize'`: render as Selected, but with no rotation and
no drag-shadow — just the raised paper. The user is editing one edge of the
existing block, not lifting it off.

---

## 3 · Resize bands (top + bottom grooves)

Today `TimeBlockCard.tsx` renders these as plain `h-2 bg-on-surface/10` strips
when `!readOnly`. Replace with a **paper groove**:

```tsx
<button
  type="button"
  aria-label="Resize block start"
  className={[
    'h-2 w-full shrink-0 cursor-ns-resize border-0 relative',
    'bg-paper-groove-bg hover:bg-paper-groove-bg-strong',
    'dark:bg-paper-groove-bg dark:hover:bg-paper-groove-bg-strong',
    "[box-shadow:var(--shadow-groove-inner)]",
    // twin-rule indicator centered horizontally
    "before:content-[''] before:absolute before:left-1/2 before:-translate-x-1/2",
    "before:top-[2px] before:h-[1px] before:w-9",
    isSelected
      ? "before:bg-paper-rule-ink"
      : "before:bg-paper-rule",
    "after:content-[''] after:absolute after:left-1/2 after:-translate-x-1/2",
    "after:top-[4.5px] after:h-[1px] after:w-9",
    isSelected
      ? "after:bg-paper-rule-ink"
      : "after:bg-paper-rule",
  ].join(' ')}
  onPointerDown={(e) => startResize('start', e)}
/>
```

Mirror for the bottom handle (`'end'`). Note the band:

- Is **always visible** on editable blocks (resting, selected, draft).
- Has the recessed inset shadow even in resting state — the groove is part of
  the resting block's identity, not just selected.
- Gets **ink-colored** twin rules when the block is selected (token
  `--color-paper-rule-ink`), and softer translucent rules otherwise
  (`--color-paper-rule`).
- Is hidden when the block is being moved (current behavior — the lifted card
  doesn't need them).
- Stays untouched in `readOnly` mode (history view) — render no bands at all,
  same as today.

Also adjust the inner content padding so the title/time row is **vertically
centered between the two bands**. The existing code uses
`px-1.5 py-1.5`; switch to `px-3 py-0` and rely on `flex items-center` to
center the body row, with the bands as separate flex children at the top and
bottom. (This is closer to the design and survives the resize bands eating
~8px each.)

---

## 4 · Planned vs Actual lanes

Keep the existing color signal but make it small and confident. The
**left-edge accent stripe** carries it, not a tinted fill:

| Lane | Stripe color (light) | Stripe color (dark) |
|---|---|---|
| Planned | `bg-planned` (`#1967d2`) | `bg-planned-dark` (`#8ab4f8`) |
| Actual  | `bg-actual` (`#0d6b63`) | `bg-actual-dark` (`#7dd3c8`) |

Apply the stripe in **selected** and **drag-lifted** states. In resting state,
drop the stripe entirely — the block's lane position is enough signal at rest.
This is a deliberate change from today's `bg-primary-container/45` tinted
resting state, which was muddy.

Remove the `laneBlockTone` background tint entirely from resting state. The
single `bg-paper-soft` is the answer for both lanes. The accent on
select/drag is enough to disambiguate.

---

## 5 · Side-text layout (very short blocks)

The existing code branches into `useSideTextLayout` when
`innerContentPx < MIN_INNER_PX_FOR_TIME` (~34 px). Keep that branch but:

- Still render the **top + bottom resize grooves** — just thinner. Drop the
  `h-2` to `h-[5px]` for blocks under 34 px tall.
- Drop the twin-rule pseudo-elements when in side-text mode (not enough room).
- The "lane bar" the side-text layout draws as a fake left stripe should keep
  matching `--color-planned` / `--color-actual` accent tokens.

---

## 6 · Transitions

All shadow / background swaps should crossfade with the existing
`transition-[box-shadow,background-color,border-color] duration-150` you
already have on the shell. Don't add new transition properties — the
existing curve is the language.

The lifted-card rotation (`-1.2deg`) should **not** be transitioned. It
applies instantly when `dragKind === 'move'` engages, and snaps back when the
drag ends. Otherwise the block looks like it's flopping over.

---

## 7 · What NOT to do

- Don't change `TimeBlockCard.tsx`'s React structure, ref handling, or
  pointer event logic.
- Don't change `DayTimeline.tsx` — the bands are still inside the block.
- Don't add new files unless the existing component clearly needs to split.
- Don't introduce new shadow tokens beyond the ones listed here.
- Don't reintroduce the blue/teal tinted resting fills. They were the muddy
  thing that made the original look "not right." The paper depth is doing the
  work now.

---

## 8 · Verification checklist

When you're done, the following should be true:

- [ ] A new resting planned block looks **pressed into** the planned-surface
      lane background, with twin recessed grooves at top/bottom.
- [ ] Clicking a block makes it visibly **rise** out of the lane (composite
      raise shadow, lighter raised paper fill, a 2px lane-colored stripe at
      the left edge, ink-darker twin rules on the bands).
- [ ] Pressing and holding a block: original spot turns into a **deep
      pressed footprint** (no text), and the cursor-following card carries
      the selected-style paint plus a `-1.2deg` rotation and the heavier
      `--shadow-engrave-drag`.
- [ ] An empty draft slot looks like the deep press but with a dashed border
      and italicized placeholder copy.
- [ ] Light mode and dark mode both work — dark uses the `html.dark` token
      mappings, never hard-coded grays.
- [ ] All existing tests in `TimeBlockCard.test.tsx` still pass. If a test
      asserts on a specific class string that's now gone, update it to assert
      the data attributes (`data-selected`, `data-drag-kind`) instead.
- [ ] No new lint errors.

---

## 9 · Reference

Open `Block States.html` in this bundle. Artboard **04 — Engraved** shows
all four states side by side on a single lane fragment. The right rail
labels which state is which. The bottom-left footer line summarizes the
resize-band treatment.

Pixel-pick from there if anything in this prompt is ambiguous.
