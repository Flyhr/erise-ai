package com.erise.ai.backend.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.time.LocalDateTime;
import lombok.Data;
/**
 * 可审计实体类
 * 用于记录实体的创建和更新信息,封装所有业务实体通用的「创建、更新、逻辑删除」审计字段
 */
@Data
public class AuditableEntity {

    private Long createdBy;

// 记录数据的创建时间；MyBatis-Plus 会在「插入（INSERT）」操作时自动填充该字段
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
// 记录数据的最后更新人 ID（需手动赋值）
    private Long updatedBy;
// 记录数据的最后更新时间；MyBatis-Plus 会在「插入 / 更新」操作时自动填充
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
/*逻辑删除标识（替代物理删除）；MyBatis-Plus 自动处理：
1. 删除操作 → 改为更新该字段（如 0→1）；
2. 查询操作 → 自动过滤 deleted=1 的数据
*/
    @TableLogic
    private Integer deleted;
}
