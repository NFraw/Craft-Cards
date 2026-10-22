package com.jokernan.craftycards.client;

/** Shared pure layout math, bounded by the actual GUI viewport. */
public final class ZJHLayout {
    public record Cards(int width,int height,int gap,int left,int centerY) {
        public int centerX(int slot){return left+width/2+slot*gap;}
        public int top(){return centerY-height/2;}
        public int bottom(){return centerY+height/2;}
    }
    private ZJHLayout(){}

    /** 默认底边距（= 斗地主 {@code RenderConfig.hudBottomMargin} 的默认值）。 */
    public static final int DEFAULT_BOTTOM_MARGIN = 30;
    /**
     * 卡牌下沿与屏幕底边的距离，默认 {@value #DEFAULT_BOTTOM_MARGIN}——与斗地主
     * {@code RenderConfig.hudBottomMargin} 的默认值一致，两边的手牌离快捷栏距离因此相同。
     *
     * <p>这里存的是<b>副本</b>而不是直接读 {@code RenderConfig}：那个类为了读写
     * {@code visual.json} 在静态初始化里建了 Gson，而布局类要被纯算术单测和不出图的探针调用，
     * 不该因此背上 Gson/文件系统的依赖。{@code ZJHClient} 每帧调 {@link #setBottomMargin(int)}
     * 同步一次，所以 {@code /craftycards reload} 改了 {@code hudBottomMargin} 之后两个游戏仍然一起变。</p>
     */
    private static int bottomMargin = DEFAULT_BOTTOM_MARGIN;

    /** 当前底边距。 */
    public static int bottomMargin(){return bottomMargin;}

    /** 由客户端渲染路径每帧同步（{@code RenderConfig.hudBottomMargin}）。 */
    public static void setBottomMargin(int value){bottomMargin=Math.max(0,value);}

    /**
     * 手牌<b>贴底</b>：下沿固定在屏幕底部上方 {@link #bottomMargin()} 像素（避开快捷栏）。
     * 选项行与提示行都叠在卡牌上方——卡牌永远是屏幕上最下面的东西（与斗地主一致）。
     *
     * <p>完整模型宽10.2、长16；布局和渲染都使用 {@link CardGameHudLayout} 的换算，
     * 高度取四舍五入。不能只按中央牌面的宽9换算，否则两侧边缘会超出布局框。</p>
     */
    public static Cards hand(int width,int height) {
        int maxW=Math.min(44,(width-32)/3);
        int maxH=Math.max(24,height-140);
        int cardW=Math.max(12,Math.min(maxW,(int)(maxH*CardGameHudLayout.MODEL_WIDTH/16)));
        int cardH=CardGameHudLayout.cardHeight(cardW);
        int gap=cardW+6;
        return new Cards(cardW,cardH,gap,(width-(2*gap+cardW))/2,height-bottomMargin()-cardH/2);
    }
    /** 手牌信息行（暗牌/明牌 · 投入 · 余额）：卡牌正上方一行。 */
    public static int handInfoY(int width,int height){return hand(width,height).top()-11;}
    /** 选项行：在信息行上方，卡牌永远在它下面。 */
    public static int optionsY(int width,int height){return Math.min(handInfoY(width,height)-18,
        HudOptionBar.actionRowY(height,CardGameHudLayout.ACTION_OFFSET));}
    /** 提示行：在选项行<b>上方</b>，间距与斗地主共用 {@link HudOptionBar#HINT_GAP}。 */
    public static int hintY(int width,int height){return HudOptionBar.hintY(optionsY(width,height));}
    /** 反馈行：在提示行再往上一行，紧贴操作块（不再画在屏幕顶部）。 */
    public static int messageY(int width,int height){return HudOptionBar.messageY(optionsY(width,height));}

    /** 结算行高：底部留关闭提示与原版快捷栏安全区，顶部留标题与获胜行。 */
    public static int resultRowHeight(int height,int players){return Math.max(14,Math.min(42,
        (height-CardGameHudLayout.CLOSE_RESERVE-RESULT_HEADER_H-RESULT_TOP_MARGIN-6)/Math.max(1,players)));}

    /** 结算页标题块高度：从标题行到第一家行之间的固定间距（标题 y、获胜行 y+14、玩家行 y+24）。 */
    public static final int RESULT_HEADER_H=24;
    /** 结算页内容块与底部操作行之间至少留出的距离。 */
    public static final int RESULT_BOTTOM_RESERVE=CardGameHudLayout.CLOSE_RESERVE+6;
    /** 结算页内容块顶部最小留白。 */
    public static final int RESULT_TOP_MARGIN=6;

    /**
     * 结算页内容块（标题 + 获胜行 + 玩家行）的起始 y：整块在上下边界之间<b>垂直居中</b>。
     *
     * <p>与斗地主的 {@code HudSettlementLayout} 同一套做法。此前炸金花是从 y=10 直接往下排，
     * 两人局在 960×540 上只在顶部占 118px，剩下 400 多像素全空——两个游戏的结算观感就对不上了。</p>
     */
    public static int resultTop(int height,int players) {
        int total=RESULT_HEADER_H+Math.max(1,players)*resultRowHeight(height,players);
        int available=Math.max(0,height-RESULT_BOTTOM_RESERVE-RESULT_TOP_MARGIN);
        return RESULT_TOP_MARGIN+Math.max(0,(available-total)/2);
    }

    /** 结算页底部操作行 y：与斗地主结算页的底部提示同一套边距。 */
    public static int resultOptionsY(int height){return height-40;}
    /** 结算页底部提示行 y。 */
    public static int resultHintY(int height){return CardGameHudLayout.closeY(height);}

    /**
     * 这一行够不够高到叠两行文字（"胜 对子" + "净得失 +12"）。
     *
     * <p>两行要占 {@code 2*9} 像素再留 2px 行缝，实测需要 22px。矮屏多人结算时行高会压到 14–21，
     * 硬叠两行的话第一行会画到<b>上一行里去</b>（{@code y+row/2-9} 落在上一行的下半部分）。
     * 此时渲染侧改画一行（{@code ZJHClient.result} 按本方法分支）。</p>
     */
    public static boolean resultRowFitsTwoLines(int rowHeight){return rowHeight>=22;}
}
