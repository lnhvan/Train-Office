package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.*;
import com.hsf302.trainoffice.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AdminWalletRepository adminWalletRepository;
    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final RouteStationRepository routeStationRepository;
    private final TrainRepository trainRepository;
    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final CompartmentRepository compartmentRepository;
    private final TrainTripRepository trainTripRepository;
    private final TripStationRepository tripStationRepository;

    public DataInitializer(UserRepository userRepository,
                           AdminWalletRepository adminWalletRepository,
                           StationRepository stationRepository,
                           RouteRepository routeRepository,
                           RouteStationRepository routeStationRepository,
                           TrainRepository trainRepository,
                           CoachRepository coachRepository,
                           SeatRepository seatRepository,
                           CompartmentRepository compartmentRepository,
                           TrainTripRepository trainTripRepository,
                           TripStationRepository tripStationRepository) {
        this.userRepository = userRepository;
        this.adminWalletRepository = adminWalletRepository;
        this.stationRepository = stationRepository;
        this.routeRepository = routeRepository;
        this.routeStationRepository = routeStationRepository;
        this.trainRepository = trainRepository;
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.compartmentRepository = compartmentRepository;
        this.trainTripRepository = trainTripRepository;
        this.tripStationRepository = tripStationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createAdminAccount();
        createAdminWallet();

        Map<String, Station> stations = seedAllVietnamStations();
        Map<String, Train> trains = seedTrainsWithCoachesCompartmentsAndSeats();

        seedRoutesAndTrips(stations, trains);

        System.out.println("======================================");
        System.out.println("Train Office seed data initialized.");
        System.out.println("Admin email: admin@railjet.com");
        System.out.println("Admin password: 123456");
        System.out.println("Stations: 34 provinces/cities");
        System.out.println("Trains, coaches, compartments, seats, routes, trips initialized.");
        System.out.println("======================================");
    }

    private void createAdminAccount() {
        String adminEmail = "admin@railjet.com";

        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .password("123456")
                .fullName("RailJet Admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(admin);
    }

    private void createAdminWallet() {
        adminWalletRepository.findFirstByOrderByWalletIdAsc()
                .orElseGet(() -> adminWalletRepository.save(
                        AdminWallet.builder()
                                .balance(BigDecimal.ZERO)
                                .build()
                ));
    }

    private Map<String, Station> seedAllVietnamStations() {
        Map<String, Station> stations = new LinkedHashMap<>();

        stationData().forEach(data -> stations.put(
                data.code(),
                findOrCreateStation(
                        data.code(),
                        data.name(),
                        data.name()
                )
        ));

        return stations;
    }

    private List<StationSeed> stationData() {
        return List.of(
                new StationSeed("HAN", "Hà Nội"),
                new StationSeed("HCM", "Thành phố Hồ Chí Minh"),
                new StationSeed("HPH", "Hải Phòng"),
                new StationSeed("DNG", "Đà Nẵng"),
                new StationSeed("HUE", "Huế"),
                new StationSeed("CTO", "Cần Thơ"),

                new StationSeed("LCH", "Lai Châu"),
                new StationSeed("DBN", "Điện Biên"),
                new StationSeed("SLA", "Sơn La"),
                new StationSeed("LSN", "Lạng Sơn"),
                new StationSeed("QNH", "Quảng Ninh"),
                new StationSeed("THA", "Thanh Hóa"),
                new StationSeed("NAN", "Nghệ An"),
                new StationSeed("HTI", "Hà Tĩnh"),
                new StationSeed("CBA", "Cao Bằng"),
                new StationSeed("LCA", "Lào Cai"),
                new StationSeed("TQG", "Tuyên Quang"),
                new StationSeed("TNG", "Thái Nguyên"),
                new StationSeed("PTO", "Phú Thọ"),
                new StationSeed("BNH", "Bắc Ninh"),
                new StationSeed("HYN", "Hưng Yên"),
                new StationSeed("NBI", "Ninh Bình"),
                new StationSeed("QTR", "Quảng Trị"),
                new StationSeed("QNG", "Quảng Ngãi"),
                new StationSeed("GLA", "Gia Lai"),
                new StationSeed("KHA", "Khánh Hòa"),
                new StationSeed("LDO", "Lâm Đồng"),
                new StationSeed("DLA", "Đắk Lắk"),
                new StationSeed("DNI", "Đồng Nai"),
                new StationSeed("TNI", "Tây Ninh"),
                new StationSeed("DTH", "Đồng Tháp"),
                new StationSeed("VLO", "Vĩnh Long"),
                new StationSeed("AGI", "An Giang"),
                new StationSeed("CMA", "Cà Mau")
        );
    }

    private Map<String, Train> seedTrainsWithCoachesCompartmentsAndSeats() {
        Map<String, Train> trains = new LinkedHashMap<>();

        List<TrainSeed> trainSeeds = List.of(
                new TrainSeed("SE1", "SE1 Express", "North South Express"),
                new TrainSeed("SE2", "SE2 Express", "North South Express"),
                new TrainSeed("SE3", "SE3 Night Express", "Sleeper Express"),
                new TrainSeed("SE4", "SE4 Night Express", "Sleeper Express"),
                new TrainSeed("SE5", "SE5 Standard", "Standard Train"),
                new TrainSeed("SE6", "SE6 Standard", "Standard Train"),

                new TrainSeed("RJ01", "RailJet Premium 01", "Premium Express"),
                new TrainSeed("RJ02", "RailJet Premium 02", "Premium Express"),
                new TrainSeed("RJ03", "RailJet Business 03", "Business Express"),

                new TrainSeed("LP01", "Lao Cai Mountain Express", "Mountain Train"),
                new TrainSeed("HP01", "Hai Phong Coastal Express", "Coastal Train"),
                new TrainSeed("QN01", "Quang Ninh Bay Express", "Coastal Train"),
                new TrainSeed("CT01", "Can Tho Mekong Express", "Mekong Train"),
                new TrainSeed("CT02", "Mekong Night Express", "Mekong Train"),
                new TrainSeed("NT01", "Nha Trang Beach Express", "Tourist Train"),
                new TrainSeed("DH01", "Central Heritage Express", "Heritage Train")
        );

        for (TrainSeed seed : trainSeeds) {
            Train train = findOrCreateTrain(seed.code(), seed.name(), seed.type());
            trains.put(seed.code(), train);

            seedCoachesCompartmentsAndSeatsForTrain(train);
        }

        return trains;
    }

    private void seedCoachesCompartmentsAndSeatsForTrain(Train train) {
        Coach vipSleeper = findOrCreateCoach(
                train,
                "C01",
                "VIP Sleeper",
                16,
                true,
                4,
                4
        );

        Coach firstClass = findOrCreateCoach(
                train,
                "C02",
                "First Class",
                20,
                false,
                null,
                null
        );

        Coach softSleeper = findOrCreateCoach(
                train,
                "C03",
                "Soft Sleeper",
                24,
                true,
                6,
                4
        );

        Coach softSeat = findOrCreateCoach(
                train,
                "C04",
                "Soft Seat",
                40,
                false,
                null,
                null
        );

        Coach standard = findOrCreateCoach(
                train,
                "C05",
                "Standard Class",
                40,
                false,
                null,
                null
        );

        Coach economy = findOrCreateCoach(
                train,
                "C06",
                "Economy Class",
                48,
                false,
                null,
                null
        );

        createSleeperCompartmentsAndBerths(vipSleeper, "VIP Sleeper", new BigDecimal("180000"));
        createNormalSeatsForCoach(firstClass, "First Class", new BigDecimal("90000"));
        createSleeperCompartmentsAndBerths(softSleeper, "Soft Sleeper", new BigDecimal("65000"));
        createNormalSeatsForCoach(softSeat, "Soft Seat", new BigDecimal("30000"));
        createNormalSeatsForCoach(standard, "Standard Class", BigDecimal.ZERO);
        createNormalSeatsForCoach(economy, "Economy Class", BigDecimal.ZERO);
    }

    private void seedRoutesAndTrips(Map<String, Station> stations,
                                    Map<String, Train> trains) {
        Route northSouth = findOrCreateRoute(
                "HN-SG",
                "Ha Noi - Sai Gon",
                1726.0
        );

        List<Station> northSouthStations = stationList(stations,
                "HAN", "BNH", "HYN", "NBI", "THA", "NAN", "HTI",
                "QTR", "HUE", "DNG", "QNG", "KHA", "LDO", "DNI", "HCM"
        );

        syncRouteStations(northSouth, northSouthStations);

        Route haNoiHaiPhongQuangNinh = findOrCreateRoute(
                "HN-HP-QN",
                "Ha Noi - Hai Phong - Quang Ninh",
                180.0
        );

        List<Station> coastalNorthStations = stationList(stations,
                "HAN", "BNH", "HPH", "QNH"
        );

        syncRouteStations(haNoiHaiPhongQuangNinh, coastalNorthStations);

        Route mountainNorth = findOrCreateRoute(
                "HN-NW",
                "Ha Noi - Northwest Highlands",
                620.0
        );

        List<Station> mountainNorthStations = stationList(stations,
                "HAN", "PTO", "TQG", "LCA", "LCH", "DBN", "SLA"
        );

        syncRouteStations(mountainNorth, mountainNorthStations);

        Route centralHeritage = findOrCreateRoute(
                "DN-HUE-QT",
                "Da Nang - Hue - Quang Tri",
                220.0
        );

        List<Station> centralHeritageStations = stationList(stations,
                "DNG", "HUE", "QTR"
        );

        syncRouteStations(centralHeritage, centralHeritageStations);

        Route centralHighland = findOrCreateRoute(
                "DN-HCM-HIGHLAND",
                "Da Nang - Central Highlands - Ho Chi Minh City",
                980.0
        );

        List<Station> centralHighlandStations = stationList(stations,
                "DNG", "QNG", "GLA", "DLA", "LDO", "DNI", "HCM"
        );

        syncRouteStations(centralHighland, centralHighlandStations);

        Route mekong = findOrCreateRoute(
                "HCM-MEKONG",
                "Ho Chi Minh City - Mekong Delta",
                430.0
        );

        List<Station> mekongStations = stationList(stations,
                "HCM", "TNI", "DTH", "VLO", "CTO", "AGI", "CMA"
        );

        syncRouteStations(mekong, mekongStations);

        Route southCoast = findOrCreateRoute(
                "HCM-NT",
                "Ho Chi Minh City - Nha Trang",
                430.0
        );

        List<Station> southCoastStations = stationList(stations,
                "HCM", "DNI", "LDO", "KHA"
        );

        syncRouteStations(southCoast, southCoastStations);

        LocalDateTime baseDate = LocalDateTime.now()
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        createTripsForRoute(
                northSouth,
                northSouthStations,
                List.of(
                        trains.get("SE1"),
                        trains.get("SE2"),
                        trains.get("SE3"),
                        trains.get("SE4"),
                        trains.get("RJ01"),
                        trains.get("RJ02")
                ),
                baseDate.withHour(6),
                32,
                new BigDecimal("870000")
        );

        createTripsForRoute(
                haNoiHaiPhongQuangNinh,
                coastalNorthStations,
                List.of(
                        trains.get("HP01"),
                        trains.get("QN01"),
                        trains.get("RJ03")
                ),
                baseDate.withHour(7),
                4,
                new BigDecimal("220000")
        );

        createTripsForRoute(
                mountainNorth,
                mountainNorthStations,
                List.of(
                        trains.get("LP01"),
                        trains.get("SE5")
                ),
                baseDate.withHour(20),
                12,
                new BigDecimal("520000")
        );

        createTripsForRoute(
                centralHeritage,
                centralHeritageStations,
                List.of(
                        trains.get("DH01"),
                        trains.get("SE6")
                ),
                baseDate.withHour(8),
                3,
                new BigDecimal("180000")
        );

        createTripsForRoute(
                centralHighland,
                centralHighlandStations,
                List.of(
                        trains.get("RJ02"),
                        trains.get("SE5")
                ),
                baseDate.withHour(9),
                18,
                new BigDecimal("650000")
        );

        createTripsForRoute(
                mekong,
                mekongStations,
                List.of(
                        trains.get("CT01"),
                        trains.get("CT02")
                ),
                baseDate.withHour(10),
                8,
                new BigDecimal("360000")
        );

        createTripsForRoute(
                southCoast,
                southCoastStations,
                List.of(
                        trains.get("NT01"),
                        trains.get("RJ03")
                ),
                baseDate.withHour(13),
                7,
                new BigDecimal("420000")
        );
    }

    private void createTripsForRoute(Route route,
                                     List<Station> stations,
                                     List<Train> trains,
                                     LocalDateTime firstDeparture,
                                     int durationHours,
                                     BigDecimal basePrice) {
        int dayOffset = 0;

        for (Train train : trains) {
            if (train == null) {
                continue;
            }

            for (int i = 0; i < 3; i++) {
                LocalDateTime departure = firstDeparture
                        .plusDays(i)
                        .plusHours(dayOffset % 4L);

                LocalDateTime arrival = departure.plusHours(durationHours);

                BigDecimal finalPrice = basePrice.add(
                        BigDecimal.valueOf(dayOffset * 25000L)
                );

                TrainTrip trip = findOrCreateOrUpdateSeedTrip(
                        train,
                        route,
                        i,
                        departure,
                        arrival,
                        finalPrice
                );

                syncTripStations(trip, stations);

                dayOffset++;
            }
        }
    }

    private Station findOrCreateStation(String stationCode,
                                        String stationName,
                                        String city) {
        Station station = stationRepository.findByStationCode(stationCode)
                .orElseGet(() -> Station.builder()
                        .stationCode(stationCode)
                        .build());

        station.setStationCode(stationCode);
        station.setStationName(stationName);
        station.setCity(city);

        return stationRepository.save(station);
    }

    private Route findOrCreateRoute(String routeCode,
                                    String routeName,
                                    Double distanceKm) {
        Route route = routeRepository.findAll()
                .stream()
                .filter(item -> routeCode.equalsIgnoreCase(item.getRouteCode()))
                .findFirst()
                .orElseGet(() -> Route.builder()
                        .routeCode(routeCode)
                        .build());

        route.setRouteCode(routeCode);
        route.setRouteName(routeName);
        route.setDistanceKm(distanceKm);

        return routeRepository.save(route);
    }

    private Train findOrCreateTrain(String trainCode,
                                    String trainName,
                                    String trainType) {
        Train train = trainRepository.findByTrainCode(trainCode)
                .orElseGet(() -> Train.builder()
                        .trainCode(trainCode)
                        .build());

        train.setTrainCode(trainCode);
        train.setTrainName(trainName);
        train.setTrainType(trainType);
        train.setStatus(TrainStatus.AVAILABLE);

        return trainRepository.save(train);
    }

    private Coach findOrCreateCoach(Train train,
                                    String coachNumber,
                                    String coachType,
                                    Integer capacity,
                                    Boolean sleeperCoach,
                                    Integer compartmentCount,
                                    Integer berthsPerCompartment) {
        Coach coach = coachRepository.findAll()
                .stream()
                .filter(item -> item.getTrain() != null
                        && item.getTrain().getTrainId().equals(train.getTrainId())
                        && coachNumber.equalsIgnoreCase(item.getCoachNumber()))
                .findFirst()
                .orElseGet(() -> Coach.builder()
                        .train(train)
                        .coachNumber(coachNumber)
                        .build());

        boolean isSleeper = Boolean.TRUE.equals(sleeperCoach);

        coach.setTrain(train);
        coach.setCoachNumber(coachNumber);
        coach.setCoachType(coachType);
        coach.setSleeperCoach(isSleeper);

        if (isSleeper) {
            coach.setCompartmentCount(compartmentCount);
            coach.setBerthsPerCompartment(berthsPerCompartment);
            coach.setCapacity(compartmentCount * berthsPerCompartment);
        } else {
            coach.setCapacity(capacity);
            coach.setCompartmentCount(null);
            coach.setBerthsPerCompartment(null);
        }

        return coachRepository.save(coach);
    }

    private void createNormalSeatsForCoach(Coach coach,
                                           String seatType,
                                           BigDecimal extraPrice) {
        String[] columns = {"A", "B", "C", "D"};
        int capacity = coach.getCapacity() == null ? 40 : coach.getCapacity();
        int created = 0;
        int row = 1;

        while (created < capacity) {
            for (String column : columns) {
                if (created >= capacity) {
                    break;
                }

                String seatNumber = String.format("%02d%s", row, column);

                boolean exists = seatRepository.existsByCoach_CoachIdAndSeatNumber(
                        coach.getCoachId(),
                        seatNumber
                );

                if (!exists) {
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

                created++;
            }

            row++;
        }
    }

    private void createSleeperCompartmentsAndBerths(Coach coach,
                                                    String seatType,
                                                    BigDecimal extraPrice) {
        int compartmentCount = coach.getCompartmentCount() == null ? 0 : coach.getCompartmentCount();
        int berthsPerCompartment = coach.getBerthsPerCompartment() == null ? 0 : coach.getBerthsPerCompartment();

        if (compartmentCount <= 0 || berthsPerCompartment <= 0) {
            return;
        }

        List<Seat> reusableOldSeats = new ArrayList<>(
                seatRepository.findByCoach_CoachIdOrderBySeatNumberAsc(coach.getCoachId())
                        .stream()
                        .filter(seat -> seat.getCompartment() == null)
                        .toList()
        );

        int oldSeatIndex = 0;

        for (int compartmentIndex = 1; compartmentIndex <= compartmentCount; compartmentIndex++) {
            String compartmentNumber = String.format("K%02d", compartmentIndex);

            Compartment compartment = compartmentRepository
                    .findByCoach_CoachIdAndCompartmentNumber(coach.getCoachId(), compartmentNumber)
                    .orElseGet(() -> compartmentRepository.save(
                            Compartment.builder()
                                    .coach(coach)
                                    .compartmentNumber(compartmentNumber)
                                    .compartmentType(seatType)
                                    .capacity(berthsPerCompartment)
                                    .build()
                    ));

            compartment.setCoach(coach);
            compartment.setCompartmentNumber(compartmentNumber);
            compartment.setCompartmentType(seatType);
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

                Seat seat;

                if (oldSeatIndex < reusableOldSeats.size()) {
                    seat = reusableOldSeats.get(oldSeatIndex);
                    oldSeatIndex++;
                } else {
                    seat = new Seat();
                    seat.setCoach(coach);
                }

                seat.setCoach(coach);
                seat.setCompartment(compartment);
                seat.setSeatNumber(seatNumber);
                seat.setSeatType(seatType);
                seat.setBerthLevel(berthLevel);
                seat.setExtraPrice(extraPrice);

                seatRepository.save(seat);
            }
        }
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
            return "UPPER";
        }

        if (berthIndex == 5 || berthIndex == 6) {
            return "MIDDLE";
        }

        return "LEVEL_" + berthIndex;
    }

    private TrainTrip findOrCreateOrUpdateSeedTrip(Train train,
                                                   Route route,
                                                   int tripIndex,
                                                   LocalDateTime departureTime,
                                                   LocalDateTime arrivalTime,
                                                   BigDecimal basePrice) {
        List<TrainTrip> existingTrips = trainTripRepository.findAll()
                .stream()
                .filter(trip -> trip.getTrain() != null
                        && trip.getTrain().getTrainId().equals(train.getTrainId())
                        && trip.getRoute() != null
                        && trip.getRoute().getRouteId().equals(route.getRouteId()))
                .sorted(Comparator.comparing(
                        TrainTrip::getDepartureTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        if (existingTrips.size() > tripIndex) {
            TrainTrip trip = existingTrips.get(tripIndex);

            trip.setTrain(train);
            trip.setRoute(route);
            trip.setDepartureTime(departureTime);
            trip.setArrivalTime(arrivalTime);
            trip.setBasePrice(basePrice);
            trip.setStatus(TripStatus.SCHEDULED);

            return trainTripRepository.save(trip);
        }

        return trainTripRepository.save(
                TrainTrip.builder()
                        .train(train)
                        .route(route)
                        .departureTime(departureTime)
                        .arrivalTime(arrivalTime)
                        .basePrice(basePrice)
                        .status(TripStatus.SCHEDULED)
                        .build()
        );
    }

    private void syncRouteStations(Route route,
                                   List<Station> stations) {
        List<RouteStation> existing = routeStationRepository
                .findByRoute_RouteIdOrderByStationOrderAsc(route.getRouteId());

        boolean sameOrder = existing.size() == stations.size();

        if (sameOrder) {
            for (int i = 0; i < stations.size(); i++) {
                RouteStation routeStation = existing.get(i);

                if (routeStation.getStation() == null
                        || !routeStation.getStation().getStationId().equals(stations.get(i).getStationId())
                        || routeStation.getStationOrder() == null
                        || routeStation.getStationOrder() != i + 1) {
                    sameOrder = false;
                    break;
                }
            }
        }

        if (sameOrder) {
            return;
        }

        if (!existing.isEmpty()) {
            routeStationRepository.deleteAll(existing);
            routeStationRepository.flush();
        }

        for (int i = 0; i < stations.size(); i++) {
            RouteStation routeStation = RouteStation.builder()
                    .route(route)
                    .station(stations.get(i))
                    .stationOrder(i + 1)
                    .build();

            routeStationRepository.save(routeStation);
        }
    }

    private void syncTripStations(TrainTrip trip,
                                  List<Station> stations) {
        List<TripStation> existing = tripStationRepository
                .findByTrainTrip_TripIdOrderByStationOrderAsc(trip.getTripId());

        if (existing.size() != stations.size()) {
            if (!existing.isEmpty()) {
                tripStationRepository.deleteAll(existing);
                tripStationRepository.flush();
            }

            for (int i = 0; i < stations.size(); i++) {
                TripStation tripStation = new TripStation();

                applyTripStationData(tripStation, trip, stations, i);

                tripStationRepository.save(tripStation);
            }

            return;
        }

        for (int i = 0; i < stations.size(); i++) {
            TripStation tripStation = existing.get(i);

            applyTripStationData(tripStation, trip, stations, i);

            tripStationRepository.save(tripStation);
        }
    }

    private void applyTripStationData(TripStation tripStation,
                                      TrainTrip trip,
                                      List<Station> stations,
                                      int index) {
        long totalMinutes = Math.max(
                60,
                Duration.between(trip.getDepartureTime(), trip.getArrivalTime()).toMinutes()
        );

        int stationCount = stations.size();

        LocalDateTime arrivalTime;
        LocalDateTime departureTime;

        if (index == 0) {
            arrivalTime = null;
            departureTime = trip.getDepartureTime();
        } else if (index == stationCount - 1) {
            arrivalTime = trip.getArrivalTime();
            departureTime = null;
        } else {
            long minutesFromStart = totalMinutes * index / (stationCount - 1);
            arrivalTime = trip.getDepartureTime().plusMinutes(minutesFromStart);
            departureTime = arrivalTime.plusMinutes(10);
        }

        tripStation.setTrainTrip(trip);
        tripStation.setStation(stations.get(index));
        tripStation.setStationOrder(index + 1);
        tripStation.setArrivalTime(arrivalTime);
        tripStation.setDepartureTime(departureTime);
    }

    private List<Station> stationList(Map<String, Station> stations,
                                      String... codes) {
        return Arrays.stream(codes)
                .map(code -> {
                    Station station = stations.get(code);

                    if (station == null) {
                        throw new IllegalStateException("Missing station code: " + code);
                    }

                    return station;
                })
                .toList();
    }

    private record StationSeed(String code, String name) {
    }

    private record TrainSeed(String code, String name, String type) {
    }
}
