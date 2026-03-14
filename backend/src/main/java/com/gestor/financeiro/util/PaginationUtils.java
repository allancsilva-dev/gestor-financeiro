package com.gestor.financeiro.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static Pageable enforceMaxSize(Pageable pageable, int maxSize) {
        int sanitizedSize = Math.min(pageable.getPageSize(), maxSize);
        return PageRequest.of(pageable.getPageNumber(), sanitizedSize, pageable.getSort());
    }
}
