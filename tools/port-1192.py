#!/usr/bin/env python3
"""Mechanical 1.20.6 -> 1.19.2 rewrites for leaf files (outside the strangler seam).

The strangler policy (AGENTS.md, tools/strangler-check) routes real API differences through the
seam (mc/, loader/, core/network/, gui/RenderUtil, gui/RenderContext) and allows mechanical
differences inline in leaf files: a renamed package or a renamed accessor. This script applies
those rewrites to a source tree, so a file copied from the 1.20.6 branch to a 1.19.2 branch gets
the same treatment every time.

    tools/port-1192.py text <src-root>               textual rules (imports, renamed types)
    tools/port-1192.py errors <src-root> <javac-log>  compiler-driven rules (e.g. Entity.level())
    tools/port-1192.py data <resources-root>          JSON data rules (tags, recipe results)

The `errors` pass exists for renames a text rule can't do safely: records with a `level`
component have their own `level()` accessor, so only the positions javac reports as missing are
rewritten.
"""
import pathlib
import re
import sys

TEXT_RULES = [
    # joml -> com.mojang.math (1.19.2 ships Mojang's own math types)
    (re.compile(r'\borg\.joml\.Vector3f\b'), 'com.mojang.math.Vector3f'),
    (re.compile(r'\borg\.joml\.Vector4f\b'), 'com.mojang.math.Vector4f'),
    (re.compile(r'\borg\.joml\.Matrix4f\b'), 'com.mojang.math.Matrix4f'),
    (re.compile(r'\borg\.joml\.Matrix3f\b'), 'com.mojang.math.Matrix3f'),
    (re.compile(r'\borg\.joml\.Quaternionf\b'), 'com.mojang.math.Quaternion'),
    (re.compile(r'\bQuaternionf\b'), 'Quaternion'),
    # joml's identity quaternion has a no-arg constructor; Mojang's has a constant
    (re.compile(r'\bnew (?:com\.mojang\.math\.)?Quaternion\(\)'), 'com.mojang.math.Quaternion.ONE.copy()'),
    # com.mojang.math.Axis (1.19.3+) -> the axis constants on Vector3f
    (re.compile(r'^import com\.mojang\.math\.Axis;\n', re.M), ''),
    (re.compile(r'\bcom\.mojang\.math\.Axis\.([XYZ][PN])\b'), r'com.mojang.math.Vector3f.\1'),
    (re.compile(r'(?<![\w.])Axis\.([XYZ][PN])\b'), r'com.mojang.math.Vector3f.\1'),
    # The loader implementation package: loader.neo (NeoForge 1.20.6) -> loader.forge (Forge 1.19.2)
    (re.compile(r'\bca\.bradj\.questown\.loader\.neo\.'), 'ca.bradj.questown.loader.forge.'),
    # NeoForge -> Forge package renames for surfaces with the same class names in Forge 1.19.2
    (re.compile(r'\bnet\.neoforged\.neoforge\.common\.NeoForge\b'), 'net.minecraftforge.common.MinecraftForge'),
    (re.compile(r'(?<![\w.])NeoForge\.EVENT_BUS\b'), 'MinecraftForge.EVENT_BUS'),
    (re.compile(r'\bnet\.neoforged\.bus\.api\.'), 'net.minecraftforge.eventbus.api.'),
    (re.compile(r'\bnet\.neoforged\.neoforge\.server\.ServerLifecycleHooks\b'), 'net.minecraftforge.server.ServerLifecycleHooks'),
    (re.compile(r'\bnet\.neoforged\.neoforge\.event\.(entity|server|level)\.'), r'net.minecraftforge.event.\1.'),
    (re.compile(r'\bnet\.neoforged\.neoforge\.common\.util\.'), 'net.minecraftforge.common.util.'),
    (re.compile(r'^import net\.neoforged\.fml\.common\.EventBusSubscriber;$', re.M), 'import net.minecraftforge.fml.common.Mod.EventBusSubscriber;'),
    (re.compile(r'\bEventBusSubscriber\.Bus\.GAME\b'), 'EventBusSubscriber.Bus.FORGE'),
    # Split tick events (NeoForge 20.4+) -> Forge's phased TickEvent. A handler method or lambda
    # parameter typed ServerTickEvent.Post becomes TickEvent.ServerTickEvent plus a phase guard.
    (re.compile(r'(\w+)\((?:net\.neoforged\.neoforge\.event\.tick\.)?ServerTickEvent\.(Post|Pre) (\w+)\)(\s*)\{'),
     lambda m: f"{m.group(1)}(net.minecraftforge.event.TickEvent.ServerTickEvent {m.group(3)}){m.group(4)}{{ "
               f"if ({m.group(3)}.phase != net.minecraftforge.event.TickEvent.Phase.{'END' if m.group(2) == 'Post' else 'START'}) {{ return; }}"),
    (re.compile(r'\((?:net\.neoforged\.neoforge\.event\.tick\.)?ServerTickEvent\.(Post|Pre) (\w+)\) -> \{'),
     lambda m: f"(net.minecraftforge.event.TickEvent.ServerTickEvent {m.group(2)}) -> {{ "
               f"if ({m.group(2)}.phase != net.minecraftforge.event.TickEvent.Phase.{'END' if m.group(1) == 'Post' else 'START'}) {{ return; }}"),
    (re.compile(r'^import net\.neoforged\.neoforge\.event\.tick\.ServerTickEvent;\n', re.M), ''),
    (re.compile(r'(\w+)\((?:net\.(?:neoforged\.neoforge|minecraftforge)\.client\.event\.)?ClientTickEvent\.(Post|Pre) (\w+)\)(\s*)\{'),
     lambda m: f"{m.group(1)}(net.minecraftforge.event.TickEvent.ClientTickEvent {m.group(3)}){m.group(4)}{{ "
               f"if ({m.group(3)}.phase != net.minecraftforge.event.TickEvent.Phase.{'END' if m.group(2) == 'Post' else 'START'}) {{ return; }}"),
    (re.compile(r'^import net\.(?:neoforged\.neoforge|minecraftforge)\.client\.event\.ClientTickEvent;\n', re.M), ''),
    # ItemStack's item+data equality: components (1.20.5) vs tags (1.19.2)
    (re.compile(r'\bItemStack\.isSameItemSameComponents\('), 'ItemStack.isSameItemSameTags('),
    # Built-in registries: one static Registry class on 1.19.2
    (re.compile(r'^import net\.minecraft\.core\.registries\.BuiltInRegistries;\n', re.M), ''),
    (re.compile(r'(?<![\w.])(?:net\.minecraft\.core\.registries\.)?BuiltInRegistries\.'), 'net.minecraft.core.Registry.'),
    # 1.20.3 renamed grass -> short_grass
    (re.compile(r'\b(Blocks|Items)\.SHORT_GRASS\b'), r'\1.GRASS'),
    # NbtIo reads without an accounter on 1.19.2
    (re.compile(r',\s*(?:net\.minecraft\.nbt\.)?NbtAccounter\.unlimitedHeap\(\)\)'), ')'),
    # The registry-aware network buffer (1.20.5) is a plain FriendlyByteBuf on 1.19.2
    (re.compile(r'\bnew (?:net\.minecraft\.network\.)?RegistryFriendlyByteBuf\(([^;]*?),\s*(?:net\.minecraft\.core\.)?RegistryAccess\.EMPTY\)'), r'new net.minecraft.network.FriendlyByteBuf(\1)'),
    (re.compile(r'\bnet\.minecraft\.network\.RegistryFriendlyByteBuf\b'), 'net.minecraft.network.FriendlyByteBuf'),
    (re.compile(r'\bRegistryFriendlyByteBuf\b'), 'FriendlyByteBuf'),
    # ItemStack.isSameItem (1.20: same item, any data)
    (re.compile(r'\bItemStack\.isSameItem\('), 'ca.bradj.questown.mc.Compat.isSameItem('),
    (re.compile(r'"minecraft:short_grass"'), '"minecraft:grass"'),
    # NeoForge common tags named in code (tests, tag lookups) -> Forge 1.19.2 tags
    (re.compile(r'"(#?)c:(chests|mushrooms)"'), r'"\1forge:\2"'),
    # @OnlyIn(Dist.CLIENT): same annotation, Forge package
    (re.compile(r'\bnet\.neoforged\.api\.distmarker\b'), 'net.minecraftforge.api.distmarker'),
]

