package me.zipi.navitotesla

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupWithNavController
import me.zipi.navitotesla.databinding.ActivityMainBinding
import me.zipi.navitotesla.ui.home.HomeFragment
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RelaunchNotifier

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        setupWithNavController(binding.navView, navController)
        RelaunchNotifier.cancel(this)
        receivedNotification(intent)
    }

    // edge-to-edge: 콘텐츠는 상태바 아래, BottomNav 는 네비바 위로 두어 현재 룩을 유지한다.
    private fun applyWindowInsets(binding: ActivityMainBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostFragmentActivityMain.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.navView.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        RelaunchNotifier.cancel(this)
        receivedNotification(intent)
    }

    private fun receivedNotification(intent: Intent) {
        val action = intent.getStringExtra("noti_action") ?: return
        AnalysisUtil.log("received notification: $action")
        when (action) {
            "requireAccessibility" -> {
                HomeFragment.nextAction = action
            }
        }
    }
}
