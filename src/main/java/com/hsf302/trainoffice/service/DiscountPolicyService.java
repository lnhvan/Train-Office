package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.DiscountPolicy;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface DiscountPolicyService {

    List<DiscountPolicy> getAllPolicies();

    List<DiscountPolicy> getActivePolicies();

    Optional<DiscountPolicy> getPolicyById(Long id);

    Optional<DiscountPolicy> getActivePolicyByCode(String policyCode);

    Optional<DiscountPolicy> findMatchingActivePolicy(LocalDate dateOfBirth);

    Optional<DiscountPolicy> findMatchingActivePolicyByAge(int age);

    String resolvePassengerType(LocalDate dateOfBirth);

    DiscountPolicy savePolicy(DiscountPolicy policy);

    void deletePolicy(Long id);

    boolean matchesAge(DiscountPolicy policy, int age);
}
