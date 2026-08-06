package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.dao.TypeDao;
import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.entity.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypeServiceTest {

    private static final int USER_ID = 7;

    @Mock
    private TypeDao typeDao;
    @Mock
    private ActionDao actionDao;

    private TypeService typeService;

    @BeforeEach
    void setUp() {
        typeService = new TypeService(typeDao, actionDao);
        AuthContext.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createRootType_success_setsUserId() {
        Action action = new Action();
        action.setId(1);
        when(actionDao.findById(1)).thenReturn(action);
        doAnswer(invocation -> {
            Type t = invocation.getArgument(0);
            t.setId(12);
            return null;
        }).when(typeDao).insert(any(Type.class));

        Type created = typeService.createType("餐饮", 1, -1);

        assertEquals(12, created.getId());
        assertEquals(USER_ID, created.getUserId());
        assertEquals("餐饮", created.getTName());
        assertEquals(-1, created.getParent());
        assertEquals(1, created.getActionId());
        assertFalse(created.isDisable());
    }

    @Test
    void createChild_rejectsNonRootParent() {
        Action action = new Action();
        action.setId(1);
        when(actionDao.findById(1)).thenReturn(action);

        Type grandparentChild = new Type();
        grandparentChild.setId(34);
        grandparentChild.setParent(12);
        grandparentChild.setDisable(false);
        grandparentChild.setActionId(1);
        when(typeDao.findById(34, USER_ID)).thenReturn(grandparentChild);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> typeService.createType("三级", 1, 34));
        assertEquals("仅支持二级分类", ex.getMessage());
        verify(typeDao, never()).insert(any());
    }

    @Test
    void create_rejectsBlankName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> typeService.createType("  ", 1, -1));
        assertEquals("分类名不能为空", ex.getMessage());
    }

    @Test
    void create_rejectsInvalidActionId() {
        when(actionDao.findById(99)).thenReturn(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> typeService.createType("餐饮", 99, -1));
        assertEquals("actionId 无效", ex.getMessage());
    }

    @Test
    void delete_softDisablesAndCascadesChildren() {
        Type root = new Type();
        root.setId(12);
        root.setUserId(USER_ID);
        root.setParent(-1);
        root.setDisable(false);
        root.setActionId(1);
        when(typeDao.findById(12, USER_ID)).thenReturn(root);

        Type child = new Type();
        child.setId(34);
        child.setUserId(USER_ID);
        child.setParent(12);
        child.setDisable(false);
        when(typeDao.findByParent(12, USER_ID)).thenReturn(List.of(child));

        typeService.deleteType(12);

        ArgumentCaptor<Type> captor = ArgumentCaptor.forClass(Type.class);
        verify(typeDao, times(2)).update(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(Type::isDisable));
    }

    @Test
    void delete_missingType_throwsNotFound() {
        when(typeDao.findById(anyInt(), eq(USER_ID))).thenReturn(null);
        assertThrows(NoSuchElementException.class, () -> typeService.deleteType(999));
    }

    @Test
    void update_rejectsDemotingRootWithChildren() {
        Type root = new Type();
        root.setId(12);
        root.setUserId(USER_ID);
        root.setParent(-1);
        root.setDisable(false);
        root.setActionId(1);
        root.setTName("餐饮");

        Type otherRoot = new Type();
        otherRoot.setId(20);
        otherRoot.setUserId(USER_ID);
        otherRoot.setParent(-1);
        otherRoot.setDisable(false);
        otherRoot.setActionId(1);

        Type child = new Type();
        child.setId(34);
        child.setParent(12);

        when(typeDao.findById(12, USER_ID)).thenReturn(root);
        when(typeDao.findById(20, USER_ID)).thenReturn(otherRoot);
        when(typeDao.findByParent(12, USER_ID)).thenReturn(List.of(child));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> typeService.updateType(12, "餐饮", null, 20));
        assertEquals("请先删除或移动子分类", ex.getMessage());
    }

    @Test
    void queryTypeSingle_scopesByUser() {
        Type type = new Type();
        type.setId(5);
        type.setUserId(USER_ID);
        when(typeDao.findById(5, USER_ID)).thenReturn(type);

        assertSame(type, typeService.queryTypeSingle(5));
        verify(typeDao).findById(5, USER_ID);
    }
}
