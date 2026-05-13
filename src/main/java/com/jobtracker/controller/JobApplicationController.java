package com.jobtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.JobApplicationRequest;
import com.jobtracker.dto.JobApplicationResponse;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<JobApplicationResponse> create(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "cv", required = false) MultipartFile cvFile,
            @RequestPart(value = "screenshots", required = false) List<MultipartFile> screenshots) throws IOException {
        JobApplicationRequest request = objectMapper.readValue(dataJson, JobApplicationRequest.class);
        return ResponseEntity.ok(jobApplicationService.create(request, cvFile, screenshots));
    }

    @GetMapping
    public ResponseEntity<List<JobApplicationResponse>> getAll(
            @RequestParam(required = false) ApplicationStatus status) {
        return ResponseEntity.ok(jobApplicationService.getAll(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobApplicationService.getById(id));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<JobApplicationResponse> update(
            @PathVariable UUID id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "cv", required = false) MultipartFile cvFile,
            @RequestPart(value = "screenshots", required = false) List<MultipartFile> screenshots) throws IOException {
        JobApplicationRequest request = objectMapper.readValue(dataJson, JobApplicationRequest.class);
        return ResponseEntity.ok(jobApplicationService.update(id, request, cvFile, screenshots));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
