package com.example.test1;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod(Test1.MOD_ID)
public class Test1 {
    public static final String MOD_ID = "test1";

    private static boolean playersEnabled = true;
    private static boolean f8WasDown;

    public Test1() {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerGuiLayers(AddGuiOverlayLayersEvent event) {
            event.getLayeredDraw().add(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "player_outlines"),
                    ClientEvents::drawPlayerRectangles
            );
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        private static List<ScreenRect> playerRectangles = List.of();

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) {
                return;
            }

            boolean f8Down = GLFW.glfwGetKey(
                    mc.getWindow().getWindow(), GLFW.GLFW_KEY_F8
            ) == GLFW.GLFW_PRESS;
            boolean pressedThisTick = f8Down && !f8WasDown;
            f8WasDown = f8Down;

            if (!pressedThisTick) {
                return;
            }

            if (mc.screen instanceof HitboxMenuScreen) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                mc.setScreen(new HitboxMenuScreen());
            }
        }

        @SubscribeEvent
        public static void collectPlayerRectangles(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !playersEnabled) {
                playerRectangles = List.of();
                return;
            }

            Matrix4f viewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
            Vec3 camera = event.getCamera().getPosition();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            List<ScreenRect> nextRectangles = new ArrayList<>();

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Player player)
                        || player == mc.player
                        || !player.isAlive()
                        || player.isSpectator()) {
                    continue;
                }

                ScreenRect rectangle = projectBox(
                        player.getBoundingBox(), camera, viewMatrix,
                        projectionMatrix, screenWidth, screenHeight
                );
                if (rectangle != null) {
                    nextRectangles.add(rectangle);
                }
            }

            playerRectangles = nextRectangles;
        }

        public static void drawPlayerRectangles(GuiGraphics graphics, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.screen != null || !playersEnabled) {
                return;
            }

            for (ScreenRect rectangle : playerRectangles) {
                drawRectangle(graphics, rectangle, 0xFF00FF00);
            }
        }

        private static ScreenRect projectBox(
                AABB box,
                Vec3 camera,
                Matrix4f viewMatrix,
                Matrix4f projectionMatrix,
                int screenWidth,
                int screenHeight
        ) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            int projectedCorners = 0;

            double[] xs = {box.minX, box.maxX};
            double[] ys = {box.minY, box.maxY};
            double[] zs = {box.minZ, box.maxZ};

            for (double x : xs) {
                for (double y : ys) {
                    for (double z : zs) {
                        Vector4f point = new Vector4f(
                                (float) (x - camera.x),
                                (float) (y - camera.y),
                                (float) (z - camera.z),
                                1.0F
                        );
                        viewMatrix.transform(point);
                        projectionMatrix.transform(point);

                        if (point.w <= 0.001F) {
                            continue;
                        }

                        double normalizedX = point.x / point.w;
                        double normalizedY = point.y / point.w;
                        double screenX = (normalizedX + 1.0) * 0.5 * screenWidth;
                        double screenY = (1.0 - normalizedY) * 0.5 * screenHeight;

                        minX = Math.min(minX, screenX);
                        minY = Math.min(minY, screenY);
                        maxX = Math.max(maxX, screenX);
                        maxY = Math.max(maxY, screenY);
                        projectedCorners++;
                    }
                }
            }

            if (projectedCorners == 0) {
                return null;
            }

            int left = Math.max(0, (int) Math.floor(minX));
            int top = Math.max(0, (int) Math.floor(minY));
            int right = Math.min(screenWidth, (int) Math.ceil(maxX));
            int bottom = Math.min(screenHeight, (int) Math.ceil(maxY));

            if (right - left < 2 || bottom - top < 2) {
                return null;
            }

            return new ScreenRect(left, top, right, bottom);
        }

        private static void drawRectangle(
                GuiGraphics graphics,
                ScreenRect rectangle,
                int color
        ) {
            int thickness = 2;
            graphics.fill(rectangle.left, rectangle.top,
                    rectangle.right, rectangle.top + thickness, color);
            graphics.fill(rectangle.left, rectangle.bottom - thickness,
                    rectangle.right, rectangle.bottom, color);
            graphics.fill(rectangle.left, rectangle.top,
                    rectangle.left + thickness, rectangle.bottom, color);
            graphics.fill(rectangle.right - thickness, rectangle.top,
                    rectangle.right, rectangle.bottom, color);
        }

        private record ScreenRect(int left, int top, int right, int bottom) {
        }
    }

    public static class HitboxMenuScreen extends Screen {
        protected HitboxMenuScreen() {
            super(Component.literal("Player Outline"));
        }

        @Override
        protected void init() {
            int x = width / 2 - 110;
            int y = height / 2 - 22;

            addRenderableWidget(
                    Button.builder(playerButtonText(), button -> {
                        playersEnabled = !playersEnabled;
                        button.setMessage(playerButtonText());
                    }).bounds(x, y, 220, 20).build()
            );

            addRenderableWidget(
                    Button.builder(Component.literal("Close"),
                                    button -> Minecraft.getInstance().setScreen(null))
                            .bounds(x, y + 28, 220, 20).build()
            );
        }

        private static Component playerButtonText() {
            return Component.literal(
                    "Player Outlines: " + (playersEnabled ? "ON" : "OFF")
            );
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
