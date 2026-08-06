package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.dao.TypeDao;
import com.rockyshen.easyaccountagent.dao.TypeTemplateDao;
import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.entity.Type;
import com.rockyshen.easyaccountagent.entity.TypeTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局 action ensure + type 模板种子 + 按用户克隆预设分类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TypeSeedService {

    private static final int ROOT_PARENT = -1;

    private final ActionDao actionDao;
    private final TypeTemplateDao typeTemplateDao;
    private final TypeDao typeDao;

    @Transactional(rollbackFor = Exception.class)
    public void ensureGlobalActions() {
        ensureAction(ContentValues.ACTION_ADD, "收入");
        ensureAction(ContentValues.ACTION_SUB, "支出");
        ensureAction(ContentValues.ACTION_INNER, "转账");
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureTypeTemplates() {
        if (typeTemplateDao.countAll() > 0) {
            return;
        }
        log.info("[Onboarding] type_template empty, seeding built-in presets");
        int sort = 0;
        // 支出
        int food = insertRoot("餐饮", ContentValues.ACTION_SUB, sort++);
        insertChild("早餐", food, ContentValues.ACTION_SUB, sort++);
        insertChild("午餐", food, ContentValues.ACTION_SUB, sort++);
        insertChild("晚餐", food, ContentValues.ACTION_SUB, sort++);
        insertChild("咖啡", food, ContentValues.ACTION_SUB, sort++);
        insertChild("外卖", food, ContentValues.ACTION_SUB, sort++);

        int transport = insertRoot("交通", ContentValues.ACTION_SUB, sort++);
        insertChild("地铁", transport, ContentValues.ACTION_SUB, sort++);
        insertChild("打车", transport, ContentValues.ACTION_SUB, sort++);
        insertChild("加油", transport, ContentValues.ACTION_SUB, sort++);

        int shopping = insertRoot("购物", ContentValues.ACTION_SUB, sort++);
        insertChild("日用", shopping, ContentValues.ACTION_SUB, sort++);
        insertChild("服装", shopping, ContentValues.ACTION_SUB, sort++);
        insertChild("数码", shopping, ContentValues.ACTION_SUB, sort++);

        int housing = insertRoot("居住", ContentValues.ACTION_SUB, sort++);
        insertChild("房租", housing, ContentValues.ACTION_SUB, sort++);
        insertChild("水电", housing, ContentValues.ACTION_SUB, sort++);
        insertChild("物业", housing, ContentValues.ACTION_SUB, sort++);

        int fun = insertRoot("娱乐", ContentValues.ACTION_SUB, sort++);
        insertChild("电影", fun, ContentValues.ACTION_SUB, sort++);
        insertChild("游戏", fun, ContentValues.ACTION_SUB, sort++);
        insertChild("订阅", fun, ContentValues.ACTION_SUB, sort++);

        int medical = insertRoot("医疗", ContentValues.ACTION_SUB, sort++);
        insertChild("药品", medical, ContentValues.ACTION_SUB, sort++);
        insertChild("诊疗", medical, ContentValues.ACTION_SUB, sort++);

        insertRoot("其他支出", ContentValues.ACTION_SUB, sort++);

        // 收入
        int salary = insertRoot("工资", ContentValues.ACTION_ADD, sort++);
        insertChild("月薪", salary, ContentValues.ACTION_ADD, sort++);
        insertChild("奖金", salary, ContentValues.ACTION_ADD, sort++);

        int invest = insertRoot("理财", ContentValues.ACTION_ADD, sort++);
        insertChild("利息", invest, ContentValues.ACTION_ADD, sort++);
        insertChild("分红", invest, ContentValues.ACTION_ADD, sort++);

        insertRoot("其他收入", ContentValues.ACTION_ADD, sort++);

        // 转账
        insertRoot("账户互转", ContentValues.ACTION_INNER, sort++);
        insertRoot("信用卡还款", ContentValues.ACTION_INNER, sort++);
    }

    /**
     * 若用户尚无任何活跃分类，则从模板克隆一份个人分类树。幂等。
     *
     * @return 是否新克隆了分类
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cloneTemplateForUserIfEmpty(int userId) {
        if (typeDao.countActiveByUserId(userId) > 0) {
            return false;
        }
        ensureGlobalActions();
        ensureTypeTemplates();

        Map<Integer, Integer> actionIdByHandle = resolveActionIds();
        List<TypeTemplate> templates = typeTemplateDao.findAllOrdered();
        if (templates.isEmpty()) {
            log.warn("[Onboarding] no type_template rows; skip clone for userId={}", userId);
            return false;
        }

        Map<Integer, Integer> templateIdToTypeId = new HashMap<>();
        List<TypeTemplate> roots = new ArrayList<>();
        List<TypeTemplate> children = new ArrayList<>();
        for (TypeTemplate t : templates) {
            if (t.getParent() == null || t.getParent() == ROOT_PARENT || t.getParent() == 0) {
                roots.add(t);
            } else {
                children.add(t);
            }
        }

        for (TypeTemplate root : roots) {
            Integer actionId = actionIdByHandle.get(root.getActionHandle());
            if (actionId == null) {
                log.warn("[Onboarding] missing action for handle={}, skip template id={}",
                        root.getActionHandle(), root.getId());
                continue;
            }
            Type type = newType(userId, root.getTName(), ROOT_PARENT, actionId);
            typeDao.insert(type);
            templateIdToTypeId.put(root.getId(), type.getId());
        }

        for (TypeTemplate child : children) {
            Integer parentTypeId = templateIdToTypeId.get(child.getParent());
            if (parentTypeId == null) {
                log.warn("[Onboarding] missing parent mapping for template id={}, parent={}",
                        child.getId(), child.getParent());
                continue;
            }
            Integer actionId = actionIdByHandle.get(child.getActionHandle());
            if (actionId == null) {
                continue;
            }
            Type type = newType(userId, child.getTName(), parentTypeId, actionId);
            typeDao.insert(type);
            templateIdToTypeId.put(child.getId(), type.getId());
        }

        log.info("[Onboarding] cloned {} type templates for userId={}", templateIdToTypeId.size(), userId);
        return !templateIdToTypeId.isEmpty();
    }

    private void ensureAction(int handle, String name) {
        Action existing = actionDao.findByHandle(handle);
        if (existing != null) {
            return;
        }
        Action action = new Action();
        action.setHName(name);
        action.setExempt(false);
        action.setHandle(handle);
        actionDao.insert(action);
        log.info("[Onboarding] inserted global action handle={} name={}", handle, name);
    }

    private Map<Integer, Integer> resolveActionIds() {
        Map<Integer, Integer> map = new HashMap<>();
        for (Action action : actionDao.findAll()) {
            map.putIfAbsent(action.getHandle(), action.getId());
        }
        return map;
    }

    private int insertRoot(String name, int handle, int sortOrder) {
        TypeTemplate t = new TypeTemplate();
        t.setTName(name);
        t.setParent(ROOT_PARENT);
        t.setActionHandle(handle);
        t.setSortOrder(sortOrder);
        typeTemplateDao.insert(t);
        return t.getId();
    }

    private void insertChild(String name, int parentId, int handle, int sortOrder) {
        TypeTemplate t = new TypeTemplate();
        t.setTName(name);
        t.setParent(parentId);
        t.setActionHandle(handle);
        t.setSortOrder(sortOrder);
        typeTemplateDao.insert(t);
    }

    private static Type newType(int userId, String name, int parent, int actionId) {
        Type type = new Type();
        type.setUserId(userId);
        type.setTName(name);
        type.setParent(parent);
        type.setActionId(actionId);
        type.setDisable(false);
        type.setHasChild(false);
        type.setArchive(false);
        type.setAnalysisDisable(false);
        return type;
    }
}
