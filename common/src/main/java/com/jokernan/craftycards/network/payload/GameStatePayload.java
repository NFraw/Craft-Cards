package com.jokernan.craftycards.network.payload;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZGamePhase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：一桌斗地主的全量状态快照，每次操作后广播给同桌 3 人（及桌边旁观者）。
 * myHand / myIndex / visibleHands 按接收者不同（每人只能看到自己手牌；
 * visibleHands 按"谁能看谁的牌面"的服务端配置逐个构建），不共用同一 payload 广播。
 * lastPlayedYaw 是出牌那一刻"牌桌中心 → 出牌者"的朝向（服务端定格），
 * 供客户端把桌面上的牌摆在原位并保持朝向——玩家可自由走动，不能按实时位置算。
 * 字段多，直接用 NBT 序列化（简单可靠，无需拼超长 composite）。
 */
public record GameStatePayload(
    TableKey table,
    int myIndex,
    List<String> playerNames,
    List<Integer> playerCardCounts,
    List<Boolean> online,
    DDZGamePhase phase,
    int landlordIndex,
    int currentPlayerIndex,
    int bidScore,
    int lastPlayedBy,
    List<Integer> lastPlayedCards,
    DDZCardType lastPlayedType,
    float lastPlayedYaw,
    List<Integer> dipai,
    List<Integer> myHand,
    List<List<Integer>> allHands,
    List<List<Integer>> visibleHands,
    int winnerTeam,
    boolean sessionClosed,
    String feedback,
    String betItem,
    int betCount,
    int betMulti,
    /**
     * 本局对玩家的筹码要求（背包里必须够这么多才能参与），用于提示玩家还差多少。
     * <p>{@code 0} = <b>本桌不玩筹码</b>：可能是全局闸门（{@code server.json} 的 {@code chipsRequired}）
     * 关掉了，也可能是本桌自己的开关关了——用 {@link #tableChipsEnabled} 区分。
     * 不必声明筹码、入局不校验也不收押注，结算不转移物品。客户端据此把界面切成"无需筹码"。
     * 客户端不能读自己那份 {@code server.json} 来判断——连专用服务器时它与服务端的配置无关。</p>
     */
    int chipRequired,
    /** 接收者是不是<b>本桌房主</b>（第一个 Shift+右键这张桌的人）。只有房主能改配置。 */
    boolean iAmHost,
    /**
     * 本桌配置界面现在是不是开在<b>接收者</b>这儿。
     *
     * <p>配置界面的开关完全由服务端说了算（它持有那把互斥锁）：客户端收到 {@code true} 就开界面、
     * 收到 {@code false} 就关。锁超时/被接管时服务端会补一份 {@code false} 的快照，
     * 所以客户端不会停在一个"点了保存没反应"的死界面上。</p>
     */
    boolean configOpen,
    /** 本桌<b>自己</b>的筹码开关（原始值，不受全局闸门影响）：配置界面用它显示/编辑这一项。 */
    boolean tableChipsEnabled,
    /**
     * 本桌<b>原始</b>的入局门槛，{@code 0} = 自动（底注 × 6）。
     *
     * <p>为什么不复用 {@link #chipRequired}：那是<b>实际生效值</b>，"自动"和"手动填了正好等于派生值"
     * 在它上面长得一模一样。配置界面要是分不出来，房主打开界面、什么都不改点一下保存，
     * 就会把他自己设的手动门槛悄悄变成"自动"——静默改配置是最难查的一类问题。</p>
     */
    int tableEntryCount,
    /**
     * 当前轮次还剩多少游戏刻（HUD 倒计时用）；<b>{@code -1} = 本服务器不限时</b>。
     *
     * <p>下发"剩余量"而不是"截止刻"：客户端与服务端的 tick 基准不同（专用服务器上更是两个进程），
     * 收到绝对值也没法换算。客户端拿到它就在本地按 50ms/刻 续算，下一次快照到达时重新对齐——
     * 这样倒计时是连续的，不必靠服务端每刻推送。</p>
     */
    int turnRemainingTicks
) implements CustomPacketPayload {

    public static final Type<GameStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CCReference.MOD_ID, "game_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GameStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeNbt(payload.toTag()),
        buf -> fromTag(buf.readNbt()));

    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", table.dimension().location().toString());
        tag.putLong("pos", table.pos().asLong());
        tag.putInt("myIndex", myIndex);
        tag.putInt("phase", phase.ordinal());
        tag.putInt("landlord", landlordIndex);
        tag.putInt("current", currentPlayerIndex);
        tag.putInt("bid", bidScore);
        tag.putInt("lastBy", lastPlayedBy);
        tag.putInt("type", lastPlayedType.ordinal());
        tag.putFloat("lastYaw", lastPlayedYaw);
        tag.putInt("winner", winnerTeam);
        tag.putBoolean("closed", sessionClosed);
        tag.putString("feedback", feedback == null ? "" : feedback);

        ListTag names = new ListTag();
        for (String name : playerNames) names.add(StringTag.valueOf(name));
        tag.put("names", names);

        tag.put("counts", toIntArray(playerCardCounts));
        tag.put("online", toByteArray(online));
        tag.put("lastCards", toIntArray(lastPlayedCards));
        tag.put("dipai", toIntArray(dipai));
        tag.put("hand", toIntArray(myHand));

        ListTag all = new ListTag();
        for (List<Integer> hand : allHands) all.add(toIntArray(hand));
        tag.put("allHands", all);

        ListTag vis = new ListTag();
        for (List<Integer> hand : visibleHands) vis.add(toIntArray(hand));
        tag.put("visHands", vis);
        tag.putString("betItem", betItem == null ? "" : betItem);
        tag.putInt("betCount", betCount);
        tag.putInt("betMulti", betMulti);
        tag.putInt("chipReq", chipRequired);
        tag.putBoolean("iAmHost", iAmHost);
        tag.putBoolean("cfgOpen", configOpen);
        tag.putBoolean("tblChips", tableChipsEnabled);
        tag.putInt("tblEntry", tableEntryCount);
        tag.putInt("turnLeft", turnRemainingTicks);
        return tag;
    }

    private static GameStatePayload fromTag(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse(tag.getString("dim")));

        List<String> names = new ArrayList<>();
        for (var n : tag.getList("names", 8)) names.add(n.getAsString());

        return new GameStatePayload(
            new TableKey(dim, net.minecraft.core.BlockPos.of(tag.getLong("pos"))),
            tag.getInt("myIndex"),
            names,
            fromIntArray(tag.getIntArray("counts")),
            fromByteArray(tag.getByteArray("online")),
            DDZGamePhase.values()[tag.getInt("phase")],
            tag.getInt("landlord"),
            tag.getInt("current"),
            tag.getInt("bid"),
            tag.getInt("lastBy"),
            fromIntArray(tag.getIntArray("lastCards")),
            DDZCardType.values()[tag.getInt("type")],
            tag.getFloat("lastYaw"),
            fromIntArray(tag.getIntArray("dipai")),
            fromIntArray(tag.getIntArray("hand")),
            fromAllHands(tag.getList("allHands", 11)),
            fromAllHands(tag.getList("visHands", 11)),
            tag.getInt("winner"),
            tag.getBoolean("closed"),
            tag.getString("feedback"),
            tag.getString("betItem"),
            tag.getInt("betCount"),
            tag.getInt("betMulti"),
            tag.getInt("chipReq"),
            tag.getBoolean("iAmHost"),
            tag.getBoolean("cfgOpen"),
            tag.getBoolean("tblChips"),
            tag.getInt("tblEntry"),
            tag.getInt("turnLeft"));
    }

    private static IntArrayTag toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return new IntArrayTag(arr);
    }

    private static List<Integer> fromIntArray(int[] arr) {
        List<Integer> list = new ArrayList<>(arr.length);
        for (int v : arr) list.add(v);
        return list;
    }

    private static List<List<Integer>> fromAllHands(ListTag list) {
        List<List<Integer>> all = new ArrayList<>(list.size());
        for (var tag : list) {
            all.add(fromIntArray(((IntArrayTag) tag).getAsIntArray()));
        }
        return all;
    }

    private static net.minecraft.nbt.ByteArrayTag toByteArray(List<Boolean> list) {
        byte[] arr = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = (byte) (list.get(i) ? 1 : 0);
        return new net.minecraft.nbt.ByteArrayTag(arr);
    }

    private static List<Boolean> fromByteArray(byte[] arr) {
        List<Boolean> list = new ArrayList<>(arr.length);
        for (byte b : arr) list.add(b != 0);
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
