package com.neram.pos.config;

import com.neram.pos.tenancy.PlatformAdminService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataInitializerTest {

    @Test
    void startupEnsuresAPlatformAdminExistsAndNothingElse() {
        PlatformAdminService platformAdminService = mock(PlatformAdminService.class);

        new DataInitializer(platformAdminService).run();

        verify(platformAdminService).bootstrapIfMissing();
    }
}
