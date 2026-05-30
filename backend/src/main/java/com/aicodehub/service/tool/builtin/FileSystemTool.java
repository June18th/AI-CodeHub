package com.aicodehub.service.tool.builtin;

import com.aicodehub.service.tool.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FileSystemTool implements ToolDefinition {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String name() { return "filesystem"; }

    @Override
    public String description() {
        return "查看项目工作目录 /workspace 中的文件和文件夹。当用户询问项目结构、有哪些文件/文件夹、项目目录内容时，必须调用此工具。支持两个操作: list_dir(列出目录下所有文件和文件夹)和 read_file(读取文件完整内容)。默认路径为 /workspace。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("description", "操作类型: list_dir 或 read_file");
        action.put("enum", List.of("list_dir", "read_file"));
        props.put("action", action);

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("type", "string");
        path.put("description", "文件或目录的绝对路径，如 /home/user/project");
        props.put("path", path);

        params.put("properties", props);
        params.put("required", List.of("action", "path"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        try {
            String action = (String) args.get("action");
            String path = (String) args.get("path");
            String root = System.getenv().getOrDefault("FILESYSTEM_ROOT", "/workspace");
            Path rootPath = Path.of(root).normalize();

            // Resolve path: handle empty, ".", "/", or relative paths
            Path p;
            if (path == null || path.isBlank() || ".".equals(path) || "/".equals(path)) {
                p = rootPath;
            } else if (path.startsWith("/")) {
                // Absolute path: check if under root, if not, resolve relative to root
                Path absPath = Path.of(path).normalize();
                p = absPath.startsWith(rootPath) ? absPath : rootPath.resolve(path.replaceAll("^/", ""));
            } else {
                p = rootPath.resolve(path).normalize();
            }

            // Security: ensure we stay within root
            if (!p.startsWith(rootPath)) {
                p = rootPath;
            }

            if ("list_dir".equals(action)) {
                File dir = p.toFile();
                if (!dir.exists() || !dir.isDirectory()) return "目录不存在: " + p;
                File[] files = dir.listFiles();
                if (files == null) return "无法读取目录: " + p;
                List<Map<String, Object>> items = new ArrayList<>();
                for (File f : files) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", f.getName());
                    item.put("type", f.isDirectory() ? "dir" : "file");
                    item.put("size", f.length());
                    items.add(item);
                }
                return mapper.writeValueAsString(Map.of("path", p.toString(), "items", items));
            }

            if ("read_file".equals(action)) {
                File f = p.toFile();
                if (!f.exists()) return "文件不存在: " + p;
                if (f.isDirectory()) return "这是一个目录，请使用 list_dir 操作";
                if (f.length() > 100_000) {
                    // For large files, read first 100KB
                    byte[] bytes = Files.readAllBytes(p);
                    return new String(bytes, 0, 100_000) + "\n...(文件过大，仅显示前100KB)";
                }
                return Files.readString(p);
            }

            return "未知操作: " + action;
        } catch (Exception e) {
            return "文件操作失败: " + e.getMessage();
        }
    }
}
