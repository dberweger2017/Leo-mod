package fuzuki.test.common.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.AvoidSunlightGoal
import net.minecraft.entity.ai.goal.EscapeSunlightGoal
import net.minecraft.entity.ai.goal.FleeEntityGoal
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.ai.goal.LookAroundGoal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.RevengeGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.AbstractSkeletonEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import java.util.EnumSet

class SniperSkeletonEntity(entityType: EntityType<out SniperSkeletonEntity>, world: World) :
    SkeletonEntity(entityType, world) {

    private var windUpTicks = 0

    override fun initGoals() {
        goalSelector.add(0, FleeEntityGoal(this, PlayerEntity::class.java, MIN_COMFORT_DIST, 1.0, FLEE_SPEED_MULT))
        goalSelector.add(1, AvoidSunlightGoal(this))
        goalSelector.add(2, EscapeSunlightGoal(this, 1.0))

        goalSelector.add(3, SniperBowAttackGoal(this, 1.0, ATTACK_INTERVAL_TICKS, FOLLOW_RANGE.toFloat()))
        goalSelector.add(4, WanderToHighGroundGoal(this, 1.0))
        goalSelector.add(5, LookAtEntityGoal(this, PlayerEntity::class.java, 30f))
        goalSelector.add(6, LookAroundGoal(this))

        targetSelector.add(1, RevengeGoal(this))
        targetSelector.add(2, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(3, ActiveTargetGoal(this, IronGolemEntity::class.java, true))
    }

    override fun initEquipment(random: net.minecraft.util.math.random.Random, difficulty: net.minecraft.world.LocalDifficulty) {
        super.initEquipment(random, difficulty)
        equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.BOW))
        setCanPickUpLoot(false)
    }

    override fun updateAttackType() {
        // custom bow handling via SniperBowAttackGoal
    }

    override fun getStepHeight(): Float = 1.1f

    override fun tick() {
        super.tick()
        if (windUpTicks > 0) {
            windUpTicks--
        }
    }

    fun beginWindUp() {
        if (windUpTicks <= 0) {
            windUpTicks = WIND_UP_TICKS
            val hand = ProjectileUtil.getHandPossiblyHolding(this, Items.BOW)
            if (hand != null) {
                setCurrentHand(hand)
            }
            world.playSound(null, blockPos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), soundCategory, 0.6f, 1.2f)
        }
    }

    fun isWindingUp(): Boolean = windUpTicks > 0

    fun consumeWindUpTick(): Boolean {
        if (windUpTicks > 0) {
            windUpTicks--
        }
        return windUpTicks <= 0
    }

    fun resetWindUp() {
        if (windUpTicks > 0) {
            windUpTicks = 0
            clearActiveItem()
        }
    }

    override fun shootAt(target: LivingEntity, pullProgress: Float) {
        if (world.isClient) return
        val serverWorld = world as ServerWorld

        val bowStack = getStackInHand(ProjectileUtil.getHandPossiblyHolding(this, Items.BOW))
        val projectileStack = getProjectileType(bowStack)
        val projectile = createArrowProjectile(projectileStack, pullProgress, bowStack)

        if (projectile is ArrowEntity) {
            projectile.damage = 7.0
            projectile.addEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0))
            projectile.addEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0))
        }

        val start = Vec3d(x, eyeY - 0.1, z)
        projectile.setPosition(start.x, start.y, start.z)

        val aimBase = Vec3d(target.x, target.eyeY, target.z)
        val speed = BASE_ARROW_SPEED
        val toTarget = aimBase.subtract(start)
        val distance = toTarget.length().coerceAtLeast(0.001)
        val time = distance / speed.toDouble()
        val gravity = 0.05
        val lead = target.velocity.multiply(time)
        val aim = aimBase.add(lead).add(0.0, 0.5 * gravity * time * time, 0.0)

        val direction = aim.subtract(start)
        projectile.setVelocity(direction.x, direction.y, direction.z, speed, 0.0f)
        projectile.isCritical = pullProgress >= 1.0f

        serverWorld.spawnEntity(projectile)
        serverWorld.playSound(null, blockPos, SoundEvents.ENTITY_ARROW_SHOOT, soundCategory, 1f, 0.8f)

        val rayEnd = raycastForTracer(serverWorld, start, aim, this)
        spawnTracer(serverWorld, start, rayEnd, 0.75)
    }

    companion object {
        const val MIN_COMFORT_DIST = 16.0f
        private const val FLEE_SPEED_MULT = 2.0
        private const val BASE_ARROW_SPEED = 6.5f
        private const val FOLLOW_RANGE = 32.0
        private const val ATTACK_INTERVAL_TICKS = 70
        private const val WIND_UP_TICKS = 8

        fun createAttributes(): DefaultAttributeContainer.Builder =
            AbstractSkeletonEntity.createAbstractSkeletonAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.15)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
    }
}

