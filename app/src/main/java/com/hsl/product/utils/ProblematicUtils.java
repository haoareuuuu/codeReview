package com.hsl.product.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Utility class with various problematic implementations
 */
public class ProblematicUtils {
    
    // 问题1: 公开静态变量
    public static String API_KEY = "sk_live_51HV9bvJBcm7jDQEIvzlVQNqQJzQXOIyNVXBRZeX";
    
    // 问题2: 硬编码TAG
    private static final String TAG = "ProblematicUtils";
    
    // 问题3: 使用MD5进行密码哈希（不安全）
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing password", e);
            return password; // 问题4: 失败时返回原始密码
        }
    }
    
    // 问题5: 不安全的文件操作
    public static void saveDataToFile(Context context, String data) {
        try {
            // 问题6: 使用外部存储而不检查权限
            File file = new File(Environment.getExternalStorageDirectory(), "app_data.txt");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data.getBytes());
            fos.close();
        } catch (IOException e) {
            // 问题7: 空catch块
        }
    }
    
    // 问题8: 不安全的SQL查询
    public static List<String> queryDatabase(SQLiteDatabase db, String userInput) {
        // 问题9: SQL注入风险
        String query = "SELECT * FROM users WHERE name LIKE '%" + userInput + "%'";
        Cursor cursor = db.rawQuery(query, null);
        
        List<String> results = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                // 问题10: 硬编码列索引
                String name = cursor.getString(1);
                results.add(name);
            } while (cursor.moveToNext());
        }
        
        // 问题11: 未关闭Cursor
        return results;
    }
    
    // 问题12: 低效的数据结构使用
    public static boolean containsValue(List<String> list, String value) {
        // 问题13: O(n)复杂度，应使用HashSet
        for (String item : list) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
    
    // 问题14: 内存低效
    public static String[] generateLargeArray() {
        // 问题15: 创建大数组但不考虑内存限制
        String[] array = new String[1000000];
        for (int i = 0; i < array.length; i++) {
            array[i] = "Item " + i;
        }
        return array;
    }
    
    // 问题16: 线程不安全
    private static Map<String, Object> cache = new HashMap<>();
    
    // 问题17: 没有同步的共享资源访问
    public static void addToCache(String key, Object value) {
        cache.put(key, value);
    }
    
    public static Object getFromCache(String key) {
        return cache.get(key);
    }
    
    // 问题18: 随机数生成不安全
    private static Random random = new Random();
    
    // 问题19: 可预测的随机数，不适合安全用途
    public static int generateRandomToken() {
        return random.nextInt(10000);
    }
    
    // 问题20: 递归没有基本情况
    public static int factorial(int n) {
        // 问题21: 可能导致栈溢出
        return n * factorial(n - 1);
    }
    
    // 问题22: 低效字符串连接
    public static String concatenateStrings(List<String> strings) {
        // 问题23: 应使用StringBuilder
        String result = "";
        for (String s : strings) {
            result += s;
        }
        return result;
    }
    
    // 问题24: 不必要的对象创建
    public static int sumOfIntegers(int[] numbers) {
        int sum = 0;
        for (int i = 0; i < numbers.length; i++) {
            // 问题25: 每次迭代创建新的Integer对象
            Integer num = new Integer(numbers[i]);
            sum += num;
        }
        return sum;
    }
}
