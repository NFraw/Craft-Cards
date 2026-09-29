package com.jokernan.craftycards.smoke;

import com.jokernan.craftycards.client.ClientDDZData;
import com.jokernan.craftycards.client.ConfigHomeScreen;
import com.jokernan.craftycards.client.ConfigScreenBase;
import com.jokernan.craftycards.client.DDZGameHud;
import com.jokernan.craftycards.client.RenderConfigScreen;
import com.jokernan.craftycards.client.ServerPlayConfigScreen;
import com.jokernan.craftycards.client.SoundConfigScreen;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.network.payload.TableKey;
import com.jokernan.craftycards.platform.WorldRenderHook;


import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Three real Minecraft clients exercise normal interactions and C2S/S2C, with screenshots. */
public final class PocClient {
    private final ClientDDZData data = ClientDDZData.getInstance();
    private final TableKey key = new TableKey(Level.OVERWORLD, PocServer.TABLE);
    private int ticks, stage, waitUntil, worldFrames, biddingAt, inWorldAt, configPage, configAt;
    private com.jokernan.craftycards.network.payload.GameStatePayload countdownSnapshot;
    private int previousCountdown;
    private com.jokernan.craftycards.network.payload.GameStatePayload pendingTurn;
    private int actionAt;
    private int playScreenshotAt;
    private int selectedScreenshotAt;
    private int fullscreenScreenshotAt;
    private boolean selectedScreenshotSaved;
    private boolean fullscreenScreenshotSaved;
    private boolean connected, prepared, joined, playedShot, biddingShot, countdownMoved, creativeChecked, tableSlotSelected, tableConfigShot;
    private long started = System.currentTimeMillis();

    public void initialize(java.util.function.Consumer<java.util.function.Consumer<Minecraft>> registerTick) {
        System.out.println("Crafty Cards POC client callback registering");
        WorldRenderHook.register(ctx -> worldFrames++);
        registerTick.accept(mc -> {
            try { tick(mc); }
            catch (Throwable e) {
                e.printStackTrace();
                result(mc, "FAIL: " + e);
                mc.stop();
            }
        });
    }

