package com.carticom.repository;

import com.carticom.model.EmailCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

    List<EmailCampaign> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
