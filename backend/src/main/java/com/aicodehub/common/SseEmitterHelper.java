package com.aicodehub.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
public final class SseEmitterHelper {

    private SseEmitterHelper() {}

    public static SseEmitter createEmitter(long timeout) {
        SseEmitter emitter = new SseEmitter(timeout);
        emitter.onCompletion(() -> log.debug("SSE completed"));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    public static void send(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    public static void complete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }
}
