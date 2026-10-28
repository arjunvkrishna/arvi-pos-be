package com.neram.pos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tells the interactive scheduled jobs (Telegram, daily close, printer alerts, SSE heartbeats, token cleanup)
 * whether this process should run them. The headless report worker container sets
 * APP_JOBS_INTERACTIVE_ENABLED=false so they do not run twice (two Telegram pollers on one bot token
 * would fight over getUpdates and every daily report would be sent twice).
 */
@Component
public class JobMode {

    // read straight from the environment too: runners and @PostConstruct hooks can fire before this bean is built
    private static volatile boolean interactive = !"false".equalsIgnoreCase(System.getenv("APP_JOBS_INTERACTIVE_ENABLED"));

    public JobMode(@Value("${app.jobs.interactive-enabled:true}") boolean enabled) {
        interactive = enabled;
    }

    public static boolean interactive() {
        return interactive;
    }
}
