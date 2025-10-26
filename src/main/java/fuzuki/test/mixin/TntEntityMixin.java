package fuzuki.test.mixin;

import fuzuki.test.common.entity.TntSkeletonEntity;
import fuzuki.test.common.entity.TntSkeletonEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntEntity.class)
public abstract class TntEntityMixin {
    @Shadow public abstract LivingEntity getOwner();

    @Shadow private boolean teleported;

    @Shadow @Final private static ExplosionBehavior TELEPORTED_EXPLOSION_BEHAVIOR;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void mod_de_leo$handleExplosion(CallbackInfo ci) {
        TntEntity self = (TntEntity)(Object)this;
        World world = self.getWorld();
        if (world.isClient) {
            return;
        }

        float power = 4.0F;
        LivingEntity owner = this.getOwner();
        if (owner instanceof TntSkeletonEntity) {
            power = 0.8f;
        }

        world.createExplosion(
            (Entity)self,
            Explosion.createDamageSource(world, (Entity)self),
            this.teleported ? TELEPORTED_EXPLOSION_BEHAVIOR : null,
            self.getX(),
            self.getBodyY(0.0625),
            self.getZ(),
            power,
            false,
            World.ExplosionSourceType.TNT
        );
        ci.cancel();
    }
}
