package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Action;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ActionDao {

    @Select("SELECT id, h_name AS hName, exempt, handle FROM action")
    List<Action> findAll();

    @Select("SELECT id, h_name AS hName, exempt, handle FROM action WHERE id = #{id}")
    Action findById(@Param("id") int id);

    @Select("SELECT id, h_name AS hName, exempt, handle FROM action WHERE handle = #{handle} LIMIT 1")
    Action findByHandle(@Param("handle") int handle);

    @Insert("INSERT INTO action (h_name, exempt, handle) VALUES (#{hName}, #{exempt}, #{handle})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Action action);
}
