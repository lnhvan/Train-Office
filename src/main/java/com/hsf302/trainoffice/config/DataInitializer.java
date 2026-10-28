package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.*;
import com.hsf302.trainoffice.repository.*;
import com.hsf302.trainoffice.service.impl.TripScheduleBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    /**
     * Tốc độ trung bình (km/h) của từng tàu, đã gồm thời gian dừng ở các ga,
     * dùng để ước lượng thời gian chạy của chuyến mẫu: thời gian = quãng đường / tốc độ.
     * Tham chiếu: tàu SE Thống Nhất chạy Hà Nội - Sài Gòn (1.726 km) khoảng 30-34 giờ (~52-57 km/h).
     */
    private static final Map<String, Integer> AVERAGE_SPEED_KMH = Map.ofEntries(
            Map.entry("SE1", 56),   // tàu nhanh Bắc - Nam
            Map.entry("SE2", 56),
            Map.entry("SE3", 52),   // tàu đêm, dừng nhiều ga hơn
            Map.entry("SE4", 52),
            Map.entry("SE5", 46),   // tàu thường
            Map.entry("SE6", 46),
            Map.entry("RJ01", 62),  // tàu cao cấp, ít điểm dừng
            Map.entry("RJ02", 62),
            Map.entry("RJ03", 58),
            Map.entry("LP01", 38),  // tuyến núi, dốc và nhiều đường cong
            Map.entry("HP01", 48),
            Map.entry("QN01", 48),
            Map.entry("CT01", 52),
            Map.entry("CT02", 46),
            Map.entry("NT01", 52),
            Map.entry("DH01", 40)   // qua đèo Hải Vân, chạy chậm
    );

    private static final int DEFAULT_SPEED_KMH = 50;

    /**
     * Tốc độ tối đa của tuyến (km/h): tuyến núi, đèo chạy chậm dù tàu nhanh.
     * Tốc độ dùng để tính = min(tốc độ tàu, tốc độ tối đa của tuyến).
     */
    private static final Map<String, Integer> ROUTE_MAX_SPEED_KMH = Map.of(
            "HN-NW", 38,             // Tây Bắc: dốc, nhiều đường cong
            "DN-HUE-QT", 42,         // đèo Hải Vân
            "DN-HCM-HIGHLAND", 45    // Tây Nguyên: đèo dốc
    );

    private final UserRepository userRepository;
    private final ProvinceRepository provinceRepository;
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
    private final TripScheduleBuilder tripScheduleBuilder;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           ProvinceRepository provinceRepository,
                           AdminWalletRepository adminWalletRepository,
                           StationRepository stationRepository,
                           RouteRepository routeRepository,
                           RouteStationRepository routeStationRepository,
                           TrainRepository trainRepository,
                           CoachRepository coachRepository,
                           SeatRepository seatRepository,
                           CompartmentRepository compartmentRepository,
                           TrainTripRepository trainTripRepository,
                           TripStationRepository tripStationRepository,
                           TripScheduleBuilder tripScheduleBuilder,
                           BookingRepository bookingRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.provinceRepository = provinceRepository;
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
        this.tripScheduleBuilder = tripScheduleBuilder;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
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
        System.out.println("Provinces: 34, each with one seed station");
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
                .password(passwordEncoder.encode("123456"))
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

    /**
     * Tạo 34 tỉnh/thành (bảng provinces) và mỗi tỉnh một ga mẫu cùng mã.
     * Ga do admin tạo trước khi có bảng provinces được gắn tỉnh theo tên thành phố đã nhập.
     */
    private Map<String, Station> seedAllVietnamStations() {
        Map<String, Province> provinces = new LinkedHashMap<>();
        Map<String, Station> stations = new LinkedHashMap<>();

        stationData().forEach(data -> {
            Province province = findOrCreateProvince(data.code(), data.name(), data.region());
            provinces.put(data.code(), province);
            stations.put(data.code(), findOrCreateStation(data.code(), data.name(), province));
        });

        linkStationsWithoutProvince(provinces.values());

        return stations;
    }

    private Province findOrCreateProvince(String code, String name, Region region) {
        Province province = provinceRepository.findByProvinceCode(code)
                .orElseGet(() -> Province.builder().provinceCode(code).build());

        province.setProvinceName(name);
        province.setRegion(region);

        return provinceRepository.save(province);
    }

    /** Gắn tỉnh cho các ga chưa có province_id, so tên thành phố / tên ga với tên tỉnh (bỏ dấu, bỏ "Thành phố", "Tỉnh"). */
    private void linkStationsWithoutProvince(java.util.Collection<Province> provinces) {
        Map<String, Province> byKey = new HashMap<>();
        for (Province province : provinces) {
            byKey.put(placeKey(province.getProvinceName()), province);
        }
        Province hcm = byKey.get(placeKey("Thành phố Hồ Chí Minh"));
        if (hcm != null) {
            byKey.put("hcm", hcm);
            byKey.put("saigon", hcm);
        }
        Province hue = byKey.get(placeKey("Huế"));
        if (hue != null) {
            byKey.put("thuathienhue", hue);
        }

        int linked = 0;
        int unmatched = 0;
        for (Station station : stationRepository.findAll()) {
            if (station.getProvince() != null) {
                continue;
            }
            Province match = byKey.get(placeKey(station.getCity()));
            if (match == null) {
                match = byKey.get(placeKey(station.getStationName()));
            }
            if (match == null) {
                unmatched++;
                continue;
            }
            station.setProvince(match);
            stationRepository.save(station);
            linked++;
        }

        if (linked > 0 || unmatched > 0) {
            System.out.println("Seed: linked " + linked + " station(s) to provinces, "
                    + unmatched + " left without province (choose one in Admin > Stations).");
        }
    }

    /** "Thành phố Hồ Chí Minh", "TP.HCM", "Tỉnh Huế" -> "hochiminh", "hcm", "hue". */
    private static String placeKey(String value) {
        if (value == null) {
            return "";
        }
        String key = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
        key = key.replaceAll("^(thanh pho|tp\\.?|tinh)\\s*", "");
        return key.replaceAll("[^a-z0-9]", "");
    }

    private List<StationSeed> stationData() {
        return List.of(
                new StationSeed("HAN", "Hà Nội", Region.NORTH),
                new StationSeed("HCM", "Thành phố Hồ Chí Minh", Region.SOUTH),
                new StationSeed("HPH", "Hải Phòng", Region.NORTH),
                new StationSeed("DNG", "Đà Nẵng", Region.CENTRAL),
                new StationSeed("HUE", "Huế", Region.CENTRAL),
                new StationSeed("CTO", "Cần Thơ", Region.SOUTH),
                new StationSeed("LCH", "Lai Châu", Region.NORTH),
                new StationSeed("DBN", "Điện Biên", Region.NORTH),
                new StationSeed("SLA", "Sơn La", Region.NORTH),
                new StationSeed("LSN", "Lạng Sơn", Region.NORTH),
                new StationSeed("QNH", "Quảng Ninh", Region.NORTH),
                new StationSeed("THA", "Thanh Hóa", Region.CENTRAL),
                new StationSeed("NAN", "Nghệ An", Region.CENTRAL),
                new StationSeed("HTI", "Hà Tĩnh", Region.CENTRAL),
                new StationSeed("CBA", "Cao Bằng", Region.NORTH),
                new StationSeed("LCA", "Lào Cai", Region.NORTH),
                new StationSeed("TQG", "Tuyên Quang", Region.NORTH),
                new StationSeed("TNG", "Thái Nguyên", Region.NORTH),
                new StationSeed("PTO", "Phú Thọ", Region.NORTH),
                new StationSeed("BNH", "Bắc Ninh", Region.NORTH),
                new StationSeed("HYN", "Hưng Yên", Region.NORTH),
                new StationSeed("NBI", "Ninh Bình", Region.NORTH),
                new StationSeed("QTR", "Quảng Trị", Region.CENTRAL),
                new StationSeed("QNG", "Quảng Ngãi", Region.CENTRAL),
                new StationSeed("GLA", "Gia Lai", Region.CENTRAL),
                new StationSeed("KHA", "Khánh Hòa", Region.CENTRAL),
                new StationSeed("LDO", "Lâm Đồng", Region.CENTRAL),
                new StationSeed("DLA", "Đắk Lắk", Region.CENTRAL),
                new StationSeed("DNI", "Đồng Nai", Region.SOUTH),
                new StationSeed("TNI", "Tây Ninh", Region.SOUTH),
                new StationSeed("DTH", "Đồng Tháp", Region.SOUTH),
                new StationSeed("VLO", "Vĩnh Long", Region.SOUTH),
                new StationSeed("AGI", "An Giang", Region.SOUTH),
                new StationSeed("CMA", "Cà Mau", Region.SOUTH)
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
        // Lý trình (km tính từ ga đầu) ước lượng theo đường sắt / quốc lộ thực tế giữa các tỉnh lỵ.
        // Tổng chiều dài tuyến = km của ga cuối. Tham chiếu đường sắt Thống Nhất (km từ ga Hà Nội):
        // Ninh Bình 115, Thanh Hóa 175, Vinh 319, Đông Hà 622, Huế 688, Đà Nẵng 791, Quảng Ngãi 928,
        // Nha Trang 1315, Bình Thuận 1551, Biên Hòa 1697, Sài Gòn 1726. Tuyến mẫu vòng qua Bắc Ninh,
        // Hưng Yên nên dài hơn khoảng 30 km.
        List<StopSeed> northSouthStops = List.of(
                stop("HAN", 0), stop("BNH", 31), stop("HYN", 76), stop("NBI", 142),
                stop("THA", 202), stop("NAN", 346), stop("HTI", 392), stop("QTR", 649),
                stop("HUE", 715), stop("DNG", 818), stop("QNG", 955), stop("KHA", 1342),
                stop("LDO", 1578), stop("DNI", 1724), stop("HCM", 1753)
        );
        Route northSouth = findOrCreateRoute("HN-SG", "Ha Noi - Sai Gon", routeLength(northSouthStops));
        List<Station> northSouthStations = stationList(stations, northSouthStops);
        syncRouteStations(northSouth, northSouthStations, northSouthStops);

        // Hà Nội - Bắc Ninh 31 km, Bắc Ninh - Hải Phòng ~94 km, Hải Phòng - Hạ Long ~71 km.
        List<StopSeed> coastalNorthStops = List.of(
                stop("HAN", 0), stop("BNH", 31), stop("HPH", 125), stop("QNH", 196)
        );
        Route haNoiHaiPhongQuangNinh = findOrCreateRoute("HN-HP-QN", "Ha Noi - Hai Phong - Quang Ninh", routeLength(coastalNorthStops));
        List<Station> coastalNorthStations = stationList(stations, coastalNorthStops);
        syncRouteStations(haNoiHaiPhongQuangNinh, coastalNorthStations, coastalNorthStops);

        // Việt Trì 73 km, Tuyên Quang, Lào Cai (~300 km theo đường sắt Hà Nội - Lào Cai), rồi qua Lai Châu, Điện Biên, Sơn La.
        List<StopSeed> mountainNorthStops = List.of(
                stop("HAN", 0), stop("PTO", 73), stop("TQG", 155), stop("LCA", 332),
                stop("LCH", 472), stop("DBN", 662), stop("SLA", 812)
        );
        Route mountainNorth = findOrCreateRoute("HN-NW", "Ha Noi - Northwest Highlands", routeLength(mountainNorthStops));
        List<Station> mountainNorthStations = stationList(stations, mountainNorthStops);
        syncRouteStations(mountainNorth, mountainNorthStations, mountainNorthStops);

        // Đà Nẵng - Huế 103 km (qua đèo Hải Vân), Huế - Đông Hà 66 km.
        List<StopSeed> centralHeritageStops = List.of(
                stop("DNG", 0), stop("HUE", 103), stop("QTR", 169)
        );
        Route centralHeritage = findOrCreateRoute("DN-HUE-QT", "Da Nang - Hue - Quang Tri", routeLength(centralHeritageStops));
        List<Station> centralHeritageStations = stationList(stations, centralHeritageStops);
        syncRouteStations(centralHeritage, centralHeritageStations, centralHeritageStops);

        // Quảng Ngãi, lên Pleiku qua Kon Tum, Buôn Ma Thuột, Đà Lạt, xuống Biên Hòa và TP.HCM.
        List<StopSeed> centralHighlandStops = List.of(
                stop("DNG", 0), stop("QNG", 137), stop("GLA", 387), stop("DLA", 567),
                stop("LDO", 767), stop("DNI", 1057), stop("HCM", 1086)
        );
        Route centralHighland = findOrCreateRoute("DN-HCM-HIGHLAND", "Da Nang - Central Highlands - Ho Chi Minh City", routeLength(centralHighlandStops));
        List<Station> centralHighlandStations = stationList(stations, centralHighlandStops);
        syncRouteStations(centralHighland, centralHighlandStations, centralHighlandStops);

        // Tân An 48 km, Mỹ Tho 72 km, Vĩnh Long, Cần Thơ 172 km, Long Xuyên, rồi qua Rạch Giá xuống Cà Mau.
        List<StopSeed> mekongStops = List.of(
                stop("HCM", 0), stop("TNI", 48), stop("DTH", 72), stop("VLO", 137),
                stop("CTO", 172), stop("AGI", 234), stop("CMA", 429)
        );
        Route mekong = findOrCreateRoute("HCM-MEKONG", "Ho Chi Minh City - Mekong Delta", routeLength(mekongStops));
        List<Station> mekongStations = stationList(stations, mekongStops);
        syncRouteStations(mekong, mekongStations, mekongStops);

        // Đúng lý trình đường sắt: Sài Gòn - Biên Hòa 29 km, Bình Thuận (Mương Mán) 175 km, Nha Trang 411 km.
        List<StopSeed> southCoastStops = List.of(
                stop("HCM", 0), stop("DNI", 29), stop("LDO", 175), stop("KHA", 411)
        );
        Route southCoast = findOrCreateRoute("HCM-NT", "Ho Chi Minh City - Nha Trang", routeLength(southCoastStops));
        List<Station> southCoastStations = stationList(stations, southCoastStops);
        syncRouteStations(southCoast, southCoastStations, southCoastStops);

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
                new BigDecimal("420000")
        );
    }

    private void createTripsForRoute(Route route,
                                     List<Station> stations,
                                     List<Train> trains,
                                     LocalDateTime firstDeparture,
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

                // Thời gian chạy ước lượng theo chiều dài tuyến và tốc độ trung bình của tàu.
                LocalDateTime arrival = departure.plus(travelTime(route, train));

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

    /**
     * Thời gian chạy = chiều dài tuyến / tốc độ, làm tròn đến 5 phút.
     * Tốc độ = tốc độ trung bình của tàu, nhưng không vượt tốc độ tối đa của tuyến.
     */
    private Duration travelTime(Route route, Train train) {
        int speed = Math.min(
                AVERAGE_SPEED_KMH.getOrDefault(train.getTrainCode(), DEFAULT_SPEED_KMH),
                ROUTE_MAX_SPEED_KMH.getOrDefault(route.getRouteCode(), Integer.MAX_VALUE));
        Double distanceKm = route.getDistanceKm();
        double hours = (distanceKm == null ? 0.0 : distanceKm) / speed;
        long minutes = Math.round(hours * 60 / 5.0) * 5;
        return Duration.ofMinutes(Math.max(30, minutes));
    }

    /** Chiều dài tuyến = lý trình của ga cuối. */
    private Double routeLength(List<StopSeed> stops) {
        return stops.isEmpty() ? 0.0 : stops.get(stops.size() - 1).km();
    }

    private Station findOrCreateStation(String stationCode,
                                        String stationName,
                                        Province province) {
        Station station = stationRepository.findByStationCode(stationCode)
                .orElseGet(() -> Station.builder()
                        .stationCode(stationCode)
                        .build());

        station.setStationCode(stationCode);
        station.setStationName(stationName);
        station.setProvince(province);
        station.setCity(province.getProvinceName());

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
                                   List<Station> stations,
                                   List<StopSeed> stops) {
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
            // Cùng ga, cùng thứ tự: chỉ cập nhật lý trình (km) theo dữ liệu mẫu.
            applySeedDistances(existing, stops);
            return;
        }

        if (!existing.isEmpty()) {
            // Tuyến đã được admin chỉnh và đang có chuyến dùng -> giữ nguyên các ga, chỉ cập nhật km của ga trùng mã.
            if (!tripStationRepository.findByRouteStation_Route_RouteId(route.getRouteId()).isEmpty()) {
                applySeedDistances(existing, stops);
                System.out.println("Seed: route " + route.getRouteCode() + " is in use, keeping its current stations.");
                return;
            }

            routeStationRepository.deleteAll(existing);
            routeStationRepository.flush();
        }

        for (int i = 0; i < stations.size(); i++) {
            RouteStation routeStation = RouteStation.builder()
                    .route(route)
                    .station(stations.get(i))
                    .stationOrder(i + 1)
                    .distanceFromStartKm(stops.get(i).km())
                    .build();

            routeStationRepository.save(routeStation);
        }
    }

    /** Ghi km từ dữ liệu mẫu vào các ga đã có của tuyến (ghép theo mã ga). */
    private void applySeedDistances(List<RouteStation> routeStations, List<StopSeed> stops) {
        Map<String, Double> kmByCode = new HashMap<>();
        for (StopSeed stop : stops) {
            kmByCode.put(stop.code(), stop.km());
        }

        for (RouteStation routeStation : routeStations) {
            if (routeStation.getStation() == null) {
                continue;
            }
            Double km = kmByCode.get(routeStation.getStation().getStationCode());
            if (km != null && !km.equals(routeStation.getDistanceFromStartKm())) {
                routeStation.setDistanceFromStartKm(km);
                routeStationRepository.save(routeStation);
            }
        }
    }

    private void syncTripStations(TrainTrip trip,
                                  List<Station> stations) {
        Map<Long, RouteStation> routeStationByStation = new HashMap<>();
        for (RouteStation routeStation : routeStationRepository
                .findByRoute_RouteIdOrderByStationOrderAsc(trip.getRoute().getRouteId())) {
            routeStationByStation.put(routeStation.getStation().getStationId(), routeStation);
        }

        // Các ga của tuyến mà chuyến mẫu dừng, theo thứ tự trên tuyến.
        List<RouteStation> desired = stations.stream()
                .map(station -> routeStationByStation.get(station.getStationId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(RouteStation::getStationOrder))
                .toList();

        if (desired.size() < 2) {
            return;
        }

        List<TripStation> existing = tripStationRepository.findStopsOfTrip(trip.getTripId());

        boolean sameStops = existing.size() == desired.size();
        for (int i = 0; sameStops && i < desired.size(); i++) {
            sameStops = existing.get(i).getRouteStation().getRouteStationId()
                    .equals(desired.get(i).getRouteStationId());
        }

        if (!sameStops) {
            // Không đụng vào lịch dừng của chuyến đã có người đặt.
            if (bookingRepository.existsByTrainTrip_TripId(trip.getTripId())) {
                return;
            }

            if (!existing.isEmpty()) {
                tripStationRepository.deleteAll(existing);
                tripStationRepository.flush();
            }
        }

        for (int i = 0; i < desired.size(); i++) {
            TripStation stop = sameStops ? existing.get(i) : new TripStation();

            stop.setTrainTrip(trip);
            stop.setRouteStation(desired.get(i));
            tripScheduleBuilder.applyInterpolatedTimes(
                    stop, trip.getDepartureTime(), trip.getArrivalTime(), desired, i);

            tripStationRepository.save(stop);
        }
    }


    private List<Station> stationList(Map<String, Station> stations,
                                      List<StopSeed> stops) {
        return stops.stream()
                .map(stop -> {
                    Station station = stations.get(stop.code());

                    if (station == null) {
                        throw new IllegalStateException("Missing station code: " + stop.code());
                    }

                    return station;
                })
                .toList();
    }

    private static StopSeed stop(String code, double km) {
        return new StopSeed(code, km);
    }

    /** Một ga trên tuyến mẫu và lý trình của nó (km tính từ ga đầu tuyến). */
    private record StopSeed(String code, double km) {
    }

    private record StationSeed(String code, String name, Region region) {
    }

    private record TrainSeed(String code, String name, String type) {
    }
}
