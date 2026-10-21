package com.neram.pos.controller;

import com.neram.pos.entity.Outlet;
import com.neram.pos.service.OutletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutletControllerTest {

    @Mock
    private OutletService outletService;

    @InjectMocks
    private OutletController outletController;

    @Test
    @DisplayName("getOutlets returns all outlets for current tenant")
    void testGetOutlets() {
        Outlet o1 = new Outlet();
        o1.setId(1L);
        o1.setName("Flagship Store");

        Outlet o2 = new Outlet();
        o2.setId(2L);
        o2.setName("Express Kiosk");

        when(outletService.getOutletsForCurrentTenant()).thenReturn(List.of(o1, o2));

        ResponseEntity<List<Outlet>> res = outletController.getOutlets();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(2, res.getBody().size());
        assertEquals("Flagship Store", res.getBody().get(0).getName());
    }

    @Test
    @DisplayName("createOutlet succeeds and returns created outlet")
    void testCreateOutlet_Success() {
        Outlet req = new Outlet();
        req.setName("Airport Branch");
        req.setCode("AIR-01");

        Outlet saved = new Outlet();
        saved.setId(10L);
        saved.setName("Airport Branch");
        saved.setCode("AIR-01");

        when(outletService.createOutlet(any(Outlet.class))).thenReturn(saved);

        ResponseEntity<?> res = outletController.createOutlet(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody() instanceof Outlet);
        assertEquals(10L, ((Outlet) res.getBody()).getId());
    }

    @Test
    @DisplayName("createOutlet returns 400 when business validation fails")
    void testCreateOutlet_Failure() {
        Outlet req = new Outlet();
        when(outletService.createOutlet(any(Outlet.class))).thenThrow(new IllegalArgumentException("Outlet code already in use"));

        ResponseEntity<?> res = outletController.createOutlet(req);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody() instanceof Map);
        assertEquals("Outlet code already in use", ((Map<?, ?>) res.getBody()).get("message"));
    }

    @Test
    @DisplayName("getFranchiseAnalytics returns comparative metrics across franchise branches")
    void testGetFranchiseAnalytics() {
        Map<String, Object> mockAnalytics = Map.of(
                "totalOutlets", 3,
                "totalRevenue", 254000.0,
                "totalOrders", 1250,
                "topPerformingOutlet", "Flagship Store"
        );
        when(outletService.getFranchiseAnalytics()).thenReturn(mockAnalytics);

        ResponseEntity<Map<String, Object>> res = outletController.getFranchiseAnalytics();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(3, res.getBody().get("totalOutlets"));
        assertEquals("Flagship Store", res.getBody().get("topPerformingOutlet"));
    }
}
