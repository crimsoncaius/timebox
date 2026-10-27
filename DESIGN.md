---
version: alpha
name: Timebox Monastic Archive
description: A calm, editorial design system for dual-lane timeboxing, balancing austere productivity with soft tonal depth.
# Colors describe shared semantic roles. CSS measurements and component recipes
# below are the web baseline; Android mappings are documented in the body.
colors:
  primary: "#5d5e61"
  primary-dim: "#515255"
  on-primary: "#f7f7fa"
  primary-container: "#e2e2e5"
  on-primary-container: "#505254"
  primary-fixed: "#e2e2e5"
  primary-fixed-dim: "#d4d4d7"
  on-primary-fixed: "#3e3f42"
  on-primary-fixed-variant: "#5a5c5e"
  secondary: "#5f5f5f"
  secondary-dim: "#535353"
  on-secondary: "#faf8f8"
  secondary-container: "#e4e2e2"
  on-secondary-container: "#515252"
  secondary-fixed: "#e4e2e2"
  secondary-fixed-dim: "#d5d4d4"
  on-secondary-fixed: "#3f3f3f"
  on-secondary-fixed-variant: "#5b5b5b"
  tertiary: "#5c605e"
  tertiary-dim: "#505452"
  on-tertiary: "#f7f9f7"
  tertiary-container: "#e9ece9"
  on-tertiary-container: "#545756"
  tertiary-fixed: "#e9ece9"
  tertiary-fixed-dim: "#dbdddb"
  on-tertiary-fixed: "#424544"
  on-tertiary-fixed-variant: "#5e6260"
  error: "#9f403d"
  error-dim: "#4e0309"
  on-error: "#fff7f6"
  error-container: "#fe8983"
  on-error-container: "#752121"
  background: "#f9f9f9"
  on-background: "#2d3435"
  surface: "#f9f9f9"
  surface-dim: "#d4dbdd"
  surface-bright: "#f9f9f9"
  surface-container-lowest: "#ffffff"
  surface-container-low: "#f2f4f4"
  surface-container: "#ebeeef"
  surface-container-high: "#e4e9ea"
  surface-container-highest: "#dde4e5"
  surface-variant: "#dde4e5"
  surface-tint: "#5d5e61"
  on-surface: "#2d3435"
  on-surface-variant: "#5a6061"
  outline: "#757c7d"
  outline-variant: "#adb3b4"
  inverse-surface: "#0c0f0f"
  inverse-on-surface: "#9c9d9d"
  inverse-primary: "#f9f9fc"
  planned: "#1967d2"
  planned-surface: "#f5f9ff"
  planned-border: "#c5d9f7"
  planned-dark: "#8ab4f8"
  planned-dark-surface: "#0f141c"
  planned-dark-border: "#2a3f55"
  actual: "#0d6b63"
  actual-surface: "#f2faf9"
  actual-border: "#b5ded6"
  actual-dark: "#7dd3c8"
  actual-dark-surface: "#0c1211"
  actual-dark-border: "#1e3d38"
  on-planned: "#f7f7fa"
  on-planned-dark: "#0a1f3d"
  on-actual: "#f7f7fa"
  on-actual-dark: "#00201d"
  planned-tint: "#e9f1fd"
  planned-dark-tint: "#14213a"
  actual-tint: "#def1ee"
  actual-dark-tint: "#0f2724"
  warn: "#8a5300"
  warn-surface: "#fbefd9"
  warn-dark: "#f0b86b"
  warn-dark-surface: "#33240f"
  accent: "#1967d2"
  on-accent: "#f7f7fa"
  accent-tint: "#e9f1fd"
  accent-border: "#c5d9f7"
  accent-dark: "#8ab4f8"
  on-accent-dark: "#0a1f3d"
  accent-dark-tint: "#14213a"
  accent-dark-border: "#2a3f55"
  done: "#0d6b63"
  on-done: "#f7f7fa"
  done-tint: "#def1ee"
  done-border: "#b5ded6"
  done-dark: "#7dd3c8"
  on-done-dark: "#00201d"
  done-dark-tint: "#0f2724"
  done-dark-border: "#1e3d38"
  neutral-strong: "#5d5e61"
  neutral-strong-dark: "#c8c7cb"
  timeline-grid-strong: "#dadce0"
  timeline-grid-soft: "#e8eaed"
  timeline-label: "#5f6368"
  now-line: "#9f403d"
  now-line-dark: "#f2a09a"
  dark-background: "#0c0a09"
  dark-surface: "#121212"
  dark-surface-container-lowest: "#0c0a09"
  dark-surface-container-low: "#1c1917"
  dark-surface-container: "#292524"
  dark-surface-container-high: "#44403c"
  dark-surface-container-highest: "#57534e"
  dark-on-surface: "#f5f5f4"
  dark-on-surface-variant: "#a8a29e"
  dark-outline: "#78716c"
  dark-outline-variant: "#44403c"
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 44px
    fontWeight: 200
    lineHeight: 1
    letterSpacing: -0.05em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: 300
    lineHeight: 40px
    letterSpacing: -0.05em
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: 300
    lineHeight: 32px
    letterSpacing: -0.025em
  headline-sm:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: 300
    lineHeight: 28px
    letterSpacing: -0.025em
  title-sm:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: 500
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: 300
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
  body-xs:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 400
    lineHeight: 16px
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 500
    lineHeight: 20px
    letterSpacing: -0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 600
    lineHeight: 16px
    letterSpacing: 0.08em
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 600
    lineHeight: 16px
    letterSpacing: 0.12em
  micro-caps:
    fontFamily: Inter
    fontSize: 10px
    fontWeight: 500
    lineHeight: 12px
    letterSpacing: 0.18em
  timeline-block:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 500
    lineHeight: 14px
  timeline-meta:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: 400
    lineHeight: 12px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  2xl: 32px
  3xl: 48px
  4xl: 64px
  page-x: 48px
  page-y: 48px
  sidebar-width: 256px
  nav-gap: 8px
  card-padding: 20px
  panel-padding: 24px
  timeline-slot-height: 46px
  timeline-column-gap: 8px
  timeline-gutter: 56px
