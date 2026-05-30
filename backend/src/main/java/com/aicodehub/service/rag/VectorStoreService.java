package com.aicodehub.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class VectorStoreService {

    private final EmbeddingService embeddingService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final String esUrl;

    private static final String INDEX = "doc_chunks";
    private static final int DIM = 2048;

    public VectorStoreService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.esUrl = System.getenv().getOrDefault("ES_URL", "http://elasticsearch:9200");
    }

    /** Initialize index if not exists */
    public void ensureIndex() {
        try {
            var head = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + INDEX)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var resp = client.send(head, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() == 200) return;
        } catch (Exception ignored) {}

        try {
            ObjectNode settings = mapper.createObjectNode();
            ObjectNode index = mapper.createObjectNode();
            index.put("number_of_shards", 1);
            index.put("number_of_replicas", 0);
            settings.set("index", index);

            ObjectNode mapping = mapper.createObjectNode();
            ObjectNode props = mapper.createObjectNode();
            ObjectNode docId = mapper.createObjectNode();
            docId.put("type", "long");
            props.set("doc_id", docId);
            ObjectNode chunkIdx = mapper.createObjectNode();
            chunkIdx.put("type", "integer");
            props.set("chunk_index", chunkIdx);
            ObjectNode text = mapper.createObjectNode();
            text.put("type", "text");
            props.set("content", text);
            ObjectNode filename = mapper.createObjectNode();
            filename.put("type", "keyword");
            props.set("filename", filename);
            ObjectNode userId = mapper.createObjectNode();
            userId.put("type", "long");
            props.set("user_id", userId);

            ObjectNode vec = mapper.createObjectNode();
            vec.put("type", "dense_vector");
            vec.put("dims", DIM);
            vec.put("index", true);
            vec.put("similarity", "cosine");
            props.set("embedding", vec);
            mapping.set("properties", props);

            ObjectNode body = mapper.createObjectNode();
            body.set("settings", settings);
            body.set("mappings", mapping);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + INDEX))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
            log.info("ES index {} created", INDEX);
        } catch (Exception e) {
            log.error("Failed to create ES index: {}", e.getMessage());
        }
    }

    /** Index a chunk with its embedding */
    public void indexChunk(Long docId, int chunkIndex, String content, String filename, Long userId) {
        float[] vec = embeddingService.embed(content);
        if (vec == null) return;

        try {
            ObjectNode doc = mapper.createObjectNode();
            doc.put("doc_id", docId);
            doc.put("chunk_index", chunkIndex);
            doc.put("content", content);
            doc.put("filename", filename);
            doc.put("user_id", userId);
            ArrayNode arr = mapper.createArrayNode();
            for (float v : vec) arr.add(v);
            doc.set("embedding", arr);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + INDEX + "/_doc/" + docId + "_" + chunkIndex))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(doc)))
                .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.error("ES index failed: {}", e.getMessage());
        }
    }

    /** Delete all chunks for a document */
    public void deleteDoc(Long docId) {
        try {
            ObjectNode query = mapper.createObjectNode();
            ObjectNode term = mapper.createObjectNode();
            term.put("doc_id", docId);
            query.set("query", mapper.createObjectNode().set("term", term));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + INDEX + "/_delete_by_query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(query)))
                .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.error("ES delete failed: {}", e.getMessage());
        }
    }

    /** Get all chunks for a document */
    public List<Map<String, Object>> getDocContent(Long docId) {
        try {
            ObjectNode query = mapper.createObjectNode();
            ObjectNode term = mapper.createObjectNode();
            term.put("doc_id", docId);
            query.set("query", mapper.createObjectNode().set("term", term));
            ObjectNode sort = mapper.createObjectNode();
            sort.put("chunk_index", mapper.createObjectNode().put("order", "asc"));
            query.set("sort", mapper.createArrayNode().add(sort));
            query.put("size", 100);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + INDEX + "/_search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(query)))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            List<Map<String, Object>> results = new ArrayList<>();
            JsonNode root = mapper.readTree(resp.body());
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode src = hit.get("_source");
                results.add(Map.of("index", src.get("chunk_index").asInt(), "content", src.get("content").asText()));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** kNN search for relevant chunks */
    public List<Map<String, Object>> search(Long userId, String query, int topK) {
        float[] qVec = embeddingService.embed(query);
        if (qVec == null) return List.of();

        try {
            ArrayNode qArr = mapper.createArrayNode();
            for (float v : qVec) qArr.add(v);

            ObjectNode knn = mapper.createObjectNode();
            knn.put("field", "embedding");
            knn.set("query_vector", qArr);
            knn.put("k", topK);
            knn.put("num_candidates", Math.min(topK * 2, 50));

            ObjectNode filter = mapper.createObjectNode();
            ObjectNode term = mapper.createObjectNode();
            term.put("user_id", userId);
            filter.set("term", term);
            knn.set("filter", filter);

            ObjectNode body = mapper.createObjectNode();
            body.set("knn", knn);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + INDEX + "/_search"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            List<Map<String, Object>> results = new ArrayList<>();
            JsonNode root = mapper.readTree(resp.body());
            JsonNode hits = root.path("hits").path("hits");
            for (JsonNode hit : hits) {
                JsonNode src = hit.get("_source");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", src.get("content").asText());
                item.put("filename", src.get("filename").asText());
                item.put("score", hit.get("_score").asDouble());
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
