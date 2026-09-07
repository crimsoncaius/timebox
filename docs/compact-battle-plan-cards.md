# Compact Battle Plan cards (#65)

Selected direction: prototype G, retaining the existing three-dot task actions menu. The user selected A's Todoist-inspired title and metadata hierarchy and G's hold-anywhere drag gesture. The two-line grip cue was removed after review.

The compact Android card shows an explicit completion control, up to two lines of title, and conditional Blocked, Subtask progress, and deadline signals. Overdue deadlines include text as well as color. Planning, priority, Project, and blocker explanations remain available in task details. The overflow menu retains the existing project move, blocking, reorder, and trash operations. Completion uses the shared task-completion coordinator and its Undo flow. Quota Tracker completion stays derived from Session Tasks.

Manual ordering retains the existing drag gesture, haptics, insertion feedback, edge scrolling, column switching, and settling animation. The lifted preview shares the resting card's summary. Neither the resting card nor its lifted preview shows a grip icon.

The native prototype study is archived on local branch `codex/prototype-65-task-cards`, commit `2d9e0a1`. Its debug route and switcher are removed from master. Phone and wide Android layouts use the selected status-paged board so the same card, overflow menu, and drag interactions remain available at either width.
