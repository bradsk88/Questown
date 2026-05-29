# Background 
In real-time gameplay, after completing a single "cycle" for a job, the villager drops that job title and tries to start
another one. We use shuffle to create an "equal opportunity" for the next job that they will start. In the ideal case,
if the villager is capable of 5 different jobs, they will cycle through each one before returning to try the first one
again (cycling in random order to create an organic feel in-game).

In practice, this often looks like:
- Cook:Insert_Fuel -> E.g. successful!
- Cook:Extract_beef -> No cooked beef yet. Wait for a bit... Nope, still no cooked beef. Try the next job!
- Cook:Stock_ingredients -> Sure, I see some raw beef in a non-kitchen chest, let me move it to where it will be useful!
- Cook:Stock_fuel -> But, there's no fuel in town. Wait for a bit... Nope, still no fuel. Try the next job!
- Cook:Extract_beef -> No cooked beef yet. Wait for a bit... Nope, still no cooked beef. Try the next job!
- Cook:Insert_Ingredients -> Ah, there's some beef in the kitchen chest, let's take that to the furnace.
- (Minecraft) I"m smelting now!
- Cook:Insert_Fuel -> No fuel in town. Wait a bit. Still not fuel in town.
- (Minecraft) I'm done smelting!
- Cook:Insert_Ingredients -> No ingredients to be moved around. Wait a bit.. Still none. On to the next.
- Cook:Extract_beef -> There's cooked beef in the furnace, I'll grab it and put it in a chest.

# Problem
Sometimes, it looks like this:
- Cook:Insert_Fuel
- Cook:Insert_Ingredient
- (Minecraft) I'm smelting now!
- `Cycle through other cooking jobs`
- (Minecraft) I'm done smelting!
- `Cycle through other cooking jobs but, by chance, take a LONG time to get to Cook:Extract`

This results in a frustrating experience for players who are watching the villager because it seems like they "should"
work, but they often just stand in one place cycling through "well, theres no fuel to stock, and there are no ingredients
to stock, and there's no ... etc." before _eventually_ deciding to extract.