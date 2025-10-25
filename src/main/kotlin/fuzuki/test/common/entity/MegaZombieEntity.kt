package fuzuki.test.common.entity

import com.mojang.logging.LogUtils
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class MegaZombieEntity(entityType: EntityType<out ZombieEntity>, world: World) : ZombieEntity(entityType, world) {
    private var spawnCooldown = 0

    override fun tick() {
        super.tick()
        if (world.isClient) return

        if (spawnCooldown > 0) spawnCooldown--
    }

    override fun damage(source: DamageSource, amount: Float): Boolean {
        val result = super.damage(source, amount)
        if (result && !world.isClient && spawnCooldown == 0) {
            val targetEntity = source.attacker ?: source.source
            val centerPos = (targetEntity ?: this).pos
            spawnZombiesAround(centerPos)
            spawnCooldown = COOLDOWN_TICKS
        }
        return result
    }

    private fun spawnZombiesAround(center: Vec3d) {
        val serverWorld = world as? ServerWorld ?: return

        val offsets = listOf(0, 60, 120, 180, 240, 300).map { angleDegrees ->
            val radians = Math.toRadians(angleDegrees.toDouble())
            Vec3d(Math.cos(radians) * SPAWN_RADIUS, 0.0, Math.sin(radians) * SPAWN_RADIUS)
        }

        offsets.forEach { offset ->
            val spawnPos = BlockPos.ofFloored(center.add(offset))
            val zombie = EntityType.ZOMBIE.create(serverWorld)
            if (zombie != null) {
                zombie.refreshPositionAndAngles(spawnPos, random.nextFloat() * 360f, 0f)
                serverWorld.spawnEntity(zombie)
            } else {
                LOGGER.warn("Failed to spawn helper zombie for Mega Zombie at {}", spawnPos)
            }
        }
    }

    override fun onDeath(source: DamageSource) {
        super.onDeath(source)
        val serverWorld = world as? ServerWorld ?: return
        val zombie = EntityType.ZOMBIE.create(serverWorld) ?: return
        zombie.refreshPositionAndAngles(this.pos, this.yaw, this.pitch)
        zombie.equipStack(EquipmentSlot.MAINHAND, ItemStack(Items.DIAMOND_SWORD))
        zombie.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f)
        serverWorld.spawnEntity(zombie)
    }

    companion object {
        private val LOGGER = LogUtils.getLogger()
        private const val COOLDOWN_SECONDS = 30
        private const val TICKS_PER_SECOND = 20
        private const val COOLDOWN_TICKS = COOLDOWN_SECONDS * TICKS_PER_SECOND
        private const val SPAWN_RADIUS = 2.0
        fun createMegaZombieAttributes(): DefaultAttributeContainer.Builder =
            ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, DEFAULT_ZOMBIE_HEALTH * HEALTH_MULTIPLIER)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, DEFAULT_ZOMBIE_SPEED)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, DEFAULT_ZOMBIE_ATTACK * DAMAGE_MULTIPLIER)

        private const val DEFAULT_ZOMBIE_HEALTH = 20.0
        private const val DEFAULT_ZOMBIE_SPEED = 0.23
        private const val DEFAULT_ZOMBIE_ATTACK = 3.0
        private const val HEALTH_MULTIPLIER = 2.0
        private const val DAMAGE_MULTIPLIER = 1.0
    }
}