SEAM = re.compile(r'/questown/(mc/|loader/[A-Z]|core/network/|gui/RenderUtil\.java|gui/RenderContext\.java)')


# ---- structural rules (block / block-entity API shapes) -----------------------------------------

LOOT_RULES = [
    (re.compile(r'\bnet\.minecraft\.world\.level\.storage\.loot\.LootParams\b'), 'net.minecraft.world.level.storage.loot.LootContext'),
    (re.compile(r'\bLootParams\.Builder\b'), 'LootContext.Builder'),
    (re.compile(r'\bLootParams\b'), 'LootContext'),
    # Block#getRenderShape went protected in 1.20.5; 1.19.2 declares it public
    (re.compile(r'\bprotected RenderShape getRenderShape\('), 'public RenderShape getRenderShape('),
    # Recipe holders (1.20.2+): a recipe carries its own id on 1.19.2
    (re.compile(r'\.forEach\((\w+) -> (\w+)\.put\(\1\.id\(\), \1\.value\(\)\)\)'), r'.forEach(\1 -> \2.put(\1.getId(), \1))'),
    (re.compile(r'\.getResultItem\([^()]*registryAccess\(\)\)'), '.getResultItem()'),
    # Sound events that became holders in 1.20.x are plain SoundEvents on 1.19.2
    (re.compile(r'\bSoundEvents\.(\w+)\.value\(\)'), r'SoundEvents.\1'),
    # Item#appendHoverText took a Level before 1.20.5's TooltipContext
    (re.compile(r'(ItemStack \w+,\s*)(?:net\.minecraft\.world\.item\.)?Item\.TooltipContext (\w+)'),
     r'\1@org.jetbrains.annotations.Nullable net.minecraft.world.level.Level \2'),
    # A registry-lookup parameter (1.20.5 item NBT) is the level's RegistryAccess on 1.19.2
    (re.compile(r'\bnet\.minecraft\.core\.HolderLookup\.Provider\b'), 'net.minecraft.core.RegistryAccess'),
    # ItemInteractionResult (1.20.5) -> the mc/ shim
    (re.compile(r'\bnet\.minecraft\.world\.ItemInteractionResult\b'), 'ca.bradj.questown.mc.ItemInteractionResult'),
]