rounded:
  none: 0px
  sm: 0.25rem
  DEFAULT: 0.375rem
  md: 0.5rem
  lg: 0.75rem
  xl: 1rem
  2xl: 1.5rem
  full: 9999px
radii:
  none: 0px
  subtle: 0.25rem
  control: 0.5rem
  card: 0.75rem
  panel: 1rem
  overlay: 1.5rem
  pill: 9999px
shadows:
  none: "none"
  hairline-inner: "inset 0 1px 2px rgba(0, 0, 0, 0.05)"
  ambient-xs: "0 0 24px rgba(45, 52, 53, 0.04)"
  ambient-sm: "0 0 40px rgba(45, 52, 53, 0.04)"
  ambient-md: "0 0 40px rgba(45, 52, 53, 0.08)"
  ambient-lg: "0 0 40px rgba(45, 52, 53, 0.14)"
  dark-ambient-sm: "0 0 40px rgba(0, 0, 0, 0.25)"
  dark-ambient-md: "0 0 40px rgba(0, 0, 0, 0.35)"
elevation:
  base:
    shadow: "none"
    surface: "{colors.surface}"
  raised-card:
    shadow: "0 0 40px rgba(45, 52, 53, 0.04)"
    surface: "{colors.surface-container-lowest}"
  floating-panel:
    shadow: "0 0 40px rgba(45, 52, 53, 0.06)"
    surface: "{colors.surface-container-lowest}"
  active-drag:
    shadow: "0 0 40px rgba(45, 52, 53, 0.14)"
    surface: "{colors.surface-container-lowest}"
motion:
  instant: "0ms"
  fast: "150ms"
  standard: "200ms"
  note-save-delay: "450ms"
  easing-standard: "ease-in-out"
  easing-linear: "linear"
opacity:
  ghost-border: 0.15
  subtle-border: 0.25
  panel-glass: 0.85
  muted-surface: 0.5
  selected-surface: 0.95
blur:
  subtle: 4px
  overlay: 20px
  header: 24px
