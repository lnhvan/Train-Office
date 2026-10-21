package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupDiscountPolicyRepository extends JpaRepository<GroupDiscountPolicy, Long> {

    boolean existsByPolicyCodeIgnoreCase(String policyCode);

    List<GroupDiscountPolicy> findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc();

    List<GroupDiscountPolicy> findAllByOrderByDisplayOrderAscPolicyNameAsc();
}