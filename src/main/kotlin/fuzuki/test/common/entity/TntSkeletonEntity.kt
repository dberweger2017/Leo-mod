package fuzuki.test.common.entity

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
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.Difficulty
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import java.util.EnumSet

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

    fun shootTnt(target: LivingEntity, pullProgress: Float) {
        if (world.isClient) return
        val serverWorld = world as ServerWorld
        val tnt = TntEntity(serverWorld, x, eyeY - 0.2, z, this)
        val d = target.x - x
        val e = target.getBodyY(0.3333333333333333) - tnt.y
        val f = target.z - z
        val direction = Vec3d(d, e, f).normalize()
        val speed = 0.6 + pullProgress * 0.4
        val velocity = direction.multiply(speed).add(0.0, VERTICAL_BOOST, 0.0)
        tnt.velocity = velocity
        tnt.setFuse(DEFAULT_FUSE)

        serverWorld.spawnEntity(tnt)
        world.playSound(null, blockPos, SoundEvents.ENTITY_TNT_PRIMED, soundCategory, 1.0f, 1.0f)
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
        const val DEFAULT_FUSE = 80
        const val EXTENDED_RANGE = 22.5f
        const val CLOSE_RANGE_FLEE_DISTANCE = 10f
        const val EXTENDED_LOOK_RANGE = 20f
        const val HARD_ATTACK_INTERVAL = 20
        const val REGULAR_ATTACK_INTERVAL = 40
        const val VERTICAL_BOOST = 0.15

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

    override fun canStart(): Boolean = skeleton.target != null

    override fun shouldContinue(): Boolean = canStart() || !skeleton.navigation.isIdle

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
        skeleton.clearActiveItem()
    }

    override fun tick() {
        val target = skeleton.target ?: return
        val distanceSq = skeleton.squaredDistanceTo(target.x, target.y, target.z)
        val canSee = skeleton.visibilityCache.canSee(target)

        targetSeeingTicker = if (canSee) targetSeeingTicker + 1 else targetSeeingTicker - 1

        if (canSee && distanceSq <= squaredRange) {
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
            skeleton.shootTnt(target, 1.0f)
            cooldown = attackInterval
        }
    }

    fun resetAttackInterval(difficulty: Difficulty) {
        attackInterval = if (difficulty == Difficulty.HARD) TntSkeletonEntity.HARD_ATTACK_INTERVAL else TntSkeletonEntity.REGULAR_ATTACK_INTERVAL
    }
}
