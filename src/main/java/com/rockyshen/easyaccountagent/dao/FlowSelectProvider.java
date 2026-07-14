package com.rockyshen.easyaccountagent.dao;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;

@Slf4j
public class FlowSelectProvider {

    public String getFlowByMain(@Param("handle") int handle, @Param("order") int order,
                                @Param("date") String date, @Param("userId") int userId) {
        String sqlStr = new SQL() {
            {
                SELECT("flow.id,flow.f_date,flow.money,flow.collect,flow.exempt,flow.note,a.handle,a.h_name,ac.a_name,acc.a_name t_a_name,t.t_name,t2.t_name p_t_name");
                FROM("flow");
                LEFT_OUTER_JOIN("action a on flow.action_id = a.id");
                LEFT_OUTER_JOIN("type t on flow.type_id = t.id");
                LEFT_OUTER_JOIN("type t2 on t.parent = t2.id");
                LEFT_OUTER_JOIN("account ac on flow.account_id = ac.id");
                LEFT_OUTER_JOIN("account acc on flow.account_to_id = acc.id");
                WHERE("flow.user_id = #{userId}");
                if (handle >= 3) {
                    WHERE("a.handle < #{handle}", "flow.f_date like #{date}");
                } else {
                    WHERE("a.handle = #{handle}", "flow.f_date  like #{date}");
                }
                if (order == 0) {
                    ORDER_BY("flow.f_date desc ");
                } else {
                    ORDER_BY("flow.money+0 desc ");
                }
            }
        }.toString();
        log.info("method: getFlowByMain\n" + sqlStr);
        return sqlStr;
    }

    public String getFlowByScreen(@Param("handle") int handle, @Param("account") int account,
                                  @Param("startDate") String startDate, @Param("endDate") String endDate,
                                  @Param("isSingleMonth") boolean isSingleMonth,
                                  @Param("isCollect") boolean isCollect,
                                  @Param("note") String note,
                                  @Param("userId") int userId) {
        StringBuilder sql = new StringBuilder("SELECT " +
                "flow.id, flow.f_date AS flowDate, flow.money, flow.collect, flow.exempt, flow.note," +
                "a.handle, a.h_name AS handleName, a.id AS actionId," +
                "ac.a_name AS accountName, ac.id AS accountId, acc.a_name AS toAccountName," +
                "t.t_name AS typeName, t.id AS typeId, t2.t_name AS parentTypeName, t2.id AS parentTypeId\n");
        sql.append("FROM flow\n");
        sql.append("LEFT OUTER JOIN action a ON flow.action_id = a.id\n");
        sql.append("LEFT OUTER JOIN type t ON flow.type_id = t.id\n");
        sql.append("LEFT OUTER JOIN type t2 ON t.parent = t2.id\n");
        sql.append("LEFT OUTER JOIN account ac ON flow.account_id = ac.id\n");
        sql.append("LEFT OUTER JOIN account acc ON flow.account_to_id = acc.id\n");
        sql.append("WHERE flow.user_id = #{userId}\n");
        sql.append("AND a.handle ").append(handle == 3 ? "<" : "=").append("#{handle}").append("\n");

        if (account > 0) {
            sql.append("AND (flow.account_id = #{account} OR flow.account_to_id = #{account})\n");
        }

        if (isSingleMonth) {
            sql.append("AND flow.f_date LIKE CONCAT(LEFT(#{startDate}, 7), '%')\n");
        } else {
            if (startDate != null && !"null".equals(startDate) && !startDate.isEmpty()) {
                sql.append("AND flow.f_date >= #{startDate}\n");
            }
            if (endDate != null && !"null".equals(endDate) && !endDate.isEmpty()) {
                sql.append("AND flow.f_date <= #{endDate}\n");
            }
        }

        if (isCollect) {
            sql.append("AND flow.collect = 1\n");
        }

        if (note != null && !note.trim().isEmpty()) {
            sql.append("AND flow.note LIKE CONCAT('%', #{note}, '%')\n");
        }

        sql.append("ORDER BY flow.f_date DESC");
        log.info("method: getFlowByScreen\n" + sql);
        return sql.toString();
    }

    public String getYearlySummary(@Param("year") int year, @Param("userId") int userId) {
        String sql = new SQL() {{
            SELECT("SUM(CASE WHEN a.handle = 0 THEN f.money ELSE 0 END) AS totalEarns",
                    "SUM(CASE WHEN a.handle = 1 THEN f.money ELSE 0 END) AS totalCosts",
                    "SUM(CASE WHEN a.handle = 0 THEN f.money ELSE 0 END) - SUM(CASE WHEN a.handle = 1 THEN f.money ELSE 0 END) AS totalBalance");
            FROM("flow f");
            JOIN("action a ON f.action_id = a.id");
            WHERE("YEAR(f.f_date) = #{year}");
            WHERE("f.user_id = #{userId}");
        }}.toString();
        log.info("method: getYearlySummary\n" + sql);
        return sql;
    }
}
