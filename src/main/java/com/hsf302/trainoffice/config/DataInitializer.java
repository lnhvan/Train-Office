package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.common.enums.RefundStatus;
import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.*;
import com.hsf302.trainoffice.repository.*;
import com.hsf302.trainoffice.service.impl.TripScheduleBuilder;
import com.hsf302.trainoffice.dto.TripSchedulePreview;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    /**
     * Tốc độ chạy trung bình (km/h) mặc định của tàu mẫu, lưu vào Train.averageSpeedKmh khi tàu chưa có.
     * Giờ đến từng ga = km ÷ tốc độ, cộng 5 phút dừng ở mỗi ga giữa (xem TripScheduleBuilder).
     * Tham chiếu: tàu SE Thống Nhất chạy Hà Nội - Sài Gòn (1.726 km) khoảng 30-34 giờ.
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
            Map.entry("NT02", 52),
            Map.entry("TN01", 55),  // tàu cao nguyên Tây Nguyên
            Map.entry("DH01", 40)   // qua đèo Hải Vân, chạy chậm
    );

    private static final int DEFAULT_SPEED_KMH = 50;

    private final UserRepository userRepository;
    private final ProvinceRepository provinceRepository;
    private final AdminWalletRepository adminWalletRepository;
    private final RefundRepository refundRepository;
    private final StationRepository stationRepository;
    private final RouteRepository routeRepository;
    private final RouteStationRepository routeStationRepository;
    private final TrainRepository trainRepository;
    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final CompartmentRepository compartmentRepository;
    private final TrainTripRepository trainTripRepository;
    private final TripScheduleBuilder tripScheduleBuilder;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           ProvinceRepository provinceRepository,
                           AdminWalletRepository adminWalletRepository,
                           RefundRepository refundRepository,
                           StationRepository stationRepository,
                           RouteRepository routeRepository,
                           RouteStationRepository routeStationRepository,
                           TrainRepository trainRepository,
                           CoachRepository coachRepository,
                           SeatRepository seatRepository,
                           CompartmentRepository compartmentRepository,
                           TrainTripRepository trainTripRepository,
                           TripScheduleBuilder tripScheduleBuilder,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.provinceRepository = provinceRepository;
        this.adminWalletRepository = adminWalletRepository;
        this.refundRepository = refundRepository;
        this.stationRepository = stationRepository;
        this.routeRepository = routeRepository;
        this.routeStationRepository = routeStationRepository;
        this.trainRepository = trainRepository;
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.compartmentRepository = compartmentRepository;
        this.trainTripRepository = trainTripRepository;
        this.tripScheduleBuilder = tripScheduleBuilder;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createAdminAccount();
        createAdminWallet();

        // Dữ liệu gốc chỉ seed khi bảng của nhóm đó còn trống (lần chạy đầu trên DB mới).
        // Sau đó seed không sửa/xóa/tạo lại tỉnh, ga, tàu, toa, ghế, tuyến: thay đổi của admin được giữ nguyên.
        boolean seededProvinces = false;
        boolean seededTrains = false;
        boolean seededRoutes = false;

        if (provinceRepository.count() == 0) {
            seedAllVietnamStations();
            seededProvinces = true;
        }

        // Đảm bảo các đoàn tàu mẫu và toa/ghế luôn sẵn sàng (idempotent, không ghi đè nếu đã có)
        seedTrainsWithCoachesCompartmentsAndSeats();
        seededTrains = true;

        if (routeRepository.count() == 0) {
            seedRoutes();
            seededRoutes = true;
        }

        // Chỉ điền chỗ còn trống (DB cũ trước khi có các cột này), không ghi đè giá trị admin đã nhập.
        backfillTrainSpeeds();
        backfillCoachSeatPrices();
        migrateLegacyApprovedRefunds();

        // Chuyến demo cho hôm nay, ngày mai, ngày kia theo lịch trình phân bổ chuẩn, không trùng lặp
        seedDemoTrips();

        System.out.println("======================================");
        System.out.println("Train Office seed data checked.");
        System.out.println("Admin email: admin@railjet.com");
        System.out.println("Admin password: 123456");
        System.out.println("Seeded now -> provinces/stations: " + seededProvinces
                + ", trains/coaches/seats: " + seededTrains
                + ", routes: " + seededRoutes);
        System.out.println("Demo trips added for missing days (today + 2 days).");
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
                new TrainSeed("NT02", "Nha Trang Sunset Express", "Tourist Train"),
                new TrainSeed("TN01", "Tay Nguyen Highland Express", "Highland Train"),
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

    /**
     * 7 tuyến mẫu: mã, tên, các ga kèm lý trình (km), tàu chạy chuyến mẫu, giờ chạy đầu ngày, giá đi hết tuyến.
     */
    private List<RouteSeed> routeSeeds() {
        // Mỗi tuyến được phân bổ đội tàu riêng biệt, không dùng chung tàu qua các tuyến địa lý khác nhau
        // để tránh xung đột hành trình và đảm bảo tàu không bị phân thân ở nhiều ga cùng lúc.
        return List.of(
                new RouteSeed("HN-SG", "Ha Noi - Sai Gon",
                        List.of(stop("HAN", 0), stop("BNH", 31), stop("HYN", 76), stop("NBI", 142),
                                stop("THA", 202), stop("NAN", 346), stop("HTI", 392), stop("QTR", 649),
                                stop("HUE", 715), stop("DNG", 818), stop("QNG", 955), stop("KHA", 1342),
                                stop("LDO", 1578), stop("DNI", 1724), stop("HCM", 1753)),
                        List.of("SE1", "SE2", "SE3", "SE4", "RJ01"), 6, new BigDecimal("870000")),

                // Hà Nội - Bắc Ninh 31 km, Bắc Ninh - Hải Phòng ~94 km, Hải Phòng - Hạ Long ~71 km.
                new RouteSeed("HN-HP-QN", "Ha Noi - Hai Phong - Quang Ninh",
                        List.of(stop("HAN", 0), stop("BNH", 31), stop("HPH", 125), stop("QNH", 196)),
                        List.of("HP01", "QN01", "RJ03"), 7, new BigDecimal("220000")),

                // Việt Trì 73 km, Tuyên Quang, Lào Cai (~300 km theo đường sắt Hà Nội - Lào Cai), rồi qua Lai Châu, Điện Biên, Sơn La.
                new RouteSeed("HN-NW", "Ha Noi - Northwest Highlands",
                        List.of(stop("HAN", 0), stop("PTO", 73), stop("TQG", 155), stop("LCA", 332),
                                stop("LCH", 472), stop("DBN", 662), stop("SLA", 812)),
                        List.of("LP01", "SE5"), 20, new BigDecimal("520000")),

                // Đà Nẵng - Huế 103 km (qua đèo Hải Vân), Huế - Đông Hà 66 km.
                new RouteSeed("DN-HUE-QT", "Da Nang - Hue - Quang Tri",
                        List.of(stop("DNG", 0), stop("HUE", 103), stop("QTR", 169)),
                        List.of("DH01", "SE6"), 8, new BigDecimal("180000")),

                // Quảng Ngãi, lên Pleiku qua Kon Tum, Buôn Ma Thuột, Đà Lạt, xuống Biên Hòa và TP.HCM.
                new RouteSeed("DN-HCM-HIGHLAND", "Da Nang - Central Highlands - Ho Chi Minh City",
                        List.of(stop("DNG", 0), stop("QNG", 137), stop("GLA", 387), stop("DLA", 567),
                                stop("LDO", 767), stop("DNI", 1057), stop("HCM", 1086)),
                        List.of("RJ02", "TN01"), 8, new BigDecimal("650000")),

                // Tân An 48 km, Mỹ Tho 72 km, Vĩnh Long, Cần Thơ 172 km, Long Xuyên, rồi qua Rạch Giá xuống Cà Mau.
                new RouteSeed("HCM-MEKONG", "Ho Chi Minh City - Mekong Delta",
                        List.of(stop("HCM", 0), stop("TNI", 48), stop("DTH", 72), stop("VLO", 137),
                                stop("CTO", 172), stop("AGI", 234), stop("CMA", 429)),
                        List.of("CT01", "CT02"), 7, new BigDecimal("360000")),

                // Đúng lý trình đường sắt: Sài Gòn - Biên Hòa 29 km, Bình Thuận (Mương Mán) 175 km, Nha Trang 411 km.
                new RouteSeed("HCM-NT", "Ho Chi Minh City - Nha Trang",
                        List.of(stop("HCM", 0), stop("DNI", 29), stop("LDO", 175), stop("KHA", 411)),
                        List.of("NT01", "NT02"), 8, new BigDecimal("420000"))
        );
    }

    /** Tạo 7 tuyến mẫu và các ga của tuyến. Chỉ gọi khi bảng routes còn trống. */
    private void seedRoutes() {
        for (RouteSeed seed : routeSeeds()) {
            List<Station> stations = new ArrayList<>();

            for (StopSeed stop : seed.stops()) {
                stationRepository.findByStationCode(stop.code()).ifPresent(stations::add);
            }

            if (stations.size() != seed.stops().size()) {
                System.out.println("Seed: skip route " + seed.code() + " because some of its stations are missing.");
                continue;
            }

            Route route = findOrCreateRoute(seed.code(), seed.name(), routeLength(seed.stops()));

            for (int i = 0; i < stations.size(); i++) {
                routeStationRepository.save(RouteStation.builder()
                        .route(route)
                        .station(stations.get(i))
                        .stationOrder(i + 1)
                        .distanceFromStartKm(seed.stops().get(i).km())
                        .build());
            }
        }
    }

    /**
     * Kế hoạch chuyến demo phân bổ theo đoàn tàu và khung giờ thực tế:
     * Tuyến dài (>12h như HN-SG, DN-HCM, HN-NW) xoay tua các đoàn tàu khác nhau mỗi ngày.
     * Tuyến ngắn (<10h) chạy theo khung giờ hàng ngày. Không có bất kỳ đoàn tàu nào bị trùng giờ.
     */
    private List<DemoTripSlot> demoTripSlots() {
        return List.of(
                // Tuyến HN-SG (1753 km, ~30-35h): xoay vòng các tàu khác nhau cho từng ngày, không trùng tàu
                new DemoTripSlot("HN-SG", "SE1", 0, 6, 0, new BigDecimal("870000")),
                new DemoTripSlot("HN-SG", "SE3", 0, 19, 0, new BigDecimal("890000")),
                new DemoTripSlot("HN-SG", "SE2", 1, 6, 0, new BigDecimal("870000")),
                new DemoTripSlot("HN-SG", "SE4", 1, 19, 0, new BigDecimal("890000")),
                new DemoTripSlot("HN-SG", "RJ01", 2, 7, 0, new BigDecimal("950000")),
                new DemoTripSlot("HN-SG", "SE1", 2, 20, 0, new BigDecimal("870000")),

                // Tuyến HN-HP-QN (196 km, ~3.5-4h): các chuyến trong ngày, chạy hàng ngày
                new DemoTripSlot("HN-HP-QN", "HP01", 0, 7, 0, new BigDecimal("220000")),
                new DemoTripSlot("HN-HP-QN", "HP01", 1, 7, 0, new BigDecimal("220000")),
                new DemoTripSlot("HN-HP-QN", "HP01", 2, 7, 0, new BigDecimal("220000")),
                new DemoTripSlot("HN-HP-QN", "QN01", 0, 11, 30, new BigDecimal("240000")),
                new DemoTripSlot("HN-HP-QN", "QN01", 1, 11, 30, new BigDecimal("240000")),
                new DemoTripSlot("HN-HP-QN", "QN01", 2, 11, 30, new BigDecimal("240000")),
                new DemoTripSlot("HN-HP-QN", "RJ03", 0, 16, 0, new BigDecimal("270000")),
                new DemoTripSlot("HN-HP-QN", "RJ03", 1, 16, 0, new BigDecimal("270000")),
                new DemoTripSlot("HN-HP-QN", "RJ03", 2, 16, 0, new BigDecimal("270000")),

                // Tuyến HN-NW (812 km, ~18-22h): luân phiên LP01 và SE5
                new DemoTripSlot("HN-NW", "LP01", 0, 20, 0, new BigDecimal("520000")),
                new DemoTripSlot("HN-NW", "SE5", 1, 20, 0, new BigDecimal("520000")),
                new DemoTripSlot("HN-NW", "LP01", 2, 20, 0, new BigDecimal("520000")),

                // Tuyến DN-HUE-QT (169 km, ~4h): chạy hàng ngày
                new DemoTripSlot("DN-HUE-QT", "DH01", 0, 8, 0, new BigDecimal("180000")),
                new DemoTripSlot("DN-HUE-QT", "DH01", 1, 8, 0, new BigDecimal("180000")),
                new DemoTripSlot("DN-HUE-QT", "DH01", 2, 8, 0, new BigDecimal("180000")),
                new DemoTripSlot("DN-HUE-QT", "SE6", 0, 14, 0, new BigDecimal("190000")),
                new DemoTripSlot("DN-HUE-QT", "SE6", 1, 14, 0, new BigDecimal("190000")),
                new DemoTripSlot("DN-HUE-QT", "SE6", 2, 14, 0, new BigDecimal("190000")),

                // Tuyến DN-HCM-HIGHLAND (1086 km, ~18-20h): luân phiên RJ02 và TN01
                new DemoTripSlot("DN-HCM-HIGHLAND", "RJ02", 0, 8, 0, new BigDecimal("650000")),
                new DemoTripSlot("DN-HCM-HIGHLAND", "TN01", 1, 8, 0, new BigDecimal("620000")),
                new DemoTripSlot("DN-HCM-HIGHLAND", "RJ02", 2, 8, 0, new BigDecimal("650000")),

                // Tuyến HCM-MEKONG (429 km, ~8.7-9.8h): chạy hàng ngày
                new DemoTripSlot("HCM-MEKONG", "CT01", 0, 7, 30, new BigDecimal("360000")),
                new DemoTripSlot("HCM-MEKONG", "CT01", 1, 7, 30, new BigDecimal("360000")),
                new DemoTripSlot("HCM-MEKONG", "CT01", 2, 7, 30, new BigDecimal("360000")),
                new DemoTripSlot("HCM-MEKONG", "CT02", 0, 13, 0, new BigDecimal("370000")),
                new DemoTripSlot("HCM-MEKONG", "CT02", 1, 13, 0, new BigDecimal("370000")),
                new DemoTripSlot("HCM-MEKONG", "CT02", 2, 13, 0, new BigDecimal("370000")),

                // Tuyến HCM-NT (411 km, ~7.2-8.1h): chạy hàng ngày
                new DemoTripSlot("HCM-NT", "NT01", 0, 8, 0, new BigDecimal("420000")),
                new DemoTripSlot("HCM-NT", "NT01", 1, 8, 0, new BigDecimal("420000")),
                new DemoTripSlot("HCM-NT", "NT01", 2, 8, 0, new BigDecimal("420000")),
                new DemoTripSlot("HCM-NT", "NT02", 0, 14, 0, new BigDecimal("430000")),
                new DemoTripSlot("HCM-NT", "NT02", 1, 14, 0, new BigDecimal("430000")),
                new DemoTripSlot("HCM-NT", "NT02", 2, 14, 0, new BigDecimal("430000"))
        );
    }

    /**
     * Chuyến demo: áp dụng danh sách lịch chạy mẫu chuẩn hóa.
     * Tính toán giờ đến ước tính trước khi tạo chuyến, kiểm tra không bị trùng đè lịch của tàu.
     */
    private void seedDemoTrips() {
        LocalDateTime baseDate = LocalDateTime.now()
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        Map<String, Route> routesByCode = new HashMap<>();
        for (Route r : routeRepository.findAll()) {
            routesByCode.put(r.getRouteCode().toUpperCase(Locale.ROOT), r);
        }

        for (DemoTripSlot slot : demoTripSlots()) {
            Route route = routesByCode.get(slot.routeCode().toUpperCase(Locale.ROOT));
            if (route == null || !hasValidDistances(route)) {
                continue;
            }

            Train train = trainRepository.findByTrainCode(slot.trainCode()).orElse(null);
            boolean usable = train != null
                    && train.getStatus() != TrainStatus.MAINTENANCE
                    && train.getAverageSpeedKmh() != null
                    && train.getAverageSpeedKmh() > 0;

            if (!usable) {
                continue;
            }

            LocalDateTime departure = baseDate
                    .plusDays(slot.dayOffset())
                    .withHour(slot.hour())
                    .withMinute(slot.minute());

            // Ước tính giờ đến dựa theo tốc độ tàu và các ga của tuyến
            LocalDateTime estimatedArrival = departure;
            try {
                TripSchedulePreview preview = tripScheduleBuilder.preview(train, route, departure);
                if (preview != null && preview.totalMinutes() > 0) {
                    estimatedArrival = departure.plusMinutes(preview.totalMinutes());
                }
            } catch (Exception ignored) {
            }

            TrainTrip trip = createSeedTripIfMissing(train, route, departure, estimatedArrival, slot.price());
            if (trip != null) {
                tripScheduleBuilder.addMissingStops(trip);
                trainTripRepository.save(trip);
            }
        }
    }

    /** Tuyến có ít nhất 2 ga và km tăng dần: đủ điều kiện tính lịch chạy (tránh lỗi làm hỏng transaction khởi động). */
    private boolean hasValidDistances(Route route) {
        List<RouteStation> routeStations = routeStationRepository
                .findByRoute_RouteIdOrderByStationOrderAsc(route.getRouteId());

        if (routeStations.size() < 2) {
            return false;
        }

        for (int i = 1; i < routeStations.size(); i++) {
            Double previous = routeStations.get(i - 1).getDistanceFromStartKm();
            Double current = routeStations.get(i).getDistanceFromStartKm();

            if (previous == null || current == null || current <= previous) {
                System.out.println("Seed: skip demo trips on route " + route.getRouteCode()
                        + " because station distances are not increasing.");
                return false;
            }
        }

        return true;
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

        // Chỉ điền tốc độ khi tàu chưa có, không ghi đè tốc độ admin đã sửa.
        if (train.getAverageSpeedKmh() == null) {
            train.setAverageSpeedKmh(AVERAGE_SPEED_KMH.getOrDefault(trainCode, DEFAULT_SPEED_KMH));
        }

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

    /**
     * Luồng hoàn tiền cũ: "Đã duyệt" = đã trừ ví ngay. Luồng mới tách thành Đã duyệt (nhả ghế) và
     * Đã chuyển tiền (trừ ví). Yêu cầu duyệt theo luồng cũ (chưa có processed_at) đã bị trừ ví rồi,
     * nên chuyển thẳng sang COMPLETED để không bị trừ ví lần nữa.
     */
    private void migrateLegacyApprovedRefunds() {
        for (Refund refund : refundRepository.findByRefundStatusAndProcessedAtIsNull(RefundStatus.APPROVED)) {
            refund.setProcessedAt(refund.getRefundedAt() != null ? refund.getRefundedAt() : LocalDateTime.now());
            if (refund.getRefundedAt() == null) {
                refund.setRefundedAt(refund.getProcessedAt());
            }
            refund.setRefundStatus(RefundStatus.COMPLETED);
            refundRepository.save(refund);
        }
    }

    /** Tàu mẫu tạo trước khi có cột average_speed_kmh: điền tốc độ mặc định theo mã tàu. Tàu admin tự tạo giữ nguyên. */
    private void backfillTrainSpeeds() {
        for (Train train : trainRepository.findAll()) {
            if (train.getAverageSpeedKmh() != null) {
                continue;
            }

            Integer speed = AVERAGE_SPEED_KMH.get(train.getTrainCode());
            if (speed != null) {
                train.setAverageSpeedKmh(speed);
                trainRepository.save(train);
            }
        }
    }

    /**
     * Toa tạo trước khi có cột seat_price: lấy phụ thu đang áp cho ghế của toa làm giá chung.
     * Chỉ điền toa còn trống, không đổi giá admin đã nhập.
     */
    private void backfillCoachSeatPrices() {
        for (Coach coach : coachRepository.findAll()) {
            if (coach.getSeatPrice() != null) {
                continue;
            }

            BigDecimal priceFromSeats = seatRepository.findByCoach_CoachIdOrderBySeatNumberAsc(coach.getCoachId())
                    .stream()
                    .map(Seat::getExtraPrice)
                    .filter(price -> price != null)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            coach.setSeatPrice(priceFromSeats);
            coachRepository.save(coach);
        }
    }

    /** Giá chung của toa nếu đã có (admin có thể đã sửa), nếu chưa thì lưu mức mặc định của seed. */
    private BigDecimal resolveCoachSeatPrice(Coach coach, BigDecimal defaultPrice) {
        if (coach.getSeatPrice() == null) {
            coach.setSeatPrice(defaultPrice);
            coachRepository.save(coach);
        }

        return coach.getSeatPrice();
    }

    private void createNormalSeatsForCoach(Coach coach,
                                           String seatType,
                                           BigDecimal defaultExtraPrice) {
        BigDecimal extraPrice = resolveCoachSeatPrice(coach, defaultExtraPrice);
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
                                                    BigDecimal defaultExtraPrice) {
        BigDecimal extraPrice = resolveCoachSeatPrice(coach, defaultExtraPrice);
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
                String berthLevel = resolveBerthLevel(berthIndex, berthsPerCompartment);

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

    private String resolveBerthLevel(int berthIndex, int totalBerths) {
        if (berthIndex == 1 || berthIndex == 2) {
            return "LOWER";
        }

        if (totalBerths == 4) {
            return (berthIndex == 3 || berthIndex == 4) ? "UPPER" : "LEVEL_" + berthIndex;
        }

        if (berthIndex == 3 || berthIndex == 4) {
            return "MIDDLE";
        }

        if (berthIndex == 5 || berthIndex == 6) {
            return "UPPER";
        }

        return "LEVEL_" + berthIndex;
    }

    /**
     * Tạo chuyến mẫu nếu ngày khởi hành đó chưa có chuyến nào của cùng tàu + tuyến.
     * Không bao giờ sửa chuyến đã có (kể cả chuyến trong quá khứ, chuyến admin tạo/sửa,
     * chuyến đã có người đặt). Trả về null khi đã có chuyến cho ngày đó.
     */
    private TrainTrip createSeedTripIfMissing(Train train,
                                              Route route,
                                              LocalDateTime departureTime,
                                              LocalDateTime arrivalTime,
                                              BigDecimal basePrice) {
        LocalDateTime startOfDay = departureTime.toLocalDate().atStartOfDay();

        boolean alreadyScheduled = trainTripRepository
                .existsByTrain_TrainIdAndRoute_RouteIdAndDepartureTimeGreaterThanEqualAndDepartureTimeLessThan(
                        train.getTrainId(),
                        route.getRouteId(),
                        startOfDay,
                        startOfDay.plusDays(1)
                );

        if (alreadyScheduled) {
            return null;
        }

        // Kiểm tra xem tàu có đang có chuyến hoạt động nào bị trùng lặp khoảng thời gian không
        List<TrainTrip> activeTrips = trainTripRepository
                .findByTrain_TrainIdAndStatusInOrderByDepartureTimeAsc(
                        train.getTrainId(),
                        List.of(TripStatus.SCHEDULED, TripStatus.ONGOING)
                );

        for (TrainTrip existing : activeTrips) {
            LocalDateTime exDep = existing.getDepartureTime();
            LocalDateTime exArr = existing.getArrivalTime() != null ? existing.getArrivalTime() : existing.getDepartureTime();
            if (departureTime.isBefore(exArr) && arrivalTime.isAfter(exDep)) {
                System.out.println("Seed: skip trip for train " + train.getTrainCode()
                        + " at " + departureTime + " because it overlaps with existing trip #"
                        + existing.getTripId() + " [" + exDep + " -> " + exArr + "]");
                return null;
            }
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

    private record DemoTripSlot(String routeCode,
                                String trainCode,
                                int dayOffset,
                                int hour,
                                int minute,
                                BigDecimal price) {
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

    private record RouteSeed(String code,
                             String name,
                             List<StopSeed> stops,
                             List<String> trainCodes,
                             int firstHour,
                             BigDecimal basePrice) {
    }
}