def _method_span(s: str, header_start: int):
    """(start, end) of a method whose header starts at header_start: through its matching brace."""
    brace = s.index('{', header_start)
    depth = 0
    for i in range(brace, len(s)):
        if s[i] == '{':
            depth += 1
        elif s[i] == '}':
            depth -= 1
            if depth == 0:
                return header_start, i + 1
    raise ValueError('unbalanced braces')


def _drop_override_before(s: str, idx: int) -> str:
    """Remove an @Override annotation directly above the declaration at idx."""
    head = s[:idx]
    m = re.search(r'@Override\s*$', head)
    if m:
        return head[:m.start()] + s[idx:]
    return s


_ANN = r'(?:@\w+ )*'
BE_SAVE = re.compile(r'(?:protected|public) void saveAdditional\(\s*' + _ANN + r'CompoundTag (\w+),\s*(?:net\.minecraft\.core\.)?(?:HolderLookup\.Provider|RegistryAccess) (\w+)\s*\)')
BE_LOAD = re.compile(r'(?:protected|public) void loadAdditional\(\s*' + _ANN + r'CompoundTag (\w+),\s*(?:net\.minecraft\.core\.)?(?:HolderLookup\.Provider|RegistryAccess) (\w+)\s*\)')
BE_HANDLE_UPDATE = re.compile(r'public void handleUpdateTag\(\s*' + _ANN + r'CompoundTag (\w+),\s*(?:net\.minecraft\.core\.)?(?:HolderLookup\.Provider|RegistryAccess) (\w+)\s*\)')
BE_GET_UPDATE = re.compile(r'public CompoundTag getUpdateTag\(\s*(?:net\.minecraft\.core\.)?(?:HolderLookup\.Provider|RegistryAccess) (\w+)\s*\)')
CODEC_METHOD = re.compile(r'(@Override\s*)?(?:protected|public) (?:com\.mojang\.serialization\.)?MapCodec<[^>]*(?:<[^>]*>)?[^>]*> codec\(\)\s*\{')
USE_DECL = re.compile(r'(?:public|protected) (?:net\.minecraft\.world\.|ca\.bradj\.questown\.mc\.)?(Item)?InteractionResult (useWithoutItem|useItemOn)\(')


