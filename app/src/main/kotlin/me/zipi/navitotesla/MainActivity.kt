package me.zipi.navitotesla

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
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
            RelaunchNotifier.NOTI_ACTION_VALUE -> {
                showBriefMessage(getString(R.string.guideRelaunchReason))
            }
        }
    }

    private fun showBriefMessage(msg: String) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val text =
            TextView(this).apply {
                text = msg
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.md_on_surface_light))
                background = AppCompatResources.getDrawable(this@MainActivity, R.drawable.bg_floating_pill)
                val pH = dpToPx(20)
                val pV = dpToPx(14)
                setPadding(pH, pV, pH, pV)
                elevation = dpToPx(4).toFloat()
                textSize = 13f
                maxWidth = dpToPx(280)
            }
        val params =
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.bottomnav_safe_area) + dpToPx(16)
                }
        root.addView(text, params)
        text.alpha = 0f
        text
            .animate()
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                text.postDelayed({
                    text
                        .animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction { root.removeView(text) }
                        .start()
                }, 4000)
            }.start()
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
