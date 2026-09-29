package com.jokernan.craftycards.util;

import java.text.DecimalFormat;

/**
 * 字符串格式化工具类。
 * <p>提供数字千分位格式化等常用字符串处理方法。</p>
 */
public class StringHelper {
    /**
     * 将整数格式化为带千分位逗号的字符串。
     * <p>例如：{@code 1234567} → {@code "1,234,567"}</p>
     *
     * @param amount 要格式化的整数
     * @return 带千分位逗号的字符串
     */
    public static String printCommas(int amount) {
        return new DecimalFormat("#,###").format((double) amount);
    }
}
