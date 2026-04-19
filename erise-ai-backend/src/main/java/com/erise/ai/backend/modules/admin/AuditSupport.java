package com.erise.ai.backend.modules;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erise.ai.backend.common.entity.AuditableEntity;
import com.erise.ai.backend.common.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    void log(CurrentUser operator, String actionCode, String resourceType, Long resourceId, Object detail) {
        AuditLogEntity entity = new AuditLogEntity();
        if (operator != null) {
            entity.setOperatorUserId(operator.userId());
            entity.setOperatorUsername(operator.username());
            entity.setCreatedBy(operator.userId());
            entity.setUpdatedBy(operator.userId());
        } else {
            entity.setCreatedBy(0L);
            entity.setUpdatedBy(0L);
        }
        entity.setActionCode(actionCode);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setDetailJson(writeJson(detail));
        auditLogMapper.insert(entity);
    }

    void log(Long operatorUserId, String actionCode, String resourceType, Long resourceId, Object detail) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setOperatorUserId(operatorUserId);
        entity.setOperatorUsername(operatorUserId == null ? "system" : "ai-action:" + operatorUserId);
        entity.setCreatedBy(operatorUserId == null ? 0L : operatorUserId);
        entity.setUpdatedBy(operatorUserId == null ? 0L : operatorUserId);
        entity.setActionCode(actionCode);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setDetailJson(writeJson(detail));
        auditLogMapper.insert(entity);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}

interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}

@Data
@TableName("ea_audit_log")
class AuditLogEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorUserId;
    private String operatorUsername;
    private String actionCode;
    private String resourceType;
    private Long resourceId;
    private String detailJson;
}
