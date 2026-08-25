package com.townai.auth.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Firebase Admin SDK의 표준 검증 API를 사용하는 ID Token 검증기이다.
 */
@Component
@ConditionalOnProperty(prefix = "town-ai.web-auth", name = "enabled", havingValue = "true")
public class FirebaseAdminIdTokenVerifier implements FirebaseIdTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    /**
     * Firebase Admin 기반 검증기를 생성한다.
     *
     * @param firebaseAuth Firebase Authentication Client
     */
    public FirebaseAdminIdTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public AuthenticatedWebUser verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken);
            return new AuthenticatedWebUser(
                    token.getUid(),
                    token.getEmail(),
                    token.getName()
            );
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            throw new InvalidFirebaseTokenException(exception);
        }
    }
}
