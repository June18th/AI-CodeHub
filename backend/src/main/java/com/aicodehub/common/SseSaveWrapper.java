package com.aicodehub.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public class SseSaveWrapper {

    private final SseEmitter delegate;
    private final StringBuilder buffer = new StringBuilder();

    public SseSaveWrapper(SseEmitter delegate) {
        this.delegate = delegate;
    }

    public void send(Object data) {
        if (data instanceof String s && !"[DONE]".equals(s)) {
            buffer.append(s);
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
}
