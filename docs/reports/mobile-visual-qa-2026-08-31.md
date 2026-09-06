# Timebox Android visual and aesthetic QA

Date: 2026-08-31\
Build reviewed: `94b790d` plus the current unmodified working tree\
Target: native Kotlin/Compose Android app\
Device: Pixel 9a AVD, Android 16, 1080 × 2424 portrait\
Themes: light and dark\
Text scaling: default and 1.3× spot check

## Executive assessment

The app has a recognizable and appealing foundation: the editorial kicker/title treatment, restrained rounded geometry, dual-lane color language, and compact bottom navigation feel coherent and product-specific. Light mode is usable and generally polished, though it is visually washed out. Dark mode is not yet at the same quality bar.

The dark experience has one concrete rendering defect and two contrast failures that should be treated as release-blocking visual issues:

1. Task-detail card content inherits near-black text on dark surfaces, making key controls effectively unreadable.
2. The dark error/destructive red is below WCAG contrast for normal text and even misses the 3:1 non-text target on the raised dark surface.
3. Interactive out-of-month calendar dates fade to 1.78:1 in the Day month picker and about 1.9–2.0:1 in Chronicle.

Beyond those defects, the main aesthetic gap is depth. The dark palette uses several near-black values, but they are not consistently assigned to page, card, field, overlay, and selected states. Some screens therefore collapse into a single black plane while Material components occasionally introduce bright white actions or purple-gray default containers. The result feels less like the intended “monastic archive” and more like a mix of a custom shell and default Material controls.

Overall judgment:

- Light mode: coherent and reviewable, with hierarchy and brand-polish opportunities.
- Dark mode: strong direction, but not visually production-ready until the high-priority defects are fixed.
- Responsive text: the tested 1.3× scale did not cause catastrophic overlap; the five-item bottom navigation still fit. Battle Plan tabs and Day header spacing are near their practical limit.

## Scope and method

I installed a fresh debug APK from the working tree and exercised the running app against the registered local Timebox API. I reviewed:

- Day in week and month modes
- Plan Mode
- new-block bottom sheet
- Chronicle
- Battle Plan task board
- task detail, including lower form sections
- Battle Plan filter sheet and scope menu
- Recurring list and recurring editor
- Task Types and the used-type deletion dialog
- Settings at the top and lower server section
- light/dark parity across all five primary tabs
- a 1.3× font-scale spot check on Day and Battle Plan

Evidence is stored under [`artifacts/visual-qa-2026-08-31`](../../artifacts/visual-qa-2026-08-31/).

Not assessed: landscape/tablet layouts, TalkBack interaction, animation timing, keyboard design supplied by the system IME, or a fully populated recurring dataset. Test-data wording such as `x` and `work work` was not treated as product copy.

## Severity model

- **High:** unreadable, inaccessible, or materially damages a primary workflow.
- **Medium:** systematic visual inconsistency or hierarchy problem that noticeably lowers product quality.
- **Low:** localized polish issue with limited workflow impact.

## Findings summary

| ID | Severity | Finding | Main surfaces |
| --- | --- | --- | --- |
| VQA-01 | High | Task-detail cards render near-black content in dark mode | Task detail |
| VQA-02 | High | Error/destructive red fails dark-mode contrast | Battle Plan, dialogs, Types |
| VQA-03 | High | Interactive adjacent-month dates nearly disappear | Day month picker, Chronicle |
| VQA-04 | Medium | Material color scheme is only partially mapped | menus, dialogs, fields, buttons |
| VQA-05 | Medium | Dark surface hierarchy collapses; light hierarchy is washed out | app-wide |
| VQA-06 | Medium | Primary actions are too bright and visually generic in dark mode | app-wide |
| VQA-07 | Medium | Long editors lack grouping and compact hierarchy | task detail, recurring editor |
| VQA-08 | Medium | Date selection overuses the Planned semantic color | Day, Chronicle |
| VQA-09 | Medium | Empty Plan Mode permanently sacrifices timeline width | Plan Mode |
| VQA-10 | Low | Intended typefaces are absent, reducing brand character | app-wide |
| VQA-11 | Low | Empty and disabled states can read as unfinished or broken | Recurring, Settings, Day |

