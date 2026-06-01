package com.aicodehub.service.rag;

import com.aicodehub.entity.Document;
import com.aicodehub.mapper.DocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class DocumentProcessingConsumer {

    private final DocumentMapper documentMapper;
    private final VectorStoreService vectorStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private final io.minio.MinioClient minioClient;
    private volatile KafkaConsumer<String, String> consumer;
    private volatile boolean running = false;

    private static final int CHUNK_SIZE = 500;
    private static final String BROKER = System.getenv().getOrDefault("KAFKA_BROKER", "kafka:9092");

    public DocumentProcessingConsumer(DocumentMapper documentMapper, VectorStoreService vectorStore,
                                       io.minio.MinioClient minioClient) {
        this.documentMapper = documentMapper;
        this.vectorStore = vectorStore;
        this.minioClient = minioClient;
    }

    /** Start consumer loop, retries every 30s if Kafka unavailable */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 30_000)
    public void ensureConsumerRunning() {
        if (running) return;
        new Thread(() -> {
            try {
                var props = new Properties();
                props.put("bootstrap.servers", BROKER);
                props.put("group.id", "doc-processor");
                props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                props.put("auto.offset.reset", "earliest");
                props.put("enable.auto.commit", "true");

                consumer = new KafkaConsumer<>(props);
                consumer.subscribe(List.of("document-ready"));
                running = true;
                log.info("Kafka consumer started, subscribed to document-ready");

                while (running) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                        for (ConsumerRecord<String, String> r : records) {
                            try {
                                processMessage(r.value());
                            } catch (Exception e) {
                                log.error("Message processing failed: {}", e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Consumer poll error: {}", e.getMessage());
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    }
                }
            } catch (Exception e) {
                running = false;
                log.warn("Kafka consumer failed: {}", e.getMessage());
            }
        }, "kafka-consumer").start();
    }

    private void processMessage(String message) {
        try {
            var msg = mapper.readTree(message);
            long docId = msg.get("documentId").asLong();
            String objectName = msg.get("object").asText();
            processDocument(docId, objectName);
        } catch (Exception e) {
            log.error("Message parse failed: {}", e.getMessage(), e);
        }
    }

    /** Process document: download from MinIO → chunk → embed → ES index */
    public void processDocument(long docId, String objectName) {
        log.info("Processing document #{} from MinIO: {}", docId, objectName);

        Document doc = documentMapper.selectById(docId);
        if (doc == null) { log.error("Document #{} not found", docId); return; }

        String content = downloadFromMinio(objectName);
        if (content == null) { doc.setStatus("error"); documentMapper.updateById(doc); return; }

        var chunks = splitChunks(content, CHUNK_SIZE);
        if (!chunks.isEmpty()) {
            int tokens = vectorStore.bulkIndexChunks(docId, chunks, doc.getFilename(),
                doc.getUserId(), doc.getVisibility(), null, doc.getOrgTag());
            doc.setEmbeddingTokens(tokens);
            log.info("Document #{} processed: {} chunks, {} embedding tokens", docId, chunks.size(), tokens);
        }

        doc.setStatus("ready");
        documentMapper.updateById(doc);
    }

    private String downloadFromMinio(String objectName) {
        try {
            var resp = minioClient.getObject(io.minio.GetObjectArgs.builder()
                .bucket("documents").object(objectName).build());
            return new String(resp.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to download from MinIO {}: {}", objectName, e.getMessage());
            return null;
        }
    }

    private List<String> splitChunks(String text, int size) {
        var chunks = new ArrayList<String>();
        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(i + size, text.length());
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }
}
