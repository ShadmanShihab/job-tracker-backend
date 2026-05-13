package com.jobtracker.service;

import com.jobtracker.dto.DashboardResponse;
import com.jobtracker.dto.JobApplicationRequest;
import com.jobtracker.dto.JobApplicationResponse;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.entity.enums.WorkLocation;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.JobApplicationRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private S3Service s3Service;

    @InjectMocks
    private JobApplicationService jobApplicationService;

    private User testUser;
    private UUID userId;
    private UUID appId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        appId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .name("Test User")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user@example.com");
        SecurityContextHolder.setContext(securityContext);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
    }

    private JobApplicationRequest buildRequest() {
        JobApplicationRequest request = new JobApplicationRequest();
        request.setCompanyName("Acme Corp");
        request.setPositionTitle("Software Engineer");
        request.setStatus(ApplicationStatus.APPLIED);
        request.setDateApplied(LocalDate.now());
        request.setSalaryExpectation(BigDecimal.valueOf(80000));
        request.setWorkLocation(WorkLocation.REMOTE);
        request.setRequiredSkills(List.of("Java", "Spring"));
        request.setNotes("Good opportunity");
        return request;
    }

    private JobApplication buildApplication() {
        return JobApplication.builder()
                .id(appId)
                .user(testUser)
                .companyName("Acme Corp")
                .positionTitle("Software Engineer")
                .status(ApplicationStatus.APPLIED)
                .screenshotKeys(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // --- create ---

    @Test
    void create_withoutFiles_savesApplicationAndReturnsResponse() throws IOException {
        mockSecurityContext();
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationResponse response = jobApplicationService.create(buildRequest(), null, null);

        assertThat(response.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(response.getPositionTitle()).isEqualTo("Software Engineer");
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        verify(jobApplicationRepository).save(any(JobApplication.class));
        verifyNoInteractions(s3Service);
    }

    @Test
    void create_withCvFile_uploadsCvAndPopulatesUrl() throws IOException {
        mockSecurityContext();
        MultipartFile cvFile = mock(MultipartFile.class);
        when(cvFile.isEmpty()).thenReturn(false);
        when(s3Service.uploadFile(cvFile, "cv/" + userId)).thenReturn("cv/some-key");
        when(s3Service.generatePresignedUrl("cv/some-key")).thenReturn("https://s3.example.com/cv");
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationResponse response = jobApplicationService.create(buildRequest(), cvFile, null);

        assertThat(response.getCvFileUrl()).isEqualTo("https://s3.example.com/cv");
        verify(s3Service).uploadFile(cvFile, "cv/" + userId);
    }

    @Test
    void create_withEmptyCvFile_doesNotUpload() throws IOException {
        mockSecurityContext();
        MultipartFile cvFile = mock(MultipartFile.class);
        when(cvFile.isEmpty()).thenReturn(true);
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobApplicationService.create(buildRequest(), cvFile, null);

        verify(s3Service, never()).uploadFile(any(), any());
    }

    @Test
    void create_withScreenshots_uploadsEachScreenshot() throws IOException {
        mockSecurityContext();
        MultipartFile shot1 = mock(MultipartFile.class);
        MultipartFile shot2 = mock(MultipartFile.class);
        when(shot1.isEmpty()).thenReturn(false);
        when(shot2.isEmpty()).thenReturn(false);
        when(s3Service.uploadFile(shot1, "screenshots/" + userId)).thenReturn("shots/key1");
        when(s3Service.uploadFile(shot2, "screenshots/" + userId)).thenReturn("shots/key2");
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobApplicationService.create(buildRequest(), null, List.of(shot1, shot2));

        verify(s3Service).uploadFile(shot1, "screenshots/" + userId);
        verify(s3Service).uploadFile(shot2, "screenshots/" + userId);
    }

    @Test
    void create_withUnknownUser_throwsResourceNotFoundException() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("unknown@example.com");
        SecurityContextHolder.setContext(securityContext);
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.create(buildRequest(), null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // --- getAll ---

    @Test
    void getAll_withoutStatusFilter_returnsAllUserApplications() {
        mockSecurityContext();
        when(jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(buildApplication()));

        List<JobApplicationResponse> result = jobApplicationService.getAll(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyName()).isEqualTo("Acme Corp");
        verify(jobApplicationRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void getAll_withStatusFilter_queriesWithStatus() {
        mockSecurityContext();
        when(jobApplicationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ApplicationStatus.INTERVIEWING))
                .thenReturn(List.of());

        List<JobApplicationResponse> result = jobApplicationService.getAll(ApplicationStatus.INTERVIEWING);

        assertThat(result).isEmpty();
        verify(jobApplicationRepository).findByUserIdAndStatusOrderByCreatedAtDesc(userId, ApplicationStatus.INTERVIEWING);
        verify(jobApplicationRepository, never()).findByUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void getAll_whenNoApplications_returnsEmptyList() {
        mockSecurityContext();
        when(jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertThat(jobApplicationService.getAll(null)).isEmpty();
    }

    // --- getById ---

    @Test
    void getById_withValidId_returnsResponse() {
        mockSecurityContext();
        when(jobApplicationRepository.findByIdAndUserId(appId, userId))
                .thenReturn(Optional.of(buildApplication()));

        JobApplicationResponse response = jobApplicationService.getById(appId);

        assertThat(response.getId()).isEqualTo(appId);
        assertThat(response.getCompanyName()).isEqualTo("Acme Corp");
    }

    @Test
    void getById_withIdBelongingToOtherUser_throwsResourceNotFoundException() {
        mockSecurityContext();
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.getById(appId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job application not found");
    }

    // --- update ---

    @Test
    void update_withValidId_appliesChangesAndSaves() throws IOException {
        mockSecurityContext();
        JobApplication app = buildApplication();
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationRequest request = buildRequest();
        request.setCompanyName("New Corp");
        request.setStatus(ApplicationStatus.INTERVIEWING);

        JobApplicationResponse response = jobApplicationService.update(appId, request, null, null);

        assertThat(response.getCompanyName()).isEqualTo("New Corp");
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    void update_withNewCvFile_deletesOldCvAndUploadsNew() throws IOException {
        mockSecurityContext();
        JobApplication app = buildApplication();
        app.setCvFileKey("old-cv-key");
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile newCv = mock(MultipartFile.class);
        when(newCv.isEmpty()).thenReturn(false);
        when(s3Service.uploadFile(newCv, "cv/" + userId)).thenReturn("new-cv-key");

        jobApplicationService.update(appId, buildRequest(), newCv, null);

        verify(s3Service).deleteFile("old-cv-key");
        verify(s3Service).uploadFile(newCv, "cv/" + userId);
    }

    @Test
    void update_withNewScreenshots_deletesOldAndUploadsNew() throws IOException {
        mockSecurityContext();
        JobApplication app = buildApplication();
        app.setScreenshotKeys(new ArrayList<>(List.of("old-shot-1", "old-shot-2")));
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile newShot = mock(MultipartFile.class);
        when(newShot.isEmpty()).thenReturn(false);
        when(s3Service.uploadFile(newShot, "screenshots/" + userId)).thenReturn("new-shot-key");

        jobApplicationService.update(appId, buildRequest(), null, List.of(newShot));

        verify(s3Service).deleteFile("old-shot-1");
        verify(s3Service).deleteFile("old-shot-2");
        verify(s3Service).uploadFile(newShot, "screenshots/" + userId);
    }

    @Test
    void update_withInvalidId_throwsResourceNotFoundException() {
        mockSecurityContext();
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.update(appId, buildRequest(), null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job application not found");
    }

    // --- delete ---

    @Test
    void delete_withValidIdAndNoFiles_deletesApplication() {
        mockSecurityContext();
        when(jobApplicationRepository.findByIdAndUserId(appId, userId))
                .thenReturn(Optional.of(buildApplication()));

        jobApplicationService.delete(appId);

        verify(jobApplicationRepository).delete(any(JobApplication.class));
        verifyNoInteractions(s3Service);
    }

    @Test
    void delete_withCvAndScreenshots_deletesAllFilesFromS3() {
        mockSecurityContext();
        JobApplication app = buildApplication();
        app.setCvFileKey("cv-key");
        app.setScreenshotKeys(List.of("shot-1", "shot-2"));
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(app));

        jobApplicationService.delete(appId);

        verify(s3Service).deleteFile("cv-key");
        verify(s3Service).deleteFile("shot-1");
        verify(s3Service).deleteFile("shot-2");
        verify(jobApplicationRepository).delete(app);
    }

    @Test
    void delete_withInvalidId_throwsResourceNotFoundException() {
        mockSecurityContext();
        when(jobApplicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.delete(appId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job application not found");

        verify(jobApplicationRepository, never()).delete(any());
    }

    // --- getDashboard ---

    @Test
    void getDashboard_returnsCorrectTotalAndStatusCounts() {
        mockSecurityContext();
        when(jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(buildApplication(), buildApplication()));
        when(jobApplicationRepository.countByStatusForUser(userId))
                .thenReturn(List.of(
                        new Object[]{ApplicationStatus.APPLIED, 2L},
                        new Object[]{ApplicationStatus.REJECTED, 0L}
                ));

        DashboardResponse response = jobApplicationService.getDashboard();

        assertThat(response.getTotalApplications()).isEqualTo(2);
        assertThat(response.getCountByStatus().get("APPLIED")).isEqualTo(2L);
        assertThat(response.getCountByStatus().get("REJECTED")).isEqualTo(0L);
    }

    @Test
    void getDashboard_withNoApplications_returnsZeroForAllStatuses() {
        mockSecurityContext();
        when(jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(jobApplicationRepository.countByStatusForUser(userId)).thenReturn(List.of());

        DashboardResponse response = jobApplicationService.getDashboard();

        assertThat(response.getTotalApplications()).isEqualTo(0);
        response.getCountByStatus().values().forEach(count -> assertThat(count).isEqualTo(0L));
    }

    @Test
    void getDashboard_allStatusesAreRepresentedInResponse() {
        mockSecurityContext();
        when(jobApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(jobApplicationRepository.countByStatusForUser(userId)).thenReturn(List.of());

        DashboardResponse response = jobApplicationService.getDashboard();

        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(response.getCountByStatus()).containsKey(status.name());
        }
    }
}
