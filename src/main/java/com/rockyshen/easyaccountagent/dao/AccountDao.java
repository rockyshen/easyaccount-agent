package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Account;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AccountDao {

    @Select("SELECT id, money, exempt_money AS exemptMoney, a_name AS aName, card, disable, "
            + "create_time AS createTime, note FROM account WHERE id = #{id}")
    Account findById(@Param("id") int id);

    @Select("SELECT id, money, exempt_money AS exemptMoney, a_name AS aName, card, disable, "
            + "create_time AS createTime, note FROM account WHERE disable = false OR disable IS NULL")
    List<Account> findByDisableFalse();

    @Update("UPDATE account SET money=#{money}, exempt_money=#{exemptMoney}, a_name=#{aName}, "
            + "card=#{card}, disable=#{disable}, note=#{note} WHERE id=#{id}")
    void update(Account account);

    default void save(Account account) {
        update(account);
    }
}
