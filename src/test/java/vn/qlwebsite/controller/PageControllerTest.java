package vn.qlwebsite.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PageControllerTest {
    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/websites", "/websites/1", "/alerts", "/login"})
    void rendersFrontendRoutesWithExplicitDemoMode(String route) throws Exception {
        mvc.perform(get(route)).andExpect(status().isOk())
            .andExpect(view().name("app"))
            .andExpect(content().string(containsString("data-demo=\"true\"")))
            .andExpect(content().string(containsString("/js/app.js")));
    }

    @Test
    void servesLocalAssetsAndDoesNotPretendToProvideAnApi() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/data.js")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
        mvc.perform(get("/api/websites")).andExpect(status().isNotFound());
    }
}
