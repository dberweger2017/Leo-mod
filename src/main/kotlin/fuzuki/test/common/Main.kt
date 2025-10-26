package fuzuki.test.common

import fuzuki.test.common.registry.ModEffects
import fuzuki.test.common.registry.ModEntityTypes
import fuzuki.test.common.registry.ModItems
import fuzuki.test.common.world.ModSpawns
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import org.slf4j.LoggerFactory

const val MOD_ID = "mod_de_leo"

class Main : ModInitializer {
    override fun onInitialize() {
        ModEffects.register()
        ModEntityTypes.register()
        ModItems.register()
        ModSpawns.register()
        registerPanicBoostHandler()

        LOGGER.info("Mod de Leo initialized")
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MOD_ID)
        private const val PANIC_RADIUS = 16.0
        private const val PANIC_DURATION_TICKS = 20 * 5

        private fun registerPanicBoostHandler() {
            ServerLivingEntityEvents.AFTER_DAMAGE.register(
                ServerLivingEntityEvents.AfterDamage { entity: LivingEntity, source, _, _, _ ->
                    val attacker = source.attacker as? PlayerEntity ?: return@AfterDamage
                    if (entity.world.isClient) return@AfterDamage
                    val hitAnimal = entity as? AnimalEntity ?: return@AfterDamage

                    applyPanicBoost(hitAnimal, attacker.pos)

                    val neighbors = entity.world.getEntitiesByClass(
                        AnimalEntity::class.java,
                        entity.boundingBox.expand(PANIC_RADIUS)
                    ) { it !== hitAnimal }

                    neighbors.forEach { applyPanicBoost(it, attacker.pos) }
                }
            )
        }

        private fun applyPanicBoost(animal: AnimalEntity, attackerPos: Vec3d) {
            if (!animal.hasStatusEffect(ModEffects.PANIC_BOOST_ENTRY)) {
                animal.addStatusEffect(
                    StatusEffectInstance(ModEffects.PANIC_BOOST_ENTRY, PANIC_DURATION_TICKS, 0, false, false, false)
                )
            }

            val direction = animal.pos.subtract(attackerPos).normalize()
            if (direction.lengthSquared() > 0.0) {
                animal.addVelocity(direction.x * 0.6, 0.15, direction.z * 0.6)
                animal.velocityModified = true
            }
        }
    }
}
