package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Type;
import org.apache.ibatis.annotations.*;

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

    @Select("SELECT id, t_name AS tName, parent, t_disable AS disable, has_child AS hasChild, archive, "
            + "action_id AS actionId, analysis_disable AS analysisDisable FROM type "
            + "WHERE parent = #{parent} AND t_disable = false "
            + "AND (archive IS NULL OR archive = false)")
    List<Type> findByParent(@Param("parent") int parent);

    @Insert("INSERT INTO type (t_name, parent, t_disable, has_child, archive, action_id, analysis_disable) "
            + "VALUES (#{tName}, #{parent}, #{disable}, #{hasChild}, #{archive}, #{actionId}, #{analysisDisable})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Type type);

    @Update("UPDATE type SET t_name=#{tName}, parent=#{parent}, t_disable=#{disable}, "
            + "has_child=#{hasChild}, archive=#{archive}, action_id=#{actionId}, "
            + "analysis_disable=#{analysisDisable} WHERE id=#{id}")
    void update(Type type);
}
