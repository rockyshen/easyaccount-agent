package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Type;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TypeDao {

    @Select("SELECT id, t_name AS tName, parent, t_disable AS disable, has_child AS hasChild, archive, "
            + "action_id AS actionId, analysis_disable AS analysisDisable FROM type WHERE id = #{id}")
    Type findById(@Param("id") int id);

    @Select("SELECT id, t_name AS tName, parent, t_disable AS disable, has_child AS hasChild, archive, "
            + "action_id AS actionId, analysis_disable AS analysisDisable FROM type "
            + "WHERE (action_id = #{actionId} OR action_id IS NULL) AND t_disable = false "
            + "AND (archive IS NULL OR archive = false)")
    List<Type> findByActionIdOrNull(@Param("actionId") Integer actionId);
}
