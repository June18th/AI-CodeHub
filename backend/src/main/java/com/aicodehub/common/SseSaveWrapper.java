package com.aicodehub.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public class SseSaveWrapper {

    private final SseEmitter delegate;
    private final StringBuilder buffer = new StringBuilder();
    private int inputTokens = 0;
    private int outputTokens = 0;

    public SseSaveWrapper(SseEmitter delegate) {
        this.delegate = delegate;
    }

    public void send(Object data) {
        if (data instanceof String s && !"[DONE]".equals(s) && !s.startsWith("🔧")) {
            if (s.startsWith("[TOKEN]")) {
                var m = java.util.regex.Pattern.compile("input=(\\d+),output=(\\d+)").matcher(s);
                if (m.find()) {
                    inputTokens = Integer.parseInt(m.group(1));
                    outputTokens = Integer.parseInt(m.group(2));
                }
            } else {
                buffer.append(s);
            }
        }
        try {
            delegate.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            delegate.completeWithError(e);
        }
    }

    public void complete(Runnable onSave) {
        try {
            delegate.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException ignored) {}
        try {
            onSave.run();
        } catch (Exception e) {
            log.error("Save failed", e);
        }
        delegate.complete();
    }

    public String getResponse() { return buffer.toString(); }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
}
