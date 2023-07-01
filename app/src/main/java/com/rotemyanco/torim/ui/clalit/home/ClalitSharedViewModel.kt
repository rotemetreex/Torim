package com.rotemyanco.torim.ui.clalit.home

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.rotemyanco.torim.models.AppointmentUIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.*


class ClalitSharedViewModel(application: Application) : AndroidViewModel(application) {

	private lateinit var workManager: WorkManager
	private var workRequestId: UUID? = null


	private val _switchState = MutableLiveData<Boolean>()
	private val _switchColor = MutableLiveData<Int>()
	private val _switchText = MutableLiveData<String>()

	var switchState: LiveData<Boolean> = _switchState

	private var _appointmentsUIModel: MutableLiveData<List<AppointmentUIModel>> = MutableLiveData()
	private var _appointmentData: MutableLiveData<Data> = MutableLiveData()
	private var _appointmentId: MutableLiveData<String> = MutableLiveData()

	private var _workRequest: MutableLiveData<WorkRequest> = MutableLiveData()
	var workRequest: LiveData<WorkRequest> = _workRequest


	fun onSwitchStateChanged(newState: Boolean) {
		_switchState.postValue(newState)
		_switchColor.postValue(Color.WHITE)
		_switchText.postValue("ON")
	}

	fun postDataToAppointmentWorker(inputData: Data) {
		_appointmentId.postValue(inputData.getString("appointmentId"))
		_appointmentData.postValue(inputData)
	}

	suspend fun startWorkManager(context: Context, workTag: String, workRequest: OneTimeWorkRequest?): MutableList<WorkInfo>? {
		val listOfWorkInfo = withContext(Dispatchers.IO) {
			workRequestId = workRequest?.id
			this@ClalitSharedViewModel._workRequest.postValue(workRequest)

			workManager = WorkManager.getInstance(context)
			workManager.enqueueUniqueWork(workTag, ExistingWorkPolicy.KEEP, workRequest!!).await()

			val liveDataListWorkInfo = withContext(Dispatchers.IO) {
				workManager.getWorkInfosForUniqueWorkLiveData(workTag)
			}
			liveDataListWorkInfo.value
		}
		return listOfWorkInfo
	}

	suspend fun startWorkManager(workRequest: PeriodicWorkRequest?) {
		withContext(Dispatchers.IO) {
			workRequestId = workRequest?.id
			this@ClalitSharedViewModel._workRequest.postValue(workRequest)
			workManager.enqueue(workRequest!!)
		}
	}

	suspend fun mapToAppointmentUIModelList(map: Map<String, Any>): List<AppointmentUIModel> {

		val temp = mutableListOf<AppointmentUIModel>()
		var appointmentUIModel: AppointmentUIModel

		temp.clear()
		_appointmentsUIModel.postValue(emptyList())


		return withContext(Dispatchers.IO) {
			for (i in 0 until map.keys.size) {
				appointmentUIModel = Json.decodeFromString(AppointmentUIModel.serializer(), map[i.toString()] as String)
				temp.add(appointmentUIModel)
			}
			_appointmentsUIModel.postValue(temp)

			return@withContext temp
		}
	}

	suspend fun stopWorkManager() {
		return withContext(Dispatchers.IO) {
			workRequest.let { workReqLiveData ->
				workReqLiveData.value?.let { it ->
					workManager.cancelWorkById(it.id)
					workRequestId = null
				}
			}
		}
	}

}