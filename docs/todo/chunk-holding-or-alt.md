On October 8, 2026. We discovered that an auto-test harness was failing intermittently because it was building a town that spanned multiple chunks, and only the spawn chunk was staying loaded - this caused several days and hundreds of dollars of wasted time/gpu-cost. 

We fixed the auto-test. But this "village stops functioning properly when chunk is unloaded" problem will probably also impact real players as they move around the world. 

We need to come up with a solution for this. Keeping a ledger of the town's chunks and keeping them loaded is the "obvious" answer, but maybe there's also something more efficient available? Maybe (but not necessarily) something like our time warp functionality?