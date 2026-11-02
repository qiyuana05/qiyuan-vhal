package com.example.vhal;

import android.content.Context;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 枚举当前车机的 VHAL 属性，输出 CSV 并打印简单统计。
 *
 * 用法（在你的车机项目里）：
 *   new EnumProperties().run(context, "/sdcard/vhal_properties.csv");
 *
 * 输出是"你这台车"的属性集合，不是通用表。
 * 换车、换 OTA 版本后请重新跑一遍。
 */
public class EnumProperties {

    public void run(Context context, String outputPath) throws Exception {
        VhalClient client = VhalClient.connect(context);
        try {
            int count = client.dumpProperties(outputPath);
            System.out.println("已枚举属性数: " + count);
            System.out.println("输出文件: " + outputPath);

            Stats stats = analyze(outputPath);
            System.out.println("总属性: " + stats.total);
            System.out.println("只读: " + stats.readOnly);
            System.out.println("可写: " + stats.writable);
            System.out.println("读写: " + stats.readWrite);
            System.out.println("多 area: " + stats.multiArea);
            System.out.println("min==max==0（可能不可写）: " + stats.zeroRange);
        } finally {
            // 如果 VhalClient 提供了 close()，在这里调用
        }
    }

    /**
     * 简单统计 CSV。只做只读分析，不碰车机。
     */
    public Stats analyze(String csvPath) throws Exception {
        Stats s = new Stats();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length < 7) continue;
                s.total++;
                int access = parseInt(parts[2]);
                if (access == 1) s.readOnly++;
                else if (access == 2) s.writable++;
                else if (access == 3) s.readWrite++;
                if (parts[3].contains("|")) s.multiArea++;
                if ("0".equals(parts[5].trim()) && "0".equals(parts[6].trim())) {
                    s.zeroRange++;
                }
            }
        }
        return s;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static class Stats {
        public int total;
        public int readOnly;
        public int writable;
        public int readWrite;
        public int multiArea;
        public int zeroRange;
    }
}