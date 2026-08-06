package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Type;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TypeDao {

    @Select("SELECT id, user_id AS userId, t_name AS tName, parent, t_disable AS disable, has_child AS hasChild, archive, "
            + "action_id AS actionId, analysis_disable AS analysisDisable FROM type "
            + "WHERE id = #{id} AND user_id = #{userId}")
    Type findById(@Param("id") int id, @Param("userId") int userId);

    @Select("SELECT id, user_id AS userId, t_name AS tName, parent, t_disable AS disable, has_child AS hasChild, archive, "
            + "action_id AS actionId, analysis_disable AS analysisDisable FROM type "
            + "WHERE user_id = #{userId} AND (action_id = #{actionId} OR action_id IS NULL) AND t_disable = false "
            + "AND (archive IS NULL OR archive = false)")
    List<Type> findByActionIdOrNull(@Param("actionId") Integer actionId, @Param("userId") int userId);

    @Select("SELECT id, user_id AS userId, t_name AS tName, parent, t_disable AS disable, has_child AS hasChild, archive, "
            + "action_id AS actionId, analysis_disable AS analysisDisable FROM type "
            + "WHERE user_id = #{userId} AND parent = #{parent} AND t_disable = false "
            + "AND (archive IS NULL OR archive = false)")
    List<Type> findByParent(@Param("parent") int parent, @Param("userId") int userId);

    @Select("SELECT COUNT(1) FROM type WHERE user_id = #{userId} AND t_disable = false "
            + "AND (archive IS NULL OR archive = false)")
    int countActiveByUserId(@Param("userId") int userId);

    @Insert("INSERT INTO type (user_id, t_name, parent, t_disable, has_child, archive, action_id, analysis_disable) "
            + "VALUES (#{userId}, #{tName}, #{parent}, #{disable}, #{hasChild}, #{archive}, #{actionId}, #{analysisDisable})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Type type);

    @Update("UPDATE type SET t_name=#{tName}, parent=#{parent}, t_disable=#{disable}, "
            + "has_child=#{hasChild}, archive=#{archive}, action_id=#{actionId}, "
            + "analysis_disable=#{analysisDisable} WHERE id=#{id} AND user_id=#{userId}")
    void update(Type type);
}
