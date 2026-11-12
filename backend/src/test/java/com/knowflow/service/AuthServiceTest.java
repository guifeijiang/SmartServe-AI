package com.knowflow.service;

import com.knowflow.common.AccountNotActiveException;
import com.knowflow.common.DuplicateUsernameException;
import com.knowflow.common.InvalidCredentialsException;
import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.dto.TokenResponse;
import com.knowflow.dto.UserResponse;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(sysUserMapper, passwordEncoder, jwtService);
    }

    @Test
    void registerEncryptsPasswordAndReturnsUserWithoutHash() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("knowflow");
        request.setPassword("plain-pass");

        when(sysUserMapper.findByUsername("knowflow")).thenReturn(null);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        UserResponse response = authService.register(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser saved = captor.getValue();

        assertEquals("knowflow", saved.getUsername());
        assertEquals("knowflow", saved.getDisplayName());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals("CUSTOMER", saved.getRole());
        assertNotEquals("plain-pass", saved.getPasswordHash());
        assertFalse(saved.getPasswordHash().contains("plain-pass"));
        assertTrue(saved.getPasswordHash().startsWith("$2a$") || saved.getPasswordHash().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("plain-pass", saved.getPasswordHash()));

        assertEquals(1L, response.getId());
        assertEquals("knowflow", response.getUsername());
        assertEquals("knowflow", response.getDisplayName());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("CUSTOMER", response.getRole());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("knowflow");
        request.setPassword("plain-pass");

        SysUser existing = new SysUser();
        existing.setId(9L);
        existing.setUsername("knowflow");
        when(sysUserMapper.findByUsername("knowflow")).thenReturn(existing);

        DuplicateUsernameException ex = assertThrows(
                DuplicateUsernameException.class,
                () -> authService.register(request)
        );
        assertTrue(ex.getMessage().contains("knowflow"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        SysUser user = activeUser(1L, "knowflow", "plain-pass");
        when(sysUserMapper.findByUsername("knowflow")).thenReturn(user);
        when(jwtService.createToken(1L)).thenReturn("signed.jwt.token");

        TokenResponse response = authService.login(loginRequest("knowflow", "plain-pass"));

        assertEquals("signed.jwt.token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void loginFailsWithWrongPassword() {
        when(sysUserMapper.findByUsername("knowflow")).thenReturn(activeUser(1L, "knowflow", "plain-pass"));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(loginRequest("knowflow", "wrong-pass"))
        );
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void loginFailsWhenUserDisabled() {
        SysUser user = activeUser(1L, "knowflow", "plain-pass");
        user.setStatus("DISABLED");
        when(sysUserMapper.findByUsername("knowflow")).thenReturn(user);

        assertThrows(
                AccountNotActiveException.class,
                () -> authService.login(loginRequest("knowflow", "plain-pass"))
        );
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void getCurrentUserHidesPasswordHash() {
        when(sysUserMapper.findById(1L)).thenReturn(activeUser(1L, "knowflow", "plain-pass"));

        UserResponse response = authService.getCurrentUser(1L);

        assertEquals(1L, response.getId());
        assertEquals("knowflow", response.getUsername());
        assertEquals("ACTIVE", response.getStatus());
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private SysUser activeUser(Long id, String username, String rawPassword) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(username);
        user.setStatus("ACTIVE");
        user.setDeletedAt(null);
        return user;
    }
}
