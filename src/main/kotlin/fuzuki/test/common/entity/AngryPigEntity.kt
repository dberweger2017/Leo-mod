package fuzuki.test.common.entity

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.random.Random
import net.minecraft.world.World
import net.minecraft.world.ServerWorldAccess

class AngryPigEntity(entityType: EntityType<out AngryPigEntity>, world: World) :
    PathAwareEntity(entityType, world) {

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(1, MeleeAttackGoal(this, 1.2, false))
        goalSelector.add(2, WanderAroundFarGoal(this, 1.0))
        goalSelector.add(3, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))
        goalSelector.add(4, LookAroundGoal(this))

        targetSelector.add(1, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(2, RevengeGoal(this))
    }

    override fun tryAttack(target: Entity?): Boolean {
        val livingTarget = target as? LivingEntity ?: return super.tryAttack(target)
        val success = super.tryAttack(livingTarget)
        if (success) {
            livingTarget.addStatusEffect(
                net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS,
                    SLOWNESS_DURATION,
                    SLOWNESS_AMPLIFIER
                )
            )
            playSound(SoundEvents.ENTITY_PIG_AMBIENT, 1.0f, 0.8f + random.nextFloat() * 0.4f)
        }
        return success
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        val attacked = super.damage(source, amount)
        if (!world.isClient && attacked) {
            val attacker = source.attacker as? PlayerEntity ?: return attacked
            val radius = 12.0
            world.getOtherEntities(this, Box.of(pos, radius, radius, radius)) {
                it is AngryPigEntity && it.isAlive && it.target == null
            }.forEach { entity ->
                (entity as AngryPigEntity).target = attacker
            }
        }
        return attacked
    }

    companion object {
        private const val SLOWNESS_DURATION = 60 // 3 seconds
        private const val SLOWNESS_AMPLIFIER = 0

        fun createAttributes(): DefaultAttributeContainer.Builder =
            MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0)

        fun canSpawn(
            type: EntityType<AngryPigEntity>,
            world: ServerWorldAccess,
            reason: SpawnReason,
            pos: BlockPos,
            random: Random
        ): Boolean {
            return world.getBaseLightLevel(pos, 0) > 8 && MobEntity.canMobSpawn(type, world, reason, pos, random)
        }
    }
}
