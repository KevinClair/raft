package com.github.kevin.raft.core;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地缓存，模拟raft在分布式存储下的数据
 * <p>
 * 该类用于存储本地缓存
 * </p>
 */
public class LocalCache {

    private final Map<String, String> cache = new HashMap<>();

    public static LocalCache getInstance() {
        return LocalCacheHolder.INSTANCE;
    }

    private static class LocalCacheHolder {
        private static final LocalCache INSTANCE = new LocalCache();
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 值
     */
    public String get(String key) {
        return cache.get(key);
    }

    /**
     * 设置缓存
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        cache.put(key, value);
    }

    /**
     * 删除缓存
     *
     * @param key 键
     */
    public void remove(String key) {
        cache.remove(key);
    }
}
