package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import com.hsf302.trainoffice.repository.GroupDiscountPolicyRepository;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GroupDiscountPolicyServiceImpl implements GroupDiscountPolicyService {

    private final GroupDiscountPolicyRepository groupDiscountPolicyRepository;

    public GroupDiscountPolicyServiceImpl(GroupDiscountPolicyRepository groupDiscountPolicyRepository) {
        this.groupDiscountPolicyRepository = groupDiscountPolicyRepository;
    }

    @Override
    public List<GroupDiscountPolicy> getAllPolicies() {
        return groupDiscountPolicyRepository.findAllByOrderByDisplayOrderAscPolicyNameAsc();
    }

    @Override
    public List<GroupDiscountPolicy> getActivePolicies() {
        return groupDiscountPolicyRepository.findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc();
    }

    @Override
    public Optional<GroupDiscountPolicy> getPolicyById(Long id) {
        return groupDiscountPolicyRepository.findById(id);
    }

    @Override
    public Optional<GroupDiscountPolicy> findBestPolicy(int passengerCount) {
        return getActivePolicies()
                .stream()
                .filter(policy -> matchesPassengerCount(policy, passengerCount))
                .max(
                        Comparator.comparing(GroupDiscountPolicy::getDiscountPercent)
                                .thenComparing(policy -> -policy.getDisplayOrder())
                );
    }

    @Override
    public GroupDiscountPolicy savePolicy(GroupDiscountPolicy policy) {
        normalize(policy);
        validate(policy);

        if (policy.getPolicyId() == null) {
            if (groupDiscountPolicyRepository.existsByPolicyCodeIgnoreCase(policy.getPolicyCode())) {
                throw new IllegalStateException(I18n.t("err.groupDiscountPolicyCodeAlreadyExists"));
            }
        } else {
            GroupDiscountPolicy oldPolicy = groupDiscountPolicyRepository.findById(policy.getPolicyId())
                    .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.groupDiscountPolicyDoesNotExist")));

            boolean changedCode = !oldPolicy.getPolicyCode().equalsIgnoreCase(policy.getPolicyCode());

            if (changedCode && groupDiscountPolicyRepository.existsByPolicyCodeIgnoreCase(policy.getPolicyCode())) {
                throw new IllegalStateException(I18n.t("err.groupDiscountPolicyCodeAlreadyExists"));
            }
        }

        return groupDiscountPolicyRepository.save(policy);
    }

    @Override
    public void deletePolicy(Long id) {
        if (!groupDiscountPolicyRepository.existsById(id)) {
            throw new IllegalArgumentException(I18n.t("err.groupDiscountPolicyDoesNotExist"));
        }

        groupDiscountPolicyRepository.deleteById(id);
    }

    @Override
    public boolean matchesPassengerCount(GroupDiscountPolicy policy, int passengerCount) {
        if (policy == null || !Boolean.TRUE.equals(policy.getActive())) {
            return false;
        }

        if (policy.getMinPassengers() != null && passengerCount < policy.getMinPassengers()) {
            return false;
        }

        return policy.getMaxPassengers() == null || passengerCount <= policy.getMaxPassengers();
    }

    private void normalize(GroupDiscountPolicy policy) {
        if (policy.getPolicyCode() != null) {
            policy.setPolicyCode(policy.getPolicyCode().trim().toUpperCase());
        }

        if (policy.getPolicyName() != null) {
            policy.setPolicyName(policy.getPolicyName().trim());
        }

        if (policy.getDiscountPercent() == null) {
            policy.setDiscountPercent(BigDecimal.ZERO);
        }

        if (policy.getActive() == null) {
            policy.setActive(true);
        }

        if (policy.getDisplayOrder() == null || policy.getDisplayOrder() < 1) {
            policy.setDisplayOrder(1);
        }
    }

    private void validate(GroupDiscountPolicy policy) {
        if (policy.getPolicyCode() == null || policy.getPolicyCode().isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.policyCodeIsRequired"));
        }

        if (policy.getPolicyName() == null || policy.getPolicyName().isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.policyNameIsRequired"));
        }

        if (policy.getMinPassengers() == null || policy.getMinPassengers() < 2) {
            throw new IllegalArgumentException(I18n.t("err.minimumPassengersMustBeAtLeast2"));
        }

        if (policy.getMaxPassengers() != null && policy.getMaxPassengers() < policy.getMinPassengers()) {
            throw new IllegalArgumentException(I18n.t("err.maximumPassengersMustBeGreaterThanOr"));
        }

        if (policy.getDiscountPercent().compareTo(BigDecimal.ZERO) < 0
                || policy.getDiscountPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(I18n.t("err.discountPercentMustBeFrom0To"));
        }
    }
}