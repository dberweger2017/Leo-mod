package fuzuki.test.common

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class Main : ModInitializer {
    override fun onInitialize() {
        LOGGER.info("Mod de Leo initialized")
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("mod_de_leo")
    }
}