## Detailed findings

### VQA-01 — Dark task-detail cards inherit black text

**Severity: High**

In the lower half of Task Details, `Planned Dates`, `Ready to Plan`, its supporting copy, and `Subtasks 0/0` render in near-black on `#1C1917`. The measured black/low-surface contrast is **1.20:1**. These labels are functionally unreadable.

Evidence: [dark task detail, lower section](../../artifacts/visual-qa-2026-08-31/dark-task-detail-lower.png)

The source uses `Modifier.background(TimeboxTheme.colors.low)` without establishing a corresponding content color, while several nested `Text` calls omit an explicit color:

- `BattlePlanScreen.kt:2077–2100`
- `BattlePlanScreen.kt:2117–2121`

This is a Compose theming problem rather than a palette preference. A plain `background` modifier does not update `LocalContentColor`.

Recommendation:

- Wrap these groups in `Surface(color = colors.low, contentColor = colors.on)` or set `color = colors.on` / `colors.onVariant` on every text and icon.
- Audit every custom `background(...)` container that includes Material text or controls.
- Add a dark-theme screenshot test for the full task-detail scroll range.

### VQA-02 — Dark error and destructive content is under-contrast

**Severity: High**

The same `#9F403D` error color is used in light and dark themes. Measured contrast:

- on dark page `#0C0A09`: **3.08:1**
- on dark raised surface `#1C1917`: **2.73:1**
- on light page `#F9F9F9`: **6.09:1**

This means `Blocked`, block reasons, `Move to Trash`, delete actions, and red trash icons are much weaker in dark mode than light. Normal-sized text fails 4.5:1; icons on the raised surface also miss the 3:1 non-text contrast target.

Evidence:

- [dark Battle Plan card](../../artifacts/visual-qa-2026-08-31/dark-battle-plan.png)
- [dark task detail lower section](../../artifacts/visual-qa-2026-08-31/dark-task-detail-lower.png)
- [dark Task Types deletion dialog](../../artifacts/visual-qa-2026-08-31/dark-types-delete-dialog.png)

The token is defined at `Colors.kt:98`.

Recommendation:

- Introduce a lighter dark error token rather than reusing the light value. A muted coral family can stay editorial while meeting 4.5:1.
- Define `onError`, `errorContainer`, and `onErrorContainer` in the Material scheme so filled and text error treatments remain coordinated.
- Recheck text, icons, borders, and disabled destructive controls separately.

### VQA-03 — Adjacent-month dates are interactive but nearly invisible

**Severity: High**

Out-of-month dates in the Day month picker use `onVariant` at 32% alpha. Composite contrast is approximately:

- dark: **1.78:1**
- light: **1.60:1**

Chronicle uses `outlineVariant`, which is **1.92:1** on dark and about **2.02:1** on light. These dates are still clickable and open a day, so their visual treatment resembles disabled content even though they are active controls.

Evidence:

- [dark Day month picker](../../artifacts/visual-qa-2026-08-31/dark-day-month.png)
- [dark Chronicle](../../artifacts/visual-qa-2026-08-31/dark-chronicle.png)
- [light Chronicle](../../artifacts/visual-qa-2026-08-31/light-chronicle.png)

Relevant source:

- `DayCalendarHeader.kt:514–520`
- `ChronicleScreen.kt:336–343`

Recommendation:

- Keep adjacent-month dates visually subordinate without treating them like disabled UI; target at least 4.5:1 for the numerals.
- Use hierarchy through lighter font weight, no cell fill, or a subtle separator—not extreme opacity reduction.
- Make today, selected date, archived day, and adjacent-month day four clearly distinct but readable states.

### VQA-04 — Partial Material mapping leaks a second design system

**Severity: Medium**

`TimeboxTheme` maps only a subset of Material 3 roles. Container roles such as `surfaceContainer`, `surfaceContainerHigh`, `primaryContainer`, `onPrimaryContainer`, and error-container roles are left at Material defaults. This is visible in the Battle Plan scope menu, where section bands have a purple-gray cast that is absent from the warm charcoal Timebox palette.

