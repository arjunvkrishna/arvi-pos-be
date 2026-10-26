package com.neram.pos.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Spring Data pagination for the list endpoints.
 *
 * <p>An endpoint takes a {@link Pageable} (so {@code ?page=0&size=15} works) and answers with the rows of that page as the same
 * JSON array it always returned, plus the paging facts in headers: {@code X-Total-Count}, {@code X-Total-Pages},
 * {@code X-Page} (0-based) and {@code X-Page-Size}. Without {@code page}/{@code size} the pageable is unpaged
 * (see PagingConfig) and the whole list comes back, so existing callers are not affected.
 */
public final class PagedResponse {

    public static final String TOTAL_COUNT = "X-Total-Count";
    public static final String TOTAL_PAGES = "X-Total-Pages";
    public static final String PAGE = "X-Page";
    public static final String PAGE_SIZE = "X-Page-Size";

    private PagedResponse() {
    }

    /** The page's rows as a JSON array with the paging headers. */
    public static <T> ResponseEntity<List<T>> of(Page<T> page) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT, String.valueOf(page.getTotalElements()))
                .header(TOTAL_PAGES, String.valueOf(page.getTotalPages()))
                .header(PAGE, String.valueOf(page.getNumber()))
                .header(PAGE_SIZE, String.valueOf(page.getSize()))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, String.join(", ", TOTAL_COUNT, TOTAL_PAGES, PAGE, PAGE_SIZE))
                .body(page.getContent());
    }

    /** Same, with each row converted for the response (the conversion only runs for the rows on the page). */
    public static <S, T> ResponseEntity<List<T>> of(Page<S> page, Function<S, T> mapper) {
        return of(page.map(mapper));
    }

    /** For a page whose row type is only known at run time (e.g. one of several report tables). */
    @SuppressWarnings("unchecked")
    public static ResponseEntity<List<Object>> ofAny(Page<?> page) {
        return of((Page<Object>) page);
    }

    /** One page of a list that is already in memory (filtered/derived data that a repository query cannot produce). */
    public static <T> Page<T> slice(List<T> all, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(all, Pageable.unpaged(), all.size());
        }
        long from = pageable.getOffset();
        if (from >= all.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, all.size());
        }
        int to = (int) Math.min(all.size(), from + pageable.getPageSize());
        return new PageImpl<>(all.subList((int) from, to), pageable, all.size());
    }

    /** The request's page and size with a default sort when the request did not ask for one (the API never does). */
    public static Pageable withSort(Pageable pageable, Sort sort) {
        if (pageable == null || pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return pageable.getSort().isSorted() ? pageable : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
