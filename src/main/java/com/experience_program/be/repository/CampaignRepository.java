package com.experience_program.be.repository;

import com.experience_program.be.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID>, JpaSpecificationExecutor<Campaign> {

    @Query("SELECT FUNCTION('DATE_FORMAT', c.requestDate, '%Y-%m'), c.status, COUNT(c) " +
           "FROM Campaign c " +
           "WHERE c.requestDate >= :startDate " +
           "GROUP BY FUNCTION('DATE_FORMAT', c.requestDate, '%Y-%m'), c.status")
    List<Object[]> countMonthlyCampaignsByStatusSince(@Param("startDate") LocalDateTime startDate);

    List<Campaign> findTop5ByOrderByRequestDateDesc();
}
