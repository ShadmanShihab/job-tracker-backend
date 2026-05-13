package com.jobtracker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DashboardResponse {
    private long totalApplications;
    private Map<String, Long> countByStatus;
}
