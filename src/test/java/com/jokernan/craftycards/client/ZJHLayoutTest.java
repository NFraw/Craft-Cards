package com.jokernan.craftycards.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 炸金花 HUD 的竖向几何：卡牌贴底、卡牌 完整模型比例16:10.2、四条带不重叠、结算块居中。
 * 选项行的横向排版已移到 {@link HudOptionBar}（两个游戏共用），见 {@code HudOptionBarTest}。
 */
class ZJHLayoutTest {
    @Test void actionRowUsesDdzBaselineOnTheSharedCanvas() {
        assertEquals(140,ZJHLayout.optionsY(480,320),"统一320高画布操作锚点应为320-180，并避开准星");
    }

    @Test void cardsUseTheFullModelWidthIncludingBevels() {
        var cards=ZJHLayout.hand(480,320);
        assertEquals(Math.round(cards.width()*16F/10.2F),cards.height(),
            "card_base.json的完整模型宽10.2、高16，不能只按中间面宽9换算");
    }

    @Test void resultHintClearsHotbarAndItemName() {
        assertTrue(ZJHLayout.resultHintY(320)+9<=320-40,"结算提示需让开快捷栏及物品名");
    }
    private static final int[] WIDTHS = {320, 427, 640, 960};
    private static final int[] HEIGHTS = {180, 240, 360, 540};

    @Test void threeHandSlotsAndSixResultsFitSupportedViewports() {
        for(int w:WIDTHS)for(int h:HEIGHTS) {
            var l=ZJHLayout.hand(w,h);
            assertTrue(l.left()>=8);
            assertTrue(l.centerX(2)+l.width()/2<=w-8);
            assertTrue(l.gap()>l.width());
            // 卡牌贴底：下沿固定让开快捷栏，选项行/提示行全部叠在卡牌上方
            assertEquals(h-ZJHLayout.bottomMargin(),l.bottom());
            assertTrue(ZJHLayout.hintY(w,h)>=54,"提示行不能顶到标题/阶段行");
            assertTrue(ZJHLayout.hintY(w,h)+9<=ZJHLayout.optionsY(w,h),"提示行不压选项行");
            assertTrue(ZJHLayout.optionsY(w,h)+12<=ZJHLayout.handInfoY(w,h),"选项行不压手牌信息");
            assertTrue(ZJHLayout.handInfoY(w,h)+9<=l.top(),"手牌信息不压卡牌");
            // 结算行必须让开底部那行"右键关闭"提示
            assertTrue(34+6*ZJHLayout.resultRowHeight(h,6)<=h-56);
        }
    }

    /** 底边距默认值与斗地主 {@code RenderConfig.hudBottomMargin} 一致（30），客户端每帧同步。 */
    @Test void bottomMarginDefaultsToTheSameValueAsDdz() {
        assertEquals(30,ZJHLayout.DEFAULT_BOTTOM_MARGIN,"与 RenderConfig.hudBottomMargin 的默认值一致");
        assertEquals(30,ZJHLayout.bottomMargin());
        ZJHLayout.setBottomMargin(42);
        try {
            assertEquals(42,ZJHLayout.bottomMargin(),"客户端同步后应生效");
            assertEquals(360-42,ZJHLayout.hand(640,360).bottom(),"卡牌下沿跟着底边距走");
        } finally {
            ZJHLayout.setBottomMargin(ZJHLayout.DEFAULT_BOTTOM_MARGIN);
        }
    }

    /**
     * 卡牌高度必须等于 {@code CardVisuals} 实绘的 {@code width*16/10.2}。
     * 否则选中框、绿框都比牌高出一截，{@code bottom()} 也不再等于 {@code 屏高-底边距}。
     */
    @Test void cardBoxMatchesWhatTheRendererActuallyDraws() {
        for(int w:WIDTHS)for(int h:HEIGHTS) {
            var l=ZJHLayout.hand(w,h);
            assertEquals(CardGameHudLayout.cardHeight(l.width()),l.height());
        }
    }

    /** 竖向语法：反馈行在提示行上方，提示行在选项行上方，间距来自 HudOptionBar。 */
    @Test void messageAndHintSitAboveTheOptionRow() {
        for(int w:WIDTHS)for(int h:HEIGHTS) {
            assertTrue(ZJHLayout.messageY(w,h)<ZJHLayout.hintY(w,h),"反馈行应在提示行上方");
            assertTrue(ZJHLayout.hintY(w,h)<ZJHLayout.optionsY(w,h),"提示行应在选项行上方");
            assertEquals(HudOptionBar.HINT_GAP,ZJHLayout.optionsY(w,h)-ZJHLayout.hintY(w,h));
            assertEquals(HudOptionBar.MESSAGE_GAP,ZJHLayout.hintY(w,h)-ZJHLayout.messageY(w,h));
            // 反馈行不能顶到顶部三行（标题 4 / 阶段 16 / 局势 30）
            assertTrue(ZJHLayout.messageY(w,h)>=30+9,"反馈行 y=" + ZJHLayout.messageY(w,h));
        }
    }

    /** 结算块垂直居中：行数少时不能贴顶（两人局 960×540 曾只在顶部占 118px）。 */
    @Test void settlementBlockIsVerticallyCentered() {
        for(int h:HEIGHTS)for(int players=2;players<=6;players++) {
            int top=ZJHLayout.resultTop(h,players);
            int row=ZJHLayout.resultRowHeight(h,players);
            assertTrue(top>=ZJHLayout.RESULT_TOP_MARGIN,"内容块不能越过上边界");
            int lastBottom=top+ZJHLayout.RESULT_HEADER_H+players*row-2;
            assertTrue(lastBottom<=ZJHLayout.resultHintY(h)-6,
                h+" 高 "+players+" 人：末行下沿 "+lastBottom+" 应让开底部提示 "+ZJHLayout.resultHintY(h));
            if(h>=360)assertTrue(top>ZJHLayout.RESULT_TOP_MARGIN,"大屏上内容块应居中而不是贴顶");
        }
    }

    /** 结算行高不足 22px 时渲染侧退成一行——这个判据必须与渲染分支一致。 */
    @Test void resultRowSwitchesToOneLineWhenTooShort() {
        assertFalse(ZJHLayout.resultRowFitsTwoLines(21));
        assertTrue(ZJHLayout.resultRowFitsTwoLines(22));
        assertEquals(15,ZJHLayout.resultRowHeight(180,6),"未经虚拟画布换算的180高压力测试");
        assertFalse(ZJHLayout.resultRowFitsTwoLines(ZJHLayout.resultRowHeight(180,6)));
    }
}
