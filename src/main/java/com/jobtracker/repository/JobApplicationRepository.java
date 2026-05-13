package com.jobtracker.repository;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<JobApplication> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, ApplicationStatus status);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT j.status, COUNT(j) FROM JobApplication j WHERE j.user.id = :userId GROUP BY j.status")
    List<Object[]> countByStatusForUser(@Param("userId") UUID userId);
}