components:
  app-shell:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
  sidebar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    width: "{spacing.sidebar-width}"
    padding: "{spacing.2xl}"
  topbar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    height: 80px
    padding: "{spacing.xl}"
  page-title:
    textColor: "{colors.on-surface}"
    typography: "{typography.display-lg}"
  supporting-copy:
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.body-lg}"
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.label-md}"
    rounded: "{rounded.DEFAULT}"
    padding: 12px 20px
  button-secondary:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label-md}"
    rounded: "{rounded.xl}"
    padding: 8px 16px
  button-ghost:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-md}"
    rounded: "{rounded.full}"
    padding: 8px 12px
  icon-button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    rounded: "{rounded.full}"
    size: 40px
  input-field:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.lg}"
    padding: 10px 12px
  quiet-input:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.xl}"
    padding: 12px 16px
  panel-card:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.2xl}"
    padding: "{spacing.card-padding}"
  floating-panel:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.2xl}"
    padding: "{spacing.panel-padding}"
  status-pill:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.body-xs}"
    rounded: "{rounded.full}"
    padding: 6px 12px
  timeline-header-planned:
    textColor: "{colors.planned}"
    typography: "{typography.label-sm}"
  timeline-header-actual:
    textColor: "{colors.actual}"
    typography: "{typography.label-sm}"
  timeline-lane-planned:
    backgroundColor: "{colors.planned-surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.none}"
  timeline-lane-actual:
    backgroundColor: "{colors.actual-surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.none}"
  timeline-block-planned:
    backgroundColor: "{colors.planned-tint}"
    textColor: "{colors.on-surface}"
    typography: "{typography.timeline-block}"
    rounded: "{rounded.md}"
    padding: 6px
  timeline-block-actual:
    backgroundColor: "{colors.actual-tint}"
    textColor: "{colors.on-surface}"
    typography: "{typography.timeline-block}"
    rounded: "{rounded.md}"
    padding: 6px
  calendar-cell:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.xl}"
    padding: "{spacing.md}"
  destructive-action:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.error}"
    rounded: "{rounded.full}"
    padding: "{spacing.sm}"
---

## Overview

Timebox should feel like a monastic archive for a day of work: quiet, precise, and intentionally sparse. Light editorial headings, paper-like neutral surfaces, and restrained color preserve the character of a high-end planner. On Android, compact hierarchy, predictable placement, and progressive disclosure create that calm without requiring desktop-sized headings or whitespace.

The signature experience is the dual-lane timeline. Planned and actual time sit side by side as calm, gridded columns, with only a few clear accents: blue for planned intent, teal for actual record, and muted red for destructive states. Most hierarchy comes from space, tone, and typography rather than decoration.

## Scope and maintenance

Reconciled with the Android implementation and accepted decisions on 2026-10-27. Shared principles below apply to both platforms. The frontmatter retains the web CSS token baseline and component recipes; its px/rem sizes, shadows, blur, and timings are not Android defaults. Android uses the explicit mappings below in dp/sp. A platform or component contract takes precedence over a general recipe for that surface.

Use [CONTEXT.md](CONTEXT.md) for domain terms and [accepted ADRs](docs/adr/) for behavior. The implementation references and component contracts below record established decisions; historical prototype rounds in linked documents are not additional requirements. Update the relevant contract and this overview together when an accepted design decision changes. This document does not imply that every existing screen has passed accessibility or visual review.

## Shared principles

### Color and meaning

The palette is mostly neutral paper, graphite text, and desaturated control colors. Use tonal layers to distinguish canvas, grouped workspaces, editable fields, and raised content. Resolve those roles through the platform's theme, especially in dark mode. Avoid pure black in light mode; reserve saturated accents for meaningful actions, selection, and time recognition.

Color has four roles, and every colored element must mean one of them:

- **Neutral** (paper and graphite) is the base: surfaces, text, icons, secondary and tonal buttons, Cancel. Neutral never makes something stand out; size, weight and space do that.
- **Accent · Plan** (blue, `accent`, the same values as `planned`) means intended, or acts here: the main action on a screen, selected tabs and chips, switches when on, focus rings, links, today's marker, and planned time.
- **Done · Live** (teal, `done`, the same values as `actual`) means real: it happened or it is happening now. Actual time, live tracking, Start and Stop tracking, completion checks, and met habit cells and totals.
- **Signals**: `warn` (amber) for an action that replaces recorded time, and `error` (red, intentionally earthy rather than alarm-red) only for what cannot be undone and for error text.

**Now Line exception (2026-10-26):** The current-time line and its leading dot use red so the present moment is easy to locate across the timeline. Use `now-line` (`#9f403d`) in light mode and `now-line-dark` (`#f2a09a`) in dark mode. This is a time-position indicator, not an error or destructive action; other uses of red retain the rules above.

Color a control by what it changes, asking in this order and stopping at the first yes: deletes for good → `error`; replaces recorded time → `warn`; starts, stops or records something real → `done`; the main action or current selection → `accent`; everything else → neutral. Meaning beats utility, so Start tracking is teal even though it is also the main action. Use `on-accent` / `on-done` text on those fills.

