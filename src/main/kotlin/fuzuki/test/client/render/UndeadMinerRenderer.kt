package fuzuki.test.client.render

import fuzuki.test.client.model.UndeadMinerModel
import fuzuki.test.common.entity.UndeadMinerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.ZombieBaseEntityRenderer
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class UndeadMinerRenderer(context: EntityRendererFactory.Context) :
    ZombieBaseEntityRenderer<UndeadMinerEntity, UndeadMinerModel>(
        context,
        UndeadMinerModel(context.getPart(UndeadMinerModel.LAYER)),
        UndeadMinerModel(context.getPart(UndeadMinerModel.INNER_ARMOR_LAYER)),
        UndeadMinerModel(context.getPart(UndeadMinerModel.OUTER_ARMOR_LAYER))
    ) {

    override fun getTexture(entity: ZombieEntity): Identifier {
        val miner = entity as? UndeadMinerEntity ?: return super.getTexture(entity)
        return miner.variantTexture
    }
}
