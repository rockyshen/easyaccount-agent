package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.dao.TypeDao;
import com.rockyshen.easyaccountagent.dao.TypeTemplateDao;
import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.entity.Type;
import com.rockyshen.easyaccountagent.entity.TypeTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypeSeedServiceTest {

    @Mock
    private ActionDao actionDao;
    @Mock
    private TypeTemplateDao typeTemplateDao;
    @Mock
    private TypeDao typeDao;

    @InjectMocks
    private TypeSeedService typeSeedService;

    @Test
    void ensureGlobalActions_insertsMissingHandles() {
        when(actionDao.findByHandle(ContentValues.ACTION_ADD)).thenReturn(null);
        when(actionDao.findByHandle(ContentValues.ACTION_SUB)).thenReturn(action(2, ContentValues.ACTION_SUB));
        when(actionDao.findByHandle(ContentValues.ACTION_INNER)).thenReturn(null);

        typeSeedService.ensureGlobalActions();

        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(actionDao, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(a -> a.getHandle() == ContentValues.ACTION_ADD));
        assertTrue(captor.getAllValues().stream().anyMatch(a -> a.getHandle() == ContentValues.ACTION_INNER));
        verify(actionDao, never()).insert(argThat(a -> a.getHandle() == ContentValues.ACTION_SUB));
    }

    @Test
    void cloneTemplateForUserIfEmpty_skipsWhenUserHasTypes() {
        when(typeDao.countActiveByUserId(9)).thenReturn(3);
        assertFalse(typeSeedService.cloneTemplateForUserIfEmpty(9));
        verify(typeDao, never()).insert(any());
    }

    @Test
    void cloneTemplateForUserIfEmpty_clonesRootsAndChildren() {
        when(typeDao.countActiveByUserId(9)).thenReturn(0);
        when(actionDao.findByHandle(anyInt())).thenAnswer(inv -> {
            int handle = inv.getArgument(0);
            return action(handle + 1, handle);
        });
        when(actionDao.findAll()).thenReturn(List.of(
                action(1, ContentValues.ACTION_ADD),
                action(2, ContentValues.ACTION_SUB),
                action(3, ContentValues.ACTION_INNER)));
        when(typeTemplateDao.countAll()).thenReturn(2);

        TypeTemplate root = new TypeTemplate();
        root.setId(100);
        root.setTName("餐饮");
        root.setParent(-1);
        root.setActionHandle(ContentValues.ACTION_SUB);

        TypeTemplate child = new TypeTemplate();
        child.setId(101);
        child.setTName("午餐");
        child.setParent(100);
        child.setActionHandle(ContentValues.ACTION_SUB);

        when(typeTemplateDao.findAllOrdered()).thenReturn(List.of(root, child));
        doAnswer(inv -> {
            Type t = inv.getArgument(0);
            if ("餐饮".equals(t.getTName())) {
                t.setId(1000);
            } else {
                t.setId(1001);
            }
            return null;
        }).when(typeDao).insert(any(Type.class));

        assertTrue(typeSeedService.cloneTemplateForUserIfEmpty(9));

        ArgumentCaptor<Type> captor = ArgumentCaptor.forClass(Type.class);
        verify(typeDao, times(2)).insert(captor.capture());
        Type insertedRoot = captor.getAllValues().get(0);
        Type insertedChild = captor.getAllValues().get(1);
        assertEquals(9, insertedRoot.getUserId());
        assertEquals("餐饮", insertedRoot.getTName());
        assertEquals(-1, insertedRoot.getParent());
        assertEquals(9, insertedChild.getUserId());
        assertEquals("午餐", insertedChild.getTName());
        assertEquals(1000, insertedChild.getParent());
    }

    private static Action action(int id, int handle) {
        Action a = new Action();
        a.setId(id);
        a.setHandle(handle);
        a.setHName("n" + handle);
        return a;
    }
}
