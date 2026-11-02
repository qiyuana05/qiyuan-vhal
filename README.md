# 长安启源车机 · VHAL 读写方法

> 不提供"某某属性是什么"的固定表——**每台车、每个年款、每次 OTA 都可能不一样**。
> 这个仓库给的是：VHAL 在哪一层、怎么枚举你自己车上的属性、怎么读、怎么写、
> 怎么确认写成功。
>
> **适用背景**：长安启源 C589 PHEV / 启源 OS（Android 9 / API 28）。
> 但方法本身对 Android Automotive 的 VHAL 是通用的。

---

## 这个仓库解决什么问题

网上常见的 VHAL 教程通常给你一张属性 ID 表，但换一台车就失效了。
本仓库反过来：

1. 先讲清楚一次读写经过哪几层；
2. 教你在自己的车上**枚举**出完整的属性列表；
3. 教你根据枚举结果判断每个属性该怎么读、能不能写；
4. 给出可复用的连接、重试、回读代码。

**属性 ID、area、取值、单位，全部由你自己从车上抓。** 仓库只给方法和示例。

---

## 四步流程

```
① 了解分层         →  docs/00
② 枚举属性         →  docs/01 + examples/EnumProperties.java
③ 读取与识别类型   →  docs/02 + examples/VhalClient.java
④ 写入与回读验证   →  docs/03
```

权限前提（为什么普通应用能读写、门槛在哪）见 [docs/04](docs/04-权限-为什么普通应用能读写.md)。

---

## 文档导航

| 文档 | 内容 |
|---|---|
| [docs/00-背景-一次读写经过哪些层.md](docs/00-背景-一次读写经过哪些层.md) | App → CarService → VHAL → MCU/CAN，每层负责什么 |
| [docs/01-发现-枚举你这台车的属性.md](docs/01-发现-枚举你这台车的属性.md) | 用 `getPropertyList` 导出全部属性，怎么读结果 |
| [docs/02-读取-方法与类型识别.md](docs/02-读取-方法与类型识别.md) | 六种取值方法、万能 `getProperty`、怎么判断类型 |
| [docs/03-写入-怎么确认写成功.md](docs/03-写入-怎么确认写成功.md) | 写入口、area、值域、回读验证、失败判断 |
| [docs/04-权限-为什么普通应用能读写.md](docs/04-权限-为什么普通应用能读写.md) | CarService 入口校验的原理与边界 |

## 示例代码

| 文件 | 用途 |
|---|---|
| [examples/VhalClient.java](examples/VhalClient.java) | 反射连接、断线重连、读、写、回读 |
| [examples/EnumProperties.java](examples/EnumProperties.java) | 枚举全部属性并输出 CSV（你自己车的 CSV） |
| [examples/README.md](examples/README.md) | 编译与运行说明 |

---

## 快速开始

```bash
# 1) 把两个 Java 文件放进你的车机项目
# 2) 在应用里调用 VhalClient.connect(context)
# 3) 枚举属性
VhalClient client = VhalClient.connect(context);
client.dumpProperties("/sdcard/vhal_properties.csv");

# 4) 读一个属性（ID 换成你自己枚举出来的）
Object v = client.read(0x00000000, 0);
int status = client.lastStatus();

# 5) 写一个属性，并回读确认
boolean ok = client.writeInt(propId, areaId, value);
Object after = client.read(propId, areaId);
```

完整说明见 [examples/README.md](examples/README.md)。

---

## 仓库结构

```
qiyuan-vhal/
├── README.md
├── docs/
│   ├── 00-背景-一次读写经过哪些层.md
│   ├── 01-发现-枚举你这台车的属性.md
│   ├── 02-读取-方法与类型识别.md
│   ├── 03-写入-怎么确认写成功.md
│   └── 04-权限-为什么普通应用能读写.md
├── examples/
│   ├── README.md
│   ├── VhalClient.java
│   └── EnumProperties.java
├── DISCLAIMER.md
├── SECURITY.md
├── LICENSE
└── .gitignore
```

---

## 免责声明（摘要）

- 仅在你拥有或获明确授权的车辆上操作；
- 属性 ID、area、值域因车型/年款/OTA 而异，照抄别人的值可能无效或造成异常；
- 写入车辆属性可能影响车辆功能，**动力、制动、转向、电源相关属性不要写**；
- 作者不对任何损失负责。完整条款见 [DISCLAIMER.md](DISCLAIMER.md)。

## 许可证

- 文档：CC BY-NC-SA 4.0
- 示例代码：MIT

> 文中提及的第三方名称、协议归各自权利人所有。本仓库不包含任何原始 dumpsys、
> 实车日志、属性数据表或车辆身份信息。