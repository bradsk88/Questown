---
title: updateWorkersAfterRequestChange NPEs on a downtime townie
status: needs-triage
created: 2026-10-01
---

## Context

`TownFlagBlockEntity.updateWorkersAfterRequestChange()` (called from the
work-handle change listener) does:

```java
if (e.getStatusForServer().isBusy()) {
```

For a townie in DOWNTIME (fresh `_qtdev townspawn`, status `NO_JOBSITE`,
job `gatherer/downtime`) `getStatusForServer()` returns `null`, so posting
a request whose product that townie's job root can satisfy
(`ServerJobsRegistry.canSatisfy(...) == true`) throws:

```
java.lang.NullPointerException: Cannot invoke
"ca.bradj.questown.jobs.IStatus.isBusy()" because the return value of
"ca.bradj.questown.mobs.visitor.VisitorMobEntity.getStatusForServer()" is null
  at TownFlagBlockEntity.lambda$updateWorkersAfterRequestChange$14
```

## Behaviour observed

- Discovered 2026-10-01 driving `_qtdev board_request wheat_seeds` in a
  fresh-world fixture.
- The NPE fires in the change listener **after** the request is posted -
  the board card still appears, so drives succeed; the cost is a server-log
  stack and (probably) the skipped `changeJobForVillager` re-evaluation for
  the downtime pool.
- **Not a port regression**: the 1.19.2 golden branch carries the identical
  method body (verified by direct diff of
  `TownFlagBlockEntity.updateWorkersAfterRequestChange`), so the same NPE
  should reproduce there for a downtime townie.

## Suggested shape

Null-guard the status in the filter (a downtime townie is trivially "not
busy" for job-assignment purposes, so treating `null` as `!isBusy()`
should be correct - confirm against the job-assignment intent before
editing), and add a unit test for the downtown branch. Check whether
`changeJobForVillager` downstream also assumes a non-null status.
