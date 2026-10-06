package vn.sysclean.feature.deviceinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.theme.SysCleanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceInfoScreen(viewModel: DeviceInfoViewModel = hiltViewModel()) {
    val staticInfo by viewModel.staticInfo.collectAsStateWithLifecycle()
    val cpu by viewModel.cpu.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()

    val tabs = DeviceTab.entries
    val pagerState = rememberPagerState(initialPage = viewModel.initialTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.device_title)) }) },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = SysCleanTheme.spacing.lg) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title()) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val tab = tabs[page]
                val info = staticInfo
                val bottom = padding.calculateBottomPadding()
                when (tab) {
                    DeviceTab.CPU -> TabList(bottom) { cpuTab(cpu, info?.overview) }
                    DeviceTab.MEMORY -> TabList(bottom) { memoryTab(memory) }
                    DeviceTab.BATTERY -> TabList(bottom) { batteryTab(battery) }
                    else -> if (info == null) {
                        LoadingContent()
                    } else {
                        TabList(bottom) {
                            when (tab) {
                                DeviceTab.OVERVIEW -> overviewTab(info.overview)
                                DeviceTab.DISPLAY -> displayTab(info.display)
                                DeviceTab.STORAGE -> storageTab(info.storage)
                                DeviceTab.SENSORS -> sensorsTab(info.sensors)
                                DeviceTab.CAMERAS -> camerasTab(info.cameras)
                                DeviceTab.SECURITY -> securityTab(info.security)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabList(bottomPadding: Dp, content: LazyListScope.() -> Unit) {
    val spacing = SysCleanTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.lg, bottom = bottomPadding + spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        content = content,
    )
}

@Composable
private fun DeviceTab.title(): String = stringResource(
    when (this) {
        DeviceTab.OVERVIEW -> R.string.device_tab_overview
        DeviceTab.CPU -> R.string.device_tab_cpu
        DeviceTab.MEMORY -> R.string.device_tab_memory
        DeviceTab.BATTERY -> R.string.device_tab_battery
        DeviceTab.DISPLAY -> R.string.device_tab_display
        DeviceTab.STORAGE -> R.string.device_tab_storage
        DeviceTab.SENSORS -> R.string.device_tab_sensors
        DeviceTab.CAMERAS -> R.string.device_tab_cameras
        DeviceTab.SECURITY -> R.string.device_tab_security
    },
)
