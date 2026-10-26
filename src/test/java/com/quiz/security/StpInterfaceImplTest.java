package com.quiz.security;

import com.quiz.common.BizException;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StpInterfaceImplTest {
    @Test void rolesAreReadFromCurrentDatabaseState() {
        UserMapper users = mock(UserMapper.class);
        SysUser user = new SysUser(); user.id = 7L; user.status = 1; user.role = "USER";
        when(users.findById(7L)).thenReturn(user);
        StpInterfaceImpl provider = new StpInterfaceImpl(users);
        assertEquals(Collections.singletonList("USER"), provider.getRoleList("7", "login"));
        user.role = "ADMIN";
        assertEquals(Collections.singletonList("ADMIN"), provider.getRoleList(7L, "login"));
        verify(users, times(2)).findById(7L);
    }

    @Test void disabledAndMissingUsersCannotRetainRoles() {
        UserMapper users = mock(UserMapper.class);
        SysUser disabled = new SysUser(); disabled.status = 0; disabled.role = "ADMIN";
        when(users.findById(7L)).thenReturn(disabled);
        StpInterfaceImpl provider = new StpInterfaceImpl(users);
        assertEquals(401, assertThrows(BizException.class, () -> provider.getRoleList(7L, "login")).code);
        assertEquals(401, assertThrows(BizException.class, () -> provider.getRoleList(8L, "login")).code);
    }
}
