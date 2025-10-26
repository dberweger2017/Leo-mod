package fuzuki.test.common.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class GymZombieEntity(entityType: EntityType<out GymZombieEntity>, world: World) : ZombieEntity(entityType, world) {

    init {
        health = maxHealth
    }

    override fun tryAttack(target: net.minecraft.entity.Entity?): Boolean {
        val livingTarget = target as? LivingEntity ?: return super.tryAttack(target)
        return if (!world.isClient && random.nextInt(3) == 0) {
            launchUpwards(livingTarget)
            true
        } else {
            val baseDamage = getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE).toFloat()
            val dealt = livingTarget.damage(damageSources.mobAttack(this), baseDamage * 2.0f)
            dealt
        }
    }

    private fun launchUpwards(entity: LivingEntity) {
        val pushDistance = 10.0 + random.nextDouble() * 10.0
        val direction = entity.pos.subtract(this.pos)
        val horizontalDirection = if (direction.horizontalLengthSquared() > 1.0e-4) {
            Vec3d(direction.x, 0.0, direction.z).normalize()
        } else {
            val angle = random.nextFloat() * (2.0 * Math.PI)
            Vec3d(Math.cos(angle), 0.0, Math.sin(angle))
        }

        val horizontalBoost = pushDistance * HORIZONTAL_MULTIPLIER
        entity.velocity = entity.velocity.add(
            horizontalDirection.x * horizontalBoost,
            VERTICAL_BOOST,
            horizontalDirection.z * horizontalBoost
        )
        entity.velocityModified = true
    }

    companion object {
        private const val VERTICAL_BOOST = 1.1
        private const val HORIZONTAL_MULTIPLIER = 0.6

        fun createGymZombieAttributes(): DefaultAttributeContainer.Builder =
            ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23 * 1.2)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0 * 1.5)
    }
}
