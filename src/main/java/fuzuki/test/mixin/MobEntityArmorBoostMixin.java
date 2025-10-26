package fuzuki.test.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.provider.EnchantmentProviders;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityArmorBoostMixin {

    private static final int EXTRA_ARMOR_ROLLS = 4;
    private static final float ENCHANT_CHANCE = 0.75f;
    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    @Inject(method = "initEquipment", at = @At("TAIL"))
    private void mod_de_leo$boostArmor(Random random, LocalDifficulty difficulty, CallbackInfo ci) {
        MobEntity self = (MobEntity)(Object)this;
        if (!(self instanceof HostileEntity)) {
            return;
        }

        for (int i = 0; i < EXTRA_ARMOR_ROLLS; i++) {
            applyAdditionalArmorRoll(self, random, difficulty);
        }

        if (!(self.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = self.getEquippedStack(slot);
            if (stack.isEmpty() || random.nextFloat() >= ENCHANT_CHANCE) {
                continue;
            }

            EnchantmentHelper.applyEnchantmentProvider(
                stack,
                serverWorld.getRegistryManager(),
                EnchantmentProviders.MOB_SPAWN_EQUIPMENT,
                difficulty,
                random
            );
            self.equipStack(slot, stack);
        }
    }

    private static void applyAdditionalArmorRoll(MobEntity self, Random random, LocalDifficulty difficulty) {
        if (random.nextFloat() >= 0.15F * difficulty.getClampedLocalDifficulty()) {
            return;
        }

        int tier = random.nextInt(2);
        if (random.nextFloat() < 0.095F) tier++;
        if (random.nextFloat() < 0.095F) tier++;
        if (random.nextFloat() < 0.095F) tier++;

        final float stopChance = self.getWorld().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
        boolean guaranteed = true;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }

            if (!guaranteed && random.nextFloat() < stopChance) {
                break;
            }

            guaranteed = false;
            ItemStack equipped = self.getEquippedStack(slot);
            if (!equipped.isEmpty()) {
                continue;
            }

            Item item = MobEntity.getEquipmentForSlot(slot, tier);
            if (item != null) {
                self.equipStack(slot, new ItemStack(item));
            }
        }
    }
}
