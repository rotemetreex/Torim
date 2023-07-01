package com.rotemyanco.torim

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.rotemyanco.torim.databinding.ActivityClalitLoginBinding
import com.rotemyanco.torim.ui.clalit.home.ClalitHomeFragment
import com.rotemyanco.torim.ui.clalit.login.ClalitLoginFragment


class ClalitUserActivity : AppCompatActivity() {

	private lateinit var binding: ActivityClalitLoginBinding

	private val USER_LOGIN_DATA_TAG = "USER_LOGIN_DATA"
	private val PASSWORD_TAG = "PASSWORD"
	private var runOneTimeRequest: Boolean = false


	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityClalitLoginBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (savedInstanceState == null) {
			try {
				val sharedPreferences = applicationContext.getSharedPreferences(USER_LOGIN_DATA_TAG, Context.MODE_PRIVATE)
				if (sharedPreferences.contains(PASSWORD_TAG)) {

					runOneTimeRequest = true
					sharedPreferences.edit().putBoolean("runOneTimeRequest", true).apply()

					supportFragmentManager.beginTransaction()
						.replace(R.id.fragmentContainerView, ClalitHomeFragment())
						.commit()
				} else {
					supportFragmentManager.beginTransaction()
						.replace(R.id.fragmentContainerView, ClalitLoginFragment())
						.commit()
				}

			} catch (e: Exception) {
				Toast.makeText(this, "onCreate:       ERROR      message                   ===>>>      ${e.message}", Toast.LENGTH_LONG).show()
			}
		}
	}


}