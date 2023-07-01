package com.rotemyanco.torim

import android.app.Application


class App : Application() {

	companion object {
		private lateinit var instance: App
	}

	override fun onCreate() {
		super.onCreate()
		instance = this
	}
}

