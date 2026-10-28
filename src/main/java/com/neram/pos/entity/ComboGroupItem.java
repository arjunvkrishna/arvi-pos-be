package com.neram.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "combo_group_items")
public class ComboGroupItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_group_id", nullable = false)
    @JsonIgnore
    private ComboGroup group;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "additional_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal additionalPrice = BigDecimal.ZERO;
}
