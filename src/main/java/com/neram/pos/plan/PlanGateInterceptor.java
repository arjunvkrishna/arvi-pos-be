package com.neram.pos.plan;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Refuses requests for a module the cafe's plan does not include, before the controller runs. */
@Component
@RequiredArgsConstructor
public class PlanGateInterceptor implements HandlerInterceptor {

    private final PlanService plans;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod endpoint)) return true;
        if (AnnotatedElementUtils.hasAnnotation(endpoint.getMethod(), OpenInEveryPlan.class)) return true;
        RequiresFeature required = AnnotatedElementUtils.findMergedAnnotation(endpoint.getMethod(), RequiresFeature.class);
        if (required == null) required = AnnotatedElementUtils.findMergedAnnotation(endpoint.getBeanType(), RequiresFeature.class);
        if (required != null) plans.require(required.value());
        return true;
    }
}
