package com.example.test1;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod(Test1.MOD_ID)
public class Test1 {
    public static final String MOD_ID = "test1";
    private static boolean playersEnabled = true;
    private static boolean vehiclesEnabled = true;
    private static KeyMapping menuKey;

    public Test1() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeys);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        menuKey = new KeyMapping(
            "key.test1.menu",
            GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.misc"
        );
        event.register(menuKey);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || menuKey == null) return;

            while (menuKey.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof HitboxMenuScreen) {
                    mc.setScreen(null);
                } else if (mc.screen == null) {
                    mc.setScreen(new HitboxMenuScreen());
                }
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            if (!playersEnabled && !vehiclesEnabled) return;

            PoseStack pose = event.getPoseStack();
            Vec3 camera = event.getCamera().getPosition();
            // Build the line vertices directly, then draw with depth testing disabled.
            // This avoids RenderType.lines() restoring the depth test during its own setup.
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            VertexConsumer lines = builder;

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!entity.isAlive() || entity == mc.player) continue;

                if (entity instanceof Player) {
                    if (!playersEnabled || entity.isSpectator()) continue;
                    drawBox(pose, lines, entity.getBoundingBox(), camera,
                            0.0F, 1.0F, 0.0F);
                } else if (vehiclesEnabled && isLikelyVehicle(entity)) {
                    // Generic heuristic: render non-living entities with a meaningful-sized box.
                    // Mod-specific vehicle detection may need adjustment for a particular mod.
                    drawBox(pose, lines, entity.getBoundingBox(), camera,
                            1.0F, 0.0F, 0.0F);
                }
            }

            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            RenderSystem.lineWidth(2.0F);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            try {
                BufferUploader.drawWithShader(builder.end());
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.lineWidth(1.0F);
            }
        }

        private static boolean isLikelyVehicle(Entity entity) {
            // Avoid tiny projectiles, dropped items, and ordinary living mobs.
            if (entity instanceof net.minecraft.world.entity.LivingEntity) return false;
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity) return false;
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile) return false;
            AABB box = entity.getBoundingBox();
            return box.getXsize() >= 1.0 || box.getYsize() >= 1.0 || box.getZsize() >= 1.0;
        }

        private static void drawBox(PoseStack pose, VertexConsumer lines, AABB worldBox,
                                    Vec3 camera, float r, float g, float b) {
            AABB relative = worldBox.move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(pose, lines, relative, r, g, b, 1.0F);
        }
    }

    public static class HitboxMenuScreen extends Screen {
        protected HitboxMenuScreen() {
            super(Component.literal("Test1"));
        }

        @Override
        protected void init() {
            int x = width / 2 - 110;
            int y = height / 2 - 35;

            addRenderableWidget(Button.builder(playerButtonText(), button -> {
                playersEnabled = !playersEnabled;
                button.setMessage(playerButtonText());
            }).bounds(x, y, 220, 20).build());

            addRenderableWidget(Button.builder(vehicleButtonText(), button -> {
                vehiclesEnabled = !vehiclesEnabled;
                button.setMessage(vehicleButtonText());
            }).bounds(x, y + 26, 220, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Close"), button ->
                Minecraft.getInstance().setScreen(null)
            ).bounds(x, y + 58, 220, 20).build());
        }

        private static Component playerButtonText() {
            return Component.literal("Player Hitboxes: " + (playersEnabled ? "ON" : "OFF"));
        }

        private static Component vehicleButtonText() {
            return Component.literal("Vehicle Hitboxes: " + (vehiclesEnabled ? "ON" : "OFF"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
