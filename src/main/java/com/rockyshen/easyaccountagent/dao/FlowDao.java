package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Flow;
import com.rockyshen.easyaccountagent.entity.FlowYear;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface FlowDao {

    @Insert("insert into flow (user_id,f_date,money,type_id,action_id,exempt,note,f_create_date,account_id,account_to_id,collect) "
            + "values (#{flow.userId},#{flow.fDate},#{flow.money},#{flow.typeId},#{flow.actionId},#{flow.exempt},#{flow.note},"
            + "#{flow.fCreateDate},#{flow.accountId},#{flow.accountToId},#{flow.collect})")
    void addFlow(@Param("flow") Flow flow);

    @Select("select id, user_id AS userId, f_date AS fDate, money, type_id AS typeId, action_id AS actionId, "
            + "exempt, account_id AS accountId, account_to_id AS accountToId, note, collect, "
            + "f_create_date AS fCreateDate from flow where id = #{id} and user_id = #{userId}")
    List<Flow> queryFlowById(@Param("id") int id, @Param("userId") int userId);

    @Update("update flow set f_date=#{flow.fDate}, money=#{flow.money}, type_id=#{flow.typeId}, "
            + "action_id=#{flow.actionId}, exempt=#{flow.exempt}, note=#{flow.note}, "
            + "f_create_date=#{flow.fCreateDate}, account_id=#{flow.accountId}, "
            + "account_to_id=#{flow.accountToId}, collect=#{flow.collect} "
            + "where id=#{flow.id} and user_id=#{flow.userId}")
    void updateFlow(@Param("flow") Flow flow);

    @Update("update flow set collect=#{collect} where id=#{id} and user_id=#{userId}")
    void collectFlowById(@Param("id") int id, @Param("userId") int userId, @Param("collect") int collect);

    @SelectProvider(type = FlowSelectProvider.class, method = "getFlowByMain")
    List<Map<String, Object>> getFlowByMain(@Param("handle") int handle, @Param("order") int order,
                                            @Param("date") String date, @Param("userId") int userId);

    @SelectProvider(type = FlowSelectProvider.class, method = "getFlowByScreen")
    List<Map<String, Object>> getFlowByScreen(@Param("handle") int handle, @Param("account") int account,
                                              @Param("startDate") String startDate, @Param("endDate") String endDate,
                                              @Param("isSingleMonth") boolean isSingleMonth,
                                              @Param("isCollect") boolean isCollect,
                                              @Param("note") String note,
                                              @Param("userId") int userId);

    @Delete("delete from flow where id = #{id} and user_id = #{userId}")
    void deleteFlowById(@Param("id") int id, @Param("userId") int userId);

    @SelectProvider(type = FlowSelectProvider.class, method = "getYearlySummary")
    FlowYear getYearlySummary(@Param("year") int year, @Param("userId") int userId);
}
