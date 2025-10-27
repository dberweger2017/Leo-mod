package fuzuki.test.common.entity

import fuzuki.test.common.MOD_ID
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.*
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventories
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTable
import net.minecraft.nbt.NbtCompound
import net.minecraft.entity.damage.DamageSource
import net.minecraft.enchantment.Enchantments
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Box
import net.minecraft.util.math.random.Random
import net.minecraft.text.Text
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import net.minecraft.world.biome.Biome
import net.minecraft.world.event.GameEvent
import net.minecraft.world.GameRules
import net.minecraft.util.Hand
import java.util.EnumSet
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.min

class UndeadMinerEntity(entityType: EntityType<out UndeadMinerEntity>, world: World) :
    ZombieEntity(entityType, world) {

    init {
        experiencePoints = EXPERIENCE
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(VARIANT, Variant.STONE.id)
    }

    private var variant: Variant
        get() = Variant.byId(dataTracker.get(VARIANT))
        set(value) {
            dataTracker.set(VARIANT, value.id)
        }

    val variantTexture: Identifier
        get() = variant.texture

    private fun debug(msg: String) {
        println("[UndeadMiner] $msg")
        if (!world.isClient) {
            world.players.forEach { player ->
                player.sendMessage(Text.literal("§7[§cMiner§7] §f$msg"), false)
            }
        }
    }

    private val lootSatchel: DefaultedList<ItemStack> = DefaultedList.ofSize(27, ItemStack.EMPTY)

    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?
    ): EntityData? {
        val biomeEntry: RegistryEntry<Biome> = world.getBiome(blockPos)
        variant = getRandomVariant(world.random, biomeEntry)
        setCanPickUpLoot(false)
        return super.initialize(world, difficulty, spawnReason, entityData)
    }

    override fun initEquipment(random: Random, difficulty: LocalDifficulty) {
        super.initEquipment(random, difficulty)
        val pickaxe = ItemStack(variant.pickaxe)
        world.registryManager.getOptional(RegistryKeys.ENCHANTMENT).ifPresent { registry ->
            registry.getEntry(Enchantments.EFFICIENCY).ifPresent { entry ->
                pickaxe.addEnchantment(entry, 5)
            }
        }
        equipStack(EquipmentSlot.MAINHAND, pickaxe)
    }

    override fun initGoals() {
        super.initGoals()
        // Through-walls targeting so tunneling makes sense
        targetSelector.add(0, AcquirePlayerTargetThroughWallsGoal(this))
        targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, 0, false, false, null))

        // Priorities: 2 = tunneling to player; 3 = melee; 4 = vandalize through walls when idle
        goalSelector.add(2, TunnelToTargetGoal(this))
        goalSelector.add(3, MeleeAttackGoal(this, 1.0, false))
        goalSelector.add(4, VandalizeThroughWallsGoal(this)) // ← new tunneling vandal goal
    }

    override fun isBaby(): Boolean = false
    override fun setBaby(baby: Boolean) {}
    override fun burnsInDaylight(): Boolean = false

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putInt(VARIANT_KEY, variant.id)
        val satchelNbt = NbtCompound()
        Inventories.writeNbt(satchelNbt, lootSatchel, true, world.registryManager)
        nbt.put(SATCHEL_KEY, satchelNbt)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        variant = Variant.byId(nbt.getInt(VARIANT_KEY))
        if (nbt.contains(SATCHEL_KEY)) {
            Inventories.readNbt(nbt.getCompound(SATCHEL_KEY), lootSatchel, world.registryManager)
        }
    }

    override fun getLootTableId(): RegistryKey<LootTable> = variant.lootTable

    override fun onDeath(source: DamageSource) {
        super.onDeath(source)
        if (!world.isClient) {
            lootSatchel.forEach { stack -> if (!stack.isEmpty) dropStack(stack.copy()) }
            for (i in lootSatchel.indices) lootSatchel[i] = ItemStack.EMPTY
        }
    }

    companion object {
        private const val EXPERIENCE = 6
        private const val VARIANT_KEY = "Variant"
        private const val SATCHEL_KEY = "Satchel"
        private val VARIANT: TrackedData<Int> =
            DataTracker.registerData(UndeadMinerEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private const val MAX_TUNNEL_RANGE_SQ = 32.0 * 32.0
        private const val MAX_SPAWN_Y = 63
        private const val MINING_REACH_BLOCKS = 4.0
        private const val MINING_REACH_SQ = MINING_REACH_BLOCKS * MINING_REACH_BLOCKS

        fun createAttributes(): DefaultAttributeContainer.Builder =
            createZombieAttributes()
                .add(EntityAttributes.ZOMBIE_SPAWN_REINFORCEMENTS, 0.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0)

        fun canSpawn(
            type: EntityType<out UndeadMinerEntity>,
            world: ServerWorldAccess,
            spawnReason: SpawnReason,
            pos: BlockPos,
            random: Random
        ): Boolean {
            if (!MobEntity.canMobSpawn(type, world, spawnReason, pos, random)) return false
            if (spawnReason != SpawnReason.SPAWNER && world.isSkyVisible(pos)) return false
            return pos.y <= MAX_SPAWN_Y
        }

        private fun getRandomVariant(random: Random, biome: RegistryEntry<Biome>): Variant {
            val roll = random.nextInt(50)
            return when {
                roll <= 15 -> Variant.IRON
                roll >= 45 -> Variant.DIAMOND
                biome.isIn(BiomeTags.IS_BADLANDS) -> Variant.GOLD
                else -> Variant.STONE
            }
        }
    }

    enum class Variant(val id: Int, private val textureName: String, val pickaxe: Item, lootName: String) {
        STONE(0, "undead_miner_stone", Items.STONE_PICKAXE, "undead_miner_stone"),
        IRON(1, "undead_miner_iron", Items.IRON_PICKAXE, "undead_miner_iron"),
        DIAMOND(2, "undead_miner_diamond", Items.DIAMOND_PICKAXE, "undead_miner_diamond"),
        GOLD(3, "undead_miner_gold", Items.GOLDEN_PICKAXE, "undead_miner_gold");

        val texture: Identifier = Identifier.of(MOD_ID, "textures/entity/undead_miner/$textureName.png")
        val lootTable: RegistryKey<LootTable> =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(MOD_ID, "entities/$lootName"))

        companion object {
            private val BY_ID = entries.associateBy { it.id }
            fun byId(id: Int): Variant = BY_ID[id] ?: STONE
        }
    }

    // --- Loot satchel utils ---------------------------------------------------

    private fun storeDrops(stacks: List<ItemStack>) {
        stacks.forEach { storeDrop(it) }
    }

    private fun storeDrop(stack: ItemStack) {
        if (stack.isEmpty) return
        var remaining = stack.copy()

        // Fill existing stacks
        for (i in lootSatchel.indices) {
            val cur = lootSatchel[i]
            if (!cur.isEmpty && ItemStack.areItemsAndComponentsEqual(cur, remaining)) {
                val move = min(remaining.count, cur.maxCount - cur.count)
                if (move > 0) {
                    cur.increment(move)
                    remaining.decrement(move)
                    if (remaining.isEmpty) return
                }
            }
        }
        // Use empty slot
        for (i in lootSatchel.indices) {
            if (lootSatchel[i].isEmpty) {
                lootSatchel[i] = remaining.copy()
                return
            }
        }
        // Drop overflow
        if (!world.isClient) dropStack(remaining.copy())
        remaining.count = 0
    }

    // --- Shared block-breaking (used by all goals) ----------------------------

    internal fun breakBlockServerSide(pos: BlockPos) {
        val serverWorld = world as? ServerWorld ?: return
        if (!serverWorld.isChunkLoaded(pos)) {
            debug("Chunk not loaded at $pos — abort")
            return
        }
        if (!serverWorld.gameRules.getBoolean(GameRules.DO_MOB_GRIEFING)) {
            debug("mobGriefing=false — abort")
            return
        }

        val before = serverWorld.getBlockState(pos)
        val hardness = before.getHardness(serverWorld, pos)
        debug("Pre: state=$before, hardness=$hardness, fluid=${!before.fluidState.isEmpty}, air=${before.isAir}")

        if (!isBreakableForMiner(before, pos)) {
            debug("Not breakable by our rules at $pos ($before)")
            return
        }

        val removed = serverWorld.breakBlock(pos, /*drop=*/true, /*breaker=*/this)
        val after = serverWorld.getBlockState(pos)
        debug("breakBlock() -> removed=$removed, postState=$after (air=${after.isAir})")

        if (!removed || !after.isAir) {
            debug("Vanilla break failed or block still present — applying fallback removeBlock()")
            val fallbackRemoved = serverWorld.removeBlock(pos, /*move=*/false)
            val afterFallback = serverWorld.getBlockState(pos)
            debug("removeBlock() -> removed=$fallbackRemoved, postState=$afterFallback")
            if (fallbackRemoved) {
                serverWorld.syncWorldEvent(2001, pos, Block.getRawIdFromState(before))
                serverWorld.emitGameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Emitter.of(this, before))
                serverWorld.updateNeighborsAlways(pos, before.block)
            }
        }

        serverWorld.markForUpdate(pos)
        captureNearbyDrops(serverWorld, pos)
    }

    private fun isBreakableForMiner(state: BlockState, pos: BlockPos): Boolean {
        if (state.isAir) return false
        if (state.getHardness(world, pos) < 0f) return false
        if (state.block === Blocks.BEDROCK) return false
        if (!state.fluidState.isEmpty) return false
        return true
    }

    private fun captureNearbyDrops(serverWorld: ServerWorld, pos: BlockPos) {
        val box = Box.of(Vec3d.ofCenter(pos), 2.5, 2.5, 2.5)
        val items = serverWorld.getEntitiesByClass(ItemEntity::class.java, box) { !it.isRemoved }
        if (items.isEmpty()) return
        items.forEach { item ->
            val stack = item.stack.copy()
            if (!stack.isEmpty) storeDrops(listOf(stack))
            item.discard()
        }
        debug("Collected ${items.size} drops into satchel at $pos")
    }

    // --- Dig planner (stairs + 2-high tunnel) --------------------------------

    private data class PathNode(
        val pos: BlockPos,
        val gCost: Double,
        val hCost: Double,
        val parent: PathNode?
    ) : Comparable<PathNode> {
        val fCost: Double = gCost + hCost

        override fun compareTo(other: PathNode): Int = fCost.compareTo(other.fCost)
    }

    private fun isSolid(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return !state.isAir && !state.getCollisionShape(world, pos).isEmpty
    }

    private fun nextDigBlockTowards(from: BlockPos, dest: BlockPos): BlockPos? {
        val path = findDigPath(from, dest, maxSearchNodes = 200)
        return path?.firstOrNull()
    }

    private fun findDigPath(start: BlockPos, goal: BlockPos, maxSearchNodes: Int): List<BlockPos>? {
        val openSet = PriorityQueue<PathNode>()
        val closedSet = mutableSetOf<BlockPos>()
        val costMap = mutableMapOf<BlockPos, Double>()

        val startNode = PathNode(start, 0.0, heuristic(start, goal), null)
        openSet.add(startNode)
        costMap[start] = 0.0

        var nodesSearched = 0

        while (openSet.isNotEmpty() && nodesSearched < maxSearchNodes) {
            val current = openSet.poll()
            nodesSearched++

            if (current.pos == goal) {
                return reconstructPath(current).drop(1)
            }

            closedSet.add(current.pos)

            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue

                        val neighbor = current.pos.add(dx, dy, dz)
                        if (neighbor in closedSet) continue

                        if (neighbor.getSquaredDistance(goal) > MAX_TUNNEL_RANGE_SQ) continue

                        val moveCost = getDigCost(current.pos, neighbor)
                        if (moveCost < 0) continue

                        val tentativeGCost = current.gCost + moveCost
                        if (tentativeGCost >= costMap.getOrDefault(neighbor, Double.MAX_VALUE)) continue

                        costMap[neighbor] = tentativeGCost
                        openSet.add(
                            PathNode(
                                neighbor,
                                tentativeGCost,
                                heuristic(neighbor, goal),
                                current
                            )
                        )
                    }
                }
            }
        }

        return findSimpleDigTarget(start, goal)?.let { listOf(it) }
    }

    private fun reconstructPath(node: PathNode): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        var current: PathNode? = node
        while (current != null) {
            path.add(current.pos)
            current = current.parent
        }
        return path.reversed()
    }

    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(to.x - from.x).toDouble()
        val dy = abs(to.y - from.y).toDouble() * 1.2
        val dz = abs(to.z - from.z).toDouble()
        return dx + dy + dz
    }

    private fun getDigCost(from: BlockPos, to: BlockPos): Double {
        val dx = abs(to.x - from.x)
        val dy = abs(to.y - from.y)
        val dz = abs(to.z - from.z)

        if (dx + dy + dz > 1) {
            if (dx > 0 && dy > 0 && dz > 0) return -1.0
            if ((dx > 0 && dy > 0) || (dx > 0 && dz > 0) || (dy > 0 && dz > 0)) {
                val cost = getBlockBreakCost(to)
                return if (cost < 0) -1.0 else cost * 1.4
            }
        }

        return getBlockBreakCost(to)
    }

    private fun getBlockBreakCost(pos: BlockPos): Double {
        val state = world.getBlockState(pos)
        if (state.isAir) return 0.1
        if (!isBreakableForMiner(state, pos)) return -1.0

        val hardness = state.getHardness(world, pos)
        val baseCost = when {
            hardness <= 0.5f -> 1.0
            hardness <= 2.0f -> 2.0
            hardness <= 5.0f -> 4.0
            else -> 8.0
        }

        val block = state.block
        return when {
            block === Blocks.DIRT || block === Blocks.STONE ||
                block === Blocks.COBBLESTONE || block === Blocks.GRAVEL -> baseCost * 0.8

            block === Blocks.DIAMOND_ORE || block === Blocks.EMERALD_ORE -> baseCost * 3.0
            block === Blocks.SPAWNER -> baseCost * 10.0
            else -> baseCost
        }
    }

    private fun findSimpleDigTarget(from: BlockPos, dest: BlockPos): BlockPos? {
        val dx = Integer.signum(dest.x - from.x)
        val dy = Integer.signum(dest.y - from.y)
        val dz = Integer.signum(dest.z - from.z)

        val candidates = listOf(
            from.add(dx, 0, 0),
            from.add(0, 0, dz),
            from.add(dx, dy, 0),
            from.add(0, dy, dz),
            from.add(dx, 0, dz),
            from.add(0, dy, 0),
            from.add(dx, dy, dz)
        )

        return candidates.find { pos ->
            val state = world.getBlockState(pos)
            !state.isAir && isBreakableForMiner(state, pos)
        }
    }

    private fun digTunnelAt(pos: BlockPos) {
        breakBlockServerSide(pos)
        val up = pos.up()
        if (isSolid(up)) breakBlockServerSide(up)

        val down = pos.down()
        if (isSolid(down) && needsFloorClearing(pos)) {
            breakBlockServerSide(down)
        }
    }

    private fun needsFloorClearing(pos: BlockPos): Boolean {
        val surrounding = listOf(pos.north(), pos.south(), pos.east(), pos.west())
        return surrounding.count { isSolid(it) } >= 3
    }

    // --- Acquire target through walls (no LOS) --------------------------------

    private class AcquirePlayerTargetThroughWallsGoal(private val mob: MobEntity) : Goal() {
        private var candidate: PlayerEntity? = null

        init { setControls(EnumSet.noneOf(Control::class.java)) }

        override fun canStart(): Boolean {
            val cur = mob.target
            if (cur is PlayerEntity && cur.isAlive) return false

            val range = mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE).toDouble()
            val player = mob.world.getClosestPlayer(mob.x, mob.y, mob.z, range) { p ->
                p != null && p.isAlive && !p.isSpectator &&
                    (p !is ServerPlayerEntity || !p.interactionManager.isCreative)
            }
            candidate = player
            return candidate != null
        }

        override fun start() {
            mob.target = candidate
            candidate = null
        }

        override fun shouldContinue(): Boolean = false
    }

    // --- Tunneling to PLAYER (sticky) -----------------------------------------

    private class TunnelToTargetGoal(private val miner: UndeadMinerEntity) : Goal() {
        private val world: World = miner.world
        private var digPos: BlockPos? = null
        private var miningTicks = 0
        private var ticksPerBlock = 20 // adjusted by hardness
        private var lastProgressPos: BlockPos? = null

        init { setControls(EnumSet.of(Control.MOVE, Control.LOOK)) }

        override fun canStart(): Boolean {
            val target = miner.target ?: return false
            if (!target.isAlive) return false
            if (miner.squaredDistanceTo(target) > MAX_TUNNEL_RANGE_SQ) return false

            digPos = miner.nextDigBlockTowards(miner.blockPos, target.blockPos)
            miner.debug("canStart -> dig=$digPos toward=${target.blockPos}")
            return digPos != null
        }

        override fun shouldContinue(): Boolean {
            val target = miner.target ?: return false
            if (!target.isAlive) return false
            if (miner.squaredDistanceTo(target) > MAX_TUNNEL_RANGE_SQ) return false

            val pos = digPos ?: return false
            val state = world.getBlockState(pos)
            return !state.isAir && miner.isBreakableForMiner(state, pos)
        }

        override fun start() {
            miningTicks = 0
            miner.navigation.stop()
            miner.isAttacking = false
            clearCrackProgress()
        }

        override fun stop() {
            clearCrackProgress()
            digPos = null
            miningTicks = 0
        }

        override fun tick() {
            val target = miner.target ?: return
            if (!target.isAlive) return

            if (digPos == null || world.getBlockState(digPos).isAir) {
                digPos = miner.nextDigBlockTowards(miner.blockPos, target.blockPos)
                miningTicks = 0
                clearCrackProgress()
                if (digPos == null) return
            }
            val pos = digPos!!
            val center = Vec3d.ofCenter(pos)

            miner.lookControl.lookAt(center.x, center.y, center.z)
            if (miner.squaredDistanceTo(center) > MINING_REACH_SQ) {
                miner.navigation.startMovingTo(center.x, center.y, center.z, 1.0)
                showMiningProgress(pos, miningTicks, ticksPerBlock)
                return
            } else {
                miner.navigation.stop()
            }

            val state = world.getBlockState(pos)
            if (state.isAir || !miner.isBreakableForMiner(state, pos)) {
                digPos = null
                miningTicks = 0
                clearCrackProgress()
                return
            }

            adjustTicksPerBlock(state, pos)

            miningTicks++
            showMiningProgress(pos, miningTicks, ticksPerBlock)
            if (miningTicks % 10 == 0) miner.swingHand(Hand.MAIN_HAND)

            if (miningTicks >= ticksPerBlock) {
                miner.digTunnelAt(pos)
                miningTicks = 0
                clearCrackProgress()
                digPos = miner.nextDigBlockTowards(miner.blockPos, target.blockPos)
            }
        }

        private fun adjustTicksPerBlock(state: BlockState, pos: BlockPos) {
            val hardness = state.getHardness(world, pos)
            val base = 12
            val scaled = (base + (hardness * 40f)).toInt().coerceAtLeast(5).coerceAtMost(80)
            ticksPerBlock = scaled
        }

        private fun showMiningProgress(pos: BlockPos, elapsed: Int, total: Int) {
            if (world.isClient) return
            lastProgressPos = pos
            val stage = ((elapsed.toFloat() / total.toFloat()) * 9f).toInt().coerceIn(0, 9)
            world.setBlockBreakingInfo(miner.id, pos, stage)
        }

        private fun clearCrackProgress() {
            val p = lastProgressPos ?: return
            lastProgressPos = null
            if (!world.isClient) world.setBlockBreakingInfo(miner.id, p, -1)
        }

    }

    // --- Vandalize through walls when idle ------------------------------------

    private class VandalizeThroughWallsGoal(private val miner: UndeadMinerEntity) : Goal() {
        private val world: World = miner.world
        private var vandalTarget: BlockPos? = null     // the actual vandal block to break
        private var digPos: BlockPos? = null           // current tunnel block along the way
        private var miningTicks = 0
        private var ticksPerBlock = 15 // a bit faster for vandalizing
        private var lastProgressPos: BlockPos? = null

        init { setControls(EnumSet.of(Control.MOVE, Control.LOOK)) }

        override fun canStart(): Boolean {
            // Only when NOT pursuing a player target
            val t = miner.target
            if (t is PlayerEntity && t.isAlive) return false

            vandalTarget = findNearestVandalTarget() ?: return false
            digPos = miner.nextDigBlockTowards(miner.blockPos, vandalTarget!!)
            return digPos != null || miner.blockPos.isWithinDistance(
                Vec3d.ofCenter(vandalTarget!!),
                MINING_REACH_BLOCKS + 0.1
            )
        }

        override fun shouldContinue(): Boolean {
            // Abort if a player target appears
            val t = miner.target
            if (t is PlayerEntity && t.isAlive) return false
            val vt = vandalTarget ?: return false
            val st = world.getBlockState(vt)
            // stop if target disappeared or became unbreakable
            if (st.isAir || !miner.isBreakableForMiner(st, vt) || !isVandalTarget(st)) return false
            return true
        }

        override fun start() {
            miningTicks = 0
            miner.navigation.stop()
            clearCrackProgress()
        }

        override fun stop() {
            clearCrackProgress()
            vandalTarget = null
            digPos = null
            miningTicks = 0
        }

        override fun tick() {
            val vt = vandalTarget ?: return

            // If we can directly reach the vandal block, go break it
            if (closeEnoughToBreak(vt) && lineUnobstructed(vt)) {
                // Move closer if just slightly out of reach
                val c = Vec3d.ofCenter(vt)
                if (miner.squaredDistanceTo(c) > MINING_REACH_SQ) {
                    miner.navigation.startMovingTo(c.x, c.y, c.z, 1.0)
                    return
                } else {
                    miner.navigation.stop()
                }

                // Break the vandal block itself
                mineCurrent(vt)
                return
            }

            // Otherwise, keep tunneling using planner
            if (digPos == null || world.getBlockState(digPos).isAir) {
                digPos = miner.nextDigBlockTowards(miner.blockPos, vt)
                miningTicks = 0
                clearCrackProgress()
                if (digPos == null) return
            }

            // Approach and mine the blocking block
            val pos = digPos!!
            val center = Vec3d.ofCenter(pos)

            miner.lookControl.lookAt(center.x, center.y, center.z)
            if (miner.squaredDistanceTo(center) > MINING_REACH_SQ) {
                miner.navigation.startMovingTo(center.x, center.y, center.z, 1.0)
                showMiningProgress(pos, miningTicks, ticksPerBlock)
                return
            } else {
                miner.navigation.stop()
            }

            val state = world.getBlockState(pos)
            if (state.isAir || !miner.isBreakableForMiner(state, pos)) {
                digPos = null
                miningTicks = 0
                clearCrackProgress()
                return
            }

            // Fixed-ish speed for vandal tunneling
            miningTicks++
            showMiningProgress(pos, miningTicks, ticksPerBlock)
            if (miningTicks % 10 == 0) miner.swingHand(Hand.MAIN_HAND)

            if (miningTicks >= ticksPerBlock) {
                miner.digTunnelAt(pos)
                miningTicks = 0
                clearCrackProgress()
                digPos = miner.nextDigBlockTowards(miner.blockPos, vt)
            }
        }

        // ----- helpers -----

        private fun closeEnoughToBreak(pos: BlockPos): Boolean {
            return miner.squaredDistanceTo(Vec3d.ofCenter(pos)) <= MINING_REACH_SQ
        }

        private fun lineUnobstructed(dest: BlockPos): Boolean {
            // crude ray march: if we find any solid block along the way, it's obstructed
            var x = miner.x
            var y = miner.y + miner.standingEyeHeight.toDouble()
            var z = miner.z
            val to = Vec3d.ofCenter(dest)
            val dir = to.subtract(x, y, z).normalize()
            val steps = 24
            repeat(steps) {
                x += dir.x
                y += dir.y
                z += dir.z
                val p = BlockPos.ofFloored(x, y, z)
                val s = world.getBlockState(p)
                if (!s.isAir && !s.getCollisionShape(world, p).isEmpty) return false
            }
            return true
        }

        private fun mineCurrent(pos: BlockPos) {
            val state = world.getBlockState(pos)
            if (state.isAir || !isVandalTarget(state) || !miner.isBreakableForMiner(state, pos)) {
                vandalTarget = null
                miningTicks = 0
                clearCrackProgress()
                return
            }

            // Approach handled earlier; here we just mine
            miningTicks++
            showMiningProgress(pos, miningTicks, ticksPerBlock)
            if (miningTicks % 10 == 0) miner.swingHand(Hand.MAIN_HAND)
            if (miningTicks >= ticksPerBlock) {
                miner.digTunnelAt(pos)
                miningTicks = 0
                clearCrackProgress()
                vandalTarget = null // pick a new vandal target next cycle
            }
        }

        private fun findNearestVandalTarget(): BlockPos? {
            val r = 10 // search radius
            val origin = miner.blockPos
            var bestPos: BlockPos? = null
            var bestDistSq = Double.MAX_VALUE

            for (y in -2..2) {
                for (x in -r..r) {
                    for (z in -r..r) {
                        val p = origin.add(x, y, z)
                        val st = world.getBlockState(p)
                        if (isVandalTarget(st) && miner.isBreakableForMiner(st, p)) {
                            val dsq = origin.getSquaredDistance(p)
                            if (dsq < bestDistSq) {
                                bestDistSq = dsq
                                bestPos = p
                            }
                        }
                    }
                }
            }
            return bestPos
        }

        private fun isVandalTarget(state: BlockState): Boolean {
            val b = state.block
            // Torches (regular, soul, redstone; wall variants included)
            if (b === Blocks.TORCH || b === Blocks.WALL_TORCH ||
                b === Blocks.SOUL_TORCH || b === Blocks.SOUL_WALL_TORCH ||
                b === Blocks.REDSTONE_TORCH || b === Blocks.REDSTONE_WALL_TORCH
            ) return true

            // Crafting table & furnaces
            if (b === Blocks.CRAFTING_TABLE ||
                b === Blocks.FURNACE || b === Blocks.BLAST_FURNACE || b === Blocks.SMOKER
            ) return true

            // Chests / containers
            if (b === Blocks.CHEST || b === Blocks.TRAPPED_CHEST || b === Blocks.BARREL) return true

            // Doors / Fences / Gates via tags
            if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.FENCE_GATES)) return true

            return false
        }

        private fun showMiningProgress(pos: BlockPos, elapsed: Int, total: Int) {
            if (world.isClient) return
            lastProgressPos = pos
            val stage = ((elapsed.toFloat() / total.toFloat()) * 9f).toInt().coerceIn(0, 9)
            world.setBlockBreakingInfo(miner.id, pos, stage)
        }

        private fun clearCrackProgress() {
            val p = lastProgressPos ?: return
            lastProgressPos = null
            if (!world.isClient) world.setBlockBreakingInfo(miner.id, p, -1)
        }
    }
}

private fun ServerWorld.markForUpdate(pos: BlockPos) {
    this.chunkManager.markForUpdate(pos)
}
