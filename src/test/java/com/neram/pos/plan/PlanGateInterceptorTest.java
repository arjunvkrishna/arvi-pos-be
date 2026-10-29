package com.neram.pos.plan;

import com.neram.pos.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlanGateInterceptorTest {

    @RestController
    @RequestMapping("/api/crm")
    @RequiresFeature(Feature.CRM)
    static class CrmController {
        @GetMapping("/list")
        String list() { return "customers"; }

        @OpenInEveryPlan
        @GetMapping("/unsubscribe")
        String unsubscribe() { return "bye"; }
    }

    @RestController
    @RequestMapping("/api/mixed")
    static class MixedController {
        @GetMapping("/basic")
        String basic() { return "basic"; }

        @RequiresFeature(Feature.EXPORTS)
        @GetMapping("/export")
        String export() { return "file"; }
    }

    private PlanService plans;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        plans = mock(PlanService.class);
        doNothing().when(plans).require(any());
        mvc = MockMvcBuilders.standaloneSetup(new CrmController(), new MixedController())
                .addInterceptors(new PlanGateInterceptor(plans))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void planLacks(Feature feature, Plan current) {
        doThrow(new PlanFeatureException(feature, current)).when(plans).require(feature);
    }

    @Test
    void aModuleTheCafeHasIsServedNormally() throws Exception {
        mvc.perform(get("/api/crm/list")).andExpect(status().isOk()).andExpect(content().string("customers"));
        verify(plans).require(Feature.CRM);
    }

    @Test
    void aModuleTheCafeLacksIsRefusedWith403AndAPlanCode() throws Exception {
        planLacks(Feature.CRM, Plan.STARTER);
        mvc.perform(get("/api/crm/list"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE"))
                .andExpect(jsonPath("$.feature").value("CRM"))
                .andExpect(jsonPath("$.requiredPlan").value("PRO"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("STARTER")));
    }

    @Test
    void anEndpointMarkedOpenWorksOnEveryPlan() throws Exception {
        planLacks(Feature.CRM, Plan.STARTER);
        mvc.perform(get("/api/crm/unsubscribe")).andExpect(status().isOk()).andExpect(content().string("bye"));
        verify(plans, never()).require(any());
    }

    @Test
    void anEndpointNobodyMarkedIsNeverAskedAbout() throws Exception {
        mvc.perform(get("/api/mixed/basic")).andExpect(status().isOk());
        verify(plans, never()).require(any());
    }

    @Test
    void oneEndpointInsideAnOpenControllerCanBeGated() throws Exception {
        planLacks(Feature.EXPORTS, Plan.STARTER);
        mvc.perform(get("/api/mixed/basic")).andExpect(status().isOk());
        mvc.perform(get("/api/mixed/export")).andExpect(status().isForbidden()).andExpect(jsonPath("$.feature").value("EXPORTS"));
    }
}
