# Keep Projects as named task groups

Projects group related Battle Plan Tasks; their descriptions added editor overhead without a planning role, and their deadlines had no distinct scheduling behavior. Issue #79 removes both fields across web, Android, the API, and storage in one coordinated change, deliberately discarding existing Project metadata and dropping compatibility with older clients rather than retaining unused fields. Individual Battle Plan Task descriptions and deadlines remain intact; a database downgrade restores only the removed column definitions, not discarded values.
