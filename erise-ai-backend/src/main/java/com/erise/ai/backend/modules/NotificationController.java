package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.api.ApiResponse;
import com.erise.ai.backend.common.api.PageResponse;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import com.erise.ai.backend.common.util.SecurityUtils;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<UserNotificationView>> myNotifications(@RequestParam(defaultValue = "1") long pageNum,
                                                                           @RequestParam(defaultValue = "10") long pageSize,
                                                                           @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.success(notificationService.myNotifications(pageNum, pageSize, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountView> unreadCount() {
        return ApiResponse.success(new NotificationUnreadCountView(notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.success("success", null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.success("success", null);
    }
}

@Service
@RequiredArgsConstructor
class NotificationService {

    private static final String ADMIN_NOTICE_TYPE = "ADMIN_NOTICE";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    PageResponse<UserNotificationView> myNotifications(long pageNum, long pageSize, boolean unreadOnly) {
        var currentUser = SecurityUtils.currentUser();
        long safePageNum = Math.max(pageNum, 1L);
        long safePageSize = Math.max(pageSize, 1L);
        long offset = (safePageNum - 1L) * safePageSize;
        String unreadClause = unreadOnly ? " and n.read_flag = 0 " : "";
        long total = scalar("""
                select count(*)
                from ea_user_notification n
                where n.deleted = 0
                  and n.user_id = ?
                """ + unreadClause, currentUser.userId());

        List<UserNotificationView> records = jdbcTemplate.query("""
                        select n.id,
                               n.notification_type,
                               n.title,
                               n.content,
                               n.read_flag,
                               n.created_at,
                               coalesce(up.display_name, u.username, '系统通知') as sender_name
                        from ea_user_notification n
                        left join ea_user u on u.id = n.created_by and u.deleted = 0
                        left join ea_user_profile up on up.user_id = u.id and up.deleted = 0
                        where n.deleted = 0
                          and n.user_id = ?
                        """ + unreadClause + """
                        order by n.created_at desc, n.id desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new UserNotificationView(
                        rs.getLong("id"),
                        rs.getString("notification_type"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getInt("read_flag") == 1,
                        rs.getString("sender_name"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ),
                currentUser.userId(),
                safePageSize,
                offset);
        return PageResponse.of(records, safePageNum, safePageSize, total);
    }

    long unreadCount() {
        var currentUser = SecurityUtils.currentUser();
        return scalar("""
                select count(*)
                from ea_user_notification
                where deleted = 0
                  and user_id = ?
                  and read_flag = 0
                """, currentUser.userId());
    }

    void markRead(Long notificationId) {
        var currentUser = SecurityUtils.currentUser();
        long exists = scalar("""
                select count(*)
                from ea_user_notification
                where deleted = 0
                  and id = ?
                  and user_id = ?
                """, notificationId, currentUser.userId());
        if (exists <= 0) {
            throw new BizException(ErrorCodes.NOT_FOUND, "Notification not found", HttpStatus.NOT_FOUND);
        }
        jdbcTemplate.update("""
                        update ea_user_notification
                        set read_flag = 1,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where deleted = 0
                          and id = ?
                          and user_id = ?
                        """,
                currentUser.userId(),
                notificationId,
                currentUser.userId());
    }

    void markAllRead() {
        var currentUser = SecurityUtils.currentUser();
        jdbcTemplate.update("""
                        update ea_user_notification
                        set read_flag = 1,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where deleted = 0
                          and user_id = ?
                          and read_flag = 0
                        """,
                currentUser.userId(),
                currentUser.userId());
    }

    void sendAdminNotification(AdminNotificationSendRequest request) {
        var currentUser = SecurityUtils.currentUser();
        if (!currentUser.isAdmin()) {
            throw new BizException(ErrorCodes.FORBIDDEN, "Administrator access required", HttpStatus.FORBIDDEN);
        }
        String title = trimToNull(request.title());
        String content = trimToNull(request.content());
        if (title == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Notification title is required", HttpStatus.BAD_REQUEST);
        }
        if (content == null) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Notification content is required", HttpStatus.BAD_REQUEST);
        }

        List<Long> targetUserIds = resolveTargetUserIds(request);
        if (targetUserIds.isEmpty()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "No notification recipients found", HttpStatus.BAD_REQUEST);
        }

        for (Long userId : targetUserIds) {
            jdbcTemplate.update("""
                            insert into ea_user_notification (
                                user_id,
                                notification_type,
                                title,
                                content,
                                read_flag,
                                created_by,
                                updated_by
                            ) values (?, ?, ?, ?, 0, ?, ?)
                            """,
                    userId,
                    ADMIN_NOTICE_TYPE,
                    title,
                    content,
                    currentUser.userId(),
                    currentUser.userId());
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sendToAll", Boolean.TRUE.equals(request.sendToAll()));
        detail.put("recipientCount", targetUserIds.size());
        detail.put("title", title);
        if (!Boolean.TRUE.equals(request.sendToAll())) {
            detail.put("userIds", targetUserIds);
        }
        auditLogService.log(currentUser, "ADMIN_NOTIFICATION_SEND", "USER_NOTIFICATION", null, detail);
    }

    private List<Long> resolveTargetUserIds(AdminNotificationSendRequest request) {
        if (Boolean.TRUE.equals(request.sendToAll())) {
            return jdbcTemplate.query("""
                            select id
                            from ea_user
                            where deleted = 0
                            order by id asc
                            """,
                    (rs, rowNum) -> rs.getLong("id"));
        }

        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>(
                request.userIds() == null ? List.of() : request.userIds().stream().filter(Objects::nonNull).toList());
        if (requestedIds.isEmpty()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Please select at least one user", HttpStatus.BAD_REQUEST);
        }

        List<Object> params = new ArrayList<>(requestedIds);
        String placeholders = String.join(", ", requestedIds.stream().map(id -> "?").toList());
        List<Long> existingIds = jdbcTemplate.query("""
                        select id
                        from ea_user
                        where deleted = 0
                          and id in (""" + placeholders + ") order by id asc",
                (rs, rowNum) -> rs.getLong("id"),
                params.toArray());

        if (existingIds.size() != requestedIds.size()) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "Some selected users do not exist", HttpStatus.BAD_REQUEST);
        }
        return existingIds;
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

record UserNotificationView(
        Long id,
        String notificationType,
        String title,
        String content,
        Boolean read,
        String senderName,
        LocalDateTime createdAt
) {
}

record NotificationUnreadCountView(long unreadCount) {
}

record AdminNotificationSendRequest(
        @Size(max = 255) String title,
        @Size(max = 4000) String content,
        Boolean sendToAll,
        List<Long> userIds
) {
}