def structural_pass_one(s: str) -> str:
    for rx, rep in LOOT_RULES:
        s = rx.sub(rep, s)

    # Block entity NBT hooks lost their registry-lookup parameter (1.20.5 added it)
    regs = set()
    for m in list(BE_SAVE.finditer(s)) + list(BE_LOAD.finditer(s)):
        regs.add(m.group(2))
    for m in BE_HANDLE_UPDATE.finditer(s):
        regs.add(m.group(2))
    for m in BE_GET_UPDATE.finditer(s):
        regs.add(m.group(1))
    s = BE_SAVE.sub(lambda m: f'protected void saveAdditional(CompoundTag {m.group(1)})', s)
    s = BE_LOAD.sub(lambda m: f'public void load(CompoundTag {m.group(1)})', s)
    s = BE_HANDLE_UPDATE.sub(lambda m: f'public void handleUpdateTag(CompoundTag {m.group(1)})', s)
    s = BE_GET_UPDATE.sub('public CompoundTag getUpdateTag()', s)
    for r in regs:
        s = re.sub(r'super\.handleUpdateTag\((\w+), ' + r + r'\)', r'super.handleUpdateTag(\1)', s)
        s = re.sub(r'super\.getUpdateTag\(' + r + r'\)', 'super.getUpdateTag()', s)
        s = re.sub(r'\bsaveWithoutMetadata\(' + r + r'\)', 'saveWithoutMetadata()', s)
        s = re.sub(r'super\.saveAdditional\((\w+), ' + r + r'\)', r'super.saveAdditional(\1)', s)
        s = re.sub(r'super\.loadAdditional\((\w+), ' + r + r'\)', r'super.load(\1)', s)
        s = re.sub(r'(ContainerHelper\.(?:save|load)AllItems\([^;]*?), ' + r + r'\)', r'\1)', s)

    # Entity#defineSynchedData(Builder) (1.20.5) -> defineSynchedData() over this.entityData
    m = re.search(r'protected void defineSynchedData\((?:net\.minecraft\.network\.syncher\.)?SynchedEntityData\.Builder (\w+)\)', s)
    if m:
        b = m.group(1)
        s = s.replace(m.group(0), 'protected void defineSynchedData()')
        s = re.sub(r'super\.defineSynchedData\(' + b + r'\)', 'super.defineSynchedData()', s)
        s = re.sub(r'(?<![\w.])' + b + r'\.define\(', 'this.entityData.define(', s)

    # Blocks' codec() (1.20.5 MapCodec registration) has no 1.19.2 counterpart
    while True:
        m = CODEC_METHOD.search(s)
        if not m:
            break
        start, end = _method_span(s, m.start())
        start = s.rfind('\n', 0, start) + 1  # from the start of the declaration's line
        # through the closing brace's line and any blank lines after it
        nl = s.find('\n', end)
        end = len(s) if nl < 0 else nl + 1
        while True:
            nxt = s.find('\n', end)
            if nxt < 0 or s[end:nxt].strip():
                break
            end = nxt + 1
        s = s[:start] + s[end:]

    # The 1.20.5 use split: generate 1.19.2's use(...) in vanilla 1.20.5's dispatch order
    decls = {m.group(2) for m in USE_DECL.finditer(s)}
    if decls and 'Generated by tools/port-1192.py' not in s:
        for name in ('useWithoutItem', 'useItemOn'):
            for m in reversed(list(re.finditer(r'(?:public|protected) (?:net\.minecraft\.world\.|ca\.bradj\.questown\.mc\.)?(?:Item)?InteractionResult ' + name + r'\(', s))):
                s = _drop_override_before(s, m.start())
        s = re.sub(r'super\.useWithoutItem\((\w+), (\w+), (\w+), (\w+), (\w+)\)',
                   r'super.use(\1, \2, \3, \4, net.minecraft.world.InteractionHand.MAIN_HAND, \5)', s)
        body = []
        if 'useItemOn' in decls:
            body.append('        ca.bradj.questown.mc.ItemInteractionResult qtItem = useItemOn(qtPlayer.getItemInHand(qtHand), qtState, qtLevel, qtPos, qtPlayer, qtHand, qtHit);')
            body.append('        if (qtItem.consumesAction()) {')
            body.append('            return qtItem.result();')
            body.append('        }')
            body.append('        if (qtItem != ca.bradj.questown.mc.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {')
            body.append('            return net.minecraft.world.InteractionResult.PASS;')
            body.append('        }')
        if 'useWithoutItem' in decls:
            body.append('        if (qtHand == net.minecraft.world.InteractionHand.MAIN_HAND) {')
            body.append('            return useWithoutItem(qtState, qtLevel, qtPos, qtPlayer, qtHit);')
            body.append('        }')
        body.append('        return net.minecraft.world.InteractionResult.PASS;')
        dispatcher = (
            '\n    // Generated by tools/port-1192.py: 1.19.2 has one use(...); 1.20.5 split it into\n'
            '    // useItemOn (the held stack) then useWithoutItem (main hand only), in this order.\n'
            '    @Override\n'
            '    public net.minecraft.world.InteractionResult use(\n'
            '            net.minecraft.world.level.block.state.BlockState qtState,\n'
            '            net.minecraft.world.level.Level qtLevel,\n'
            '            net.minecraft.core.BlockPos qtPos,\n'
            '            net.minecraft.world.entity.player.Player qtPlayer,\n'
            '            net.minecraft.world.InteractionHand qtHand,\n'
            '            net.minecraft.world.phys.BlockHitResult qtHit\n'
            '    ) {\n' + '\n'.join(body) + '\n    }\n'
        )
        last = s.rstrip().rfind('}')
        s = s[:last].rstrip('\n') + '\n' + dispatcher + '}\n'
    return s


