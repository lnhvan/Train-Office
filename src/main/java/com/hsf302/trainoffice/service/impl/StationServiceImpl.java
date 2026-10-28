package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.Province;
import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.repository.ProvinceRepository;
import com.hsf302.trainoffice.repository.StationRepository;
import com.hsf302.trainoffice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StationServiceImpl implements StationService {

    public static final int STATIONS_PER_PAGE = 5;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Override
    public List<Station> getAllStations() {
        return stationRepository.findAll();
    }

    @Override
    public Page<Station> listAll(int pageNum, String keyword) {
        return listAll(pageNum, keyword, null, null);
    }

    @Override
    public Page<Station> listAll(int pageNum, String keyword, Region region, Long provinceId) {
        int safePageNumber = Math.max(pageNum, 1);
        Pageable pageable = PageRequest.of(
                safePageNumber - 1,
                STATIONS_PER_PAGE,
                Sort.by("stationId").ascending()
        );

        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return stationRepository.searchStations(cleanKeyword, region, provinceId, pageable);
    }

    @Override
    public Station getStationById(Long id) {
        return stationRepository.findById(id).orElse(null);
    }

    @Override
    public Station createStation(Station station) {
        if (stationRepository.existsByStationCode(
                station.getStationCode())) {
            return null;
        }
        Province province = resolveProvince(station);
        station.setProvince(province);
        station.setCity(province.getProvinceName());
        return stationRepository.save(station);
    }

    @Override
    public Station updateStation(Long id, Station station) {
        Station existing =
                stationRepository.findById(id).orElse(null);

        if (existing != null) {

            if (!existing.getStationCode().equals(station.getStationCode())
                    &&
                    stationRepository.existsByStationCode(
                            station.getStationCode())) {
                return null;
            }

            existing.setStationCode(station.getStationCode());
            existing.setStationName(station.getStationName());
            Province province = resolveProvince(station);
            existing.setProvince(province);
            existing.setCity(province.getProvinceName());

            return stationRepository.save(existing);
        }

        return null;
    }

    /** Form chỉ gửi province.provinceId -> nạp tỉnh thật từ DB; bắt buộc phải chọn. */
    private Province resolveProvince(Station form) {
        Long provinceId = form.getProvince() != null ? form.getProvince().getProvinceId() : null;
        if (provinceId == null) {
            throw new IllegalArgumentException(I18n.t("err.provinceRequired"));
        }
        return provinceRepository.findById(provinceId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.provinceRequired")));
    }

    @Override
    public void deleteStation(Long id) {
        stationRepository.deleteById(id);
    }

    @Override
    public boolean stationExists(String code) {
        return stationRepository.existsByStationCode(code);
    }

    @Override
    public Optional<Station> findById(Long id) {
        return stationRepository.findById(id);
    }
}
