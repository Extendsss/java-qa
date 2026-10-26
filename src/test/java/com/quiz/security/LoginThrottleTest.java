package com.quiz.security;

import com.quiz.common.BizException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {
    @Test void fifthFailureLocksTheNormalizedUsernameOnly() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < 4; i++) {
            throttle.failure(" Alice ");
            assertDoesNotThrow(() -> throttle.check("alice"));
        }
        throttle.failure("ALICE");
        BizException error = assertThrows(BizException.class, () -> throttle.check("alice"));
        assertEquals(1003, error.code);
        assertDoesNotThrow(() -> throttle.check("bob"));
    }

    @Test void successfulLoginClearsAllPreviousFailures() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < 4; i++) throttle.failure("alice");
        throttle.success(" ALICE ");
        throttle.failure("alice");
        assertDoesNotThrow(() -> throttle.check("alice"));
    }
}
