# Recreation and relaxation poses during downtime

**Status:** wanted, not scheduled. Acknowledged as effortful.

## What

Townies on **downtime** should visibly *do something recreational* — sitting, leaning, chatting,
stretching, warming at a campfire — rather than walking a random route with a random pose.

## Why it matters more than it sounds

Under ADR-0011 watching the town is the primary experience, and **mood owns downtime** (CONTEXT,
"Proficiency owns speed; mood owns downtime"): a low-mood town is *supposed* to be visibly
lounging. That design only pays off if lounging looks like lounging. Today the visible vocabulary
is `RANDOM_DOWNTIME_POSE` plus wandering, which reads closer to "idle" than to "resting" — and
"looks idle" is the exact impression the legibility pass exists to remove.

It is also the intended answer to a question that keeps coming up: *what should a townie do when
it is blocked or has nothing useful to do?* The answer is *look alive*, not *silently pick
different work* — see the **Need bubble** entry in CONTEXT, where a retry backoff was considered
and declined for the same reason.

## Why it is not scheduled

Poses are animation work, not logic work: new model poses, transitions that do not snap, and
placement (you cannot sit where there is no seat). The existing `PoseInPlace` / `requestPose`
plumbing on `SimpleVillagerHandle` and the `LIE_ON_WORKSPOT` / `CLEAR_POSE` special rules are the
hooks it would build on, so the mechanism exists; the cost is in the content and the polish.

## Prior art in the repo

- `RANDOM_DOWNTIME_POSE` and `DowntimeWork` — the current, minimal version.
- `ResterWork` / `LIE_ON_WORKSPOT` — a townie lying on a hospital bed, the closest existing
  example of an authored, placed pose.
- Helper chicken presentation — precedent for expressive non-verbal communication.
