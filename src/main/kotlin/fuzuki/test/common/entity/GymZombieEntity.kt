package fuzuki.test.common.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.world.World

class GymZombieEntity(entityType: EntityType<out GymZombieEntity>, world: World) : ZombieEntity(entityType, world) {

    init {
        health = maxHealth
    }

    override fun tryAttack(target: net.minecraft.entity.Entity?): Boolean {
        if (!world.isClient && target is LivingEntity) {
            launchUpwards(target)
        }
        return true
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        if (!world.isClient && source.attacker is LivingEntity) {
            launchUpwards(source.attacker as LivingEntity)
        }
        return false
    }

    private fun launchUpwards(entity: LivingEntity) {
        entity.velocity = entity.velocity.add(0.0, VERTICAL_BOOST, 0.0)
        entity.velocityModified = true
    }

    companion object {
        private const val VERTICAL_BOOST = 14.0

        fun createGymZombieAttributes(): DefaultAttributeContainer.Builder =
            ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23 * 1.2)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0 * 1.5)
    }
}
