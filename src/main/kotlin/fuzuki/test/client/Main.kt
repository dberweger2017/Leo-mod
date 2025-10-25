package fuzuki.test.client

import fuzuki.test.common.registry.ModEntityTypes
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.entity.CreeperEntityRenderer

class Main : ClientModInitializer {
    override fun onInitializeClient() {
        EntityRendererRegistry.register(ModEntityTypes.SUPER_CREEPER) { context ->
            CreeperEntityRenderer(context)
        }
    }
}
