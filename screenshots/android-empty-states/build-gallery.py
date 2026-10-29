from pathlib import Path
import html, zipfile
root = Path(__file__).resolve().parent
titles = {
'01-battle-plan-open':'Battle Plan · Open', '02-battle-plan-in-progress':'Battle Plan · In Progress',
'03-battle-plan-completed':'Battle Plan · Completed', '04-filter-no-task-types':'Filters · No Task Types',
'05-archive':'Battle Plan · Archive', '06-trash':'Battle Plan · Trash', '07-habits':'Habits · Empty week',
'08-habits-no-candidates':'Add habit · No available routines', '09-recurring-active':'Recurring Tasks · Active',
'10-recurring-paused':'Recurring Tasks · Paused', '11-recurring-ended':'Recurring Tasks · Ended',
'12-time-goals':'Time Goals · Empty week', '13-trends':'Trends · No recorded time', '14-calendar':'Calendar · No activity',
'15-conversation':'Conversation · New conversation', '16-review-pending':'Review · To review',
'17-review-reviewed':'Review · Reviewed', '18-task-types':'Task Types · Empty library',
'19-task-types-no-match':'Task Types · No search results', '20-time-zone-no-match':'Time zone · No search results',
'21-shortcuts':'Shortcuts · None selected', '22-goal-no-recorded-time':'Time Goal · No recorded blocks',
'23-routine-no-work':'Routine · No current tasks', '24-routine-no-completions':'Routine · No completed tasks',
'25-filter-no-match':'Filters · No matching Task Type', '26-project-no-match':'Project picker · No matches',
'27-move-project-no-match':'Move to project · No projects', '28-calendar-none':'Google Calendar · No calendars available',
'29-calendar-unselected':'Google Calendar · No calendars selected',
'30-assistant-planned':'Assistant · No planned blocks', '31-assistant-actual':'Assistant · No actual blocks',
'32-assistant-both':'Assistant · No blocks in either lane', '33-assistant-types':'Assistant · No stored time',
'34-assistant-tasks':'Assistant · No matching Tasks', '35-assistant-partial':'Assistant · No rows in incomplete results',
'36-task-type-picker-empty':'Task Type picker · Empty library', '37-task-type-picker-no-match':'Task Type picker · No matches',
}
files = sorted(root.glob('*.png'))
reviews = [
    (1, 'Weak · highest priority', 'Battle Plan, Archive and Trash', [1, 2, 3, 5, 6],
     'Muted messages sit far below the controls. The creation action feels detached, and “Nothing here” does not explain Archive or Trash.',
     'Group a specific heading and explanation in a consistent position. Connect creation to Open; explain the other collections without forcing an action.'),
    (2, 'Weak', 'Conversation', [15],
     'The blank canvas offers no product-specific guidance. The generic composer placeholder carries the first-use experience.',
     'Add a short capability statement and two or three useful example prompts near the composer. Preserve the quiet canvas.'),
    (3, 'Weak', 'Habits and Add habit', [7, 8],
     'The empty weekday scaffold outweighs the explanation. Add habit opens a dead-end message when no candidates exist, including a fresh account.',
     'Give the absence a clear heading. Distinguish no routines from all routines already tracked, and offer the prerequisite next step.'),
    (4, 'Weak', 'Empty Task Type picker', [36],
     '“Unset” appears as both the field label and sole choice. Nothing visibly explains the empty library or how to create a type.',
     'Keep Unset as a choice, but add a search/create hint and a separate empty-library explanation.'),
    (5, 'Needs polish', 'Time Goals', [12],
     'The isolated information icon and large gaps fragment useful guidance. The add action reads quieter than comparable actions elsewhere.',
     'Place help beside its heading and tighten the title, explanation and action into one group. Check button semantics before changing colour.'),
    (6, 'Weak component', 'Google Calendar · no available calendars', [28],
     '“Select a calendar below” appears when there are no choices. Account metadata and an isolated help icon separate the empty message from recovery.',
     'Show selection guidance only when choices exist. Group the unavailable-calendar message with recovery. This finding concerns the production component, not the fixture shell.'),
    (7, 'Needs polish', 'Recurring Tasks · especially Paused and Ended', [9, 10, 11],
     'On inactive tabs, New recurrence dominates without explaining the selected collection. The zero count repeats the empty heading and “lifecycle changes” is vague. Active is comparatively clear.',
     'Use concrete tab-specific explanations, reduce redundant count emphasis, and keep creation secondary on Paused and Ended.'),
]
strengths = {
    18: 'Preserve the compact heading and explanation beside the relevant input.',
    19: 'Preserve the compact search-result explanation and concrete creation guidance.',
    16: 'Preserve the restrained, specific explanation of what will appear here.',
    17: 'Preserve the restrained, specific explanation of what will appear here.',
    26: 'Preserve recovery guidance close to the search context.',
    37: 'Preserve the concrete creation action beside the empty search result.',
    13: 'Preserve the real zero value and date-range context; a large first-use treatment would add little.',
    22: 'Preserve the zero value and period context within this useful detail view.',
}
review_by_number = {number: review for review in reviews for number in review[3]}
summary = '<section class="review-summary" aria-labelledby="review-heading"><h2 id="review-heading">Sub-agent visual review</h2><p>Ranked judgement of the current screenshots. Start with Battle Plan. Empty space alone is not a weakness; disconnected hierarchy and unclear next steps are.</p><ol>'
for rank, rating, label, numbers, problem, direction in reviews:
    target = next(p.stem for p in files if int(p.name[:2]) == numbers[0])
    summary += f'<li><a href="#{target}">{html.escape(label)}</a><span>{html.escape(rating)}</span></li>'
