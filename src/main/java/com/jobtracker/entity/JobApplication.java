package com.jobtracker.entity;

import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.entity.enums.WorkLocation;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_applications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String positionTitle;

    @Column
    private String jobPostUrl;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @ElementCollection
    @CollectionTable(name = "required_skills", joinColumns = @JoinColumn(name = "job_application_id"))
    @Column(name = "skill")
    private List<String> requiredSkills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column
    private LocalDate dateApplied;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryExpectation;

    @Enumerated(EnumType.STRING)
    @Column
    private WorkLocation workLocation;

    @Column
    private String contactPersonName;

    @Column
    private String contactPersonEmail;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private String cvFileKey;

    @ElementCollection
    @CollectionTable(name = "screenshot_keys", joinColumns = @JoinColumn(name = "job_application_id"))
    @Column(name = "s3_key")
    private List<String> screenshotKeys;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
