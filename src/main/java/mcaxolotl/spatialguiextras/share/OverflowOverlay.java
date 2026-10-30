package mcaxolotl.spatialguiextras.share;

import com.mojang.blaze3d.systems.RenderSystem;

import mcaxolotl.spatialguiextras.*;
import mcaxolotl.spatialguiextras.config.InteractionOptions;

import net.minecraft.*;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.util.MouseHandlerUtil;
import org.tastytrash.spatialGUI.util.RenderUtil;

public final class OverflowOverlay {
    private static class_6367 sprite;
    private static OverlayPose displayed;
    private static byte[] displayedPixels;
    private static final java.nio.ByteBuffer readback =
            org.lwjgl.BufferUtils.createByteBuffer(OverlayVisual.PIXEL_BYTES);

    public static OverlayVisual publishedVisual() {
        var pose = published();
        return pose == null || displayedPixels == null
                ? null
                : new OverlayVisual(pose, displayedPixels);
    }

    public static void beginFrame() {
        displayed = null;
        displayedPixels = null;
    }

    public static OverlayPose published() {
        var client = class_310.method_1551();
        return client.field_1724 == null ? null : displayed;
    }

    public static void close() {
        if (sprite != null) {
            sprite.method_1238();
            sprite = null;
        }
    }

    private static boolean outsidePanel() {
        var c = class_310.method_1551();
        var r = SpatialGUIClient.renderer();
        if (!ExtrasClient.enabled()
                || !SpatialGUIClient.isEnabled()
                || r == null
                || r.isClosing()
                || !r.shouldCapture()
                || c.field_1724 == null
                || r.getHookedScreen() != c.field_1755) return false;
        var inv = r.getInventoryRenderer();
        var quad = inv.getQuadBasis();
        if (quad == null) return false;
        var cylinder = inv.getCylinderBasis();
        double x = MouseHandlerUtil.getSourceX(), y = MouseHandlerUtil.getSourceY();
        return !(cylinder == null
                ? RenderUtil.isMouseOverQuad(x, y, quad)
                : RenderUtil.isMouseOverCylinder(x, y, cylinder));
    }

    public static boolean active() {
        var c = class_310.method_1551();
        return InteractionOptions.overflowItems
                && outsidePanel()
                && SpatialGUIClient.getEffectiveFirstPersonMode()
                && c.field_1755 instanceof class_465
                && !c.field_1724.field_7512.method_34255().method_7960();
    }

    public static OverlayPose snapshot() {
        if (!outsidePanel()) return null;
        var c = class_310.method_1551();
        var camera = c.field_1773.method_19418();
        var player = c.field_1724;
        var ray =
                mcaxolotl.spatialguiextras.mixin.ScreenProjectionAccess.sge$ray(
                        MouseHandlerUtil.getSourceX(), MouseHandlerUtil.getSourceY());
        if (ray == null) return null;
        var direction = new Vector3f(ray[0], ray[1], ray[2]);
        var origin = camera.method_19326().method_1020(player.method_19538());
        var inv = SpatialGUIClient.renderer().getInventoryRenderer();
        float distance = 2f;
        float itemSize =
                Math.max(
                        .025f,
                        Math.min(
                                .5f,
                                inv.getQuadBasis().halfHeight()
                                        * 32f
                                        / c.method_22683().method_4502()));
        return new OverlayPose(
                (float) origin.field_1352 + direction.x * distance,
                (float) origin.field_1351 + direction.y * distance,
                (float) origin.field_1350 + direction.z * distance,
                AvatarPose.wrap(camera.method_19330()),
                camera.method_19329(),
                itemSize);
    }

    public static void renderLocal(class_4587 matrices) {
        if (!IntegrationHooks.worldView()) return;
        boolean item = active(), indicator = outsidePanel() && SharedCursor.overflowVisible();
        if (!item && !indicator) return;
        OverlayPose pose = snapshot();
        if (pose == null) return;
        var c = class_310.method_1551();
        if (item)
            draw(
                    matrices,
                    pose,
                    c.field_1724.method_19538(),
                    c.field_1724.field_7512.method_34255());
        if (ShareConfig.INSTANCE.enabled && ShareClient.recipientsNearby()) {
            displayed = pose;
            displayedPixels = new byte[OverlayVisual.PIXEL_BYTES];
            if (item) {
                int previous = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
                try (PixelTransferState state = new PixelTransferState(true)) {
                    org.lwjgl.opengl.GL30.glBindFramebuffer(
                            org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, sprite.field_1476);
                    readback.clear();
                    GL11.glReadPixels(0, 0, 64, 64, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readback);
                    readback.position(0);
                    readback.limit(OverlayVisual.PIXEL_BYTES);
                    readback.get(displayedPixels);
                } finally {
                    org.lwjgl.opengl.GL30.glBindFramebuffer(
                            org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, previous);
                }
            }
            if (indicator) SharedCursor.paintOverflow(displayedPixels);
        }
    }

