---
version: alpha
name: Timebox Monastic Archive
description: A calm, editorial design system for dual-lane timeboxing, balancing austere productivity with soft tonal depth.
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
  timeline-grid-strong: "#dadce0"
  timeline-grid-soft: "#e8eaed"
  timeline-label: "#5f6368"
  now-line: "#9f403d"
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
gradients:
  primary-action: "linear-gradient(135deg, #5d5e61 0%, #515255 100%)"
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
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
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
  timeline-block:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-surface}"
    typography: "{typography.timeline-block}"
    rounded: "{rounded.md}"
    padding: 6px
  timeline-block-selected:
    backgroundColor: "{colors.surface-container-lowest}"
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

Timebox should feel like a monastic archive for a day of work: quiet, precise, and intentionally sparse. The product is utilitarian, but the UI avoids the busyness of a dashboard by using large editorial headings, thin typography, warm off-white surfaces, and restrained gray-green neutrals. It should feel like a high-end paper planner translated into software.

The signature experience is the dual-lane timeline. Planned and actual time sit side by side as calm, gridded columns, with only a few clear accents: blue for planned intent, teal for actual record, and muted red for the current time or destructive states. Most hierarchy comes from space, tone, and typography rather than decoration.

## Colors

The palette is mostly warm neutral paper, graphite text, and desaturated control colors. Use `surface` as the page canvas, `surface-container-low` for grouped workspaces, and `surface-container-lowest` for focused cards, popovers, and editable panels. Avoid pure black in light mode and avoid saturated accents except where the timeline requires fast recognition.

Planned timeline elements use the blue family anchored by `planned`; actual timeline elements use the teal family anchored by `actual`. These accents should remain lane-specific and should not become general brand colors. `primary` is a subdued graphite used for primary actions, selections, and focus rings. `tertiary` is an equally quiet success/save color, and `error` is intentionally earthy rather than alarm-red.

Dark mode reverses the surface hierarchy without becoming neon. The background becomes deep charcoal, elevated regions move toward warmer stone grays, and planned/actual accents shift to brighter blue and mint so the timeline remains readable.

## Typography

Typography carries most of the brand personality. Use Manrope for display, headings, time labels, page titles, and navigation. Its light weights and tight tracking create the editorial, architectural tone. Use Inter for body copy, labels, dense controls, task type names, and timeline block text, where legibility matters more than drama.

Headings should be large and light, not bold. A 44px page title with tight tracking is the default page-level gesture. Small labels should be uppercase, letter-spaced, and restrained. Use weight sparingly: medium and semibold are for navigation state, lane labels, compact metadata, or button legibility, not for general emphasis.

## Layout

The desktop app uses a fixed left navigation rail with a spacious main canvas. Content should breathe: page sections commonly start with 48px padding, 32px to 64px vertical separation, and left-aligned editorial introductions. Favor intentional asymmetry over centered dashboard grids.

The timeline follows a precise 30-minute rhythm with a 46px slot height. Its time gutter is narrow and functional, while the planned and actual columns take the visual weight. Adjacent controls should stay compact so the grid remains the dominant object on the Day view.

Use a 4px base unit with 8px, 12px, 16px, 24px, 32px, and 48px as the practical spacing scale. Larger gaps are a feature, not waste: they reduce cognitive load and make schedule editing feel deliberate.

## Elevation & Depth

Depth is quiet and atmospheric. Do not use heavy drop shadows. Elevated cards, popovers, combobox menus, and mobile sheets use a soft 40px ambient shadow with very low opacity. On light surfaces this reads as a faint graphite halo; in dark mode it becomes a soft black glow.

Most hierarchy should come from tonal layers and translucency. Floating panels may use an 85% white or charcoal surface with a 20px backdrop blur. Hover and active states usually shift to a slightly higher surface tone instead of lifting with stronger shadows.

## Shapes

The shape language is soft-professional. Timeline blocks and utility controls use small radii so the grid remains crisp. Cards, calendar cells, panels, and popovers use larger radii to soften the otherwise austere layout. Pills and icon controls are fully rounded.

Avoid mixing sharp, square controls with highly rounded cards in the same local cluster. The system should feel measured: straight grid lines for time, rounded containers for decisions and details.

## Components

Navigation is quiet and textual: icons are thin outlined symbols, active state is signaled by a strong left rule and darker text, and inactive items stay muted until hover. The app identity uses uppercase Manrope with wide tracking and a small, soft tagline.

Primary buttons use the graphite action gradient defined in `gradients.primary-action`, with light text and a modest radius. Secondary and ghost buttons are tonal, often pill-shaped, and should change background tone on hover. Inputs are pale, rounded, and lightly inset; focus states use thin primary rings rather than saturated outlines.

Timeline lanes are the most domain-specific components. Planned and actual headers must preserve their blue and teal identity. Lane surfaces are very pale tints in light mode and near-black tinted panels in dark mode. Time blocks should remain compact, readable, draggable, and softly rounded. Selected blocks become whiter or more luminous and may use a subtle left accent or ring, but should not become visually loud.

Popovers, inspector panels, and combobox menus should feel like translucent paper placed over the canvas. They can use blur and ambient shadow, but their text and controls should remain plain and fast to scan.

## Do's and Don'ts

- Do use whitespace, tonal surfaces, and light Manrope headings to create calm hierarchy.
- Do keep planned blue and actual teal tied to the timeline lanes.
- Do use muted graphite primary actions instead of bright brand color.
- Do use ambient shadows and backdrop blur only for floating surfaces.
- Do keep dark mode charcoal, warm, and restrained.
- Don't use saturated blues for generic actions; blue belongs to planned time.
- Don't use thick borders or heavy shadows for sectioning.
- Don't over-bold headings; scale and spacing should do that work.
- Don't introduce playful colors, dense dashboard widgets, or decorative illustration.
- Don't make timeline blocks visually busier than the grid they live inside.
