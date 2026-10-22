package com.jokernan.craftycards.game;

/** Currency arithmetic without Minecraft dependencies. Amounts are always in the table's base unit. */
public final class ChipDenominations {
    private ChipDenominations() {}
    public static String blockFor(String unit) {
        return switch(unit) {
            case "minecraft:iron_ingot" -> "minecraft:iron_block";
            case "minecraft:gold_ingot" -> "minecraft:gold_block";
            case "minecraft:diamond" -> "minecraft:diamond_block";
            default -> null;
        };
    }
    public record Withdrawal(long units,long blocks,int change) {}
    public static Withdrawal withdraw(long units,long blocks,long amount) {
        if(units<0||blocks<0||amount<0||amount>Math.addExact(units,Math.multiplyExact(blocks,9L)))
            throw new IllegalArgumentException("筹码不足或金额无效");
        long loose=Math.min(units,amount),rest=amount-loose;
        long broken=rest/9+(rest%9==0?0:1);
        return new Withdrawal(loose,broken,(int)(broken*9-rest));
    }
}
