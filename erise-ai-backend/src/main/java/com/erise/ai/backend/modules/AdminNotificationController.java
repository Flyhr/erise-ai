package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ApiResponse<Void> sendNotification(@Valid @RequestBody AdminNotificationSendRequest request) {
        notificationService.sendAdminNotification(request);
        return ApiResponse.success("success", null);
    }
}
