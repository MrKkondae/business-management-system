package com.bms.backend.system.infrastructure.persistence.mybatis.mapper;

import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.LockedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthenticationStateMapper {

    List<LockedUser> findUsersForUpdate(@Param("userId") String userId);

    int updateFailedLogin(
            @Param("userId") String userId,
            @Param("failureCount") int failureCount,
            @Param("accountStatusCode") String accountStatusCode,
            @Param("inactiveReasonCode") String inactiveReasonCode,
            @Param("inactiveAt") LocalDateTime inactiveAt,
            @Param("securityVersion") int securityVersion,
            @Param("modifiedBy") String modifiedBy,
            @Param("modifiedAt") LocalDateTime modifiedAt);

    int updateSuccessfulLogin(
            @Param("userId") String userId,
            @Param("lastLoginAt") LocalDateTime lastLoginAt,
            @Param("modifiedBy") String modifiedBy,
            @Param("modifiedAt") LocalDateTime modifiedAt);
}
