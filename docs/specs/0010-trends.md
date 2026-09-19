# Trends

Design discussion for GitHub issue #10. These decisions are agreed; the design is still in progress and is not yet approved for implementation.

## Agreed decisions

- Trends presents activity neutrally, without interpreting it against goals or assigning productivity scores.
- The initial breakdown uses Actual Block time only, grouped by the Blocks' Task Types. Planned time is excluded.
- Each Task Type shows duration and its percentage of all recorded time in the selected range.
- Presets are calendar Day, Week, and Month, with previous/next navigation. Custom ranges have inclusive start and end dates.
- Date boundaries use the Reporting Time Zone.
- Parent Task Types show totals including descendants and can expand to show children. Time assigned directly to a parent remains visible in its breakdown. Each duration contributes only once to the overall total.
- As settled in #210, Trends lives beside Calendar in Chronicle and owns its date range independently of Calendar. Task Type figures drill through to Calendar, highlighting contributing days.

## Still to resolve

- Initial range and selection persistence.
- Breakdown presentation and ordering.
- Whether to include a time-series chart in the initial scope.
- Week boundaries, ongoing activity, and partial-day attribution.
- Drill-through details, including single-day ranges and ranges spanning months.
- Empty ranges and unclassified activity presentation.
