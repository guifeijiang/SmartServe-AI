package com.knowflow.service;

import com.knowflow.common.AccountNotActiveException;
import com.knowflow.common.DuplicateUsernameException;
import com.knowflow.common.InvalidCredentialsException;
import com.knowflow.common.UserNotFoundException;
import com.knowflow.dto.LoginRequest;
import com.knowflow.dto.RegisterRequest;
import com.knowflow.dto.TokenResponse;
import com.knowflow.dto.UserResponse;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String ROLE_CUSTOMER = "CUSTOMER";
    static final String TOKEN_TYPE_BEARER = "Bearer";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (sysUserMapper.findByUsername(request.getUsername()) != null) {
            throw new DuplicateUsernameException(request.getUsername());
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getUsername());
        user.setStatus(STATUS_ACTIVE);
        user.setRole(ROLE_CUSTOMER);
        sysUserMapper.insert(user);

        return toUserResponse(user);
    }

    /**
     * 登录只接受未删除且 ACTIVE 的用户；密码用 BCrypt 比对，成功后签发 JWT。
     * 用户不存在和密码错误返回同一类错误，避免被用来枚举用户名。
     */
    public TokenResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.findByUsername(request.getUsername());
        if (user == null || user.getDeletedAt() != null) {
            throw new InvalidCredentialsException();
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw new AccountNotActiveException();
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new TokenResponse(jwtService.createToken(user.getId()), TOKEN_TYPE_BEARER);
    }

    public UserResponse getCurrentUser(Long userId) {
        SysUser user = sysUserMapper.findById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new UserNotFoundException();
        }
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(SysUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                user.getRole()
        );
    }
}
