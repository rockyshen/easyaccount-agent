package com.rockyshen.easyaccountagent.dto;

import com.rockyshen.easyaccountagent.entity.Type;
import lombok.Data;

import java.util.List;

@Data
public class TypeListResponseDto {
    private int id;
    private String tName;
    private Integer parent;
    private List<TypeListResponseDto> childrenTypes;

    public TypeListResponseDto convertToDto(Type type) {
        if (type == null) {
            return this;
        }
        setId(type.getId());
        setTName(type.getTName());
        setParent(type.getParent());
        return this;
    }
}