def _dedupe_imports(s: str) -> str:
    seen = set()
    out = []
    for line in s.split('\n'):
        if line.startswith('import '):
            if line in seen:
                continue
            seen.add(line)
        out.append(line)
    return '\n'.join(out)


def text_pass(root: pathlib.Path) -> int:
    changed = 0
    for p in root.rglob('*.java'):
        if SEAM.search(str(p)):
            continue
        s = p.read_text()
        t = s
        for rx, rep in TEXT_RULES:
            t = rx.sub(rep, t)
        t = structural_pass_one(t)
        t = _dedupe_imports(t)
        if t != s:
            p.write_text(t)
            changed += 1
    return changed


ERR = re.compile(r'^(?P<file>/\S+\.java):(?P<line>\d+): error: cannot find symbol')


def errors_pass(root: pathlib.Path, log: pathlib.Path) -> int:
    """Rewrite `level()` / `serverLevel()` -> `getLevel()` where javac reports the method missing."""
    lines = log.read_text().splitlines()
    fixes = {}
    for i, l in enumerate(lines):
        m = ERR.match(l)
        if not m:
            continue
        window = '\n'.join(lines[i + 1:i + 5])
        sm = re.search(r'symbol:\s+method (level|serverLevel)\(\)', window)
        if not sm:
            continue
        # javac prints the source line then a caret line under the symbol.
        caret = next((x for x in lines[i + 1:i + 4] if x.strip() == '^'), None)
        src = lines[i + 1] if i + 1 < len(lines) else ''
        col = caret.index('^') if caret else None
        fixes.setdefault(m.group('file'), []).append((int(m.group('line')), col, sm.group(1)))
    changed = 0
    for f, items in fixes.items():
        p = pathlib.Path(f)
        # The accessor rename is mechanical everywhere but the neutral seam itself.
        if not str(p).startswith(str(root.resolve())) or re.search(r'/questown/(mc/|loader/[A-Z])', f):
            continue
        rows = p.read_text().split('\n')
        for line, col, name in items:
            row = rows[line - 1]
            call = name + '()'
            if col is not None and row[col:col + len(call)] == call:
                rows[line - 1] = row[:col] + 'getLevel()' + row[col + len(call):]
            else:
                rows[line - 1] = re.sub(r'(?<![\w])' + name + r'\(\)', 'getLevel()', row, count=1)
        p.write_text('\n'.join(rows))
        changed += 1
    return changed


