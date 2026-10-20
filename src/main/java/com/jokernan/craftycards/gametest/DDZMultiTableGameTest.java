package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.block.BlockDDZTable;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.*;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.DDZNetworking;
import com.jokernan.craftycards.network.payload.*;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZMultiTableGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(2, 1, 2);

    private static TableKey key(GameTestHelper h, int x, int z) {
        return new TableKey(h.getLevel().dimension(), h.absolutePos(CORE.offset(x, 0, z)));
    }

    private static void rectangle(GameTestHelper h, int width, int depth) {
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++)
            h.setBlock(CORE.offset(x, 0, z), InitItems.DDZ_TABLE.get());
    }

    private static ServerPlayer[] players(GameTestHelper h, int count) {
        ServerPlayer[] result = new ServerPlayer[count];
        for (int i = 0; i < count; i++)
            result[i] = DDZTestSupport.atTable(h, CORE, h.makeMockServerPlayerInLevel());
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void action(DDZTableManager m, ServerPlayer p, TableKey key,
                               PlayerActionPayload.Action action, int score, List<Integer> cards) {
        m.handle(p, new PlayerActionPayload(key, action, score, cards));
    }

    @GameTest(template = TEMPLATE)
    public static void sixTilesShareOneRoundAndOneBet(GameTestHelper h) {
        rectangle(h, 2, 3);
        var m = DDZTableManager.getInstance();
        var root = key(h, 0, 0);
        var ps = players(h, 3);
        DDZTestSupport.prepareBetting(m, root, ps);
        int initial = DDZTestSupport.countOf(ps[0], DDZTestSupport.CHIP);
        try {
            m.join(ps[0], key(h, 1, 2));
            var s = m.session(root);
            require(s != null, "A member tile must create the canonical session");
            for (int x = 0; x < 2; x++) for (int z = 0; z < 3; z++) {
                m.join(ps[0], key(h, x, z));
                require(m.session(key(h, x, z)) == s, "All six tiles must resolve to one session");
            }
            require(s.playerCount() == 1, "Repeated member JOIN must not create extra seats");
            require(DDZTestSupport.countOf(ps[0], DDZTestSupport.CHIP) == initial - 1,
                "Repeated member JOIN must collect only one stake");
            m.join(ps[1], key(h, 0, 1)); m.join(ps[2], key(h, 1, 0));
            require(s.phase() == DDZGamePhase.BIDDING, "Three players across tiles must start one game");
            var snap = s.snapshotFor(ps[0]);
            require(snap.table().equals(root) && snap.tableWidth() == 2 && snap.tableDepth() == 3,
                "Snapshot must contain canonical identity and whole-table dimensions");
            require(snap.tableCenterX() == root.pos().getX() + 1
                && snap.tableCenterZ() == root.pos().getZ() + 1.5, "Cards/chips must use whole-table centre");
            action(m, ps[s.currentSeat()], key(h, 1, 2), PlayerActionPayload.Action.BID, 3, List.of());
            require(s.phase() == DDZGamePhase.PLAYING, "Bid through a different tile must work");
            int steps = 0;
            while (s.phase() == DDZGamePhase.PLAYING && ++steps < 400) {
                var who = ps[s.currentSeat()]; var view = s.snapshotFor(who);
                boolean leading = view.lastPlayedBy() < 0 || view.lastPlayedBy() == view.myIndex();
                int above = leading ? -1 : view.lastPlayedType() == DDZCardType.SINGLE
                    ? DDZEngine.getRank(view.lastPlayedCards().get(0)) : Integer.MAX_VALUE;
                int move = -1, rank = Integer.MAX_VALUE;
                for (int card : view.myHand()) if (DDZEngine.getRank(card) > above && DDZEngine.getRank(card) < rank) {
                    move = card; rank = DDZEngine.getRank(card);
                }
                action(m, who, key(h, steps % 2, steps % 3), move < 0
                    ? PlayerActionPayload.Action.PASS : PlayerActionPayload.Action.PLAY,
                    0, move < 0 ? List.of() : List.of(move));
            }
            require(s.phase() == DDZGamePhase.SETTLED, "A full game across all six tiles must settle");
            int total = Arrays.stream(ps).mapToInt(p -> DDZTestSupport.countOf(p, DDZTestSupport.CHIP)).sum();
            require(total == initial * 3, "Merged-table settlement must conserve chips");
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); }
    }

    @GameTest(template = TEMPLATE)
    public static void removingAnyMemberRefundsAndNotifiesEveryone(GameTestHelper h) {
        rectangle(h, 2, 3);
        var m = DDZTableManager.getInstance(); var root = key(h, 0, 0); var ps = players(h, 4);
        Map<UUID, GameStatePayload> received = new HashMap<>();
        Network.install(new Network.Bridge() {
            public void sendToServer(PlayerActionPayload payload) {}
            public void sendToPlayer(ServerPlayer player, GameStatePayload payload) { received.put(player.getUUID(), payload); }
        });
        try {
            DDZTestSupport.prepareBetting(m, root, ps[0], ps[1], ps[2]);
            int initial = DDZTestSupport.countOf(ps[0], DDZTestSupport.CHIP);
            for (int i = 0; i < 3; i++) m.join(ps[i], key(h, i % 2, i));
            var s = m.session(root);
            action(m, ps[3], key(h, 1, 2), PlayerActionPayload.Action.WATCH, 0, List.of());
            require(s.isWatching(ps[3]), "Member WATCH must target the same table");
            // Actual block removal callback (also used by /setblock and explosions), not a synthetic break event.
            h.getLevel().setBlockAndUpdate(key(h, 1, 2).pos(), Blocks.AIR.defaultBlockState());
            require(m.session(root) == null, "Removing a non-controller member must disband the game");
            for (int i = 0; i < 3; i++) require(DDZTestSupport.countOf(ps[i], DDZTestSupport.CHIP) == initial,
                "Each player's original stake must be refunded");
            for (var p : ps) require(received.get(p.getUUID()) != null
                && received.get(p.getUUID()).sessionClosed(), "All participants and watcher must receive closure");
            h.succeed();
        } finally {
            DDZTestSupport.cleanup(m, root, h, ps);
            Network.install(new DDZNetworking.PacketDistributorBridge());
        }
    }

    @GameTest(template = TEMPLATE)
    public static void expansionRefundsAndControllerMoveKeepsConfiguration(GameTestHelper h) {
        rectangle(h, 1, 1); var m = DDZTableManager.getInstance();
        var root = key(h, 0, 0); var ps = players(h, 1);
        DDZTestSupport.prepareBetting(m, root, ps);
        try {
            int initial = DDZTestSupport.countOf(ps[0], DDZTestSupport.CHIP);
            m.join(ps[0], root); var old = m.session(root);
            h.setBlock(CORE.west(), InitItems.DDZ_TABLE.get());
            require(m.session(root) == null && old.snapshotFor(ps[0]).sessionClosed(), "Expansion must close the old game");
            require(DDZTestSupport.countOf(ps[0], DDZTestSupport.CHIP) == initial, "Expansion must refund the old stake");
            m.join(ps[0], root); var joined = m.session(root);
            require(joined.getKey().equals(key(h, -1, 0)), "New northwest corner must become controller");
            var be = (DDZTableBlockEntity) h.getLevel().getBlockEntity(key(h, -1, 0).pos());
            require(be.chipItem().equals("minecraft:diamond"), "Controller move must preserve chip configuration");
            h.getLevel().setBlockAndUpdate(key(h, -1, 0).pos(), Blocks.AIR.defaultBlockState());
            require(m.session(root) == null, "Removing controller must close the entire game");
            m.join(ps[0], root);
            require(m.session(root).chipItemName().equals("minecraft:diamond"), "Surviving table must retain configuration");
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); }
    }

    @GameTest(template = TEMPLATE)
    public static void configAndSnapshotRoundTripFromOppositeCorner(GameTestHelper h) throws Exception {
        rectangle(h, 3, 2); var m = DDZTableManager.getInstance();
        var root = key(h, 0, 0); var opposite = key(h, 2, 1); var ps = players(h, 1);
        try {
            DDZTestSupport.forceBettingDefaults();
            action(m, ps[0], opposite, PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of());
            m.handle(ps[0], new PlayerActionPayload(opposite, PlayerActionPayload.Action.SAVE_TABLE_CONFIG,
                2, List.of(12), "minecraft:diamond", true));
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                var tile = (DDZTableBlockEntity) h.getLevel().getBlockEntity(key(h, x, z).pos());
                require(tile.stake() == 2 && tile.entryCount() == 12,
                    "Configuration must be shared and persisted on all member tiles");
                var restored = new DDZTableBlockEntity(tile.getBlockPos(), tile.getBlockState());
                restored.loadWithComponents(tile.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
                require(restored.stake() == 2 && restored.entryCount() == 12, "NBT reload must retain shared configuration");
            }
            var snap = m.session(root).snapshotFor(ps[0]);
            var encode = GameStatePayload.class.getDeclaredMethod("toTag"); encode.setAccessible(true);
            var decode = GameStatePayload.class.getDeclaredMethod("fromTag", CompoundTag.class); decode.setAccessible(true);
            var tag = (CompoundTag) encode.invoke(snap); var decoded = (GameStatePayload) decode.invoke(null, tag);
            require(decoded.equals(snap) && decoded.contains(opposite), "Snapshot geometry must survive serialization");
            tag.remove("tableWidth"); tag.remove("tableDepth");
            var legacy = (GameStatePayload) decode.invoke(null, tag);
            require(legacy.tableWidth() == 1 && legacy.tableDepth() == 1, "Old snapshots default to one block");
            action(m, ps[0], opposite, PlayerActionPayload.Action.CLOSE_TABLE_CONFIG, 0, List.of());
            require(!m.session(root).snapshotFor(ps[0]).configOpen(), "Closing config from another tile must release one lock");
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); }
    }

    @GameTest(template = TEMPLATE)
    public static void incompleteShapeAndForgedCoordinatesCannotCreateGames(GameTestHelper h) {
        rectangle(h, 2, 1); h.setBlock(CORE.south(), InitItems.DDZ_TABLE.get());
        var m = DDZTableManager.getInstance(); var ps = players(h, 1); var root = key(h, 0, 0);
        try {
            m.join(ps[0], root); require(m.session(root) == null, "L shaped table must not create independent games");
            m.join(ps[0], key(h, 4, 4)); require(m.session(key(h, 4, 4)) == null, "Air coordinates must not create games");
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); }
    }

    @GameTest(template = TEMPLATE)
    public static void horizontalConnectionsFollowPlacementAndRemoval(GameTestHelper h) {
        rectangle(h, 2, 3);
        var nw = h.getBlockState(CORE); var southeast = h.getBlockState(CORE.offset(1, 0, 2));
        require(!nw.getValue(BlockDDZTable.NORTH) && !nw.getValue(BlockDDZTable.WEST)
            && nw.getValue(BlockDDZTable.EAST) && nw.getValue(BlockDDZTable.SOUTH), "Northwest outer rim must remain visible");
        require(!southeast.getValue(BlockDDZTable.EAST) && !southeast.getValue(BlockDDZTable.SOUTH), "Southeast outer rim must remain visible");
        h.getLevel().setBlockAndUpdate(key(h, 1, 0).pos(), Blocks.AIR.defaultBlockState());
        require(!h.getBlockState(CORE).getValue(BlockDDZTable.EAST), "Removing neighbour must restore rim");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void oldSavedConnectionStateIsRepairedOnLoad(GameTestHelper h) {
        rectangle(h, 2, 3);
        BlockPos pos = key(h, 0, 0).pos();
        h.getLevel().setBlock(pos, InitItems.DDZ_TABLE.get().defaultBlockState(), 2);
        h.getLevel().getBlockEntity(pos).onLoad();
        h.runAfterDelay(3, () -> {
            var state = h.getLevel().getBlockState(pos);
            require(state.getValue(BlockDDZTable.EAST) && state.getValue(BlockDDZTable.SOUTH),
                "Loading an old table must refresh its connection properties");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breakingTableNotifiesConfigOnlyEditor(GameTestHelper h) {
        rectangle(h, 2, 3); var m = DDZTableManager.getInstance(); var root = key(h, 0, 0);
        var ps = players(h, 1); List<GameStatePayload> received = new ArrayList<>();
        Network.install(new Network.Bridge() {
            public void sendToServer(PlayerActionPayload payload) {}
            public void sendToPlayer(ServerPlayer player, GameStatePayload payload) {
                if (player == ps[0]) received.add(payload);
            }
        });
        try {
            action(m, ps[0], key(h, 1, 2), PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of());
            require(m.session(root).snapshotFor(ps[0]).configOpen(), "Editor must have open config without a seat");
            h.getLevel().setBlockAndUpdate(root.pos(), Blocks.AIR.defaultBlockState());
            require(!received.isEmpty() && received.getLast().sessionClosed() && !received.getLast().configOpen(),
                "Config-only editor must receive closure when controller is broken");
            require(m.session(root) == null, "Config-only session must be removed");
            h.succeed();
        } finally {
            DDZTestSupport.cleanup(m, root, h, ps);
            Network.install(new DDZNetworking.PacketDistributorBridge());
        }
    }

    @GameTest(template = TEMPLATE)
    public static void mergingTwoOccupiedTablesRefundsBothPools(GameTestHelper h) {
        h.setBlock(CORE, InitItems.DDZ_TABLE.get());
        h.setBlock(CORE.east(2), InitItems.DDZ_TABLE.get());
        var m = DDZTableManager.getInstance(); var root = key(h, 0, 0); var east = key(h, 2, 0);
        var ps = players(h, 2);
        try {
            DDZTestSupport.prepareBetting(m, root, ps[0]);
            DDZTestSupport.prepareBetting(m, east, ps[1]);
            var eastConfig = (DDZTableBlockEntity) h.getLevel().getBlockEntity(east.pos());
            eastConfig.setStake(2); eastConfig.setEntryCount(6);
            int initial = DDZTestSupport.countOf(ps[0], DDZTestSupport.CHIP);
            m.join(ps[0], root); m.join(ps[1], east);
            var westSession = m.session(root); var eastSession = m.session(east);
            require(westSession != eastSession, "Separated tables must start as two games");
            h.setBlock(CORE.east(), InitItems.DDZ_TABLE.get());
            require(m.session(root) == null && m.session(east) == null, "Joining bridge must close both old sessions");
            for (var p : ps) require(DDZTestSupport.countOf(p, DDZTestSupport.CHIP) == initial,
                "Both original pools must be refunded before merge");
            m.join(ps[0], east); m.join(ps[1], root);
            require(m.session(root) == m.session(east) && m.session(root).playerCount() == 2,
                "Merged structure must have exactly one new session");
            require(m.session(root).stake() == 1, "Northwest table configuration wins on merge");
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); m.remove(east); }
    }

    @GameTest(template = TEMPLATE)
    public static void joinDistanceIsMeasuredFromWholeTable(GameTestHelper h) {
        rectangle(h, 2, 3); var m = DDZTableManager.getInstance(); var root = key(h, 0, 0);
        var ps = players(h, 1); double previous = ServerGameConfig.joinRadius;
        try {
            DDZTestSupport.prepareBetting(m, root, ps);
            ServerGameConfig.joinRadius = 1;
            var end = key(h, 1, 2).pos();
            ps[0].teleportTo(end.getX() + 1.5, end.getY() + 1, end.getZ() + .5);
            m.join(ps[0], key(h, 1, 2));
            require(m.session(root).contains(ps[0]), "Player near far end must be able to join despite distant anchor");
            h.succeed();
        } finally {
            ServerGameConfig.joinRadius = previous;
            DDZTestSupport.cleanup(m, root, h, ps);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void nineTilesShareOneTableAndTenthPlacementConsumesNothing(GameTestHelper h) {
        rectangle(h, 3, 3); var m = DDZTableManager.getInstance(); var root = key(h, 0, 0);
        var ps = players(h, 1);
        try {
            DDZTestSupport.prepareBetting(m, root, ps);
            m.join(ps[0], key(h, 2, 2)); var session = m.session(root);
            require(session != null && session.snapshotFor(ps[0]).tableWidth() == 3
                && session.snapshotFor(ps[0]).tableDepth() == 3, "Nine tiles must form one 3x3 table");
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                require(m.session(key(h, x, z)) == session, "Every one of nine tiles must share one session");
            BlockPos candidate = key(h, 3, 0).pos(), support = candidate.below();
            h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            var stack = new ItemStack(InitItems.DDZ_TABLE_ITEM.get(), 2);
            ps[0].setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new BlockPlaceContext(ps[0], InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
            require(InitItems.DDZ_TABLE.get().getStateForPlacement(context) == null,
                "Tenth connected block must be refused by the placement rule");
            ((BlockItem) InitItems.DDZ_TABLE_ITEM.get()).place(context);
            require(h.getLevel().isEmptyBlock(candidate) && stack.getCount() == 2,
                "Rejected tenth placement must not change the world or consume an item");
            require(m.session(root) == session, "Rejected placement must not end the current game");
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); }
    }

    @GameTest(template = TEMPLATE)
    public static void commandPlacedTenTileTableCannotStartIndependentGames(GameTestHelper h) {
        rectangle(h, 2, 5); var m = DDZTableManager.getInstance(); var root = key(h, 0, 0);
        var ps = players(h, 1);
        try {
            for (int x = 0; x < 2; x++) for (int z = 0; z < 5; z++) {
                m.join(ps[0], key(h, x, z));
                require(m.session(key(h, x, z)) == null, "Every member of a ten-tile component must refuse a game");
            }
            h.succeed();
        } finally { DDZTestSupport.cleanup(m, root, h, ps); }
    }
}
