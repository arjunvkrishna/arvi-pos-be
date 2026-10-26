package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.entity.MenuItem;
import com.neram.pos.entity.MenuItemBranchSetting;
import com.neram.pos.repository.MenuItemBranchSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** "Sold out" or a different price in one branch must never change what another branch sells. */
class MenuBranchServiceTest {

    private MenuItemBranchSettingRepository repo;
    private MenuBranchService service;
    private final List<MenuItemBranchSetting> rows = new ArrayList<>();
    private MenuItem latte;

    @BeforeEach
    void setUp() {
        repo = mock(MenuItemBranchSettingRepository.class);
        service = new MenuBranchService(repo);
        latte = new MenuItem();
        latte.setId(1L);
        latte.setPrice(new BigDecimal("150.00"));
        latte.setIsAvailable(true);
        latte.setActive(true);
        when(repo.findByOutletId(any())).thenAnswer(i -> rows.stream().filter(r -> r.getOutletId().equals(i.getArgument(0))).toList());
        when(repo.findByMenuItemIdAndOutletId(any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getMenuItemId().equals(i.getArgument(0)) && r.getOutletId().equals(i.getArgument(1))).findFirst());
        when(repo.save(any(MenuItemBranchSetting.class))).thenAnswer(i -> {
            MenuItemBranchSetting s = i.getArgument(0);
            if (!rows.contains(s)) rows.add(s);
            return s;
        });
        doAnswer(i -> { rows.remove((MenuItemBranchSetting) i.getArgument(0)); return null; }).when(repo).delete(any(MenuItemBranchSetting.class));
    }

    @AfterEach
    void clear() {
        OutletContext.clear();
    }

    private MenuBranchService.Effective seenFrom(Long branch) {
        return OutletContext.callAs(branch, () -> service.effective(latte));
    }

    @Test
    void soldOutInOneBranchStaysAvailableInTheOther() {
        OutletContext.runAs(1L, () -> assertFalse(service.toggleAvailability(latte, "mgr1")));

        assertFalse(seenFrom(1L).available());
        assertTrue(seenFrom(2L).available());
        assertTrue(seenFrom(null).available(), "the cafe-wide default is untouched");
        assertTrue(latte.getIsAvailable());
    }

    @Test
    void aBranchCanSellAtItsOwnPriceWithoutChangingOthers() {
        OutletContext.runAs(2L, () -> service.update(latte, new BigDecimal("175"), false, null, "mgr2"));

        assertEquals(new BigDecimal("175.00"), seenFrom(2L).price());
        assertEquals(new BigDecimal("150.00"), seenFrom(1L).price());
        assertEquals(new BigDecimal("150.00"), seenFrom(null).price());
    }

    @Test
    void aBranchCanReOpenAnItemTheCafeMarkedSoldOut() {
        latte.setIsAvailable(false);
        assertFalse(seenFrom(1L).available());

        OutletContext.runAs(1L, () -> service.update(latte, null, false, true, "mgr1"));

        assertTrue(seenFrom(1L).available());
        assertFalse(seenFrom(2L).available());
    }

    @Test
    void anItemRemovedFromTheMenuIsNeverAvailableAnywhere() {
        OutletContext.runAs(1L, () -> service.update(latte, null, false, true, "mgr1"));
        latte.setActive(false);
        assertFalse(seenFrom(1L).available());
    }

    @Test
    void clearingThePriceAndAvailabilityRemovesTheOverride() {
        OutletContext.runAs(1L, () -> {
            service.update(latte, new BigDecimal("99"), false, null, "m");
            assertEquals(1, rows.size());
            service.update(latte, null, true, null, "m");
        });
        assertTrue(rows.isEmpty());
        assertEquals(new BigDecimal("150.00"), seenFrom(1L).price());
    }

    @Test
    void branchSettingsNeedABranchAndRejectNegativePrices() {
        assertThrows(IllegalStateException.class, () -> service.toggleAvailability(latte, "owner"));
        assertThrows(IllegalStateException.class, () -> service.update(latte, BigDecimal.ONE, false, null, "owner"));
        OutletContext.runAs(1L, () ->
                assertThrows(IllegalArgumentException.class, () -> service.update(latte, new BigDecimal("-1"), false, null, "m")));
    }

    @Test
    void aUserWithoutABranchGetsTheCafeWideMenu() {
        OutletContext.runAs(OutletContext.NO_OUTLET, () -> assertEquals(Optional.empty(), service.branch()));
    }
}
