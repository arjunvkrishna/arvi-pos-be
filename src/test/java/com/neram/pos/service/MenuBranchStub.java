package com.neram.pos.service;

import com.neram.pos.repository.MenuItemBranchSettingRepository;

import static org.mockito.Mockito.mock;

/** A MenuBranchService for tests that don't care about branches: no branch is selected, so the cafe-wide menu applies. */
final class MenuBranchStub {
    private MenuBranchStub() {
    }

    static MenuBranchService cafeWide() {
        return new MenuBranchService(mock(MenuItemBranchSettingRepository.class));
    }
}
