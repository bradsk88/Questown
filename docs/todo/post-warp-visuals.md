Some jobs (e.g. fisher) have special rules which cause a visual change to the world (i.e. deploy a fishing hook entity)
but that visual change does not impact the work in any way. Because of this, we exclude deploying that entity from the
time warp logic. However, we should consider another step that runs just after the warp to apply any visual changes that 
should happen. For example, if the warp ends with the fisher half-way through their job, this step should place them at
their work spot and deploy the hook.

Priority: Low