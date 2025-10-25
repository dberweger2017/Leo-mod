package fuzuki.test.client.render

import fuzuki.test.common.entity.GymZombieEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.ZombieBaseEntityRenderer
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.ZombieEntityModel
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.util.Identifier

class GymZombieRenderer(context: EntityRendererFactory.Context) :
    ZombieBaseEntityRenderer<GymZombieEntity, ZombieEntityModel<GymZombieEntity>>(
        context,
        ZombieEntityModel(context.getPart(EntityModelLayers.ZOMBIE)),
        ZombieEntityModel(context.getPart(EntityModelLayers.ZOMBIE_INNER_ARMOR)),
        ZombieEntityModel(context.getPart(EntityModelLayers.ZOMBIE_OUTER_ARMOR))
    ) {

    override fun getTexture(entity: ZombieEntity): Identifier = TEXTURE

    override fun scale(entity: GymZombieEntity, matrices: MatrixStack, amount: Float) {
        super.scale(entity, matrices, amount)
    }

    companion object {
        private val TEXTURE = Identifier.of("mod_de_leo", "textures/entity/gym_zombie.png")
    }
}
