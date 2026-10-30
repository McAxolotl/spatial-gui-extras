package mcaxolotl.spatialguiextras.share;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.class_243;
import net.minecraft.class_286;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_293;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_742;
import net.minecraft.class_757;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;
import java.util.UUID;

final class ShareRenderer {

    static void render(class_4587 matrices) {
        var client = class_310.method_1551();
        if (client.field_1687 == null || client.field_1724 == null) return;
        List<RemoteGui> panels = ShareClient.visibleRemotes();
        if (panels.isEmpty()) return;

        class_293.class_5596 mode = class_293.class_5596.field_27382;

        var camera = client.field_1773.method_19418();
        class_243 cameraPos = camera.method_19326();

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float[] color = RenderSystem.getShaderColor().clone();
        var shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        matrices.method_22903();
        try {
            RenderSystem.setShader(() -> class_757.method_34542());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            // Match Spatial's overlay visibility, only for this panel draw; never clear world
            // depth.
            if (ShareConfig.INSTANCE.occludePanels) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            } else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            for (RemoteGui gui : panels) {
                RemoteGui.Pose pose = gui.pose();
                if (pose == null) continue;
                class_742 owner = findPlayer(client, gui.sender());
                if (owner == null) continue;
                class_243 ownerPos = owner.method_19538();
                float alpha = ShareClient.alphaFor(gui);
                if (alpha <= 0.01F && gui.overlay() == null) continue;
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                RenderSystem._setShaderTexture(0, gui.texture().glId());
                if (pose.curved()) {
                    drawCurved(matrices, mode, pose, ownerPos, cameraPos);
                } else {
                    drawFlat(matrices, mode, pose, ownerPos, cameraPos);
                }
                var overlay = gui.overlay();
                if (overlay != null) {
                    if (gui.overlayTexture().width() > 0)
                        OverflowOverlay.drawImage(
                                matrices,
                                overlay.scaledAround(
                                        pose.offsetX(),
                                        pose.offsetY(),
                                        pose.offsetZ(),
                                        ShareConfig.INSTANCE.panelScale),
                                ownerPos,
                                gui.overlayTexture().glId(),
                                alpha);
                    RenderSystem.setShader(() -> class_757.method_34542());
                }
            }
        } finally {
            matrices.method_22909();
            RenderSystem.setShader(() -> shader);
            RenderSystem._setShaderTexture(0, texture);
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            if (!blend) RenderSystem.disableBlend();
            else RenderSystem.enableBlend();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunction);
        }
    }

    private static void drawFlat(
            class_4587 matrices,
            class_293.class_5596 mode,
            RemoteGui.Pose pose,
            class_243 ownerPos,
            class_243 cameraPos) {
        Vector3f center =
                new Vector3f(
                        (float) (ownerPos.field_1352 + pose.offsetX() - cameraPos.field_1352),
                        (float) (ownerPos.field_1351 + pose.offsetY() - cameraPos.field_1351),
                        (float) (ownerPos.field_1350 + pose.offsetZ() - cameraPos.field_1350));
        Vector3f right =
                new Vector3f(pose.rightX(), pose.rightY(), pose.rightZ())
                        .mul(pose.halfWidth() * ShareConfig.INSTANCE.panelScale);
        Vector3f up =
                new Vector3f(pose.upX(), pose.upY(), pose.upZ())
                        .mul(pose.halfHeight() * ShareConfig.INSTANCE.panelScale);

        class_287 builder = class_289.method_1348().method_1349();
        builder.method_1328(mode, class_290.field_1585);
        Matrix4f matrix = matrices.method_23760().method_23761();
        vertex(builder, matrix, center, right, up, -1, -1, 0, 0);
        vertex(builder, matrix, center, right, up, 1, -1, 1, 0);
        vertex(builder, matrix, center, right, up, 1, 1, 1, 1);
        vertex(builder, matrix, center, right, up, -1, 1, 0, 1);
        class_286.method_43433(builder.method_1326());
    }

    private static void drawCurved(
            class_4587 matrices,
            class_293.class_5596 mode,
            RemoteGui.Pose pose,
            class_243 ownerPos,
            class_243 cameraPos) {
        int segments = 24;
        float arc = pose.arc() <= 0.01F ? 0.5F : pose.arc();
        Vector3f axis = new Vector3f(pose.axisDirX(), pose.axisDirY(), pose.axisDirZ());
        if (axis.lengthSquared() < 1.0E-6F) return;
        axis.normalize();
        Vector3f ref = new Vector3f(pose.refDirX(), pose.refDirY(), pose.refDirZ());
        if (ref.lengthSquared() < 1.0E-6F) return;
        ref.normalize();

        float scale = ShareConfig.INSTANCE.panelScale;
        Vector3f pivot =
                new Vector3f(
                        (float)
                                (ownerPos.field_1352
                                        + pose.offsetX()
                                        + (pose.axisOffsetX() - pose.offsetX()) * scale
                                        - cameraPos.field_1352),
                        (float)
                                (ownerPos.field_1351
                                        + pose.offsetY()
                                        + (pose.axisOffsetY() - pose.offsetY()) * scale
                                        - cameraPos.field_1351),
                        (float)
                                (ownerPos.field_1350
                                        + pose.offsetZ()
                                        + (pose.axisOffsetZ() - pose.offsetZ()) * scale
                                        - cameraPos.field_1350));
        float radius = pose.radius() <= 0.001F ? Math.max(0.1F, pose.halfWidth()) : pose.radius();
        float halfHeight =
                pose.halfHeightCylinder() <= 0.0001F
                        ? pose.halfHeight()
                        : pose.halfHeightCylinder();

        radius *= scale;
        halfHeight *= scale;
        class_287 builder = class_289.method_1348().method_1349();
        builder.method_1328(mode, class_290.field_1585);
        Matrix4f matrix = matrices.method_23760().method_23761();

        Vector3f previous = null;
        float previousU = curvedTextureU(0.0F);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = (t - 0.5F) * arc;
            Vector3f radial =
                    new Vector3f(ref)
                            .rotate(new Quaternionf().rotateAxis(angle, axis.x, axis.y, axis.z));
            Vector3f point = new Vector3f(pivot).add(radial.mul(radius));
            float u = curvedTextureU(t);
            if (previous != null) {
                Vector3f lowPrev = new Vector3f(previous).add(new Vector3f(axis).mul(-halfHeight));
                Vector3f highPrev = new Vector3f(previous).add(new Vector3f(axis).mul(halfHeight));
                Vector3f lowCur = new Vector3f(point).add(new Vector3f(axis).mul(-halfHeight));
                Vector3f highCur = new Vector3f(point).add(new Vector3f(axis).mul(halfHeight));
                vertex(builder, matrix, lowPrev, previousU, 0);
                vertex(builder, matrix, lowCur, u, 0);
                vertex(builder, matrix, highCur, u, 1);
                vertex(builder, matrix, highPrev, previousU, 1);
            }
            previous = point;
            previousU = u;
        }
        class_286.method_43433(builder.method_1326());
    }

    private static float curvedTextureU(float arcFraction) {
        return 1.0F - arcFraction;
    }

    private static void vertex(
            class_287 builder,
            Matrix4f matrix,
            Vector3f center,
            Vector3f right,
            Vector3f up,
            float sx,
            float sy,
            float u,
            float v) {
        float x = center.x + right.x * sx + up.x * sy;
        float y = center.y + right.y * sx + up.y * sy;
        float z = center.z + right.z * sx + up.z * sy;
        builder.method_22918(matrix, x, y, z).method_22913(u, v).method_1344();
    }

    private static void vertex(
            class_287 builder, Matrix4f matrix, Vector3f point, float u, float v) {
        builder.method_22918(matrix, point.x, point.y, point.z).method_22913(u, v).method_1344();
    }

    private static class_742 findPlayer(class_310 client, UUID uuid) {
        if (client.field_1687 == null) return null;
        for (class_742 player : client.field_1687.method_18456()) {
            if (player.method_5667().equals(uuid)) return player;
        }
        return null;
    }

    private ShareRenderer() {}
}
