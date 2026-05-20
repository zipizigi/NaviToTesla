package me.zipi.navitotesla

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupWithNavController
import me.zipi.navitotesla.databinding.ActivityMainBinding
import me.zipi.navitotesla.ui.home.HomeFragment
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RelaunchNotifier

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        setupWithNavController(binding.navView, navController)
        RelaunchNotifier.cancel(this)
        receivedNotification(intent)
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
