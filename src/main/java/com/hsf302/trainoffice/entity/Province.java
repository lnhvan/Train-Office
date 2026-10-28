package com.hsf302.trainoffice.entity;

import com.hsf302.trainoffice.common.enums.Region;
import jakarta.persistence.*;
import lombok.*;

/**
 * Tỉnh / thành phố (34 đơn vị hành chính cấp tỉnh sau sáp nhập 2025).
 * Mỗi ga thuộc đúng một tỉnh/thành (stations.province_id).
 */
@Entity
@Table(
        name = "provinces",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provinces_code", columnNames = "province_code"),
                @UniqueConstraint(name = "uk_provinces_name", columnNames = "province_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Province {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "province_id")
    @EqualsAndHashCode.Include
    private Long provinceId;

    @Column(name = "province_code", nullable = false, length = 10)
    private String provinceCode;

    @Column(name = "province_name", nullable = false, length = 100, columnDefinition = "NVARCHAR(100)")
    private String provinceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 20)
    private Region region;
}
