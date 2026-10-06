package com.mycompany.do_an_ltm_2380600550.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record AlertPageResponse(List<AlertResponse> content, long totalElements,
                                int totalPages, int number, int size) {
    public static AlertPageResponse from(Page<AlertResponse> page) {
        return new AlertPageResponse(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }
}
