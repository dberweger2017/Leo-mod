package fuzuki.test.client.model

import fuzuki.test.common.MOD_ID
import fuzuki.test.common.entity.AngryPigEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.model.ModelData
import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelPartData
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.render.entity.model.EntityModelLayer
import net.minecraft.client.render.entity.model.EntityModelPartNames
import net.minecraft.client.render.entity.model.QuadrupedEntityModel
import net.minecraft.util.Identifier
import kotlin.math.PI

@Environment(EnvType.CLIENT)
class AngryPigModel(root: ModelPart) :
    QuadrupedEntityModel<AngryPigEntity>(root, false, 4.0f, 4.0f, 2.0f, 2.0f, 24) {

    companion object {
        val LAYER_LOCATION: EntityModelLayer = EntityModelLayer(Identifier.of(MOD_ID, "angry_pig"), "main")

        fun getTexturedModelData(): TexturedModelData {
            val modelData = ModelData()
            val root = modelData.root

            root.addChild(
                EntityModelPartNames.BODY,
                ModelPartBuilder.create()
                    .uv(28, 8).cuboid(-5.0f, -10.0f, -7.0f, 10.0f, 16.0f, 8.0f),
                ModelTransform.of(0.0f, 11.0f, 3.0f, PI.toFloat() / 2f, 0.0f, 0.0f)
            )

            val head = root.addChild(
                EntityModelPartNames.HEAD,
                ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-4.0f, -4.0f, -8.0f, 8.0f, 8.0f, 8.0f)
                    .uv(16, 16).cuboid(-2.0f, 0.0f, -9.0f, 4.0f, 3.0f, 1.0f),
                ModelTransform.pivot(0.0f, 12.0f, -6.0f)
            )

            head.addChild(
                "tusk",
                ModelPartBuilder.create()
                    .uv(-1, -2).cuboid(-0.5f, 0.0f, -3.0f, 1.0f, 1.0f, 4.0f)
                    .uv(2, 1).cuboid(-0.5f, -1.0f, -3.0f, 1.0f, 1.0f, 1.0f),
                ModelTransform.of(-1.5f, 2.0f, -9.0f, 0.2618f, 0.6109f, 0.0f)
            )

            head.addChild(
                "tusk2",
                ModelPartBuilder.create()
                    .uv(-1, -2).mirrored().cuboid(-0.5f, 0.0f, -3.0f, 1.0f, 1.0f, 4.0f)
                    .uv(2, 1).mirrored().cuboid(-0.5f, -1.0f, -3.0f, 1.0f, 1.0f, 1.0f),
                ModelTransform.of(1.5f, 2.0f, -9.0f, 0.2618f, -0.6109f, 0.0f)
            )

            val legBuilder = ModelPartBuilder.create()
                .uv(0, 16).cuboid(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f)

            root.addChild(EntityModelPartNames.RIGHT_HIND_LEG, legBuilder, ModelTransform.pivot(-3.0f, 18.0f, 7.0f))
            root.addChild(EntityModelPartNames.LEFT_HIND_LEG, legBuilder, ModelTransform.pivot(3.0f, 18.0f, 7.0f))
            root.addChild(EntityModelPartNames.RIGHT_FRONT_LEG, legBuilder, ModelTransform.pivot(-3.0f, 18.0f, -5.0f))
            root.addChild(EntityModelPartNames.LEFT_FRONT_LEG, legBuilder, ModelTransform.pivot(3.0f, 18.0f, -5.0f))

            return TexturedModelData.of(modelData, 64, 64)
        }
    }
}
