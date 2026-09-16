# Path-aware Task Type matching on a repaired query

While typing, suggestions use the web path-aware matcher (segment alignment, ancestor/descendant/prefix). The query is canonicalized with Android's forgiving rules: trim, lowercase, collapse inner whitespace, drop empty segments. `coding//ai` becomes `coding/ai`; a trailing slash collapses to the parent (`coding/` → `coding`). Matching and create-on-commit run on that repaired path.

Web's reject-empty-segments rule was too brittle while typing. Android's slashed-query matcher was too coarse: a new path could hide nearby existing types. Clients POST only repaired paths, so the server may keep rejecting empty segments.
