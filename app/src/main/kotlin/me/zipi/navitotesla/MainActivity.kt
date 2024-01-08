package me.zipi.navitotesla

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import me.zipi.navitotesla.databinding.ActivityMainBinding
import me.zipi.navitotesla.ui.home.HomeFragment
import me.zipi.navitotesla.util.AnalysisUtil

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_favorite, R.id.navigation_setting),
        )

        val navHostFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController
        setupActionBarWithNavController(this, navController, appBarConfiguration)
        setupWithNavController(binding.navView, navController)
        if (supportActionBar != null) {
            supportActionBar!!.hide()
        }
        receivedNotification(intent)
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receivedNotification(intent)
    }

    private fun receivedNotification(intent: Intent) {
        val action = intent.getStringExtra("noti_action") ?: return
        if (action == "requireAccessibility") {
            AnalysisUtil.log("received notification: $action")
            HomeFragment.nextAction = action
        }
    }
}
