package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.GroupDiscountPolicy;

import java.util.List;
import java.util.Optional;

public interface GroupDiscountPolicyService {

    List<GroupDiscountPolicy> getAllPolicies();

    List<GroupDiscountPolicy> getActivePolicies();

    Optional<GroupDiscountPolicy> getPolicyById(Long id);

    Optional<GroupDiscountPolicy> findBestPolicy(int passengerCount);

    GroupDiscountPolicy savePolicy(GroupDiscountPolicy policy);

    void deletePolicy(Long id);

    boolean matchesPassengerCount(GroupDiscountPolicy policy, int passengerCount);
}