Blue works because it is rare: give each area one strong blue (a fill, link or underline), for the selection or the next action. Planned items mark themselves with `accent-tint` plus `accent-border`, never a solid blue fill, so a blue button beside the timeline never reads as plan. Completion is a moment, not a state: the check fills `done` and the row flashes `done-tint` once, then fades to secondary text with a strike-through. Never rely on hue alone, since blue and teal are close in lightness: done carries a check, live a pulse dot and solid edge, plan a tint and outline, today a ring.

Outside the timeline, use the role names (`accent`, `done`) rather than the lane names, so code says why something is colored. `neutral-strong` (graphite) is the exception filled button, for a confirmation that should draw no color. `project` and `tertiary` are retired as colors: project names and icons use ink (the accent when a project scope is selected), and on or chosen states use `accent`. If user-labeled things (habits, task types, projects) need color later, give them a separate tag palette kept off buttons and selection.

Dark mode reverses the surface hierarchy without becoming neon. The background becomes deep charcoal, elevated regions move toward warmer stone grays, and planned/actual accents shift to brighter blue and mint so the timeline remains readable.

### Typography and density

Typography carries most of the brand personality. Manrope's light weights and tight tracking give headings their editorial tone. Inter supports body copy, labels, dense controls, Task Type names, and timeline text. Platform-specific navigation and numeric roles are defined below.

Prefer light headings and clear relative scale over heavy weight. Kickers and lane labels are uppercase and restrained; form labels are sentence case and mark optional fields with a muted "optional". Medium and semibold support selection, compact metadata, and button legibility. Compact layouts are part of the identity when they preserve readable content, reachable actions, and clear grouping.

### Surfaces, shapes, and controls

Keep depth quiet. Tonal surfaces and fine borders do most of the work; avoid heavy shadows. The grid stays crisp with small block radii, while cards and decision surfaces use softer corners. Pills and icon controls may be fully rounded. Use the platform shape scale rather than mixing arbitrary radii within one component family.

Primary buttons use the color their action earns and its paired content token (`on-accent`, `on-done`, or the appropriate warning/error pair). Do not assume that button text is always light: dark-mode accent fills use dark content. Secondary and ghost actions stay tonal. Neutral `primary` and Material compatibility tokens are not substitutes for semantic `accent` and `done` roles.

### Timeline

Keep Planned and Actual headers blue and teal, with pale lane surfaces in light mode and near-black tinted surfaces in dark mode. Blocks use their lane's tint and outline, not a generic gray fill. Selection and dragging retain lane identity; Android uses stronger lane tints rather than whitening every selected block.

Block height represents duration, including very short blocks. As space shrinks, hide resize grooves and then text instead of enlarging a block to imply a longer duration. Tapping opens details; exact time fields provide an alternative to direct resizing. Both lanes share pinch zoom. View contains scale and Reset zoom; there is no standalone Zoom bar. See [short-block behavior](docs/design/short-blocks-170-167.md) and its [Zoom presentation revision](docs/design/remove-zoom-view-241.md).

### Feedback and accessibility

Keep loading, saving, disabled, failed, and recoverable states legible. Actions name their result; Undo, Retry, and Open retain their distinct meanings. Preserve drafts and relevant context when moving between a surface and its details. Never communicate state through hue alone. Respect reduced motion and platform accessibility timing; keyboard, system insets, and floating navigation must not cover actions or recovery feedback.

## Web rules

The frontmatter describes the web baseline: 44px editorial page titles, a 4px spacing unit, spacious desktop sections, and CSS radii and elevation. Desktop uses a fixed left navigation rail with textual labels, outlined icons, a left selection rule, and muted inactive items. Android's Home directory and Shortcut Bar do not replace web navigation.

Use 48px desktop page padding and 32–64px section separation where space permits; adapt to narrower viewports rather than carrying these dimensions unchanged. The baseline timeline has 46px half-hour slots, a 56px gutter, and an 8px lane gap before zoom. Keep surrounding controls compact enough that the timeline remains dominant.

Manrope serves editorial titles, headings, and the web's display/navigation treatment; Inter serves body and dense controls. The 40px icon-button recipe is a web visual size, not an Android touch-target requirement.

Web floating panels, popovers, and comboboxes may use low-opacity ambient shadows and translucent paper surfaces with backdrop blur. Hover shifts surface tone and focus uses an accent ring. These CSS effects do not require native Android sheets to become translucent.

## Android rules

### Foundations

