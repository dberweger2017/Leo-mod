package fuzuki.test.client.render

import fuzuki.test.common.entity.MegaZombieEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.ZombieBaseEntityRenderer
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.ZombieEntityModel
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.util.Identifier

class MegaZombieRenderer(context: EntityRendererFactory.Context) :
    ZombieBaseEntityRenderer<MegaZombieEntity, ZombieEntityModel<MegaZombieEntity>>(
        context,
        ZombieEntityModel(context.getPart(EntityModelLayers.ZOMBIE)),
        ZombieEntityModel(context.getPart(EntityModelLayers.ZOMBIE_INNER_ARMOR)),
        ZombieEntityModel(context.getPart(EntityModelLayers.ZOMBIE_OUTER_ARMOR))
    ) {

    override fun scale(entity: MegaZombieEntity, matrices: MatrixStack, amount: Float) {
        matrices.scale(SCALE, SCALE, SCALE)
        super.scale(entity, matrices, amount)
    }

    override fun getTexture(entity: ZombieEntity): Identifier = TEXTURE

    companion object {
        private const val SCALE = 1.5f
        private val TEXTURE: Identifier = Identifier.of("mod_de_leo", "textures/entity/mega_zombie.png")
    }
}
