package com.neram.pos.controller;

import com.neram.pos.dto.UpdateProfileRequestDTO;
import com.neram.pos.dto.UserProfileDTO;
import com.neram.pos.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerProfileTest {

    @Mock
    private UserService userService;

    @Mock
    private Authentication auth;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("getMyProfile returns user profile for authenticated user")
    void testGetMyProfile() {
        when(auth.getName()).thenReturn("staff_jane");
        UserProfileDTO profile = UserProfileDTO.builder()
                .userId(25L)
                .username("staff_jane")
                .employeeName("Jane Doe")
                .role("EMPLOYEE")
                .email("jane@cafe.internal")
                .canCancelOrder(true)
                .canTakeOrders(true)
                .build();

        when(userService.getMyProfile("staff_jane")).thenReturn(profile);

        ResponseEntity<UserProfileDTO> res = userController.getMyProfile(auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("staff_jane", res.getBody().getUsername());
        assertEquals("Jane Doe", res.getBody().getEmployeeName());
        assertTrue(res.getBody().getCanCancelOrder());
    }

    @Test
    @DisplayName("updateMyProfile saves updated personal info")
    void testUpdateMyProfile() {
        when(auth.getName()).thenReturn("staff_jane");
        UpdateProfileRequestDTO req = new UpdateProfileRequestDTO();
        req.setName("Jane Doe Smith");
        req.setPhone("+91 9123456789");

        UserProfileDTO updated = UserProfileDTO.builder()
                .username("staff_jane")
                .employeeName("Jane Doe Smith")
                .build();
        when(userService.updateMyProfile(eq("staff_jane"), any(UpdateProfileRequestDTO.class))).thenReturn(updated);

        ResponseEntity<UserProfileDTO> res = userController.updateMyProfile(req, auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Jane Doe Smith", res.getBody().getEmployeeName());
    }

    @Test
    @DisplayName("changeOwnPassword requires both currentPassword and newPassword")
    void testChangeOwnPassword_MissingFields() {
        when(auth.getName()).thenReturn("staff_jane");

        ResponseEntity<?> res1 = userController.changeOwnPassword(Map.of("newPassword", "abc"), auth);
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());
        assertTrue(((Map<?, ?>) res1.getBody()).get("message").toString().contains("currentPassword is required"));

        ResponseEntity<?> res2 = userController.changeOwnPassword(Map.of("currentPassword", "abc"), auth);
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertTrue(((Map<?, ?>) res2.getBody()).get("message").toString().contains("newPassword is required"));
    }

    @Test
    @DisplayName("changeOwnPassword successfully changes password and returns 200")
    void testChangeOwnPassword_Success() {
        when(auth.getName()).thenReturn("staff_jane");
        when(userService.changeOwnPassword("staff_jane", "OldPass@123", "NewPass@456")).thenReturn("cafe~refresh");

        ResponseEntity<?> res = userController.changeOwnPassword(
                Map.of("currentPassword", "OldPass@123", "newPassword", "NewPass@456"), auth);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Password changed successfully", ((Map<?, ?>) res.getBody()).get("message"));
    }

    @Test
    @DisplayName("changeOwnPassword returns 400 when old password check fails")
    void testChangeOwnPassword_IncorrectCurrentPassword() {
        when(auth.getName()).thenReturn("staff_jane");
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(userService).changeOwnPassword("staff_jane", "WrongPass", "NewPass@456");

        ResponseEntity<?> res = userController.changeOwnPassword(
                Map.of("currentPassword", "WrongPass", "newPassword", "NewPass@456"), auth);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(((Map<?, ?>) res.getBody()).get("message").toString().contains("Current password is incorrect"));
    }
}
