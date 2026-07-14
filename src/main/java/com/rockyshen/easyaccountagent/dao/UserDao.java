package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserDao {

    @Select("SELECT id, name, password FROM `user` WHERE name = #{name} AND password = #{password} LIMIT 1")
    User findByNameAndPassword(@Param("name") String name, @Param("password") String password);

    @Select("SELECT id, name, password FROM `user` WHERE name = #{name} LIMIT 1")
    User findByName(@Param("name") String name);

    @Select("SELECT id, name, password FROM `user` WHERE id = #{id}")
    User findById(@Param("id") int id);

    @Insert("INSERT INTO `user` (name, password) VALUES (#{name}, #{password})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
}
