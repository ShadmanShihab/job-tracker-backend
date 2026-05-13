package com.jobtracker.dto;

import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.entity.enums.WorkLocation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class JobApplicationResponse {
    private UUID id;
    private String companyName;
    private String positionTitle;
    private String jobPostUrl;
    private String jobDescription;
    private List<String> requiredSkills;
    private ApplicationStatus status;
    private LocalDate dateApplied;
    private BigDecimal salaryExpectation;
    private WorkLocation workLocation;
    private String contactPersonName;
    private String contactPersonEmail;
    private String notes;
    private String cvFileUrl;
    private List<String> screenshotUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
