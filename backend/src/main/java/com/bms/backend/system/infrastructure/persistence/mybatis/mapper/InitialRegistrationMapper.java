package com.bms.backend.system.infrastructure.persistence.mybatis.mapper;

import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.InitialRegistrationAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InitialRegistrationMapper {

    List<InitialRegistrationAccount> findAccountsForUpdate(@Param("userId") String userId);

    int complete(
            @Param("userId") String userId,
            @Param("newPasswordHash") String newPasswordHash,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("newSecurityVersion") int newSecurityVersion,
            @Param("emailAddress") String emailAddress,
            @Param("mobileNumber") String mobileNumber,
            @Param("expectedSecurityVersion") int expectedSecurityVersion,
            @Param("expectedPasswordChangedAt") LocalDateTime expectedPasswordChangedAt);
}
