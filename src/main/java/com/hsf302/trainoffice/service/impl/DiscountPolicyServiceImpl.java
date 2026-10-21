package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.repository.DiscountPolicyRepository;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.Period;

@Service
public class DiscountPolicyServiceImpl implements DiscountPolicyService {

    private final DiscountPolicyRepository discountPolicyRepository;

    public DiscountPolicyServiceImpl(DiscountPolicyRepository discountPolicyRepository) {
        this.discountPolicyRepository = discountPolicyRepository;
    }

    @Override
    public List<DiscountPolicy> getAllPolicies() {
        return discountPolicyRepository.findAllByOrderByDisplayOrderAscPolicyNameAsc();
    }

    @Override
    public List<DiscountPolicy> getActivePolicies() {
        return discountPolicyRepository.findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc();
    }

    @Override
    public Optional<DiscountPolicy> getPolicyById(Long id) {
        return discountPolicyRepository.findById(id);
    }

    @Override
    public Optional<DiscountPolicy> getActivePolicyByCode(String policyCode) {
        if (policyCode == null || policyCode.isBlank()) {
            return Optional.empty();
        }

        return discountPolicyRepository.findByPolicyCodeIgnoreCaseAndActiveTrue(policyCode.trim());
    }

    @Override
    public Optional<DiscountPolicy> findMatchingActivePolicy(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("Passenger date of birth is required");
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            throw new IllegalArgumentException("Passenger date of birth cannot be in the future");
        }
        return findMatchingActivePolicyByAge(Period.between(dateOfBirth, today).getYears());
    }

    @Override
    public Optional<DiscountPolicy> findMatchingActivePolicyByAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Passenger age cannot be negative");
        }
        return getActivePolicies().stream()
                .filter(policy -> matchesAge(policy, age))
                .findFirst();
    }

    @Override
    public String resolvePassengerType(LocalDate dateOfBirth) {
        return findMatchingActivePolicy(dateOfBirth)
                .map(DiscountPolicy::getPolicyCode)
                .orElse("DEFAULT");
    }

    @Override
    public DiscountPolicy savePolicy(DiscountPolicy policy) {
        normalize(policy);
        validate(policy);

        if (policy.getPolicyId() == null) {
            if (discountPolicyRepository.existsByPolicyCodeIgnoreCase(policy.getPolicyCode())) {
                throw new IllegalStateException("Policy code already exists.");
            }
        } else {
            DiscountPolicy oldPolicy = discountPolicyRepository.findById(policy.getPolicyId())
                    .orElseThrow(() -> new IllegalArgumentException("Discount policy does not exist."));

            boolean changedCode = !oldPolicy.getPolicyCode().equalsIgnoreCase(policy.getPolicyCode());

            if (changedCode && discountPolicyRepository.existsByPolicyCodeIgnoreCase(policy.getPolicyCode())) {
                throw new IllegalStateException("Policy code already exists.");
            }
        }

        return discountPolicyRepository.save(policy);
    }

    @Override
    public void deletePolicy(Long id) {
        if (!discountPolicyRepository.existsById(id)) {
            throw new IllegalArgumentException("Discount policy does not exist.");
        }

        discountPolicyRepository.deleteById(id);
    }

    @Override
    public boolean matchesAge(DiscountPolicy policy, int age) {
        if (policy == null || !Boolean.TRUE.equals(policy.getActive())) {
            return false;
        }

        if (policy.getMinAge() != null && age < policy.getMinAge()) {
            return false;
        }

        return policy.getMaxAge() == null || age <= policy.getMaxAge();
    }

    private void normalize(DiscountPolicy policy) {
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

    private void validate(DiscountPolicy policy) {
        if (policy.getPolicyCode() == null || policy.getPolicyCode().isBlank()) {
            throw new IllegalArgumentException("Policy code is required.");
        }

        if (policy.getPolicyName() == null || policy.getPolicyName().isBlank()) {
            throw new IllegalArgumentException("Policy name is required.");
        }

        if (policy.getMinAge() == null || policy.getMinAge() < 0) {
            throw new IllegalArgumentException("Minimum age must be greater than or equal to 0.");
        }

        if (policy.getMaxAge() != null && policy.getMaxAge() < policy.getMinAge()) {
            throw new IllegalArgumentException("Maximum age must be greater than or equal to minimum age.");
        }

        if (policy.getDiscountPercent().compareTo(BigDecimal.ZERO) < 0
                || policy.getDiscountPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Discount percent must be from 0 to 100.");
        }
    }
}
