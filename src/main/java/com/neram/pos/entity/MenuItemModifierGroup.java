package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "menu_item_modifier_groups")
@IdClass(MenuItemModifierGroup.PK.class)
public class MenuItemModifierGroup {

    @Id
    @Column(name = "menu_item_id")
    private Long menuItemId;

    @Id
    @Column(name = "modifier_group_id")
    private Long modifierGroupId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private Long menuItemId;
        private Long modifierGroupId;
    }
}
