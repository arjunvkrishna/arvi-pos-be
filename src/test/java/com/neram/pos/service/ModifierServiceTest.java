package com.neram.pos.service;

import com.neram.pos.dto.ModifierGroupDTO;
import com.neram.pos.entity.Modifier;
import com.neram.pos.entity.ModifierGroup;
import com.neram.pos.repository.MenuItemModifierGroupRepository;
import com.neram.pos.repository.MenuItemRepository;
import com.neram.pos.repository.ModifierGroupRepository;
import com.neram.pos.repository.ModifierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Editing a group keeps each option's id (bills already rung up point at it); removed options are switched off, not deleted. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModifierServiceTest {
    @Mock private ModifierGroupRepository groups;
    @Mock private ModifierRepository modifiers;
    @Mock private MenuItemModifierGroupRepository links;
    @Mock private MenuItemRepository menu;

    private ModifierService svc;
    private ModifierGroup group;
    private Modifier oat;
    private Modifier soy;

    private static Modifier option(long id, String name, String price) {
        return Modifier.builder().id(id).name(name).priceAdjustment(new BigDecimal(price)).isDefault(false).isActive(true).sortOrder(0).inventoryQuantity(BigDecimal.ZERO).build();
    }

    @BeforeEach
    void setUp() {
        svc = new ModifierService(groups, modifiers, links, menu);
        group = ModifierGroup.builder().id(1L).name("Milk").minSelections(0).maxSelections(1).isActive(true).modifiers(new ArrayList<>()).build();
        oat = option(11, "Oat", "30");
        soy = option(12, "Soy", "20");
        oat.setGroup(group);
        soy.setGroup(group);
        group.getModifiers().add(oat);
        group.getModifiers().add(soy);
        when(groups.findById(1L)).thenReturn(Optional.of(group));
        when(groups.save(any(ModifierGroup.class))).thenAnswer(i -> i.getArgument(0));
        when(modifiers.findByGroupIdAndIsActiveTrueOrderBySortOrderAsc(1L)).thenAnswer(i -> group.getModifiers().stream().filter(m -> Boolean.TRUE.equals(m.getIsActive())).toList());
    }

    private static ModifierGroupDTO.ModifierDTO dto(Long id, String name, String price) {
        return ModifierGroupDTO.ModifierDTO.builder().id(id).name(name).priceAdjustment(new BigDecimal(price)).build();
    }

    private ModifierGroupDTO request(List<ModifierGroupDTO.ModifierDTO> mods) {
        return ModifierGroupDTO.builder().name("Milk").minSelections(0).maxSelections(1).modifiers(mods).build();
    }

    @Test
    void anExistingOptionKeepsItsIdWhenItsPriceChanges() {
        svc.updateGroup(1L, request(List.of(dto(11L, "Oat", "35"), dto(12L, "Soy", "20"))));
        assertEquals(11L, oat.getId());
        assertEquals(0, new BigDecimal("35").compareTo(oat.getPriceAdjustment()));
        assertEquals(2, group.getModifiers().size());
    }

    @Test
    void anOptionLeftOutIsSwitchedOffNotDeleted() {
        svc.updateGroup(1L, request(List.of(dto(11L, "Oat", "30"))));
        assertTrue(oat.getIsActive());
        assertFalse(soy.getIsActive());
        assertTrue(group.getModifiers().contains(soy), "kept so old bills can still find it");
    }

    @Test
    void aNewOptionIsAddedWithoutDisturbingTheOthers() {
        svc.updateGroup(1L, request(List.of(dto(11L, "Oat", "30"), dto(12L, "Soy", "20"), dto(null, "Almond", "40"))));
        assertEquals(3, group.getModifiers().size());
        assertTrue(oat.getIsActive() && soy.getIsActive());
    }

    @Test
    void groupNeedsANameAndSaneLimits() {
        assertThrows(IllegalArgumentException.class, () -> svc.createGroup(ModifierGroupDTO.builder().name(" ").build()));
        assertThrows(IllegalArgumentException.class, () -> svc.createGroup(ModifierGroupDTO.builder().name("X").minSelections(3).maxSelections(2).build()));
        assertThrows(IllegalArgumentException.class, () -> svc.createGroup(ModifierGroupDTO.builder().name("X").minSelections(-1).maxSelections(2).build()));
    }

    @Test
    void anOptionThatUsesStockMustSayWhichItem() {
        ModifierGroupDTO.ModifierDTO m = dto(null, "Extra sugar", "5");
        m.setInventoryQuantity(new BigDecimal("2"));
        assertThrows(IllegalArgumentException.class, () -> svc.createGroup(request(List.of(m))));
    }
}
