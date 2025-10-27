package fuzuki.test.client.model

import fuzuki.test.common.MOD_ID
import fuzuki.test.common.entity.UndeadMinerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.model.Dilation
import net.minecraft.client.model.ModelData
import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelPartData
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.render.entity.model.EntityModelLayer
import net.minecraft.client.render.entity.model.EntityModelPartNames
import net.minecraft.client.render.entity.model.ZombieEntityModel
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class UndeadMinerModel(root: ModelPart) : ZombieEntityModel<UndeadMinerEntity>(root) {
    companion object {
        val LAYER = EntityModelLayer(Identifier.of(MOD_ID, "undead_miner"), "main")
        val INNER_ARMOR_LAYER = EntityModelLayer(Identifier.of(MOD_ID, "undead_miner"), "inner_armor")
        val OUTER_ARMOR_LAYER = EntityModelLayer(Identifier.of(MOD_ID, "undead_miner"), "outer_armor")

        fun getTexturedModelData(): TexturedModelData = createLayer(Dilation(0.0f))

        fun getInnerArmorTexturedModelData(): TexturedModelData = createLayer(Dilation(0.5f))

        fun getOuterArmorTexturedModelData(): TexturedModelData = createLayer(Dilation(1.0f))

        private fun createLayer(dilation: Dilation): TexturedModelData {
            val modelData = ZombieEntityModel.getModelData(dilation, 0.0f)
            val root: ModelPartData = modelData.root

            val body = root.getChild(EntityModelPartNames.BODY)
            body.addChild(
                "belt",
                ModelPartBuilder.create()
                    .uv(100, 7).cuboid(-6.0f, -4.0f, -1.0f, 2.0f, 5.0f, 4.0f, dilation)
                    .uv(100, 7).cuboid(4.0f, -4.0f, -1.0f, 2.0f, 5.0f, 4.0f, dilation),
                ModelTransform.pivot(0.0f, 12.0f, -1.0f)
            )

            val head = root.getChild(EntityModelPartNames.HEAD)
            head.addChild(
                "helmet",
                ModelPartBuilder.create()
                    .uv(64, 0).cuboid(-4.5f, -9.0f, -4.5f, 9.0f, 4.0f, 9.0f, dilation)
                    .uv(64, 17).cuboid(-5.0f, -9.5f, -5.0f, 10.0f, 5.0f, 10.0f, dilation)
                    .uv(102, 1).cuboid(-1.5f, -7.5f, -6.0f, 3.0f, 3.0f, 2.0f, dilation),
                ModelTransform.NONE
            )

            return TexturedModelData.of(modelData, 112, 64)
        }
    }
}
