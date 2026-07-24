package com.bms.backend.system.infrastructure.persistence.mybatis.mapper;

import com.bms.backend.system.application.authentication.AuthenticationMenu;
import com.bms.backend.system.application.authentication.AuthenticationRole;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.ReauthenticationCredential;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.SessionState;
import com.bms.backend.system.infrastructure.persistence.mybatis.model.AuthenticationPersistenceRows.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthenticationUserMapper {

    List<User> findLoginCandidates(@Param("loginId") String loginId);

    List<AuthenticationRole> findRoles(@Param("userId") String userId);

    List<AuthenticationMenu> findMenus(@Param("userId") String userId);

    List<SessionState> findSessionStates(@Param("userId") String userId);

    List<ReauthenticationCredential> findReauthenticationCredentials(
            @Param("userId") String userId);
}
