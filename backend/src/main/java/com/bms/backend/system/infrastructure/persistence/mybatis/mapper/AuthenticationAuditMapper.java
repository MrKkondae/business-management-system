package com.bms.backend.system.infrastructure.persistence.mybatis.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthenticationAuditMapper {

    int insertAccessLog(
            @Param("accessLogId") String accessLogId,
            @Param("userId") String userId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("clientIpAddress") String clientIpAddress,
            @Param("successfulFlag") String successfulFlag,
            @Param("registeredBy") String registeredBy,
            @Param("failureReasonCode") String failureReasonCode,
            @Param("requestTraceId") String requestTraceId,
            @Param("protectedLoginId") String protectedLoginId,
            @Param("userAgent") String userAgent);

    int updateLogout(
            @Param("accessLogId") String accessLogId,
            @Param("userId") String userId,
            @Param("logoutTypeCode") String logoutTypeCode,
            @Param("occurredAt") LocalDateTime occurredAt);

    int insertSystemLog(
            @Param("logId") String logId,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("registeredBy") String registeredBy,
            @Param("eventTypeCode") String eventTypeCode,
            @Param("targetTypeCode") String targetTypeCode,
            @Param("targetUserId") String targetUserId,
            @Param("resultCode") String resultCode,
            @Param("changeSummary") String changeSummary,
            @Param("requestTraceId") String requestTraceId,
            @Param("clientIpAddress") String clientIpAddress);
}
