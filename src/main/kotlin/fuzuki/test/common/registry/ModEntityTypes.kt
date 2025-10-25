package fuzuki.test.common.registry

import fuzuki.test.common.MOD_ID
import fuzuki.test.common.entity.MegaZombieEntity
import fuzuki.test.common.entity.SuperCreeperEntity
import fuzuki.test.common.entity.TntSkeletonEntity
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.AbstractSkeletonEntity
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModEntityTypes {
    val SUPER_CREEPER: EntityType<SuperCreeperEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(MOD_ID, "super_creeper"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::SuperCreeperEntity)
            .dimensions(EntityDimensions.fixed(0.6f, 1.7f))
            .trackRangeBlocks(8)
            .trackedUpdateRate(3)
            .build()
    )

    val MEGA_ZOMBIE: EntityType<MegaZombieEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(MOD_ID, "mega_zombie"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::MegaZombieEntity)
            .dimensions(EntityDimensions.changing(0.6f * 1.5f, 1.95f * 1.5f))
            .trackRangeBlocks(8)
            .trackedUpdateRate(3)
            .build()
    )

    val TNT_SKELETON: EntityType<TntSkeletonEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(MOD_ID, "tnt_skeleton"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::TntSkeletonEntity)
            .dimensions(EntityDimensions.fixed(0.6f, 1.99f))
            .trackRangeBlocks(8)
            .trackedUpdateRate(3)
            .build()
    )

    fun register() {
        FabricDefaultAttributeRegistry.register(SUPER_CREEPER, CreeperEntity.createCreeperAttributes())
        FabricDefaultAttributeRegistry.register(MEGA_ZOMBIE, MegaZombieEntity.createMegaZombieAttributes())
        FabricDefaultAttributeRegistry.register(TNT_SKELETON, AbstractSkeletonEntity.createAbstractSkeletonAttributes())
    }
}
