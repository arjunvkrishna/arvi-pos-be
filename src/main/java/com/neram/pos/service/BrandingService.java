package com.neram.pos.service;

import com.neram.pos.access.OutletContext;
import com.neram.pos.entity.BrandingAsset;
import com.neram.pos.entity.BrandingAsset.Kind;
import com.neram.pos.repository.BrandingAssetRepository;
import com.neram.pos.repository.OutletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The cafe's own logo and payment QR. Each cafe uploads its own (nothing is shared between cafes); a branch may
 * override the cafe-wide image with its own, e.g. a different UPI account per branch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandingService {

    static final int MAX_BYTES = 1_500_000;
    static final int MAX_DIMENSION = 4000;

    /** The image to use, with the version stamp used to invalidate printer raster caches. */
    public record Image(byte[] data, String contentType, LocalDateTime updatedAt, Long outletId) {
    }

    private final BrandingAssetRepository repository;
    private final OutletRepository outletRepository;

    /** The branch's own image if it has one, otherwise the cafe-wide one. {@code outletId} null = cafe-wide only. */
    @Transactional(readOnly = true)
    public Optional<Image> effective(Kind kind, Long outletId) {
        if (outletId != null && outletId != OutletContext.NO_OUTLET) {
            Optional<BrandingAsset> own = repository.findByKindAndOutletId(kind, outletId);
            if (own.isPresent()) {
                return own.map(BrandingService::toImage);
            }
        }
        return repository.findByKindAndOutletIdIsNull(kind).map(BrandingService::toImage);
    }

    /** Same, for the branch currently in context (whole-cafe view falls back to the cafe-wide image). */
    public Optional<Image> effectiveForContext(Kind kind) {
        return effective(kind, OutletContext.current().orElse(null));
    }

    @Transactional
    public Image save(Kind kind, Long outletId, byte[] data, String declaredType, String actor) {
        String type = validate(kind, data, declaredType);
        if (outletId != null && !outletRepository.existsById(outletId)) {
            throw new NoSuchElementException("Branch not found with id: " + outletId);
        }
        BrandingAsset asset = (outletId == null
                ? repository.findByKindAndOutletIdIsNull(kind)
                : repository.findByKindAndOutletId(kind, outletId))
                .orElseGet(() -> BrandingAsset.builder().kind(kind).outletId(outletId).build());
        asset.setData(data);
        asset.setContentType(type);
        asset.setUpdatedBy(actor);
        asset.setUpdatedAt(LocalDateTime.now());
        return toImage(repository.save(asset));
    }

    @Transactional
    public boolean delete(Kind kind, Long outletId) {
        Optional<BrandingAsset> asset = outletId == null
                ? repository.findByKindAndOutletIdIsNull(kind)
                : repository.findByKindAndOutletId(kind, outletId);
        asset.ifPresent(repository::delete);
        return asset.isPresent();
    }

    /** What exists, without the bytes: {"LOGO": [null, 2], "PAYMENT_QR": [null]} where null = cafe-wide. */
    @Transactional(readOnly = true)
    public Map<String, List<Long>> overview() {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        for (Kind k : Kind.values()) {
            out.put(k.name(), new ArrayList<>());
        }
        for (Object[] row : repository.findKindsAndOutlets()) {
            out.get(((Kind) row[0]).name()).add((Long) row[1]);
        }
        return out;
    }

    /** Accepts only real PNG/JPEG pictures within sane limits; a payment QR must also be readable as a QR code. */
    static String validate(Kind kind, byte[] data, String declaredType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("No image was uploaded");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("The image is too large (max " + (MAX_BYTES / 1000) + " KB)");
        }
        String type = sniff(data);
        if (type == null) {
            throw new IllegalArgumentException("Only PNG or JPEG images are accepted");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException e) {
            img = null;
        }
        if (img == null) {
            throw new IllegalArgumentException("The file is not a readable image");
        }
        if (img.getWidth() > MAX_DIMENSION || img.getHeight() > MAX_DIMENSION) {
            throw new IllegalArgumentException("The image is too big (max " + MAX_DIMENSION + " x " + MAX_DIMENSION + " pixels)");
        }
        if (kind == Kind.PAYMENT_QR && PaymentQrRaster.build(img, 384) == null) {
            throw new IllegalArgumentException("No QR code could be found in this picture. Upload a clear picture of your payment QR.");
        }
        return type;
    }

    /** The picture's real type from its first bytes (the client's declared type and file name are not trusted). */
    static String sniff(byte[] d) {
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private static Image toImage(BrandingAsset a) {
        return new Image(a.getData(), a.getContentType(), a.getUpdatedAt(), a.getOutletId());
    }
}
