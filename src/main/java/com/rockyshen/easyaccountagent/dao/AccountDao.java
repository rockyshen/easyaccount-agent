package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Account;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AccountDao {

    @Select("SELECT id, user_id AS userId, money, exempt_money AS exemptMoney, a_name AS aName, card, disable, "
            + "create_time AS createTime, note, "
            + "IFNULL(account_type, 0) AS accountType FROM account WHERE id = #{id} AND user_id = #{userId}")
    Account findById(@Param("id") int id, @Param("userId") int userId);

    @Select("SELECT id, user_id AS userId, money, exempt_money AS exemptMoney, a_name AS aName, card, disable, "
            + "create_time AS createTime, note, "
            + "IFNULL(account_type, 0) AS accountType FROM account "
            + "WHERE user_id = #{userId} AND (disable = false OR disable IS NULL)")
    List<Account> findByDisableFalse(@Param("userId") int userId);

    @Insert("INSERT INTO account (user_id, money, exempt_money, a_name, card, disable, create_time, note, account_type) "
            + "VALUES (#{userId}, #{money}, #{exemptMoney}, #{aName}, #{card}, #{disable}, #{createTime}, #{note}, #{accountType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Account account);

    @Update("UPDATE account SET money=#{money}, exempt_money=#{exemptMoney}, a_name=#{aName}, "
            + "card=#{card}, disable=#{disable}, note=#{note}, account_type=#{accountType} "
            + "WHERE id=#{id} AND user_id=#{userId}")
    void update(Account account);

    default void save(Account account) {
        update(account);
    }
}
