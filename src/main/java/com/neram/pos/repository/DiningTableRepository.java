package com.neram.pos.repository;

import com.neram.pos.entity.DiningTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<DiningTable> findByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where lower(t.name) = lower(:name) and t.active = true")
    Optional<DiningTable> lockActiveByNameIgnoreCase(@Param("name") String name);

    boolean existsByNameIgnoreCaseAndActiveTrue(String name);
}
