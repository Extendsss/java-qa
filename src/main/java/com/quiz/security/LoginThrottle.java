package com.quiz.security;

import com.quiz.common.BizException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class LoginThrottle {
    private static final long WINDOW = 5 * 60 * 1000L;
    private final ConcurrentMap<String, Attempt> attempts = new ConcurrentHashMap<String, Attempt>();
    private static class Attempt { int failures; long expiresAt; }
    private String key(String username) { return username.trim().toLowerCase(Locale.ROOT); }
    public void check(String username) {
        Attempt value = attempts.get(key(username));
        if (value != null && value.expiresAt > System.currentTimeMillis() && value.failures >= 5)
            throw new BizException(1003, "失败次数过多，请 5 分钟后再试");
    }
    public void failure(String username) {
        final long now = System.currentTimeMillis();
        attempts.compute(key(username), (name, value) -> {
            if (value == null || value.expiresAt <= now) value = new Attempt();
            value.failures++;
            value.expiresAt = now + WINDOW;
            return value;
        });
        if (attempts.size() > 10000) attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }
    public void success(String username) { attempts.remove(key(username)); }
}
