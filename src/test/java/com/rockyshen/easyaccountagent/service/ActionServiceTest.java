package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.entity.Action;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    @Mock
    private ActionDao actionDao;

    @InjectMocks
    private ActionService actionService;

    @Test
    void getActions_returnsOnlyPrimaryNonExempt() {
        when(actionDao.findAll()).thenReturn(List.of(
                action(15, "收入", ContentValues.ACTION_ADD, false),
                action(16, "支出", ContentValues.ACTION_SUB, false),
                action(17, "内部转账", ContentValues.ACTION_INNER, false),
                action(18, "借入", ContentValues.ACTION_ADD, true),
                action(19, "还钱", ContentValues.ACTION_SUB, true),
                action(20, "借出", ContentValues.ACTION_SUB, true),
                action(21, "收钱", ContentValues.ACTION_ADD, true)
        ));

        List<Action> primary = actionService.getActions();
        assertEquals(3, primary.size());
        assertEquals("收入", primary.get(0).getHName());
        assertEquals("支出", primary.get(1).getHName());
        assertEquals("内部转账", primary.get(2).getHName());
        assertTrue(primary.stream().noneMatch(Action::isExempt));
    }

    @Test
    void getActions_picksSmallestIdPerHandle() {
        when(actionDao.findAll()).thenReturn(List.of(
                action(30, "收入-旧", ContentValues.ACTION_ADD, false),
                action(15, "收入", ContentValues.ACTION_ADD, false),
                action(16, "支出", ContentValues.ACTION_SUB, false)
        ));

        List<Action> primary = actionService.getActions();
        assertEquals(2, primary.size());
        assertEquals(15, primary.get(0).getId());
        assertEquals("收入", primary.get(0).getHName());
    }

    private static Action action(int id, String name, int handle, boolean exempt) {
        Action a = new Action();
        a.setId(id);
        a.setHName(name);
        a.setHandle(handle);
        a.setExempt(exempt);
        return a;
    }
}
