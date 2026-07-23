package com.bms.backend.system.application.authentication;

import com.bms.backend.common.domain.authentication.NormalizedLoginId;
import java.util.Optional;

public interface AuthenticationUserQuery {

    Optional<AuthenticationUser> findLoginCandidate(NormalizedLoginId loginId);

    AuthenticationAuthorizationSnapshot findAuthorizationSnapshot(String userId);

    Optional<AuthenticationSessionState> findSessionState(String userId);

    Optional<ReauthenticationCredential> findReauthenticationCredential(String userId);
}
