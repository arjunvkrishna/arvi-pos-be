package com.neram.pos.access;

import com.neram.pos.entity.Outlet;
import com.neram.pos.entity.User.Role;
import com.neram.pos.repository.OutletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutletScopeResolverTest {

    private OutletRepository outlets;
    private OutletScopeResolver resolver;

    @BeforeEach
    void setUp() {
        outlets = mock(OutletRepository.class);
        resolver = new OutletScopeResolver(outlets);
        when(outlets.existsById(2L)).thenReturn(true);
    }

    private static CafeUserDetails user(Role role, Long outlet) {
        return new CafeUserDetails("u", "x", true, List.of(), 1L, role, outlet);
    }

    @Test
    void ownerAndMainManagerSeeTheWholeCafeUnlessTheyPickABranch() {
        assertNull(resolver.resolve(user(Role.ADMIN, 1L), null));
        assertNull(resolver.resolve(user(Role.GENERAL_MANAGER, null), ""));
        assertEquals(2L, resolver.resolve(user(Role.CAFE_ADMIN, 1L), "2"));
        assertEquals(2L, resolver.resolve(user(Role.GENERAL_MANAGER, null), " 2 "));
        assertNull(resolver.resolve(user(Role.ADMIN, 1L), "not-a-number"));
    }

    @Test
    void pickingABranchThatDoesNotExistIsRefused() {
        assertThrows(OutletAccessDeniedException.class, () -> resolver.resolve(user(Role.ADMIN, 1L), "99"));
    }

    @Test
    void branchManagersAndStaffAreTiedToTheirOwnBranchWhateverTheyAsk() {
        assertEquals(1L, resolver.resolve(user(Role.MANAGER, 1L), "2"));
        assertEquals(1L, resolver.resolve(user(Role.CASHIER, 1L), "2"));
        assertEquals(OutletContext.NO_OUTLET, resolver.resolve(user(Role.MANAGER, null), "2"),
                "no assigned branch means no branch data, never everything");
        verify(outlets, never()).existsById(any());
    }

    @Test
    void aPrintBridgeNamesItsBranchByCode() {
        Outlet o = new Outlet();
        o.setId(2L);
        o.setCode("BR-2");
        when(outlets.findAll()).thenReturn(List.of(o));
        assertEquals(2L, resolver.resolveByCode("br-2"));
        assertNull(resolver.resolveByCode(" "));
        assertThrows(OutletAccessDeniedException.class, () -> resolver.resolveByCode("NOPE"));
    }
}
