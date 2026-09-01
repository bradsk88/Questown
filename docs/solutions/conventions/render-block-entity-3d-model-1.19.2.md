# Rendering a 3D model for a block entity (1.19.2 / Forge 43.3.0)

Use this when a block must render a 3D model (e.g. a mob/animal model) instead
of a cube. The helper-chicken onboarding trophy (`StoneChickenStatue`) is the
first example: a block whose cube is empty and whose block entity draws the
vanilla chicken model.

**Verified by:** client-booted. The `createBodyLayer().bakeRoot()` path below boots
the client past resource load; the old `context.bakeLayer` version of this doc was
only ever *compiled*, never *run*, which is why it was wrong. Treat any fix here
as "verified" only once the client has booted past resource load — a successful
`compileJava` is not proof the layer lookup succeeds.

## The one crash that bites: `bakeLayer` on the vanilla chicken texture fails

`context.bakeLayer(new ModelLayerLocation("minecraft:textures/entity/chicken.png",
"chicken"))` throws `IllegalArgumentException: No model for layer
minecraft:textures/entity/chicken.png#chicken` and FATALs the render thread
(`BlockEntityRenderers.createEntityRenderers`, at resource load). In 1.19.2 the
`EntityModelSet` only has layers that something *registered*, and the vanilla
**does not** register the chicken layer under that texture key (its
`ChickenRenderer` bakes its own private `CHICKEN_LOCATION`, and `ModelLayers`
has no `CHICKEN` constant). So neither the vanilla texture nor a mod texture
works as a `bakeLayer` key here.

**Fix:** don't `bakeLayer` at all. Bake the geometry straight from the static
layer builder and apply your colour at render time via `renderType`:

```java
private static final ResourceLocation STATUE_TEXTURE =
        new ResourceLocation("minecraft", "textures/blocks/stone.png");
private final ChickenModel<Chicken> model;
public ChickenStatueBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.model = new ChickenModel<>(ChickenModel.createBodyLayer().bakeRoot());
}
// render():
RenderType renderType = this.model.renderType(STATUE_TEXTURE);   // colour
this.model.renderToBuffer(pose, buffer.getBuffer(renderType), packedLight, 0, 1F,1F,1F,1F);
```

`ChickenModel.createBodyLayer()` is a static `LayerDefinition`; `LayerDefinition
.bakeRoot()` returns the `ModelPart` the `ChickenModel` constructor takes. This
needs no pre-registered layer and no `context.bakeLayer`, so it cannot miss a key.

Verified against `build/createMcpToSrg/output.tsrg` (1.19.2 API): `ChickenModel
.createBodyLayer() -> LayerDefinition` (static), `LayerDefinition.bakeRoot()
-> ModelPart`, `ChickenModel.<init>(ModelPart)`. A `ModelLayerRegistrationEvent`
does **not** exist in 1.19.2 (it is 1.20+), so the "register your own layer"
route is not available here; baking from `createBodyLayer()` is the way.

## The 1.19.2 chicken-model API (verified against the mapped jar via javap)

- `net.minecraft.client.model.ChickenModel<T extends Entity>` — generic; the
  vanilla uses `ChickenModel<Chicken>`.
- Constructed from a `ModelPart`: `new ChickenModel<>(context.bakeLayer(layer))`.
- `Model` ctor takes `Function<ResourceLocation, RenderType>`; `renderType(tex)`
  returns the RenderType for a texture; render via
  `renderToBuffer(PoseStack, VertexConsumer, int, int, float, float, float, float)`
  (packedLight, overlay, r,g,b,a).
- `ModelLayerLocation` has one ctor: `(ResourceLocation texture, String name)`.
- The codebase's existing block-entity renderers (`HospitalBedEntityRenderer`,
  `PlateBlockEntityRenderer`, `AxeRackBlockEntityRenderer`) register via
  `BlockEntityRenderers.register(TilesInit.X.get(), X::new)` in `Questown.java`
  `doClientStuff`, and the block entity type in `TilesInit`.

## Wiring a block to a block entity in 1.19.2

- `EntityBlock` has only `newBlockEntity(BlockPos, BlockState)` (plus default
  `getTicker`/`getListener`). There is **no** `getBlockEntityType()` override —
  adding one is a compile error ("method does not override"). The block entity
  type is associated by registering it in `TilesInit` and referencing it in the
  block entity's `super(TilesInit.X.get(), ...)`.

## The mapped client classes are not in a locatable jar

The mapped `forge-1.19.2-...-mapped_official-1.19.2.jar` has **no**
`net/minecraft/client` classes (`unzip -l | grep client` returns 0), so
`javap`/`unzip` on it will not find a client class even though the compile
classpath has it. To verify a client API, `javap -c` the vanilla renderer that
uses it (it resolves from the mapped jar) and read the bytecode, or just compile
and let `compileJava` surface the error. Do not chase a `find` for the client
jar.

## Gotchas that cost time in the first attempt

- `noOcclusion()` on the statue block is fine (a 3D model that is not a full
  cube). `strength(0.0f)` with no `requiresCorrectToolForDrops` makes it
  hand-breakable.
- Hide the cube by pointing the block model at `minecraft:root` — but 1.19.2
  has **no** `minecraft:models/root.json`, so the model fails to load (warning,
  renders missing). Use a transparent cube instead, which loads fine:
  `{"parent":"minecraft:block/cube_all","textures":{"all":"minecraft:particle"}}`
  (the block entity then draws the 3D model over an invisible cube).
- A hand-generated PNG (Python `struct`+`zlib`) can carry a valid signature yet
  be rejected by the client: `TextureAtlas: ... Could not read info from the
  PNG file ... Image not of any known type, or corrupt`. Prefer reusing an
  existing texture or a known-good generator.
- The `sleep` in the same bash call as `nohup ./gradlew runClient` kills the
  launch (see AGENTS.md "This machine"). Launch, then verify in a separate call.
