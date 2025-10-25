package fuzuki.test.common.entity

import fuzuki.test.mixin.CreeperEntityAccessor
import net.minecraft.entity.EntityType
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.world.World

@Suppress("UNCHECKED_CAST")
class SuperCreeperEntity(entityType: EntityType<out SuperCreeperEntity>, world: World) :
    CreeperEntity(entityType as EntityType<out CreeperEntity>, world) {

    init {
        (this as CreeperEntityAccessor).setExplosionRadius(EXPLOSION_RADIUS)
    }

    companion object {
        private const val EXPLOSION_RADIUS = 30
    }
}
