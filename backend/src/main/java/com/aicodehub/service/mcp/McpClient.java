package com.aicodehub.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class McpClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private String serverName;
    private List<Map<String, Object>> tools = new ArrayList<>();

    /** Start MCP server via command (e.g., "npx -y @modelcontextprotocol/server-filesystem /workspace") */
    public synchronized boolean connect(String command) {
        try {
            String[] parts = command.split("\\s+");
            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.redirectErrorStream(false);  // Keep stdout for JSON-RPC, stderr for logging
            process = pb.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Initialize
            String initReq = rpcRequest("initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "ai-codehub", "version", "1.0.0")
            ));
            JsonNode initResp = sendRpc(initReq);
            if (initResp == null) {
                log.warn("MCP initialize failed (no response), cmd: {}", command);
                disconnect();
                return false;
            }
            serverName = initResp.path("result").path("serverInfo").path("name").asText("mcp-server");

            // List tools
            String listReq = rpcRequest("tools/list", Map.of());
            JsonNode listResp = sendRpc(listReq);
            if (listResp == null) return false;
            JsonNode toolsNode = listResp.path("result").path("tools");
            tools.clear();
            if (toolsNode.isArray()) {
                for (JsonNode t : toolsNode) {
                    Map<String, Object> tool = new LinkedHashMap<>();
                    tool.put("name", t.get("name").asText());
                    tool.put("description", t.has("description") ? t.get("description").asText() : "");
                    tool.put("inputSchema", mapper.convertValue(t.get("inputSchema"), Map.class));
                    tools.add(tool);
                }
            }

            log.info("MCP connected: {} ({} tools)", serverName, tools.size());
            return true;
        } catch (Exception e) {
            log.error("MCP connect failed: {} (cmd: {})", e.getMessage(), command);
            disconnect();
            return false;
        }
    }

    /** Call a tool by name with arguments */
    public String callTool(String toolName, Map<String, Object> args) {
        try {
            String req = rpcRequest("tools/call", Map.of("name", toolName, "arguments", args));
            JsonNode resp = sendRpc(req);
            if (resp == null) return "MCP call failed";
            JsonNode content = resp.path("result").path("content");
            if (content.isArray() && !content.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode c : content) {
                    if (c.has("text")) sb.append(c.get("text").asText());
                }
                return sb.toString();
            }
            return resp.path("result").toString();
        } catch (Exception e) {
            return "MCP call error: " + e.getMessage();
        }
    }

    public void disconnect() {
        tools.clear();
        if (process != null) { process.destroy(); process = null; }
        if (writer != null) { try { writer.close(); } catch (Exception ignored) {} writer = null; }
        if (reader != null) { try { reader.close(); } catch (Exception ignored) {} reader = null; }
    }

    public boolean isConnected() { return process != null && process.isAlive(); }
    public String getServerName() { return serverName; }
    public List<Map<String, Object>> getTools() { return tools; }

    private JsonNode sendRpc(String json) {
        try {
            writer.write(json + "\n");
            writer.flush();
            // Wait up to 60s for npx first install
            long deadline = System.currentTimeMillis() + 60_000;
            String line = null;
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) { line = reader.readLine(); break; }
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
            if (line == null) line = reader.readLine();
            if (line == null) return null;
            return mapper.readTree(line);
        } catch (Exception e) {
            return null;
        }
    }

    private String rpcRequest(String method, Map<String, Object> params) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("jsonrpc", "2.0");
            root.put("id", System.currentTimeMillis());
            root.put("method", method);
            root.set("params", mapper.valueToTree(params));
            return mapper.writeValueAsString(root);
        } catch (Exception e) { return "{}"; }
    }
}
