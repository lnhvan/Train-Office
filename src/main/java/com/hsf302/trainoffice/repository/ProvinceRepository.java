package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {

    Optional<Province> findByProvinceCode(String provinceCode);
}
