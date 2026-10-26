package com.quiz.security;

import com.quiz.common.BizException;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionPermissionsTest {
    private SessionService session;
    private SysUser user;

    @BeforeEach void setUp() {
        user = new SysUser(); user.id = 10L; user.role = "USER"; user.status = 1;
        session = spy(new SessionService(mock(UserMapper.class)));
        doReturn(user).when(session).current();
    }

    @Test void privateContentIsVisibleOnlyToItsOwner() {
        assertTrue(session.visible(10L, 0));
        assertFalse(session.visible(11L, 0));
        assertTrue(session.visible(11L, 1));
    }

    @Test void publicationRemovesTheOwnersEditPermission() {
        assertTrue(session.canEdit(10L, 0));
        assertFalse(session.canEdit(10L, 1));
        assertFalse(session.canEdit(11L, 0));
        assertFalse(session.canEdit(11L, 1));
        assertEquals(403, assertThrows(BizException.class, () -> session.requireEdit(10L, 1)).code);
    }

    @Test void administratorsCanViewAndEditAllOwnershipStates() {
        user.role = "ADMIN";
        assertTrue(session.isAdmin());
        assertTrue(session.visible(11L, 0));
        assertTrue(session.visible(11L, 1));
        assertTrue(session.canEdit(11L, 0));
        assertTrue(session.canEdit(11L, 1));
        assertDoesNotThrow(() -> session.requireEdit(11L, 1));
    }
}
