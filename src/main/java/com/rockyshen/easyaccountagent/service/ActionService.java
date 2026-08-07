package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.entity.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionService {

    /** 客户端 Tab / Agent 常用的三类：收入、支出、内部转账 */
    private static final int[] PRIMARY_HANDLES = {
            ContentValues.ACTION_ADD,
            ContentValues.ACTION_SUB,
            ContentValues.ACTION_INNER
    };

    private final ActionDao actionDao;

    /**
     * 对外列表：仅返回非豁免的收入 / 支出 / 内部转账（每类取 id 最小的一条）。
     * 借入、还钱、借出、收钱等历史扩展项不展示。
     */
    @Transactional(readOnly = true)
    public List<Action> getActions() {
        return getPrimaryActions();
    }

    @Transactional(readOnly = true)
    public List<Action> getPrimaryActions() {
        List<Action> all = actionDao.findAll();
        List<Action> primary = new ArrayList<>(PRIMARY_HANDLES.length);
        for (int handle : PRIMARY_HANDLES) {
            all.stream()
                    .filter(a -> a.getHandle() == handle && !a.isExempt())
                    .min(Comparator.comparingInt(Action::getId))
                    .ifPresent(primary::add);
        }
        return primary;
    }

    @Transactional(readOnly = true)
    public Action getAction(int id) {
        return actionDao.findById(id);
    }
}
