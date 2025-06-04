package com.riskguard.appuser

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

open class BaseActivity : AppCompatActivity() {

        private val timeoutMillis = 5 * 60 * 1000L
        private var logoutHandler: Handler? = null
        private var logoutRunnable: Runnable? = null

        private lateinit var auth: FirebaseAuth

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)


                auth = FirebaseAuth.getInstance()

                logoutHandler = Handler(Looper.getMainLooper())
                logoutRunnable = Runnable {
                        auth.signOut()

                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                }
        }

        override fun onStart() {
                super.onStart()
                setupLogoutTimeout()
        }

        override fun onStop() {
                super.onStop()
                cancelLogoutTimeout()
        }

        private fun setupLogoutTimeout() {
                logoutRunnable?.let {
                        logoutHandler?.postDelayed(it, timeoutMillis)
                }
        }

        private fun cancelLogoutTimeout() {
                logoutRunnable?.let {
                        logoutHandler?.removeCallbacks(it)
                }
        }
}
