package com.jokernan.craftycards.blockentity;

import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.init.InitBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 牌桌的<b>每桌</b>筹码配置：筹码类型、底注、入局门槛、本桌是否启用筹码，外加房主与"正在配置"的锁。
 *
 * <p>为什么要有这一层：筹码原本是<b>全局</b>配置（{@code server.json}），于是"这张桌押什么、押多少"
 * 只能全服统一；而玩家想要的是"一张桌子一套筹码信息"，服务器管理员只保留一个总闸门。
 * 两者必须分开存：闸门（{@link ServerGameConfig#chipsRequired}）只决定"能不能用"，
 * <b>不读也不写</b>这里的数据—所以"关掉再打开"不会把桌子的类型/数量重置掉。</p>
 *
 * <p>存方块实体而不是 {@code SavedData}：随区块自动持久化、天然按桌，老存档的桌子在第一次被用到时
 * 会被补上一个默认实例（见 {@link #ensureSeeded()}），不需要迁移脚本。</p>
 *
 * <p><b>字段语义</b>：{@link #stake()} 是每人入场押注数（≥1）；{@link #entryCount()} 是入局门槛，
 * {@code 0} = 自动（= 底注 × 6，见 {@link #effectiveEntryCount()}）；{@link #chipsEnabled()} 是本桌
 * 自己的开关（与全局闸门是"与"的关系）；{@link #chipItem()} 是筹码物品的注册名，空串 = 还没定。
 * 房主（{@link #host()}）是第一个 Shift+右键这张桌的玩家，只有他能改配置；
 * 房主离线时下一位 Shift+右键的人可以接管，否则房主一走这张桌子就再没人配得了。</p>
 *
 * <p>配置锁（{@link #configLocker()}）只活在内存里、不落盘：同一时间只允许一个玩家打开本桌配置界面。
 * 它同时记下取得时刻（{@link #configLockTick()}），超时或持有者走远就释放——否则界面开着人走了，
 * 别人永远打不开这张桌。</p>
 */
public class DDZTableBlockEntity extends BlockEntity {
    private static final String TAG_INIT = "Initialized";
    private static final String TAG_HOST = "Host";
    private static final String TAG_HOST_NAME = "HostName";
    private static final String TAG_CHIP = "ChipItem";
    private static final String TAG_STAKE = "Stake";
    private static final String TAG_ENTRY = "EntryCount";
    private static final String TAG_ENABLED = "ChipsEnabled";
    private static final String TAG_VERSION = "Version";
    private static final int CURRENT_VERSION = 1;

    /** 是否已从全局默认值种入过（区分"显式设成默认值"与"还没初始化"）。 */
    private boolean initialized;
    @Nullable private UUID host;
    /** 房主名（随 {@link #host()} 一起存）：给"本桌房主是 X"这类提示用，房主离线时也拿得到名字。 */
    private String hostName = "";
    private String chipItem = "";
    private int stake = 1;
    /** 入局门槛；0 = 自动（底注 × 6）。 */
    private int entryCount = 0;
    private boolean chipsEnabled = true;

    /** 正在配置本桌的玩家（内存态，不落盘）。 */
    @Nullable private transient UUID configLocker;
    /** 配置锁的取得／最后刷新时刻（服务器刻，内存态）：锁超时释放用。 */
    private transient long configLockTick;

    public DDZTableBlockEntity(BlockPos pos, BlockState state) {
        super(InitBlockEntityTypes.DDZ_TABLE.get(), pos, state);
    }

    /**
     * 首次访问时把全局默认值种进来（只做一次）。
     *
     * <p>种入而不是每次都回退到全局：桌子一旦被配置过，就应该只认自己的值—否则管理员改一次
     * 全局默认，全世界的桌子会跟着变，正是"每桌独立"要避免的。</p>
     *
     * <p>由会话在第一次拿到本桌配置时调用（{@code DDZSession#tableConfig}）：老存档里的桌子
     * 不需要迁移脚本，谁先用到它谁就把它补齐。</p>
     */
    public void ensureSeeded() {
        if (initialized) return;
        stake = Math.max(1, ServerGameConfig.chipStake);
        entryCount = Math.max(0, ServerGameConfig.chipEntryCount);
        chipsEnabled = true;
        initialized = true;
        setChanged();
    }

    public boolean initialized() {
        return initialized;
    }

    @Nullable public UUID host() {
        return host;
    }

    /** 房主名（空串 = 未记录名字，用占位文字显示）。 */
    public String hostName() {
        return hostName == null ? "" : hostName;
    }

    public void setHost(@Nullable UUID value) {
        setHost(value, "");
    }

    public void setHost(@Nullable UUID value, String name) {
        host = value;
        hostName = value == null || name == null ? "" : name;
        setChanged();
    }

    /** 本桌房主是否是这名玩家。 */
    public boolean isHost(UUID player) {
        return host != null && host.equals(player);
    }

    public String chipItem() {
        return chipItem;
    }

    public void setChipItem(String value) {
        chipItem = value == null ? "" : value;
        // 显式设定即视为"已经配置过"：否则后面的 ensureSeeded 会把刚设好的值又种回全局默认
        initialized = true;
        setChanged();
    }

    public int stake() {
        return Math.max(1, stake);
    }

    public void setStake(int value) {
        stake = Math.max(1, value);
        initialized = true;
        setChanged();
    }

    /** 入局门槛；0 = 自动（底注 × 6）。 */
    public int entryCount() {
        return Math.max(0, entryCount);
    }

    public void setEntryCount(int value) {
        entryCount = Math.max(0, value);
        initialized = true;
        setChanged();
    }

    /** 本桌实际生效的门槛：手动值优先，否则底注 × 6。 */
    public int effectiveEntryCount() {
        return entryCount() > 0 ? entryCount() : stake() * 6;
    }

    /** 本桌自己的筹码开关（与全局闸门是"与"关系）。 */
    public boolean chipsEnabled() {
        return chipsEnabled;
    }

    public void setChipsEnabled(boolean value) {
        chipsEnabled = value;
        initialized = true;
        setChanged();
    }

    @Nullable public UUID configLocker() {
        return configLocker;
    }

    /** 配置锁的取得／最后刷新时刻（服务器刻）。 */
    public long configLockTick() {
        return configLockTick;
    }

    public void setConfigLocker(@Nullable UUID value, long tick) {
        configLocker = value;
        configLockTick = tick;
    }

    /** 配置锁是否握在这名玩家手里。 */
    public boolean isConfigLocker(UUID player) {
        return configLocker != null && configLocker.equals(player);
    }

    /** 本桌是否正在被别人配置（互斥用：同一时间只允许一个玩家开配置界面）。 */
    public boolean isLockedByOther(UUID player) {
        return configLocker != null && !configLocker.equals(player);
    }

    /** 释放配置锁（谁持有都释放：靠超时/下线/主动关闭这条路进来）。 */
    public void clearConfigLocker() {
        configLocker = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        tag.putBoolean(TAG_INIT, initialized);
        if (host != null) tag.putUUID(TAG_HOST, host);
        tag.putString(TAG_HOST_NAME, hostName == null ? "" : hostName);
        tag.putString(TAG_CHIP, chipItem);
        tag.putInt(TAG_STAKE, stake);
        tag.putInt(TAG_ENTRY, entryCount);
        tag.putBoolean(TAG_ENABLED, chipsEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        initialized = tag.getBoolean(TAG_INIT);
        host = tag.hasUUID(TAG_HOST) ? tag.getUUID(TAG_HOST) : null;
        hostName = tag.getString(TAG_HOST_NAME);
        chipItem = tag.getString(TAG_CHIP);
        stake = tag.contains(TAG_STAKE) ? tag.getInt(TAG_STAKE) : 1;
        entryCount = tag.getInt(TAG_ENTRY);
        chipsEnabled = !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
    }
}
