package vn.sysclean.feature.deviceinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import vn.sysclean.core.ui.displayLabel
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.common.format.formatDuration
import vn.sysclean.core.common.format.formatFrequency
import vn.sysclean.core.designsystem.component.InfoRow
import vn.sysclean.core.designsystem.component.LabeledUsage
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.component.UsageBar
import vn.sysclean.core.model.BatteryHealth
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.CameraItem
import vn.sysclean.core.model.CpuInfo
import vn.sysclean.core.model.DeviceOverview
import vn.sysclean.core.model.DisplayInfo
import vn.sysclean.core.model.EncryptionStatus
import vn.sysclean.core.model.LensFacing
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.SecurityInfo
import vn.sysclean.core.model.SensorItem
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.model.ThermalStatus
import vn.sysclean.core.ui.label
import vn.sysclean.core.ui.usedOfTotal
import vn.sysclean.core.ui.yesNo
import java.util.Locale

private fun fmt(pattern: String, vararg args: Any?) = String.format(Locale.getDefault(), pattern, *args)

internal fun LazyListScope.overviewTab(overview: DeviceOverview) {
    item {
        SectionCard(title = stringResource(R.string.device_section_device), icon = Icons.Outlined.PhoneAndroid) {
            InfoRow(stringResource(R.string.device_manufacturer), overview.manufacturer)
            InfoRow(stringResource(R.string.device_brand), overview.brand)
            InfoRow(stringResource(R.string.device_model), overview.model)
            InfoRow(stringResource(R.string.device_codename), overview.device)
            InfoRow(stringResource(R.string.device_product), overview.product)
            InfoRow(stringResource(R.string.device_board), overview.board)
            InfoRow(stringResource(R.string.device_hardware), overview.hardware)
            InfoRow(
                stringResource(R.string.device_soc),
                listOfNotNull(overview.socManufacturer, overview.socModel).joinToString(" ").ifBlank { null },
            )
        }
    }
    item {
        SectionCard(title = stringResource(R.string.device_section_android), icon = Icons.Outlined.Android) {
            InfoRow(
                stringResource(R.string.device_android_version),
                stringResource(R.string.device_android_version_value, overview.androidVersion, overview.sdkInt),
            )
            InfoRow(stringResource(R.string.device_security_patch), overview.securityPatch)
            InfoRow(stringResource(R.string.device_build), overview.buildId)
            InfoRow(stringResource(R.string.device_kernel), overview.kernelVersion)
            InfoRow(stringResource(R.string.device_bootloader), overview.bootloader)
            InfoRow(stringResource(R.string.device_baseband), overview.radioVersion)
            InfoRow(stringResource(R.string.device_abis), overview.supportedAbis.joinToString(", "))
            InfoRow(stringResource(R.string.device_uptime), formatDuration(overview.uptimeMillis))
            InfoRow(stringResource(R.string.device_fingerprint), overview.fingerprint)
        }
    }
}

