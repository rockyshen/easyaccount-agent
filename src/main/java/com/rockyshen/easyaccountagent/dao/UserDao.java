package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserDao {

    @Select("SELECT id, name, password FROM `user` WHERE name = #{name} AND password = #{password} LIMIT 1")
    User findByNameAndPassword(@Param("name") String name, @Param("password") int password);

    @Select("SELECT id, name, password FROM `user` WHERE id = #{id}")
    User findById(@Param("id") int id);
}
