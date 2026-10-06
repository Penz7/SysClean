package vn.sysclean

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.feature.widget.WidgetRefresher
import vn.sysclean.navigation.SysCleanApp
import javax.inject.Inject

// AppCompatActivity (not ComponentActivity) so per-app language changes apply on Android 12 and below.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var accessRepository: AccessRepository

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SysCleanTheme {
                SysCleanApp(widgetRefresher)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions are granted in system Settings; re-check whenever the user comes back.
        accessRepository.refresh()
    }
}
