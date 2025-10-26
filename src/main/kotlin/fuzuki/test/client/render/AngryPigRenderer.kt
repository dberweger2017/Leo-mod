package fuzuki.test.client.render

import fuzuki.test.client.model.AngryPigModel
import fuzuki.test.common.MOD_ID
import fuzuki.test.common.entity.AngryPigEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.MobEntityRenderer
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class AngryPigRenderer(context: EntityRendererFactory.Context) :
    MobEntityRenderer<AngryPigEntity, AngryPigModel>(
        context,
        AngryPigModel(context.getPart(AngryPigModel.LAYER_LOCATION)),
        0.7f
    ) {

    override fun getTexture(entity: AngryPigEntity): Identifier =
        Identifier.of(MOD_ID, "textures/entity/angry_pig.png")
}
