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
        val id = Identifier.of(MOD_ID, "panic_boost")
        Registry.register(Registries.STATUS_EFFECT, id, PANIC_BOOST)
        PANIC_BOOST_ENTRY = Registries.STATUS_EFFECT.getEntry(id).orElseThrow {
            IllegalStateException("Failed to obtain registry entry for panic boost effect")
        }
    }
}
