package fuzuki.test.common.entity

import net.minecraft.block.Blocks
import net.minecraft.entity.EntityData
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.TntEntity
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.AbstractSkeletonEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.passive.TurtleEntity
import net.minecraft.entity.passive.WolfEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.Difficulty
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import net.minecraft.world.RaycastContext
import java.util.EnumSet
import kotlin.math.hypot
import kotlin.math.sqrt

class TntSkeletonEntity(entityType: EntityType<out TntSkeletonEntity>, world: World) :
    SkeletonEntity(entityType, world) {

    private lateinit var tntGoal: TntRangedAttackGoal

    override fun initGoals() {
        goalSelector.add(1, FleeEntityGoal(this, PlayerEntity::class.java, CLOSE_RANGE_FLEE_DISTANCE, 1.3, 1.6))
        goalSelector.add(2, AvoidSunlightGoal(this))
        goalSelector.add(3, EscapeSunlightGoal(this, 1.0))
        goalSelector.add(3, FleeEntityGoal(this, WolfEntity::class.java, 6.0f, 1.0, 1.2))
        tntGoal = TntRangedAttackGoal(this, 1.0, HARD_ATTACK_INTERVAL, EXTENDED_RANGE).also {
            it.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK))
        }
        goalSelector.add(4, tntGoal)
        goalSelector.add(5, WanderAroundFarGoal(this, 1.0))
        goalSelector.add(6, LookAtEntityGoal(this, PlayerEntity::class.java, EXTENDED_LOOK_RANGE))
        goalSelector.add(6, LookAroundGoal(this))

        targetSelector.add(1, RevengeGoal(this))
        targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(3, ActiveTargetGoal(this, IronGolemEntity::class.java, true))
        targetSelector.add(4, ActiveTargetGoal(this, TurtleEntity::class.java, 10, true, false, TurtleEntity.BABY_TURTLE_ON_LAND_FILTER))
    }

    override fun updateAttackType() {
        if (!::tntGoal.isInitialized || world.isClient) {
            return
        }
        tntGoal.resetAttackInterval(world.difficulty)
    }

    override fun initEquipment(random: Random, localDifficulty: LocalDifficulty) {
        super.initEquipment(random, localDifficulty)
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
        setCanPickUpLoot(false)
    }

    override fun initialize(world: ServerWorldAccess, difficulty: LocalDifficulty, spawnReason: SpawnReason, entityData: EntityData?): EntityData? {
        val data = super.initialize(world, difficulty, spawnReason, entityData)
        if (::tntGoal.isInitialized) {
            tntGoal.resetAttackInterval(difficulty.globalDifficulty)
        }
        return data
    }

    @Suppress("UNUSED_PARAMETER")
    fun shootTnt(target: LivingEntity, pullProgress: Float) {
        if (world.isClient) return
        val aimPos = target.pos.add(0.0, target.height * 0.5, 0.0)
        shootTntAt(aimPos)
    }

    fun shootTntAt(targetPos: Vec3d, forceMortar: Boolean = false) {
        if (world.isClient) return
        val serverWorld = world as ServerWorld
        val start = Vec3d(x, eyeY - 0.2, z)
        val delta = targetPos.subtract(start)
        val horizontalDistance = hypot(delta.x, delta.z)
        val closeRange = !forceMortar && horizontalDistance < CLOSE_RANGE_THRESHOLD

        val params = if (closeRange) {
            MortarParams(
                minTicks = 12,
                maxTicksCap = 48,
                baseMaxSpeed = 1.2,
                maxSpeedCap = 1.6,
                toleranceSq = 1.0,
                desiredApex = 3.0
            )
        } else {
            MortarParams(
                minTicks = 55,
                maxTicksCap = 220,
                baseMaxSpeed = 1.6,
                maxSpeedCap = 2.8,
                toleranceSq = 2.5,
                desiredApex = 8.0
            )
        }

        val solution = solveMortarAdaptive(
            start = start,
            target = targetPos,
            minTicks = params.minTicks,
            maxTicksCap = params.maxTicksCap,
            baseMaxSpeed = params.baseMaxSpeed,
            maxSpeedCap = params.maxSpeedCap,
            toleranceSq = params.toleranceSq
        ) ?: return

        val gravity = 0.04
        var velocity = solution.velocity
        if (closeRange) {
            val maxVy = sqrt(2.0 * gravity * params.desiredApex)
            if (velocity.y > maxVy) {
                velocity = Vec3d(velocity.x, maxVy, velocity.z)
            }
        }

        val tnt = TntEntity(serverWorld, start.x, start.y, start.z, this)
        tnt.velocity = velocity
        val fuseOffset = if (closeRange) 6 else 10
        tnt.fuse = (solution.flightTicks + fuseOffset).coerceAtMost(Short.MAX_VALUE.toInt())
        serverWorld.spawnEntity(tnt)
        world.playSound(null, blockPos, SoundEvents.ENTITY_TNT_PRIMED, soundCategory, 1.0f, 1.0f)
    }

    private data class MortarSolution(val velocity: Vec3d, val flightTicks: Int)
    private data class MortarParams(
        val minTicks: Int,
        val maxTicksCap: Int,
        val baseMaxSpeed: Double,
        val maxSpeedCap: Double,
        val toleranceSq: Double,
        val desiredApex: Double
    )

    private fun solveMortarAdaptive(
        start: Vec3d,
        target: Vec3d,
        minTicks: Int,
        maxTicksCap: Int,
        baseMaxSpeed: Double,
        maxSpeedCap: Double,
        toleranceSq: Double
    ): MortarSolution? {
        val gravity = 0.04
        val drag = 0.98

        fun simulate(initialVelocity: Vec3d, ticks: Int): Vec3d {
            var position = start
            var velocity = initialVelocity
            repeat(ticks) {
                velocity = Vec3d(velocity.x * drag, velocity.y * drag, velocity.z * drag).add(0.0, -gravity, 0.0)
                position = position.add(velocity)
            }
            return position
        }

        val delta = target.subtract(start)
        val horizontalDistance = hypot(delta.x, delta.z)
        var ticks = horizontalDistance.times(1.8).coerceAtLeast(minTicks.toDouble()).toInt()
        var maxSpeed = baseMaxSpeed

        repeat(6) {
            var velocity = Vec3d(
                delta.x / ticks,
                (delta.y + 0.5 * gravity * ticks * ticks) / ticks,
                delta.z / ticks
            )

            repeat(14) {
                val landedAt = simulate(velocity, ticks)
                val missVector = target.subtract(landedAt)
                if (missVector.lengthSquared() <= toleranceSq) {
                    return MortarSolution(velocity, ticks)
                }

                val correction = missVector.multiply(1.0 / ticks)
                velocity = Vec3d(
                    velocity.x + correction.x * 0.9,
                    velocity.y + correction.y * 1.2,
                    velocity.z + correction.z * 0.9
                )

                val speed = velocity.length()
                if (speed > maxSpeed) {
                    velocity = velocity.normalize().multiply(maxSpeed)
                }
            }

            val landedAt = simulate(velocity, ticks)
            val missSquared = landedAt.squaredDistanceTo(target)
            val isShort = missSquared > toleranceSq
            val hitSpeed = velocity.length()
            val speedCapped = hitSpeed >= maxSpeed * 0.999

            if (isShort && (speedCapped || horizontalDistance > 18.0) && ticks < maxTicksCap) {
                ticks = (ticks + 12).coerceAtMost(maxTicksCap)
                return@repeat
            }

            if (isShort && maxSpeed < maxSpeedCap) {
                maxSpeed = (maxSpeed + 0.3).coerceAtMost(maxSpeedCap)
                return@repeat
            }

            if (!isShort) {
                return MortarSolution(velocity, ticks)
            }
        }

        var ticksFinal = maxTicksCap
        var velocityFinal = Vec3d(
            delta.x / ticksFinal,
            (delta.y + 0.5 * gravity * ticksFinal * ticksFinal) / ticksFinal,
            delta.z / ticksFinal
        )
        val finalSpeed = velocityFinal.length()
        if (finalSpeed > maxSpeedCap) {
            velocityFinal = velocityFinal.normalize().multiply(maxSpeedCap)
        }
        val finalLanding = simulate(velocityFinal, ticksFinal)
        return if (finalLanding.squaredDistanceTo(target) <= toleranceSq * 4) {
            MortarSolution(velocityFinal, ticksFinal)
        } else {
            null
        }
    }

    override fun isInvulnerableTo(source: DamageSource): Boolean {
        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            return true
        }
        return super.isInvulnerableTo(source)
    }

    override fun onDeath(source: DamageSource) {
        super.onDeath(source)
        if (!world.isClient) {
            val amount = random.nextBetween(1, 13)
            dropStack(ItemStack(Items.TNT, amount))
        }
    }

    companion object {
        const val EXTENDED_RANGE = 22.5f
        const val CLOSE_RANGE_FLEE_DISTANCE = 10f
        const val EXTENDED_LOOK_RANGE = 20f
        const val HARD_ATTACK_INTERVAL = 40
        const val REGULAR_ATTACK_INTERVAL = 40
        const val MORTAR_ATTACK_INTERVAL = 120
        const val CLOSE_ATTACK_INTERVAL = 80
        const val CLOSE_RANGE_THRESHOLD = 12.0
        val BLOCK_TARGETS = setOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE)
        const val BLOCK_SEARCH_RADIUS = 12

        fun createAttributes() = AbstractSkeletonEntity.createAbstractSkeletonAttributes()
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
    }
}

