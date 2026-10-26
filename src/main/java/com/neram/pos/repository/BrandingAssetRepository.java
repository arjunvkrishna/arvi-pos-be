package com.neram.pos.repository;

import com.neram.pos.entity.BrandingAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandingAssetRepository extends JpaRepository<BrandingAsset, Long> {

    Optional<BrandingAsset> findByKindAndOutletId(BrandingAsset.Kind kind, Long outletId);

    Optional<BrandingAsset> findByKindAndOutletIdIsNull(BrandingAsset.Kind kind);

    /** Which branches have their own override, without loading the image bytes. */
    @Query("SELECT a.kind, a.outletId FROM BrandingAsset a")
    List<Object[]> findKindsAndOutlets();
}