Evidence: [dark Battle Plan scope menu](../../artifacts/visual-qa-2026-08-31/dark-battle-list-menu.png)

Relevant source: `Theme.kt:63–94`.

Recommendation:

- Map the complete Material 3 surface ladder and all foreground pairs from Timebox tokens.
- Add a small component gallery covering DropdownMenu, AlertDialog, OutlinedTextField, Switch, Button, disabled Button, Tabs, and BottomSheet in both themes.
- Treat any unassigned Material role as a regression risk; default M3 neutrals are not neutral relative to this palette.

### VQA-05 — Surface depth is compressed in dark and washed out in light

**Severity: Medium**

Dark mode sets both `bg` and `lowest` to `#0C0A09`. Page backgrounds, field interiors, and some menu rows therefore merge. `low` (`#1C1917`) is only subtly distinct, and many custom groups have no border or shadow. The visual result is a large black plane with isolated outlines rather than a deliberate depth stack.

Light mode has the inverse problem: `#F9F9F9`, `#F2F4F4`, and very pale container fills are so close that cards and groups can feel washed out.

Evidence:

- [dark task detail](../../artifacts/visual-qa-2026-08-31/dark-task-detail.png)
- [dark Settings](../../artifacts/visual-qa-2026-08-31/dark-settings.png)
- [light Settings](../../artifacts/visual-qa-2026-08-31/light-settings.png)
- [light Battle Plan](../../artifacts/visual-qa-2026-08-31/light-battle-plan.png)

Relevant dark tokens: `Colors.kt:78–87`.

Recommendation:

- Define an explicit elevation ladder for page, embedded field, card, raised card, sheet, and selected surface.
- Separate fields from page backgrounds without relying only on outlines.
- Increase light-mode card separation slightly through surface tone or a consistent hairline, not heavy shadows.
- Preserve the warm neutral direction; the issue is assignment and spacing, not a need for more color.

### VQA-06 — Primary actions are too bright and generic in dark mode

**Severity: Medium**

Dark-mode primary buttons such as `New task`, `New recurrence`, `Done`, and `Enable notifications` are nearly pure white pills with black text. They dominate every screen and feel closer to default Material than the restrained design specification. Selected chips also use the same treatment, which flattens the distinction between “current selection” and “primary action.”

The design source specifies a neutral gray primary action (`#5D5E61`), while Android maps `primary = colors.on` and `onPrimary = colors.bg` (`Theme.kt:66–68`).

Evidence:

- [dark Battle Plan](../../artifacts/visual-qa-2026-08-31/dark-battle-plan.png)
- [dark Plan Mode](../../artifacts/visual-qa-2026-08-31/dark-plan-mode.png)
- [dark Settings](../../artifacts/visual-qa-2026-08-31/dark-settings.png)

Recommendation:

- Add first-class `primary` and `onPrimary` Timebox tokens rather than deriving them from general foreground/background.
- Reserve the highest-contrast treatment for the single most consequential action.
- Give selected filters/chips a quieter selected surface and use text/outline changes to communicate state.

### VQA-07 — Editors are long, sparse, and weakly grouped

**Severity: Medium**

Task Details and New Recurrence use a long sequence of full-width fields and naked label/value pairs. Important relationships—status/project/type, urgency/importance, deadline/reminder, recurrence mode/period/end, and preview/save—are not grouped into meaningful sections. Users must scroll through several screens, and the large vertical gaps make the forms feel unfinished rather than calm.

New Recurrence also repeats two large headings: the shell title `New recurrence` and the in-content title `New recurring template`.

Evidence:

- [dark task detail](../../artifacts/visual-qa-2026-08-31/dark-task-detail.png)
- [dark recurring editor](../../artifacts/visual-qa-2026-08-31/dark-recurring-editor.png)
- [dark recurring editor lower section](../../artifacts/visual-qa-2026-08-31/dark-recurring-editor-lower.png)

Recommendation:

- Use the Settings screen as the model: titled surface groups, compact rows, supporting copy only where needed.
- Remove the redundant in-content screen title.
- Group related selections into compact two-column or row-based controls where touch targets allow.
- Consider a sticky bottom save action so completion is always visible without adding another dominant white pill mid-flow.

