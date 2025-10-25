package fuzuki.test.client.render

import net.minecraft.client.render.entity.CreeperEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.util.Identifier

class SuperCreeperRenderer(context: EntityRendererFactory.Context) :
    CreeperEntityRenderer(context) {

    override fun getTexture(entity: CreeperEntity): Identifier = TEXTURE

    companion object {
        private val TEXTURE: Identifier = Identifier.of("mod_de_leo", "textures/entity/super_creeper.png")
    }
}
