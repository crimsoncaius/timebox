"""Curated family-separated fixtures. Generate once; never auto-regenerate a test set.

Run `python -m scripts.presentation_dataset DIRECTORY` to create a new dataset.
The directory must not exist. No model calls are made.
"""
import argparse
import hashlib
import json
from copy import deepcopy
from pathlib import Path

from scripts.presentation_fixtures import NOW


def digest(data):
    # Git's Windows line-ending conversion must not invalidate the same fixture.
    return hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest()


def read(tool="read_activity", **args):
    return {"tool": tool, "args": args}


def final(answer="", cards=()):
    return {"answer": answer, "cards": list(cards) if cards != "all" else cards}


def card(**fields):
    return {"fields": fields}


def history(question="Show me today's plan.", *, acknowledge=True):
    return {"history": [{"question": question, "acknowledge": acknowledge,
                         "steps": [read(), final(cards="all")]}]}


def families():
    result = []

    def add(split, family, category, questions, expectation, probe, setup=None):
        assert len(questions) == 5
        for index, question in enumerate(questions):
            def value(item, index=index):
                return deepcopy(item(index) if callable(item) else item)
            result.append({"id": f"{family}-{index + 1:02d}", "family": family,
                           "split": split, "category": category, "question": question,
                           "setup": value(setup or {}), "expectation": value(expectation), "probe": value(probe)})

    add("train", "arithmetic-only", "text", [
        "What is 7 plus 8? Reply with the number only, no cards.",
        "What is 12 minus 5? Just the number, without cards.",
        "Calculate 6 times 4. Give only the number and no cards.",
        "What is 18 divided by 3? Answer with just the number, no cards.",
        "Calculate 9 plus 11. Only the number, without cards."],
        lambda i: {"cards": "unwanted", "facts": ["^" + str([15, 7, 24, 6, 20][i]) + "[.]?$"]},
        lambda i: [final(str([15, 7, 24, 6, 20][i]))])
    dates = ["2026-10-08", "2026-10-09", "2026-10-07", "2026-10-06", "2026-10-05"]
    add("train", "single-day-schedule", "required-card", [
        "Show today's full saved plan as a card.", "Show tomorrow's saved plan as a card.",
        "Show the saved plan for yesterday as a card.", "Show Tuesday October 6's full plan as a card.",
        "Show Monday October 5's saved schedule as a card."],
        lambda i: {"cards": "required", "required_reads": ["read_activity"],
                   "allowed_cards": [card(schema_version=2, date=dates[i], lane="planned", task_type=None)]},
        lambda i: [read(when=dates[i]), final(cards="all")])
    paths, seconds = ["Work/Coding", "Work/Admin", "Reading", "Meals", "Exercise/Walking"], [5400, 2700, 6300, 2700, 0]
    add("train", "single-type-duration", "optional-card", [
        f"How much actual time did I record for {path} yesterday? A relevant card is fine but not necessary." for path in paths],
        lambda i: {"cards": "optional", "required_reads": ["read_activity"],
                   "allowed_cards": [card(schema_version=3, start="2026-10-07", end="2026-10-07", lane="actual")],
                   "durations": [{"label": paths[i], "seconds": seconds[i]}]},
        lambda i: [read(when="yesterday", lane="actual", group_by="task_type", task_type=paths[i]),
                   final(f"{paths[i]}: {seconds[i] / 60:g} minutes.")])
    filters = [{"ready_to_plan": True}, {"blocked": True}, {"project_id": "unassigned"},
               {"task_type_id": "unset"}, {"ready_to_plan": True, "blocked": False}]
    ids = [[1, 4], [6], [8], [7], [1, 4]]
    add("train", "task-collection-filters", "required-card", [
        "Show my Ready to Plan ordinary tasks as a card.", "Show my blocked ordinary tasks as a card.",
        "Show incomplete ordinary tasks with no project as a card.",
        "Show incomplete ordinary tasks with no Task Type as a card.",
        "Show unblocked Ready to Plan ordinary tasks as a card."],
        lambda i: {"cards": "required", "required_reads": ["read_tasks"],
                   "allowed_cards": [{"fields": {"schema_version": 4, "kind": "tasks"}, "task_ids": ids[i]}]},
        lambda i: [read("read_tasks", mode="search", kinds=["ordinary"], filters=filters[i]), final(cards="all")])
    titles = ["Weekly report", "Quarterly report", "Send invoice", "Buy groceries", "Review pull request"]
    targets = [3, 2, 5, 7, 4]
    replacements = ["Send weekly update", "Prepare quarterly review", "Send October invoice", "Buy pantry supplies", "Review API changes"]
    def rename(i):
        return [{"op": "patch_task", "target": {"id": targets[i]}, "set": {"title": replacements[i]}}]
    add("train", "rename-proposal", "read-task-proposal", [
        f"Rename {old} to '{new}'." for old, new in zip(titles, replacements, strict=True)],
        lambda i: {"cards": "optional", "task_proposal": True, "operations": rename(i),
                   "required_reads": ["read_tasks"], "allowed_cards": [{"fields": {"schema_version": 4}, "task_ids": [targets[i]]}],
                   "forbidden": ["has been renamed", "I (?:have )?renamed"]},
        lambda i: [read("read_tasks", mode="search", query=titles[i]),
                   read("propose_task_changes", operations=rename(i)), final("Review the proposed rename before confirming.")])
    new_titles = ["Test calendar import", "Call the dentist", "Read the migration guide", "Buy printer paper", "Write release notes"]
    def create(i):
        return [{"op": "create_task", "ref": "new", "title": new_titles[i]}]
    add("train", "create-unclassified-task", "task-proposal-no-read", [
        f"Create an ordinary task titled '{title}', with no other fields or classification." for title in new_titles],
        lambda i: {"cards": "unwanted", "task_proposal": True, "operations": create(i),
                   "forbidden": ["(?:I have|I've|successfully) created", "has been created"]},
        lambda i: [read("propose_task_changes", operations=create(i)), final("Review the new task proposal.")])
    track_paths = ["Reading", "Meals", "Exercise/Walking", "Work/Coding", "Work/Admin"]
    add("train", "tracking-now", "tracking-no-read", [
        f"I'm doing {path} now. Propose tracking it." for path in track_paths],
        lambda i: {"cards": "unwanted", "tracking": {"action": "track", "paths": [track_paths[i]], "at": None},
                   "forbidden": ["tracking (?:has )?started", "I(?:'ve| have) started"]},
        lambda i: [read("propose_tracking", action="track", task_type_paths=[track_paths[i]]), final("Confirm to start tracking.")])
    add("train", "tracking-from-current-plan", "read-tracking-proposal", [
        "Read today's plan and propose tracking what I planned for right now.",
        "I am doing the activity scheduled at this moment. Check the plan and propose tracking it.",
        "Look up what is planned at 3:30 pm today and propose tracking that now.",
        "Use the current schedule to find this moment's activity, then propose tracking it.",
        "Check the plan for now; that is what I'm doing. Prepare a tracking proposal."],
        {"cards": "optional", "required_reads": ["read_activity"],
         "allowed_cards": [card(schema_version=2, date="2026-10-08", lane="planned")],
         "tracking": {"action": "track", "paths": ["Reading"], "at": None}},
        [read(), read("propose_tracking", action="track", task_type_paths=["Reading"]), final("Confirm to track Reading.")])
    add("train", "historical-plan-reshow", "acknowledged-history", [
        "Show the same plan card you showed in your previous answer, as it was then.",
        "Display that earlier plan snapshot again without refreshing it.",
        "Bring back the original schedule card from your last answer.",
        "Show the plan snapshot from our previous exchange again.",
        "Repeat the earlier plan card, keeping its original data."],
        {"cards": "required", "allowed_cards": [card(schema_version=2, date="2026-10-08", lane="planned")],
         "historical_cards": True}, [final(cards=[0])], history())
    add("train", "unacknowledged-reference", "unacknowledged-history", [
        "What did your previous answer say? I only want a reminder, no new reads or cards.",
        "Repeat your last answer in words without reading any data or showing cards.",
        "Remind me of the answer you already gave, without fetching anything or displaying cards.",
        "What had you told me in this conversation? Don't look up fresh information or show cards.",
        "Summarize the answer immediately before this message, without tools or cards."],
        {"cards": "unwanted", "facts": ["(?i)(?:no|don't|do not|cannot|can't).*(?:previous|earlier|prior|history|answer)"],
         "forbidden": ["you had|I showed|I told you"], "no_reads": True},
        [final("I don't have a previous answer in this conversation.")], history(acknowledge=False))
    add("train", "activity-unavailable", "unavailable-read", [
        "Show today's plan if it can be verified; otherwise explain that it is unavailable.",
        "Read my current schedule. If the read fails, explain the failure without inventing a plan.",
        "Check the plan for today and tell me if it cannot be loaded.",
        "Can you retrieve today's saved plan? Tell me if the data is unavailable.",
        "Fetch today's plan. If you cannot verify it, say so."],
        {"cards": "unwanted", "required_reads": ["read_activity"], "read_error": True,
         "facts": ["(?i)(?:unavailable|could not|cannot|can't|unable|failed)"],
         "forbidden": ["no (?:planned blocks|plans|activities)"]},
        [read(), final("The plan is unavailable, so I cannot verify today's schedule.")], {"unavailable": ["read_activity"]})
    add("train", "hierarchy-totals", "hierarchy-facts", [
        "Give this week's actual Work total and its Coding and Admin breakdown, without double counting.",
        "How many hours of Work have I recorded this week? Separate Coding and Admin.",
        "Summarize actual Work time since Monday, showing Coding and Admin within the total.",
        "What is this week's Work total, with its two child Task Types broken out?",
        "Compare Coding and Admin actual hours this week and state their combined Work total."],
        {"cards": "optional", "required_reads": ["read_activity"],
         "allowed_cards": [card(schema_version=3, start="2026-10-05", end="2026-10-11", lane="actual")],
         "durations": [{"label": "Work", "seconds": 32400}, {"label": "Work/Coding", "aliases": ["Coding", "Work/Coding"], "seconds": 21600},
                       {"label": "Work/Admin", "aliases": ["Admin", "Work/Admin"], "seconds": 10800}]},
        [read(when="this_week", lane="actual", group_by="task_type", task_type="Work"),
         final("Work: 9 hours.\nCoding: 6 hours.\nAdmin: 3 hours.")])

    add("validation", "plan-free-gap", "text-after-read", [
        "Is 16:00 to 16:30 today free in my saved plan? Answer in text, no cards.",
        "Does my plan leave a free half hour at 4 pm today? No card, please.",
        "Check whether 4:00–4:30 pm today is unscheduled. Just tell me in words.",
        "Am I free from 16:00 until 16:30 according to today's plan? Don't show cards.",
        "Is there a planned block between 4 and 4:30 this afternoon? Answer without cards."],
        {"cards": "unwanted", "required_reads": ["read_activity"],
         "facts": [r"(?:free|unscheduled|no (?:saved|planned) block|nothing is planned|unbooked|half.hour is open|plan is clear|ends at (?:4:00|16:00).*next (?:planned )?block starts at (?:4:30|16:30))"],
         "forbidden": [r"not (?:free|clear|unscheduled|unbooked)|occupied|busy|isn['’]t (?:free|clear)"]}, [read(), final("16:00–16:30 is free in your saved plan.")])
    minutes = [0, 5, 10, 15, 30]
    add("validation", "tracking-stop", "tracking-no-read", [
        "I stopped reading just now.", "I stopped reading five minutes ago.", "Stop tracking; I finished ten minutes ago.",
        "I stopped fifteen minutes ago. Prepare the stop proposal.", "I stopped reading half an hour ago."],
        lambda i: {"cards": "unwanted", "tracking": {"action": "stop", "paths": [],
                   "at": None if not minutes[i] else f"2026-10-08T07:{30-minutes[i]:02d}:00Z"}},
        lambda i: [read("propose_tracking", action="stop", **({"minutes_ago": minutes[i]} if minutes[i] else {})),
                   final("Confirm the stop proposal.")])
    child_ops = [{"op": "set_subtask_checked", "target": {"id": 11}, "parent": {"id": 1}, "checked": True}]
    add("validation", "check-child-proposal", "read-task-proposal", [
        "Check 'Patch export' under Fix calendar export.", "Mark the Patch export subtask checked under Fix calendar export.",
        "I've finished Patch export in Fix calendar export; check that subtask only.",
        "Set Patch export to checked, leaving its parent Fix calendar export open.",
        "Please check off Patch export within Fix calendar export, without completing the parent."],
        {"cards": "optional", "task_proposal": True, "operations": child_ops, "required_reads": ["read_tasks"],
         "allowed_cards": [{"fields": {"schema_version": 4}, "task_ids": [1]}]},
        [read("read_tasks", mode="search", query="Fix calendar export"),
         read("read_tasks", mode="children", parent_id=1, kind="subtasks"),
         read("propose_task_changes", operations=child_ops), final("Review the proposal to check Patch export.")])
    add("validation", "explicit-supporting-notes", "private-text-request", [
        "What did I write in yesterday's Reading notes? Quote the note; no cards.",
        "Read the Supporting Notes on yesterday's Reading block and repeat the text without cards.",
        "Show the exact note I wrote for Reading yesterday, in text only.",
        "Quote yesterday's Reading Supporting Notes. I don't need a card.",
        "Tell me the saved note text for yesterday's Reading activity; no cards please."],
        {"cards": "unwanted", "required_reads": ["read_activity"],
         "facts": ["Spent extra time understanding transaction isolation[.]"]},
        [read(when="yesterday", lane="actual", task_type="Reading", include_text=True),
         final("Spent extra time understanding transaction isolation.")])
    stale = history()
    stale["move_plan"] = True
    add("validation", "stale-plan-refresh", "changed-history", [
        "Has the plan changed? Tell me when Quarterly report is now scheduled, without a card.",
        "Refresh today's plan: when does Quarterly report start now? Text only.",
        "Check the current schedule again and tell me the report's current start time, no cards.",
        "What time is Quarterly report planned now? Use fresh data and answer without cards.",
        "Read today's plan again. Is the report still at its old time? Give the new time in words, no card."],
        {"cards": "unwanted", "required_reads": ["read_activity"],
         "facts": [r"(?:\b18:00\b|\b6(?::00)?(?:\s*(?:to|[-–—])\s*7(?::00)?)?\s*p\.?m\.?\b|\bsix(?: to seven)? (?:in the|this) evening\b)"],
         "forbidden": ["still (?:at )?(?:16:30|4:30)"]},
        [read(), final("Quarterly report is now scheduled at 18:00.")], stale)
    pair = [card(schema_version=2, date="2026-10-07", lane=lane) for lane in ("planned", "actual")]
    add("validation", "same-day-lane-cards", "multiple-cards", [
        "Show yesterday's planned and actual blocks in two separate cards.",
        "Display one card for yesterday's plan and another for yesterday's actual activity.",
        "I want two cards for October 7: the plan and the actual blocks, separately.",
        "Show planned versus actual activity for yesterday, using a separate card for each lane.",
        "Give me yesterday's planned schedule card followed by its actual activity card."],
        {"cards": "required", "allowed_cards": pair, "required_cards": pair, "required_reads": ["read_activity"]},
        [read(when="yesterday", lane="planned"), read(when="yesterday", lane="actual"), final(cards="all")])

    reopen = [{"op": "reopen_task", "target": {"id": 9}}]
    add("test", "reopen-completed-task", "read-task-proposal", [
        "Reopen Update changelog.", "Move Update changelog back to open tasks.",
        "Undo the completed status of Update changelog by reopening it.",
        "I need to work on Update changelog again; reopen that completed task.",
        "Set Update changelog back to open, with no other changes."],
        {"cards": "optional", "task_proposal": True, "operations": reopen,
         "required_reads": ["read_tasks"], "allowed_cards": [{"fields": {"schema_version": 4}, "task_ids": [9]}],
         "forbidden": ["has been reopened|I reopened|is now open"]},
        [read("read_tasks", mode="search", query="Update changelog", scope="history", filters={"completion": "completed"}),
         read("propose_task_changes", operations=reopen), final("Review the proposal to reopen Update changelog.")])
    correction = {"history": [{"question": "I've been reading for twenty minutes.", "steps": [
        read("propose_tracking", action="track", task_type_paths=["Reading"], minutes_ago=20), final("Confirm to track Reading.")]}]}
    add("test", "correct-unconfirmed-tracking", "tracking-history", [
        "Actually I started at 2:15 pm. Correct the proposal.",
        "Change that unconfirmed tracking proposal: I began at 14:15.",
        "I meant 2:15 pm as the start of Reading; prepare the corrected proposal.",
        "The start time should be 14:15, not twenty minutes ago. Update the proposal.",
        "Please propose Reading from 2:15 pm instead; I haven't confirmed the first proposal."],
        {"cards": "unwanted", "tracking": {"action": "track", "paths": ["Reading"], "at": "2026-10-08T06:15:00Z"},
         "forbidden": ["tracking (?:has )?started|already tracking"]},
        [read("propose_tracking", action="track", task_type_paths=["Reading"], hour=14, minute=15),
         final("Confirm the corrected Reading proposal for 14:15.")], correction)
    task_history = {"history": [{"question": "Show my blocked tasks as a card.", "steps": [
        read("read_tasks", mode="search", kinds=["ordinary"], filters={"blocked": True}), final(cards="all")]}]}
    add("test", "historical-task-ordinal", "task-history", [
        "What is the title of the first task in that earlier card? No new card.",
        "Name the first task you showed in the previous card, in text only.",
        "Which task was first in the card from your last answer? Don't show another card.",
        "Remind me of the title at the top of that original task card. No card this time.",
        "Tell me the first task's title from the previous snapshot, without a new card."],
        {"cards": "unwanted", "facts": ["Renew insurance"]}, [final("Renew insurance.")], task_history)
    add("test", "partial-cross-tool-coverage", "partial-read-failure", [
        "Show today's plan and my blocked tasks; tell me clearly if either read is unavailable.",
        "Read the current schedule and blocked task list. Show what is available and explain any failed read.",
        "Display today's plan plus blocked tasks, but be explicit about missing coverage.",
        "Fetch today's schedule and blocked tasks. If only one can be read, show it and explain the gap.",
        "Can you show today's planned blocks and my blocked tasks? Don't treat failed reads as empty results."],
        {"cards": "required", "allowed_cards": [card(schema_version=2, date="2026-10-08", lane="planned")],
         "required_reads": ["read_activity", "read_tasks"], "read_error": True,
         "facts": ["(?i)tasks.*(?:unavailable|could not|cannot|can't|unable)"], "forbidden": ["no blocked tasks"]},
        [read(), read("read_tasks", mode="search", filters={"blocked": True}),
         final("Saved tasks are unavailable; the card shows today's verified plan.", cards="all")],
        {"unavailable": ["read_tasks"]})
    mixed = [card(schema_version=2, date="2026-10-08", lane="planned"),
             {"fields": {"schema_version": 4}, "task_ids": [1]}]
    add("test", "task-and-activity-cards", "multiple-card-types", [
        "Show today's plan and the Fix calendar export task in separate cards.",
        "Display two cards: today's planned blocks and the saved task Fix calendar export.",
        "Give me the current plan card together with a task card for Fix calendar export.",
        "Show Fix calendar export as a Task Card and today's schedule as another card.",
        "I want the plan for today alongside a card containing just Fix calendar export."],
        {"cards": "required", "allowed_cards": mixed, "required_cards": mixed,
         "required_reads": ["read_activity", "read_tasks"]},
        [read(), read("read_tasks", mode="search", query="Fix calendar export"), final(cards="all")])
    add("test", "running-block-elapsed", "clock-sensitive-fact", [
        "How long has today's running Reading block lasted so far? No cards.",
        "Tell me the elapsed duration of the Reading activity still running today, in text only.",
        "How many minutes have accumulated on today's active Reading block? Don't show a card.",
        "As of now, how long have I been reading in the current actual block? No cards please.",
        "Read the ongoing Reading block and report its current elapsed time without a card."],
        {"cards": "unwanted", "required_reads": ["read_activity"],
         "durations": [{"label": "Reading", "seconds": 5400}]},
        [read(when="today", lane="actual", task_type="Reading"), final("Reading: 90 minutes so far.")])
    return result


