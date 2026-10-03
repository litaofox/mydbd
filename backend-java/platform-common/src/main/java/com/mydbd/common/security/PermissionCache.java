package com.mydbd.common.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 登录主体（权限集/数据范围）进程内短缓存。
 * TTL 120s 兜底；用户/角色/授权写操作后主动 evict，使权限调整实际立即生效。
 */
@Component
public class PermissionCache {

    private static final long TTL_MS = 120_000L;

    private record Entry(UserInfo info, long expireAt) {
        boolean alive() {
            return System.currentTimeMillis() < expireAt;
        }
    }

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public UserInfo getOrLoad(Long userId, Supplier<UserInfo> loader) {
        if (userId == null) {
            return null;
        }
        Entry hit = cache.get(userId);
        if (hit != null && hit.alive()) {
            return hit.info();
        }
        UserInfo loaded = loader.get();
        if (loaded != null) {
            cache.put(userId, new Entry(loaded, System.currentTimeMillis() + TTL_MS));
        } else {
            cache.remove(userId);
        }
        return loaded;
    }

    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void evictAll() {
        cache.clear();
    }
}
