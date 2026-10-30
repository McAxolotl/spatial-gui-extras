package mcaxolotl.spatialguiextras.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.tastytrash.spatialGUI.util.RenderUtil;

@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public interface ShareInventoryAccess {
    @Accessor("quadBasis")
    RenderUtil.QuadBasis sge$getQuadBasis();

    @Accessor("cylinderBasis")
    RenderUtil.CylinderBasis sge$getCylinderBasis();

    @Invoker("getFadeAlpha")
    float sge$getFadeAlpha();
}
