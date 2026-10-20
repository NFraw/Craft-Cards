package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import com.jokernan.craftycards.platform.Network;
import com.jokernan.craftycards.network.DDZNetworking;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZPublicTableConfigGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    private static class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final DDZTableManager m = DDZTableManager.getInstance();
        final TableKey key;
        final ServerPlayer first, second;
        Fixture(GameTestHelper h) {
            this.h = h; DDZTestSupport.forceBettingDefaults();
            h.setBlock(CORE, InitItems.DDZ_TABLE.get());
            key = new TableKey(h.getLevel().dimension(), h.absolutePos(CORE));
            first = DDZTestSupport.atTable(h, CORE, h.makeMockServerPlayerInLevel());
            second = DDZTestSupport.atTable(h, CORE, h.makeMockServerPlayerInLevel());
        }
        void open(ServerPlayer p) { m.handle(p, new PlayerActionPayload(key, PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of())); }
        void close(ServerPlayer p) { m.handle(p, new PlayerActionPayload(key, PlayerActionPayload.Action.CLOSE_TABLE_CONFIG, 0, List.of())); }
        void save(ServerPlayer p, int stake) { m.handle(p, new PlayerActionPayload(key, PlayerActionPayload.Action.SAVE_TABLE_CONFIG,
            stake, List.of(0), "minecraft:diamond", true)); }
        DDZTableBlockEntity table() { return (DDZTableBlockEntity) h.getLevel().getBlockEntity(key.pos()); }
        boolean editing(ServerPlayer p) { return m.session(key).snapshotFor(p).configOpen(); }
        public void close() { DDZTestSupport.cleanup(m, key, h, first, second); }
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }

    @GameTest(template = TEMPLATE)
    public static void saveReleasesEditorAndAnotherOnlinePlayerCanEdit(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.open(f.first); f.save(f.first, 3);
            require(!f.editing(f.first) && f.table().configLocker() == null, "Saving must finish editing and release lock");
            f.open(f.second); require(f.editing(f.second), "Another player must edit even while first remains online and nearby");
            f.save(f.second, 2); require(f.table().stake() == 2, "Second player must be able to save");
            f.open(f.first); require(f.editing(f.first), "Editor is temporary, not permanent ownership");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void closeAllowsNextEditorAndStaleSaveCannotOverwrite(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.open(f.first); f.open(f.second);
            require(f.editing(f.first) && !f.editing(f.second), "Only one concurrent editor is allowed");
            f.close(f.first); f.open(f.second);
            require(f.editing(f.second), "Closing must allow the next player to edit");
            f.save(f.first, 99); require(f.table().stake() == 1 && f.editing(f.second), "Stale editor cannot save or release new editor lock");
            f.close(f.first); require(f.editing(f.second), "Stale CLOSE cannot unlock somebody else's session");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void departedOrTimedOutEditorDoesNotBlockNextPlayer(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.open(f.first);
            f.first.teleportTo(f.key.pos().getX() + 100, f.key.pos().getY(), f.key.pos().getZ());
            f.open(f.second); require(f.editing(f.second), "Online editor far away must not retain editing rights");
            long now = h.getLevel().getServer().getTickCount();
            f.table().setConfigLocker(f.second.getUUID(), now - 2401);
            DDZTestSupport.atTable(h, CORE, f.first); f.open(f.first);
            require(f.editing(f.first), "Expired lock must be reclaimable without waiting for logout");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void legacyOwnerIsIgnoredButChipParametersSurvive(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var tag = f.table().saveWithFullMetadata(h.getLevel().registryAccess());
            tag.putUUID("Host", f.first.getUUID()); tag.putString("HostName", "old_owner");
            tag.putBoolean("Initialized", true); tag.putString("ChipItem", "minecraft:diamond");
            tag.putInt("Stake", 3); tag.putInt("EntryCount", 20); tag.putBoolean("ChipsEnabled", true);
            f.table().loadWithComponents(tag, h.getLevel().registryAccess());
            f.open(f.second);
            require(f.editing(f.second), "Legacy Host UUID must not prevent public editing");
            require(f.table().stake() == 3 && f.table().entryCount() == 20, "Migration must preserve chip parameters");
            var saved = f.table().saveWithFullMetadata(h.getLevel().registryAccess());
            require(!saved.contains("Host") && !saved.contains("HostName"), "Table must no longer persist player ownership");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void editingBlocksJoinAndOccupiedTableStillSupportsSpectating(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.open(f.first); f.save(f.first, 1);
            DDZTestSupport.stockChips(f.second, 8);
            f.open(f.first); f.m.join(f.second, f.key);
            require(!f.m.session(f.key).contains(f.second), "Joining must wait until rules are saved or editor closes");
            f.save(f.first, 1); f.m.join(f.second, f.key);
            require(f.m.session(f.key).contains(f.second), "Join must resume after save releases lock");
            f.open(f.first);
            require(!f.editing(f.first) && f.m.session(f.key).isWatching(f.first),
                "Shift-clicking an occupied table must preserve the spectator entry");
            f.save(f.first, 99); require(f.table().stake() == 1, "An occupied game cannot have its rules changed");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void logoutAndExpiredSaveReleaseEditingWithoutOwnership(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.open(f.first); f.m.handleLogout(f.first);
            f.open(f.second); require(f.editing(f.second), "Logout must release only temporary edit lock");
            long now = h.getLevel().getServer().getTickCount();
            f.table().setConfigLocker(f.second.getUUID(), now - 2401);
            f.save(f.second, 99);
            require(f.table().stake() == 1 && !f.editing(f.second), "Expired editor cannot extend its lock by saving");
            f.open(f.first); require(f.editing(f.first), "Another nearby player can continue editing");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void allNineTilesShareOnePublicEditingLock(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            // Extend north/west so the current fixture tile becomes the opposite member, not controller.
            for (int x = -2; x <= 0; x++) for (int z = -2; z <= 0; z++)
                h.setBlock(CORE.offset(x, 0, z), InitItems.DDZ_TABLE.get());
            f.open(f.first);
            var corner = new TableKey(f.key.dimension(), f.key.pos().offset(-2, 0, -2));
            f.m.handle(f.second, new PlayerActionPayload(corner, PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of()));
            require(f.editing(f.first) && !f.editing(f.second), "Opposite corners must share one lock");
            f.save(f.first, 2);
            f.m.handle(f.second, new PlayerActionPayload(corner, PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of()));
            require(f.editing(f.second), "After saving another player can edit from another tile");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void savedAndClosedEditorsReceiveClosureWhenTableIsDestroyed(GameTestHelper h) {
        var received = new java.util.HashMap<java.util.UUID, GameStatePayload>();
        Network.install(new Network.Bridge() {
            public void sendToServer(PlayerActionPayload payload) {}
            public void sendToPlayer(ServerPlayer player, GameStatePayload payload) { received.put(player.getUUID(), payload); }
        });
        try (var f = new Fixture(h)) {
            f.open(f.first); f.save(f.first, 1);
            f.open(f.second); f.close(f.second);
            require(f.table().configLocker() == null && f.m.session(f.key).playerCount() == 0,
                "Both editors must have left editing without joining or watching");
            h.getLevel().setBlockAndUpdate(f.key.pos(), Blocks.AIR.defaultBlockState());
            for (var p : new ServerPlayer[]{f.first, f.second}) require(received.containsKey(p.getUUID())
                && received.get(p.getUUID()).sessionClosed(), "Every former editor retaining a preview needs a closing snapshot");
            require(f.m.session(f.key) == null, "Destroyed table must not leave a session");
            h.succeed();
        } finally { Network.install(new DDZNetworking.PacketDistributorBridge()); }
    }

    @GameTest(template = TEMPLATE)
    public static void closingAnOldPreviewDoesNotSendPrivateHands(GameTestHelper h) {
        var received = new java.util.HashMap<java.util.UUID, GameStatePayload>();
        Network.install(new Network.Bridge() {
            public void sendToServer(PlayerActionPayload payload) {}
            public void sendToPlayer(ServerPlayer player, GameStatePayload payload) { received.put(player.getUUID(), payload); }
        });
        var players = new ServerPlayer[3];
        boolean oldVisibility = com.jokernan.craftycards.game.server.ServerGameConfig.spectatorsSeeCards;
        try (var f = new Fixture(h)) {
            com.jokernan.craftycards.game.server.ServerGameConfig.spectatorsSeeCards = true;
            f.open(f.first); f.save(f.first, 1);
            for (int i = 0; i < players.length; i++) {
                players[i] = DDZTestSupport.atTable(h, CORE, h.makeMockServerPlayerInLevel());
                DDZTestSupport.stockChips(players[i], 8); f.m.join(players[i], f.key);
            }
            var session = f.m.session(f.key);
            f.m.handle(players[session.currentSeat()], new PlayerActionPayload(f.key, PlayerActionPayload.Action.BID, 3, List.of()));
            require(!session.contains(f.first) && !session.isWatching(f.first), "Former editor is not a participant or spectator");
            h.getLevel().setBlockAndUpdate(f.key.pos(), Blocks.AIR.defaultBlockState());
            var closed = received.get(f.first.getUUID());
            require(closed != null && closed.sessionClosed(), "Old preview must be closed");
            require(closed.myHand().isEmpty() && closed.dipai().isEmpty()
                && closed.visibleHands().stream().allMatch(List::isEmpty)
                && closed.allHands().stream().allMatch(List::isEmpty), "Preview-only closure must never disclose hand faces");
            h.succeed();
        } finally {
            for (var player : players) if (player != null) h.getLevel().getServer().getPlayerList().remove(player);
            com.jokernan.craftycards.game.server.ServerGameConfig.spectatorsSeeCards = oldVisibility;
            Network.install(new DDZNetworking.PacketDistributorBridge());
        }
    }
}