summary += '</ol><p><strong>Stronger examples:</strong> Task Types, Review, local search recovery, and reporting views with useful zero/range context. Green notes mark what to preserve. Unmarked screenshots are not flagged by this reviewer.</p><a href="visual-review.txt">Read the full review and evidence</a></section>'
cards=[]
for p in files:
    title=titles.get(p.stem,p.stem)
    number = int(p.name[:2])
    fixture=number in range(28,36)
    note = ''
    review = review_by_number.get(number)
    if review:
        rank, rating, label, numbers, problem, direction = review
        note = f'<aside class="critique"><strong>#{rank} · {html.escape(rating)}</strong><p>{html.escape(problem)}</p><p><b>Direction:</b> {html.escape(direction)}</p></aside>'
    elif number in strengths:
        note = f'<aside class="strength"><strong>Preserve</strong><p>{html.escape(strengths[number])}</p></aside>'
    cards.append(f'<article id="{p.stem}"><h2>{html.escape(title)}</h2><p>{"Production component · deterministic fixture" if fixture else "Live app · isolated test data"}</p>{note}<a href="{p.name}" target="_blank"><img loading="lazy" src="{p.name}" alt="{html.escape(title)}"></a><a class="download" href="{p.name}" download>Download PNG</a></article>')
page='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Android empty states</title><style>
body{margin:0;background:#111114;color:#eee;font:16px system-ui,sans-serif}header{padding:36px max(24px,5vw);max-width:1000px}h1{font-size:36px;margin:0 0 12px}p{color:#bbb;line-height:1.6}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:24px;padding:0 5vw 48px}article{background:#1d1d22;padding:16px;border-radius:12px}h2{font-size:18px;margin:0 0 6px}article p{font-size:12px;margin:0 0 14px}img{width:100%;height:auto;border-radius:6px}a{color:#a9caff}.download{display:block;padding-top:14px}header a{margin-right:20px}</style><header><h1>Android empty states</h1><p>COUNT captures · Dark theme · 1080 × 2424 · 29 October 2026</p><p>Day and Day-specific editors/planning views are excluded. Distinct tab, picker, search, nested detail, and response-card states are included. Repeated project names and date ranges using the same empty layout are represented once. These are actual Android screenshots, not mockups.</p><p>Main flows use the current app and an isolated database. Google Calendar and Assistant response cards use production components with deterministic empty fixtures; those images are labeled. Loading, errors, disabled actions, ordinary empty form fields, and unused prototype-only screens are outside this empty-content inventory.</p><a href="../android-empty-states.zip">Download all screenshots</a></header><main>CARDS</main></html>'''.replace('COUNT',str(len(files))).replace('CARDS',''.join(cards))
review_css = '''.review-summary{margin:0 5vw 32px;padding:24px;border:1px solid #49434e;border-radius:12px;background:#1d1d22;max-width:1000px}.review-summary h2{font-size:26px}.review-summary li{padding:8px 0}.review-summary li span{display:block;color:#e5b88a;font-size:13px;margin-top:4px}.critique,.strength{padding:14px;margin:0 0 16px;border-left:3px solid #e5b88a;background:#29231f;border-radius:4px}.critique strong{color:#ffd1a3}.strength{border-color:#9fceb5;background:#1e2b26}.strength strong{color:#b8e5cc}article aside p{font-size:14px;color:#e1dcd7;margin:10px 0 0}article{align-self:start;scroll-margin-top:20px}article:target{outline:2px solid #e5b88a}a:focus-visible{outline:2px solid #a9caff;outline-offset:4px}'''
page = page.replace('</style>', review_css + '</style>').replace('</header><main>', '</header>' + summary + '<main>')
comparisons=[]
for rank, rating, label, numbers, problem, direction in reviews:
    for number in numbers:
        p=next(p for p in files if int(p.name[:2]) == number)
        title=titles[p.stem]
        comparisons.append(f'''<section class="comparison" id="compare-{number}">
        <div class="comparison-heading"><h2>{html.escape(title)}</h2><p>{html.escape(direction)}</p></div>
        <div class="pair"><figure><figcaption>Before <span>Current Android capture</span></figcaption><a href="{p.name}" target="_blank"><img src="{p.name}" loading="lazy" alt="Before: {html.escape(title)}"></a></figure>
        <figure><figcaption>After <span class="variant-name">A · Compact and inline</span></figcaption><div class="device"><iframe loading="lazy" src="prototype/index.html?case={number}&amp;variant=A" data-case="{number}" title="Interactive prototype: {html.escape(title)}"></iframe></div><p class="preview-status" data-status="{number}" aria-live="polite">Initial empty state · no saved data</p><button class="reset-preview" data-reset="{number}">Reset this preview</button> · <a class="full-preview" data-preview="{number}" href="prototype/index.html?case={number}&amp;variant=A" target="_blank" rel="noopener">Open full-size preview</a></figure></div>
        <p class="comparison-note">{html.escape(problem)} <strong>Proposal only.</strong> The after panel is an HTML recreation using the app’s fonts and color tokens, not an Android screenshot. {'The before image also uses a production-component fixture.' if number==28 else ''}</p></section>''')
new_header='''<header><p class="prototype-label">TIMEBOX · DESIGN REVIEW</p><h1>Empty states, with a next step</h1><p>Before-and-after prototypes for the 14 flagged screens across seven groups. Day is excluded. Try the after panels: actions open local draft previews, example prompts fill the composer, and the Task Type field offers creation. Nothing is sent or saved.</p><div class="directions"><p><b>A · Compact and inline</b><br>Recommended starting point. Close to the controls, with the least added chrome.</p><p><b>B · Grouped panel</b><br>Contains the explanation and next step in a tonal surface.</p><p><b>C · Centered guidance</b><br>Gives the message more visual prominence and centers the action.</p></div><p>Switch directions with the bar below. All 37 original captures and the sub-agent critique remain available.</p><a href="visual-review.txt">Full critique</a><a href="#originals">Original screenshot gallery</a><a href="../android-empty-states.zip">Download the complete review</a></header>'''
start=page.index('<header>'); end=page.index('</header>')+len('</header>')
page=page[:start]+new_header+page[end:]
page=page.replace(summary, '<details class="review-details"><summary>Seven ranked findings from the independent review</summary>'+summary+'</details>')
page=page.replace('<main>', '<div id="comparisons">'+''.join(comparisons)+'</div><details id="originals"><summary>Original gallery · all 37 screenshots and annotations</summary><main>')
page=page.replace('</main>', '</main></details>')
extra_css='''.full-preview{font-size:12px;display:inline-block;padding:8px 0}html{scroll-behavior:smooth;scroll-padding-top:24px}body{padding-bottom:110px}header{max-width:1100px;margin:auto;padding:36px 24px 24px}.prototype-label{font-size:12px;letter-spacing:.12em;color:#b7b1ad}h1{font-size:clamp(28px,4vw,40px)}.directions{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}.directions p{font-size:14px}.directions b{color:#eee}.review-details{max-width:1052px;margin:0 auto 28px;padding:0 24px}summary{cursor:pointer;min-height:44px;padding:12px 0;color:#c6d9f6}.review-details .review-summary{margin:10px 0}.comparison{max-width:1100px;margin:0 auto 56px;padding:24px;border-top:1px solid #39363b;scroll-margin-top:16px}.comparison-heading{max-width:860px;margin-bottom:22px}.comparison-heading h2{font-size:25px}.comparison-heading p{font-size:15px;margin:10px 0 0}.pair{display:grid;grid-template-columns:1fr 1fr;gap:24px;max-width:850px;margin:auto}.pair figure{margin:0;min-width:0}.pair img{display:block;width:100%;border-radius:0}.pair figcaption{font-weight:650;padding:0 0 12px;font-size:16px}.pair figcaption span{display:block;color:#aaa;font-size:12px;font-weight:400;margin-top:4px}.device{position:relative;aspect-ratio:1080 / 2424;background:#0c0a09;overflow:hidden}.device iframe{border:0;position:absolute;inset:0;width:100%;height:100%;display:block}.preview-status{font-size:12px;line-height:1.5;margin:10px 0 4px;color:#b9cbbd;min-height:36px}.reset-preview{font:inherit;font-size:12px;color:#a9caff;border:0;background:none;padding:8px 0;cursor:pointer;min-height:36px}.comparison-note{font-size:13px;max-width:850px;margin:18px auto 0;color:#aaa}.comparison-note strong{color:#d6ccc4}#originals{margin:0 5vw}#originals>summary{font-size:22px}#originals main{padding:20px 0}.switcher{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);display:flex;gap:6px;align-items:center;background:#e8e4df;color:#201e1c;border-radius:18px;padding:8px 10px;box-shadow:0 8px 28px #0008;z-index:50;max-width:calc(100% - 24px)}.switcher button{min-height:44px;min-width:44px;border:0;border-radius:10px;padding:10px 14px;cursor:pointer;font:600 14px system-ui;background:transparent;color:inherit}.switcher .chosen{background:#292524;color:#fff}.switcher .arrow{padding:8px;font-size:20px}.switcher-label{font-size:12px;padding:0 8px}.switcher button:focus-visible,.reset-preview:focus-visible{outline:2px solid #6195d9;outline-offset:2px}@media(max-width:650px){.directions{grid-template-columns:1fr;gap:0}.pair{gap:12px}.comparison{padding:20px 12px}.comparison-heading h2{font-size:21px}.switcher-label{display:none}.switcher button{padding:10px 12px}.pair figcaption{font-size:14px}.pair figcaption span{min-height:34px}.comparison-note{font-size:12px}}@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}}'''
page=page.replace('</style>',extra_css+'</style>')
switcher='''<nav class="switcher" aria-label="Prototype direction"><span class="switcher-label">PROTOTYPE</span><button class="arrow" id="previous" aria-label="Previous direction">←</button><button data-variant="A" aria-pressed="true" class="chosen">A · Inline</button><button data-variant="B" aria-pressed="false">B · Panel</button><button data-variant="C" aria-pressed="false">C · Center</button><button class="arrow" id="next" aria-label="Next direction">→</button></nav><script src="prototype/gallery-controls.js"></script>'''
page=page.replace('</body>',switcher+'</body>') if '</body>' in page else page.replace('</html>',switcher+'</html>')
(root/'index.html').write_text(page,encoding='utf-8')
with zipfile.ZipFile(root.parent/'android-empty-states.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in [root/'index.html',root/'visual-review.txt',root/'build-gallery.py',*files,*sorted((root/'prototype').glob('*'))]:
        if p.is_file(): z.write(p,p.relative_to(root))
print(f'{len(files)} screenshots packaged in {root}')