private class SniperBowAttackGoal(
    private val skeleton: SniperSkeletonEntity,
    private val speed: Double,
    private val attackInterval: Int,
    range: Float
) : Goal() {
    private val squaredRange = range * range
    private var cooldown = attackInterval
    private var targetSeeingTicker = 0
    private var strafingClockwise = false
    private var strafingBackwards = false
    private var strafingTime = -1

    init {
        setControls(EnumSet.of(Control.MOVE, Control.LOOK))
    }

    override fun canStart(): Boolean {
        val target = skeleton.target
        return target != null && target.isAlive && skeleton.isHolding(Items.BOW)
    }

    override fun shouldContinue(): Boolean = canStart()

    override fun shouldRunEveryTick(): Boolean = true

    override fun start() {
        skeleton.setAttacking(true)
        skeleton.resetWindUp()
    }

    override fun stop() {
        skeleton.setAttacking(false)
        skeleton.resetWindUp()
        targetSeeingTicker = 0
        strafingTime = -1
        cooldown = attackInterval
        skeleton.clearActiveItem()
    }

    override fun tick() {
        val target = skeleton.target ?: return
        val distanceSq = skeleton.squaredDistanceTo(target.x, target.y, target.z)
        val canSee = skeleton.visibilityCache.canSee(target)

        if (canSee) targetSeeingTicker++ else targetSeeingTicker--

        val withinRange = distanceSq <= squaredRange
        if (withinRange && canSee) {
            skeleton.navigation.stop()
            strafingTime++
        } else {
            skeleton.navigation.startMovingTo(target, speed)
            strafingTime = -1
        }

        if (strafingTime >= 20) {
            if (skeleton.random.nextFloat() < 0.3f) {
                strafingClockwise = !strafingClockwise
            }
            if (skeleton.random.nextFloat() < 0.3f) {
                strafingBackwards = !strafingBackwards
            }
            strafingTime = 0
        }

        if (strafingTime >= 0) {
            skeleton.moveControl.strafeTo(
                if (strafingBackwards) -0.5f else 0.5f,
                if (strafingClockwise) 0.5f else -0.5f
            )
        } else {
            skeleton.moveControl.strafeTo(0.0f, 0.0f)
        }

        skeleton.lookAtEntity(target, 30f, 30f)

        if (cooldown > 0) {
            cooldown--
        }

        if (withinRange && targetSeeingTicker >= 20 && canSee) {
            skeleton.beginWindUp()
            if (!skeleton.isWindingUp() || skeleton.consumeWindUpTick()) {
                skeleton.shootAt(target, 1.0f)
                cooldown = attackInterval
                skeleton.resetWindUp()
            }
        } else {
            skeleton.resetWindUp()
        }
    }
}

private class WanderToHighGroundGoal(
    private val skeleton: SniperSkeletonEntity,
    private val speed: Double
) : Goal() {
    private var targetPos: BlockPos? = null
    private var cooldown = 0

    init {
        setControls(EnumSet.of(Control.MOVE))
    }

    override fun canStart(): Boolean {
        if (skeleton.target != null) return false
        if (cooldown > 0) {
            cooldown--
            return false
        }
        cooldown = 40 + skeleton.random.nextInt(40)
        targetPos = chooseHighGround() ?: return false
        return true
    }

    override fun shouldContinue(): Boolean = targetPos != null && !skeleton.navigation.isIdle

    override fun start() {
        targetPos?.let {
            skeleton.navigation.startMovingTo(it.x + 0.5, it.y.toDouble(), it.z + 0.5, speed)
        }
    }

    override fun stop() {
        targetPos = null
    }

    private fun chooseHighGround(): BlockPos? {
        val world = skeleton.world
        val origin = skeleton.blockPos
        val radius = 12
        var bestPos: BlockPos? = null
        var bestScore = Double.NEGATIVE_INFINITY

        repeat(40) {
            val dx = skeleton.random.nextBetween(-radius, radius)
            val dz = skeleton.random.nextBetween(-radius, radius)
            val candidateX = origin.x + dx
            val candidateZ = origin.z + dz
            val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidateX, candidateZ)
            if (topY <= world.bottomY) return@repeat
            val pos = BlockPos(candidateX, topY, candidateZ)

            if (!world.isAir(pos)) return@repeat
            val below = pos.down()
            if (world.isAir(below)) return@repeat
            val path = skeleton.navigation.findPathTo(pos, 0) ?: return@repeat

            var score = (pos.y - origin.y).toDouble()
            if (world.isSkyVisible(pos)) {
                if (world.isDay) {
                    score -= 1000.0
                } else {
                    score += 2.0
                }
            }

            val nearestPlayer = world.getClosestPlayer(
                skeleton.x,
                skeleton.y,
                skeleton.z,
                48.0
            ) { it.isAlive && !it.isSpectator }

            if (nearestPlayer != null && hasLineOfSight(pos, nearestPlayer)) {
                score += 3.0
            }

            if (score > bestScore) {
                bestPos = pos
                bestScore = score
            }
        }
        return bestPos
    }

    private fun hasLineOfSight(pos: BlockPos, player: PlayerEntity): Boolean {
        val world = skeleton.world
        val start = Vec3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
        val end = Vec3d(player.x, player.eyeY, player.z)
        val result = world.raycast(
            RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, skeleton)
        )
        return result.type == HitResult.Type.MISS
    }
}

private fun raycastForTracer(world: ServerWorld, start: Vec3d, end: Vec3d, shooter: SniperSkeletonEntity): Vec3d {
    val result = world.raycast(
        RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter)
    )
    return if (result.type == HitResult.Type.BLOCK) {
        (result as net.minecraft.util.hit.BlockHitResult).pos
    } else {
        end
    }
}

private fun spawnTracer(world: ServerWorld, start: Vec3d, end: Vec3d, step: Double) {
    val direction = end.subtract(start)
    val length = direction.length()
    if (length <= 1.0e-4) return
    val steps = (length / step).toInt().coerceIn(1, 40)
    val increment = direction.multiply(1.0 / steps)
    var current = start
    repeat(steps + 1) {
        world.spawnParticles(ParticleTypes.END_ROD, current.x, current.y, current.z, 1, 0.0, 0.0, 0.0, 0.0)
        current = current.add(increment)
    }
}
