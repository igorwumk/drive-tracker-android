package pl.igorwumk.drivetracker.activity

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import pl.igorwumk.drivetracker.R
import pl.igorwumk.drivetracker.api.APIService
import pl.igorwumk.drivetracker.api.RetrofitClient
import pl.igorwumk.drivetracker.database.TrackingDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

abstract class BaseDrawerActivity : AppCompatActivity() {
    protected lateinit var drawerLayout: DrawerLayout
    protected lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var tvLoginStatus: TextView
    private lateinit var btnLogout: Button

    private val db by lazy { TrackingDatabase.getDatabase(this) }

    // Auth
    private lateinit var authService: APIService
    private val prefs by lazy { EncryptedPrefs.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Common layout that includes the drawer
        setContentView(getLayoutResourceId())

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup hamburger button
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Header view
        val header = navigationView.getHeaderView(0)
        tvLoginStatus = header.findViewById(R.id.tv_login_status)
        btnLogout = header.findViewById(R.id.btn_logout)

        // API
        authService = RetrofitClient.instance.create(APIService::class.java)

        // Update UI based on login state
        updateLoginHeader()

        btnLogout.setOnClickListener { doLogout() }

        // Listen for navigation item selections
        navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_main -> {
                    // Go to MainActivity
                    if (this !is MainActivity) {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                R.id.nav_history -> {
                    // Go to HistoryActivity
                    if (this !is HistoryActivity) {
                        startActivity(Intent(this, HistoryActivity::class.java))
                        //finish()
                    }
                }
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    internal fun updateLoginHeader() {
        val username = prefs.getUsername()
        if (username.isNullOrEmpty()) {
            tvLoginStatus.setText(R.string.account_not_logged_in)
            btnLogout.visibility = View.GONE
        } else {
            tvLoginStatus.text = getString(R.string.account_logged_in_as, username)
            btnLogout.visibility = View.VISIBLE
        }
    }

    private fun doLogout() {
        // show ProgressDialog
        val progress = ProgressDialog(this).apply {
            setMessage("Logging out...")
            setCancelable(false)
            show()
        }

        val token = prefs.getToken()!!
        authService.logout("Token $token").enqueue(object: Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                progress.dismiss()
                if (response.isSuccessful) {
                    // clear stored creds
                    prefs.clearCredentials()
                    performPostLogoutCleanup()
                    updateLoginHeader()
                    Toast.makeText(this@BaseDrawerActivity, "Logged out", Toast.LENGTH_SHORT).show()
                } else {
                    showLogoutError("Logout failed: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                progress.dismiss()
                showLogoutError("Connection failed.")
            }
        })
    }

    private fun showLogoutError(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Logout failed")
            .setMessage(msg)
            .setNeutralButton("Cancel", null)
            .setPositiveButton("Retry") {_, _ -> doLogout() }
            .setNegativeButton("Remove token") { _, _ ->
                prefs.clearCredentials()
                performPostLogoutCleanup()
                updateLoginHeader()
            }
            .show()
    }

    // mark all synced trackings as desynced
    private fun performPostLogoutCleanup() {
        lifecycleScope.launch(Dispatchers.IO) {
            db.sessionDao().markAllSyncedAsDesynced()
        }
    }

    abstract fun getLayoutResourceId(): Int
}