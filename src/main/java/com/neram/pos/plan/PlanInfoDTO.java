package com.neram.pos.plan;

import java.util.List;

/** What the screens need to know about the signed-in person's plan: its name, the two limits, and the modules that are on. */
public record PlanInfoDTO(String name, int maxBranches, int maxLogins, List<String> features) {
}
