package mcaxolotl.spatialguiextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import mcaxolotl.spatialguiextras.share.*;

import net.minecraft.*;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.class_1007", priority = 900, remap = false)
public abstract class SharedAvatarApplyMixin {
    @WrapMethod(
            method =
                    "method_4215(Lnet/minecraft/class_742;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
            require = 1,
            remap = false)
    private void sge$remoteDisplayPose(
            class_742 player,
            float yaw,
            float delta,
            class_4587 matrices,
            class_4597 consumers,
            int light,
            Operation<Void> original) {
        AvatarPose pose = ShareClient.avatar(player.method_5667());
        try (AvatarDisplay.Scope scope =
                pose == null
                        ? null
                        : new AvatarDisplay.Scope(
                                player,
                                pose,
                                delta,
                                ShareClient.avatarWeight(player.method_5667()))) {
            original.call(player, yaw, delta, matrices, consumers, light);
        }
    }
}
