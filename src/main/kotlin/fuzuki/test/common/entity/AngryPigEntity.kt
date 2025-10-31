package fuzuki.test.common.entity

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.goal.ActiveTargetGoal
import net.minecraft.entity.ai.goal.LookAroundGoal
import net.minecraft.entity.ai.goal.LookAtEntityGoal
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.entity.ai.goal.RevengeGoal
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.ai.goal.WanderAroundFarGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.world.World
import net.minecraft.world.ServerWorldAccess

class AngryPigEntity(entityType: EntityType<out AngryPigEntity>, world: World) :
    PathAwareEntity(entityType, world) {

    override fun initGoals() {
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(1, MeleeAttackGoal(this, 1.2, true))
        goalSelector.add(2, WanderAroundFarGoal(this, 1.0))
        goalSelector.add(3, LookAtEntityGoal(this, PlayerEntity::class.java, 8.0f))
        goalSelector.add(4, LookAroundGoal(this))

        targetSelector.add(1, ActiveTargetGoal(this, PlayerEntity::class.java, true))
        targetSelector.add(2, ActiveTargetGoal(this, VillagerEntity::class.java, true))
        targetSelector.add(3, RevengeGoal(this, AngryPigEntity::class.java))
    }

    override fun tryAttack(target: Entity?): Boolean {
        val livingTarget = target as? LivingEntity ?: return super.tryAttack(target)
        if (handSwinging) return false
        swingHand(Hand.MAIN_HAND)
        playSound(SoundEvents.ENTITY_PIG_HURT, 1.0f, 0.9f + random.nextFloat() * 0.2f)

        val success = super.tryAttack(livingTarget)
        if (success) {
            livingTarget.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, SLOWNESS_DURATION, SLOWNESS_AMPLIFIER))
        }
        return success
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        val attacked = super.damage(source, amount)
        if (!world.isClient && attacked) {
            val attacker = resolveAttacker(source)
            if (attacker != null) {
                target = attacker
            }
        }
        return attacked
    }

    override fun getStepHeight(): Float = 1.1f

    private fun resolveAttacker(source: DamageSource): LivingEntity? {
        val direct = source.attacker ?: source.source
        return when (direct) {
            is LivingEntity -> direct
            is ProjectileEntity -> direct.owner as? LivingEntity ?: direct as? LivingEntity
            else -> null
        }
    }

    companion object {
        private const val SLOWNESS_DURATION = 60 // 3 seconds
        private const val SLOWNESS_AMPLIFIER = 0

        fun createAttributes(): DefaultAttributeContainer.Builder =
            MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 14.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.15)

        fun canSpawn(
            type: EntityType<AngryPigEntity>,
            world: ServerWorldAccess,
            reason: SpawnReason,
            pos: BlockPos,
            random: Random
        ): Boolean {
            if (world.getBaseLightLevel(pos, 0) <= 8) return false
            if (!world.getBlockState(pos.down()).isOpaque) return false
            return MobEntity.canMobSpawn(type, world, reason, pos, random)
        }
    }
}
