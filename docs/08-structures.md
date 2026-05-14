# 可建造结构

Steve 可以通过程序化生成以下结构。使用 `build` 命令指定结构类型。

## 结构列表

| 结构类型 | 别名 | 默认尺寸 | 材料 | 说明 |
|---------|------|---------|------|------|
| `house` | `home` | 9x6x9 | 橡木板、圆石、玻璃板 | 带窗户、门和金字塔屋顶的房屋 |
| `castle` | `catle`, `fort` | 14x10x14 | 石砖、圆石、玻璃板 | 带角楼、城垛和大门的城堡 |
| `tower` | — | 6x6x16 | 石砖、錾制石砖、玻璃板、深色橡木楼梯 | 带窗户和金字塔顶的塔楼 |
| `barn` | `shed` | 12x8x14 | 橡木板、橡木原木、云杉木板 | 带大门和尖顶的谷仓 |
| `modern` | `modern_house` | 9x6x9 | 石英块、平滑石头、玻璃、深色橡木板 | 大量玻璃的现代风格房屋 |
| `wall` | — | 用户指定 | 使用第一个材料 | 单层墙壁 |
| `platform` | — | 用户指定 | 使用第一个材料 | 平台/地板 |
| `box` | `cube` | 用户指定 | 使用第一个材料 | 实心方块 |

## 使用方式

```
build house
build castle
build tower
build barn
build modern
build wall
build platform
build box
```

## 材料说明

- `house` — 地板用材料1，墙壁用材料2，屋顶用材料3，窗户固定为玻璃板，门固定为橡木门
- `castle` — 固定使用石砖（地板）、圆石（墙壁）、玻璃板（窗户）
- `tower` — 固定使用石砖、錾制石砖、玻璃板、深色橡木楼梯
- `barn` — 固定使用橡木板、橡木原木、云杉木板
- `modern` — 固定使用石英块、平滑石头、玻璃、深色橡木板
- `wall`/`platform`/`box` — 使用用户指定的材料

## 自定义尺寸

```
build house with dimensions 12x8x12
build castle with width 20 height 15 depth 20
```

默认尺寸为程序化生成的推荐值。NBT 模板（house, oldhouse, powerplant）使用自动尺寸。

## NBT 模板

除程序化生成外，还支持从 NBT 模板文件加载结构：

- 放置 `.nbt` 文件到 `structures/` 目录
- 文件名即为结构名（如 `house.nbt`）
- 优先使用 NBT 模板，找不到时回退到程序化生成