    private void tick(Minecraft mc) throws Exception {
        ticks++;
        if (ticks == 1 || ticks == 40 || ticks == 100 || ticks % 1200 == 0)
            System.out.println("Crafty Cards POC client tick " + ticks + " stage " + stage + " prepared " + prepared
                + " connected " + connected + " screen " + (mc.screen == null ? "null" : mc.screen.getClass().getName())
                + " overlay " + (mc.getOverlay() == null ? "null" : mc.getOverlay().getClass().getName()));
        var timed = data.getSnapshot();
        if (timed != null && timed.turnRemainingTicks() > 0) {
            int remaining = data.turnRemainingTicks();
            if (timed == countdownSnapshot && remaining < previousCountdown) countdownMoved = true;
            countdownSnapshot = timed;
            previousCountdown = remaining;
        }
        if (System.currentTimeMillis() - started > 600_000) throw new AssertionError("Client timeout at stage " + stage);
        if (!connected) {
            if (!prepared) {
                if (mc.screen == null || mc.getOverlay() != null || ticks < 40) return;
                models(mc);
                check(mc.getResourcePackRepository().getSelectedIds().contains("crafty_cards/custom_sounds"), "audio pack selected");
                var dataPacks = new net.minecraft.server.packs.repository.PackRepository();
                dataPacks.reload();
                check(!dataPacks.getAvailableIds().contains("crafty_cards/custom_sounds"), "client mixin ignores non-client repositories");
                check(mc.getSoundManager().getSoundEvent(ResourceLocation.parse("crafty_cards:pass")) != null, "config pack sound event loaded");
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(3);
                mc.options.framerateLimit().set(30);
                mc.options.guiScale().set(2);
                mc.options.hideGui = false;
                prepared = true;
                mc.setScreen(new ConfigHomeScreen(null));
                configAt = ticks + 6;
                return;
            }
            if (configPage != 4) {
                if (ticks < configAt) return;
                captureConfigPage(mc);
                return;
            }
            mc.setScreen(new TitleScreen());
            connected = true;
            mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
                check(mc.getResourcePackRepository().getSelectedIds().contains("crafty_cards/custom_sounds"), "audio pack survives reload");
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("127.0.0.1:25576"),
                    new ServerData("POC", "127.0.0.1:25576", ServerData.Type.OTHER), false, null);
            }));
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null || ticks < waitUntil) return;
        if (inWorldAt == 0) inWorldAt = ticks;
        if (ticks - inWorldAt < 60) return;
        mc.getToasts().clear();
        int n = Integer.parseInt(mc.getUser().getName().substring(5));
        if (!creativeChecked) {
            net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
            var tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.parse("crafty_cards:crafty_cards"));
            check(tab != null && tab.shouldDisplay() && tab.getDisplayItems().size() == 3, "creative tab visible with 3 items");
            creativeChecked = true;
        }
        Vec3 delta = Vec3.atCenterOf(PocServer.TABLE).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x*delta.x + delta.z*delta.z))));
        var snap = data.getSnapshot();
        if (!mc.level.getBlockState(PocServer.TABLE).is(InitItems.DDZ_TABLE.get())) {
            if (n == 1 && !tableSlotSelected && select(mc, InitItems.DDZ_TABLE_ITEM.get())) {
                check(mc.player.getMainHandItem().is(InitItems.DDZ_TABLE_ITEM.get()),
                    "table slot switch immediate: hand=" + mc.player.getMainHandItem()
                        + " slot0=" + mc.player.getInventory().getItem(0)
                        + " slot1=" + mc.player.getInventory().getItem(1));
                tableSlotSelected = true; waitUntil = ticks + 5; return;
            }
            if (n == 1 && tableSlotSelected) {
                check(mc.player.getMainHandItem().is(InitItems.DDZ_TABLE_ITEM.get()),
                    "table item in selected hand: hand=" + mc.player.getMainHandItem()
                        + " slot0=" + mc.player.getInventory().getItem(0)
                        + " slot1=" + mc.player.getInventory().getItem(1));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(
                    new Vec3(0.5, 65, 0.5), Direction.UP, PocServer.TABLE.below(), false));
                waitUntil = ticks + 30;
            }
            return;
        }
        if (stage == 0 && n == 1) {
            select(mc, net.minecraft.world.item.Items.STICK);
            mc.options.keyShift.setDown(true);
            stage = 10; waitUntil = ticks + 3; return;
        }
        if (stage == 10 && n == 1) {
            check(mc.player.isShiftKeyDown(), "sneak input reached player before table click");
            click(mc); // Fabric use-block callback must override sneak with a held item.
            mc.options.keyShift.setDown(false);
            stage = 1; waitUntil = ticks + 20; return;
        }
        if (n == 1 && stage == 1) {
            if (snap == null || !snap.configOpen()) return;
            if (!tableConfigShot) {
                check(mc.screen != null && mc.screen.getClass().getSimpleName().equals("TableConfigScreen"), "table config screen opened");
                checkConfigPage(mc, "本桌筹码配置", "筹码类型", "入局底注（每人押注数）", "入局筹码数（门槛）", "本桌启用筹码");
                screenshot(mc, "04-config-table.png");
                tableConfigShot = true; waitUntil = ticks + 5; return;
            }
            data.send(new PlayerActionPayload(key, PlayerActionPayload.Action.SAVE_TABLE_CONFIG, 1, List.of(6), "minecraft:emerald", true));
            stage = 2; waitUntil = ticks + 15; return;
        }
        if (n == 1 && stage == 2) {
            check(snap != null && snap.betItem().equals("minecraft:emerald"), "configuration saved over network");
            data.send(new PlayerActionPayload(key, PlayerActionPayload.Action.CLOSE_TABLE_CONFIG, 0, List.of()));
            mc.setScreen(null);
            stage = 3; waitUntil = ticks + 15; return;
        }
        if (!joined) {
            if (snap != null && snap.myIndex() >= 0) { joined = true; return; }
            if (n != 1 && ticks % 40 != 0) return;
            mc.setScreen(null);
            click(mc);
            waitUntil = ticks + 15; return;
        }
        if (snap == null) throw new AssertionError("Participant snapshot disappeared: " + data.getMessage());
        select(mc, InitItems.DDZ_CARD.get());
        mc.setScreen(null);
        if (snap.phase() == DDZGamePhase.BIDDING) {
            check(snap.myHand().size() == 17, "17 private cards");
            check(snap.dipai().isEmpty(), "bottom cards hidden during bidding");
            check(snap.turnRemainingTicks() > 0, "server countdown");
            if (biddingAt == 0) { biddingAt = ticks; waitUntil = ticks + 100; return; }
            if (!biddingShot) {
                screenshot(mc, "01-bidding.png"); biddingShot = true;
                waitUntil = ticks + 30; return;
            }
            if (snap.currentPlayerIndex() == snap.myIndex()) {
                for (int attempt = 0; data.getBidChoice() != 3 && attempt < 4; attempt++) scroll(mc);
                check(data.getBidChoice() == 3, "scroll mixin selects bid");
                click(mc); waitUntil = ticks + 10;
            }
        } else if (snap.phase() == DDZGamePhase.PLAYING) {
            for (int seat = 0; seat < 3; seat++)
                if (seat != snap.myIndex()) check(snap.visibleHands().get(seat).isEmpty(), "opponent privacy");
            if (n == 2 && "true".equals(System.getProperty("craftycards.smoke.fullscreen")) && !fullscreenScreenshotSaved) {
                if (fullscreenScreenshotAt == 0) {
                    screenshot(mc, "02-hud-windowed.png");
                    mc.options.guiScale().set(0);
                    mc.getWindow().toggleFullScreen();
                    mc.resizeDisplay();
                    fullscreenScreenshotAt = ticks + 25;
                    return;
                }
                if (ticks < fullscreenScreenshotAt) return;
                screenshot(mc, "02-hud-fullscreen.png");
                mc.getWindow().toggleFullScreen();
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                fullscreenScreenshotSaved = true;
                return;
            }
            if (!playedShot && !snap.lastPlayedCards().isEmpty()) {
                if (playScreenshotAt == 0) playScreenshotAt = ticks + 3;
                if (ticks >= playScreenshotAt) { screenshot(mc, "02-playing.png"); playedShot = true; }
            }
            if (snap.currentPlayerIndex() != snap.myIndex()) return;
            // Leave each public play visible for rendered frames on all three clients.
            // Immediate farmer passes can otherwise clear it before the landlord's next tick.
            if (pendingTurn != snap) { pendingTurn = snap; actionAt = ticks + 12; return; }
            if (ticks < actionAt) return;
            // Landlord plays singles, farmers pass: reaches ordinary settlement deterministically.
            if (snap.myIndex() == snap.landlordIndex()) {
                if (!selectedScreenshotSaved && selectedScreenshotAt > 0) {
                    if (ticks < selectedScreenshotAt) return;
                    screenshot(mc, "02-selected.png");
                    selectedScreenshotSaved = true;
                } else {
                    data.clearSelection();
                    var press = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
                    press.setAccessible(true);
                    press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0, 1, 0);
                    check(!mc.options.keyAttack.isDown(), "mouse mixin prevents block attack");
                    check(data.getSelectedCards().size() == 1, "one card selected");
                    if (!selectedScreenshotSaved) { selectedScreenshotAt = ticks + 15; return; }
                }
            }
            click(mc); waitUntil = ticks + 10;
        } else if (snap.phase() == DDZGamePhase.SETTLED) {
            check(countdownMoved, "countdown advanced locally");
            check(worldFrames > 0, "translucent render mixin fired");
            check(playedShot, "observed real plays");
            check(snap.playerCardCounts().get(snap.landlordIndex()) == 0, "normal victory, not timeout");
            if (stage < 4) { stage = 4; waitUntil = ticks + 30; return; }
            if (stage == 4) {
                screenshot(mc, "03-settled.png");
                check(configPage == 4 && (n != 1 || tableConfigShot), "configuration pages captured");
                result(mc, "PASS: models, four configuration pages, audio pack reload, 3-player snapshots, HUD input/countdown, world render, normal settlement");
                stage = 5; waitUntil = ticks + 80; return;
            }
            mc.stop();
        }
    }

    private void captureConfigPage(Minecraft mc) {
        switch (configPage) {
            case 0 -> {
                checkConfigPage(mc, "Crafty Cards 设置", "音乐包", "玩法设置", "渲染参数");
                screenshot(mc, "04-config-home.png");
                mc.setScreen(new SoundConfigScreen(mc.screen));
            }
            case 1 -> {
                checkConfigPage(mc, "音乐包", "启用模组音频", "音量", "选用音乐包");
                screenshot(mc, "04-config-sound.png");
                mc.setScreen(new ServerPlayConfigScreen(mc.screen));
            }
            case 2 -> {
                checkConfigPage(mc, "玩法设置（房主）", "需要筹码", "入局底注（每人押注数）", "入局筹码数（门槛）", "每轮出牌限时", "观战者可见牌面");
                screenshot(mc, "04-config-play.png");
                ((ConfigScreenBase) mc.screen).scrollToLastRow();
                configPage = 20; configAt = ticks + 6; return;
            }
            case 20 -> {
                screenshot(mc, "04-config-play-bottom.png");
                mc.setScreen(new RenderConfigScreen(mc.screen));
                configPage = 3; configAt = ticks + 6; return;
            }
            case 3 -> {
                checkConfigPage(mc, "渲染参数", "手牌大小（像素宽）", "手牌间距", "距离（格）", "出牌缩放");
                screenshot(mc, "04-config-render.png");
                ((ConfigScreenBase) mc.screen).scrollToLastRow();
                configPage = 30; configAt = ticks + 6; return;
            }
            case 30 -> {
                screenshot(mc, "04-config-render-bottom.png");
                configPage = 4; configAt = ticks + 6; return;
            }
            default -> throw new AssertionError("Unexpected config page " + configPage);
        }
        configPage++;
        configAt = ticks + 6;
    }

    private static void checkConfigPage(Minecraft mc, String title, String... labels) {
        check(mc.screen instanceof ConfigScreenBase, "configuration page class: " + title);
        ConfigScreenBase page = (ConfigScreenBase) mc.screen;
        check(page.getTitle().getString().equals(title), "configuration title: " + title);
        for (String label : labels) {
            ConfigScreenBase.ConfigRow row = page.configRows().stream()
                .filter(candidate -> candidate.labelText().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing configuration label: " + title + " / " + label));
            check(!row.controlTexts().isEmpty() && row.controlTexts().stream().allMatch(value -> !value.isBlank()),
                "configuration value visible: " + title + " / " + label);
        }
    }

    private void click(Minecraft mc) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(
            Vec3.atCenterOf(PocServer.TABLE), Direction.UP, PocServer.TABLE, false));
    }
    private static boolean select(Minecraft mc, net.minecraft.world.item.Item item) {
        if (mc.player.getMainHandItem().is(item)) return true;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).is(item)) {
            mc.player.getInventory().selected = i;
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(i));
            return true;
        }
        return false;
    }
    private static void models(Minecraft mc) {
        Set<Object> models = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int id = 0; id < 54; id++) {
            ItemStack card = new ItemStack(InitItems.CARD.get()); card.setDamageValue(id);
            var property = ItemProperties.getProperty(card, ResourceLocation.parse("crafty_cards:value"));
            check(property != null && property.call(card, null, null, 0) == id, "model property preserves card " + id);
            models.add(mc.getItemRenderer().getModel(card, null, null, 0));
        }
        check(models.size() == 54, "54 distinct card models, got " + models.size());
        Set<Object> skins = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int id = 0; id < 4; id++) {
            ItemStack card = new ItemStack(InitItems.CARD_COVERED.get());
            var tag = new net.minecraft.nbt.CompoundTag(); tag.putByte("SkinID", (byte)id);
            com.jokernan.craftycards.util.ItemHelper.setNBT(card, tag);
            skins.add(mc.getItemRenderer().getModel(card, null, null, 0));
        }
        check(skins.size() == 4, "4 distinct card backs");
    }
    private static void scroll(Minecraft mc) throws Exception {
        var scroll = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
        scroll.setAccessible(true);
        scroll.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0.0, 1.0);
    }
    private static void screenshot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }
    private static void result(Minecraft mc, String text) {
        try { Files.writeString(mc.gameDirectory.toPath().resolve("poc-result.txt"), text); }
        catch (Exception e) { throw new RuntimeException(e); }
        System.out.println("POC " + text);
    }
    private static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
    }
}

