package fuzuki.test.common.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.AvoidSunlightGoal
import net.minecraft.entity.ai.goal.BowAttackGoal
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
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import net.minecraft.entity.projectile.ArrowEntity
import java.util.EnumSet

class SniperSkeletonEntity(entityType: EntityType<out SniperSkeletonEntity>, world: World) :
    SkeletonEntity(entityType, world) {

    override fun initGoals() {
        goalSelector.add(0, FleeEntityGoal(this, PlayerEntity::class.java, MIN_COMFORT_DIST, 1.0, FLEE_SPEED_MULT))
        goalSelector.add(1, AvoidSunlightGoal(this))
        goalSelector.add(2, EscapeSunlightGoal(this, 1.0))

        goalSelector.add(3, BowAttackGoal(this as SkeletonEntity, 1.0, 80, FOLLOW_RANGE.toFloat()))
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
        // no bow usage
    }

    override fun shootAt(target: LivingEntity, pullProgress: Float) {
        if (world.isClient) return
        val serverWorld = world as ServerWorld

        val bowStack = getStackInHand(ProjectileUtil.getHandPossiblyHolding(this, Items.BOW))
        val projectileStack = getProjectileType(bowStack)
        val projectile = createArrowProjectile(projectileStack, pullProgress, bowStack)

        if (projectile is ArrowEntity) {
            projectile.damage = 9.0
            projectile.addEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0))
            projectile.addEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1))
            projectile.addEffect(StatusEffectInstance(StatusEffects.POISON, 100, 1))
        }

        val start = Vec3d(x, eyeY - 0.1, z)
        projectile.setPosition(start.x, start.y, start.z)

        val aimBase = Vec3d(target.x, target.eyeY, target.z)
        val speed = BASE_ARROW_SPEED * SPEED_MULTIPLIER
        val toBase = aimBase.subtract(start)
        val distance = toBase.length()
        val lead = target.velocity.multiply(distance / speed.toDouble())
        val aim = aimBase.add(lead)

        val direction = aim.subtract(start)
        projectile.setVelocity(direction.x, direction.y, direction.z, speed, 0.0f)
        projectile.isCritical = pullProgress >= 1.0f

        serverWorld.spawnEntity(projectile)
        serverWorld.playSound(null, blockPos, SoundEvents.ENTITY_ARROW_SHOOT, soundCategory, 1f, 0.8f)

        val rayEnd = raycastForTracer(serverWorld, start, aim, this)
        spawnTracer(serverWorld, start, rayEnd, 0.5)
    }

    companion object {
        const val MIN_COMFORT_DIST = 12.0f
        private const val FLEE_SPEED_MULT = 2.0
        private const val BASE_ARROW_SPEED = 3.0f
        private const val SPEED_MULTIPLIER = 10.0f
        private const val FOLLOW_RANGE = 32.0

        fun createAttributes(): DefaultAttributeContainer.Builder =
            AbstractSkeletonEntity.createAbstractSkeletonAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
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
            val path = skeleton.navigation.findPathTo(pos, 0)
            if (path == null) return@repeat

            val heightBonus = (pos.y - origin.y).toDouble()
            val skyBonus = if (world.isSkyVisible(pos)) 2.0 else 0.0
            val score = heightBonus + skyBonus
            if (score > bestScore) {
                bestPos = pos
                bestScore = score
            }
        }
        return bestPos
    }
}

private fun raycastForTracer(world: ServerWorld, start: Vec3d, end: Vec3d, shooter: SniperSkeletonEntity): Vec3d {
    val result = world.raycast(
        RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter)
    )
    return when (result.type) {
        HitResult.Type.BLOCK -> (result as net.minecraft.util.hit.BlockHitResult).pos
        else -> end
    }
}

private fun spawnTracer(world: ServerWorld, start: Vec3d, end: Vec3d, step: Double) {
    val direction = end.subtract(start)
    val length = direction.length()
    if (length <= 1.0e-4) return
    val steps = (length / step).toInt().coerceAtLeast(1)
    val increment = direction.multiply(1.0 / steps)
    var current = start
    repeat(steps + 1) {
        world.spawnParticles(ParticleTypes.END_ROD, current.x, current.y, current.z, 1, 0.0, 0.0, 0.0, 0.0)
        current = current.add(increment)
    }
}
