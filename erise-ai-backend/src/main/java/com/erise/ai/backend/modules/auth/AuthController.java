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
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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

    /**
     * 登录接口：验证验证码 → 验证用户名密码 → 生成访问令牌和刷新令牌 → 记录登录日志 → 返回认证信息
     *
     * @param request
     * @param userAgent
     * @param ip
     * @param User-Agent      客户端设备信息（浏览器型号、手机系统、APP 版本）
     * @param X-Forwarded-For 客户端真实 IP 地址
     * @return
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            // 1. 接收前端JSON参数 + 开启参数校验
            @Valid @RequestBody LoginRequest request,
            // 2. 获取请求头：客户端设备信息（浏览器/APP型号）
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            // 3. 获取请求头：客户端真实IP地址
            @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
        return ApiResponse.success(authService.login(request, ip, userAgent));
    }

    /**
     * 刷新令牌接口：验证刷新令牌 → 生成新的访问令牌和刷新令牌 → 返回认证信息
     *
     * @param request
     * @return
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    /**
     * 退出登录接口：验证刷新令牌 → 删除刷新令牌 → 记录退出日志
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success("success", null);
    }
}

@RequiredArgsConstructor // @RequiredArgsConstructor：Lombok 注解，自动生成含所有 final 字段的构造方法，配合 Spring 完成依赖注入；
@Service
class AuthService {

    private static final String CAPTCHA_PREFIX = "auth:captcha:";// 验证码Redis键前缀
    private static final String REFRESH_PREFIX = "auth:refresh:";// 刷新令牌Redis键前缀
    private static final char[] CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // 验证码字符集（排除0/O、1/I等易混淆字符）
    private static final int REGISTER_USERNAME_MIN_LENGTH = 4;
    private static final int REGISTER_USERNAME_MAX_LENGTH = 20;
    private static final int REGISTER_PASSWORD_MIN_LENGTH = 6;
    private static final int REGISTER_PASSWORD_MAX_LENGTH = 20;

    // 依赖注入的核心组件
    private final UserMapper userMapper; // 用户数据操作
    private final UserProfileMapper userProfileMapper; // 用户资料数据操作
    private final UserLoginLogMapper userLoginLogMapper; // 登录日志数据操作
    private final PasswordEncoder passwordEncoder; // 密码加密器（Spring Security）
    private final JwtTokenProvider jwtTokenProvider; // JWT令牌生成/解析工具
    private final StringRedisTemplate redisTemplate; // Redis缓存操作
    private final AuditLogService auditLogService; // 审计日志服务
    private final EriseProperties properties; // 系统配置（如JWT过期时间）
    private final JdbcTemplate jdbcTemplate; // 数据库操作（用于补发历史系统通知）

    // 生成图形验证码：生成随机验证码 → 存储到Redis（5分钟过期） → 生成SVG图像 → Base64编码 → 返回验证码ID和图像数据
    /**
     *
     * @return
     */
    CaptchaResponse generateCaptcha() {
        // 1. 生成4位随机验证码（从指定字符集里选，排除0/O/1/I易混淆字符）
        String code = randomCode();
        // 2. 生成唯一的验证码ID（UUID全球唯一，防止重复）s
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

    // 处理用户注册：验证验证码 → 检查用户名唯一性 → 创建用户记录 → 创建用户资料记录 → 记录注册审计日志 → 返回认证信息
    AuthTokenResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());
        String displayName = normalizeDisplayName(request.displayName());
        ensureCaptcha(request.captchaId(), request.captchaCode());
        if (userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username)) != null) {
            throw new BizException(ErrorCodes.CONFLICT, "用户名已存在", HttpStatus.CONFLICT);
        }
        if (userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email)) != null) {
            throw new BizException(ErrorCodes.CONFLICT, "邮箱已被使用", HttpStatus.CONFLICT);
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setRoleCode("USER");
        entity.setStatus("ACTIVE");
        entity.setEnabled(1);
        entity.setCreatedBy(0L);
        entity.setUpdatedBy(0L);
        userMapper.insert(entity);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(entity.getId());
        profile.setDisplayName(displayName != null ? displayName : username);
        profile.setCreatedBy(entity.getId());
        profile.setUpdatedBy(entity.getId());
        userProfileMapper.insert(profile);

        // 将历史的全量系统通知（ADMIN_NOTICE 类型）补发给新注册用户
        copyHistoricalAdminNotificationsTo(entity.getId());

        CurrentUser currentUser = new CurrentUser(entity.getId(), entity.getUsername(), entity.getRoleCode());
        auditLogService.log(currentUser, "AUTH_REGISTER", "USER", entity.getId(),
                Map.of("username", entity.getUsername()));
        return buildAuthResponse(currentUser, profile.getDisplayName(), entity.getEmail(), profile.getAvatarUrl(),
                profile.getBio());
    }

    // 处理用户登录：验证验证码 → 验证用户名密码 → 生成访问令牌和刷新令牌 → 记录登录日志 → 返回认证信息
    AuthTokenResponse login(LoginRequest request, String ip, String userAgent) {
        ensureCaptcha(request.captchaId(), request.captchaCode());
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            insertLoginLog(null, request.username(), ip, userAgent, false);
            throw new BizException(ErrorCodes.UNAUTHORIZED, "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BizException(ErrorCodes.FORBIDDEN, "账号已被禁用，请联系管理员", HttpStatus.FORBIDDEN);
        }
        UserProfileEntity profile = profileByUserId(user.getId());
        CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), user.getRoleCode());
        insertLoginLog(user.getId(), user.getUsername(), ip, userAgent, true);
        auditLogService.log(currentUser, "AUTH_LOGIN", "USER", user.getId(),
                ip == null || ip.isBlank() ? Map.of() : Map.of("ip", ip));
        return buildAuthResponse(currentUser, profile.getDisplayName(), user.getEmail(), profile.getAvatarUrl(),
                profile.getBio());
    }

    /**
     *
     * @param refreshToken
     * @return
     */
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

    private AuthTokenResponse buildAuthResponse(CurrentUser currentUser, String displayName, String email,
            String avatarUrl, String bio) {
        String accessToken = jwtTokenProvider.createAccessToken(currentUser);
        String refreshToken = jwtTokenProvider.createRefreshToken(currentUser);
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + currentUser.userId(),
                refreshToken,
                Duration.ofDays(properties.getJwt().getRefreshTokenExpireDays()));
        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserView(currentUser.userId(), currentUser.username(), displayName, email, currentUser.roleCode(),
                        avatarUrl, bio));
    }

    private void ensureCaptcha(String captchaId, String captchaCode) {
        String expected = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaId);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "验证码错误或已过期");
        }
        redisTemplate.delete(CAPTCHA_PREFIX + captchaId);
    }

    private String normalizeUsername(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "请输入用户名", HttpStatus.BAD_REQUEST);
        }
        if (normalized.contains(" ")) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "用户名不能包含空格", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() < REGISTER_USERNAME_MIN_LENGTH || normalized.length() > REGISTER_USERNAME_MAX_LENGTH) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "用户名长度需为 4-20 位", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "请输入正确的邮箱地址", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizePassword(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "请输入密码", HttpStatus.BAD_REQUEST);
        }
        if (value.length() < REGISTER_PASSWORD_MIN_LENGTH || value.length() > REGISTER_PASSWORD_MAX_LENGTH) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "密码长度需为 6-20 位", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeDisplayName(String value) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > 128) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "显示名称不能超过 128 个字符", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 生成4位随机验证码（字符集：大写字母+数字，排除易混淆字符）
     */
    private String randomCode() {
        StringBuilder builder = new StringBuilder();
        // 循环4次，生成4个字符
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

    private void copyHistoricalAdminNotificationsTo(Long newUserId) {
        // 仅补发管理员发送给“全部用户”的系统通知，不补发定向通知
        List<Long> templateIds = jdbcTemplate.query("""
                select n.id
                from ea_user_notification n
                inner join (
                    select min(id) as id
                    from ea_user_notification
                    where deleted = 0
                      and notification_type = 'ADMIN_NOTICE'
                      and broadcast_flag = 1
                      and user_id != ?
                    group by title, content
                ) dedup on dedup.id = n.id
                order by n.id asc
                """,
                (rs, rowNum) -> rs.getLong("id"),
                newUserId);

        for (Long templateId : templateIds) {
            jdbcTemplate.update(
                    """
                            insert into ea_user_notification (user_id, notification_type, title, content, read_flag, broadcast_flag, created_by, updated_by)
                            select ?, notification_type, title, content, 0, broadcast_flag, created_by, created_by
                            from ea_user_notification
                            where id = ? and deleted = 0
                            """,
                    newUserId, templateId);
        }
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
        @NotBlank(message = "请输入用户名") String username,
        @NotBlank(message = "请输入邮箱地址") @Email(message = "请输入正确的邮箱地址") String email,
        @NotBlank(message = "请输入密码") String password,
        String displayName,
        @NotBlank(message = "验证码已失效，请刷新后重试") String captchaId,
        @NotBlank(message = "请输入验证码") @Size(min = 4, max = 4, message = "请输入4位验证码") String captchaCode) {
}

record LoginRequest(
        @NotBlank(message = "请输入用户名") String username,
        @NotBlank(message = "请输入密码") String password,
        @NotBlank(message = "验证码已失效，请刷新后重试") String captchaId,
        @NotBlank(message = "请输入验证码") @Size(min = 4, max = 4, message = "请输入4位验证码") String captchaCode) {
}

record RefreshRequest(@NotBlank String refreshToken) {
}

record LogoutRequest(@NotBlank String refreshToken) {
}

record CaptchaResponse(String captchaId, String captchaImage) {
}

record AuthTokenResponse(String accessToken, String refreshToken, UserView user) {
}

record UserView(Long id, String username, String displayName, String email, String roleCode, String avatarUrl,
        String bio) {
}
