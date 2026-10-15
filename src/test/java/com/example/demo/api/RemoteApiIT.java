package com.example.demo.api;

import com.example.demo.support.ApiCalls;
import com.example.demo.support.JsonFiles.ApiCase;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Same JSON-driven RestAssured cases as {@link ApiIT}, but against an already deployed (external)
 * server - e.g. the passive slot of a blue/green deployment before promoting it to active.
 * No Spring context is started. Runs only when {@code -Dapi.baseUri=https://host[:port]} is given.
 */
@EnabledIfSystemProperty(named = "api.baseUri", matches = ".+")
class RemoteApiIT {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.example.demo.support.JsonFiles#allCases")
    void endpoint(ApiCase tc) throws Exception {
        ApiCalls.run(System.getProperty("api.baseUri").replaceAll("/+$", ""), tc);
    }
}
