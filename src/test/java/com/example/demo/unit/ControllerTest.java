package com.example.demo.unit;

import com.example.demo.config.SecurityConfig;
import com.example.demo.controller.GreetingController;
import com.example.demo.controller.HealthController;
import com.example.demo.service.GreetingService;
import com.example.demo.support.JsonFiles.ApiCase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.example.demo.support.ApiCalls.resolve;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, GreetingController.class})
@Import({SecurityConfig.class, GreetingService.class})
class ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.example.demo.support.JsonFiles#allCases")
    void endpoint(ApiCase tc) throws Exception {
        String path = tc.path();
        for (var p : tc.map("pathParams").entrySet()) {
            path = path.replace("{" + p.getKey() + "}", p.getValue());
        }
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(tc.method()), path);
        if (tc.has("auth")) {
            String type = tc.at("/auth/type").asString("basic").toLowerCase();
            switch (type) {
                case "basic" -> builder.with(httpBasic(resolve(tc.at("/auth/username").asString()), resolve(tc.at("/auth/password").asString())));
                case "bearer" -> builder.header("Authorization", "Bearer " + resolve(tc.at("/auth/token").asString()));
                case "apikey" -> builder.header(tc.at("/auth/header").asString("X-API-Key"), resolve(tc.at("/auth/key").asString()));
                default -> throw new IllegalArgumentException("Unsupported auth type: " + type);
            }
        }
        tc.headers().forEach((k, v) -> builder.header(k, v));
        tc.map("queryParams").forEach((k, v) -> builder.queryParam(k, v));
        tc.map("formParams").forEach((k, v) -> builder.param(k, v));
        tc.map("cookies").forEach((k, v) -> builder.cookie(new Cookie(k, v)));
        if (tc.contentType() != null) {
            builder.contentType(MediaType.parseMediaType(tc.contentType()));
        }
        if (tc.body() != null) {
            builder.content(tc.body());
        }
        ResultActions result = mockMvc.perform(builder);
        result.andExpect(status().is(tc.at("/response/status").asInt()));
        if (tc.get("response").has("body")) {
            result.andExpect(content().json(tc.at("/response/body").toString(), true));
        }
    }
}
