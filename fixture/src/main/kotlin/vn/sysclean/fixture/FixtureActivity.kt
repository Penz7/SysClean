package vn.sysclean.fixture

import android.app.Activity
import android.os.Bundle

/**
 * Opened by the test before SysClean, so it sits in RAM as a "previous" app: exactly the
 * kind of background app the RAM manager offers to put into deep sleep.
 */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A small held allocation makes the process visible in the memory map.
        ballast = ByteArray(BALLAST_BYTES) { it.toByte() }
    }

    private companion object {
        const val BALLAST_BYTES = 8 * 1024 * 1024
        @Suppress("unused")
        var ballast: ByteArray? = null
    }
}
