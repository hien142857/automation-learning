package vn.startupbench

import org.junit.Assert.*
import org.junit.Test

class StartReadinessTest {
    private fun state(binder: Boolean = true, permission: Boolean = true, service: Boolean = true,
                      running: Boolean = false, pkg: String = "com.example", modes: Boolean = true,
                      error: String = "") = StartReadiness.evaluate(binder, permission, service, running, pkg, modes, error)

    @Test fun permissionWithoutUserServiceDoesNotEnableStart() {
        val value = state(service = false)
        assertFalse(value.enabled)
        assertTrue(value.message.contains("Đang kết nối dịch vụ đo"))
    }
    @Test fun serviceConnectionEnablesStartOncePrerequisitesAreReady() {
        assertFalse(state(service = false).enabled)
        assertTrue(state(service = true).enabled)
    }
    @Test fun missingBinderAndPermissionHaveDifferentRecoveryInstructions() {
        assertFalse(state(binder = false).enabled)
        assertTrue(state(binder = false).message.contains("Chưa chạy Shizuku"))
        assertFalse(state(permission = false).enabled)
        assertTrue(state(permission = false).message.contains("Chưa cấp quyền"))
    }
    @Test fun timeoutCannotBeDisplayedAsReady() {
        val value = state(error = "Kết nối quá hạn 15 giây.")
        assertFalse(value.enabled); assertTrue(value.message.contains("quá hạn")); assertTrue(value.message.contains("thử lại"))
    }
    @Test fun missingPackageAndModesPreventStarting() {
        assertFalse(state(pkg = "  ").enabled)
        assertFalse(state(modes = false).enabled)
    }
    @Test fun duplicateStartIsDisabledEvenWithLiveService() {
        assertFalse(state(running = true).enabled)
    }
}