    public static void draw(
            class_4587 matrices, OverlayPose pose, class_243 owner, class_1799 stack) {
        if (stack.method_7960()) return;
        var c = class_310.method_1551();
        var camera = c.field_1773.method_19418();
        var pos = camera.method_19326();
        var lights = mcaxolotl.spatialguiextras.mixin.ItemLightingAccess.sge$lights();
        Vector3f light0 = lights[0] == null ? null : new Vector3f(lights[0]),
                light1 = lights[1] == null ? null : new Vector3f(lights[1]);
        Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
        int texture1 = RenderSystem.getShaderTexture(1),
                texture2 = RenderSystem.getShaderTexture(2);
        var shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND),
                cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int srcRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),
                dstRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),
                dstAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        java.nio.ByteBuffer colorMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        float[] clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        int draw = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                read = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.method_22903();
        try {
            if (sprite == null) sprite = new class_6367(64, 64, true, false);
            com.mojang.blaze3d.platform.GlStateManager._disableScissorTest();
            RenderSystem.colorMask(true, true, true, true);
            sprite.method_1236(0, 0, 0, 0);
            sprite.method_1230(false);
            sprite.method_1235(true);
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0, 32, 32, 0, 1000, 21000), class_8251.field_43361);
            modelView.method_34426();
            modelView.method_22904(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            class_308.method_24211();
            var graphics = new class_332(c, c.method_22940().method_23000());
            graphics.method_51427(stack, 8, 8);
            graphics.method_51431(c.field_1772, stack, 8, 8);
            graphics.method_51452();
            org.lwjgl.opengl.GL30.glBindFramebuffer(
                    org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, draw);
            org.lwjgl.opengl.GL30.glBindFramebuffer(
                    org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, read);
            com.mojang.blaze3d.platform.GlStateManager._viewport(
                    viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.method_22909();
            modelView.method_22903();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> class_757.method_34542());
            RenderSystem._setShaderTexture(0, sprite.method_30277());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            drawQuad(matrices, pose, owner);
        } finally {
            modelView.method_22909();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            org.lwjgl.opengl.GL30.glBindFramebuffer(
                    org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, draw);
            org.lwjgl.opengl.GL30.glBindFramebuffer(
                    org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, read);
            com.mojang.blaze3d.platform.GlStateManager._viewport(
                    viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.colorMask(
                    colorMask.get(0) != 0,
                    colorMask.get(1) != 0,
                    colorMask.get(2) != 0,
                    colorMask.get(3) != 0);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            if (light0 != null && light1 != null) RenderSystem.setShaderLights(light0, light1);
            RenderSystem.setTextureMatrix(textureMatrix);
            RenderSystem._setShaderTexture(1, texture1);
            RenderSystem._setShaderTexture(2, texture2);
            RenderSystem.setShader(() -> shader);
            RenderSystem._setShaderTexture(0, texture);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            com.mojang.blaze3d.platform.GlStateManager._blendFuncSeparate(
                    srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
            else com.mojang.blaze3d.platform.GlStateManager._disableScissorTest();
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    public static void drawImage(
            class_4587 matrices, OverlayPose pose, class_243 owner, int texture, float opacity) {
        var shader = RenderSystem.getShader();
        int oldTexture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            RenderSystem.setShader(() -> class_757.method_34542());
            RenderSystem._setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1, 1, 1, opacity);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            drawQuad(matrices, pose, owner);
        } finally {
            RenderSystem.setShader(() -> shader);
            RenderSystem._setShaderTexture(0, oldTexture);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
        }
    }

    private static void drawQuad(class_4587 matrices, OverlayPose pose, class_243 owner) {
        var pos = class_310.method_1551().field_1773.method_19418().method_19326();
        var rotation =
                new Quaternionf()
                        .rotationYXZ(
                                (float) Math.PI - (float) Math.toRadians(pose.yaw()),
                                -(float) Math.toRadians(pose.pitch()),
                                0);
        var right = new Vector3f(1, 0, 0).rotate(rotation).mul(pose.size());
        var up = new Vector3f(0, 1, 0).rotate(rotation).mul(pose.size());
        var center =
                new Vector3f(
                        (float) (owner.field_1352 + pose.x() - pos.field_1352),
                        (float) (owner.field_1351 + pose.y() - pos.field_1351),
                        (float) (owner.field_1350 + pose.z() - pos.field_1350));
        var buffer = class_289.method_1348().method_1349();
        buffer.method_1328(class_293.class_5596.field_27382, class_290.field_1585);
        var matrix = matrices.method_23760().method_23761();
        for (int i = 0; i < 4; i++) {
            float x = i == 0 || i == 3 ? -1 : 1, y = i < 2 ? -1 : 1;
            var point = new Vector3f(center).fma(x, right).fma(y, up);
            buffer.method_22918(matrix, point.x, point.y, point.z)
                    .method_22913(x < 0 ? 0 : 1, y < 0 ? 0 : 1)
                    .method_1344();
        }
        class_286.method_43433(buffer.method_1326());
    }

    private OverflowOverlay() {}
}
