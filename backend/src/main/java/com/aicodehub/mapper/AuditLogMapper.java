package com.aicodehub.mapper;

import com.aicodehub.entity.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("SELECT model, COUNT(*) as cnt FROM audit_log WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) GROUP BY model ORDER BY cnt DESC")
    List<Map<String, Object>> modelUsage24h();

    @Select("SELECT HOUR(created_at) as hour, COUNT(*) as cnt FROM audit_log WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) GROUP BY HOUR(created_at) ORDER BY hour")
    List<Map<String, Object>> hourlyTrend();

    @Select("SELECT COUNT(DISTINCT user_id) FROM audit_log WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)")
    Integer activeUsers24h();

    @Select("SELECT COUNT(*) FROM audit_log WHERE created_at >= CURDATE()")
    Integer todayCalls();

    @Select("SELECT COALESCE(SUM(input_tokens+output_tokens),0) FROM audit_log WHERE created_at >= CURDATE()")
    Integer todayTokens();

    @Select("SELECT COUNT(*) FROM audit_log WHERE status = 'error' AND created_at >= CURDATE()")
    Integer todayErrors();

    @Select("SELECT COALESCE(AVG(latency_ms),0) FROM audit_log WHERE created_at >= CURDATE()")
    Integer avgLatency();

    @Select("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT #{limit}")
    List<AuditLog> recentLogs(int limit);
}
