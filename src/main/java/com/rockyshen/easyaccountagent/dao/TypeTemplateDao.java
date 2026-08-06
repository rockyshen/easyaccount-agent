package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.TypeTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TypeTemplateDao {

    @Select("SELECT id, t_name AS tName, parent, action_handle AS actionHandle, sort_order AS sortOrder "
            + "FROM type_template ORDER BY action_handle, sort_order, id")
    List<TypeTemplate> findAllOrdered();

    @Select("SELECT COUNT(1) FROM type_template")
    int countAll();

    @Insert("INSERT INTO type_template (t_name, parent, action_handle, sort_order) "
            + "VALUES (#{tName}, #{parent}, #{actionHandle}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TypeTemplate template);
}