private class TntRangedAttackGoal(
    private val skeleton: TntSkeletonEntity,
    private val speed: Double,
    private var attackInterval: Int,
    range: Float
) : Goal() {
    private val squaredRange = range * range
    private var cooldown = -1
    private var targetSeeingTicker = 0
    private var movingToLeft = false
    private var movingBackward = false
    private var combatTicks = -1

    init {
        setControls(EnumSet.of(Control.MOVE, Control.LOOK))
    }

    private var blockTarget: BlockPos? = null

    override fun canStart(): Boolean {
        if (skeleton.target != null) {
            return true
        }
        blockTarget = findBlockTarget()
        return blockTarget != null
    }

    override fun shouldContinue(): Boolean {
        val hasEntityTarget = skeleton.target != null
        val hasBlockTarget = blockTarget?.let { isBlockTargetValid(it) } == true
        if (!hasBlockTarget) {
            blockTarget = null
        }
        return hasEntityTarget || hasBlockTarget
    }

    override fun shouldRunEveryTick(): Boolean = true

    override fun start() {
        skeleton.setAttacking(true)
        cooldown = 0
    }

    override fun stop() {
        skeleton.setAttacking(false)
        targetSeeingTicker = 0
        combatTicks = -1
        cooldown = -1
        blockTarget = null
        skeleton.clearActiveItem()
    }

    override fun tick() {
        val entityTarget = skeleton.target
        if (entityTarget != null && entityTarget.isAlive) {
            blockTarget = null
            handleEntityTarget(entityTarget)
        } else {
            handleBlockTarget()
        }
    }

    private fun handleEntityTarget(target: LivingEntity) {
        val distanceSq = skeleton.squaredDistanceTo(target.x, target.y, target.z)
        val canSee = skeleton.visibilityCache.canSee(target)

        targetSeeingTicker = if (canSee) targetSeeingTicker + 1 else targetSeeingTicker - 1

        if (canSee) {
            skeleton.navigation.stop()
            combatTicks++
        } else {
            skeleton.navigation.startMovingTo(target, speed)
            combatTicks = -1
        }

        if (combatTicks >= 20) {
            if (skeleton.random.nextFloat() < 0.3f) movingToLeft = !movingToLeft
            if (skeleton.random.nextFloat() < 0.3f) movingBackward = !movingBackward
            combatTicks = 0
        }

        if (combatTicks > -1) {
            when {
                distanceSq > squaredRange * 0.75f -> movingBackward = false
                distanceSq < squaredRange * 0.25f -> movingBackward = true
            }
            skeleton.moveControl.strafeTo(if (movingBackward) -0.5f else 0.5f, if (movingToLeft) 0.5f else -0.5f)
            skeleton.lookAtEntity(target, 30f, 30f)
        } else {
            skeleton.lookControl.lookAt(target, 30f, 30f)
        }

        if (cooldown > 0) {
            cooldown--
        }

        if (cooldown <= 0 && canSee && targetSeeingTicker >= 20) {
            val horizontalDistance = hypot(target.x - skeleton.x, target.z - skeleton.z)
            attackInterval = if (horizontalDistance < TntSkeletonEntity.CLOSE_RANGE_THRESHOLD) {
                TntSkeletonEntity.CLOSE_ATTACK_INTERVAL
            } else {
                TntSkeletonEntity.MORTAR_ATTACK_INTERVAL
            }
            skeleton.shootTnt(target, 1.0f)
            cooldown = attackInterval
        }
    }

    private fun handleBlockTarget() {
        val current = blockTarget?.takeIf { isBlockTargetValid(it) } ?: findBlockTarget()
        if (current == null) {
            skeleton.navigation.stop()
            targetSeeingTicker = 0
            combatTicks = -1
            return
        }

        blockTarget = current.toImmutable()
        val targetVec = Vec3d.ofCenter(current)
        val canSee = hasLineOfSight(current)

        if (canSee) {
            skeleton.navigation.stop()
            skeleton.lookControl.lookAt(targetVec.x, targetVec.y, targetVec.z)
            targetSeeingTicker = (targetSeeingTicker + 1).coerceAtMost(40)
        } else {
            skeleton.navigation.startMovingTo(targetVec.x, targetVec.y, targetVec.z, speed)
            targetSeeingTicker = 0
        }

        if (cooldown > 0) {
            cooldown--
        }

        if (cooldown <= 0 && canSee) {
            attackInterval = TntSkeletonEntity.MORTAR_ATTACK_INTERVAL
            skeleton.shootTntAt(targetVec, forceMortar = true)
            cooldown = attackInterval
        }
    }

    private fun findBlockTarget(): BlockPos? {
        val world = skeleton.world
        val origin = skeleton.blockPos
        val radius = TntSkeletonEntity.BLOCK_SEARCH_RADIUS
        var closestPos: BlockPos? = null
        var closestDistance = Double.MAX_VALUE
        for (pos in BlockPos.iterateOutwards(origin, radius, radius, radius)) {
            if (!isBlockTargetValid(pos)) continue
            val center = Vec3d.ofCenter(pos)
            val distance = skeleton.squaredDistanceTo(center.x, center.y, center.z)
            if (distance < closestDistance) {
                closestDistance = distance
                closestPos = pos.toImmutable()
            }
        }
        return closestPos
    }

    private fun isBlockTargetValid(pos: BlockPos): Boolean {
        val state = skeleton.world.getBlockState(pos)
        return TntSkeletonEntity.BLOCK_TARGETS.contains(state.block)
    }

    private fun hasLineOfSight(pos: BlockPos): Boolean {
        val start = Vec3d(skeleton.x, skeleton.eyeY, skeleton.z)
        val end = Vec3d.ofCenter(pos)
        val result = skeleton.world.raycast(
            RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, skeleton)
        )
        return result.type == HitResult.Type.MISS || (result is BlockHitResult && result.blockPos == pos)
    }

    fun resetAttackInterval(difficulty: Difficulty) {
        attackInterval = if (difficulty == Difficulty.HARD) TntSkeletonEntity.HARD_ATTACK_INTERVAL else TntSkeletonEntity.REGULAR_ATTACK_INTERVAL
    }
}
