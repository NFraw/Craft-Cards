package com.jokernan.craftycards.game.zjh;

import java.util.*;

/** Pure Java authoritative state. All amounts are logical chips; no Minecraft dependencies. */
public final class ZJHEngine {
    private final int[][] hands;
    private final boolean[] active;
    private final int[] revealed;
    private final long[] contributions, payouts;
    private final int ante, cap, dealer, start;
    private int current, round=1, base;
    private boolean settled;
    private List<Integer> winners=List.of();

    public ZJHEngine(int[][] dealt,int ante,int cap,int dealer) {
        if(dealt.length<2||dealt.length>6||ante<1||ante>1_000_000||cap<ante||cap>1_000_000
            ||dealer<0||dealer>=dealt.length)throw new IllegalArgumentException("无效的牌局配置");
        Set<Integer> cards=new HashSet<>();
        hands=new int[dealt.length][];
        for(int i=0;i<dealt.length;i++) {
            ZJHHandEvaluator.evaluate(dealt[i]);hands[i]=dealt[i].clone();
            for(int c:dealt[i])if(!cards.add(c))throw new IllegalArgumentException("发牌重复");
        }
        this.ante=ante;this.cap=cap;this.dealer=dealer;base=ante;
        current=start=(dealer+1)%dealt.length;
        active=new boolean[dealt.length];Arrays.fill(active,true);
        revealed=new int[dealt.length];contributions=new long[dealt.length];Arrays.fill(contributions,ante);
        payouts=new long[dealt.length];
        if(cap==ante)finish();
    }
    public static ZJHEngine deal(int count,int ante,int cap,int dealer,Random random) {
        List<Integer> deck=new ArrayList<>();for(int i=0;i<52;i++)deck.add(i);Collections.shuffle(deck,random);
        int[][] hands=new int[count][3];
        for(int c=0;c<3;c++)for(int p=0;p<count;p++)hands[p][c]=deck.get(c*count+p);
        return new ZJHEngine(hands,ante,cap,dealer);
    }
    private void requireTurn(int seat) {
        if(settled||seat!=current||!active[seat])throw new IllegalArgumentException("还没轮到你行动");
    }
    public boolean reveal(int seat,int mask) {
        requireTurn(seat);
        if(mask<=0||(mask&~7)!=0)throw new IllegalArgumentException("请选择要翻开的牌");
        int before=revealed[seat];revealed[seat]|=mask;return before!=revealed[seat];
    }
    public void call(int seat) { requireTurn(seat);if(pay(seat,callCost(seat)))advance(); }
    public void raise(int seat,int value) {
        requireTurn(seat);
        if(value<=base||(value!=ante*2&&value!=ante*4))throw new IllegalArgumentException("无效的加注档位");
        long fee=(long)value*(looked(seat)?2:1);
        // Validate before updating the shared base; a cap showdown cannot leave a phantom raise.
        if(contributions[seat]+fee>cap){finish();return;}
        base=value;if(pay(seat,fee))advance();
    }
    public void compare(int seat,int target) {
        requireTurn(seat);
        if(round<3)throw new IllegalArgumentException("第三轮开始才可以比牌");
        if(target<0||target>=size()||target==seat||!active[target])throw new IllegalArgumentException("无效的比牌目标");
        if(!pay(seat,callCost(seat)*2))return;
        int loser=ZJHHandEvaluator.compare(hands[seat],hands[target])>0?target:seat;
        active[loser]=false;
        if(survivors()==1)finish();else advance();
    }
    /** Off-turn fold is used only for a verified disconnect/leave on the server. */
    public void fold(int seat) {
        if(settled||seat<0||seat>=size()||!active[seat])throw new IllegalArgumentException("不在牌局中");
        active[seat]=false;
        if(survivors()==1)finish();else if(current==seat)advance();
    }
    private boolean pay(int seat,long amount) {
        if(contributions[seat]+amount>cap){finish();return false;}
        contributions[seat]+=amount;
        if(contributions[seat]==cap){finish();return false;}
        return true;
    }
    private void advance() {
        do {current=(current+1)%size();if(current==start)round++;}while(!active[current]);
    }
    private void finish() {
        List<Integer> seats=new ArrayList<>();List<int[]> eligible=new ArrayList<>();
        for(int i=0;i<size();i++)if(active[i]){seats.add(i);eligible.add(hands[i]);}
        winners=ZJHHandEvaluator.winners(eligible).stream().map(seats::get).toList();
        long share=pot()/winners.size(),remainder=pot()%winners.size();
        for(int w:winners)payouts[w]=share;
        for(int offset=0;offset<size()&&remainder>0;offset++) {
            int p=(dealer+offset)%size();if(winners.contains(p)){payouts[p]++;remainder--;}
        }
        settled=true;
    }
    private int survivors(){int n=0;for(boolean a:active)if(a)n++;return n;}
    public int size(){return hands.length;}
    public int current(){return current;}
    public int round(){return round;}
    public int dealer(){return dealer;}
    public int base(){return base;}
    public int ante(){return ante;}
    public int cap(){return cap;}
    public boolean active(int p){return active[p];}
    public boolean looked(int p){return revealed[p]!=0;}
    public int revealedMask(int p){return revealed[p];}
    public int revealedCount(int p){return Integer.bitCount(revealed[p]);}
    public int[] hand(int p){return hands[p].clone();}
    public long callCost(int p){return (long)base*(looked(p)?2:1);}
    public long contribution(int p){return contributions[p];}
    public long pot(){return Arrays.stream(contributions).sum();}
    public long payout(int p){return payouts[p];}
    public boolean settled(){return settled;}
    public List<Integer> winners(){return winners;}
}
