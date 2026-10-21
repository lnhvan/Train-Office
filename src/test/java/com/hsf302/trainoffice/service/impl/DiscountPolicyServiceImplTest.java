package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.repository.DiscountPolicyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscountPolicyServiceImplTest {

    private final DiscountPolicyRepository repository = mock(DiscountPolicyRepository.class);
    private final DiscountPolicyServiceImpl service = new DiscountPolicyServiceImpl(repository);

    @Test
    void resolvesFirstMatchingActivePolicyInRepositoryOrder() {
        DiscountPolicy child = policy("CHILD", "Child", 0, 15, 25, true);
        DiscountPolicy backup = policy("YOUTH", "Youth", 10, 20, 10, true);
        when(repository.findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc())
                .thenReturn(List.of(child, backup));

        assertEquals("CHILD", service.resolvePassengerType(LocalDate.now().minusYears(12)));
    }

    @Test
    void inactiveOrMissingMatchFallsBackToDefault() {
        DiscountPolicy inactive = policy("CHILD", "Child", 0, 15, 25, false);
        DiscountPolicy senior = policy("SENIOR", "Senior", 60, null, 30, true);
        when(repository.findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc())
                .thenReturn(List.of(senior));

        assertFalse(service.matchesAge(inactive, 10));
        assertEquals("DEFAULT", service.resolvePassengerType(LocalDate.now().minusYears(30)));

        when(repository.findByActiveTrueOrderByDisplayOrderAscPolicyNameAsc()).thenReturn(List.of());
        assertEquals("DEFAULT", service.resolvePassengerType(LocalDate.now().minusYears(10)));
    }

    @Test
    void rejectsFutureDateOfBirthAndNegativeAge() {
        assertEquals("Passenger date of birth cannot be in the future",
                assertThrows(IllegalArgumentException.class,
                        () -> service.resolvePassengerType(LocalDate.now().plusDays(1))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.findMatchingActivePolicyByAge(-1));
    }

    private DiscountPolicy policy(String code, String name, int min, Integer max, int discount, boolean active) {
        return DiscountPolicy.builder().policyCode(code).policyName(name).minAge(min).maxAge(max)
                .discountPercent(BigDecimal.valueOf(discount)).active(active).displayOrder(1).build();
    }
}
