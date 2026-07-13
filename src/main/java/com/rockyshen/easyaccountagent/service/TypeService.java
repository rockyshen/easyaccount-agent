package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.dao.TypeDao;
import com.rockyshen.easyaccountagent.dto.TypeListResponseDto;
import com.rockyshen.easyaccountagent.entity.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TypeService {

    private final TypeDao typeDao;

    @Transactional(readOnly = true)
    public Type queryTypeSingle(int id) {
        return typeDao.findById(id);
    }

    @Transactional(readOnly = true)
    public List<TypeListResponseDto> queryTypeByActionId(int actionId) {
        List<Type> allTypes = typeDao.findByActionIdOrNull(actionId);
        List<TypeListResponseDto> roots = new ArrayList<>();
        for (Type type : allTypes) {
            if (type.getParent() == -1) {
                TypeListResponseDto dto = new TypeListResponseDto();
                dto.convertToDto(type);
                roots.add(dto);
            }
        }
        for (Type type : allTypes) {
            if (type.getParent() != -1) {
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
}
