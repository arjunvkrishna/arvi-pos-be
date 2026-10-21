package com.neram.pos.controller;

import com.neram.pos.dto.DiningTableDTO;
import com.neram.pos.dto.DiningTableRequestDTO;
import com.neram.pos.service.DiningTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class DiningTableController {

    private final DiningTableService tableService;

    @GetMapping
    public ResponseEntity<List<DiningTableDTO>> getAllTables() {
        return ResponseEntity.ok(tableService.getAllActiveTables());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DiningTableDTO> createTable(@RequestBody DiningTableRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createTable(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteTable(@PathVariable Long id) {
        tableService.deleteTable(id);
        return ResponseEntity.noContent().build();
    }
}
