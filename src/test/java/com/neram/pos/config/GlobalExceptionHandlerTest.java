package com.neram.pos.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class Throws {
        @GetMapping("/gone")
        String gone() throws Exception {
            throw new AsyncRequestNotUsableException("Response not usable after response errors.");
        }

        @GetMapping("/broken")
        String broken() throws Exception {
            throw new Exception("boom");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Throws()).setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void aBrowserClosingALiveUpdatesConnectionIsNotAnError() throws Exception {
        String body = mvc.perform(get("/gone")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("", body, "there is nobody left to answer");
    }

    @Test
    void aRealFailureStillAnswersWithATraceableServerError() throws Exception {
        mvc.perform(get("/broken")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.traceId").exists());
    }
}
