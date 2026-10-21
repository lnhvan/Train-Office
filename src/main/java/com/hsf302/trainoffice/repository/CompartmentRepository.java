package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Compartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompartmentRepository extends JpaRepository<Compartment, Long> {

    Optional<Compartment> findByCoach_CoachIdAndCompartmentNumber(Long coachId, String compartmentNumber);

    List<Compartment> findByCoach_CoachIdOrderByCompartmentNumberAsc(Long coachId);

    boolean existsByCoach_CoachId(Long coachId);
}
