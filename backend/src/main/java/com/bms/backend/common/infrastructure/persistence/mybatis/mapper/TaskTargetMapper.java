package com.bms.backend.common.infrastructure.persistence.mybatis.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskTargetMapper {

    int insert(
            @Param("taskTargetId") String taskTargetId,
            @Param("taskTargetTypeCode") String taskTargetTypeCode,
            @Param("registeredBy") String registeredBy,
            @Param("registeredAt") LocalDateTime registeredAt);
}
