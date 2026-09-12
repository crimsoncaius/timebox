# Android Actual Block details — approved design

The user clarified that #162 means the sheet opened by tapping an existing Actual Block, rather than its timeline card. They selected prototype A, removed the separate Start/End/date/time-zone panel, and requested the action label "Delete".

The production sheet leads with the recorded time range and elapsed minutes, followed by Block Name, Task Type, Note, and linked Battle Plan Task context when present. Cancel and Save changes remain outside the scrolling fields. Times remain unchanged when editing metadata; Task Completion stays independent. New-block creation and running-record handling retain their existing fields and behavior.

Primary design source: branch `codex/prototype-162-android`, commit `d109399`. The implementation applies the later "Delete" label decision. Prototype variants and controls remain outside this implementation branch.

Validation: Android debug build and ActualBlockContractTest, ActivityRepositoryTest, ReportingTimeTest pass. Opened a real ended Actual Block on emulator-5554 and verified the implemented sheet. The broader suite's `DayWorkModeViewModelTest.initial Work Mode restoration resolves before planning can begin` fails identically on unchanged baseline `29db966`; unrelated legacy Work Mode failure.
