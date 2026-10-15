package com.example.demo.api;

import com.example.demo.support.ApiCalls;
import com.example.demo.support.JsonFiles;
import com.example.demo.support.JsonFiles.ApiCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Generic, data-driven API test against the embedded app: every case in every json/*.json file becomes one test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** Lists every endpoint in the application code and fails if any has no JSON test file/case. */
    @Test
    void everyEndpointHasJsonFile() {
        Set<String> inCode = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            if (!e.getValue().getBeanType().getPackageName().startsWith("com.example")) {
                continue; // skip framework endpoints such as /error
            }
            var methods = e.getKey().getMethodsCondition().getMethods();
            for (String pattern : e.getKey().getPatternValues()) {
                if (methods.isEmpty()) {
                    inCode.add("ANY " + pattern);
                }
                methods.forEach(m -> inCode.add(m.name() + " " + pattern));
            }
        }
        Set<String> covered = JsonFiles.coveredEndpoints();

        System.out.println("Endpoints found in code:");
        inCode.forEach(ep -> System.out.println("  " + ep + (isCovered(ep, covered) ? "  [json OK]" : "  [NO JSON]")));

        Set<String> missing = new TreeSet<>();
        inCode.stream().filter(ep -> !isCovered(ep, covered)).forEach(missing::add);
        if (!missing.isEmpty()) {
            throw new AssertionError("No JSON test file/case found in src/test/resources/json for endpoint(s): " + missing);
        }
    }

    private static boolean isCovered(String endpoint, Set<String> covered) {
        if (endpoint.startsWith("ANY ")) {
            String path = endpoint.substring(4);
            return covered.stream().anyMatch(c -> c.endsWith(" " + path));
        }
        return covered.contains(endpoint);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.example.demo.support.JsonFiles#allCases")
    void endpoint(ApiCase tc) throws Exception {
        ApiCalls.run("http://localhost:" + port, tc);
    }
}
