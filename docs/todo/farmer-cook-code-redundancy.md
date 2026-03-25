**Status: Resolved** — both farmer and cook rules now access the world
exclusively through `QTWorldAccess` (`MinecraftWorldAccess` in production,
`WarpWorldAccess` during time warp). The abstraction is the consolidation:
there is no separate farmer-path or cook-path for world access.