# ---- compiler-driven call rewrites ----------------------------------------------------------------
# Each rule names a method javac reports missing (or a call it rejects) and how to rewrite that one
# call. The receiver is found by walking back from the '.' over a balanced Java expression.

def _receiver_start(row: str, dot: int) -> int:
    i = dot - 1
    depth = 0
    while i >= 0:
        c = row[i]
        if c in ')]':
            depth += 1
        elif c in '([':
            if depth == 0:
                break
            depth -= 1
        elif depth == 0 and not (c.isalnum() or c in '_.$'):
            break
        i -= 1
    return i + 1


def _args_end(text: str, open_paren: int) -> int:
    depth = 0
    for i in range(open_paren, len(text)):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                return i
    raise ValueError('unbalanced call')


COMPAT = 'ca.bradj.questown.mc.Compat'

# method -> rewrite(receiver, args) for a call javac says doesn't exist on 1.19.2
CALL_RULES = {
    'copyWithCount': lambda r, a: f'{COMPAT}.copyWithCount({r}, {a})',
    'isSolid': lambda r, a: f'{COMPAT}.isSolid({r})',
    'canBeReplaced': lambda r, a: f'{COMPAT}.canBeReplaced({r})',
    # AbstractWidget position accessors (1.19.3+); 1.19.2 has public x / y fields
    'getX': lambda r, a: f'{r}.x',
    'getY': lambda r, a: f'{r}.y',
    'setX': lambda r, a: f'{r}.x = {a}',
    'setY': lambda r, a: f'{r}.y = {a}',
}


def _rewrite_call(text: str, offset: int, name: str):
    """Rewrite the call of `name` whose '.' is at text[offset]; None when it doesn't match."""
    if text[offset:offset + 1 + len(name) + 1] != '.' + name + '(':
        return None
    line_start = text.rfind('\n', 0, offset) + 1
    start = line_start + _receiver_start(text[line_start:offset + 1], offset - line_start)
    receiver = text[start:offset]
    open_paren = offset + 1 + len(name)
    close = _args_end(text, open_paren)
    args = text[open_paren + 1:close].strip()
    return text[:start] + CALL_RULES[name](receiver, args) + text[close + 1:]


def call_rules_pass(root: pathlib.Path, log: pathlib.Path) -> int:
    lines = log.read_text().splitlines()
    todo = {}
    for i, l in enumerate(lines):
        m = re.match(r'^(?P<file>/\S+\.java):(?P<line>\d+): error: (?P<msg>.*)$', l)
        if not m or not str(pathlib.Path(m.group('file'))).startswith(str(root.resolve())):
            continue
        if re.search(r'/questown/(mc/|loader/[A-Z])', m.group('file')):
            continue
        caret = next((x for x in lines[i + 1:i + 4] if x.strip() == '^'), None)
        if caret is None:
            continue
        col = caret.index('^')
        msg = m.group('msg')
        name = None
        if msg.startswith('cannot find symbol'):
            sm = re.search(r'symbol:\s+method (\w+)\(', '\n'.join(lines[i + 1:i + 5]))
            name = sm.group(1) if sm else None
        elif msg.startswith('no suitable method found for canBeReplaced(no arguments)'):
            name = 'canBeReplaced'
        elif 'Component is not a functional interface' in msg:
            name = '<supplier>'
        if name in CALL_RULES or name == '<supplier>':
            todo.setdefault(m.group('file'), []).append((int(m.group('line')), col, name))
    changed = 0
    for f, items in todo.items():
        p = pathlib.Path(f)
        text = p.read_text()
        starts = [0]
        for idx, ch in enumerate(text):
            if ch == '\n':
                starts.append(idx + 1)
        # bottom-up so earlier offsets stay valid
        for line, col, name in sorted(items, key=lambda t: (t[0], t[1]), reverse=True):
            off = starts[line - 1] + col
            if name == '<supplier>':
                # javac points at the call's '.'; drop the leading `() ->` of its first argument
                paren = text.index('(', off)
                m2 = re.compile(r'\(\s*\(\)\s*->\s*').match(text, paren)
                if m2:
                    text = text[:paren + 1] + text[m2.end():]
                continue
            # javac's caret sits on the '.' before the method (or on the name itself)
            dot = off if text[off] == '.' else text.rfind('.', 0, off + 1)
            new = _rewrite_call(text, dot, name)
            if new is not None:
                text = new
        p.write_text(text)
        changed += 1
    return changed


