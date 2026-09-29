package com.jokernan.craftycards.util;

import java.util.Random;

/**
 * 数组工具类 — 提供基本类型数组与包装类型数组的转换、克隆和洗牌功能。
 * <p>
 * 主要用于 {@link com.jokernan.craftycards.entity.base.EntityStacked} 中的
 * 牌堆数据存储（Byte[] ↔ byte[]）和洗牌操作。
 * </p>
 */
public class ArrayHelper {

    /**
     * 将 {@code Byte[]} 包装类型数组转换为 {@code byte[]} 基本类型数组。
     *
     * @param array 包装类型数组，可为 null
     * @return 基本类型数组，null 输入返回 null
     */
    public static byte[] toPrimitive(Byte[] array) {
        if (array == null) return null;
        if (array.length == 0) return new byte[0];
        byte[] result = new byte[array.length];
        for (int i = 0; i < array.length; i++) result[i] = array[i];
        return result;
    }

    /**
     * 将 {@code byte[]} 基本类型数组转换为 {@code Byte[]} 包装类型数组。
     *
     * @param array 基本类型数组，可为 null
     * @return 包装类型数组，null 输入返回 null
     */
    public static Byte[] toObject(byte[] array) {
        if (array == null) return null;
        if (array.length == 0) return new Byte[0];
        Byte[] result = new Byte[array.length];
        for (int i = 0; i < array.length; i++) result[i] = array[i];
        return result;
    }

    /**
     * 克隆数组（浅拷贝）。
     *
     * @param array 源数组，可为 null
     * @return 克隆后的数组，null 输入返回 null
     */
    public static <T> T[] clone(final T[] array) {
        if (array == null) return null;
        return array.clone();
    }

    /**
     * Fisher-Yates 洗牌算法 — 随机打乱数组元素顺序。
     * <p>用于 {@link com.jokernan.craftycards.entity.EntityCardDeck} 中的牌堆洗牌。</p>
     *
     * @param array 要洗牌的数组
     */
    public static void shuffle(final Object[] array) {
        Random random = new Random();
        for (int i = array.length; i > 1; i--) {
            swap(array, i - 1, random.nextInt(i), 1);
        }
    }

    /**
     * 交换数组中两个区间的元素。
     *
     * @param array   目标数组
     * @param offset1 第一个区间的起始偏移
     * @param offset2 第二个区间的起始偏移
     * @param length  要交换的元素数量
     */
    private static void swap(final Object[] array, int offset1, int offset2, int length) {
        if (array == null || array.length == 0 || offset1 >= array.length || offset2 >= array.length) return;
        if (offset1 < 0) offset1 = 0;
        if (offset2 < 0) offset2 = 0;
        length = Math.min(Math.min(length, array.length - offset1), array.length - offset2);
        for (int i = 0; i < length; i++, offset1++, offset2++) {
            Object aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
}
