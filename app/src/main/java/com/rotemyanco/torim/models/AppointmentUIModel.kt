package com.rotemyanco.torim.models

import kotlinx.serialization.*

@Serializable
data class AppointmentUIModel(
	var id: Int,
	val appointment: Appointment,
	var switchState: Boolean,
	var switchColor: Int,
	var switchText: String
)

@Serializable
data class Appointment(
	val dataId: String,
	val dataActionLink: String,

	var switchState: Boolean,
	var switchText: String,
	var switchColor: String,

	val doctorName: String,
	val professionName: String,
	val visitDateTime: String,


	val visitDate: String,
	val visitDay: String,
	val visitMonth: String,
	val visitYear: String,
	val visitTime: String,
	val visitHour: String,
	val visitMinute: String
)