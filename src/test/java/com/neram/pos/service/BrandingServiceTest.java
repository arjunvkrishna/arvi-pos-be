package com.neram.pos.service;

import com.neram.pos.entity.BrandingAsset;
import com.neram.pos.entity.BrandingAsset.Kind;
import com.neram.pos.repository.BrandingAssetRepository;
import com.neram.pos.repository.OutletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrandingServiceTest {

    private BrandingAssetRepository repository;
    private OutletRepository outlets;
    private BrandingService service;

    @BeforeEach
    void setUp() {
        repository = mock(BrandingAssetRepository.class);
        outlets = mock(OutletRepository.class);
        service = new BrandingService(repository, outlets);
        when(repository.save(any(BrandingAsset.class))).thenAnswer(i -> i.getArgument(0));
        when(outlets.existsById(2L)).thenReturn(true);
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    @Test
    void acceptsRealPngAndJpegLogos() throws Exception {
        assertEquals("image/png", BrandingService.validate(Kind.LOGO, png(50, 50), "whatever"));
        assertEquals("image/jpeg", BrandingService.validate(Kind.LOGO, jpeg(), "image/png"));
    }

    @Test
    void theRealTypeIsDetectedNotTheOneTheClientClaims() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.LOGO, svg, "image/png"));
        byte[] html = "<html></html>".getBytes();
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.LOGO, html, "image/jpeg"));
    }

    @Test
    void emptyOversizedAndCorruptFilesAreRefused() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.LOGO, new byte[0], "image/png"));
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.LOGO, null, "image/png"));
        byte[] big = new byte[BrandingService.MAX_BYTES + 1];
        System.arraycopy(png(10, 10), 0, big, 0, 8);
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.LOGO, big, "image/png"));
        byte[] corrupt = png(10, 10);
        for (int i = 40; i < corrupt.length; i++) corrupt[i] = 0;
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.LOGO, corrupt, "image/png"));
    }

    @Test
    void absurdDimensionsAreRefusedBeforeTheyCanExhaustMemoryWhenPrinting() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> BrandingService.validate(Kind.LOGO, png(BrandingService.MAX_DIMENSION + 1, 10), "image/png"));
    }

    @Test
    void aPaymentQrMustActuallyContainAReadableQrCode() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> BrandingService.validate(Kind.PAYMENT_QR, png(200, 200), "image/png"));
        assertEquals("image/png", BrandingService.validate(Kind.PAYMENT_QR, QrFixture.png(), "image/png"));
    }

    @Test
    void aBranchUsesItsOwnImageAndFallsBackToTheCafeWideOne() {
        BrandingAsset cafe = BrandingAsset.builder().kind(Kind.PAYMENT_QR).data(new byte[]{1}).contentType("image/png").build();
        BrandingAsset branch = BrandingAsset.builder().kind(Kind.PAYMENT_QR).outletId(2L).data(new byte[]{2}).contentType("image/png").build();
        when(repository.findByKindAndOutletIdIsNull(Kind.PAYMENT_QR)).thenReturn(Optional.of(cafe));
        when(repository.findByKindAndOutletId(Kind.PAYMENT_QR, 2L)).thenReturn(Optional.of(branch));
        when(repository.findByKindAndOutletId(Kind.PAYMENT_QR, 3L)).thenReturn(Optional.empty());

        assertEquals(2, service.effective(Kind.PAYMENT_QR, 2L).orElseThrow().data()[0]);
        assertEquals(1, service.effective(Kind.PAYMENT_QR, 3L).orElseThrow().data()[0]);
        assertEquals(1, service.effective(Kind.PAYMENT_QR, null).orElseThrow().data()[0]);
        assertEquals(1, service.effective(Kind.PAYMENT_QR, -1L).orElseThrow().data()[0], "no-branch placeholder gets the cafe image");
    }

    @Test
    void nothingIsReturnedWhenTheCafeHasUploadedNothing() {
        when(repository.findByKindAndOutletIdIsNull(any())).thenReturn(Optional.empty());
        assertTrue(service.effective(Kind.LOGO, null).isEmpty());
        assertTrue(service.effective(Kind.PAYMENT_QR, 2L).isEmpty());
    }

    @Test
    void savingReplacesTheExistingImageOfTheSameScope() throws Exception {
        BrandingAsset existing = BrandingAsset.builder().id(5L).kind(Kind.LOGO).data(new byte[]{9}).contentType("image/png").build();
        when(repository.findByKindAndOutletIdIsNull(Kind.LOGO)).thenReturn(Optional.of(existing));

        service.save(Kind.LOGO, null, png(30, 30), "image/png", "owner");

        verify(repository).save(existing);
        assertEquals("owner", existing.getUpdatedBy());
        assertTrue(existing.getData().length > 1);
    }

    @Test
    void savingForAnUnknownBranchFails() throws Exception {
        assertThrows(NoSuchElementException.class, () -> service.save(Kind.LOGO, 77L, png(30, 30), "image/png", "x"));
    }
}
