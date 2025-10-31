package fuzuki.test.common.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.LocalDifficulty
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.EntityData
import net.minecraft.world.ServerWorldAccess
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects

class GymZombieEntity(entityType: EntityType<out GymZombieEntity>, world: World) : ZombieEntity(entityType, world) {
    private var launchCooldownTicks = 0

    override fun tick() {
        super.tick()
        if (!world.isClient && launchCooldownTicks > 0) {
            launchCooldownTicks--
        }
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
        val livingTarget = target as? LivingEntity ?: return super.tryAttack(target)
        if (!super.tryAttack(livingTarget)) return false

        val canLaunch = !world.isClient && launchCooldownTicks <= 0 && random.nextInt(LAUNCH_CHANCE) == 0
        if (canLaunch) {
            performLaunch(livingTarget)
            return true
        }

        val bonusDamage = getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE).toFloat() * HEAVY_PUNCH_MULTIPLIER
        livingTarget.damage(damageSources.mobAttack(this), bonusDamage)
        playSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.9f + random.nextFloat() * 0.2f)
        return true
    }

    private fun performLaunch(target: LivingEntity) {
        playSound(SoundEvents.ENTITY_HOGLIN_ATTACK, 1.0f, 0.75f + random.nextFloat() * 0.1f)

        val offset = target.pos.subtract(pos)
        val horizontal = Vec3d(offset.x, 0.0, offset.z)
        val norm = if (horizontal.lengthSquared() > 1.0e-4) horizontal.normalize() else randomHorizontal()

        // baseline knockback (respects resistance)
        target.takeKnockback(BASE_KNOCKBACK, x - target.x, z - target.z)

        val vx = (norm.x * LAUNCH_HORIZONTAL).coerceIn(-MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED)
        val vz = (norm.z * LAUNCH_HORIZONTAL).coerceIn(-MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED)
        target.addVelocity(vx, LAUNCH_VERTICAL, vz)
        target.velocityModified = true

        target.addStatusEffect(StatusEffectInstance(StatusEffects.SLOW_FALLING, SLOW_FALL_DURATION_TICKS, 0))

        launchCooldownTicks = LAUNCH_COOLDOWN_TICKS
    }

    private fun randomHorizontal(): Vec3d {
        val angle = random.nextDouble() * (2.0 * Math.PI)
        return Vec3d(Math.cos(angle), 0.0, Math.sin(angle))
    }

    companion object {
        private const val LAUNCH_CHANCE = 3
        private const val LAUNCH_COOLDOWN_TICKS = 40
        private const val LAUNCH_VERTICAL = 0.9
        private const val LAUNCH_HORIZONTAL = 0.7
        private const val MAX_HORIZONTAL_SPEED = 0.8
        private const val BASE_KNOCKBACK = 0.6
        private const val HEAVY_PUNCH_MULTIPLIER = 1.2f
        private const val SLOW_FALL_DURATION_TICKS = 40

        fun createGymZombieAttributes(): DefaultAttributeContainer.Builder =
            ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 36.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23 * 1.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.25)
    }
}
