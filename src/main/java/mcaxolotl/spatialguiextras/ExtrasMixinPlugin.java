package mcaxolotl.spatialguiextras;

import net.fabricmc.loader.api.FabricLoader;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class ExtrasMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String target, String mixin) {
        return FabricLoader.getInstance().isModLoaded("spatial_gui");
    }

    public void acceptTargets(Set<String> mine, Set<String> others) {}

    public List<String> getMixins() {
        return null;
    }

    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith(".SharedAvatarCaptureMixin")) return;
        boolean found = false;
        for (var method : node.methods)
            if (method.name.contains("computePose"))
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call
                            && call.name.contains("sge$computedAvatar")) found = true;
        System.out.println("[Spatial GUI Extras] Avatar capture hook verified=" + found);
    }
}
