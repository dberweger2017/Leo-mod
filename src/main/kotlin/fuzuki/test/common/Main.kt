package fuzuki.test.common

import fuzuki.test.common.registry.ModEffects
import fuzuki.test.common.registry.ModEntityTypes
import fuzuki.test.common.registry.ModItems
import fuzuki.test.common.world.ModSpawns
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

const val MOD_ID = "mod_de_leo"

class Main : ModInitializer {
    override fun onInitialize() {
        ModEffects.register()
        ModEntityTypes.register()
        ModItems.register()
        ModSpawns.register()

        LOGGER.info("Mod de Leo initialized")
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MOD_ID)
    }
}
