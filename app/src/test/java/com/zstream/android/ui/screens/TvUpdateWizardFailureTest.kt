package com.zstream.android.ui.screens

import com.zstream.android.R
import com.zstream.android.data.adb.AdbFailureKind
import com.zstream.android.data.adb.AdbOperationException
import org.junit.Assert.assertEquals
import org.junit.Test

class TvUpdateWizardFailureTest {
    @Test
    fun mapsTechnicalFailuresToLocalizedResourceKinds() {
        assertEquals(
            R.string.transport_error_install,
            tvUpdateInstallErrorResource(
                AdbOperationException(AdbFailureKind.INSTALL, "internal package-manager output"),
            ),
        )
        assertEquals(
            R.string.transport_operation_failed,
            tvUpdateInstallErrorResource(IllegalStateException("internal protocol detail")),
        )
        assertEquals(
            R.string.tv_update_invalid_repository,
            tvUpdateScanErrorResource(IllegalArgumentException("raw parser detail")),
        )
    }
}
