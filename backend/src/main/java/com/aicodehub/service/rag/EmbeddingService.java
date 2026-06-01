package com.aicodehub.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final String apiKey;
    private final String model;
    private final int dim;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public EmbeddingService(@Value("${embedding.api-key:}") String apiKey,
                            @Value("${embedding.model:text-embedding-v4}") String model,
                            @Value("${embedding.dim:2048}") int dim) {
        this.apiKey = apiKey;
        this.model = model;
        this.dim = dim;
    }

    /** Result of a batch embedding call */
    public record EmbeddingResult(float[][] vectors, int totalTokens) {}

    /**
     * Embed a single text, returns float array.
     */
    public float[] embed(String text) {
        EmbeddingResult batch = embedBatch(List.of(text));
        return (batch != null && batch.vectors().length > 0) ? batch.vectors()[0] : null;
    }

    /**
     * Batch embed multiple texts in a single API call.
     */
    public EmbeddingResult embedBatch(List<String> texts) {
        if (texts.isEmpty()) return new EmbeddingResult(new float[0][], 0);
        try {
            String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "input", Map.of("texts", texts),
                "parameters", Map.of("text_type", "document")
            ));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("Embedding API error: {}", resp.body());
                return null;
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode embeddings = root.path("output").path("embeddings");
            if (!embeddings.isArray()) return null;
            int tokens = root.path("usage").path("total_tokens").asInt();

            float[][] results = new float[embeddings.size()][];
            for (int j = 0; j < embeddings.size(); j++) {
                JsonNode vec = embeddings.get(j).path("embedding");
                float[] result = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) result[i] = vec.get(i).floatValue();
                results[j] = result;
            }
            return new EmbeddingResult(results, tokens);
        } catch (Exception e) {
            log.error("Batch embedding failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Serialize float array to byte array for DB storage (4 bytes per float).
     */
    public byte[] toBytes(float[] vec) {
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4);
        for (float v : vec) buf.putFloat(v);
        return buf.array();
    }

    /**
     * Deserialize byte array back to float array.
     */
    public float[] fromBytes(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] vec = new float[bytes.length / 4];
        for (int i = 0; i < vec.length; i++) vec[i] = buf.getFloat();
        return vec;
    }

    /**
     * Cosine similarity between two float vectors.
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10));
    }
}
