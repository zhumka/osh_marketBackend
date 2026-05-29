package com.oshmarket.repository;

import com.oshmarket.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findAllByOccupiedFalseAndDeletedFalseOrderByPlaceNumber();

    List<Place> findAllByOccupiedTrueAndDeletedFalseOrderByPlaceNumber();

    Optional<Place> findByIdAndDeletedFalse(Long id);

    boolean existsByPlaceNumberAndDeletedFalse(String placeNumber);

    long countByOccupiedTrueAndDeletedFalse();

    long countByOccupiedFalseAndDeletedFalse();
}
