package com.example.demo.support;

import org.junit.jupiter.params.provider.Arguments;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class JsonFiles {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonFiles() {
    }

    public static String read(String name) {
        try (InputStream in = JsonFiles.class.getResourceAsStream("/json/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("JSON file not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** All cases from every endpoint file (json/*.json) - used as a JUnit @MethodSource. */
    public static Stream<Arguments> allCases() {
        List<Arguments> result = new ArrayList<>();
        for (String file : endpointFiles()) {
            JsonNode root = MAPPER.readTree(read(file));
            if (!root.has("endpoint") || !root.has("cases")) {
                continue; // not an endpoint file
            }
            for (Map.Entry<String, JsonNode> e : root.get("cases").properties()) {
                result.add(Arguments.of(new ApiCase(file, e.getKey(), root.get("endpoint"), e.getValue())));
            }
        }
        return result.stream();
    }

    /** "METHOD /path" for every endpoint covered by at least one JSON case (case overrides included). */
    public static Set<String> coveredEndpoints() {
        Set<String> result = new TreeSet<>();
        allCases().forEach(a -> {
            ApiCase tc = (ApiCase) a.get()[0];
            result.add(tc.method() + " " + tc.path());
        });
        return result;
    }

    private static List<String> endpointFiles() {
        try (Stream<Path> files = Files.list(Path.of(JsonFiles.class.getResource("/json").toURI()))) {
            return files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * One test case together with the endpoint it targets. Request-side values (method, path,
     * headers, queryParams, pathParams, cookies, formParams, contentType) may be defined on the
     * endpoint as defaults and overridden/extended by each case.
     */
    public record ApiCase(String file, String name, JsonNode endpoint, JsonNode data) {

        public String method() {
            return text("method", "GET").toUpperCase();
        }

        public String path() {
            return text("path", "/");
        }

        public String contentType() {
            String ct = text("contentType", null);
            if (ct == null && body() != null && !data.has("formParams") && !isText(bodyNode())) {
                return "application/json";
            }
            return ct;
        }

        public Map<String, String> headers() {
            return map("headers");
        }

        /** Endpoint-level entries merged with case-level entries (case wins). */
        public Map<String, String> map(String field) {
            Map<String, String> result = new LinkedHashMap<>(toMap(endpoint.path(field)));
            result.putAll(toMap(data.path(field)));
            return result;
        }

        public Map<String, String> mapAt(String pointer) {
            return toMap(data.at(pointer));
        }

        /** Request body: JSON objects/arrays are serialized, strings are sent as-is (raw/XML/text). */
        public String body() {
            JsonNode body = bodyNode();
            if (body.isMissingNode() || body.isNull()) {
                return null;
            }
            return isText(body) ? body.asString() : body.toString();
        }

        private JsonNode bodyNode() {
            return data.has("body") ? data.get("body") : data.path("request");
        }

        private String text(String field, String fallback) {
            if (data.has(field)) {
                return data.get(field).asString();
            }
            return endpoint.has(field) ? endpoint.get(field).asString() : fallback;
        }

        public static boolean isText(JsonNode node) {
            return node.getNodeType() == JsonNodeType.STRING;
        }

        /** Converts a scalar JSON node to the Java type RestAssured's JsonPath returns. */
        public static Object value(JsonNode node) {
            if (node.isNumber()) {
                return node.isIntegralNumber() ? (Object) node.asInt() : (Object) node.floatValue();
            }
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            return node.isNull() ? null : node.asString();
        }

        private static Map<String, String> toMap(JsonNode node) {
            Map<String, String> result = new LinkedHashMap<>();
            if (node != null && node.isObject()) {
                for (Map.Entry<String, JsonNode> e : node.properties()) {
                    result.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asString() : e.getValue().toString());
                }
            }
            return result;
        }

        public boolean has(String field) {
            return data.has(field);
        }

        public JsonNode get(String field) {
            return data.get(field);
        }

        public JsonNode at(String pointer) {
            return data.at(pointer);
        }

        @Override
        public String toString() {
            return method() + " " + path() + " [" + name + "]";
        }
    }
}
