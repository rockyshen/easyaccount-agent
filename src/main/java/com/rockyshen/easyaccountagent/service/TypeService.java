package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.dao.TypeDao;
import com.rockyshen.easyaccountagent.dto.TypeListResponseDto;
import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.entity.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class TypeService {

    private static final int ROOT_PARENT = -1;
    private static final int MAX_NAME_LENGTH = 50;

    private final TypeDao typeDao;
    private final ActionDao actionDao;

    @Transactional(readOnly = true)
    public Type queryTypeSingle(int id) {
        return typeDao.findById(id, AuthContext.requireUserId());
    }

    @Transactional(readOnly = true)
    public List<TypeListResponseDto> queryTypeByActionId(int actionId) {
        int userId = AuthContext.requireUserId();
        List<Type> allTypes = typeDao.findByActionIdOrNull(actionId, userId);
        List<TypeListResponseDto> roots = new ArrayList<>();
        for (Type type : allTypes) {
            if (isRoot(type.getParent())) {
                TypeListResponseDto dto = new TypeListResponseDto();
                dto.convertToDto(type);
                roots.add(dto);
            }
        }
        for (Type type : allTypes) {
            if (!isRoot(type.getParent())) {
                TypeListResponseDto child = new TypeListResponseDto();
                child.convertToDto(type);
                for (TypeListResponseDto parent : roots) {
                    if (parent.getId() == child.getParent()) {
                        if (parent.getChildrenTypes() == null) {
                            parent.setChildrenTypes(new ArrayList<>());
                        }
                        parent.getChildrenTypes().add(child);
                    }
                }
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveTypes(int userId) {
        return typeDao.countActiveByUserId(userId) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public Type createType(String tName, Integer actionId, Integer parent) {
        int userId = AuthContext.requireUserId();
        String name = requireName(tName);
        int resolvedActionId = requireActionId(actionId);
        int resolvedParent = normalizeParent(parent);

        if (resolvedParent != ROOT_PARENT) {
            Type parentType = requireActiveType(resolvedParent, userId);
            if (!isRoot(parentType.getParent())) {
                throw new IllegalArgumentException("仅支持二级分类");
            }
            if (parentType.getActionId() != null && parentType.getActionId() != resolvedActionId) {
                throw new IllegalArgumentException("子分类的 actionId 须与父分类一致");
            }
        }

        Type type = new Type();
        type.setUserId(userId);
        type.setTName(name);
        type.setParent(resolvedParent);
        type.setActionId(resolvedActionId);
        type.setDisable(false);
        type.setHasChild(false);
        type.setArchive(false);
        type.setAnalysisDisable(false);
        typeDao.insert(type);
        return type;
    }

    @Transactional(rollbackFor = Exception.class)
    public Type updateType(int id, String tName, Integer actionId, Integer parent) {
        int userId = AuthContext.requireUserId();
        Type type = requireActiveType(id, userId);
        String name = requireName(tName);
        type.setTName(name);

        Integer resolvedActionId = type.getActionId();
        if (actionId != null) {
            resolvedActionId = requireActionId(actionId);
            type.setActionId(resolvedActionId);
        }

        if (parent != null) {
            int resolvedParent = normalizeParent(parent);
            if (resolvedParent == id) {
                throw new IllegalArgumentException("父分类不能是自己");
            }
            if (resolvedParent != ROOT_PARENT) {
                Type parentType = requireActiveType(resolvedParent, userId);
                if (!isRoot(parentType.getParent())) {
                    throw new IllegalArgumentException("仅支持二级分类");
                }
                if (isRoot(type.getParent()) && parentType.getParent() != null && parentType.getParent() == id) {
                    throw new IllegalArgumentException("不能将分类挂到自己的子分类下");
                }
                if (isRoot(type.getParent()) && !typeDao.findByParent(id, userId).isEmpty()) {
                    throw new IllegalArgumentException("请先删除或移动子分类");
                }
                if (resolvedActionId != null
                        && parentType.getActionId() != null
                        && !resolvedActionId.equals(parentType.getActionId())) {
                    throw new IllegalArgumentException("子分类的 actionId 须与父分类一致");
                }
            }
            type.setParent(resolvedParent);
        }

        if (isRoot(type.getParent()) && actionId != null) {
            for (Type child : typeDao.findByParent(id, userId)) {
                child.setActionId(resolvedActionId);
                typeDao.update(child);
            }
        }

        typeDao.update(type);
        return type;
    }

    /**
     * 软删/停用；若为一级分类则级联停用子分类（与 EasyAccounts 原版 disableType 一致）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(int id) {
        int userId = AuthContext.requireUserId();
        Type type = requireActiveType(id, userId);
        type.setDisable(true);
        typeDao.update(type);
        if (isRoot(type.getParent())) {
            for (Type child : typeDao.findByParent(id, userId)) {
                child.setDisable(true);
                typeDao.update(child);
            }
        }
    }

    private Type requireActiveType(int id, int userId) {
        Type type = typeDao.findById(id, userId);
        if (type == null || type.isDisable() || Boolean.TRUE.equals(type.getArchive())) {
            throw new NoSuchElementException("分类不存在或已停用");
        }
        return type;
    }

    private int requireActionId(Integer actionId) {
        if (actionId == null) {
            throw new IllegalArgumentException("actionId 不能为空");
        }
        Action action = actionDao.findById(actionId);
        if (action == null) {
            throw new IllegalArgumentException("actionId 无效");
        }
        return actionId;
    }

    private static String requireName(String tName) {
        if (tName == null || tName.isBlank()) {
            throw new IllegalArgumentException("分类名不能为空");
        }
        String trimmed = tName.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("分类名不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static int normalizeParent(Integer parent) {
        if (parent == null || parent == 0) {
            return ROOT_PARENT;
        }
        return parent;
    }

    private static boolean isRoot(Integer parent) {
        return parent == null || parent == ROOT_PARENT || parent == 0;
    }
}
