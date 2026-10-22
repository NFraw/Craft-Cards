package com.jokernan.craftycards.game.zjh;

import java.util.*;

/** Three-card rules. The special/trips cycle deliberately is NOT a sorting comparator. */
public final class ZJHHandEvaluator {
    public enum Type {
        SPECIAL("特殊235"), HIGH("散牌"), PAIR("对子"), STRAIGHT("顺子"),
        FLUSH("金花"), STRAIGHT_FLUSH("顺金"), TRIPS("豹子");
        public final String label;
        Type(String label) { this.label=label; }
    }
    public record Value(Type type, List<Integer> ranks) {}
    private ZJHHandEvaluator() {}
    public static Value evaluate(int[] cards) {
        if(cards.length!=3) throw new IllegalArgumentException("必须是三张牌");
        Set<Integer> unique=new HashSet<>(); int[] r=new int[3];
        for(int i=0;i<3;i++) {
            if(cards[i]<0||cards[i]>=52||!unique.add(cards[i]))throw new IllegalArgumentException("无效或重复的牌");
            int rank=cards[i]/4+1;r[i]=rank==1?14:rank;
        }
        Arrays.sort(r);
        boolean flush=cards[0]%4==cards[1]%4&&cards[1]%4==cards[2]%4;
        int straight=r[0]==2&&r[1]==3&&r[2]==14?13:
            r[1]==r[0]+1&&r[2]==r[1]+1?(r[2]==14?14:r[2]-1):0;
        if(r[0]==r[2])return new Value(Type.TRIPS,List.of(r[0]));
        if(straight>0)return new Value(flush?Type.STRAIGHT_FLUSH:Type.STRAIGHT,List.of(straight));
        if(flush)return new Value(Type.FLUSH,List.of(r[2],r[1],r[0]));
        if(r[0]==r[1]||r[1]==r[2])return new Value(Type.PAIR,List.of(r[1],r[0]==r[1]?r[2]:r[0]));
        if(r[0]==2&&r[1]==3&&r[2]==5)return new Value(Type.SPECIAL,List.of(5,3,2));
        return new Value(Type.HIGH,List.of(r[2],r[1],r[0]));
    }
    public static int compare(int[] a,int[] b) { return compare(evaluate(a),evaluate(b)); }
    private static int compare(Value a,Value b) {
        if(a.type==Type.SPECIAL&&b.type==Type.TRIPS)return 1;
        if(b.type==Type.SPECIAL&&a.type==Type.TRIPS)return -1;
        int type=Integer.compare(a.type.ordinal(),b.type.ordinal());
        if(type!=0)return type;
        for(int i=0;i<a.ranks.size();i++) {
            int c=Integer.compare(a.ranks.get(i),b.ranks.get(i));if(c!=0)return c;
        }
        return 0;
    }
    /** Inputs contain only eligible survivors, never already eliminated hands. */
    public static List<Integer> winners(List<int[]> hands) {
        if(hands.isEmpty())throw new IllegalArgumentException("没有参比玩家");
        var values=hands.stream().map(ZJHHandEvaluator::evaluate).toList();
        boolean special=values.stream().anyMatch(v->v.type==Type.SPECIAL);
        boolean trips=values.stream().anyMatch(v->v.type==Type.TRIPS);
        List<Integer> result=new ArrayList<>();
        if(special&&trips) {
            for(int i=0;i<values.size();i++)if(values.get(i).type==Type.SPECIAL)result.add(i);
        } else {
            int best=0;
            for(int i=1;i<values.size();i++)if(compare(values.get(i),values.get(best))>0)best=i;
            for(int i=0;i<values.size();i++)if(compare(values.get(i),values.get(best))==0)result.add(i);
        }
        return List.copyOf(result);
    }
}
