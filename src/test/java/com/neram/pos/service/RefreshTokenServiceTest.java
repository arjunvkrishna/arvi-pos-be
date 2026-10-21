package com.neram.pos.service;

import com.neram.pos.entity.RefreshToken;
import com.neram.pos.entity.User;
import com.neram.pos.repository.RefreshTokenRepository;
import com.neram.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private UserRepository userRepository;
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        userRepository = mock(UserRepository.class);
        service = new RefreshTokenService(repository, userRepository);
        ReflectionTestUtils.setField(service, "refreshExpirationMillis", 60_000L);

        user = new User();
        user.setId(7L);
        user.setUsername("manager");
        user.setActive(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(anyLong())).thenReturn(new ArrayList<>());
    }

    private RefreshToken stored(String raw, boolean revoked, LocalDateTime expiry, LocalDateTime updatedAt) {
        RefreshToken t = RefreshToken.builder()
                .token(RefreshTokenService.hash(raw)).userId(7L).expiryDate(expiry).revoked(revoked)
                .createdAt(LocalDateTime.now()).updatedAt(updatedAt).build();
        when(repository.findByTokenForUpdate(RefreshTokenService.hash(raw))).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void issueStoresOnlyTheHashNeverTheRawToken() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertNotEquals(raw, captor.getValue().getToken());
        assertEquals(RefreshTokenService.hash(raw), captor.getValue().getToken());
        assertEquals(64, captor.getValue().getToken().length());
        assertTrue(captor.getValue().getExpiryDate().isAfter(LocalDateTime.now()));
        assertNotEquals(raw, service.issue(user), "every token must be unique");
    }

    @Test
    void rotateRevokesPresentedTokenAndIssuesANewOne() {
        RefreshToken t = stored("old-token", false, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());

        RefreshTokenService.Rotation r = service.rotate("old-token");

        assertTrue(t.isRevoked());
        assertSame(user, r.user());
        assertNotNull(r.refreshToken());
        assertNotEquals("old-token", r.refreshToken());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenForUpdate(any())).thenReturn(Optional.empty());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("nope"));
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("  "));
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(null));
    }

    @Test
    void expiredTokenIsRejectedAndRevoked() {
        RefreshToken t = stored("expired", false, LocalDateTime.now().minusMinutes(1), LocalDateTime.now());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("expired"));
        assertTrue(t.isRevoked());
    }

    @Test
    void reusingARevokedTokenRevokesEveryActiveSession() {
        stored("stolen", true, LocalDateTime.now().plusMinutes(5), LocalDateTime.now().minusMinutes(5));
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("stolen"));
        verify(repository).revokeAllByUserId(eq(7L), any());
    }

    @Test
    void concurrentDoubleRefreshWithinGraceDoesNotKillOtherSessions() {
        stored("just-rotated", true, LocalDateTime.now().plusMinutes(5), LocalDateTime.now().minusSeconds(2));
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("just-rotated"));
        verify(repository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    void inactiveUserCannotRefresh() {
        user.setActive(false);
        RefreshToken t = stored("valid", false, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("valid"));
        assertTrue(t.isRevoked());
    }

    @Test
    void liveSessionsPerUserAreCapped() {
        List<RefreshToken> active = new ArrayList<>();
        for (int i = 0; i < RefreshTokenService.MAX_ACTIVE_TOKENS_PER_USER + 2; i++) {
            active.add(RefreshToken.builder().token("t" + i).userId(7L)
                    .expiryDate(LocalDateTime.now().plusDays(1)).build());
        }
        when(repository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(7L)).thenReturn(active);

        service.issue(user);

        assertTrue(active.get(0).isRevoked());
        assertTrue(active.get(1).isRevoked());
        assertFalse(active.get(2).isRevoked());
    }

    @Test
    void logoutRevokesOnlyThePresentedToken() {
        RefreshToken t = stored("session", false, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        service.revoke("session");
        assertTrue(t.isRevoked());
        verify(repository, never()).revokeAllByUserId(anyLong(), any());
    }
}
