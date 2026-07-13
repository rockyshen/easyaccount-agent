package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.Flow;
import com.rockyshen.easyaccountagent.entity.FlowYear;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface FlowDao {

    @Insert("insert into flow (f_date,money,type_id,action_id,exempt,note,f_create_date,account_id,account_to_id,collect) "
            + "values (#{flow.fDate},#{flow.money},#{flow.typeId},#{flow.actionId},#{flow.exempt},#{flow.note},"
            + "#{flow.fCreateDate},#{flow.accountId},#{flow.accountToId},#{flow.collect})")
    void addFlow(@Param("flow") Flow flow);

    @Select("select * from flow where id = #{id}")
    List<Flow> queryFlowById(@Param("id") int id);

    @Update("update flow set f_date=#{flow.fDate}, money=#{flow.money}, type_id=#{flow.typeId}, "
            + "action_id=#{flow.actionId}, exempt=#{flow.exempt}, note=#{flow.note}, "
            + "f_create_date=#{flow.fCreateDate}, account_id=#{flow.accountId}, "
            + "account_to_id=#{flow.accountToId}, collect=#{flow.collect} where id=#{flow.id}")
    void updateFlow(@Param("flow") Flow flow);

    @Update("update flow set collect=#{collect} where id=#{id}")
    void collectFlowById(@Param("id") int id, @Param("collect") int collect);

    @SelectProvider(type = FlowSelectProvider.class, method = "getFlowByMain")
    List<Map<String, Object>> getFlowByMain(@Param("handle") int handle, int order, @Param("date") String date);

    @SelectProvider(type = FlowSelectProvider.class, method = "getFlowByScreen")
    List<Map<String, Object>> getFlowByScreen(int handle, int account, String startDate, String endDate,
                                              boolean isSingleMonth, boolean isCollect, String note);

    @Delete("delete from flow where id = #{id}")
    void deleteFlowById(@Param("id") int id);

    @SelectProvider(type = FlowSelectProvider.class, method = "getYearlySummary")
    FlowYear getYearlySummary(@Param("year") int year);
}
