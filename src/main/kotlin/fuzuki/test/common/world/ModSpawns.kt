package fuzuki.test.common.world

import fuzuki.test.common.MOD_ID
import fuzuki.test.common.registry.ModEntityTypes
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.SpawnRestriction
import net.minecraft.entity.mob.HostileEntity

object ModSpawns {
    fun register() {
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            ModEntityTypes.MEGA_ZOMBIE,
            MEGA_ZOMBIE_WEIGHT,
            1,
            2
        )
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            ModEntityTypes.SUPER_CREEPER,
            SUPER_CREEPER_WEIGHT,
            1,
            1
        )
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            ModEntityTypes.TNT_SKELETON,
            TNT_SKELETON_WEIGHT,
            1,
            2
        )

        SpawnRestriction.register(
            ModEntityTypes.MEGA_ZOMBIE,
            SpawnRestriction.getLocation(EntityType.ZOMBIE),
            SpawnRestriction.getHeightmapType(EntityType.ZOMBIE),
            HostileEntity::canSpawnInDark
        )
        SpawnRestriction.register(
            ModEntityTypes.SUPER_CREEPER,
            SpawnRestriction.getLocation(EntityType.CREEPER),
            SpawnRestriction.getHeightmapType(EntityType.CREEPER),
            HostileEntity::canSpawnInDark
        )
        SpawnRestriction.register(
            ModEntityTypes.TNT_SKELETON,
            SpawnRestriction.getLocation(EntityType.SKELETON),
            SpawnRestriction.getHeightmapType(EntityType.SKELETON),
            HostileEntity::canSpawnInDark
        )
    }

    private const val MEGA_ZOMBIE_WEIGHT = 10
    private const val SUPER_CREEPER_WEIGHT = 10
    private const val TNT_SKELETON_WEIGHT = 10
}
