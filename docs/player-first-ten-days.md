# Your first ten days in Questown

> DRAFT (2026-10-10): narrated from the autotest suite's design (`village/growth`,
> `village/self_sufficiency`, the chicken-arc + tutorial-phase state machines) and
> recorded run output. Not yet read through by the dev — treat pacing claims and
> day-number assignments as approximate until verified. The Day-1 flow below
> reflects the **mat-gated-arrival design** (`.scratch/mat-gated-arrival/`),
> implemented 2026-10-10 in the campfire→mat order: the welcome mat is the first
> ask *after* the campfire beat, and its completion hands over the first villager
> (next morning) + the basics batch (job board + store room).

**Day 1.** You place the town flag, and a helper chicken shows up to guide you. It pecks at a wall gap, points at a spot for a door, and waits for you to right-click that door with your new town wand. Then it takes over: it builds a little room around the door and drops its own chest inside (the store-room quest is already half-done). Next it wants your **campfire**: you light one, and a toast confirms that this campfire is now *your* camp (sleeping on it for the first time earns the advancement). Then it points at the spot for your **welcome mat** — the town's gate. When you place it, the quest board fills with the evening's work: a job-board sign and the store room — and the flag's menu opens to the villagers view, where it tells you *"a villager will arrive in the morning."* When dark falls, you sleep on the campfire — your first night.

**Morning.** You wake to your first villager — a gatherer — arrived at the gate, *because* you placed the welcome mat there the night before (place no mat, wake to an empty town, and the chicken is still coaching you on the gate beat). The mat sits inside the room the chicken built, so the gatherer has somewhere to work from at once: they start going out on a loose quarter-day rhythm, bringing back whatever the land offers. The chicken keeps coaching: a sign becomes your job board, you feed the flag some free food, you open the villager's menu. When the gatherer delivers a bag of Worldly Seeds, the chicken turns into a stone statue, and onboarding is over.

**Day 2.** A second villager arrives at dawn, and the town hands you three Blocks of Progress — your first currency for the skill tree. The quests now name the work: bring back raw food, craft 4 wooden swords and a stone sword, teach someone to be a hunter, and build a bedroom so the townies can actually sleep.

**Days 3–4.** A third villager comes. The town goes hungry for *cooked* food, so you staff a cook: raw meat in, meals out, in a kitchen with a furnace. The townies stop eating raw at the flag and start working at full mood. Then the town tells you what it's running short of — usually coal — and asks you to unlock the miner and build their room. That's your first leaver: someone who vanishes off-map and comes home with ore.

**Days 5–7.** You meet the crafter, give them a workshop, and specialize them. By now a fourth or fifth villager has arrived, each new face coming with their own little quest batch. The town's rhythm is settled: dawn brings rewards and new faces, townies work until late morning, lunch breaks when hunger strikes, downtime in the afternoon, beds at night. Anyone without a bed is grumpy at dawn. If you log off for an afternoon, the town catches up on its own — crops and furnaces even run through the night.

**Days 8–10.** Storage fills fast, so you upgrade the store room. The quest board's horizon is now an expedition: fetch an exotic wood "from the lands beyond" for a proper flagpole. A healthy town reaches its fifth villager somewhere in this stretch — that's the bar the test suite holds the first ten days to — and after that the scripted tutorial ends and the town starts running on its own economy.
