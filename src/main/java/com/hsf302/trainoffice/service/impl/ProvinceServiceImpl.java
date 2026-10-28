package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.entity.Province;
import com.hsf302.trainoffice.repository.ProvinceRepository;
import com.hsf302.trainoffice.service.ProvinceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ProvinceServiceImpl implements ProvinceService {

    private final ProvinceRepository provinceRepository;

    public ProvinceServiceImpl(ProvinceRepository provinceRepository) {
        this.provinceRepository = provinceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Province> getAllProvinces() {
        Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
        List<Province> provinces = new ArrayList<>(provinceRepository.findAll());
        provinces.sort(Comparator
                .comparing((Province p) -> p.getRegion() == null ? Integer.MAX_VALUE : p.getRegion().ordinal())
                .thenComparing(Province::getProvinceName, vietnamese));
        return provinces;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Region, List<Province>> getProvincesByRegion() {
        Map<Region, List<Province>> byRegion = new LinkedHashMap<>();
        for (Region region : Region.values()) {
            byRegion.put(region, new ArrayList<>());
        }
        for (Province province : getAllProvinces()) {
            if (province.getRegion() != null) {
                byRegion.get(province.getRegion()).add(province);
            }
        }
        byRegion.values().removeIf(List::isEmpty);
        return byRegion;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Province> findById(Long provinceId) {
        return provinceId == null ? Optional.empty() : provinceRepository.findById(provinceId);
    }
}
