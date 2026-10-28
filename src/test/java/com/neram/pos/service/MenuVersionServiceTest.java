package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.tenancy.TenantContext;
import com.neram.pos.tenancy.TenantInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Many devices asking every few seconds must cost the database one query, not one each - and never mix cafes or branches. */
class MenuVersionServiceTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
        OutletContext.clear();
    }

    private static String anyQuery(JdbcTemplate jdbc) {
        return jdbc.queryForObject(anyString(), eq(String.class), any(), any());
    }

    @Test
    void devices_asking_together_share_one_query_and_a_later_ask_sees_the_change() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(anyQuery(jdbc)).thenReturn("aaaaaaaaaaaa", "bbbbbbbbbbbb");
        MenuVersionService service = new MenuVersionService(jdbc);

        assertEquals("aaaaaaaaaaaa", service.current());
        assertEquals("aaaaaaaaaaaa", service.current());
        assertEquals("aaaaaaaaaaaa", service.current());
        verify(jdbc, times(1)).queryForObject(anyString(), eq(String.class), any(), any());

        Thread.sleep(MenuVersionService.REMEMBER_MS + 150);
        assertEquals("bbbbbbbbbbbb", service.current(), "after the short memory expires the new menu is seen");
        verify(jdbc, times(2)).queryForObject(anyString(), eq(String.class), any(), any());
    }

    @Test
    void one_cafes_fingerprint_is_never_served_to_another_cafe() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(anyQuery(jdbc)).thenReturn("cafe-one-aaa", "cafe-two-bbb");
        MenuVersionService service = new MenuVersionService(jdbc);

        TenantContext.set(new TenantInfo(1, "one"));
        String first = service.current();
        TenantContext.set(new TenantInfo(2, "two"));
        String second = service.current();

        assertEquals("cafe-one-aaa", first);
        assertEquals("cafe-two-bbb", second);
        assertNotEquals(first, second);
        verify(jdbc, times(2)).queryForObject(anyString(), eq(String.class), any(), any());
    }

    @Test
    void each_branch_has_its_own_fingerprint_because_branch_prices_differ() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(10L), eq(10L))).thenReturn("branch-ten");
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(20L), eq(20L))).thenReturn("branch-twenty");
        MenuVersionService service = new MenuVersionService(jdbc);

        TenantContext.set(new TenantInfo(1, "one"));
        OutletContext.set(10L);
        assertEquals("branch-ten", service.current());
        OutletContext.set(20L);
        assertEquals("branch-twenty", service.current());
        OutletContext.set(10L);
        assertEquals("branch-ten", service.current());
        verify(jdbc, times(1)).queryForObject(anyString(), eq(String.class), eq(10L), eq(10L));
    }
}
