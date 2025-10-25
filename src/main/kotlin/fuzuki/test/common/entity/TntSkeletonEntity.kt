package fuzuki.test.common.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.entity.TntEntity

class TntSkeletonEntity(entityType: EntityType<out TntSkeletonEntity>, world: World) :
    SkeletonEntity(entityType, world) {

    override fun shootAt(target: LivingEntity, pullProgress: Float) {
        if (world.isClient) return
        val hand = ProjectileUtil.getHandPossiblyHolding(this, Items.BOW)
        if (hand == null) {
            super.shootAt(target, pullProgress)
            return
        }

        val serverWorld = world as ServerWorld
        val tnt = TntEntity(serverWorld, x, eyeY - 0.2, z, this)
        val d = target.x - x
        val e = target.getBodyY(0.3333333333333333) - tnt.y
        val f = target.z - z
        val direction = Vec3d(d, e, f).normalize()
        val speed = 0.6 + pullProgress * 0.4
        tnt.velocity = direction.multiply(speed)
        tnt.setFuse(DEFAULT_FUSE)

        serverWorld.spawnEntity(tnt)
        world.playSound(null, blockPos, SoundEvents.ENTITY_TNT_PRIMED, soundCategory, 1.0f, 1.0f)
    }

    companion object {
        private const val DEFAULT_FUSE = 80
    }
}
