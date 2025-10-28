package fuzuki.test.common.entity

import fuzuki.test.common.MOD_ID
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.*
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
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
import net.minecraft.particle.ParticleTypes
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

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
    private var nextEnemyScanTick: Long = 0

    private fun currentEnemyTarget(): LivingEntity? {
        val candidate = target as? LivingEntity ?: return null
        if (!candidate.isAlive) return null
        if (!isValidEnemyTarget(candidate)) return null
        if (squaredDistanceTo(candidate) > MAX_TUNNEL_RANGE_SQ) return null
        return candidate
    }

    private fun refreshEnemyTarget(): LivingEntity? {
        val found = acquireEnemyTarget(force = false)
        if (found != null) {
            if (target !== found) {
                target = found
            }
            return found
        }
        target = null
        return null
    }

    private fun hasEnemyTargetInRange(): Boolean {
        return acquireEnemyTarget(force = false) != null
    }

    private fun acquireEnemyTarget(force: Boolean): LivingEntity? {
        val existing = currentEnemyTarget()
        if (existing != null) {
            return existing
        }
        val worldTime = world.time
        if (!force && worldTime < nextEnemyScanTick) {
            return null
        }
        val found = scanForEnemyTarget()
        nextEnemyScanTick = worldTime + ENEMY_RESCAN_INTERVAL_TICKS
        target = found
        return found
    }

    private fun scanForEnemyTarget(): LivingEntity? {
        val range = ENEMY_SEARCH_RANGE
        val rangeSq = MAX_TUNNEL_RANGE_SQ

        val nearestPlayer = world.players
            .asSequence()
            .filter { it.isAlive && !it.isSpectator && !it.isCreative }
            .filter { isValidEnemyTarget(it) }
            .filter { squaredDistanceTo(it) <= rangeSq }
            .minByOrNull { squaredDistanceTo(it) }

        if (nearestPlayer != null) {
            return nearestPlayer
        }

        val box = Box.of(Vec3d.ofCenter(blockPos), range * 2, range * 2, range * 2)
        return world.getEntitiesByClass(LivingEntity::class.java, box) { other ->
            other !== this && isValidEnemyTarget(other) && squaredDistanceTo(other) <= rangeSq
        }.minByOrNull { squaredDistanceTo(it) }
    }

    private fun isValidEnemyTarget(entity: LivingEntity): Boolean {
        if (entity === this) return false
        if (!entity.isAlive) return false
        if (entity is PlayerEntity && (entity.isSpectator || entity.isCreative)) return false
        if (!canTarget(entity)) return false
        return true
    }

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

        private const val ENEMY_SEARCH_RANGE = 32.0
        private const val MAX_TUNNEL_RANGE_SQ = ENEMY_SEARCH_RANGE * ENEMY_SEARCH_RANGE
        private const val ENEMY_RESCAN_INTERVAL_TICKS = 5L
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

    // --- Movement planning & tunneling --------------------------------------

    private data class MovementGoal(
        val targetPos: BlockPos,
        val strategy: MovementStrategy,
        val waypoints: List<BlockPos> = emptyList(),
        val priority: Double = 0.0
    )

    private enum class MovementStrategy {
        DIRECT_HORIZONTAL,
        BUILD_STAIRS_UP,
        DIG_STAIRS_DOWN,
        VERTICAL_THEN_HORIZONTAL,
        SPIRAL_ASCENT,
        BRIDGE_GAP
    }

    private fun isSolid(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return !state.isAir && !state.getCollisionShape(world, pos).isEmpty
    }

    private fun planMovementTo(target: BlockPos): MovementGoal? {
        val myPos = blockPos
        val horizontalDist = sqrt(
            ((target.x - myPos.x) * (target.x - myPos.x) +
                (target.z - myPos.z) * (target.z - myPos.z)).toDouble()
        )
        val verticalDist = target.y - myPos.y

        debug("Planning movement: horizontal=$horizontalDist, vertical=$verticalDist")

        if (horizontalDist <= 3.0 && kotlin.math.abs(verticalDist) <= 2) {
            debug("Already in melee range!")
            return null
        }

        val goal = when {
            kotlin.math.abs(verticalDist) <= 1 ->
                MovementGoal(target, MovementStrategy.DIRECT_HORIZONTAL, planHorizontalPath(myPos, target))

            verticalDist in 2..8 ->
                MovementGoal(target, MovementStrategy.BUILD_STAIRS_UP, planStaircase(myPos, target))

            verticalDist > 8 ->
                MovementGoal(target, MovementStrategy.VERTICAL_THEN_HORIZONTAL, planVerticalThenHorizontal(myPos, target))

            verticalDist < -1 ->
                MovementGoal(target, MovementStrategy.DIG_STAIRS_DOWN, planDownwardPath(myPos, target))

            else -> null
        }

        if (goal != null && !world.isClient) {
            val center = Vec3d.ofCenter(myPos)
            (world as? ServerWorld)?.let { serverWorld ->
                serverWorld.spawnParticles(
                    ParticleTypes.POOF,
                    center.x, center.y + 1.0, center.z,
                    10, 0.5, 0.5, 0.5, 0.1
                )
            }
            debug("New plan: ${goal.strategy} with ${goal.waypoints.size} waypoints")
        }

        return goal
    }

    private fun planHorizontalPath(from: BlockPos, to: BlockPos): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)

        var current = from
        val maxSteps = 32
        var steps = 0

        while (steps < maxSteps && !isCloseEnoughForMelee(current, to)) {
            val remainingX = kotlin.math.abs(to.x - current.x)
            val remainingZ = kotlin.math.abs(to.z - current.z)

            val nextPos = when {
                remainingX > remainingZ && remainingX > 0 -> current.add(dx, 0, 0)
                remainingZ > 0 -> current.add(0, 0, dz)
                remainingX > 0 -> current.add(dx, 0, 0)
                else -> break
            }

            if (needsDigging(nextPos)) path.add(nextPos)
            current = nextPos
            steps++
        }

        debug("Horizontal path planned: ${path.size} blocks to dig")
        return path
    }

    private fun planStaircase(from: BlockPos, to: BlockPos): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)

        var current = from
        val targetHeight = to.y
        val maxSteps = 64
        var steps = 0

        debug("Planning staircase from $from to $to")

        while (steps < maxSteps && current.y < targetHeight && !isCloseEnoughForMelee(current, to)) {
            val horizontalNext = current.add(
                if (kotlin.math.abs(to.x - current.x) > 0) dx else 0,
                0,
                if (kotlin.math.abs(to.z - current.z) > 0) dz else 0
            )

            val stairTop = horizontalNext.up()

            addStairStepBlocks(path, current, horizontalNext, stairTop)

            current = stairTop
            steps++
        }

        if (current.y >= targetHeight - 1) {
            path.addAll(planHorizontalPath(current, to))
        }

        debug("Staircase planned: ${path.size} blocks to dig, final height: ${current.y}")
        return path
    }

    private fun addStairStepBlocks(path: MutableList<BlockPos>, from: BlockPos, horizontalPos: BlockPos, topPos: BlockPos) {
        if (needsDigging(horizontalPos)) path.add(horizontalPos)
        if (needsDigging(horizontalPos.up())) path.add(horizontalPos.up())
        if (needsDigging(topPos)) path.add(topPos)
        if (needsDigging(topPos.up())) path.add(topPos.up())
    }

    private fun planVerticalThenHorizontal(from: BlockPos, to: BlockPos): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        val intermediateHeight = to.y - 2
        val verticalTarget = BlockPos(from.x, intermediateHeight, from.z)

        path.addAll(planStaircase(from, verticalTarget))

        val finalTarget = BlockPos(to.x, intermediateHeight, to.z)
        path.addAll(planHorizontalPath(verticalTarget, finalTarget))

        if (to.y > intermediateHeight) {
            path.addAll(planStaircase(finalTarget, to))
        }

        debug("Vertical-then-horizontal: ${path.size} total blocks")
        return path
    }

    private fun planDownwardPath(from: BlockPos, to: BlockPos): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)

        var current = from
        val maxSteps = 32
        var steps = 0

        while (steps < maxSteps && current.y > to.y && !isCloseEnoughForMelee(current, to)) {
            val nextDown = current.add(dx, -1, dz)

            if (needsDigging(nextDown)) path.add(nextDown)
            if (needsDigging(nextDown.up())) path.add(nextDown.up())

            current = nextDown
            steps++
        }

        if (!isCloseEnoughForMelee(current, to)) {
            path.addAll(planHorizontalPath(current, to))
        }

        return path
    }

    private fun needsDigging(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return !state.isAir && isBreakableForMiner(state, pos)
    }

    private fun isCloseEnoughForMelee(pos: BlockPos, target: BlockPos): Boolean {
        val dx = kotlin.math.abs(target.x - pos.x)
        val dy = kotlin.math.abs(target.y - pos.y)
        val dz = kotlin.math.abs(target.z - pos.z)
        return dx <= 3 && dz <= 3 && dy <= 2
    }

    private var currentMovementGoal: MovementGoal? = null
    private var currentWaypointIndex: Int = 0

    private fun nextDigBlockTowards(from: BlockPos, dest: BlockPos): BlockPos? {
        if (currentMovementGoal?.targetPos != dest) {
            if (currentMovementGoal != null) {
                clearMovementPlan(celebrate = false)
            }
            currentMovementGoal = planMovementTo(dest)
            currentWaypointIndex = 0
        }

        val goal = currentMovementGoal ?: return null
        val waypoints = goal.waypoints
        if (waypoints.isEmpty()) return null

        while (currentWaypointIndex < waypoints.size) {
            val nextBlock = waypoints[currentWaypointIndex]
            if (needsDigging(nextBlock)) {
                debug("Next dig target: $nextBlock (waypoint ${currentWaypointIndex + 1}/${waypoints.size})")
                return nextBlock
            }
            currentWaypointIndex++
        }

        clearMovementPlan()
        return null
    }

    private fun digTunnelAt(pos: BlockPos) {
        when (currentMovementGoal?.strategy) {
            MovementStrategy.BUILD_STAIRS_UP -> digStairStep(pos)
            MovementStrategy.DIG_STAIRS_DOWN -> digDownwardStep(pos)
            else -> digStandardTunnel(pos)
        }
    }

    private fun digStairStep(pos: BlockPos) {
        breakBlockServerSide(pos)
        val up = pos.up()
        if (isSolid(up)) breakBlockServerSide(up)
    }

    private fun digDownwardStep(pos: BlockPos) {
        breakBlockServerSide(pos)
        val up = pos.up()
        if (isSolid(up)) breakBlockServerSide(up)
    }

    private fun digStandardTunnel(pos: BlockPos) {
        breakBlockServerSide(pos)
        val up = pos.up()
        if (isSolid(up)) breakBlockServerSide(up)
    }

    private fun visualizeMovementPlan() {
        if (world.isClient) return
        val serverWorld = world as? ServerWorld ?: return
        val goal = currentMovementGoal ?: return
        val waypoints = goal.waypoints
        if (waypoints.isEmpty()) return

        val strategyParticle = when (goal.strategy) {
            MovementStrategy.DIRECT_HORIZONTAL -> ParticleTypes.SMOKE
            MovementStrategy.BUILD_STAIRS_UP -> ParticleTypes.FLAME
            MovementStrategy.DIG_STAIRS_DOWN -> ParticleTypes.DRIPPING_WATER
            MovementStrategy.VERTICAL_THEN_HORIZONTAL -> ParticleTypes.END_ROD
            MovementStrategy.SPIRAL_ASCENT -> ParticleTypes.PORTAL
            MovementStrategy.BRIDGE_GAP -> ParticleTypes.CLOUD
        }

        val startIndex = currentWaypointIndex
        val endIndex = (startIndex + 8).coerceAtMost(waypoints.size)

        for (i in startIndex until endIndex) {
            val pos = waypoints[i]
            val center = Vec3d.ofCenter(pos)
            when {
                i == currentWaypointIndex -> {
                    serverWorld.spawnParticles(
                        ParticleTypes.FLAME,
                        center.x, center.y + 0.5, center.z,
                        3, 0.2, 0.2, 0.2, 0.02
                    )
                    serverWorld.spawnParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        center.x, center.y + 1.0, center.z,
                        1, 0.0, 0.0, 0.0, 0.0
                    )
                }
                i < currentWaypointIndex + 3 -> {
                    serverWorld.spawnParticles(
                        strategyParticle,
                        center.x, center.y + 0.3, center.z,
                        2, 0.1, 0.1, 0.1, 0.01
                    )
                }
                else -> {
                    serverWorld.spawnParticles(
                        ParticleTypes.SMOKE,
                        center.x, center.y + 0.1, center.z,
                        1, 0.05, 0.05, 0.05, 0.005
                    )
                }
            }
        }

        val target = goal.targetPos
        val targetCenter = Vec3d.ofCenter(target)
        serverWorld.spawnParticles(
            ParticleTypes.HEART,
            targetCenter.x, targetCenter.y + 2.0, targetCenter.z,
            2, 0.3, 0.3, 0.3, 0.0
        )
    }

    private fun debugMovementStrategy() {
        val goal = currentMovementGoal ?: return
        val strategyName = when (goal.strategy) {
            MovementStrategy.DIRECT_HORIZONTAL -> "Direct Tunnel"
            MovementStrategy.BUILD_STAIRS_UP -> "Building Stairs Up"
            MovementStrategy.DIG_STAIRS_DOWN -> "Digging Down"
            MovementStrategy.VERTICAL_THEN_HORIZONTAL -> "Vertical→Horizontal"
            MovementStrategy.SPIRAL_ASCENT -> "Spiral Ascent"
            MovementStrategy.BRIDGE_GAP -> "Bridge Gap"
        }
        debug("$strategyName -> ${goal.targetPos} (${currentWaypointIndex}/${goal.waypoints.size})")
    }

    private fun clearMovementPlan(celebrate: Boolean = true) {
        if (celebrate && !world.isClient && currentMovementGoal != null) {
            val center = Vec3d.ofCenter(blockPos)
            (world as? ServerWorld)?.spawnParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                center.x, center.y + 1.5, center.z,
                8, 0.8, 0.8, 0.8, 0.2
            )
            debug("Movement plan completed/cleared")
        }
        currentMovementGoal = null
        currentWaypointIndex = 0
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
            val target = miner.refreshEnemyTarget() ?: return false
            if (miner.squaredDistanceTo(target) > MAX_TUNNEL_RANGE_SQ) return false

            digPos = miner.nextDigBlockTowards(miner.blockPos, target.blockPos)
            miner.debug("canStart -> dig=$digPos toward=${target.blockPos}")
            return digPos != null
        }

        override fun shouldContinue(): Boolean {
            val target = miner.refreshEnemyTarget() ?: return false
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
            miner.clearMovementPlan(celebrate = false)
        }

        override fun tick() {
            val target = miner.refreshEnemyTarget() ?: return
            if (!target.isAlive) return

            if (miner.world.time % 10L == 0L) {
                miner.visualizeMovementPlan()
            }
            if (miner.world.time % 60L == 0L) {
                miner.debugMovementStrategy()
            }

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

                if (!world.isClient) {
                    (world as? ServerWorld)?.spawnParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        center.x, center.y, center.z,
                        5, 0.5, 0.5, 0.5, 0.1
                    )
                }
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
            if (miner.hasEnemyTargetInRange()) return false

            vandalTarget = findNearestVandalTarget() ?: return false
            digPos = miner.nextDigBlockTowards(miner.blockPos, vandalTarget!!)
            return digPos != null || miner.blockPos.isWithinDistance(
                Vec3d.ofCenter(vandalTarget!!),
                MINING_REACH_BLOCKS + 0.1
            )
        }

        override fun shouldContinue(): Boolean {
            if (miner.hasEnemyTargetInRange()) return false
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
            miner.clearMovementPlan(celebrate = false)
        }

        override fun stop() {
            clearCrackProgress()
            vandalTarget = null
            digPos = null
            miningTicks = 0
            miner.clearMovementPlan(celebrate = false)
        }

        override fun tick() {
            val vt = vandalTarget ?: return

            if (miner.world.time % 10L == 0L) {
                miner.visualizeMovementPlan()
            }
            if (miner.world.time % 60L == 0L) {
                miner.debugMovementStrategy()
            }

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

                if (!world.isClient) {
                    (world as? ServerWorld)?.spawnParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        center.x, center.y, center.z,
                        4, 0.4, 0.4, 0.4, 0.08
                    )
                }
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

                if (!world.isClient) {
                    val center = Vec3d.ofCenter(pos)
                    (world as? ServerWorld)?.spawnParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        center.x, center.y, center.z,
                        6, 0.6, 0.6, 0.6, 0.12
                    )
                }
            }
        }

        private fun findNearestVandalTarget(): BlockPos? {
            val r = ENEMY_SEARCH_RANGE.toInt()
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

            if (b === Blocks.COBBLESTONE || b === Blocks.MOSSY_COBBLESTONE) return true

            // Doors / Fences / Gates via tags
            if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.FENCES) ||
                state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.WALLS)
            ) return true

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
