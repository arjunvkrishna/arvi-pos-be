package com.neram.pos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Drops the cached public menus, e.g. after a cafe's name, tagline or logo changed. */
@Component
@RequiredArgsConstructor
public class PublicMenuCache {

    private final CacheManager cacheManager;

    public void evictCurrentCafe() {
        Cache cache = cacheManager.getCache("publicMenu");
        if (cache != null) {
            cache.clear(); // keys are per cafe and branch; clearing is cheap and never leaks across cafes
        }
    }
}