Use [TimeboxType](android/app/src/main/java/com/timebox/android/ui/theme/Type.kt), [TimeboxShapes and TimeboxDimens](android/app/src/main/java/com/timebox/android/ui/theme/Theme.kt), and [TimeboxColors](android/app/src/main/java/com/timebox/android/ui/theme/Colors.kt). Values below are the current native baseline, not conversions from CSS pixels.

| Type role | Family | Size / line height (sp) | Weight |
| --- | --- | --- | --- |
| Screen title | Manrope | 27 / 30 | 200 |
| Display | Manrope | 34 / default | 200 |
| Section title | Manrope | 15 / default | 300 |
| Kicker | Manrope | 11 / default | 500 |
| Body | Inter | 13.5 / 20 | 400 |
| Small body | Inter | 12 / 18 | 400 |
| Label / button | Inter | 13.5 / 14 respectively; default line height | 500 |
| Lane label | Inter | 10.5 / default | 600 |
| Block title | Inter | 12.5 / 15 | 500 |
| Selected block title | Inter | 13 / 15.6 | 600 |
| Navigation label / gutter | Inter | 10.5 / 10 respectively; default line height | 500 / 400 |
| Mono / small mono | Platform monospace | 10.5 / 10 respectively; default line height | 400 |

Manrope and Inter are bundled. Monospace styles support compact times, durations, and paths; the timeline gutter itself uses Inter. Component-specific typography may differ: the Shortcut Bar uses an 11sp small-body treatment, and approved Assistant task cards use a 22sp leading task title. Keep such overrides in their component contracts.

| Measurement | Android baseline |
| --- | --- |
| Screen padding | 12dp; shared top bar uses 16dp horizontal padding |
| Timeline at default scale | 48dp per 30 minutes; 44dp gutter; 6dp lane gap |
| Block / field / calendar cell radius | 6dp / 12dp / 10dp |
| Card / group radius | 14dp / 18dp |
| Sheet | 24dp top corners |
| Chip | Fully rounded |

Account for measured navigation clearance and system/keyboard insets rather than relying on fixed bottom padding alone. The existing `touchTarget` token is 44dp; it records the implementation, not a blanket accessibility certification. Small type, enlarged system fonts, long labels, and effective touch targets require checking on the affected surface when it changes.

### Semantic surfaces and states

Use semantic roles rather than selecting a raw surface rung independently in every component:

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| `field` | `#ffffff` | `#1c1917` | Editable field surface |
| `card` | `#f2f4f4` | `#1c1917` | Grouped content |
| `raised` | `#ffffff` | `#292524` | Raised content |
| `selected` / `onSelected` | `#e2e2e5` / `#454749` | `#454649` / `#e2e2e5` | Neutral selected surface, including the Shortcut Bar |
| `disabledContainer` / `disabledContent` | `#e1e4e5` / `#5a6061` | `#34302e` / `#a8a29e` | Disabled controls |
| `plannedBlock` / `plannedBlockRaised` | `#e9f1fd` / `#e2edfc` | `#14213a` / `#1b2c4b` | Normal / selected or dragged Planned Block |
| `actualBlock` / `actualBlockRaised` | `#def1ee` / `#cbe8e3` | `#0f2724` / `#143430` | Normal / selected or dragged Actual Block |

The Shortcut Bar's neutral selected background is an established component-specific treatment; its active content and semantics still identify selection. Android's canvas uses `bg` (`#f9f9f9` light, `#0c0a09` dark); do not substitute the web `dark-surface` value. Theme roles also map to Material controls. Compatibility `tertiary` fields do not authorize a new product color meaning.

### Home and Shortcut Bar

Home is the launch destination and full-page directory. Group destinations under Daily work (Day, Battle Plan), Chronicle (Calendar, Trends), Routines and goals (Habits, Recurring Tasks, Time Goals), Assistant (Conversation, Review), Organization (Task Types), and Preferences (Settings and access to Edit shortcuts). Group headings are not destinations. Retain useful contextual links without recreating duplicate sibling tabs.

The Shortcut Bar is a centered, floating, rounded tonal island with icons above labels and a full selected-item background. It contains only saved shortcuts, wraps a small selection, and scrolls beyond four visible items. Four is a visible-slot limit, not a saved-shortcut limit. Day and Battle Plan are initial defaults; an explicitly empty list hides the bar. There is no Navigate or Add shortcut item in the live bar. Editing lives in Home; Save persists the ordered list on this installation, while Cancel and Back discard the draft.

