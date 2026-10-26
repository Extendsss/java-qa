package com.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.common.BizException;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import com.quiz.security.LoginThrottle;
import com.quiz.security.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private UserMapper mapper;
    private SessionService session;
    private LoginThrottle throttle;
    private UserService service;

    @BeforeEach void setUp() {
        mapper = mock(UserMapper.class);
        session = mock(SessionService.class);
        throttle = mock(LoginThrottle.class);
        service = new UserService(mapper, session, throttle);
    }

    @Test void accountCreationStoresBcryptAndServerControlledStatus() {
        SysUser stored = new SysUser(); stored.id = 42L;
        doAnswer(call -> { ((SysUser) call.getArgument(0)).id = 42L; return null; }).when(mapper).insert(any(SysUser.class));
        when(mapper.findById(42L)).thenReturn(stored);
        assertSame(stored, service.create(" alice ", "secret123", null, "USER"));
        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(mapper).insert(user.capture());
        assertEquals("alice", user.getValue().username);
        assertEquals("alice", user.getValue().nickname);
        assertEquals("USER", user.getValue().role);
        assertEquals(Integer.valueOf(1), user.getValue().status);
        assertNotEquals("secret123", user.getValue().password);
        assertTrue(new BCryptPasswordEncoder().matches("secret123", user.getValue().password));
    }

    @Test void duplicateUsernameIsRejectedBeforeWriting() {
        when(mapper.findByUsername("alice")).thenReturn(new SysUser());
        assertEquals(1004, assertThrows(BizException.class, () -> service.create("alice", "secret123", "A", "USER")).code);
        verify(mapper, never()).insert(any());
    }

    @Test void concurrentDuplicateInsertIsConvertedToBusinessError() {
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any());
        assertEquals(1004, assertThrows(BizException.class, () -> service.create("alice", "secret123", "A", "USER")).code);
    }

    @Test void arbitraryRolesAreRejected() {
        assertEquals(400, assertThrows(BizException.class, () -> service.create("alice", "secret123", "A", "SUPERUSER")).code);
        verifyNoInteractions(mapper);
    }

    @Test void administratorCannotDisableOrDemoteSelf() {
        SysUser admin = new SysUser(); admin.id = 1L; admin.role = "ADMIN"; admin.status = 1;
        when(mapper.findById(1L)).thenReturn(admin);
        when(session.current()).thenReturn(admin);
        assertEquals(403, assertThrows(BizException.class, () -> service.update(1L, "Admin", "USER", 1)).code);
        assertEquals(403, assertThrows(BizException.class, () -> service.update(1L, "Admin", "ADMIN", 0)).code);
        verify(mapper, never()).update(any());
    }

    @Test void unknownUsersAndInvalidStatusCannotBeUpdated() {
        assertEquals(404, assertThrows(BizException.class, () -> service.update(99L, "User", "USER", 1)).code);
        SysUser user = new SysUser(); user.id = 2L;
        when(mapper.findById(2L)).thenReturn(user);
        assertEquals(400, assertThrows(BizException.class, () -> service.update(2L, "User", "USER", 2)).code);
        verify(mapper, never()).update(any());
    }

    @Test void loginThrottleRunsBeforeDatabaseLookup() {
        doThrow(new BizException(1003, "locked")).when(throttle).check("alice");
        assertEquals(1003, assertThrows(BizException.class, () -> service.login("alice", "secret123")).code);
        verifyNoInteractions(mapper);
    }

    @Test void unknownUsernameUsesTheSameCredentialErrorAndCountsFailure() {
        assertEquals(1001, assertThrows(BizException.class, () -> service.login("alice", "secret123")).code);
        verify(throttle).failure("alice");
        verify(throttle, never()).success(anyString());
    }

    @Test void invalidPasswordCountsFailureAndDoesNotLogin() {
        SysUser user = new SysUser(); user.password = new BCryptPasswordEncoder().encode("correct123"); user.status = 1;
        when(mapper.findByUsername("alice")).thenReturn(user);
        assertEquals(1001, assertThrows(BizException.class, () -> service.login("alice", "incorrect")).code);
        verify(throttle).failure("alice");
        verify(throttle, never()).success(anyString());
    }

    @Test void disabledUserWithCorrectCredentialsCannotLogin() {
        SysUser user = new SysUser(); user.password = new BCryptPasswordEncoder().encode("correct123"); user.status = 0;
        when(mapper.findByUsername("alice")).thenReturn(user);
        assertEquals(1002, assertThrows(BizException.class, () -> service.login("alice", "correct123")).code);
        verify(throttle, never()).success(anyString());
    }

    @Test void incorrectOldPasswordDoesNotWriteOrInvalidateSessions() {
        SysUser user = new SysUser(); user.id = 3L; user.password = new BCryptPasswordEncoder().encode("correct123");
        when(session.current()).thenReturn(user);
        assertEquals(1009, assertThrows(BizException.class, () -> service.changePassword("incorrect", "newsecret")).code);
        verify(mapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test void userSerializationNeverContainsThePasswordHash() throws Exception {
        SysUser user = new SysUser(); user.id = 1L; user.username = "alice"; user.password = "$2a$secret-hash";
        JsonNode data = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(user));
        assertEquals("alice", data.path("username").asText());
        assertFalse(data.has("password"));
    }
}