internal fun LazyListScope.cpuTab(cpu: CpuInfo?, overview: DeviceOverview?) {
    if (cpu == null) {
        item { LoadingContent() }
        return
    }
    item {
        SectionCard(title = stringResource(R.string.device_cpu_summary), icon = Icons.Outlined.DeveloperBoard) {
            val soc = overview?.let { listOfNotNull(it.socManufacturer, it.socModel).joinToString(" ") }
            InfoRow(stringResource(R.string.device_soc), soc?.ifBlank { null } ?: cpu.hardwareName ?: overview?.hardware)
            InfoRow(stringResource(R.string.device_cpu_cores), cpu.coreCount.toString())
            val clusters = cpu.clusters.filter { cluster -> cluster.first().maxFreqKHz != null }
            if (clusters.isNotEmpty()) {
                InfoRow(
                    stringResource(R.string.device_cpu_clusters),
                    clusters.joinToString(" + ") { "${it.size} × ${formatFrequency(it.first().maxFreqKHz!!)}" },
                )
            }
            InfoRow(stringResource(R.string.device_cpu_governor), cpu.cores.firstNotNullOfOrNull { it.governor })
        }
    }
    item {
        SectionCard(title = stringResource(R.string.device_cpu_per_core), icon = Icons.Outlined.Speed) {
            if (cpu.cores.all { it.maxFreqKHz == null }) {
                Text(stringResource(R.string.device_cpu_unreadable), style = MaterialTheme.typography.bodyMedium)
            }
            cpu.cores.filter { it.maxFreqKHz != null }.forEach { core ->
                val max = core.maxFreqKHz!!
                val current = core.currentFreqKHz
                LabeledUsage(
                    title = stringResource(R.string.device_cpu_core, core.index),
                    detail = if (core.online && current != null) {
                        "${formatFrequency(current)} / ${formatFrequency(max)}"
                    } else {
                        stringResource(R.string.device_cpu_offline)
                    },
                    fraction = if (core.online && current != null) current.toFloat() / max else 0f,
                    // A core at full clock is working as intended, not a warning.
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal fun LazyListScope.memoryTab(memory: MemoryInfo?) {
    if (memory == null) {
        item { LoadingContent() }
        return
    }
    item {
        SectionCard(title = stringResource(R.string.device_ram), icon = Icons.Outlined.Memory) {
            LabeledUsage(
                title = stringResource(R.string.device_ram_used),
                detail = usedOfTotal(formatBytes(memory.usedBytes), formatBytes(memory.totalBytes)),
                fraction = memory.usedFraction,
            )
            InfoRow(stringResource(R.string.device_ram_total), formatBytes(memory.totalBytes))
            InfoRow(stringResource(R.string.device_ram_available), formatBytes(memory.availableBytes))
            InfoRow(stringResource(R.string.device_ram_threshold), formatBytes(memory.lowMemoryThresholdBytes))
            InfoRow(stringResource(R.string.device_ram_low), yesNo(memory.isLowMemory))
        }
    }
    val swapTotal = memory.swapTotalBytes
    if (swapTotal != null && swapTotal > 0) {
        item {
            SectionCard(title = stringResource(R.string.device_swap), icon = Icons.Outlined.Memory) {
                val used = swapTotal - (memory.swapFreeBytes ?: 0)
                LabeledUsage(
                    title = stringResource(R.string.device_ram_used),
                    detail = usedOfTotal(formatBytes(used), formatBytes(swapTotal)),
                    fraction = used.toFloat() / swapTotal,
                )
            }
        }
    }
}

internal fun LazyListScope.batteryTab(battery: BatteryInfo?) {
    if (battery == null) {
        item { LoadingContent() }
        return
    }
    item {
        SectionCard(title = stringResource(R.string.device_tab_battery), icon = Icons.Outlined.BatteryChargingFull) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${battery.levelPercent}%", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                StatusChip(battery.health.label(), if (battery.health == BatteryHealth.GOOD) Status.GOOD else Status.WARNING)
            }
            UsageBar(battery.levelPercent / 100f, color = MaterialTheme.colorScheme.primary)
            InfoRow(stringResource(R.string.device_battery_status), battery.status.label())
            InfoRow(stringResource(R.string.device_battery_power_source), battery.plugType.label())
            InfoRow(stringResource(R.string.device_battery_temperature), battery.temperatureCelsius?.let { fmt("%.1f °C", it) })
            InfoRow(stringResource(R.string.device_battery_voltage), battery.voltageMillivolts?.let { fmt("%.2f V", it / 1000.0) })
            InfoRow(stringResource(R.string.device_battery_current), battery.currentNowMicroAmp?.let { fmt("%d mA", it / 1000) })
            InfoRow(stringResource(R.string.device_battery_technology), battery.technology)
            InfoRow(stringResource(R.string.device_battery_cycles), battery.cycleCount?.toString())
            InfoRow(
                stringResource(R.string.device_battery_thermal),
                battery.thermalStatus.takeIf { it != ThermalStatus.UNKNOWN }?.label(),
            )
        }
    }
    item {
        SectionCard(title = stringResource(R.string.device_battery_health), icon = Icons.Outlined.BatteryChargingFull) {
            val design = battery.designCapacityMah
            val estimated = battery.estimatedCapacityMah
            InfoRow(stringResource(R.string.device_battery_design_capacity), design?.let { fmt("%.0f mAh", it) })
            InfoRow(stringResource(R.string.device_battery_estimated_capacity), estimated?.let { fmt("%.0f mAh", it) })
            if (design != null && estimated != null) {
                val ratio = (estimated / design).coerceAtMost(1.0)
                InfoRow(stringResource(R.string.device_battery_wear), fmt("%.0f%%", ratio * 100))
            }
            Text(
                stringResource(R.string.device_battery_capacity_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun LazyListScope.displayTab(display: DisplayInfo) {
    item {
        SectionCard(title = stringResource(R.string.device_tab_display), icon = Icons.Outlined.Screenshot) {
            InfoRow(stringResource(R.string.device_display_resolution), "${display.widthPx} × ${display.heightPx}")
            InfoRow(stringResource(R.string.device_display_density), "${display.densityDpi} dpi")
            InfoRow(stringResource(R.string.device_display_size), display.diagonalInches?.let { fmt("%.1f\"", it) })
            InfoRow(stringResource(R.string.device_display_refresh), fmt("%.0f Hz", display.refreshRateHz))
            InfoRow(
                stringResource(R.string.device_display_supported_refresh),
                display.supportedRefreshRatesHz.joinToString(", ") { fmt("%.0f Hz", it) },
            )
            InfoRow(
                stringResource(R.string.device_display_hdr),
                display.hdrTypes.joinToString(", ").ifBlank { stringResource(R.string.device_display_no_hdr) },
            )
        }
    }
}

internal fun LazyListScope.storageTab(storage: StorageInfo) {
    items(storage.volumes) { volume ->
        SectionCard(title = volume.displayLabel(), icon = Icons.Outlined.SdStorage) {
            LabeledUsage(
                title = stringResource(R.string.device_storage_used),
                detail = fmt("%.0f%%", volume.usedFraction * 100),
                fraction = volume.usedFraction,
            )
            InfoRow(stringResource(R.string.device_storage_total), formatBytes(volume.totalBytes))
            InfoRow(stringResource(R.string.device_storage_used), formatBytes(volume.usedBytes))
            InfoRow(stringResource(R.string.device_storage_free), formatBytes(volume.freeBytes))
            InfoRow(stringResource(R.string.device_storage_removable), yesNo(volume.isRemovable))
        }
    }
}

internal fun LazyListScope.sensorsTab(sensors: List<SensorItem>) {
    item {
        Text(
            stringResource(R.string.device_sensor_count, sensors.size),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    items(sensors) { sensor ->
        SectionCard(title = sensor.type, icon = Icons.Outlined.Sensors) {
            Text(sensor.name, style = MaterialTheme.typography.bodyMedium)
            InfoRow(stringResource(R.string.device_sensor_vendor), sensor.vendor)
            InfoRow(stringResource(R.string.device_sensor_power), fmt("%.2f mA", sensor.powerMilliAmp))
            InfoRow(stringResource(R.string.device_sensor_range), fmt("%.2f", sensor.maxRange))
        }
    }
}

internal fun LazyListScope.camerasTab(cameras: List<CameraItem>) {
    if (cameras.isEmpty()) {
        item { Text(stringResource(R.string.device_camera_none)) }
        return
    }
    items(cameras) { camera ->
        val title = stringResource(
            when (camera.facing) {
                LensFacing.FRONT -> R.string.device_camera_front
                LensFacing.BACK -> R.string.device_camera_back
                LensFacing.EXTERNAL -> R.string.device_camera_external
                LensFacing.UNKNOWN -> R.string.device_camera_unknown
            },
        )
        SectionCard(title = "$title · #${camera.id}", icon = Icons.Outlined.CameraAlt) {
            InfoRow(stringResource(R.string.device_camera_resolution), camera.megapixels?.let { fmt("%.1f MP", it) })
            InfoRow(stringResource(R.string.device_camera_flash), yesNo(camera.hasFlash))
        }
    }
}

internal fun LazyListScope.securityTab(security: SecurityInfo) {
    item {
        SectionCard(title = stringResource(R.string.device_tab_security), icon = Icons.Outlined.Security) {
            StatusRow(
                label = stringResource(R.string.device_security_root),
                value = stringResource(
                    if (security.rootDetected) R.string.device_security_root_detected else R.string.device_security_root_not_detected,
                ),
                status = if (security.rootDetected) Status.WARNING else Status.GOOD,
            )
            if (security.rootIndicators.isNotEmpty()) {
                InfoRow(stringResource(R.string.device_security_root_traces), security.rootIndicators.joinToString("\n"))
            }
            StatusRow(
                label = stringResource(R.string.device_security_screen_lock),
                value = onOff(security.isDeviceSecure),
                status = if (security.isDeviceSecure) Status.GOOD else Status.CRITICAL,
            )
            StatusRow(
                label = stringResource(R.string.device_security_encryption),
                value = when (security.encryption) {
                    EncryptionStatus.ENCRYPTED -> stringResource(R.string.device_security_encrypted)
                    EncryptionStatus.NOT_ENCRYPTED -> stringResource(R.string.device_security_not_encrypted)
                    EncryptionStatus.UNKNOWN -> yesNo(null)
                },
                status = when (security.encryption) {
                    EncryptionStatus.ENCRYPTED -> Status.GOOD
                    EncryptionStatus.NOT_ENCRYPTED -> Status.CRITICAL
                    EncryptionStatus.UNKNOWN -> Status.NEUTRAL
                },
            )
            StatusRow(
                label = stringResource(R.string.device_security_dev_options),
                value = onOff(security.developerOptionsEnabled),
                status = if (security.developerOptionsEnabled) Status.NEUTRAL else Status.GOOD,
            )
            StatusRow(
                label = stringResource(R.string.device_security_usb_debugging),
                value = onOff(security.usbDebuggingEnabled),
                status = if (security.usbDebuggingEnabled) Status.WARNING else Status.GOOD,
            )
            security.verifiedBootState?.let { state ->
                StatusRow(
                    label = stringResource(R.string.device_security_verified_boot),
                    value = when (state) {
                        "green" -> stringResource(R.string.device_security_boot_green)
                        "yellow" -> stringResource(R.string.device_security_boot_yellow)
                        "orange" -> stringResource(R.string.device_security_boot_orange)
                        else -> state
                    },
                    status = if (state == "green") Status.GOOD else Status.WARNING,
                )
            }
            security.selinuxEnforcing?.let { enforcing ->
                StatusRow(
                    label = stringResource(R.string.device_security_selinux),
                    value = stringResource(if (enforcing) R.string.device_security_enforcing else R.string.device_security_permissive),
                    status = if (enforcing) Status.GOOD else Status.WARNING,
                )
            }
        }
    }
}

@Composable
private fun onOff(value: Boolean) = stringResource(if (value) R.string.device_on else R.string.device_off)

@Composable
private fun StatusRow(label: String, value: String, status: Status) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip(value, status)
    }
}