Direct destinations have a back arrow immediately left of the title and return to preserved Home. Home has no back arrow or Close control. Contextual links and details return to their source. Destination changes preserve current-session date, filters, scroll position, and drafts without accumulating top-level Back history. Day resumes its selected date rather than jumping to Today. Full restoration after process death is outside this contract.

Hide global navigation while editing, with the keyboard open, in Focus Mode, and on detail screens. Leaving Focus Mode does not stop Activity Tracking. Scroll content may continue behind the floating bar, but trailing clearance must let the final item scroll above it; fixed actions and feedback remain reachable above it. See [the navigation ADR](docs/adr/0020-full-page-mobile-navigation.md) for the current contract; older Navigate-overlay prototype notes are superseded.

### Day sheets and dialogs

Day sheets and their confirmation dialogs share one frame. A lane-colored kicker and dot sit above a light Manrope title; block sheets use the time range as the title with duration on the right. Sheets open fully expanded on an **opaque** surface, with a scrolling body and pinned footer. Cancel (or a view-only secondary action) sits left; the primary sits right and names its result ("Stop at 11:12") or disabled reason ("No changes").

There is no separate visible Close button. Swipe, Back, scrim, and Cancel follow the sheet's dismissal/discard contract; busy work prevents dismissal. View-only sheets retain an accessible Close action. Keyboard insets shrink the available body instead of covering the footer. Related time uses the other lane's tint, or amber when replacing recorded time. Red is reserved for a destructive primary. This frame is specific to Day; do not automatically impose lane kickers on unrelated task or settings sheets. See [TimeboxSheet](android/app/src/main/java/com/timebox/android/ui/components/TimeboxSheet.kt).

### Motion

Ordered shortcut navigation slides the full page at 250ms using `FastOutSlowInEasing`; direction follows saved shortcut order and the dock stays fixed. Selecting the current shortcut does nothing. Other destination transitions currently fade at 220ms. Android Remove animations skips these transitions. These are tap-triggered transitions, not swipe navigation. The former Navigate overlay's rise/reveal animation is historical and must not be reintroduced as Home behavior. See [TimeboxNavHost](android/app/src/main/java/com/timebox/android/ui/TimeboxNavHost.kt).

## Component contracts

Keep detailed behavior in these focused documents; this overview establishes their place in the system:

| Family | Established direction | Contract |
| --- | --- | --- |
| Transient feedback and Undo | Floating paper, neutral raised surface, 24px/dp corners, tonal action, meaningful Undo/Retry labels; navigation clearance and accessible exposure timing | [Transient feedback](docs/design/transient-feedback.md) |
| Assistant read cards | Snapshot context remains visible; cards represent what was read, not live state | [Assistant Cards](docs/design/assistant-cards-296.md) |
| Android Assistant task cards | Task title leads; secondary metadata expands; proposals expose material changes before confirmation and results identify saved outcomes | [Assistant card polish](docs/design/assistant-card-polish.md) |
| Short timeline blocks and zoom | Duration controls geometry; details support precise edits; scale controls live in View | [Short blocks](docs/design/short-blocks-170-167.md), [Zoom revision](docs/design/remove-zoom-view-241.md) |
| Android navigation | Home directory, optional Shortcut Bar, preserved session state, explicit return behavior | [Navigation ADR](docs/adr/0020-full-page-mobile-navigation.md) |

## Do's and Don'ts

- Do use appropriate spacing, tonal surfaces, and light Manrope headings to create calm hierarchy at each platform's density.
- Do color a control by what it changes: red deletes, amber replaces recorded time, teal records or starts something real, blue is the main action or selection, and everything else is neutral.
- Do keep teal for real time and finished things only, so it reads as "done".
- Do limit ambient shadows and optional web backdrop blur to floating surfaces; keep Android Day sheets opaque.
- Do keep dark mode charcoal, warm, and restrained.
- Don't give an area more than one strong blue; planned items use the blue tint and outline, not a solid fill.
- Don't use teal on generic buttons or selection, or as a lasting row background.
- Reserve red for destructive actions and errors, except for the Now Line and its leading dot (the explicit current-time indicator exception).
- Don't use thick borders or heavy shadows for sectioning.
- Don't over-bold headings; scale and spacing should do that work.
- Don't introduce playful colors, dense dashboard widgets, or decorative illustration.
- Don't make timeline blocks visually busier than the grid they live inside.
- Don't apply desktop CSS measurements to Android or reinstate superseded navigation and Zoom controls from prototype notes.
