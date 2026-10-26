package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.MenuItemBranchSetting;
import com.neram.pos.repository.MenuItemBranchSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-branch menu: price and availability of a shared item as seen from one branch. "Sold out" or a different price in
 * one branch never touches another branch. With no branch in context (whole-cafe view, background jobs) the
 * cafe-wide values apply.
 */
@Service
@RequiredArgsConstructor
public class MenuBranchService {

    private final MenuItemBranchSettingRepository repository;

    /** The branch in context, or empty for the whole-cafe view. */
    public Optional<Long> branch() {
        return OutletContext.current().filter(id -> id != OutletContext.NO_OUTLET);
    }

    /** All overrides of the branch in context, by menu item id (empty map in the whole-cafe view). */
    @Transactional(readOnly = true)
    public Map<Long, MenuItemBranchSetting> overrides() {
        Optional<Long> branch = branch();
        if (branch.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, MenuItemBranchSetting> out = new HashMap<>();
        for (MenuItemBranchSetting s : repository.findByOutletId(branch.get())) {
            out.put(s.getMenuItemId(), s);
        }
        return out;
    }

    public static BigDecimal priceOf(MenuItem item, Map<Long, MenuItemBranchSetting> overrides) {
        MenuItemBranchSetting o = overrides.get(item.getId());
        return o != null && o.getPrice() != null ? o.getPrice() : item.getPrice();
    }

    public static boolean availableIn(MenuItem item, Map<Long, MenuItemBranchSetting> overrides) {
        MenuItemBranchSetting o = overrides.get(item.getId());
        Boolean value = o != null && o.getIsAvailable() != null ? o.getIsAvailable() : item.getIsAvailable();
        return Boolean.TRUE.equals(value) && !Boolean.FALSE.equals(item.getActive());
    }

    /** Effective price/availability of one item for the branch in context. */
    @Transactional(readOnly = true)
    public Effective effective(MenuItem item) {
        Map<Long, MenuItemBranchSetting> overrides = branch()
                .flatMap(b -> repository.findByMenuItemIdAndOutletId(item.getId(), b))
                .map(s -> Map.of(item.getId(), s)).orElse(Map.of());
        return new Effective(priceOf(item, overrides), availableIn(item, overrides));
    }

    public record Effective(BigDecimal price, boolean available) {
    }

    /** Sold out here <-> available here, for this branch only. */
    @Transactional
    public boolean toggleAvailability(MenuItem item, String actor) {
        long branch = requireBranch();
        MenuItemBranchSetting s = settingFor(item.getId(), branch);
        boolean current = availableIn(item, Map.of(item.getId(), s));
        s.setIsAvailable(!current);
        stamp(s, actor);
        repository.save(s);
        return !current;
    }

    /**
     * Sets this branch's own price and/or availability. A null argument leaves that field as it is; {@code clearPrice}
     * goes back to the cafe-wide price.
     */
    @Transactional
    public MenuItemBranchSetting update(MenuItem item, BigDecimal price, boolean clearPrice, Boolean available, String actor) {
        long branch = requireBranch();
        if (price != null && price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        MenuItemBranchSetting s = settingFor(item.getId(), branch);
        if (clearPrice) {
            s.setPrice(null);
        } else if (price != null) {
            s.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        }
        if (available != null) {
            s.setIsAvailable(available);
        }
        stamp(s, actor);
        if (s.getPrice() == null && s.getIsAvailable() == null) {
            repository.delete(s);
            return s;
        }
        return repository.save(s);
    }

    /** Drops every override of the branch for this item (back to the cafe-wide price and availability). */
    @Transactional
    public void reset(Long menuItemId) {
        repository.findByMenuItemIdAndOutletId(menuItemId, requireBranch()).ifPresent(repository::delete);
    }

    private long requireBranch() {
        return branch().orElseThrow(() -> new IllegalStateException(
                "Select a branch first: branch prices and availability are set for one branch at a time."));
    }

    private MenuItemBranchSetting settingFor(Long menuItemId, long branch) {
        return repository.findByMenuItemIdAndOutletId(menuItemId, branch)
                .orElseGet(() -> MenuItemBranchSetting.builder().menuItemId(menuItemId).outletId(branch).build());
    }

    private static void stamp(MenuItemBranchSetting s, String actor) {
        s.setUpdatedBy(actor);
        s.setUpdatedAt(java.time.LocalDateTime.now());
    }
}
