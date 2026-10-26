package com.neram.pos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Pagination for list endpoints that declare a {@link Pageable} parameter.
 * Paging is opt-in: a request must send both {@code page} (0-based) and {@code size}. Without them the pageable is
 * unpaged, so every caller that does not ask for a page still receives the whole list exactly as before.
 * The page size is capped so a client cannot ask for an unbounded page.
 */
@Configuration
public class PagingConfig {

    static final int MAX_PAGE_SIZE = 100;

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableResolverCustomizer() {
        return resolver -> {
            resolver.setFallbackPageable(Pageable.unpaged());
            resolver.setMaxPageSize(MAX_PAGE_SIZE);
        };
    }
}
