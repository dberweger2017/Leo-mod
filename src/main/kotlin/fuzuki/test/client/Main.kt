package fuzuki.test.client

import fuzuki.test.client.render.GymZombieRenderer
import fuzuki.test.client.render.MegaZombieRenderer
import fuzuki.test.client.render.SuperCreeperRenderer
import fuzuki.test.client.render.TntSkeletonRenderer
import fuzuki.test.common.registry.ModEntityTypes
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

class Main : ClientModInitializer {
    override fun onInitializeClient() {
        EntityRendererRegistry.register(ModEntityTypes.SUPER_CREEPER) { context ->
            SuperCreeperRenderer(context)
        }
        EntityRendererRegistry.register(ModEntityTypes.MEGA_ZOMBIE) { context ->
            MegaZombieRenderer(context)
        }
        EntityRendererRegistry.register(ModEntityTypes.TNT_SKELETON) { context ->
            TntSkeletonRenderer(context)
        }
        EntityRendererRegistry.register(ModEntityTypes.GYM_ZOMBIE, ::GymZombieRenderer)
    }
}
