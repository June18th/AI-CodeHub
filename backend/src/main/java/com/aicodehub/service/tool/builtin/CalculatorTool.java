package com.aicodehub.service.tool.builtin;

import com.aicodehub.service.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngineManager;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CalculatorTool implements ToolDefinition {

    @Override
    public String name() { return "calculator"; }

    @Override
    public String description() {
        return "执行数学表达式计算，支持加减乘除、三角函数等。输入一个数学表达式字符串。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("type", "string");
        expr.put("description", "数学表达式，例如 2+3*4 或 Math.sqrt(16)");
        props.put("expression", expr);
        params.put("properties", props);
        params.put("required", java.util.List.of("expression"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        try {
            String expr = (String) args.get("expression");
            Object result = new ScriptEngineManager()
                .getEngineByName("JavaScript")
                .eval(expr);
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }
}