### VQA-08 — Date selection consumes the Planned semantic color

**Severity: Medium**

Selected dates use the full Planned blue fill in both week and month views. On dark mode, the pale blue pill becomes the brightest object on the screen, competing with the actual planning controls. It also overloads a semantic color that otherwise means Planned Block/lane.

Relevant source: `DayCalendarHeader.kt:509–520`.

Evidence:

- [dark Day week view](../../artifacts/visual-qa-2026-08-31/dark-day.png)
- [dark Day month view](../../artifacts/visual-qa-2026-08-31/dark-day-month.png)
- [light Day](../../artifacts/visual-qa-2026-08-31/light-day.png)

Recommendation:

- Use a neutral selected surface with a Planned-color outline or numeral for navigation state.
- Reserve solid blue for planned-work objects and the most direct planning action.
- Differentiate today from selected date without relying on the same accent treatment.

### VQA-09 — Empty Plan Mode wastes the task column

**Severity: Medium**

When there are no Ready to Plan tasks, the right column remains a tall bordered panel occupying roughly one third of the work area. The copy wraps awkwardly (`Nothing waiting to / be planned.`) while the Planned timeline loses width for no functional benefit.

Evidence: [dark Plan Mode](../../artifacts/visual-qa-2026-08-31/dark-plan-mode.png)

Recommendation:

- Collapse the empty queue into a compact banner or empty-state row above the timeline.
- Expand the queue only when tasks exist, or reveal it as a sheet/drawer on demand.
- Keep the two-column planning interaction for populated states, where the relationship is useful.

### VQA-10 — The intended typography is not shipped

**Severity: Low**

The design calls for Manrope headlines and Inter body text, but both currently fall back to the platform sans (`Type.kt:10–25`). The size, weight, and tracking still create a recognizable hierarchy, but the product loses some of the refined editorial character visible in the design source.

Recommendation:

- Bundle the intended font files or a carefully subsetted variable-font build.
- Recheck title width, tab labels, and the five-item bottom nav after changing font metrics.
- Preserve the tested 1.3× text-scale fit as an acceptance criterion.

### VQA-11 — Empty and disabled states can read as broken

**Severity: Low**

Several states use very low-contrast content with little explanation:

- the disabled Today button nearly disappears when already on today;
- disabled Save/Create buttons use dark text on a mid-gray surface and can resemble a rendering failure;
- the Recurring empty message sits far from the tabs and action, creating an unfinished-feeling void;
- empty Day/Plan timelines provide no gentle orientation or affordance hint.

Evidence:

- [dark Recurring empty state](../../artifacts/visual-qa-2026-08-31/dark-recurring.png)
- [dark recurring editor lower section](../../artifacts/visual-qa-2026-08-31/dark-recurring-editor-lower.png)
- [dark Day](../../artifacts/visual-qa-2026-08-31/dark-day.png)

Recommendation:

- Keep disabled controls visibly present while clearly unavailable.
- Pull empty-state copy closer to the control that resolves it.
- Add concise, non-decorative guidance where the first action is otherwise unclear.

## Screen-by-screen notes

### Day

What works:

- Planned and Actual lanes are immediately distinguishable without excessive saturation.
- The editorial date header and compact mode controls give the app identity.
- Timeline labels remain readable in both themes.

Improve:

- reduce the selected-date fill dominance;
- raise dark lane/grid separation slightly without making the timeline busy;
- make the empty timeline offer a subtle first-action cue;
- ensure 1.3× title text has a little more reserved width before the two top-right actions.

### Chronicle

What works:

- Month navigation is simple and the archive grid is easy to scan in light mode.
- The current-day outline is clear.

Improve:

- adjacent-month date contrast;
- stronger distinction between opened-empty and unopened days in dark mode;
- bring the explanatory line closer to the grid and reduce the unused lower space.

### Battle Plan

What works:

- Card hierarchy is understandable: title, project, condition, reason, planned date.
- Status tabs and filtering are discoverable.
- The floating New Task action is well positioned.

Improve:

