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

    /** Index a single chunk (fallback for small docs) */
    public void indexChunk(Long docId, int chunkIndex, String content, String filename, Long userId) {
        float[] vec = embeddingService.embed(content);
        if (vec == null) return;
        indexChunkWithVec(docId, chunkIndex, content, filename, userId, vec);
    }

    private void indexChunkWithVec(Long docId, int chunkIndex, String content, String filename, Long userId, float[] vec) {
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

    /** Bulk index chunks with pre-computed embeddings */
    public int bulkIndexChunks(Long docId, List<String> chunks, String filename, Long userId) {
        return bulkIndexChunks(docId, chunks, filename, userId, "PRIVATE", null, null);
    }

    public int bulkIndexChunks(Long docId, List<String> chunks, String filename, Long userId,
                                 String visibility, Long departmentId) {
        return bulkIndexChunks(docId, chunks, filename, userId, visibility, departmentId, null);
    }

    /** Bulk index chunks, returns embedding token count */
    public int bulkIndexChunks(Long docId, List<String> chunks, String filename, Long userId,
                                 String visibility, Long departmentId, String orgTag) {
        if (chunks.isEmpty()) return 0;
        var embedResult = embeddingService.embedBatch(chunks);
        if (embedResult == null) return 0;
        float[][] vecs = embedResult.vectors();

        StringBuilder bulk = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            try {
                ObjectNode doc = mapper.createObjectNode();
                doc.put("doc_id", docId);
                doc.put("chunk_index", i);
                doc.put("content", chunks.get(i));
                doc.put("filename", filename);
                doc.put("user_id", userId);
                doc.put("visibility", visibility != null ? visibility : "PRIVATE");
                if (departmentId != null) doc.put("department_id", departmentId);
                if (orgTag != null) doc.put("org_tag", orgTag);
                ArrayNode arr = mapper.createArrayNode();
                for (float v : vecs[i]) arr.add(v);
                doc.set("embedding", arr);

                bulk.append("{\"index\":{\"_index\":\"").append(INDEX)
                    .append("\",\"_id\":\"").append(docId).append("_").append(i).append("\"}}\n");
                bulk.append(mapper.writeValueAsString(doc)).append("\n");
            } catch (Exception e) {
                log.error("Bulk index build failed for chunk {}: {}", i, e.getMessage());
            }
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/_bulk"))
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(bulk.toString()))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) log.error("ES bulk index error: {}", resp.body());
        } catch (Exception e) {
            log.error("ES bulk index failed: {}", e.getMessage());
        }
        return embedResult.totalTokens();
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

    /** Hybrid search: kNN vector + BM25 keyword with RRF fusion */
    /** Search with orgTag-based access control */
    public List<Map<String, Object>> search(Long userId, Set<String> effectiveOrgTags,
                                             String query, int topK, boolean skipFilter) {
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
            if (!skipFilter) {
                ObjectNode filter = mapper.createObjectNode();
                ObjectNode should = mapper.createObjectNode();
                ArrayNode clauses = mapper.createArrayNode();
                // PUBLIC: visible to all
                clauses.add(mapper.createObjectNode().set("term",
                    mapper.createObjectNode().put("visibility", "PUBLIC")));
                // PRIVATE: only owner
                ObjectNode privateClause = mapper.createObjectNode();
                ObjectNode boolPrivate = mapper.createObjectNode();
                ArrayNode privateMust = mapper.createArrayNode();
                privateMust.add(mapper.createObjectNode().set("term",
                    mapper.createObjectNode().put("visibility", "PRIVATE")));
                privateMust.add(mapper.createObjectNode().set("term",
                    mapper.createObjectNode().put("user_id", userId)));
                boolPrivate.set("must", privateMust);
                privateClause.set("bool", boolPrivate);
                clauses.add(privateClause);
                // DEPARTMENT: doc's org_tag must be in user's effective org tags
                if (effectiveOrgTags != null && !effectiveOrgTags.isEmpty()) {
                    ObjectNode deptClause = mapper.createObjectNode();
                    ObjectNode boolDept = mapper.createObjectNode();
                    ArrayNode deptMust = mapper.createArrayNode();
                    deptMust.add(mapper.createObjectNode().set("term",
                        mapper.createObjectNode().put("visibility", "DEPARTMENT")));
                    ArrayNode termsArr = mapper.createArrayNode();
                    for (String t : effectiveOrgTags) termsArr.add(t);
                    deptMust.add(mapper.createObjectNode().set("terms",
                        mapper.createObjectNode().set("org_tag", termsArr)));
                    boolDept.set("must", deptMust);
                    deptClause.set("bool", boolDept);
                    clauses.add(deptClause);
                }
                should.set("should", clauses);
                should.put("minimum_should_match", 1);
                filter.set("bool", should);
                knn.set("filter", filter);
            }

            ObjectNode bm25 = mapper.createObjectNode();
            ObjectNode match = mapper.createObjectNode();
            match.set("content", mapper.createObjectNode().put("query", query));
            bm25.set("match", match);

            ObjectNode body = mapper.createObjectNode();
            body.set("knn", knn);
            body.set("query", bm25);

            ObjectNode rrf = mapper.createObjectNode();
            rrf.put("rank_constant", 60);
            rrf.put("window_size", Math.min(topK * 4, 200));
            body.set("rank", rrf);
            body.put("size", topK);

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

    // ── Long-term memory summaries ──

    private static final String SUMMARY_INDEX = "memory_summaries";

    public void ensureSummaryIndex() {
        try {
            var head = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + SUMMARY_INDEX)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var resp = client.send(head, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() == 200) return;
        } catch (Exception ignored) {}

        try {
            ObjectNode settings = mapper.createObjectNode();
            settings.set("index", mapper.createObjectNode().put("number_of_shards", 1).put("number_of_replicas", 0));
            ObjectNode props = mapper.createObjectNode();
            props.set("user_id", mapper.createObjectNode().put("type", "long"));
            props.set("conversation_id", mapper.createObjectNode().put("type", "long"));
            props.set("content", mapper.createObjectNode().put("type", "text"));
            props.set("saved_at", mapper.createObjectNode().put("type", "long"));
            props.set("source", mapper.createObjectNode().put("type", "keyword"));
            ObjectNode vec = mapper.createObjectNode();
            vec.put("type", "dense_vector");
            vec.put("dims", DIM);
            vec.put("index", true);
            vec.put("similarity", "cosine");
            props.set("embedding", vec);
            ObjectNode mapping = mapper.createObjectNode().set("properties", props);
            ObjectNode body = mapper.createObjectNode();
            body.set("settings", settings);
            body.set("mappings", mapping);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + SUMMARY_INDEX))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
            log.info("ES index {} created", SUMMARY_INDEX);
        } catch (Exception e) {
            log.error("Failed to create summary index: {}", e.getMessage());
        }
    }

    public void indexSummary(Long userId, Long conversationId, String content, float[] vec) {
        indexSummary(userId, conversationId, content, vec, "auto");
    }

    public void indexSummary(Long userId, Long conversationId, String content, float[] vec, String source) {
        try {
            ObjectNode doc = mapper.createObjectNode();
            doc.put("user_id", userId);
            doc.put("conversation_id", conversationId);
            doc.put("content", content);
            doc.put("saved_at", System.currentTimeMillis());
            doc.put("source", source);
            ArrayNode arr = mapper.createArrayNode();
            for (float v : vec) arr.add(v);
            doc.set("embedding", arr);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + SUMMARY_INDEX + "/_doc/" + conversationId + "_" + System.nanoTime()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(doc)))
                .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.error("Summary index failed: {}", e.getMessage());
        }
    }

    public List<com.aicodehub.service.MemoryEntry> searchSummaries(Long userId, float[] qVec, int topK) {
        try {
            ArrayNode qArr = mapper.createArrayNode();
            for (float v : qVec) qArr.add(v);
            ObjectNode knn = mapper.createObjectNode();
            knn.put("field", "embedding");
            knn.set("query_vector", qArr);
            knn.put("k", topK);
            knn.put("num_candidates", Math.min(topK * 3, 50));
            knn.set("filter", mapper.createObjectNode().set("term", mapper.createObjectNode().put("user_id", userId)));
            ObjectNode body = mapper.createObjectNode();
            body.set("knn", knn);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUrl + "/" + SUMMARY_INDEX + "/_search"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            List<com.aicodehub.service.MemoryEntry> results = new ArrayList<>();
            JsonNode hits = mapper.readTree(resp.body()).path("hits").path("hits");
            for (JsonNode hit : hits) {
                JsonNode src = hit.get("_source");
                results.add(new com.aicodehub.service.MemoryEntry(
                    src.get("content").asText(),
                    src.has("saved_at") ? src.get("saved_at").asLong() : System.currentTimeMillis(),
                    src.has("source") ? src.get("source").asText() : "auto"
                ));
            }
            return results;
        } catch (Exception e) {
            log.error("Summary search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
