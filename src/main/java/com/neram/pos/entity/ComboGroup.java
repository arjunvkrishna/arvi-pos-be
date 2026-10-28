package com.neram.pos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "combo_groups")
public class ComboGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "combo_menu_item_id", nullable = false)
    private Long comboMenuItemId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "min_choices", nullable = false)
    @Builder.Default
    private Integer minChoices = 1;

    @Column(name = "max_choices", nullable = false)
    @Builder.Default
    private Integer maxChoices = 1;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ComboGroupItem> items = new ArrayList<>();
}
