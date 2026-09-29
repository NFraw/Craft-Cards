package com.jokernan.craftycards.game;

/**
 * 牌桌/卡牌渲染共用的纯几何换算。不依赖 Minecraft 类型，可直接单测。
 *
 * <p>两套角度约定（都验证过 JOML {@code Matrix4f.rotate(θ,0,1,0)} 的实际行为）：
 * <ul>
 *   <li><b>朝向角</b>：绕 Y 轴转 θ 后，局部 +z 轴指向世界方向 {@code (sinθ, cosθ)}——
 *       故要让 +z 指向 (dx, dz)，θ = {@code atan2(dx, dz)}（归一化到 [0, 360)）。</li>
 *   <li><b>Minecraft 玩家 yaw</b>：0 = 南(+z)，90 = 西(−x)；前方水平单位向量为
 *       {@code (−sin(yaw), cos(yaw))}。</li>
 * </ul>
 */
public final class DDZGeom {
    private DDZGeom() {}

    /**
     * 让局部 +z 轴指向水平方向 (dx, dz) 所需的绕 Y 轴旋转角（度，归一化到 [0, 360)）。
     *
     * <p>用法：立牌/出牌面朝某个玩家时，传"牌 → 玩家"的水平方向。
     */
    public static float facingYaw(double dx, double dz) {
        // 先取模到 (-360, 360)，转成 float 后再归一化一次：
        // double 域的 359.999…° 转 float 时会被舍入成 360.0f，必须在 float 域夹回 [0, 360)
        float yaw = (float) (Math.toDegrees(Math.atan2(dx, dz)) % 360.0);
        if (yaw < 0F) yaw += 360F;
        if (yaw >= 360F) yaw -= 360F;
        return yaw;
    }

    /** Minecraft 玩家 yaw（度）对应的前方水平单位向量 x 分量。 */
    public static double forwardX(double yawDeg) {
        return -Math.sin(Math.toRadians(yawDeg));
    }

    /**
     * 扇形手持牌的"逐张前移量"：让相邻两张牌的<b>平面距离</b>恒为 {@code planeDist}。
     *
     * <p>牌组横向按 {@code gap} 等距排开（横向间隔不随扇形变化），每张再绕<b>自身竖直中心</b>
     * 转 {@code θ}。若前移量"每个相邻对都等量、且恒定沿组坐标 +z"，它在<b>每张牌自己的法线</b>
     * 上的投影是 {@code gap·sinθ + step·cosθ}：θ 为负（左半边）时两项相抵、θ 为正
     * （右半边）时两项相加。实测（gap 0.104、扇形 3°/张、17 张）：最左相邻对 0.0074（比牌模型
     * 厚度 0.0125 还小，两张牌的实体已经互相穿插），最右相邻对 0.0881（牌厚的 7 倍）——同样的
     * 横向间隔，左右观感却完全不同，整叠牌还朝一侧斜出去 0.87 格。
     *
     * <p>本函数把那个一次 θ 项反解掉：{@code dz = (planeDist - gap·sinθ) / cosθ}，于是每个
     * 相邻对的平面距离都等于 {@code planeDist}——不是"用安全系数压过交叉"，而是让交叉量恒为 0，
     * 左右两支怎么排都一致。
     *
     * @param gap       相邻两张牌中心的横向间隔（方块）
     * @param planeDist 期望的平面距离（沿被压住那张牌的法线，方块）
     * @param thetaDeg  被压住的那张（左面那张）的扇形角，度
     * @return 后一张相对前一张沿组坐标 +z 的前移量；右半边可以是负数（略往后退），
     *         但算出来的平面距离始终为正
     */
    public static float fanDepthAdvance(float gap, float planeDist, float thetaDeg) {
        double t = Math.toRadians(thetaDeg);
        // 牌面接近与观察方向平行时（|θ|→90°）前移量会被放大到失控；极端配置下夹住。
        // 正常配置走不到这里（17 张 × 3°/张 = ±24°，17 张 × 6°/张 = ±48°）
        double cos = Math.max(0.2, Math.cos(t));
        return (float) ((planeDist - gap * Math.sin(t)) / cos);
    }

    /** Minecraft 玩家 yaw（度）对应的前方水平单位向量 z 分量。 */
    public static double forwardZ(double yawDeg) {
        return Math.cos(Math.toRadians(yawDeg));
    }
}
