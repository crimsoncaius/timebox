---
target: Every Android main screen with Navigation Shelf open, including overlapping half-screen scroll positions
total_score: 25
max_score: 32
na_heuristics: 9,10
p0_count: 0
p1_count: 0
target_identity: "file:C:\\Users\\Caius\\.codex\\worktrees\\9821\\timebox\\android\\app\\src\\main\\java\\com\\timebox\\android\\ui\\components\\NavigationShelf.kt"
target_fingerprint: "sha256:bd57874bb0f3e85467e9525d7fd141fad98817f4168bfec945cd4086cb13c3d4"
target_path: "C:\\Users\\Caius\\.codex\\worktrees\\9821\\timebox\\android\\app\\src\\main\\java\\com\\timebox\\android\\ui\\components\\NavigationShelf.kt"
timestamp: 2026-10-29T04-58-43Z
slug: android-ui-components-navigationshelf-kt-31a8ff72
---
Method: dual-agent (A: /root/shelf_design_review · B: /root/shelf_evidence_review)

The shelf is consistent across all 11 main screens, but it still looks like a full-page menu compressed into a sidebar. The main aesthetic improvements are lighter framing, a smaller heading, and a clearer edge beside the current page.

Inspected all 11 shelf states and overlapping top/bottom scroll captures on the existing Android emulator. This covers light mode and the current font size; populated, error, dark-mode, and enlarged-text states were not comprehensively assessed.

## Priority issues

1. **P2 — Too much framing.** Rounded group cards, outer borders, internal dividers, selected-row fill, the Current pill, and chevrons compete with simple destination labels. Organization is especially overframed for its single Task Types entry. Remove the outer group borders first, retain the headings and quiet fills, and remove the separate background behind Current while keeping its text. Suggested command: `$impeccable quieter`.
2. **P2 — Weak boundary between shelf and page.** Identical backgrounds make blank page strips merge with the shelf; busier strips introduce disconnected fragments of lines and cards. Add one restrained vertical hairline at the page's leading edge. Preserve the approved width, crop, and movement. Suggested command: `$impeccable polish`.
3. **P3 — Oversized fixed Navigation heading.** The 34sp display heading and 20dp vertical padding above and below feel like a landing-page title. They contribute to pushing Task Types, Settings, and Edit shortcuts below the first viewport. Try the existing 27sp screen-title scale and 12dp vertical padding, preserving 52dp destination targets. This need not make every item fit without scrolling. Suggested command: `$impeccable distill`.
4. **P3 — Extra divider above selected Settings.** Its divider remains directly beside the filled selected row, unlike selected rows elsewhere. Apply the same selected-neighbor divider rule to the Edit shortcuts/Settings boundary. Suggested command: `$impeccable polish`.

No P0 or P1 issues were observed. No destination labels wrapped or clipped at this captured size; Recurring Tasks fits beside Current.

## Screen-by-screen

| Screen | Aesthetic observation with shelf open |
|---|---|
| Day | Clear selection. Date, tracking color, and timeline fragments make the page edge visually busy; a seam would clarify it. |
| Battle Plan | Clean selection; its sparse page strip makes the missing boundary particularly apparent. |
| Calendar | Calendar fragments provide context; shared framing and boundary issues apply. |
| Trends | Selection remains legible beside the blue control/large-number fragments; no additional defect observed. |
| Habits | Bright blue page-action fragment attracts attention away from the muted shelf; a clearer edge would separate the two surfaces. |
| Recurring Tasks | Long label fits. The selected fill plus Current pill plus chevron is more elaborate than necessary. |
| Time Goals | Selected row reads clearly; no additional screen-specific defect observed. |
| Conversation | Mostly blank page strip merges with the shelf background. The composer fragment is intentional cropping. |
| Review | Selected row stays fully visible near the bottom; no system-bar overlap observed. |
| Task Types | A full outlined Organization card for one destination creates disproportionate visual weight. |
| Settings | Extra divider above the selected row; retained settings cards repeat the shelf's heavy framing. |

The lower menu is reachable and has sufficient bottom clearance. Top and bottom captures overlap because the overflow is shorter than half a screen. A partially clipped card during scrolling is normal, not a layout defect.

## What works

Timebox's restrained palette and typography remain coherent. Destination labels, icons, and chevrons align consistently. Current text plus a tonal row communicates selection without relying on color. The exposed page strip preserves context and should remain.

## Design health

| Heuristic | Score / 4 | Finding |
|---|---:|---|
| Status visibility | 3 | Clear Current indicator when its row is visible. |
| Familiar language | 3 | Mostly familiar labels; some established product terminology. |
| User control | 3 | Back, swipe, and tap-strip dismissal; visual exit cue is indirect. |
| Consistency | 4 | Consistent overall anatomy across all 11 screens; minor Settings-divider exception. |
| Error prevention | 3 | Underlying page is inert; generous row targets. |
| Recognition | 3 | Labeled groups; lower utilities need scrolling. |
| Efficiency | 3 | Shortcuts and multiple dismissal routes; long directory. |
| Aesthetic restraint | 3 | Calm palette, excessive framing. |
| Error recovery | n/a | No failed operation assessed. |
| Help/documentation | n/a | No help surface assessed. |
| Total | 25/32 | Good; refinement rather than redesign. |

These are visual/source-review judgments, not an accessibility or performance certification.

## Cognitive load and user experience

Roughly nine destinations appear at the top, but groups of one to three make them scannable. Keep the approved expanded groups. Repeated outlines create more visual work than the choices themselves. The retained page makes navigation feel safe; the minor interruption is scrolling through a long, equally weighted directory to reach utilities.

For a first-time user, Settings is initially unseen and tapping the page strip to close the shelf is only indirectly signaled. For a distracted mobile user, generous targets help, but repeated frames slow scanning. These observations support lighter framing, not smaller touch targets.

## Automated evidence

The detector ran once against NavigationShelf.kt, NavigationDirectory.kt, and Chrome.kt, exited successfully, and returned zero findings (`[]`). It reported no coverage metadata, so this does not establish Kotlin/Compose coverage. Native screenshots and source inspection support the findings. No browser overlay applies to this native screen.

## Follow-up choices

1. Visual direction: keep outlined groups but make them more compact, or use quieter groups without outer borders?
2. Scope: address all four issues, or only the heading and boundary first?
