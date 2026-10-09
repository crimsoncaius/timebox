"""Conservative factual checks; unfamiliar phrasing remains unresolved."""
import re

WORDS = dict(zip("zero one two three four five six seven eight nine ten eleven twelve".split(), range(13), strict=True))
DURATION = re.compile(r"\b(\d+(?:\.\d+)?|" + "|".join(WORDS) + r")\s*(hours?|hrs?|h|minutes?|mins?|m)\b", re.I)


def matches_card(card, spec):
    fields = spec.get("fields", {})
    if any(card.get(k) != v for k, v in fields.items()):
        return False
    if "task_ids" in spec:
        if {r.get("id") for r in card.get("rows", [])} != set(spec["task_ids"]):
            return False
    return True


def factual_checks(expectation, answer, cards):
    failures, unresolved = [], []
    # Strip formatting, not punctuation that separates factual clauses.
    text = re.sub(r"[*_`|]", " ", answer).lower()
    clauses = re.split(r"\n|;|(?<!\d)\.(?!\d)", text)
    for fact in expectation.get("durations", []):
        labels = fact.get("aliases", [fact["label"]])
        label = r"(?<!\w)(?:" + "|".join(re.escape(s.lower()) for s in labels) + r")(?![\w/])"
        observed = []
        duration_clauses = [part for sentence in clauses
                            if not ("plan" in sentence and fact.get("lane", "actual") == "actual")
                            for part in re.split(r",|\band\b", sentence)]
        for clause in duration_clauses:
            if not re.search(label, clause):
                continue
            durations = list(DURATION.finditer(clause))
            associated = []
            for mention in re.finditer(label, clause):
                preceding = [d for d in durations if d.end() <= mention.start() and
                             re.fullmatch(r"\s*(?:of|on|was|were|for)?\s*", clause[d.end():mention.start()])]
                following = [d for d in durations if d.start() >= mention.end() and
                             re.fullmatch(r"\s*(?::|=|[-–—]|was|were|is|total(?:s|ed)?)?\s*", clause[mention.end():d.start()])]
                # In "9 hours on Work: 6 hours on Coding", Work belongs to
                # the preceding duration, not the breakdown following the colon.
                associated.extend(preceding or following)
            uncertain = re.search(r"\b(?:not|never|no|maybe|perhaps|might|could|unknown)\b|[?]", clause)
            if uncertain:
                continue
            # Retain the single-value form ("Work logged a total of 9 hours"),
            # but never assign every duration in a multi-value clause to its label.
            other_labels = [alias for other in expectation.get("durations", []) if other != fact
                            for alias in other.get("aliases", [other["label"]])]
            mentions_other = any(re.search(r"(?<![\w/])" + re.escape(alias.lower()) + r"(?![\w/])", clause)
                                 for alias in other_labels)
            if not associated and len(durations) == 1 and not mentions_other:
                associated = durations
            for duration in associated:
                number, unit = duration.groups()
                value = float(WORDS[number]) if number in WORDS else float(number)
                observed.append(value * (3600 if unit.startswith("h") else 60))
        card_values = [row[fact.get("lane", "actual") + "_seconds"]
                       for card in cards for row in card.get("types", [])
                       if row["task_type"] == fact["label"] and row.get(fact.get("lane", "actual") + "_seconds") is not None]
        if any(abs(v - fact["seconds"]) > .01 for v in observed + card_values):
            failures.append("incorrect duration: " + fact["label"])
        elif not observed and not card_values:
            unresolved.append("duration not established: " + fact["label"])
    for fact in expectation.get("checked", []):
        observed = []
        for clause in clauses:
            if fact["title"].lower() not in clause:
                continue
            if re.search(r"unchecked|not checked|incomplete|not done|\[ \]|⬜|☐", clause):
                observed.append(False)
            elif re.search(r"\bchecked\b|\bcomplete(?:d)?\b|\bdone\b|\[x\]|✅|☑", clause):
                observed.append(True)
        rows = [row for card in cards for row in card.get("rows", [])]
        rows += [child for row in rows for child in row.get("subtasks") or []]
        observed += [row["checked"] for row in rows
                     if row.get("title") == fact["title"] and isinstance(row.get("checked"), bool)]
        if any(v != fact["value"] for v in observed):
            failures.append("incorrect checked state: " + fact["title"])
        elif not observed:
            unresolved.append("checked state not established: " + fact["title"])
    return failures, unresolved


def comparable_operations(operations):
    # Model-local creation references are arbitrary names, not requested fields.
    return [{k: v for k, v in op.items() if k != "ref"} for op in operations]
