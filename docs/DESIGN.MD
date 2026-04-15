# Design System Specification: The Quiet Architect

## 1. Overview & Creative North Star

**Creative North Star: The Monastic Archive**
This design system rejects the "dashboard" aesthetic in favor of a high-end editorial experience. It is designed for deep work, treating a user’s schedule not as a chaotic list of chores, but as a prestigious, curated archive of their time.

To break the "template" look, we employ **Intentional Asymmetry**. Instead of centered, rigid grids, we use expansive white space to the left or right to give the eye a place to rest. Elements should feel like they are floating on a calm pool of water—connected but not constrained by boxes. We prioritize tonal depth over structural lines, ensuring the interface feels "built" rather than "drawn."

---

## 2. Colors & Surface Philosophy

The palette is a sophisticated range of charcoals and warm greys designed to reduce cognitive load.

### The "No-Line" Rule

**Strict Mandate:** Prohibit the use of 1px solid borders for sectioning.
Structure must be defined through **Background Shifts**. A section is not a box; it is a change in the environment. Use `surface-container-low` for secondary content sitting atop a `surface` background. The transition should be felt, not seen as a line.

### Surface Hierarchy & Nesting

Treat the UI as a series of stacked, fine-milled paper sheets.

- **Base Layer:** `surface` (#f9f9f9) – The canvas.
- **Primary Sectioning:** `surface-container-low` (#f2f4f4) – Subtle grouping.
- **Interactive Elements:** `surface-container-lowest` (#ffffff) – Used for high-priority focus cards to create a natural "lift."

### The Glass & Gradient Rule

To prevent a "flat" or "cheap" feel:

- **CTAs:** Use a subtle linear gradient (135°) from `primary` (#5d5e61) to `primary_dim` (#515255). This provides a "milled metal" texture.
- **Floating Overlays:** Use `surface_container_lowest` with an 85% opacity and a `backdrop-blur: 20px`. This creates a high-end glassmorphism effect that integrates the background colors into the foreground.

---

## 3. Typography: Editorial Authority

We pair **Manrope** (Display/Headline) with **Inter** (Body/Label) to balance character with utility.

- **Display (Manrope):** Large, low-tracking, and authoritative. Use `display-md` (2.75rem) for daily reflections to make user words feel momentous.
- **Body (Inter):** High legibility with generous line-height (1.6). Use `body-lg` (1rem) for planning notes.
- **The Hierarchy Rule:** Never use bold for emphasis alone. Use scale and color (`on_surface` vs `on_surface_variant`). A massive, light-weight header is more premium than a small, bold one.

---

## 4. Elevation & Depth

Traditional shadows are too heavy for a "calm" tool. We use **Tonal Layering**.

- **Ambient Shadows:** For floating dialogs, use a shadow with a 40px blur, 0px offset, and 4% opacity using the `on_surface` color. It should appear as a soft glow of light, not a shadow cast by a sun.
- **The Ghost Border:** If accessibility requires a container boundary, use `outline_variant` (#adb3b4) at **15% opacity**. It must be a "suggestion" of a line, barely visible to the naked eye but enough for the subconscious to map the space.
- **Interactive Depth:** On hover, instead of a shadow increasing, the background should shift from `surface-container` to `surface-container-high`. The element doesn't "rise"—it becomes more "present."

---

## 5. Component Logic

### Time Blocks (The Signature Component)

- **Visuals:** Forbid hard borders. Use a left-accent bar (4px) of `primary_fixed` to denote time.
- **States:** "On Track" uses `tertiary_container` (soft green tint); "Partially" uses an amber tint; "Off Track" uses a muted `error_container` (soft red).
- **Spacing:** Use `0.75rem` (xl roundedness) for block corners to maintain a "soft-professional" feel.

### Buttons & Interaction

- **Primary:** Gradient-filled (Primary to Primary-Dim), `md` (0.375rem) roundedness. No border.
- **Secondary:** Transparent background with a "Ghost Border" (15% `outline_variant`).
- **Tertiary:** Text-only, using `label-md` uppercase with `0.05em` letter spacing for a refined, architectural look.

### Input Fields & Inline Editing

- **The "Invisible" Input:** Inline editing should not have a box. Hovering over text should reveal a subtle `surface-container-highest` background shift.
- **Active State:** Focus is indicated by a subtle transition of the `primary` color in the text itself, rather than a thick blue ring.

### Cards & Lists

- **The Gap Rule:** Forbid divider lines. Separate list items using `1rem` of vertical whitespace. If separation is needed, use alternating backgrounds (`surface` to `surface-container-low`) rather than a line.

---

## 6. Do's and Don'ts

### Do

- **Do** embrace "wasteful" white space. It signals luxury and focus.
- **Do** use `manrope` for numbers. Time durations should look elegant.
- **Do** use `surface-tint` for subtle brand moments in dark mode.
- **Do** ensure the `on_surface_variant` is used for "ghost" text to keep the hierarchy clear.

### Don't

- **Don't** use 100% black (#000) or pure blue. It breaks the calm, neutral intentionality.
- **Don't** use standard "Drop Shadows." Use the Tonal Layering or Ambient Shadow rules.
- **Don't** use "Alert Red." Use the semantic `error` (#9f403d) which is muted and sophisticated.
- **Don't** center-align long-form text. Keep everything left-aligned to mimic the layout of a high-end architectural magazine.

---

## 7. Dark Mode Integration

In Dark Mode, the hierarchy reverses.

- **Surface:** Becomes the deepest charcoal.
- **Nesting:** Higher importance elements become _lighter_ (moving toward `surface-bright`), mimicking how light hits physical objects in a dark room.
- **Gradients:** Maintain the 135° gradient on CTAs but reduce the contrast between the two stops to prevent "neon" vibrating edges.
