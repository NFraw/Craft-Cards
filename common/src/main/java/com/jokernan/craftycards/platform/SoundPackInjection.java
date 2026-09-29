package com.jokernan.craftycards.platform;

/**
 * 音频注入资源包的接缝：common 的界面（音乐包页）只需要"重建一下、告诉我复制了几个文件"，
 * 具体怎么把 {@code config/crafty_cards/sounds_pack/} 变成游戏认的高优先级资源包由加载器决定。
 *
 * <p><b>默认实现直接调 common 的 {@link com.jokernan.craftycards.client.CustomSoundPack}</b>：
 * 生成逻辑本身与加载器无关（纯文件 IO + 原版资源包 API），所以两个加载器都不需要安装实现。
 * 接缝保留是因为"要不要重建"与"怎么把它交给游戏"仍是两件事——后者 NeoForge 走
 * 打包来源注册事件、Fabric 走 {@code PackRepository} 的 mixin。</p>
 *
 * <p><b>踩过的坑</b>：抽 common 层时把这里的调用方从 {@code CustomSoundPack.regenerate()}
 * 换成了接缝调用，却忘了安装实现 → 默认空实现让游戏里「保存并应用」变成空转
 * （保存后要重启才有声音，界面还显示"已保存"）。编译不会报错、单测也碰不到，
 * 只有读代码时"谁调用了 install"这一问才发现。</p>
 */
public final class SoundPackInjection {
    /** 各加载器可实现这两个方法（默认即够用）。 */
    public interface Bridge {
        /** 重建注入资源包并让游戏重载音频。 */
        void regenerate();

        /** 上一次生成时成功复制进包的候选文件数（界面显示用）。 */
        int lastCopiedCount();
    }

    private static volatile Bridge bridge = new Bridge() {
        @Override
        public void regenerate() {
            com.jokernan.craftycards.client.CustomSoundPack.regenerate();
        }

        @Override
        public int lastCopiedCount() {
            return com.jokernan.craftycards.client.CustomSoundPack.lastCopiedCount();
        }
    };

    private SoundPackInjection() {}

    /** 由各加载器入口尽早安装实现。 */
    public static void install(Bridge implementation) {
        if (implementation != null) {
            bridge = implementation;
        }
    }

    /** 重建注入资源包并重载音频（音乐包页点「保存并应用」时调用）。 */
    public static void regenerate() {
        bridge.regenerate();
    }

    /** 上一次生成时复制成功的候选文件数。 */
    public static int lastCopiedCount() {
        return bridge.lastCopiedCount();
    }
}
