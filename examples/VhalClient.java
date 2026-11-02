package com.example.vhal;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * VHAL 的最小客户端：反射连接、读写、回读、枚举。
 *
 * 说明：
 * - 全部走反射，避免编译期依赖 android.car 的具体版本；
 * - 需要 android.jar（任意 API 28+）作为编译 classpath；
 * - 属性 ID / area 由调用方从自己车机的枚举结果里取，本类不内置任何 ID。
 */
public class VhalClient {

    private static final long READY_TIMEOUT_MS = 5000;
    private static final long WRITE_SETTLE_MS = 150;

    private Object car;
    private Object property;
    private Object cabin;
    private Object mcu;
    private Object sensor;

    private Method getManager;
    private Method getProperty;
    private Method getPropertyList;
    private Method setInt;
    private Method setFloat;
    private Method setBool;
    private Method setString;
    private Method setBytes;

    private volatile int lastStatus = -1;
    private volatile long lastTimestamp = 0L;

    // ------------------------------------------------------------------
    // 连接
    // ------------------------------------------------------------------

    public static VhalClient connect(Context context) throws Exception {
        VhalClient client = new VhalClient();
        client.connectInternal(context);
        return client;
    }

    private void connectInternal(Context context) throws Exception {
        Class<?> carClass = Class.forName("android.car.Car");
        Method createCar = carClass.getMethod("createCar", Context.class, ServiceConnection.class);
        Method carConnect = carClass.getMethod("connect");
        getManager = carClass.getMethod("getCarManager", String.class);

        ServiceConnection conn = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    property = getManager.invoke(car, "property");
                    cabin = safeManager("cabin");
                    mcu = safeManager("mcu");
                    sensor = safeManager("sensor");
                } catch (Throwable ignored) {
                    // 某个 manager 拿不到不影响其他 manager
                }
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                property = null;
                cabin = null;
                mcu = null;
                sensor = null;
            }
        };

        car = createCar.invoke(null, context, conn);   // createCar 是静态方法
        carConnect.invoke(car);

        // 等 onServiceConnected
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (property == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        if (property == null) {
            throw new IllegalStateException("CarService 未就绪（入口校验未通过或连接超时）");
        }

        prepareMethodRefs(property.getClass());
    }

    private Object safeManager(String name) {
        try {
            return getManager.invoke(car, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void prepareMethodRefs(Class<?> cls) {
        for (Method m : cls.getMethods()) {
            String n = m.getName();
            if ("getProperty".equals(n) && m.getParameterCount() == 2) getProperty = m;
            else if ("getPropertyList".equals(n) && m.getParameterCount() == 0) getPropertyList = m;
            else if ("setIntProperty".equals(n) && m.getParameterCount() == 3) setInt = m;
            else if ("setFloatProperty".equals(n) && m.getParameterCount() == 3) setFloat = m;
            else if ("setBooleanProperty".equals(n) && m.getParameterCount() == 3) setBool = m;
            else if ("setStringProperty".equals(n) && m.getParameterCount() == 3) setString = m;
            else if ("setBytesProperty".equals(n) && m.getParameterCount() == 3) setBytes = m;
        }
    }

    public boolean isConnected() {
        return property != null;
    }

    /**
     * 熄火/休眠后 Binder 可能"假连接"。读写抛异常时调用它重建连接。
     */
    public void reconnect(Context context) throws Exception {
        try {
            Method disconnect = car.getClass().getMethod("disconnect");
            disconnect.invoke(car);
        } catch (Throwable ignored) { }
        property = null;
        cabin = null;
        mcu = null;
        sensor = null;
        connectInternal(context);
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /**
     * 万能读法：返回原始值（Integer/Float/Boolean/String/byte[]/int[]）。
     * 类型不确定时先用它，看 lastType() 再决定后续用哪个 getXxx。
     */
    public Object read(int propertyId, int areaId) throws Exception {
        ensureReady();
        Object value = getProperty.invoke(property, propertyId, areaId);
        lastStatus = (Integer) invoke(value, "getStatus", -1);
        lastTimestamp = (Long) invoke(value, "getTimestamp", 0L);
        return invoke(value, "getValue", null);
    }

    public int lastStatus() {
        return lastStatus;
    }

    public long lastTimestamp() {
        return lastTimestamp;
    }

    public static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    public boolean writeInt(int id, int area, int value) throws Exception {
        ensureReady();
        if (setInt == null) return false;
        setInt.invoke(property, id, area, value);
        return true;
    }

    public boolean writeFloat(int id, int area, float value) throws Exception {
        ensureReady();
        if (setFloat == null) return false;
        setFloat.invoke(property, id, area, value);
        return true;
    }

    public boolean writeBoolean(int id, int area, boolean value) throws Exception {
        ensureReady();
        if (setBool == null) return false;
        setBool.invoke(property, id, area, value);
        return true;
    }

    /**
     * 写入 + 回读验证。返回 true 表示回读值与目标一致。
     */
    public boolean writeIntAndVerify(int id, int area, int target) throws Exception {
        writeInt(id, area, target);
        Thread.sleep(WRITE_SETTLE_MS);
        Object after = read(id, area);
        return after instanceof Integer && ((Integer) after) == target;
    }

    public boolean writeFloatAndVerify(int id, int area, float target, float tolerance)
            throws Exception {
        writeFloat(id, area, target);
        Thread.sleep(WRITE_SETTLE_MS);
        Object after = read(id, area);
        return after instanceof Float
                && Math.abs(((Float) after) - target) <= tolerance;
    }

    // ------------------------------------------------------------------
    // 枚举
    // ------------------------------------------------------------------

    /**
     * 枚举全部属性，输出 CSV。
     * 输出的是"你这台车"的属性集合，不是任何固定表。
     */
    public int dumpProperties(String outputPath) throws Exception {
        ensureReady();
        if (getPropertyList == null) {
            throw new IllegalStateException("当前 CarPropertyManager 不支持 getPropertyList");
        }

        List<?> list = (List<?>) getPropertyList.invoke(property);
        StringBuilder sb = new StringBuilder();
        sb.append("property_id_hex,property_id_dec,access,areas,change_mode,min,max\n");

        int count = 0;
        for (Object config : list) {
            int id = (Integer) invoke(config, "getPropertyId", 0);
            int access = (Integer) invoke(config, "getAccess", 0);
            int[] areas = (int[]) invoke(config, "getAreaIds", new int[0]);
            int mode = (Integer) invoke(config, "getChangeMode", 0);
            int firstArea = areas != null && areas.length > 0 ? areas[0] : 0;
            Object min = invokeWithArea(config, "getMinValue", firstArea);
            Object max = invokeWithArea(config, "getMaxValue", firstArea);

            sb.append(String.format("0x%08X,%d,%d,%s,%d,%s,%s%n",
                    id, id, access, join(areas), mode, str(min), str(max)));
            count++;
        }

        File file = new File(outputPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return count;
    }

    /**
     * 探测某个 ID 所有 area 的当前类型与值（只读）。
     */
    public void probeAreas(int propertyId, int[] areas) {
        if (areas == null) return;
        for (int area : areas) {
            try {
                Object v = read(propertyId, area);
                System.out.println(String.format(
                        "id=0x%08X area=%d type=%s value=%s status=%d",
                        propertyId, area, typeName(v), str(v), lastStatus));
            } catch (Throwable t) {
                System.out.println(String.format(
                        "id=0x%08X area=%d read failed: %s",
                        propertyId, area, t.getClass().getSimpleName()));
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private void ensureReady() {
        if (property == null) {
            throw new IllegalStateException("CarService 未连接");
        }
    }

    private static Object invoke(Object target, String method, Object fallback) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Object invokeWithArea(Object target, String method, int area) {
        try {
            Method m = target.getClass().getMethod(method, int.class);
            return m.invoke(target, area);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String join(int[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static String str(Object value) {
        if (value == null) return "";
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02X", b));
            return sb.toString();
        }
        if (value instanceof int[]) {
            return join((int[]) value);
        }
        return String.valueOf(value);
    }
}