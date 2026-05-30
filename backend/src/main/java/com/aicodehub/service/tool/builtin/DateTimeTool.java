package com.aicodehub.service.tool.builtin;

import com.aicodehub.service.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DateTimeTool implements ToolDefinition {

    @Override
    public String name() { return "datetime"; }

    @Override
    public String description() { return "获取当前日期时间或指定时区的时间。"; }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> tz = new LinkedHashMap<>();
        tz.put("type", "string");
        tz.put("description", "时区，如 Asia/Shanghai、America/New_York，默认 Asia/Shanghai");
        props.put("timezone", tz);
        params.put("properties", props);
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String tz = (String) args.getOrDefault("timezone", "Asia/Shanghai");
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        } catch (Exception e) {
            return "时区错误: " + e.getMessage();
        }
    }
}
