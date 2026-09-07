# Web Frontend Audit Test Plan

## Application Overview

Timebox's desktop web frontend provides the Day timeline, Chronicle, Battle Plan, recurring-task administration, Task types, Settings, and Work Mode. This audit protects desktop readability and verifies that light/dark theming follows both the user's system preference and any explicit saved override.

## Test Scenarios

### 1. Theme behavior

**Seed:** `e2e/seed.spec.ts`

#### 1.1. system-dark-preference

**File:** `e2e/theme/system-dark-preference.spec.ts`

**Steps:**
  1. Open Timebox with no saved theme while the browser prefers dark colors.
    - expect: The root document uses the dark theme before the page is presented.
    - expect: The theme control offers to switch to light mode.
    - expect: The page canvas uses the dark design-system background.
  2. Reload the page without saving an explicit preference.
    - expect: Dark mode remains active.
  3. Change the browser preference to light without saving an explicit preference.
    - expect: Timebox follows the updated system preference.

#### 1.2. system-light-preference

**File:** `e2e/theme/system-light-preference.spec.ts`

**Steps:**
  1. Open Timebox with no saved theme while the browser prefers light colors.
    - expect: The root document uses the light theme.
    - expect: The theme control offers to switch to dark mode.

#### 1.3. manual-theme-persists

**File:** `e2e/theme/manual-theme-persists.spec.ts`

**Steps:**
  1. Open Timebox in light mode and choose dark mode.
    - expect: The root document switches to dark mode.
    - expect: The explicit dark preference is saved.
  2. Navigate through Day, Chronicle, Battle Plan, Task types, and Settings, then reload.
    - expect: Every route remains dark.
    - expect: The theme control continues to offer light mode.

### 2. Desktop layout

**Seed:** `e2e/seed.spec.ts`

#### 2.1. battle-plan-board-remains-readable

**File:** `e2e/desktop/battle-plan-board-remains-readable.spec.ts`

**Steps:**
  1. Open Battle Plan at a 1280 by 900 desktop viewport with a normal task card.
    - expect: Every workflow lane has a readable minimum width of 240 pixels.
    - expect: The board can scroll horizontally inside its own workspace when all four lanes do not fit.
    - expect: The application shell itself does not overflow the viewport.
  2. Use the task card controls in the first lane.
    - expect: The subtask control is visible and clickable.

#### 2.2. desktop-core-routes

**File:** `e2e/desktop/desktop-core-routes.spec.ts`

**Steps:**
  1. Visit Day, Chronicle, Battle Plan, recurring-task administration, Task types, and Settings at 1280 and 1440 pixel widths.
    - expect: The primary heading and navigation remain visible.
    - expect: No uncaught page errors occur.
    - expect: The document does not develop unintended horizontal overflow.

#### 2.3. day-timeline-remains-readable

**File:** `e2e/desktop/day-timeline-remains-readable.spec.ts`

**Steps:**
  1. Open Day at a 1024 by 900 desktop viewport.
    - expect: The Planned lane remains at least 240 pixels wide.
    - expect: Ready to Plan appears above the timeline instead of consuming a narrow side rail.
    - expect: A large Ready to Plan queue scrolls within a bounded panel instead of pushing the timeline several screens down.
    - expect: The persistent desktop inspector is deferred until a wider viewport.

### 3. Existing core workflows

**Seed:** `e2e/seed.spec.ts`

#### 3.1. existing-browser-suite

**File:** `e2e/timebox.spec.ts`

**Steps:**
  1. Run the existing Playwright suite against the isolated API and database.
    - expect: Day planning, actual-time recording, Work Mode, task types, recurrence, and Battle Plan workflows pass.

#### 3.2. component-suite

**File:** `src/App.test.tsx`

**Steps:**
  1. Run the frontend unit and component suite.
    - expect: Theme, navigation, responsive layout, and feature component behavior pass.
