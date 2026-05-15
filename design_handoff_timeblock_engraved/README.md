# Handoff · Timebox time block — "Engraved" redesign

> Paste the contents of `PROMPT.md` into Claude Code (or hand the whole folder
> to your engineer). The other files in this bundle are the design source of
> truth.

## What's in this bundle

| File | Purpose |
| --- | --- |
| `PROMPT.md` | **Paste-ready prompt** for Claude Code. Self-contained spec — references your codebase paths and the visual design directly. |
| `README.md` | This file. |
| `Block States.html` + `variants.jsx` + `app.jsx` + `design-canvas.jsx` | The interactive design canvas. Open `Block States.html` in a browser to see all six variants. The **Engraved** artboard (04) is the one we're shipping. |
| `engraved-states.png` | Static screenshot of the Engraved artboard, for reference. (Optional — generate from the HTML if you need it.) |

## Fidelity

**Hi-fi.** Final colors, typography, shadows, and resize-band treatment are
locked. Spacing values are intentional. Use them as-is.

## Scope

This redesign replaces the visual treatment of a single component in your
existing codebase:

- **File:** `frontend/src/components/TimeBlockCard.tsx`
- **Tokens:** `frontend/src/index.css` (new tokens listed in `PROMPT.md`)

Behavior — drag-to-move, resize, swipe-to-complete, selection, the inspector
rail, light/dark mode — **is unchanged**. Only the paint changes.

## States covered

The component has four states. The redesign specifies each:

1. **Empty draft** — user clicked an empty time slot; no task type yet.
2. **Created · idle** — block exists, not selected.
3. **Selected** — block clicked; inspector is open.
4. **Press · hold (drag)** — user is dragging the whole block to a new time.

In addition, every state except press-hold drag exposes **start/end resize
bands** at the top and bottom of the block. These are the existing
`h-2` resize handles in `TimeBlockCard.tsx`, restyled as recessed paper
grooves with twin ink rules.

## How to read the design

Open `Block States.html`. The Engraved artboard (04) shows all four states
stacked in one lane fragment. The right rail annotates which state is which.
The footer line describes the resize-band treatment.

Every variant in the canvas was drawn at the same scale and on the same lane —
so if you ever want to compare against the alternatives, they're right there.

## Tokens added

Two new tokens for the paper depth:

```css
--color-paper-soft:   #ede8d8;  /* resting fill */
--color-paper-deep:   #e8e3d4;  /* draft / drag-ghost fill (pressed) */
--color-paper-raised: #fefcf5;  /* selected fill */
```

Full token + shadow recipe lives in `PROMPT.md`.

## Light & dark

The design here is light-mode. Dark-mode mappings are specified in `PROMPT.md`
and follow your existing dark-token convention in `index.css`.
