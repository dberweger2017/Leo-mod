package fuzuki.test.common.registry

import fuzuki.test.common.MOD_ID
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.ItemGroups
import net.minecraft.item.Item
import net.minecraft.item.SpawnEggItem
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModItems {
    val SUPER_CREEPER_SPAWN_EGG: Item = register(
        "super_creeper_spawn_egg",
        SpawnEggItem(
            ModEntityTypes.SUPER_CREEPER,
            0x0da70b,
            0x000000,
            Item.Settings()
        )
    )

    val MEGA_ZOMBIE_SPAWN_EGG: Item = register(
        "mega_zombie_spawn_egg",
        SpawnEggItem(
            ModEntityTypes.MEGA_ZOMBIE,
            0x799c65,
            0x1b331a,
            Item.Settings()
        )
    )

    val GYM_ZOMBIE_SPAWN_EGG: Item = register(
        "gym_zombie_spawn_egg",
        SpawnEggItem(
            ModEntityTypes.GYM_ZOMBIE,
            0x4a7f2b,
            0xcfcfcf,
            Item.Settings()
        )
    )

    val TNT_SKELETON_SPAWN_EGG: Item = register(
        "tnt_skeleton_spawn_egg",
        SpawnEggItem(
            ModEntityTypes.TNT_SKELETON,
            0xc1c1c1,
            0x4c4c4c,
            Item.Settings()
        )
    )

    private fun register(id: String, item: Item): Item =
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, id), item)

    fun register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register {
            it.add(SUPER_CREEPER_SPAWN_EGG)
            it.add(MEGA_ZOMBIE_SPAWN_EGG)
            it.add(GYM_ZOMBIE_SPAWN_EGG)
            it.add(TNT_SKELETON_SPAWN_EGG)
        }
    }
}
