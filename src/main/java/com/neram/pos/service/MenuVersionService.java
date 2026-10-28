package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A short fingerprint of everything a screen shows about the menu: dishes, categories, option groups, combos and the
 * caller's branch prices. The apps ask for it every few seconds and reload the menu only when it changes, so a price or
 * dish edited on one device shows on the others within seconds without anyone refreshing. It is worked out from the
 * data itself, so every way of changing the menu (screens, price import, SQL) is covered.
 * <p>
 * It is remembered for 2 seconds per cafe and branch, so many devices asking at once cost one query. The memory is keyed
 * by cafe: one cafe's fingerprint is never served to another.
 */
@Service
@RequiredArgsConstructor
public class MenuVersionService {

    static final long REMEMBER_MS = 2000;
    /** Bounds the memory held for cafes that stopped asking; the map refills itself in seconds. */
    private static final int MAX_REMEMBERED = 5000;

    private static final String SQL = """
            SELECT left(md5(
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM menu_items t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM categories t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM modifier_groups t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM modifiers t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.menu_item_id, t.modifier_group_id) FROM menu_item_modifier_groups t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM combo_groups t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM combo_group_items t), '') || '|' ||
                coalesce((SELECT string_agg(t::text, ';' ORDER BY t.id) FROM menu_item_branch_settings t
                          WHERE ?::bigint IS NULL OR t.outlet_id = ?::bigint), '')
            ), 12)
            """;

    private record Remembered(String version, long computedAt) {
    }

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Remembered> remembered = new ConcurrentHashMap<>();

    public String current() {
        Long outlet = OutletContext.currentIdOrNull();
        String key = TenantContext.current().map(TenantInfo::id).map(String::valueOf).orElse("-") + ":" + outlet;
        long now = System.currentTimeMillis();
        Remembered hit = remembered.get(key);
        if (hit != null && now - hit.computedAt() < REMEMBER_MS) {
            return hit.version();
        }
        if (remembered.size() > MAX_REMEMBERED) {
            remembered.clear();
        }
        // Not synchronized across cafes: a burst for one cafe must not make another cafe's devices wait.
        String fresh = jdbc.queryForObject(SQL, String.class, outlet, outlet);
        remembered.put(key, new Remembered(fresh, System.currentTimeMillis()));
        return fresh;
    }
}
