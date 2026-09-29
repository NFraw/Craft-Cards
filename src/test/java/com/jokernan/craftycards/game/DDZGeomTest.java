package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渲染几何换算的不变量测试。
 *
 * <p>两条角度约定（朝向角、Minecraft yaw）是世界内立牌/出牌位置与朝向的全部依据，
 * 写错的表现是"牌浮在玩家背后"或"牌背朝人"，靠肉眼很难定位，故在此固定。</p>
 */
class DDZGeomTest {
    private static final double EPS = 1e-6;

    /** Minecraft yaw 约定：0=南(+z)，90=西(−x)，180=北(−z)，270=东(+x)。 */
    @Test
    void minecraftYawConvention() {
        assertEquals(0.0, DDZGeom.forwardX(0), EPS);
        assertEquals(1.0, DDZGeom.forwardZ(0), EPS);
        assertEquals(-1.0, DDZGeom.forwardX(90), EPS);
        assertEquals(0.0, DDZGeom.forwardZ(90), EPS);
        assertEquals(0.0, DDZGeom.forwardX(180), EPS);
        assertEquals(-1.0, DDZGeom.forwardZ(180), EPS);
        assertEquals(1.0, DDZGeom.forwardX(270), EPS);
        assertEquals(0.0, DDZGeom.forwardZ(270), EPS);
    }

    /** 朝向角的核心约定：绕 Y 转 θ 后 +z 指向 (sinθ, cosθ)。 */
    @Test
    void facingYawPointsAlongDirection() {
        double[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {0.6, 0.8}, {-0.7071, 0.7071}};
        for (double[] d : dirs) {
            double yaw = DDZGeom.facingYaw(d[0], d[1]);
            assertEquals(d[0], Math.sin(Math.toRadians(yaw)), 1e-4, "方向 (" + d[0] + "," + d[1] + ") 的 x 分量不符");
            assertEquals(d[1], Math.cos(Math.toRadians(yaw)), 1e-4, "方向 (" + d[0] + "," + d[1] + ") 的 z 分量不符");
        }
    }

    /** 朝向角归一化在 [0, 360)，同一方向不会出现两个相差 360° 的写法。 */
    @Test
    void facingYawIsNormalized() {
        for (int deg = -720; deg <= 720; deg += 15) {
            double rad = Math.toRadians(deg);
            double yaw = DDZGeom.facingYaw(Math.sin(rad), Math.cos(rad));
            assertTrue(yaw >= 0 && yaw < 360, "朝向角越界: " + yaw + "（输入 " + deg + "°）");
        }
    }

    /**
     * 核心不变量：无论扇形角是正是负，相邻两张的<b>平面距离</b>（沿左边那张的法线）都等于 planeDist。
     *
     * <p>旧实现（前移量等量且恒定沿组坐标 +z）在 θ < 0 时两项相抵、θ > 0 时相加，实测左右相差
     * 12 倍（0.0074 vs 0.0881）——这正是"同样的横向间隔下，右侧平面距离相隔较远、和左侧不一致"的根因。
     */
    @Test
    void fanDepthAdvanceKeepsPlaneDistanceConstant() {
        float gap = 0.104F, plane = 0.016F;
        for (float theta = -24F; theta <= 21F; theta += 3F) {
            float dz = DDZGeom.fanDepthAdvance(gap, plane, theta);
            double pd = gap * Math.sin(Math.toRadians(theta))
                + dz * Math.cos(Math.toRadians(theta));
            assertEquals(plane, pd, 1e-5, "θ=" + theta + "° 的平面距离应恒为 " + plane);
        }
    }

    /**
     * 左右两支观感一致：±θ 时"每张牌露出多少"（面内横向偏移）之差应远小于旧实现的偏差。
     * 旧实现（等量且方向固定的前移）实测最左 0.0074、最右 0.0881，相差 12 倍。
     */
    @Test
    void fanDepthAdvanceKeepsLeftRightConsistent() {
        float gap = 0.104F, plane = 0.016F;
        for (float theta = 3F; theta <= 24F; theta += 3F) {
            float left = exposedWidth(gap, plane, -theta);
            float right = exposedWidth(gap, plane, theta);
            double rel = Math.abs(left - right) / Math.min(left, right);
            assertTrue(rel < 0.25,
                "θ=±" + theta + "° 左右露出宽度差异过大：" + left + " vs " + right);
        }
    }

    /** 极端扇形角（|θ|→90°）不能让前移量发散：渲染线程拿到 NaN/无穷会直接崩游戏。 */
    @Test
    void fanDepthAdvanceClampsExtremeAngles() {
        for (float theta : new float[] { 75F, 85F, 89F, -89F }) {
            float dz = DDZGeom.fanDepthAdvance(0.104F, 0.02F, theta);
            assertTrue(Float.isFinite(dz) && Math.abs(dz) < 10F,
                "θ=" + theta + "° 的前移量失控：" + dz);
        }
    }

    /** 一张牌露出多少：相邻两张中心沿"被压住那张"自身横向轴的距离。 */
    private static float exposedWidth(float gap, float plane, float thetaDeg) {
        double t = Math.toRadians(thetaDeg);
        float dz = DDZGeom.fanDepthAdvance(gap, plane, thetaDeg);
        return (float) (gap * Math.cos(t) - dz * Math.sin(t));
    }

    /**
     * 组合不变量：牌放在玩家前方 forward×dist 处、牌面朝向玩家时，
     * "牌 → 玩家"方向恰好是 −forward，即 facingYaw(−fx, −fz) 的 sin/cos 与 (−fx, −fz) 一致。
     * 这正是 WorldHandCards 让持牌人看到自己牌面所用的换算。
     */
    @Test
    void cardsInFrontFaceBackAtOwner() {
        for (int yaw = -180; yaw < 180; yaw += 30) {
            double fx = DDZGeom.forwardX(yaw);
            double fz = DDZGeom.forwardZ(yaw);
            // 单位向量性质
            assertEquals(1.0, Math.hypot(fx, fz), EPS);
            double backYaw = DDZGeom.facingYaw(-fx, -fz);
            assertEquals(-fx, Math.sin(Math.toRadians(backYaw)), 1e-4);
            assertEquals(-fz, Math.cos(Math.toRadians(backYaw)), 1e-4);
        }
    }
}
