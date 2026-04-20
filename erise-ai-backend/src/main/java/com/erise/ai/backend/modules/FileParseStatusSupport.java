package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class FileParseStatusSupport {

    private final FileParseTaskMapper fileParseTaskMapper;
    private final RagTaskMapper ragTaskMapper;

    FileParseStatusView resolve(Long fileId, String parseStatus, String indexStatus) {
        FileParseTaskEntity latestTask = latestTask(fileId);
        RagTaskEntity latestIndexTask = latestIndexTask(fileId);
        String resolvedParseStatus = parseStatus;
        String resolvedIndexStatus = indexStatus;
        if (isRetrying(latestTask)) {
            String retryStatus = isTimeoutLike(latestTask.getLastError()) ? "TIMEOUT_RETRYING" : "RETRYING";
            if ("SUCCESS".equalsIgnoreCase(parseStatus)) {
                resolvedIndexStatus = retryStatus;
            } else {
                resolvedParseStatus = retryStatus;
            }
        }
        return new FileParseStatusView(
                resolvedParseStatus,
                resolvedIndexStatus,
                resolveErrorMessage(parseStatus, indexStatus, latestTask, latestIndexTask)
        );
    }

    private FileParseTaskEntity latestTask(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return fileParseTaskMapper.selectOne(new LambdaQueryWrapper<FileParseTaskEntity>()
                .eq(FileParseTaskEntity::getFileId, fileId)
                .orderByDesc(FileParseTaskEntity::getUpdatedAt)
                .orderByDesc(FileParseTaskEntity::getId)
                .last("limit 1"));
    }

    private RagTaskEntity latestIndexTask(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return ragTaskMapper.selectOne(new LambdaQueryWrapper<RagTaskEntity>()
                .eq(RagTaskEntity::getTaskType, "INDEX")
                .eq(RagTaskEntity::getSourceType, "FILE")
                .eq(RagTaskEntity::getSourceId, fileId)
                .orderByDesc(RagTaskEntity::getUpdatedAt)
                .orderByDesc(RagTaskEntity::getId)
                .last("limit 1"));
    }

    private String resolveErrorMessage(String parseStatus,
                                       String indexStatus,
                                       FileParseTaskEntity latestTask,
                                       RagTaskEntity latestIndexTask) {
        boolean failed = "FAILED".equalsIgnoreCase(parseStatus) || "FAILED".equalsIgnoreCase(indexStatus);
        boolean active = "PROCESSING".equalsIgnoreCase(parseStatus) || "PROCESSING".equalsIgnoreCase(indexStatus);
        if ("SUCCESS".equalsIgnoreCase(parseStatus)) {
            if (latestIndexTask == null || latestIndexTask.getLastError() == null || latestIndexTask.getLastError().isBlank()) {
                return null;
            }
            if (!failed && !active && !isRetrying(latestIndexTask)) {
                return null;
            }
            return latestIndexTask.getLastError();
        }
        if (latestTask == null || latestTask.getLastError() == null || latestTask.getLastError().isBlank()) {
            return null;
        }
        if (!failed && !active && !isRetrying(latestTask)) {
            return null;
        }
        return latestTask.getLastError();
    }

    private boolean isRetrying(FileParseTaskEntity task) {
        return task != null
                && "PENDING".equalsIgnoreCase(task.getTaskStatus())
                && task.getRetryCount() != null
                && task.getRetryCount() > 0
                && task.getLastError() != null
                && !task.getLastError().isBlank();
    }

    private boolean isRetrying(RagTaskEntity task) {
        return task != null
                && "PENDING".equalsIgnoreCase(task.getTaskStatus())
                && task.getRetryCount() != null
                && task.getRetryCount() > 0
                && task.getLastError() != null
                && !task.getLastError().isBlank();
    }

    private boolean isTimeoutLike(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase();
        return normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("read timed out")
                || normalized.contains("gateway timeout");
    }
}

record FileParseStatusView(
        String parseStatus,
        String indexStatus,
        String parseErrorMessage
) {
}
