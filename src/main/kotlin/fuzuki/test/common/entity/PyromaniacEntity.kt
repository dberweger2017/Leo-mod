package fuzuki.test.common.entity

import net.minecraft.block.AbstractFireBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.EntityData
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.goal.LookAroundGoal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.ai.goal.WanderAroundFarGoal
import net.minecraft.entity.ai.goal.WanderAroundGoal
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.RevengeGoal
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.TurtleEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import net.minecraft.util.math.random.Random
import net.minecraft.world.GameRules
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import net.minecraft.world.event.GameEvent
import java.util.EnumSet
import java.util.ArrayDeque

class PyromaniacEntity(entityType: EntityType<out PyromaniacEntity>, world: World) :
    ZombieEntity(entityType, world) {

    private var igniteCooldown = 0
    private var nextRegenCheckTick = 0L
    private val recentFirePositions: ArrayDeque<BlockPos> = ArrayDeque()
    private val recentFireTimes: ArrayDeque<Long> = ArrayDeque()
    private var fireRestUntilTick: Long = 0L

    init {
        setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0f)
        setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0f)
    }

    override fun tick() {
        super.tick()
        if (igniteCooldown > 0) {
            igniteCooldown--
        }
        ensureFlintAndSteel()
        if (isOnFire) extinguish()
        handleSelfRegen()
    }

    override fun isFireImmune(): Boolean = true

    private fun handleSelfRegen() {
        if (world.isClient) return
        val currentTime = world.time
        if (currentTime < nextRegenCheckTick) return
        nextRegenCheckTick = currentTime + REGEN_CHECK_INTERVAL_TICKS
        if (health <= REGEN_TRIGGER_HEALTH && !hasStatusEffect(StatusEffects.REGENERATION)) {
            addStatusEffect(
                StatusEffectInstance(
                    StatusEffects.REGENERATION,
                    REGEN_DURATION_TICKS,
                    0,
                    false,
                    true,
                    true
                )
            )
        }
    }

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(1, BurnTargetGoal(this))
        goalSelector.add(2, BurnPlanksGoal(this))
        goalSelector.add(3, WanderAroundFarGoal(this, 1.0))
        goalSelector.add(4, WanderAroundGoal(this, 0.8))
        goalSelector.add(5, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))
        goalSelector.add(6, LookAroundGoal(this))

        targetSelector.add(1, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(2, ActiveTargetGoal(this, VillagerEntity::class.java, false))
        targetSelector.add(3, ActiveTargetGoal<IronGolemEntity>(this, IronGolemEntity::class.java, true))
        targetSelector.add(
            4,
            ActiveTargetGoal<TurtleEntity>(this, TurtleEntity::class.java, 10, true, false, TurtleEntity.BABY_TURTLE_ON_LAND_FILTER)
        )
        targetSelector.add(5, RevengeGoal(this))
    }

    override fun initEquipment(random: Random, difficulty: LocalDifficulty) {
        super.initEquipment(random, difficulty)
        equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.FLINT_AND_STEEL))
        setCanPickUpLoot(false)
    }

    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?
    ): EntityData? {
        val data = super.initialize(world, difficulty, spawnReason, entityData)
        health = maxHealth
        return data
    }

    override fun tryAttack(target: net.minecraft.entity.Entity?): Boolean {
        // Prevent dealing melee damage; focus on ignition instead
        return false
    }

    fun canIgnite(): Boolean {
        return igniteCooldown <= 0 && !mainHandStack.isEmpty && mainHandStack.isOf(Items.FLINT_AND_STEEL) && canAttemptFire()
    }

    fun onIgniteSuccess() {
        val stack = mainHandStack
        if (!stack.isEmpty && stack.isOf(Items.FLINT_AND_STEEL)) {
            stack.damage(1, this, EquipmentSlot.MAINHAND)
            if (stack.isEmpty) {
                ensureFlintAndSteel()
            }
        }
        igniteCooldown = IGNITE_COOLDOWN_TICKS
    }

    private fun ensureFlintAndSteel() {
        if (world.isClient) return
        val stack = mainHandStack
        if (stack.isEmpty || !stack.isOf(Items.FLINT_AND_STEEL)) {
            equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.FLINT_AND_STEEL))
        }
    }

    internal fun igniteAt(pos: BlockPos): Boolean {
        if (world.isClient) return false
        if (!canAttemptFire()) return false
        val serverWorld = world as? ServerWorld ?: return false
        if (!serverWorld.gameRules.getBoolean(GameRules.DO_MOB_GRIEFING)) return false
        if (!serverWorld.gameRules.getBoolean(GameRules.DO_FIRE_TICK)) return false

        var targetPos = pos
        if (!serverWorld.getBlockState(targetPos).isAir) {
            targetPos = targetPos.up()
        }
        if (!serverWorld.getBlockState(targetPos).isAir) return false
        if (recentFirePositions.contains(targetPos)) return false

        val fireState = AbstractFireBlock.getState(serverWorld, targetPos) ?: return false
        if (!fireState.canPlaceAt(serverWorld, targetPos)) return false
        if (!serverWorld.setBlockState(targetPos, fireState, Block.NOTIFY_ALL)) return false
        recordFire(targetPos)
        serverWorld.playSound(
            null,
            targetPos,
            SoundEvents.ITEM_FLINTANDSTEEL_USE,
            SoundCategory.HOSTILE,
            0.8f,
            1.0f + serverWorld.random.nextFloat() * 0.2f
        )
        serverWorld.emitGameEvent(
            GameEvent.BLOCK_PLACE,
            targetPos,
            GameEvent.Emitter.of(this, Blocks.FIRE.defaultState)
        )
        swingHand(Hand.MAIN_HAND)
        onIgniteSuccess()
        return true
    }

    internal fun igniteAdjacentTo(pos: BlockPos): Boolean {
        return igniteAt(pos) || igniteAt(pos.up()) || igniteAt(pos.down())
    }

    fun isCoolingDown(): Boolean = igniteCooldown > 0 || world.time < fireRestUntilTick

    private fun canAttemptFire(): Boolean {
        if (world.isClient) return false
        val currentTime = world.time
        if (currentTime < fireRestUntilTick) return false
        trimOldFireTimes(currentTime)
        return recentFireTimes.size < FIRE_BUDGET_MAX
    }

    private fun recordFire(pos: BlockPos) {
        val currentTime = world.time
        trimOldFireTimes(currentTime)

        val immutable = pos.toImmutable()
        recentFirePositions.remove(immutable)
        recentFirePositions.addLast(immutable)
        while (recentFirePositions.size > MAX_RECENT_FIRE_POSITIONS) {
            recentFirePositions.pollFirst()
        }

        recentFireTimes.addLast(currentTime)
        if (recentFireTimes.size >= FIRE_BUDGET_MAX) {
            fireRestUntilTick = currentTime + FIRE_BUDGET_REST_TICKS
        }
    }

    private fun trimOldFireTimes(currentTime: Long) {
        while (true) {
            val oldest = recentFireTimes.peekFirst() ?: break
            if (currentTime - oldest <= FIRE_BUDGET_WINDOW_TICKS) break
            recentFireTimes.pollFirst()
        }
    }

    override fun damage(damageSource: net.minecraft.entity.damage.DamageSource, amount: Float): Boolean {
        val damaged = super.damage(damageSource, amount)
        if (damaged) {
            ensureFlintAndSteel()
        }
        return damaged
    }

    companion object {
        private const val IGNITE_COOLDOWN_TICKS = 20
        private const val TARGET_BURN_DISTANCE_SQ = 6.25
        private const val PLANK_SEARCH_RADIUS = 12
        private const val SURROUND_IGNITE_INTERVAL_TICKS = 10L
        private const val REGEN_CHECK_INTERVAL_TICKS = 20L * 20
        private const val REGEN_DURATION_TICKS = 10 * 20
        private const val REGEN_TRIGGER_HEALTH = 12f
        private const val PLANK_COOLDOWN_BASE_TICKS = 80
        private const val PLANK_COOLDOWN_JITTER_TICKS = 20
        private const val FIRE_BUDGET_WINDOW_TICKS = 200L
        private const val FIRE_BUDGET_MAX = 8
        private const val FIRE_BUDGET_REST_TICKS = 100L
        private const val MAX_RECENT_FIRE_POSITIONS = 24
        private val SURROUND_OFFSETS: Array<Vec3i> = arrayOf(
            Vec3i(1, 0, 0),
            Vec3i(-1, 0, 0),
            Vec3i(0, 0, 1),
            Vec3i(0, 0, -1),
            Vec3i(1, 0, 1),
            Vec3i(1, 0, -1),
            Vec3i(-1, 0, 1),
            Vec3i(-1, 0, -1),
            Vec3i(2, 0, 0),
            Vec3i(-2, 0, 0),
            Vec3i(0, 0, 2),
            Vec3i(0, 0, -2),
            Vec3i(0, -1, 1),
            Vec3i(0, -1, -1),
            Vec3i(1, -1, 0),
            Vec3i(-1, -1, 0),
            Vec3i(0, 1, 1),
            Vec3i(0, 1, -1),
            Vec3i(1, 1, 0),
            Vec3i(-1, 1, 0)
        )

        fun createAttributes(): DefaultAttributeContainer.Builder =
            createZombieAttributes()
                .add(EntityAttributes.ZOMBIE_SPAWN_REINFORCEMENTS, 0.0)
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0)

        fun canSpawn(
            type: EntityType<out PyromaniacEntity>,
            world: ServerWorldAccess,
            spawnReason: SpawnReason,
            pos: BlockPos,
            random: Random
        ): Boolean {
            if (!MobEntity.canMobSpawn(type, world, spawnReason, pos, random)) return false
            return pos.y > world.bottomY + 5 && world.isSkyVisible(pos).not()
        }
    }

    private class BurnTargetGoal(private val pyromaniac: PyromaniacEntity) : Goal() {
        init {
            controls = EnumSet.of(Control.MOVE, Control.LOOK)
        }

        override fun canStart(): Boolean {
            val target = pyromaniac.target as? LivingEntity ?: return false
            return target.isAlive && pyromaniac.canTarget(target) && pyromaniac.canIgnite()
        }

        override fun shouldContinue(): Boolean {
            val target = pyromaniac.target as? LivingEntity ?: return false
            if (!target.isAlive) return false
            if (!pyromaniac.canTarget(target)) return false
            if (!pyromaniac.canIgnite() && pyromaniac.isCoolingDown()) return false
            return true
        }

        override fun start() {
            pyromaniac.navigation.startMovingTo(pyromaniac.target, 1.1)
        }

        override fun stop() {
            pyromaniac.navigation.stop()
        }

        override fun tick() {
            val target = pyromaniac.target as? LivingEntity ?: return

            pyromaniac.lookControl.lookAt(target, 30.0f, 30.0f)
            val distanceSq = pyromaniac.squaredDistanceTo(target)
            if (distanceSq > TARGET_BURN_DISTANCE_SQ) {
                pyromaniac.navigation.startMovingTo(target, 1.2)
                return
            }

            pyromaniac.navigation.stop()
            if (!pyromaniac.canIgnite()) return

            val targetPos = BlockPos.ofFloored(target.x, target.boundingBox.minY, target.z)
            val centerVec = Vec3d.ofCenter(targetPos)
            if (pyromaniac.squaredDistanceTo(centerVec) > TARGET_BURN_DISTANCE_SQ) {
                pyromaniac.navigation.startMovingTo(centerVec.x, centerVec.y, centerVec.z, 1.1)
                return
            }
            igniteAroundTarget(targetPos)
        }

        private fun igniteAroundTarget(center: BlockPos) {
            if (!pyromaniac.canIgnite()) return
            if (pyromaniac.world.time % SURROUND_IGNITE_INTERVAL_TICKS != 0L) return

            if (pyromaniac.igniteAt(center)) return
            if (pyromaniac.igniteAt(center.down())) return
            if (pyromaniac.igniteAt(center.up())) return

            for (offset in SURROUND_OFFSETS) {
                val candidate = center.add(offset.x, offset.y, offset.z)
                if (pyromaniac.world.getBlockState(candidate).isOf(Blocks.FIRE)) continue
                if (pyromaniac.igniteAt(candidate)) return
            }
        }
    }

    private class BurnPlanksGoal(private val pyromaniac: PyromaniacEntity) : Goal() {
        private var plankPos: BlockPos? = null
        private var cooldownTicks = 0

        init {
            controls = EnumSet.of(Control.MOVE, Control.LOOK)
        }

        override fun canStart(): Boolean {
            if (pyromaniac.target != null) return false
            if (pyromaniac.isCoolingDown()) return false
            if (cooldownTicks > 0) {
                cooldownTicks--
                return false
            }

            plankPos = findNearestPlank()
            return plankPos != null
        }

        override fun shouldContinue(): Boolean {
            if (pyromaniac.target != null) return false
            val pos = plankPos ?: return false
            return pyromaniac.world.getBlockState(pos).isIn(BlockTags.PLANKS)
        }

        override fun start() {
            plankPos?.let {
                pyromaniac.navigation.startMovingTo(
                    it.x + 0.5,
                    it.y + 0.5,
                    it.z + 0.5,
                    1.0
                )
            }
        }

        override fun stop() {
            plankPos = null
            pyromaniac.navigation.stop()
            cooldownTicks = rollCooldown()
        }

        override fun tick() {
            val pos = plankPos ?: return
            val center = Vec3d.ofCenter(pos)
            pyromaniac.lookControl.lookAt(center.x, center.y, center.z)

            if (pyromaniac.squaredDistanceTo(center) > 2.5) {
                pyromaniac.navigation.startMovingTo(center.x, center.y, center.z, 1.0)
                return
            }

            pyromaniac.navigation.stop()
            if (!pyromaniac.canIgnite()) return

            val success = pyromaniac.igniteAdjacentTo(pos)
            if (success) {
                plankPos = null
            } else {
                cooldownTicks = rollCooldown()
                plankPos = null
            }
            if (success) {
                cooldownTicks = rollCooldown()
            }
        }

        private fun findNearestPlank(): BlockPos? {
            val origin = pyromaniac.blockPos
            if (isValidPlank(origin)) return origin.toImmutable()
            for (radius in 1..PLANK_SEARCH_RADIUS) {
                // Top and bottom edges of square ring
                for (dx in -radius..radius) {
                    val north = origin.add(dx, 0, radius)
                    if (isValidPlank(north)) return north.toImmutable()
                    val south = origin.add(dx, 0, -radius)
                    if (isValidPlank(south)) return south.toImmutable()
                }
                // Left and right edges (excluding corners already checked)
                for (dz in (-radius + 1) until radius) {
                    val east = origin.add(radius, 0, dz)
                    if (isValidPlank(east)) return east.toImmutable()
                    val west = origin.add(-radius, 0, dz)
                    if (isValidPlank(west)) return west.toImmutable()
                }
            }
            return null
        }

        private fun isValidPlank(pos: BlockPos): Boolean {
            val state = pyromaniac.world.getBlockState(pos)
            if (!state.isIn(BlockTags.PLANKS)) return false
            if (!pyromaniac.world.getBlockState(pos.up()).isAir) return false
            return pyromaniac.world.getBlockState(pos.up(2)).isAir
        }

        private fun rollCooldown(): Int {
            return PLANK_COOLDOWN_BASE_TICKS + pyromaniac.random.nextInt(PLANK_COOLDOWN_JITTER_TICKS + 1)
        }
    }
}
