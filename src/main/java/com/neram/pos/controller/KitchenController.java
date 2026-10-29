package com.neram.pos.controller;

import com.neram.pos.dto.KitchenTicketDTO;
import com.neram.pos.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The kitchen display: open orders that have gone to the kitchen, without prices or customer details. */
@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final BillService billService;

    @GetMapping("/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'KITCHEN') or hasAuthority('PERM_TAKE_ORDERS')")
    public ResponseEntity<List<KitchenTicketDTO>> tickets() {
        return ResponseEntity.ok(billService.getKitchenTickets());
    }
}
