package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erise.ai.backend.common.api.PageResponse;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TaskService {

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    Long createTask(CurrentUser operator, String taskType, Object payload) {
        TaskEntity entity = new TaskEntity();
        entity.setOwnerUserId(operator == null ? null : operator.userId());
        entity.setTaskType(taskType);
        entity.setTaskStatus("PENDING");
        entity.setPayloadJson(toJson(payload));
        entity.setCreatedBy(operator == null ? 0L : operator.userId());
        entity.setUpdatedBy(operator == null ? 0L : operator.userId());
        taskMapper.insert(entity);
        return entity.getId();
    }

    void markSuccess(Long taskId, Object result) {
        TaskEntity entity = new TaskEntity();
        entity.setId(taskId);
        entity.setTaskStatus("SUCCESS");
        entity.setResultJson(toJson(result));
        taskMapper.updateById(entity);
    }

    void markFailure(Long taskId, String error) {
        TaskEntity entity = new TaskEntity();
        entity.setId(taskId);
        entity.setTaskStatus("FAILED");
        entity.setLastError(error);
        taskMapper.updateById(entity);
    }

    PageResponse<TaskSummary> page(long pageNum, long pageSize) {
        Page<TaskEntity> page = taskMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<TaskEntity>().orderByDesc(TaskEntity::getCreatedAt));
        List<TaskSummary> records = page.getRecords().stream()
                .map(entity -> new TaskSummary(entity.getId(), entity.getTaskType(), entity.getTaskStatus(),
                        entity.getRetryCount(), entity.getLastError(), entity.getCreatedAt()))
                .toList();
        return PageResponse.of(records, pageNum, pageSize, page.getTotal());
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}

interface TaskMapper extends BaseMapper<TaskEntity> {
}

record TaskSummary(Long id, String taskType, String taskStatus, Integer retryCount, String lastError,
                   java.time.LocalDateTime createdAt) {
}

@Data
@TableName("ea_task")
class TaskEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String taskType;
    private String taskStatus;
    private String payloadJson;
    private String resultJson;
    private Integer retryCount;
    private String lastError;
}
