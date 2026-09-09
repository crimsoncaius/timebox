# Project selection motion — issue #113

The user selected prototype B: fade new board content from 20% to full opacity
over 170 ms. Close the project menu and apply the new scope immediately. Keep
the header, status tabs, and New task button steady, and keep content interactive
throughout. Do not animate initial display, repeated selection of the same scope,
task updates, or status changes. Respect Android's animator duration scale.

The three-way browser comparison and the selection verdict are preserved on the
local throwaway branch `codex/issue-113-motion-prototype`, commit
`c3c3c3dba449277b5985c3e85fa5f44c5c98a2c4`.
Run `npm run dev` in that branch's frontend and open
`/battle-plan?prototype=113&variant=B` to revisit the primary source.
