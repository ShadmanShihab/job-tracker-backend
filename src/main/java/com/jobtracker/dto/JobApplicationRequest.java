package com.jobtracker.dto;

import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.entity.enums.WorkLocation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class JobApplicationRequest {
    @NotBlank
    private String companyName;

    @NotBlank
    private String positionTitle;

    private String jobPostUrl;
    private String jobDescription;
    private List<String> requiredSkills;

    @NotNull
    private ApplicationStatus status;

    private LocalDate dateApplied;
    private BigDecimal salaryExpectation;
    private WorkLocation workLocation;
    private String contactPersonName;
    private String contactPersonEmail;
    private String notes;
}
