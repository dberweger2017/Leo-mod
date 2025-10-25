package fuzuki.test.common.entity

import com.mojang.logging.LogUtils
import net.minecraft.entity.EntityType
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class MegaZombieEntity(entityType: EntityType<out ZombieEntity>, world: World) : ZombieEntity(entityType, world) {
    private var spawnCooldown = COOLDOWN_TICKS

    override fun tick() {
        super.tick()
        if (world.isClient) return

        if (spawnCooldown > 0) spawnCooldown--

        if (spawnCooldown == 0) {
            val target = world.getClosestPlayer(this, SPAWN_RADIUS.toDouble())
            if (target != null && this.squaredDistanceTo(target) <= SPAWN_RADIUS * SPAWN_RADIUS) {
                spawnZombiesAround(target)
                spawnCooldown = COOLDOWN_TICKS
            }
        }
    }

    private fun spawnZombiesAround(player: PlayerEntity) {
        val serverWorld = world as? ServerWorld ?: return

        val positions = listOf(
            Vec3d(2.0, 0.0, 0.0),
            Vec3d(-2.0, 0.0, 0.0),
            Vec3d(0.0, 0.0, 2.0),
            Vec3d(0.0, 0.0, -2.0)
        )

        positions.forEach { offset ->
            val spawnPos = BlockPos.ofFloored(player.pos.add(offset))
            val zombie = EntityType.ZOMBIE.create(serverWorld)
            if (zombie != null) {
                zombie.refreshPositionAndAngles(spawnPos, random.nextFloat() * 360f, 0f)
                serverWorld.spawnEntity(zombie)
            } else {
                LOGGER.warn("Failed to spawn helper zombie for Mega Zombie at {}", spawnPos)
            }
        }
    }

    companion object {
        private val LOGGER = LogUtils.getLogger()
        private const val COOLDOWN_SECONDS = 30
        private const val TICKS_PER_SECOND = 20
        private const val COOLDOWN_TICKS = COOLDOWN_SECONDS * TICKS_PER_SECOND
        private const val SPAWN_RADIUS = 3f

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
