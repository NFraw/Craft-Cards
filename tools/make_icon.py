#!/usr/bin/env python3
"""生成模组图标（common/src/main/resources/logo.png）。

由现有素材合成，不引入任何新的美术资源：
- 底色取自牌桌呢面贴图（block/ddz_table_top.png）的平均色，中心略亮、边缘压暗做柔和晕影
- 主角是红牌背（card_cover_red.png）与小王（card_joker_small.png），扇形展开并带投影；
  用红背是为了在小尺寸下有颜色分离（两张白牌会糊成一块）

像素画缩放一律用 NEAREST 保持像素质感，旋转用 BICUBIC 避免锯齿。

用法：  python tools/make_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "common/src/main/resources/assets/crafty_cards/textures"
OUT = ROOT / "common/src/main/resources/logo.png"

SIZE = 512          # 输出边长（平台推荐 >= 128，512 在模组列表里也清晰）
CARD_H = 300        # 单张牌缩放后的高度


def load_card(rel: str) -> Image.Image:
    """读一张牌面贴图并裁到实际画布（贴图右侧大片是透明的）。"""
    im = Image.open(TEX / rel).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def upscale(im: Image.Image, height: int) -> Image.Image:
    w = max(1, round(im.width * height / im.height))
    return im.resize((w, height), Image.NEAREST)


def felt_color() -> tuple[int, int, int]:
    """取牌桌呢面贴图的平均色作为底色，保证与游戏内桌子同色系。"""
    felt = Image.open(TEX / "block/ddz_table_top.png").convert("RGB")
    pixels = list(felt.getdata())
    n = len(pixels)
    return tuple(sum(c[i] for c in pixels) // n for i in range(3))


def radial_background(base: tuple[int, int, int]) -> Image.Image:
    """中心略亮、边缘压暗的径向渐变，让中间的牌更突出。"""
    canvas = Image.new("RGB", (SIZE, SIZE), base)
    # 用一张小图放大做渐变即可，省去逐像素计算
    small = Image.new("RGB", (64, 64))
    px = small.load()
    cx = cy = 31.5
    maxd = (cx**2 + cy**2) ** 0.5
    for y in range(64):
        for x in range(64):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / maxd   # 0 中心 → 1 角落
            k = 1.32 - 0.72 * d                                  # 中心 1.32x、角落 0.60x
            px[x, y] = tuple(max(0, min(255, round(c * k))) for c in base)
    canvas.paste(small.resize((SIZE, SIZE), Image.BICUBIC))
    return canvas


def with_shadow(card: Image.Image, blur: int = 10, offset: int = 10) -> Image.Image:
    """给牌加投影：用 alpha 做黑色剪影、模糊后垫在下方。"""
    pad = blur * 3
    base = Image.new("RGBA", (card.width + pad * 2, card.height + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    silhouette = Image.new("RGBA", base.size, (0, 0, 0, 0))
    silhouette.paste((0, 0, 0, 175), (pad, pad, pad + card.width, pad + card.height), card)
    shadow.alpha_composite(silhouette.filter(ImageFilter.GaussianBlur(blur)))
    base.alpha_composite(shadow)
    base.paste(card, (pad + offset, pad + offset), card)
    return base


def main() -> None:
    base = felt_color()
    canvas = radial_background(base).convert("RGBA")

    # 底层用红牌背、上层用小王：两张白牌在小尺寸（模组列表 64px）会糊成一块，
    # 红背提供颜色分离，"一叠牌"的轮廓在小尺寸下也能读出来
    back = upscale(load_card("item/cards/card_cover_red.png"), CARD_H)
    front = upscale(load_card("item/cards/card_joker_small.png"), CARD_H)

    back = with_shadow(back.rotate(-15, resample=Image.BICUBIC, expand=True))
    front = with_shadow(front.rotate(9, resample=Image.BICUBIC, expand=True))

    # 扇形展开、两张牌重叠，显出一"手牌"
    canvas.alpha_composite(back, (SIZE // 2 - back.width // 2 - 55, SIZE // 2 - back.height // 2 - 5))
    canvas.alpha_composite(front, (SIZE // 2 - front.width // 2 + 60, SIZE // 2 - front.height // 2 + 10))

    # 金色细边框，让图标在深色背景的模组列表里也有轮廓
    draw = ImageDraw.Draw(canvas)
    inset = 10
    draw.rectangle([inset, inset, SIZE - inset - 1, SIZE - inset - 1],
                   outline=(212, 175, 55, 255), width=5)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT, "PNG", optimize=True)
    print(f"已生成 {OUT.relative_to(ROOT)}  {SIZE}x{SIZE}  {OUT.stat().st_size} 字节  底色 {base}")


if __name__ == "__main__":
    main()
