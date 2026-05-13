package com.jobtracker.service;

import com.jobtracker.dto.DashboardResponse;
import com.jobtracker.dto.JobApplicationRequest;
import com.jobtracker.dto.JobApplicationResponse;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.JobApplicationRepository;
import com.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public JobApplicationResponse create(JobApplicationRequest request,
                                          MultipartFile cvFile,
                                          List<MultipartFile> screenshots) throws IOException {
        User user = getCurrentUser();

        JobApplication application = JobApplication.builder()
                .user(user)
                .companyName(request.getCompanyName())
                .positionTitle(request.getPositionTitle())
                .jobPostUrl(request.getJobPostUrl())
                .jobDescription(request.getJobDescription())
                .requiredSkills(request.getRequiredSkills())
                .status(request.getStatus())
                .dateApplied(request.getDateApplied())
                .salaryExpectation(request.getSalaryExpectation())
                .workLocation(request.getWorkLocation())
                .contactPersonName(request.getContactPersonName())
                .contactPersonEmail(request.getContactPersonEmail())
                .notes(request.getNotes())
                .screenshotKeys(new ArrayList<>())
                .build();

        if (cvFile != null && !cvFile.isEmpty()) {
            String cvKey = s3Service.uploadFile(cvFile, "cv/" + user.getId());
            application.setCvFileKey(cvKey);
        }

        if (screenshots != null) {
            List<String> screenshotKeys = new ArrayList<>();
            for (MultipartFile screenshot : screenshots) {
                if (!screenshot.isEmpty()) {
                    String key = s3Service.uploadFile(screenshot, "screenshots/" + user.getId());
                    screenshotKeys.add(key);
                }
            }
            application.setScreenshotKeys(screenshotKeys);
        }

        jobApplicationRepository.save(application);
        return toResponse(application);
    }

    public List<JobApplicationResponse> getAll(ApplicationStatus status) {
        User user = getCurrentUser();
        List<JobApplication> applications = status != null
                ? jobApplicationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status)
                : jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return applications.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public JobApplicationResponse getById(UUID id) {
        User user = getCurrentUser();
        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Job application not found"));
        return toResponse(application);
    }

    public JobApplicationResponse update(UUID id, JobApplicationRequest request,
                                          MultipartFile cvFile,
                                          List<MultipartFile> screenshots) throws IOException {
        User user = getCurrentUser();
        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Job application not found"));

        application.setCompanyName(request.getCompanyName());
        application.setPositionTitle(request.getPositionTitle());
        application.setJobPostUrl(request.getJobPostUrl());
        application.setJobDescription(request.getJobDescription());
        application.setRequiredSkills(request.getRequiredSkills());
        application.setStatus(request.getStatus());
        application.setDateApplied(request.getDateApplied());
        application.setSalaryExpectation(request.getSalaryExpectation());
        application.setWorkLocation(request.getWorkLocation());
        application.setContactPersonName(request.getContactPersonName());
        application.setContactPersonEmail(request.getContactPersonEmail());
        application.setNotes(request.getNotes());

        if (cvFile != null && !cvFile.isEmpty()) {
            if (application.getCvFileKey() != null) {
                s3Service.deleteFile(application.getCvFileKey());
            }
            application.setCvFileKey(s3Service.uploadFile(cvFile, "cv/" + user.getId()));
        }

        if (screenshots != null && !screenshots.isEmpty()) {
            if (application.getScreenshotKeys() != null) {
                application.getScreenshotKeys().forEach(s3Service::deleteFile);
            }
            List<String> screenshotKeys = new ArrayList<>();
            for (MultipartFile screenshot : screenshots) {
                if (!screenshot.isEmpty()) {
                    screenshotKeys.add(s3Service.uploadFile(screenshot, "screenshots/" + user.getId()));
                }
            }
            application.setScreenshotKeys(screenshotKeys);
        }

        jobApplicationRepository.save(application);
        return toResponse(application);
    }

    public void delete(UUID id) {
        User user = getCurrentUser();
        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Job application not found"));

        if (application.getCvFileKey() != null) {
            s3Service.deleteFile(application.getCvFileKey());
        }
        if (application.getScreenshotKeys() != null) {
            application.getScreenshotKeys().forEach(s3Service::deleteFile);
        }

        jobApplicationRepository.delete(application);
    }

    public DashboardResponse getDashboard() {
        User user = getCurrentUser();
        List<Object[]> counts = jobApplicationRepository.countByStatusForUser(user.getId());
        long total = jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).size();

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            countByStatus.put(status.name(), 0L);
        }
        for (Object[] row : counts) {
            countByStatus.put(((ApplicationStatus) row[0]).name(), (Long) row[1]);
        }

        return DashboardResponse.builder()
                .totalApplications(total)
                .countByStatus(countByStatus)
                .build();
    }

    private JobApplicationResponse toResponse(JobApplication app) {
        List<String> screenshotUrls = app.getScreenshotKeys() == null ? List.of() :
                app.getScreenshotKeys().stream()
                        .map(s3Service::generatePresignedUrl)
                        .collect(Collectors.toList());

        return JobApplicationResponse.builder()
                .id(app.getId())
                .companyName(app.getCompanyName())
                .positionTitle(app.getPositionTitle())
                .jobPostUrl(app.getJobPostUrl())
                .jobDescription(app.getJobDescription())
                .requiredSkills(app.getRequiredSkills())
                .status(app.getStatus())
                .dateApplied(app.getDateApplied())
                .salaryExpectation(app.getSalaryExpectation())
                .workLocation(app.getWorkLocation())
                .contactPersonName(app.getContactPersonName())
                .contactPersonEmail(app.getContactPersonEmail())
                .notes(app.getNotes())
                .cvFileUrl(app.getCvFileKey() != null ? s3Service.generatePresignedUrl(app.getCvFileKey()) : null)
                .screenshotUrls(screenshotUrls)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
