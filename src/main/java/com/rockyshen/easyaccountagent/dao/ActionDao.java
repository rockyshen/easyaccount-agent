package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Action;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ActionDao {

    @Select("SELECT id, h_name AS hName, exempt, handle FROM action")
    List<Action> findAll();

    @Select("SELECT id, h_name AS hName, exempt, handle FROM action WHERE id = #{id}")
    Action findById(@Param("id") int id);
}
