# Home navigation

Status: agreed on 2026-10-26; implementation authorized.

## Confirmed decisions

- Home replaces the Navigate button as the central entry point to screens.
- Other screens have an arrow immediately to the left of their title to return Home.
- A fresh app launch starts on Home.
- Returning Home and reopening a screen preserves its state, including selected date, filters, and scroll position.

## Navigation rules

- Android only; web navigation is unchanged.
- Home reuses the existing grouped destination directory, with the title Home and no Close control.
- Keep the customizable shortcut dock and Add shortcut; remove its Navigate item.
- Header arrows on main destinations return Home. Details, editors, contextual visits, and Focus Mode retain their existing return behavior.
- Android system Back follows the same hierarchy and uses normal exit behavior from Home.
- Destination state is preserved within the current session. Full restoration after process termination is outside this change.

See the revised [navigation ADR](../adr/0020-full-page-mobile-navigation.md).
