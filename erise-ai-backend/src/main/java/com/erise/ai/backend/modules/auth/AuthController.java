package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.security.CurrentUser;
import com.erise.ai.backend.common.security.JwtTokenProvider;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.success(authService.generateCaptcha());
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                                @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
        return ApiResponse.success(authService.login(request, ip, userAgent));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success("success", null);
    }
}

@RequiredArgsConstructor
@Service
class AuthService {

    private static final String CAPTCHA_PREFIX = "auth:captcha:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final char[] CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserLoginLogMapper userLoginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;
    private final EriseProperties properties;

    CaptchaResponse generateCaptcha() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, code.toLowerCase(), Duration.ofMinutes(5));
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="120" height="42">
                  <rect width="120" height="42" fill="#f5f7fa"/>
                  <text x="14" y="28" font-size="22" font-family="monospace" fill="#1f2937">%s</text>
                </svg>
                """.formatted(code);
        String encoded = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptchaResponse(captchaId, "data:image/svg+xml;base64," + encoded);
    }

    AuthTokenResponse register(RegisterRequest request) {
        ensureCaptcha(request.captchaId(), request.captchaCode());
        if (userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.username())) != null) {
            throw new BizException(ErrorCodes.CONFLICT, "Username already exists", HttpStatus.CONFLICT);
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setEmail(request.email());
        entity.setPasswordHash(passwordEncoder.encode(request.password()));
        entity.setRoleCode("USER");
        entity.setStatus("ACTIVE");
        entity.setEnabled(1);
        entity.setCreatedBy(0L);
        entity.setUpdatedBy(0L);
        userMapper.insert(entity);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(entity.getId());
        profile.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : request.username());
        profile.setCreatedBy(entity.getId());
        profile.setUpdatedBy(entity.getId());
        userProfileMapper.insert(profile);

        CurrentUser currentUser = new CurrentUser(entity.getId(), entity.getUsername(), entity.getRoleCode());
        auditLogService.log(currentUser, "AUTH_REGISTER", "USER", entity.getId(), Map.of("username", entity.getUsername()));
        return buildAuthResponse(currentUser, profile.getDisplayName(), entity.getEmail(), profile.getAvatarUrl(), profile.getBio());
    }

    AuthTokenResponse login(LoginRequest request, String ip, String userAgent) {
        ensureCaptcha(request.captchaId(), request.captchaCode());
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            insertLoginLog(null, request.username(), ip, userAgent, false);
            throw new BizException(ErrorCodes.UNAUTHORIZED, "Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "User is disabled", HttpStatus.FORBIDDEN);
        }
        UserProfileEntity profile = profileByUserId(user.getId());
        CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), user.getRoleCode());
        insertLoginLog(user.getId(), user.getUsername(), ip, userAgent, true);
        auditLogService.log(currentUser, "AUTH_LOGIN", "USER", user.getId(), Map.of("ip", ip));
        return buildAuthResponse(currentUser, profile.getDisplayName(), user.getEmail(), profile.getAvatarUrl(), profile.getBio());
    }

    AuthTokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        CurrentUser user = jwtTokenProvider.parse(refreshToken);
        String stored = redisTemplate.opsForValue().get(REFRESH_PREFIX + user.userId());
        if (!refreshToken.equals(stored)) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "Refresh token expired", HttpStatus.UNAUTHORIZED);
        }
        UserEntity entity = activeUserById(user.userId());
        UserProfileEntity profile = profileByUserId(entity.getId());
        return buildAuthResponse(new CurrentUser(entity.getId(), entity.getUsername(), entity.getRoleCode()),
                profile.getDisplayName(), entity.getEmail(), profile.getAvatarUrl(), profile.getBio());
    }

    void logout(String refreshToken) {
        CurrentUser user = jwtTokenProvider.parse(refreshToken);
        redisTemplate.delete(REFRESH_PREFIX + user.userId());
        auditLogService.log(user, "AUTH_LOGOUT", "USER", user.userId(), null);
    }

    UserEntity activeUserById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "User not found or disabled", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    UserProfileEntity profileByUserId(Long userId) {
        UserProfileEntity profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getUserId, userId)
                .last("limit 1"));
        if (profile == null) {
            profile = new UserProfileEntity();
            profile.setUserId(userId);
            profile.setDisplayName(activeUserById(userId).getUsername());
            profile.setCreatedBy(userId);
            profile.setUpdatedBy(userId);
            userProfileMapper.insert(profile);
        }
        return profile;
    }

    private AuthTokenResponse buildAuthResponse(CurrentUser currentUser, String displayName, String email, String avatarUrl, String bio) {
        String accessToken = jwtTokenProvider.createAccessToken(currentUser);
        String refreshToken = jwtTokenProvider.createRefreshToken(currentUser);
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + currentUser.userId(),
                refreshToken,
                Duration.ofDays(properties.getJwt().getRefreshTokenExpireDays())
        );
        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserView(currentUser.userId(), currentUser.username(), displayName, email, currentUser.roleCode(), avatarUrl, bio)
        );
    }

    private void ensureCaptcha(String captchaId, String captchaCode) {
        String expected = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaId);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Invalid captcha");
        }
        redisTemplate.delete(CAPTCHA_PREFIX + captchaId);
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(CAPTCHA_CHARS[(int) (Math.random() * CAPTCHA_CHARS.length)]);
        }
        return builder.toString();
    }

    private void insertLoginLog(Long userId, String username, String ip, String userAgent, boolean success) {
        UserLoginLogEntity entity = new UserLoginLogEntity();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setLoginIp(ip);
        entity.setUserAgent(userAgent);
        entity.setSuccess(success ? 1 : 0);
        entity.setCreatedBy(userId == null ? 0L : userId);
        entity.setUpdatedBy(userId == null ? 0L : userId);
        userLoginLogMapper.insert(entity);
    }
}

@RequiredArgsConstructor
@Component
class BootstrapAdminInitializer {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final EriseProperties properties;

    @PostConstruct
    public void init() {
        if (userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, properties.getBootstrap().getAdminUsername())
                .last("limit 1")) != null) {
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(properties.getBootstrap().getAdminUsername());
        user.setPasswordHash(passwordEncoder.encode(properties.getBootstrap().getAdminPassword()));
        user.setRoleCode("ADMIN");
        user.setStatus("ACTIVE");
        user.setEnabled(1);
        user.setCreatedBy(0L);
        user.setUpdatedBy(0L);
        userMapper.insert(user);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(user.getId());
        profile.setDisplayName(properties.getBootstrap().getAdminDisplayName());
        profile.setCreatedBy(user.getId());
        profile.setUpdatedBy(user.getId());
        userProfileMapper.insert(profile);
    }
}

interface UserMapper extends BaseMapper<UserEntity> {
}

interface UserProfileMapper extends BaseMapper<UserProfileEntity> {
}

interface UserLoginLogMapper extends BaseMapper<UserLoginLogEntity> {
}

@Data
@TableName("ea_user")
class UserEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String roleCode;
    private String status;
    private Integer enabled;
}

@Data
@TableName("ea_user_profile")
class UserProfileEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String displayName;
    private String avatarUrl;
    private String bio;
}

@Data
@TableName("ea_user_login_log")
class UserLoginLogEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String loginIp;
    private String userAgent;
    private Integer success;
}

record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String password,
        String displayName,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
}

record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
}

record RefreshRequest(@NotBlank String refreshToken) {
}

record LogoutRequest(@NotBlank String refreshToken) {
}

record CaptchaResponse(String captchaId, String captchaImage) {
}

record AuthTokenResponse(String accessToken, String refreshToken, UserView user) {
}

record UserView(Long id, String username, String displayName, String email, String roleCode, String avatarUrl, String bio) {
}
