package vn.sysclean.core.model

enum class EncryptionStatus { ENCRYPTED, NOT_ENCRYPTED, UNKNOWN }

data class SecurityInfo(
    val rootDetected: Boolean,
    val rootIndicators: List<String>,
    val isDeviceSecure: Boolean,
    val developerOptionsEnabled: Boolean,
    val usbDebuggingEnabled: Boolean,
    val encryption: EncryptionStatus,
    /** "green" = locked + verified, "orange" = bootloader unlocked. Not readable on every ROM. */
    val verifiedBootState: String?,
    val selinuxEnforcing: Boolean?,
)