# ---- data (JSON) rules ----------------------------------------------------------------------------

DATA_RULES = [
    # NeoForge common tags (c:) -> Forge 1.19.2 tags (forge:)
    (re.compile(r'"(#?)c:(chests|mushrooms)"'), r'"\1forge:\2"'),
    # 1.20.3 renamed grass -> short_grass
    (re.compile(r'"minecraft:short_grass"'), '"minecraft:grass"'),
    # Vanilla armor-slot tags (1.20.5) -> Forge 1.19.2's armor tags
    (re.compile(r'"#minecraft:head_armor"'), '"#forge:armors/helmets"'),
    (re.compile(r'"#minecraft:chest_armor"'), '"#forge:armors/chestplates"'),
    (re.compile(r'"#minecraft:leg_armor"'), '"#forge:armors/leggings"'),
    (re.compile(r'"#minecraft:foot_armor"'), '"#forge:armors/boots"'),
]


def _items_as_list(obj):
    """Advancement item predicates list their items (1.19.2); 1.20.5 accepts a single string."""
    if isinstance(obj, dict):
        for k, v in list(obj.items()):
            if k == 'items' and isinstance(v, str):
                obj[k] = [v]
            else:
                _items_as_list(v)
    elif isinstance(obj, list):
        for v in obj:
            _items_as_list(v)


def _recipe_result_item(obj):
    """Crafting results name their item with "id" since 1.20.5; 1.19.2 reads "item"."""
    if isinstance(obj, dict):
        res = obj.get('result')
        if isinstance(res, dict) and 'id' in res and 'item' not in res:
            res['item'] = res.pop('id')
        for v in obj.values():
            _recipe_result_item(v)
    elif isinstance(obj, list):
        for v in obj:
            _recipe_result_item(v)


def _drop_shadowed_c_keys(s: str) -> str:
    """Remove a "c:..." key line when the same object already names its "forge:..." twin (lang files
    carry both; renaming would leave a duplicate key)."""
    out = []
    for line in s.split('\n'):
        m = re.match(r'\s*"(#?)c:(chests|mushrooms)"\s*:', line)
        if m and re.search(r'"' + re.escape(m.group(1)) + r'forge:' + m.group(2) + r'"\s*:', s):
            continue
        out.append(line)
    return '\n'.join(out)


def data_pass(root: pathlib.Path) -> int:
    import json
    changed = 0
    for p in root.rglob('*.json'):
        s = p.read_text()
        t = _drop_shadowed_c_keys(s)
        for rx, rep in DATA_RULES:
            t = rx.sub(rep, t)
        if '/advancements/' in str(p) and '"items"' in t:
            obj = json.loads(t)
            before = json.dumps(obj, sort_keys=True)
            _items_as_list(obj)
            if json.dumps(obj, sort_keys=True) != before:
                t = json.dumps(obj, indent=2) + '\n'
        if '/recipes/' in str(p) and '"id"' in t:
            obj = json.loads(t)
            before = json.dumps(obj, sort_keys=True)
            _recipe_result_item(obj)
            if json.dumps(obj, sort_keys=True) != before:
                t = json.dumps(obj, indent=2) + '\n'
        if t != s:
            p.write_text(t)
            changed += 1
    return changed


if __name__ == '__main__':
    mode, root = sys.argv[1], pathlib.Path(sys.argv[2])
    if mode == 'text':
        print(f'text: {text_pass(root)} file(s) rewritten')
    elif mode == 'data':
        print(f'data: {data_pass(root)} file(s) rewritten')
    elif mode == 'errors':
        log = pathlib.Path(sys.argv[3])
        print(f'errors: {errors_pass(root, log)} file(s) rewritten (accessors)')
        print(f'errors: {call_rules_pass(root, log)} file(s) rewritten (calls)')
    else:
        sys.exit(__doc__)
