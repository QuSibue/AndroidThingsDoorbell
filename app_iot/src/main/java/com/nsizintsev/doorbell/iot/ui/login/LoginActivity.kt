package com.nsizintsev.doorbell.iot.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.nsizintsev.doorbell.iot.ui.main.MainActivity

/**
 * Created by nsizintsev on 3/13/2018.
 */

class LoginActivity : AppCompatActivity() {

    companion object {
        const val RC_SIGN_IN = 1
    }

    private lateinit var firebaseAuth: FirebaseAuth

    private val providers: List<AuthUI.IdpConfig>

    init {
        val googleBuilder = AuthUI.IdpConfig.GoogleBuilder()
        providers = arrayListOf(googleBuilder.build())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()

        if (firebaseAuth.currentUser != null) {
            startMainActivity()
            finish()
            return
        }

        startLogin()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(intent)
            if (resultCode == Activity.RESULT_OK) {
                val user = firebaseAuth.currentUser
                startMainActivity()
            } else {
                Toast.makeText(this,
                        "Login failed. Try again, please",
                        Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startLogin() {
        startActivityForResult(AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(), RC_SIGN_IN)
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
    }

}
