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
            4
        )
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            ModEntityTypes.GYM_ZOMBIE,
            GYM_ZOMBIE_WEIGHT,
            1,
            3
        )
        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            SpawnGroup.MONSTER,
            ModEntityTypes.ANGRY_PIG,
            ANGRY_PIG_WEIGHT,
            2,
            4
        )
        val deserts = BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.IS_DESERT)
        val badlands = BiomeSelectors.tag(net.minecraft.registry.tag.BiomeTags.IS_BADLANDS)
        val hills = BiomeSelectors.foundInOverworld().and { it.biome.value().depth > 0.4f }
        val open = BiomeSelectors.foundInOverworld().and { it.biome.value().hasPrecipitation && it.biome.value().depth >= 0f }
        val notOcean = BiomeSelectors.foundInOverworld().and { !it.biome.value().category.isOcean }

        BiomeModifications.addSpawn(
            deserts.or(badlands),
            SpawnGroup.MONSTER,
            ModEntityTypes.PYROMANIAC,
            PYROMANIAC_WEIGHT,
            1,
            2
        )

        BiomeModifications.addSpawn(
            open.or(hills),
            SpawnGroup.MONSTER,
            ModEntityTypes.SNIPER_SKELETON,
            SNIPER_SKELETON_WEIGHT,
            1,
            2
        )

        BiomeModifications.addSpawn(
            notOcean,
            SpawnGroup.MONSTER,
            ModEntityTypes.UNDEAD_MINER,
            UNDEAD_MINER_WEIGHT,
            1,
            2
        )

        registerHostile(ModEntityTypes.MEGA_ZOMBIE)
        registerHostile(ModEntityTypes.SUPER_CREEPER)
        registerHostile(ModEntityTypes.TNT_SKELETON)
        registerHostile(ModEntityTypes.GYM_ZOMBIE)
        registerHostile(ModEntityTypes.SNIPER_SKELETON)
        registerHostile(ModEntityTypes.UNDEAD_MINER)
        registerHostile(ModEntityTypes.PYROMANIAC)
        SpawnRestriction.register(
            ModEntityTypes.ANGRY_PIG,
            SpawnRestriction.Location.ON_GROUND,
            net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            fuzuki.test.common.entity.AngryPigEntity::canSpawn
        )
    }

    private fun registerHostile(entityType: EntityType<*>) {
        SpawnRestriction.register(
            entityType,
            SpawnRestriction.Location.ON_GROUND,
            net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            HostileEntity::canSpawnInDark
        )
    }

    private const val MEGA_ZOMBIE_WEIGHT = 2
    private const val SUPER_CREEPER_WEIGHT = 2
    private const val TNT_SKELETON_WEIGHT = 4
    private const val GYM_ZOMBIE_WEIGHT = 4
    private const val SNIPER_SKELETON_WEIGHT = 3
    private const val ANGRY_PIG_WEIGHT = 6
    private const val UNDEAD_MINER_WEIGHT = 6
    private const val PYROMANIAC_WEIGHT = 3
}