def write_dataset(directory):
    cases = families()
    directory.mkdir(parents=True, exist_ok=False)
    fixture_source = Path(__file__).with_name("presentation_fixtures.py")
    manifest = {"version": 1, "clock": NOW, "families": {}, "files": {},
                "fixture_source_sha256": digest(fixture_source.read_bytes()),
                "held_out_policy": "Structural validation only until scorer and candidates are frozen."}
    for split in ("train", "validation", "test"):
        selected = [c for c in cases if c["split"] == split]
        data = (json.dumps(selected, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        path = directory / f"{split}.json"
        path.write_bytes(data)
        manifest["files"][path.name] = {"cases": len(selected), "sha256": digest(data)}
        manifest["families"][split] = sorted({c["family"] for c in selected})
    pilot = Path(__file__).parents[1] / "studies/presentation/pilot.json"
    regressions = [c for c in json.loads(pilot.read_text(encoding="utf-8")) if c["split"] == "regression"]
    data = (json.dumps(regressions, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    (directory / "regression.json").write_bytes(data)
    manifest["files"]["regression.json"] = {"cases": len(regressions), "sha256": digest(data)}
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def load_cases(directory, split):
    if split not in {"train", "validation", "test", "regression"}:
        raise ValueError("Choose an explicit split")
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if digest(Path(__file__).with_name("presentation_fixtures.py").read_bytes()) != manifest["fixture_source_sha256"]:
        raise ValueError("Fixture source changed; create a new dataset version")
    data = (directory / f"{split}.json").read_bytes()
    if digest(data) != manifest["files"][f"{split}.json"]["sha256"]:
        raise ValueError("Dataset changed; create a new version and document test exposure")
    return json.loads(data)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    write_dataset(parser.parse_args().directory)
