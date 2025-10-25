package fuzuki.test.client.render

import fuzuki.test.common.entity.TntSkeletonEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.SkeletonEntityRenderer
import net.minecraft.util.Identifier

class TntSkeletonRenderer(context: EntityRendererFactory.Context) :
    SkeletonEntityRenderer<TntSkeletonEntity>(context) {

    override fun getTexture(entity: TntSkeletonEntity): Identifier = TEXTURE

    companion object {
        private val TEXTURE: Identifier = Identifier.of("mod_de_leo", "textures/entity/tnt_skeleton.png")
    }
}
