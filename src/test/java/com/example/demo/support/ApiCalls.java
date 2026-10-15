package com.example.demo.support;

import com.example.demo.support.JsonFiles.ApiCase;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Assertions;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** Shared RestAssured logic: runs one JSON case against any base URI (embedded or external server). */
public final class ApiCalls {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private ApiCalls() {
    }

    public static void run(String baseUri, ApiCase tc) throws Exception {
        ValidatableResponse res = request(baseUri, tc).request(tc.method(), tc.path()).then().log().ifValidationFails();
        assertResponse(tc, res);
    }

    /**
     * Replaces {@code ${NAME}} / {@code ${NAME:default}} with a system property or environment
     * variable, so credentials/tokens for external environments are not stored in JSON files.
     */
    public static String resolve(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = System.getProperty(m.group(1), System.getenv(m.group(1)));
            if (v == null) {
                v = m.group(2);
            }
            if (v == null) {
                throw new IllegalStateException("No system property or env variable for ${" + m.group(1) + "}");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Per-request spec (no static RestAssured config) so tests are safe to run in parallel. */
    private static RequestSpecification request(String baseUri, ApiCase tc) {
        RequestSpecification spec = given().baseUri(baseUri);
        if (Boolean.parseBoolean(System.getProperty("api.relaxedHttps", "false"))) {
            spec.relaxedHTTPSValidation();
        }

        JsonNode auth = tc.at("/auth");
        if (!auth.isMissingNode()) {
            String type = auth.path("type").asString("basic");
            switch (type.toLowerCase()) {
                case "basic" -> spec.auth().preemptive().basic(
                    resolve(auth.path("username").asString()), resolve(auth.path("password").asString()));
                case "bearer" -> spec.header("Authorization", "Bearer " + resolve(auth.path("token").asString()));
                case "apikey" -> spec.header(auth.path("header").asString("X-API-Key"), resolve(auth.path("key").asString()));
                default -> throw new IllegalArgumentException("Unsupported auth type: " + type);
            }
        }
        tc.headers().forEach((k, v) -> spec.header(k, resolve(v)));
        tc.map("queryParams").forEach((k, v) -> spec.queryParam(k, resolve(v)));
        tc.map("pathParams").forEach((k, v) -> spec.pathParam(k, resolve(v)));
        tc.map("cookies").forEach((k, v) -> spec.cookie(k, resolve(v)));
        tc.map("formParams").forEach((k, v) -> spec.formParam(k, resolve(v)));

        String contentType = tc.contentType();
        if (contentType != null) {
            spec.contentType(contentType);
        }
        String body = tc.body();
        if (body != null) {
            spec.body(body);
        }
        return spec;
    }

    private static void assertResponse(ApiCase tc, ValidatableResponse res) throws Exception {
        JsonNode expected = tc.at("/response");
        res.statusCode(expected.path("status").asInt());
        tc.mapAt("/response/headers").forEach((name, value) -> res.header(name, containsString(value)));
        if (expected.has("contentType")) {
            res.contentType(containsString(expected.get("contentType").asString()));
        }
        if (expected.has("jsonPath")) {
            for (var e : expected.get("jsonPath").properties()) {
                res.body(e.getKey(), equalTo(ApiCase.value(e.getValue())));
            }
        }
        if (expected.has("bodyContains")) {
            res.body(containsString(expected.get("bodyContains").asString()));
        }
        if (expected.has("schema")) {
            res.body(matchesJsonSchemaInClasspath(expected.get("schema").asString()));
        }
        if (expected.has("body")) {
            JsonNode body = expected.get("body");
            String actual = res.extract().asString();
            if (ApiCase.isText(body)) {
                Assertions.assertEquals(body.asString(), actual);
            } else {
                JSONAssert.assertEquals(body.toString(), actual, expected.path("strict").asBoolean(true));
            }
        }
    }
}
