package com.jokernan.craftycards.network.payload;

import com.jokernan.craftycards.CCReference;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * C2S：玩家对某张牌桌的操作。join=入座，bid=叫分(score)，play=出牌(cards)，pass=过牌，leave=离开，
 * watch=观战开关，open/save/close_table_config=本桌筹码配置界面。
 *
 * <p>字段是<b>按动作复用</b>的（每次只发其中一个动作，没必要为每个动作开一个包）：</p>
 * <ul>
 *   <li>{@code score}：BID = 叫分；SAVE_TABLE_CONFIG = <b>底注</b></li>
 *   <li>{@code cards}：PLAY = 要出的牌；SAVE_TABLE_CONFIG = <b>[门槛]</b>（{@code 0} = 自动）</li>
 *   <li>{@code chipItem} / {@code chipsEnabled}：仅 SAVE_TABLE_CONFIG 用，其余动作填 {@code ""} / {@code false}</li>
 * </ul>
 * 复用而不是给每个动作加字段：包已经在线上跑了，字段越多越容易在"哪个动作填哪个字段"上出错；
 * 新增字段一律<b>追加在末尾</b>（与动作枚举同规矩）。
 */
public record PlayerActionPayload(TableKey table, Action action, int score, List<Integer> cards,
                                 String chipItem, boolean chipsEnabled) implements CustomPacketPayload {
    /**
     * 玩家动作。**只能往末尾追加**：网络用 ordinal 编码（{@code writeEnum}），
     * 插在中间会让新旧版本的动作号错位。
     */
    public enum Action {
        JOIN, BID, PLAY, PASS, LEAVE, WATCH,
        /** Shift+右键牌桌：打开/接管本桌配置界面（房主限定，见 {@code DDZSession#handleOpenConfig}）。 */
        OPEN_TABLE_CONFIG,
        /** 配置界面的「保存」：底注在 {@code score}、门槛在 {@code cards[0]}、筹码类型与开关在末尾两个字段。 */
        SAVE_TABLE_CONFIG,
        /** 配置界面的「返回」：释放自己持有的配置锁。 */
        CLOSE_TABLE_CONFIG
    }

    public static final Type<PlayerActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CCReference.MOD_ID, "player_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerActionPayload> STREAM_CODEC = StreamCodec.composite(
        TableKey.STREAM_CODEC, PlayerActionPayload::table,
        // 枚举用原版 ByteBufCodecs.idMapper（写 VAR_INT 序数）而不是 NeoForge 的 enumCodec：
        // 两者线上格式一致（都是 writeEnum 那套序数），但这个是**原版 API**，common 层两侧通用。
        // 老规矩不变：枚举**只能往末尾追加**，插在中间会让新旧版本的动作号错位。
        ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), PlayerActionPayload::action,
        ByteBufCodecs.INT, PlayerActionPayload::score,
        ByteBufCodecs.INT.apply(ByteBufCodecs.list()), PlayerActionPayload::cards,
        ByteBufCodecs.STRING_UTF8, PlayerActionPayload::chipItem,
        ByteBufCodecs.BOOL, PlayerActionPayload::chipsEnabled,
        PlayerActionPayload::new);

    /** 不带配置数据的动作（占绝大多数）用这个：筹码类型与开关留空。 */
    public PlayerActionPayload(TableKey table, Action action, int score, List<Integer> cards) {
        this(table, action, score, cards, "", false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
