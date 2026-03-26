package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.success(userService.current());
    }

    @PutMapping("/me")
    public ApiResponse<UserView> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.success("success", null);
    }
}

@RequiredArgsConstructor
@Service
class UserService {

    private final AuthService authService;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    UserView current() {
        var currentUser = SecurityUtils.currentUser();
        UserEntity user = authService.activeUserById(currentUser.userId());
        UserProfileEntity profile = authService.profileByUserId(currentUser.userId());
        return new UserView(user.getId(), user.getUsername(), profile.getDisplayName(), user.getEmail(), user.getRoleCode());
    }

    UserView updateProfile(UpdateProfileRequest request) {
        var currentUser = SecurityUtils.currentUser();
        UserEntity user = authService.activeUserById(currentUser.userId());
        UserProfileEntity profile = authService.profileByUserId(currentUser.userId());
        user.setEmail(request.email());
        user.setUpdatedBy(currentUser.userId());
        userMapper.updateById(user);
        profile.setDisplayName(request.displayName());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setBio(request.bio());
        profile.setUpdatedBy(currentUser.userId());
        userProfileMapper.updateById(profile);
        auditLogService.log(currentUser, "USER_PROFILE_UPDATE", "USER", currentUser.userId(), request);
        return current();
    }

    void changePassword(ChangePasswordRequest request) {
        var currentUser = SecurityUtils.currentUser();
        UserEntity user = authService.activeUserById(currentUser.userId());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Old password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedBy(currentUser.userId());
        userMapper.updateById(user);
        auditLogService.log(currentUser, "USER_PASSWORD_CHANGE", "USER", currentUser.userId(), null);
    }
}

record UpdateProfileRequest(
        @NotBlank String displayName,
        @NotBlank String email,
        String avatarUrl,
        String bio
) {
}

record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {
}
