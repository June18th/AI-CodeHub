package com.aicodehub.service.tool.builtin;

import com.aicodehub.service.mcp.McpClient;
import com.aicodehub.service.tool.ToolDefinition;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class McpTool implements ToolDefinition {

    private final McpClient mcpClient;
    private final String mcpCommand;

    public McpTool(McpClient mcpClient, @Value("${mcp.command:}") String mcpCommand) {
        this.mcpClient = mcpClient;
        this.mcpCommand = mcpCommand;
    }

    @PostConstruct
    public void init() {
        if (mcpCommand != null && !mcpCommand.isBlank()) {
            new Thread(() -> {
                try {
                    if (mcpClient.connect(mcpCommand)) {
                        log.info("MCP server started: {} with {} tools", mcpClient.getServerName(), mcpClient.getTools().size());
                    } else {
                        log.warn("MCP connect failed, will retry on first tool call");
                    }
                } catch (Exception e) {
                    log.warn("MCP init error: {}", e.getMessage());
                }
            }, "mcp-init").start();
        }
    }

    @PreDestroy
    public void destroy() { mcpClient.disconnect(); }

    @Override
    public String name() { return "mcp"; }

    @Override
    public String description() {
        if (!mcpClient.isConnected()) return "MCP 本地工具（未连接，请在 .env 中配置 MCP_COMMAND）";
        return "MCP 本地工具集: " + mcpClient.getServerName() + "。可用子工具: " + mcpClient.getTools().stream().map(t -> (String)t.get("name")).reduce((a,b) -> a + ", " + b).orElse("");
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> toolProp = new LinkedHashMap<>();
        toolProp.put("type", "string");
        toolProp.put("description", "要调用的子工具名称");
        props.put("tool", toolProp);
        Map<String, Object> argsProp = new LinkedHashMap<>();
        argsProp.put("type", "object");
        argsProp.put("description", "子工具参数（JSON 对象）");
        props.put("arguments", argsProp);
        params.put("properties", props);
        params.put("required", List.of("tool"));
        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> args) {
        if (!mcpClient.isConnected()) return "MCP 未连接，请在 .env 中设置 MCP_COMMAND";
        String tool = (String) args.get("tool");
        Object rawArgs = args.get("arguments");
        Map<String, Object> toolArgs;
        if (rawArgs instanceof String s) {
            try { toolArgs = new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Map.class); }
            catch (Exception e) { toolArgs = Map.of(); }
        } else {
            toolArgs = (Map<String, Object>) (rawArgs != null ? rawArgs : Map.of());
        }
        return mcpClient.callTool(tool, toolArgs);
    }
}
