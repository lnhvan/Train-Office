package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Compartment;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.repository.CoachRepository;
import com.hsf302.trainoffice.repository.CompartmentRepository;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class CoachServiceImpl implements CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachServiceImpl.class);

    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final CompartmentRepository compartmentRepository;
    private static final int COACHES_PER_PAGE = 8;
    private final TicketRepository ticketRepository;

    public CoachServiceImpl(CoachRepository coachRepository,
                            SeatRepository seatRepository,
                            CompartmentRepository compartmentRepository,
                            TicketRepository ticketRepository) {
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.compartmentRepository = compartmentRepository;
        this.ticketRepository = ticketRepository;
    }

    @Override
    public List<Coach> getAllCoaches() {
        return coachRepository.findAll();
    }

    @Override
    public Optional<Coach> getCoachById(Long id) {
        return coachRepository.findById(id);
    }

    @Override
    @Transactional
    public Coach saveCoach(Coach coach) {
        validateCoach(coach);
        normalizeCoach(coach);

        getAvailableCoachTypes().stream()
                .filter(existingType -> existingType.equalsIgnoreCase(coach.getCoachType()))
                .findFirst()
                .ifPresent(coach::setCoachType);

        Long trainId = coach.getTrain().getTrainId();
        String coachNumber = coach.getCoachNumber();

        if (coach.getCoachId() == null) {
            if (coachRepository.existsByTrain_TrainIdAndCoachNumber(trainId, coachNumber)) {
                throw new IllegalStateException("Coach number already exists in this train.");
            }
        } else {
            Optional<Coach> existingOpt = coachRepository.findById(coach.getCoachId());

            if (existingOpt.isPresent()) {
                Coach existing = existingOpt.get();

                boolean changedTrain = !existing.getTrain().getTrainId().equals(trainId);
                boolean changedNumber = !existing.getCoachNumber().equalsIgnoreCase(coachNumber);

                if ((changedTrain || changedNumber)
                        && coachRepository.existsByTrain_TrainIdAndCoachNumber(trainId, coachNumber)) {
                    throw new IllegalStateException("Coach number already exists in this train.");
                }
            }
        }

        Coach savedCoach = coachRepository.save(coach);

        if (isSleeperCoach(savedCoach)) {
            syncSleeperCompartmentsAndBerths(savedCoach);
        } else {
            syncNormalSeats(savedCoach);
        }

        return savedCoach;
    }

    @Override
    public List<String> getAvailableCoachTypes() {
        TreeMap<String, String> canonicalTypes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> storedTypes = coachRepository.findDistinctCoachTypes();
        if (storedTypes == null) {
            return List.of();
        }

        storedTypes.stream()
                .filter(type -> type != null && !type.isBlank())
                .map(String::trim)
                .forEach(type -> canonicalTypes.putIfAbsent(type, type));

        return canonicalTypes.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCoach(Long id) {
        Coach coach = coachRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coach not found with ID: " + id));

        long seatCount = seatRepository.countByCoach_CoachId(id);
        if (seatCount > 0) {
            throw new ResourceInUseException(
                    "Cannot delete this coach because it still contains " + seatCount + " seats."
            );
        }

        if (compartmentRepository.existsByCoach_CoachId(id)) {
            throw new ResourceInUseException(
                    "Cannot delete this coach because it still contains compartments."
            );
        }

        try {
            coachRepository.delete(coach);
            coachRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Coach {} could not be deleted because a dependent record was added or still exists.", id, ex);
            throw new ResourceInUseException(
                    "Cannot delete this coach because it is still in use.",
                    ex
            );
        }
    }

    private void validateCoach(Coach coach) {
        if (coach.getTrain() == null || coach.getTrain().getTrainId() == null) {
            throw new IllegalStateException("Train is required for coach.");
        }

        if (coach.getCoachNumber() == null || coach.getCoachNumber().isBlank()) {
            throw new IllegalStateException("Coach number is required.");
        }

        if (coach.getCoachType() == null || coach.getCoachType().isBlank()) {
            throw new IllegalStateException("Coach type is required.");
        }

        if (isSleeperCoach(coach)) {
            if (coach.getCompartmentCount() == null || coach.getCompartmentCount() <= 0) {
                throw new IllegalStateException("Sleeper coach must have compartment count.");
            }

            if (coach.getBerthsPerCompartment() == null) {
                throw new IllegalStateException("Sleeper coach must have berths per compartment.");
            }

            if (!List.of(2, 4, 6).contains(coach.getBerthsPerCompartment())) {
                throw new IllegalStateException("Berths per compartment must be 2, 4, or 6.");
            }

            coach.setCapacity(coach.getCompartmentCount() * coach.getBerthsPerCompartment());
            return;

        }

        if (coach.getCapacity() == null || coach.getCapacity() <= 0) {
            throw new IllegalStateException("Coach capacity must be greater than 0.");
        }
    }

    private void normalizeCoach(Coach coach) {
        coach.setCoachNumber(normalize(coach.getCoachNumber()));
        coach.setCoachType(coach.getCoachType().trim());

        boolean sleeperCoach = Boolean.TRUE.equals(coach.getSleeperCoach());
        coach.setSleeperCoach(sleeperCoach);

        if (!sleeperCoach) {
            coach.setCompartmentCount(null);
            coach.setBerthsPerCompartment(null);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private boolean isSleeperCoach(Coach coach) {
        return coach != null && Boolean.TRUE.equals(coach.getSleeperCoach());
    }


    private void syncNormalSeats(Coach coach) {
        int capacity = coach.getCapacity() == null ? 0 : coach.getCapacity();

        if (capacity <= 0) {
            return;
        }

        String seatType = coach.getCoachType();
        BigDecimal extraPrice = resolveExtraPrice(seatType);

        Set<String> desiredSeatNumbers = new HashSet<>();

        for (int index = 1; index <= capacity; index++) {
            desiredSeatNumbers.add(generateNormalSeatNumber(index));
        }

        List<Seat> existingSeats = seatRepository.findByCoach_CoachIdOrderBySeatNumberAsc(coach.getCoachId());

        for (Seat seat : existingSeats) {
            boolean desired = desiredSeatNumbers.contains(seat.getSeatNumber());
            boolean hasTicket = seat.getSeatId() != null && ticketRepository.existsBySeat_SeatId(seat.getSeatId());

            if (!desired && !hasTicket) {
                seatRepository.delete(seat);
                continue;
            }

            if (!desired) {
                seat.setActive(false);
                seatRepository.save(seat);
                continue;
            }

            seat.setCoach(coach);
            seat.setCompartment(null);
            seat.setBerthLevel(null);
            seat.setSeatType(seatType);
            seat.setExtraPrice(extraPrice);
            seat.setActive(true);

            seatRepository.save(seat);
        }

        seatRepository.flush();
        deleteEmptyCompartments(coach);

        for (int index = 1; index <= capacity; index++) {
            String seatNumber = generateNormalSeatNumber(index);

            boolean exists = seatRepository.existsByCoach_CoachIdAndSeatNumber(
                    coach.getCoachId(),
                    seatNumber
            );

            if (exists) {
                continue;
            }

            Seat seat = Seat.builder()
                    .coach(coach)
                    .compartment(null)
                    .seatNumber(seatNumber)
                    .seatType(seatType)
                    .berthLevel(null)
                    .extraPrice(extraPrice)
                    .active(true)
                    .build();
            seatRepository.save(seat);
        }
    }

    private void syncSleeperCompartmentsAndBerths(Coach coach) {
        int compartmentCount = coach.getCompartmentCount();
        int berthsPerCompartment = coach.getBerthsPerCompartment();

        BigDecimal extraPrice = resolveExtraPrice(coach.getCoachType());

        Set<String> desiredSeatNumbers = new HashSet<>();

        for (int compartmentIndex = 1; compartmentIndex <= compartmentCount; compartmentIndex++) {
            String compartmentNumber = generateCompartmentNumber(compartmentIndex);

            for (int berthIndex = 1; berthIndex <= berthsPerCompartment; berthIndex++) {
                desiredSeatNumbers.add(generateSleeperSeatNumber(compartmentNumber, berthIndex));
            }
        }

        List<Seat> existingSeats = seatRepository.findByCoach_CoachIdOrderBySeatNumberAsc(coach.getCoachId());

        for (Seat seat : existingSeats) {
            boolean desired = desiredSeatNumbers.contains(seat.getSeatNumber());
            boolean hasTicket = seat.getSeatId() != null && ticketRepository.existsBySeat_SeatId(seat.getSeatId());

            if (!desired && !hasTicket) {
                seatRepository.delete(seat);
                continue;
            }

            if (!desired) {
                seat.setActive(false);
                seatRepository.save(seat);
            }
        }

        seatRepository.flush();

        for (int compartmentIndex = 1; compartmentIndex <= compartmentCount; compartmentIndex++) {
            String compartmentNumber = generateCompartmentNumber(compartmentIndex);

            Compartment compartment = compartmentRepository
                    .findByCoach_CoachIdAndCompartmentNumber(coach.getCoachId(), compartmentNumber)
                    .orElseGet(() -> compartmentRepository.save(
                            Compartment.builder()
                                    .coach(coach)
                                    .compartmentNumber(compartmentNumber)
                                    .compartmentType(coach.getCoachType())
                                    .capacity(berthsPerCompartment)
                                    .build()
                    ));

            compartment.setCoach(coach);
            compartment.setCompartmentType(coach.getCoachType());
            compartment.setCapacity(berthsPerCompartment);
            compartmentRepository.save(compartment);

            for (int berthIndex = 1; berthIndex <= berthsPerCompartment; berthIndex++) {
                String seatNumber = generateSleeperSeatNumber(compartmentNumber, berthIndex);
                String berthLevel = resolveBerthLevel(berthIndex);

                Seat seat = seatRepository.findByCoach_CoachIdOrderBySeatNumberAsc(coach.getCoachId())
                        .stream()
                        .filter(item -> item.getSeatNumber().equals(seatNumber))
                        .findFirst()
                        .orElse(null);

                if (seat == null) {
                    seat = Seat.builder()
                            .coach(coach)
                            .seatNumber(seatNumber)
                            .build();
                }

                seat.setCoach(coach);
                seat.setCompartment(compartment);
                seat.setSeatNumber(seatNumber);
                seat.setSeatType(coach.getCoachType());
                seat.setBerthLevel(berthLevel);
                seat.setExtraPrice(extraPrice);
                seat.setActive(true);

                seatRepository.save(seat);
            }
        }

        seatRepository.flush();
        deleteUnusedCompartments(coach, compartmentCount);
    }

    private void deleteUnusedCompartments(Coach coach, int compartmentCount) {
        List<Compartment> compartments = compartmentRepository
                .findByCoach_CoachIdOrderByCompartmentNumberAsc(coach.getCoachId());

        for (Compartment compartment : compartments) {
            int compartmentIndex = parseCompartmentIndex(compartment.getCompartmentNumber());

            boolean shouldDelete = compartmentIndex > compartmentCount;
            boolean empty = seatRepository.countByCompartment_CompartmentId(compartment.getCompartmentId()) == 0;

            if (shouldDelete && empty) {
                compartmentRepository.delete(compartment);
            }
        }
    }

    private void deleteEmptyCompartments(Coach coach) {
        List<Compartment> compartments = compartmentRepository
                .findByCoach_CoachIdOrderByCompartmentNumberAsc(coach.getCoachId());

        for (Compartment compartment : compartments) {
            boolean empty = seatRepository.countByCompartment_CompartmentId(compartment.getCompartmentId()) == 0;

            if (empty) {
                compartmentRepository.delete(compartment);
            }
        }
    }

    private int parseCompartmentIndex(String compartmentNumber) {
        if (compartmentNumber == null) {
            return 0;
        }

        try {
            return Integer.parseInt(compartmentNumber.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    private void generateNormalSeats(Coach coach) {
        int capacity = coach.getCapacity() == null ? 0 : coach.getCapacity();

        if (capacity <= 0) {
            return;
        }

        String seatType = coach.getCoachType();
        BigDecimal extraPrice = resolveExtraPrice(seatType);

        for (int index = 1; index <= capacity; index++) {
            String seatNumber = generateNormalSeatNumber(index);

            boolean exists = seatRepository.existsByCoach_CoachIdAndSeatNumber(
                    coach.getCoachId(),
                    seatNumber
            );

            if (exists) {
                continue;
            }

            Seat seat = Seat.builder()
                    .coach(coach)
                    .compartment(null)
                    .seatNumber(seatNumber)
                    .seatType(seatType)
                    .berthLevel(null)
                    .extraPrice(extraPrice)
                    .build();

            seatRepository.save(seat);
        }
    }

    @Override
    public Page<Coach> listAll(int pageNumber, String keyword, Long trainId) {
        int safePageNumber = Math.max(pageNumber, 1);

        Pageable pageable = PageRequest.of(
                safePageNumber - 1,
                COACHES_PER_PAGE,
                Sort.by("coachNumber").ascending()
        );

        return coachRepository.searchCoaches(normalizeKeyword(keyword), trainId, pageable);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private void generateSleeperCompartmentsAndBerths(Coach coach) {
        int compartmentCount = coach.getCompartmentCount();
        int berthsPerCompartment = coach.getBerthsPerCompartment();

        BigDecimal extraPrice = resolveExtraPrice(coach.getCoachType());

        for (int compartmentIndex = 1; compartmentIndex <= compartmentCount; compartmentIndex++) {
            String compartmentNumber = generateCompartmentNumber(compartmentIndex);

            Compartment compartment = compartmentRepository
                    .findByCoach_CoachIdAndCompartmentNumber(coach.getCoachId(), compartmentNumber)
                    .orElseGet(() -> compartmentRepository.save(
                            Compartment.builder()
                                    .coach(coach)
                                    .compartmentNumber(compartmentNumber)
                                    .compartmentType(coach.getCoachType())
                                    .capacity(berthsPerCompartment)
                                    .build()
                    ));

            compartment.setCompartmentType(coach.getCoachType());
            compartment.setCapacity(berthsPerCompartment);
            compartmentRepository.save(compartment);

            for (int berthIndex = 1; berthIndex <= berthsPerCompartment; berthIndex++) {
                String seatNumber = generateSleeperSeatNumber(compartmentNumber, berthIndex);
                String berthLevel = resolveBerthLevel(berthIndex);

                boolean exists = seatRepository.existsByCoach_CoachIdAndSeatNumber(
                        coach.getCoachId(),
                        seatNumber
                );

                if (exists) {
                    continue;
                }

                Seat seat = Seat.builder()
                        .coach(coach)
                        .compartment(compartment)
                        .seatNumber(seatNumber)
                        .seatType(coach.getCoachType())
                        .berthLevel(berthLevel)
                        .extraPrice(extraPrice)
                        .build();

                seatRepository.save(seat);
            }
        }
    }

    private String generateNormalSeatNumber(int index) {
        String[] columns = {"A", "B", "C", "D"};

        int row = ((index - 1) / columns.length) + 1;
        String column = columns[(index - 1) % columns.length];

        return String.format("%02d%s", row, column);
    }

    private String generateCompartmentNumber(int index) {
        return String.format("K%02d", index);
    }

    private String generateSleeperSeatNumber(String compartmentNumber, int berthIndex) {
        String suffix = switch (berthIndex) {
            case 1 -> "L1A";
            case 2 -> "L1B";
            case 3 -> "L2A";
            case 4 -> "L2B";
            case 5 -> "L3A";
            case 6 -> "L3B";
            default -> "B" + berthIndex;
        };

        return compartmentNumber + "-" + suffix;
    }

    private String resolveBerthLevel(int berthIndex) {
        if (berthIndex == 1 || berthIndex == 2) {
            return "LOWER";
        }

        if (berthIndex == 3 || berthIndex == 4) {
            return "MIDDLE";
        }

        if (berthIndex == 5 || berthIndex == 6) {
            return "UPPER";
        }

        return "LEVEL_" + berthIndex;
    }

    private BigDecimal resolveExtraPrice(String coachType) {
        if (coachType == null) {
            return BigDecimal.ZERO;
        }

        String type = coachType.toLowerCase();

        if (type.contains("vip")) {
            return new BigDecimal("180000");
        }

        if (type.contains("first")) {
            return new BigDecimal("90000");
        }

        if (type.contains("soft sleeper") || type.contains("sleeper") || type.contains("giường") || type.contains("giuong")) {
            return new BigDecimal("65000");
        }

        if (type.contains("soft seat")) {
            return new BigDecimal("30000");
        }

        return BigDecimal.ZERO;
    }
}
