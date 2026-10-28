package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "stations",
        indexes = {
                @Index(name = "idx_stations_code", columnList = "station_code"),
                @Index(name = "idx_stations_city", columnList = "city"),
                @Index(name = "idx_stations_province", columnList = "province_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Station {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    @EqualsAndHashCode.Include
    private Long stationId;

    @Column(name = "station_code", unique = true, nullable = false, length = 30)
    private String stationCode;

    @Column(name = "station_name", nullable = false, length = 120, columnDefinition = "NVARCHAR(120)")
    private String stationName;

    /** Tỉnh / thành phố của ga (bắt buộc với ga tạo mới; ga cũ được gắn tự động khi app khởi động). */
    @ManyToOne
    @JoinColumn(name = "province_id", foreignKey = @ForeignKey(name = "fk_stations_province"))
    @ToString.Exclude
    private Province province;

    /**
     * Tên tỉnh/thành, tự chép từ province mỗi khi lưu.
     * Giữ cột city cũ (NOT NULL) để không phải sửa database và các trang đang hiển thị nó.
     */
    @Column(name = "city", nullable = false, length = 80, columnDefinition = "NVARCHAR(80)")
    private String city;

    @Builder.Default
    @OneToMany(mappedBy = "station", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<RouteStation> routeStations = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void syncCityFromProvince() {
        if (province != null && province.getProvinceName() != null) {
            city = province.getProvinceName();
        }
    }
}
