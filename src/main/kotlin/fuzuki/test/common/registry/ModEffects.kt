package fuzuki.test.common.registry

import fuzuki.test.common.MOD_ID
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectCategory
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier

object ModEffects {
    private val PANIC_BOOST_ID: Identifier = Identifier.of(MOD_ID, "panic_boost")

    val PANIC_BOOST: StatusEffect = object : StatusEffect(StatusEffectCategory.BENEFICIAL, 0xFFAA00) {
        init {
            addAttributeModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                Identifier.of(MOD_ID, "panic_speed_modifier"),
                0.5,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            )
        }
    }

    lateinit var PANIC_BOOST_ENTRY: RegistryEntry<StatusEffect>
        private set

    fun register() {
        Registry.register(Registries.STATUS_EFFECT, PANIC_BOOST_ID, PANIC_BOOST)
        PANIC_BOOST_ENTRY = Registries.STATUS_EFFECT.getEntry(PANIC_BOOST_ID).orElseThrow {
            IllegalStateException("Failed to retrieve registry entry for panic boost effect")
        }
    }
}
