package com.rockyshen.easyaccountagent.tool;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyAccountsToolFunctionsDateTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void resolveDate_blank_usesShanghaiToday() throws Exception {
        String today = LocalDate.now(APP_ZONE).toString();
        assertEquals(today, invokeResolveDate(null));
        assertEquals(today, invokeResolveDate(""));
        assertEquals(today, invokeResolveDate("   "));
    }

    @Test
    void resolveDate_explicitPastDate_accepted() throws Exception {
        assertEquals("2026-07-20", invokeResolveDate("2026-07-20"));
    }

    @Test
    void resolveDate_rejectsIllegalFormat() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invokeResolveDate("2026年7月25日"));
        assertTrue(ex.getMessage().contains("yyyy-MM-dd"));
    }

    @Test
    void resolveDate_rejectsFutureDate() {
        String future = LocalDate.now(APP_ZONE).plusDays(1).toString();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invokeResolveDate(future));
        assertTrue(ex.getMessage().contains("不能记录未来日期"));
    }

    private static String invokeResolveDate(String date) throws Exception {
        Method m = EasyAccountsToolFunctions.class.getDeclaredMethod("resolveDate", String.class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(null, date);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