- dark error contrast;
- menu surface colors;
- slightly stronger secondary metadata in dark cards;
- make the filter sheet feel more connected to the board rather than a full visual blackout.

### Task detail

What works:

- Back navigation and editability are obvious.
- Selection values are readable and touch-friendly.

Improve:

- fix inherited content color immediately;
- group the form;
- keep save state visible;
- reduce the large gaps between label/value selections.

### Recurring

What works:

- Active/Paused/Ended tabs are clear.
- The server preview card is a useful confidence-building element.

Improve:

- tighten the empty-state composition;
- remove redundant editor headings;
- group recurrence setup into Definition, Schedule, End, and Preview sections;
- quiet selected chips and primary actions.

### Task Types

What works:

- The list is compact and easy to scan.
- Usage counts and destructive affordances are visible.
- The deletion dialog communicates the consequence before action.

Improve:

- raise placeholder and destructive-icon contrast in dark mode;
- make “Choose migration target” look like a field or dropdown rather than a bold text command;
- keep dialog action order and emphasis consistent with other destructive dialogs.

### Settings

What works:

- This is the strongest information architecture in the app.
- Groups, row labels, supporting copy, steppers, switches, and notification actions are easy to parse.
- It demonstrates the calm card-based hierarchy other long forms should reuse.

Improve:

- avoid pure-white primary actions in dark mode;
- strengthen the Server field/background distinction;
- ensure disabled Save Connection styling cannot be mistaken for black-on-gray color leakage.

## Contrast measurements

| Pair | Ratio | Assessment |
| --- | ---: | --- |
| dark `on` / `bg` | 18.11:1 | Excellent |
| dark `onVariant` / `bg` | 7.83:1 | Pass |
| dark `outline` / `bg` | 4.12:1 | Pass for non-text |
| dark `error` / `bg` | 3.08:1 | Fail for normal text |
| dark `error` / `low` | 2.73:1 | Fail for normal text and non-text |
| inherited black / dark `low` | 1.20:1 | Severe failure |
| dark adjacent-month date / `bg` | 1.78:1 | Severe failure |
| dark `outlineVariant` / `bg` | 1.92:1 | Fail for interactive date text |
| light `onVariant` / `bg` | 6.08:1 | Pass |
| light `outlineVariant` / `bg` | 2.02:1 | Fail for interactive date text |
| light `error` / `bg` | 6.09:1 | Pass |
| dark Planned / `bg` | 9.37:1 | Pass |
| dark Actual / `bg` | 11.31:1 | Pass |

## Recommended remediation order

### Pass 1 — correctness and accessibility

1. Fix inherited content colors in all custom dark containers.
2. Introduce dark-specific error and error-container tokens.
3. Raise adjacent-month date contrast in Day and Chronicle.
4. Complete the Material 3 color-role mapping.
5. Add dark-theme screenshot coverage for Task Details, menus, dialogs, and calendar states.

### Pass 2 — hierarchy and product character

1. Establish a deliberate surface/elevation ladder in both themes.
2. Add dedicated primary-action tokens and quiet selected-chip styling.
3. Redesign Task Details and Recurring Editor around grouped sections.
4. Decouple selected-date styling from the Planned semantic fill.
5. Collapse the empty Plan Mode task column.

### Pass 3 — polish

1. Bundle Manrope and Inter and recheck text fit.
2. Normalize empty-state placement and disabled-state treatments.
3. Refine light-mode separation without introducing heavy shadows.
4. Build a small in-app/theme-preview component gallery for future visual QA.

## Acceptance criteria for a follow-up visual pass

- No text or icon inside a dark custom container inherits black/default content color.
- Error/destructive normal text reaches at least 4.5:1 on every surface where it appears.
- All interactive calendar numerals reach at least 4.5:1 in both themes.
- Dropdowns, dialogs, fields, sheets, switches, tabs, and buttons use only Timebox-mapped colors.
- Each screen has one visually dominant action at most.
- Task and recurrence editors expose clear semantic sections and do not repeat the screen title.
- Day and Battle Plan remain legible at 1.3× text scale on the tested Pixel 9a viewport.
- Light and dark screenshots are regenerated for all reviewed states.
