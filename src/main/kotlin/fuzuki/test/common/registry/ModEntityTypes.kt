package fuzuki.test.common.registry

import fuzuki.test.common.MOD_ID
import fuzuki.test.common.entity.AngryPigEntity
import fuzuki.test.common.entity.GymZombieEntity
import fuzuki.test.common.entity.MegaZombieEntity
import fuzuki.test.common.entity.SniperSkeletonEntity
import fuzuki.test.common.entity.SuperCreeperEntity
import fuzuki.test.common.entity.TntSkeletonEntity
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.mob.CreeperEntity
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

    val GYM_ZOMBIE: EntityType<GymZombieEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(MOD_ID, "gym_zombie"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::GymZombieEntity)
            .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
            .trackRangeBlocks(8)
            .trackedUpdateRate(3)
            .build()
    )

    val ANGRY_PIG: EntityType<AngryPigEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(MOD_ID, "angry_pig"),
        FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ::AngryPigEntity)
            .dimensions(EntityDimensions.fixed(0.9f, 0.9f))
            .trackRangeBlocks(10)
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

    val SNIPER_SKELETON: EntityType<SniperSkeletonEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(MOD_ID, "sniper_skeleton"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::SniperSkeletonEntity)
            .dimensions(EntityDimensions.fixed(0.6f, 1.99f))
            .trackRangeBlocks(12)
            .trackedUpdateRate(3)
            .build()
    )

    fun register() {
        FabricDefaultAttributeRegistry.register(SUPER_CREEPER, CreeperEntity.createCreeperAttributes())
        FabricDefaultAttributeRegistry.register(MEGA_ZOMBIE, MegaZombieEntity.createMegaZombieAttributes())
        FabricDefaultAttributeRegistry.register(GYM_ZOMBIE, GymZombieEntity.createGymZombieAttributes())
        FabricDefaultAttributeRegistry.register(ANGRY_PIG, AngryPigEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(TNT_SKELETON, TntSkeletonEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SNIPER_SKELETON, SniperSkeletonEntity.createAttributes())
    }
}
