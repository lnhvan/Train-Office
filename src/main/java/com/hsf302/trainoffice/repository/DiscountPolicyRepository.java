package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.DiscountPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscountPolicyRepository extends JpaRepository<DiscountPolicy, Long> {

    boolean existsByPolicyCodeIgnoreCase(String policyCode);

    Optional<DiscountPolicy> findByPolicyCodeIgnoreCase(String policyCode);

    Optional<DiscountPolicy> findByPolicyCodeIgnoreCaseAndActiveTrue(String policyCode);

    List<DiscountPolicy> findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc();

    List<DiscountPolicy> findAllByOrderByDisplayOrderAscPolicyNameAsc();
}