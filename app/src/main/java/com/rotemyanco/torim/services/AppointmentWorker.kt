package com.rotemyanco.torim.services

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.*
import com.rotemyanco.torim.models.Appointment
import com.rotemyanco.torim.models.AppointmentUIModel
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


class AppointmentWorker(
	private val context: Context,
	workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

	private val client = KtorClientManager.getClient()
	private lateinit var monthResult: HttpResponse
	private lateinit var deferredResult: Unit
	private lateinit var deferredSearchResult: Result

	// url's:
	private val urlLoginPage: String = "https://e-services.clalit.co.il/OnlineWeb/General/login.aspx"
	private val urlTamuzTransferPage: String = "https://e-services.clalit.co.il/OnlineWeb/Services/Tamuz/TamuzTransfer.aspx"
	private val urlTamuzContentByService: String =
		"https://e-services.clalit.co.il/OnlineWeb/Services/Tamuz/TamuzTransferContentByService.aspx?MethodName=TransferWithAuth"
	private val urlZimunetVisitsLogin: String = "https://e-services.clalit.co.il/Zimunet/Visits/Login"
	private val urlZimunetVisits: String = "https://e-services.clalit.co.il/Zimunet/"


	private lateinit var username: String
	private lateinit var usercode: String
	private lateinit var password: String

	private var _myPrePayloadMap = mutableMapOf<String, String>()
	private var _cookies = mutableListOf<Cookie>()

	private var _appointmentUIModelList = mutableListOf<AppointmentUIModel>()
	private var _appointmentList = mutableListOf<Appointment>()

	private lateinit var loginPageGETResponse: HttpResponse
	private lateinit var loginPagePOSTResponse: HttpResponse

	private lateinit var afterLoginGetToTamuzTransferAspx: HttpResponse
	private lateinit var afterLoginGetToTamuzTransferWithAuth: HttpResponse

	private lateinit var postToIFrameLogin: HttpResponse

	private lateinit var assertPostToIFrameLoginViaLocationHeader: HttpResponse

	private lateinit var parsedStringResultForClientAppointmentLists: String


	private var _elementsTextList = arrayListOf<String>()

	private val USER_LOGIN_DATA_TAG = "USER_LOGIN_DATA"
	private val USERNAME_TAG = "USERNAME"
	private val USERCODE_TAG = "USERCODE"
	private val PASSWORD_TAG = "PASSWORD"

	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var sharedPrefsUsername: String
	private lateinit var sharedPrefsUsercode: String
	private lateinit var sharedPrefsUserPassword: String

	private var visitDayInt: Int = 0
	private var visitYearInt: Int = 0

	private var positiveDaySearchResult: Int = 0
	private var positiveMonthSearchResult: Int = 0

	private lateinit var doctorName: String
	private lateinit var appInternalID: String

	private lateinit var mUniqueVisitId: String
	private lateinit var checkedVisits: String
	private val allResultsForSpecificDoctorsApp = mutableMapOf<String, ArrayList<String>>()


	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	override suspend fun doWork(): Result {
		try {
			sharedPreferences = applicationContext.getSharedPreferences(USER_LOGIN_DATA_TAG, Context.MODE_PRIVATE)

			sharedPrefsUsername = sharedPreferences.getString(USERNAME_TAG, "").toString()
			sharedPrefsUsercode = sharedPreferences.getString(USERCODE_TAG, "").toString()
			sharedPrefsUserPassword = sharedPreferences.getString(PASSWORD_TAG, "").toString()

		} catch (e: Exception) {
			Toast.makeText(context, "doWork:           ERROR  -===>   ${e.message}", Toast.LENGTH_LONG).show()
		}


		var workRequestType = inputData.getInt("workRequestType", 1)

		username = inputData.getString("USERNAME").toString()
		usercode = inputData.getString("USERCODE").toString()
		password = inputData.getString("PASSWORD").toString()

		val outputData = Data.Builder()

		val result = withContext(Dispatchers.IO) {
			when (workRequestType) {
				1 -> {
					// workRequestType    ==>  ONE_TIME
					launchLogin()
					_appointmentUIModelList = getAllAppointmentsUIModelClassList()

					deferredResult = withContext(Dispatchers.IO) {
						_appointmentUIModelList.forEach { visitUI ->
							outputData.putString(visitUI.id.toString(), Json.encodeToString(AppointmentUIModel.serializer(), visitUI))
						}
					}
					var builtOutputData: Data? = outputData.build()
					return@withContext Result.success(builtOutputData!!)
				}
				2 -> {
					// workRequestType    ==>  PERIODIC
					launchLogin()
					_appointmentUIModelList = getAllAppointmentsUIModelClassList()
					deferredSearchResult = withContext(Dispatchers.IO) { search() }

					outputData.putAll(deferredSearchResult.outputData)

					var builtOutputData: Data?
					builtOutputData = outputData.build()

					return@withContext Result.success(builtOutputData)
				}
				else -> {
					return@withContext Result.failure()
				}
			}
		}
		return result
	}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private fun createNotificationChannel(isSearchResultPositive: Boolean) {

		val SUMMARY_ID = 0
		val GROUP_KEY_SEARCH_SERVICE = "com.android.example.SearchService.NOTIFICATION_GROUP"

		val CHANNEL_ID = "appointment_service_channel_ID"
		val CHANNEL_NAME = "appointment_service_channel_name"
		val NOTIFICATION_ID = 0

		val intent = Intent(context, AppointmentWorker::class.java)
		val pendingIntent = TaskStackBuilder.create(context).run {
			addNextIntent(intent)
			getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
		}
		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		val searchInProgressNotification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(com.rotemyanco.torim.R.drawable.ic_baseline_info_24)
			.setContentTitle("Clalit App Search Service for - $doctorName")
			.setContentText("Searched for earlier dates")

			.setOngoing(true)
			.setGroup(GROUP_KEY_SEARCH_SERVICE)
			.setAutoCancel(false)

			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setContentIntent(pendingIntent)
			.build()
		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		val positiveSearchResultNotification = NotificationCompat.Builder(context, CHANNEL_ID)

			.setSmallIcon(com.rotemyanco.torim.R.drawable.ic_baseline_info_24)
			.setLargeIcon(BitmapFactory.decodeResource(context.resources, com.rotemyanco.torim.R.drawable.ic_baseline_info_24))

			.setContentTitle("Clalit App Search Results for - $doctorName")
//			.setContentText("earlier date at $positiveDaySearchResult.$positiveMonthSearchResult.2023")

			.setStyle(NotificationCompat.BigTextStyle().bigText("for $doctorName : earlier date at $positiveDaySearchResult.$positiveMonthSearchResult.2023"))
			.setGroup(GROUP_KEY_SEARCH_SERVICE)
			.setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
			.setOngoing(true)
			.setAutoCancel(false)

//			.setSmallIcon(com.rotemyanco.torim.R.drawable.ic_baseline_info_24)
//			.setLargeIcon(BitmapFactory.decodeResource(context.resources, com.rotemyanco.torim.R.drawable.ic_baseline_info_24))
//			.setCustomBigContentView(getCustomBigContentView())
//			.setCategory(NotificationCompat.CATEGORY_MESSAGE)
//			.setOnlyAlertOnce(true)


			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setContentIntent(pendingIntent)
			.build()
		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(com.rotemyanco.torim.R.drawable.ic_baseline_info_24)
			.setStyle(
				NotificationCompat.InboxStyle()
					.addLine("there's an earlier date for $doctorName!")
					.addLine("for $doctorName : earlier date at $positiveDaySearchResult.$positiveMonthSearchResult.2023")
					.setBigContentTitle("*** Clalit App Search Service ***")
					.setSummaryText("Search Results")
			)
			.setOngoing(true)
			.setAutoCancel(false)
			.setGroup(GROUP_KEY_SEARCH_SERVICE)
			.setGroupSummary(true)
			.build()

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		val notificationManagerCompat = NotificationManagerCompat.from(context)
		when (isSearchResultPositive && ActivityCompat.checkSelfPermission(
			context,
			Manifest.permission.POST_NOTIFICATIONS
		) == PackageManager.PERMISSION_GRANTED) {
			true -> {
				notificationManagerCompat.apply {
					notify(SUMMARY_ID, summaryNotification)
					notify(NOTIFICATION_ID, positiveSearchResultNotification)
				}
			}
			false -> {
				notificationManagerCompat.apply {
					notify(SUMMARY_ID, summaryNotification)
					notify(NOTIFICATION_ID + 1, searchInProgressNotification)
				}
			}
		}
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		val channel = NotificationChannel(
			CHANNEL_ID,
			CHANNEL_NAME,
			NotificationManager.IMPORTANCE_HIGH
		)

		val notificationManager = getSystemService(context, NotificationManager::class.java)!!
		notificationManager.createNotificationChannel(channel)
	}

	private suspend fun launchLogin() {
		loginPageGETResponse = loginPageGET()
		loginPagePOSTResponse = loginPagePOST()

		afterLoginGetToTamuzTransferAspx = afterLoginGetToTamuzTransferAspx()
		afterLoginGetToTamuzTransferWithAuth = afterLoginGetToTamuzTransferWithAuth()

		postToIFrameLogin = postToIFrameLogin()

		assertPostToIFrameLoginViaLocationHeader = assertPostToIFrameLoginViaLocationHeader()
	}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private suspend fun search(): Result {
		try {
			checkedVisits = sharedPreferences.getString("checked_visits", null).toString()
		} catch (e: Exception) {
			Toast.makeText(context, "search:            ${e.message}", Toast.LENGTH_LONG).show()
		}

		val data = Data.Builder()
		val outputDataKeyValueMap = mutableMapOf<String, Any>()

		var visitId: String
		var visitDate: String

		// list is renewed with the new login - id's are different!!
		_appointmentUIModelList.forEach { appointmentUIModel ->
			if (checkedVisits.isNotBlank()) {
				visitId = appointmentUIModel.appointment.dataId

				mUniqueVisitId = "${appointmentUIModel.appointment.visitDate.replace(".", "")}_${
					appointmentUIModel.appointment.doctorName
						.replace(" ", "")
						.replace("\"", "")
						.replace("\'", "")
				}"

				if (checkedVisits.contains(mUniqueVisitId)) {
					appInternalID = appointmentUIModel.id.toString()
					visitDate = appointmentUIModel.appointment.visitDate
					doctorName = appointmentUIModel.appointment.doctorName


					val cal = Calendar.getInstance()
					val instant = Instant.ofEpochMilli(cal.timeInMillis)
					val systemZoneId = ZoneId.systemDefault()
					val zoneId = ZoneId.of(systemZoneId.id)
					val newLocalDateTime01 = LocalDateTime.ofInstant(instant, zoneId)
					var visitMonthInt: Int? = 0

					try {
						val listOfStrDate = visitDate.split(".")

						visitDayInt = listOfStrDate[0].toInt()
						visitMonthInt = listOfStrDate[1].toInt()
						visitYearInt = listOfStrDate[2].toInt()

					} catch (e: Exception) {
						Toast.makeText(context, "search:            ${e.message}", Toast.LENGTH_LONG).show()
					}

					withContext(Dispatchers.IO) {
						for (i in (newLocalDateTime01.month.value)..visitMonthInt!!) {
							monthResult =
								client.get("https://e-services.clalit.co.il/Zimunet/AvailableVisit/GetMonthlyAvailableVisit?id=$visitId&professionType=Professional&month=${i}&year=2023&isUpdateVisit=True")

							var dailyVisitsJsonLiteral: String
							var tempArr = arrayListOf<String>()
							var outputData: Data?

							val htmlResponseToJsonElement = Json.parseToJsonElement(monthResult.call.response.bodyAsText())
							val jsonResponseElementToJsonObject = htmlResponseToJsonElement.jsonObject
							val tempList = mutableListOf<String>()

							jsonResponseElementToJsonObject.entries.forEach {
								if ((it.key == "data") && (it.value is JsonObject)) {
									val dataKeyJsonObject = it.value.jsonObject
									val dailyVisits = dataKeyJsonObject["dailyVisits"]
									val availableDaysJsonArray = dataKeyJsonObject["availableDays"]?.jsonArray
									if (availableDaysJsonArray is JsonArray) {
										availableDaysJsonArray.forEach { jsonEl ->
											if (jsonEl is JsonPrimitive) {
												tempArr.add(jsonEl.content)
											}
										}
									}
									if (dailyVisits is JsonPrimitive) {
										dailyVisitsJsonLiteral = dailyVisits.content
										tempList.add(dailyVisitsJsonLiteral)

										val document = Jsoup.parse(dailyVisitsJsonLiteral)
										val elements = document.select("header.margin-right")
										val elTxt = elements[0].text()

										if (!_elementsTextList.contains(elTxt)) {
											_elementsTextList.add(elTxt)
										}
									}
								}
							}

							try {
//								when (tempArr.size) {
								if (tempList.size == 0) {
									createNotificationChannel(false)
								} else if (tempList.size > 0) {
									val singleDateStringList = tempArr[0].split(".")
									val firstFoundDayInt = singleDateStringList[0].toInt()
									val firstFoundMonthInt = singleDateStringList[1].toInt()

									// check that the first found day is actually an earlier day than the visit day
									if (firstFoundMonthInt == visitMonthInt && firstFoundDayInt >= visitDayInt) {
										return@withContext
									}
									positiveDaySearchResult = firstFoundDayInt
									positiveMonthSearchResult = firstFoundMonthInt

									allResultsForSpecificDoctorsApp[appInternalID] = tempArr

									createNotificationChannel(true)
									break
								}

							} catch (e: Exception) {
								Toast.makeText(context, "search:            ${e.message}", Toast.LENGTH_LONG).show()
							}

							outputData = Data.Builder()
								.putStringArray("available_days_array_list", tempArr.toTypedArray())
								.build()

						}
					}
				}
			}
		}

		return Result.success(data.putAll(outputDataKeyValueMap).build())
	}

	private suspend fun getAllAppointmentsUIModelClassList(): MutableList<AppointmentUIModel> {
		return withContext(Dispatchers.IO) {

			parsedStringResultForClientAppointmentLists = assertPostToIFrameLoginViaLocationHeader.call.response.bodyAsText()

			val document = Jsoup.parse(parsedStringResultForClientAppointmentLists)
			val liElementWithAttDataId: Elements = document.select("div#visits")

			var futureAppointments: MutableList<Element>
			val tempList = mutableListOf<Appointment>()
			val tempList1 = mutableListOf<AppointmentUIModel>()

			var visitDateString: String
			var visitTimeString: String

			var spanList: Elements

			liElementWithAttDataId.forEach {
				futureAppointments = it.allElements.select("li[data-id]")

				// TODO: mapping function: will alter the Element class into an Appointment class
				// TODO:  then push the appointment to an Appointment List

				for (idx in 0 until futureAppointments.size) {

					spanList = futureAppointments[idx].select("span.visitDateTime")

					visitDateString = spanList[0].text()
					visitTimeString = spanList[1].text()

					val visitLocalDateTime = "$visitDateString$visitTimeString:00"
					val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyyHH:mm:ss")

					val currentVisitLocalDateTime = LocalDateTime.parse(visitLocalDateTime, formatter)

					val appointment = Appointment(
						dataId = futureAppointments[idx].attr("data-id"),
						dataActionLink = futureAppointments[idx].select("a[data-action-link]").text(),

						switchState = false,
						switchText = "Service is OFF",
						switchColor = Color.GRAY.toString(),

						doctorName = futureAppointments[idx].select("div.doctorName").text(),
						professionName = futureAppointments[idx].select("div.professionName").text(),
						visitDateTime = futureAppointments[idx].select("span.visitDateTime").text(),
						visitDate = visitDateString,
						visitDay = currentVisitLocalDateTime.dayOfMonth.toString(),
						visitMonth = currentVisitLocalDateTime.month.toString(),
						visitYear = currentVisitLocalDateTime.year.toString(),
						visitTime = visitTimeString,
						visitHour = currentVisitLocalDateTime.hour.toString(),
						visitMinute = currentVisitLocalDateTime.minute.toString(),
					)

					val appointmentUIModel = AppointmentUIModel(
						id = idx,
						appointment = appointment,
						switchState = false,
						switchColor = Color.GRAY,
						switchText = com.rotemyanco.torim.R.string.switch_OFF_state.toString(),
					)

					tempList.add(appointment)
					tempList1.add(appointmentUIModel)
				}
			}
			_appointmentList.addAll(tempList)
			_appointmentUIModelList.addAll(tempList1)

			_cookies.addAll(client.cookies("https://e-services.clalit.co.il"))

			parsedStringResultForClientAppointmentLists
			_appointmentUIModelList
		}
	}


	// login to clalit:
	private suspend fun loginPageGET(): HttpResponse {
		// GET login page response
		//		 https://e-services.clalit.co.il       ----> 		/login.aspx

		return withContext(Dispatchers.IO) {
			loginPageGETResponse = client.get(urlLoginPage) {
				userAgent("Mozilla/5.0 (Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0")
			}
			val regex = Regex("<input.*name=\"(.*?)\".*value=\"(.*)\"")
			val matches = regex.findAll(loginPageGETResponse.bodyAsText(Charsets.UTF_8))
			matches.forEach {
				_myPrePayloadMap[it.groups[1]?.value.toString()] = it.groups[2]?.value.toString()
			}
			loginPageGETResponse
		}
	}

	private suspend fun loginPagePOST(): HttpResponse {

		// POST https://e-services.clalit.co.il/OnlineWeb/General/login.aspx
		return withContext(Dispatchers.IO) {
			val formParameters = Parameters.build {
				append("__EVENTTARGET", "ctl00\$cphBody\$_loginView\$btnSend")
				_myPrePayloadMap["__VIEWSTATE"]?.let { append("__VIEWSTATE", it) }
				_myPrePayloadMap["__VIEWSTATEGENERATOR"]?.let { append("__VIEWSTATEGENERATOR", it) }
				append("__VIEWSTATEENCRYPTED", "")
				_myPrePayloadMap["__PREVIOUSPAGE"]?.let { append("__PREVIOUSPAGE", it) }
				_myPrePayloadMap["__EVENTVALIDATION"]?.let { append("__EVENTVALIDATION", it) }

				append("ctl00\$cphBody\$_loginView\$tbUserId", username)
				append("ctl00\$cphBody\$_loginView\$tbUserName", usercode)
				append("ctl00\$cphBody\$_loginView\$tbPassword", password)

				_myPrePayloadMap["LBD_VCID_c_general_login_ctl00_cphbody__loginview_captchalogin"]?.let {
					append("LBD_VCID_c_general_login_ctl00_cphbody__loginview_captchalogin", it)
				}
			}

			loginPagePOSTResponse = client.submitForm(urlLoginPage, formParameters) {
				userAgent("Mozilla/5.0 (Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0")
			}

			loginPagePOSTResponse

		}
	}


	private suspend fun afterLoginGetToTamuzTransferAspx(): HttpResponse {

		// after Login - GET to TamuzTransfer.aspx
		//		 https://e-services.clalit.co.il       ----> 	    /OnlineWeb/Services/Tamuz/TamuzTransfer.aspx

		return withContext(Dispatchers.IO) {
			afterLoginGetToTamuzTransferAspx = client.get(URL(urlTamuzTransferPage)) {
				userAgent("Mozilla/5.0 (Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0")
			}
			afterLoginGetToTamuzTransferAspx
		}

	}

	private suspend fun afterLoginGetToTamuzTransferWithAuth(): HttpResponse {
		// after Login - GET to TamuzTransfer.aspx with PAYLOAD -- attach userAgent - automatically assigns the payload
		//		 https://e-services.clalit.co.il       ----> 	    /OnlineWeb/Services/Tamuz/TamuzTransferContentByService.aspx?MethodName=TransferWithAuth

		return withContext(Dispatchers.IO) {
			afterLoginGetToTamuzTransferWithAuth = client.get(URL(urlTamuzContentByService)) {
				userAgent("Mozilla/5.0 (Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0")
			}
			afterLoginGetToTamuzTransferWithAuth
		}
	}


	private suspend fun postToIFrameLogin(): HttpResponse {
		//      handle POST to IFrame at Zimunet/Visits/Login -> submitForm()
		//		https://e-services.clalit.co.il 		----->    	/Zimunet/Visits/Login

		return withContext(Dispatchers.IO) {
			val mapOfInputsAndValues = mutableMapOf<String, String>()
			val regex = Regex("<input.*name=\"(.*?)\".*value=\"(.*)\"")

			afterLoginGetToTamuzTransferWithAuth.let { regex.findAll(it.bodyAsText(Charsets.UTF_8)) }.forEach {
				mapOfInputsAndValues[it.groups[1]?.value.toString()] = it.groups[2]?.value.toString()
			}

			postToIFrameLogin = client.submitForm(
				url = urlZimunetVisitsLogin,
				formParameters = Parameters.build {
					append("SessionID", mapOfInputsAndValues.getValue("SessionID"))
					append("PatientID", mapOfInputsAndValues.getValue("PatientID"))
					append("MethodName", mapOfInputsAndValues.getValue("MethodName"))
					append("DeviceType", mapOfInputsAndValues.getValue("DeviceType"))
					append("Language", mapOfInputsAndValues.getValue("Language"))
					append("PlatformType", mapOfInputsAndValues.getValue("PlatformType"))
				}
			) {
				userAgent("Mozilla/5.0 (Android 10; Mobile; rv:88.0) Gecko/88.0 Firefox/88.0")
			}

			postToIFrameLogin
		}
	}

	private suspend fun assertPostToIFrameLoginViaLocationHeader(): HttpResponse {
		//      assert Response To IFrame login with header: "Location="
		//		https://e-services.clalit.co.il 		----->    	/Zimunet/Visits

		return withContext(Dispatchers.IO) {
			assertPostToIFrameLoginViaLocationHeader = client.get(urlZimunetVisits)
			assertPostToIFrameLoginViaLocationHeader
		}
	}


}


