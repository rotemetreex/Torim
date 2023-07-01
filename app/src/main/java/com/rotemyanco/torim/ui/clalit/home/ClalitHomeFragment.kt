package com.rotemyanco.torim.ui.clalit.home

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.rotemyanco.torim.adapters.AppointmentsAdapter
import com.rotemyanco.torim.adapters.OnItemCheckedListener
import com.rotemyanco.torim.databinding.FragmentClalitHomeBinding

import com.rotemyanco.torim.models.Appointment
import com.rotemyanco.torim.models.AppointmentUIModel
import com.rotemyanco.torim.services.AppointmentWorker
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class ClalitHomeFragment : Fragment() {

	private var _homeBinding: FragmentClalitHomeBinding? = null
	private val binding get() = _homeBinding!!
	private lateinit var clalitSharedViewModel: ClalitSharedViewModel

	private lateinit var workManager: WorkManager

	private var oneTimeWorkRequestUUID: UUID? = null
	private var periodicWorkRequestUUID: UUID? = null
	private val uniqueOneTimeWorkTag = "ONE_TIME"

	private val payload = " SWITCH_STATE_CHANGE"

	private var appointments = mutableListOf<Appointment>()
	private var appointmentsUIModelList = mutableListOf<AppointmentUIModel>()
	private lateinit var appointmentsAdapter: AppointmentsAdapter

	// list of visit id's in string format -
	// used to compare and update the recycler cards to toggle color and text,
	// when the switch state is changed
	private var checkedUniqueStringVisitIDList = mutableListOf<String>()
	private var checkedIdList = mutableListOf<String>()

	private val USER_LOGIN_DATA_TAG = "USER_LOGIN_DATA"
	private val USER_IS_LOGIN_TAG = "USER_IS_LOGIN_TAG"
	private val USERNAME_TAG = "USERNAME"
	private val USERCODE_TAG = "USERCODE"
	private val PASSWORD_TAG = "PASSWORD"

	private lateinit var sharedPrefs: SharedPreferences

	private lateinit var args: Bundle

	private var runOneTimeRequest: Boolean? = null
	private var isLogin: Boolean? = null
	private lateinit var username: String
	private lateinit var usercode: String
	private lateinit var password: String

	private lateinit var uniqueVisitId: String

	var checkedVisits = ""


	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_homeBinding = FragmentClalitHomeBinding.inflate(inflater, container, false)
		val root: View = binding.root
		clalitSharedViewModel = ViewModelProvider(this)[ClalitSharedViewModel::class.java]


		sharedPrefs = requireContext().getSharedPreferences(USER_LOGIN_DATA_TAG, Context.MODE_PRIVATE)
		isLogin = sharedPrefs.getBoolean(USER_IS_LOGIN_TAG, false)

		username = sharedPrefs.getString("USERNAME", "") ?: args.getString("USERNAME", "")
		usercode = sharedPrefs.getString("USERCODE", "") ?: args.getString("USERCODE", "")
		password = sharedPrefs.getString("PASSWORD", "") ?: args.getString("PASSWORD", "")

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		// a boolean stored in sharedPrefs --> to run the oneTimeRequest:
		runOneTimeRequest = sharedPrefs.getBoolean("runOneTimeRequest", false)

		try {
			args = this.requireArguments()
		} catch (e: Exception) {
			Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
		}

		// getting the list of checked visits from the shared preferences and adding them to the list
		checkedVisits = sharedPrefs.getString("checked_visits", "").toString()

		// getting the list of checked visits from the shared preferences and adding them to the list
		checkedUniqueStringVisitIDList.addAll(checkedVisits.split(",") as MutableList<String>)

		if (checkedIdList.size > 0 && checkedIdList.contains(uniqueVisitId)) {
			appointmentsUIModelList.forEach {
				val tempUniqueVisitId =
					"${it.appointment.visitDate.replace(".", "")}_${it.appointment.doctorName.replace(" ", "").replace("\"", "").replace("\'", "")},"

				if (tempUniqueVisitId == uniqueVisitId) {
					it.switchState = true
					it.switchColor = Color.parseColor("#53b882")
					it.switchText = requireContext().getString(com.rotemyanco.torim.R.string.switch_ON_state)

				}
			}
		}

		if (runOneTimeRequest as Boolean) {
			workManager = WorkManager.getInstance(requireContext())
			createOneTimeWorkRequest()
		}

		appointmentsAdapter = AppointmentsAdapter(appointmentsUIModelList,
			onItemChecked = object : OnItemCheckedListener<Appointment> {
				override fun onItemChecked(item: Appointment, isChecked: Boolean) {
					with(clalitSharedViewModel) {
						onSwitchStateChanged(isChecked)
						viewModelScope.launch {

							checkedVisits = sharedPrefs.getString("checked_visits", "").toString()

							uniqueVisitId = "${item.visitDate.replace(".", "")}_${item.doctorName.replace(" ", "").replace("\"", "").replace("\'", "")}"

							if (checkedVisits.contains(uniqueVisitId)) {
								if (!isChecked) {

									checkedVisits = sharedPrefs.getString("checked_visits", "").toString()

									stopWorkManager()
									periodicWorkRequestUUID = null

									if (checkedVisits.contains(uniqueVisitId)) {
										checkedUniqueStringVisitIDList = checkedVisits.split(",").toMutableList()
										(checkedUniqueStringVisitIDList as MutableList<String>?)?.removeAll { it == uniqueVisitId.replace(",", "").trim() }
										checkedVisits = checkedUniqueStringVisitIDList.joinToString(separator = ",")

										/////////////////////////////////////////////////////////////
										sharedPrefs.edit()
											.remove("checked_visits")
											.apply()
										/////////////////////////////////////////////////////////////

										sharedPrefs.edit()
											.putString("checked_visits", checkedVisits)
											.apply()
									}

								} else return@launch
							}

							if (isChecked) {

								val inputData = Data.Builder()
									.putString("appointmentId", item.dataId)
									.putInt("workRequestType", 2)
									.putString(USERNAME_TAG, username)
									.putString(USERCODE_TAG, usercode)
									.putString(PASSWORD_TAG, password)
									.build()

								sharedPrefs.edit()
									.putString("checked_visits", sharedPrefs.getString("checked_visits", "").toString() + "," + uniqueVisitId)
									.apply()

								postDataToAppointmentWorker(inputData)

								val periodicWorkRequest = PeriodicWorkRequestBuilder<AppointmentWorker>(
									repeatInterval = 3, repeatIntervalTimeUnit = TimeUnit.HOURS
								)

									.setBackoffCriteria(
										BackoffPolicy.LINEAR,
										PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
										TimeUnit.MILLISECONDS
									)
									.addTag("worker_output_periodic_request")
									.setInputData(inputData)
									.build()

								startWorkManager(periodicWorkRequest)
							}
						}
					}
				}
			}
		) { v ->
			if (v != null) {
				Toast.makeText(requireContext(), "mClickListener:       ==  this view: ${v.rootView}            was CLICKED!!", Toast.LENGTH_SHORT).show()
			}
		}

		with(binding) {
			rcvAppointments.adapter = appointmentsAdapter
			rcvAppointments.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
		}
		return root
	}


	private fun createOneTimeWorkRequest() {
		clalitSharedViewModel.viewModelScope.launch {
			val inputData = Data.Builder()
				.putInt("workRequestType", 1)
				.putString("USERNAME", username)
				.putString("USERCODE", usercode)
				.putString("PASSWORD", password)
				.build()
			val work = OneTimeWorkRequestBuilder<AppointmentWorker>()
				.setInputData(inputData)
				.build()

			oneTimeWorkRequestUUID = work.id

			clalitSharedViewModel.startWorkManager(requireContext(), uniqueOneTimeWorkTag, work)
			oneTimeWorkRequestUUID = work.id
		}
	}

	private fun observeWork(uuid: UUID) {
		var temp: List<AppointmentUIModel>
		workManager.getWorkInfoByIdLiveData(uuid).observe(viewLifecycleOwner) {

			if (it != null && it.state.isFinished && it.state == WorkInfo.State.SUCCEEDED) {
				clalitSharedViewModel.viewModelScope.launch {
					temp = clalitSharedViewModel.mapToAppointmentUIModelList(it.outputData.keyValueMap)
					appointmentsUIModelList.addAll(temp)
					appointmentsAdapter.notifyItemRangeChanged(0, appointmentsUIModelList.size)
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		oneTimeWorkRequestUUID?.let { observeWork(it) }
		clalitSharedViewModel.switchState.observe(viewLifecycleOwner) { newState ->
			sharedPrefs.edit().putBoolean("switchState", newState).apply()
			for (visit in appointments) {
				if (checkedIdList.contains(visit.dataId)) {
					appointmentsAdapter.notifyItemChanged(appointments.indexOf(visit), payload)
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_homeBinding = null
	}

}

