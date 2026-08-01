package com.rockyshen.easyaccountagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rockyshen.easyaccountagent.entity.Type;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TypeListResponseDto {
    private int id;

    /** 与现网 iOS 兼容：JSON 字段名为 tname */
    @JsonProperty("tname")
    private String tName;

    private Integer parent;
    private List<TypeListResponseDto> childrenTypes;

    public TypeListResponseDto convertToDto(Type type) {
        if (type == null) {
            return this;
        }
        setId(type.getId());
        setTName(type.getTName());
        Integer parentId = type.getParent();
        setParent(parentId == null || parentId == 0 ? -1 : parentId);
        return this;
    }

    public static TypeListResponseDto fromEntity(Type type) {
        TypeListResponseDto dto = new TypeListResponseDto();
        dto.convertToDto(type);
        dto.setChildrenTypes(new ArrayList<>());
        return dto;
    }
}
