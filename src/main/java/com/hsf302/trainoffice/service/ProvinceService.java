package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.entity.Province;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProvinceService {

    /** Mọi tỉnh/thành, sắp theo vùng (Bắc -> Trung -> Nam) rồi theo tên. */
    List<Province> getAllProvinces();

    /** Tỉnh/thành gom theo vùng, giữ thứ tự Bắc -> Trung -> Nam (dùng cho dropdown có nhóm). */
    Map<Region, List<Province>> getProvincesByRegion();

    Optional<Province> findById(Long provinceId);
}
