# 示例代码

> 两个文件，覆盖"连接 → 枚举 → 读 → 写 → 回读"。
>
> **不含任何具体属性 ID。** 所有 ID / area 都由你从自己车机的枚举结果里取。

## 文件

| 文件 | 用途 |
|---|---|
| `VhalClient.java` | 反射连接 CarService，读、写、回读、枚举、重连 |
| `EnumProperties.java` | 调用 `dumpProperties` 输出 CSV，并做简单统计 |

## 编译

这两个类依赖 Android 的 `Context` / `ServiceConnection` / `CarPropertyValue`，
需要 Android SDK 的 `android.jar` 作为 classpath：

```bash
# 用 javac 编译（不打包 APK，只做语法/类型检查）
javac -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_HOME/platforms/android-34/android.jar" \
  -d out \
  VhalClient.java EnumProperties.java
```

放进你的车机项目时，把 `package com.example.vhal;` 改成你自己的包名即可。

> 全反射写法的好处是不用依赖某个具体版本的 `android.car` 类；
> 代价是编译期检查少，运行时异常要靠日志排查。

## 使用

```java
import com.example.vhal.VhalClient;
import com.example.vhal.EnumProperties;

// 1) 枚举：输出你这台车的属性表
new EnumProperties().run(context, "/sdcard/vhal_properties.csv");

// 2) 连接
VhalClient client = VhalClient.connect(context);

// 3) 读（ID 从上面的 CSV 里选）
Object value = client.read(propId, areaId);
System.out.println("type=" + VhalClient.typeName(value)
        + " status=" + client.lastStatus());

// 4) 写 + 回读
boolean ok = client.writeIntAndVerify(propId, areaId, targetValue);

// 5) 多 area 属性逐个探测
client.probeAreas(propId, new int[]{1, 2, 4, 15});
```

## 使用顺序

1. 先跑 `EnumProperties`，拿到 CSV；
2. 在 CSV 里按 `access`、`areas`、`min/max` 筛候选；
3. 用 `probeAreas` 只读探测；
4. 确认语义后，再用 `writeIntAndVerify`；
5. 写入后一定看返回值，不要只看有没有抛异常。

## 注意

- 熄火/休眠后连接可能失效，调用 `client.reconnect(context)`；
- `status != 0` 时不要使用读到的值；
- `min == max == 0` 通常意味着不可写；
- 写入前先记录原值，准备恢复；
- 不要对动力、制动、转向、电源相关属性做写入实验。

## 不包含

本目录不提供：

- 任何具体属性 ID / area 对照表；
- 实车 dumpsys / 日志 / CSV 数据；
- 白名单进程名、包名、证书；
- 一键读写工具。

这些都需要你自己从车上抓，原因见 [README](../README.md)。