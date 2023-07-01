package com.rotemyanco.torim.ui.clalit.login

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rotemyanco.torim.R
import com.rotemyanco.torim.databinding.FragmentClalitLoginBinding
import com.rotemyanco.torim.services.AppointmentWorker
import com.rotemyanco.torim.ui.clalit.home.ClalitHomeFragment
import com.rotemyanco.torim.ui.clalit.home.ClalitSharedViewModel
import kotlinx.coroutines.launch
import java.util.*

class ClalitLoginFragment : Fragment() {

	private var _loginBinding: FragmentClalitLoginBinding? = null
	private val loginBinding get() = _loginBinding!!

	private lateinit var clalitSharedViewModel: ClalitSharedViewModel
	private lateinit var b: Bundle

	private lateinit var workManager: WorkManager
	private var oneTimeWorkRequestUUID: UUID? = null
	private val uniqueOneTimeWorkTag = "ONE_TIME"

	private var isLogin: Boolean = false
	private val USER_IS_LOGIN_TAG = "USER_IS_LOGIN_TAG"
	private val USER_LOGIN_DATA_TAG = "USER_LOGIN_DATA"
	private val USERNAME_TAG = "USERNAME"
	private val USERCODE_TAG = "USERCODE"
	private val PASSWORD_TAG = "PASSWORD"

	private var username: String = ""
	private var usercode: String = ""
	private var password: String = ""


	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_loginBinding = FragmentClalitLoginBinding.inflate(inflater, container, false)
		workManager = WorkManager.getInstance(requireContext())
		clalitSharedViewModel = ViewModelProvider(this)[ClalitSharedViewModel::class.java]
		return loginBinding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		b = Bundle()
		val shredPrefs = this.requireContext().getSharedPreferences(USER_LOGIN_DATA_TAG, Context.MODE_PRIVATE)
		isLogin = (shredPrefs?.getBoolean(USER_IS_LOGIN_TAG, false) == true)
		with(loginBinding) {
			btnLogin.setOnClickListener {
				when (shredPrefs?.getBoolean(USER_IS_LOGIN_TAG, false)!!) {
					false -> {
						username = etUsername.text.toString()
						usercode = etUsercode.text.toString()
						password = etUserPassword.text.toString()

						with(shredPrefs.edit()) {
							this?.let {
								this.putBoolean(USER_IS_LOGIN_TAG, true)

								this.putString(USERNAME_TAG, etUsername.text.toString())
								this.putString(USERCODE_TAG, etUsercode.text.toString())
								this.putString(PASSWORD_TAG, etUserPassword.text.toString())
							}?.apply()
						}

						b.apply {
							b.putBoolean(USER_IS_LOGIN_TAG, true)

							b.putString(USERNAME_TAG, username)
							b.putString(USERCODE_TAG, usercode)
							b.putString(PASSWORD_TAG, password)
						}
					}
					true -> {
						username = shredPrefs.getString(USERNAME_TAG, "").toString()
						username = shredPrefs.getString(USERCODE_TAG, "").toString()
						username = shredPrefs.getString(PASSWORD_TAG, "").toString()

						b.apply {
							b.putBoolean(USER_IS_LOGIN_TAG, true)

							b.putString(USERNAME_TAG, username)
							b.putString(USERCODE_TAG, usercode)
							b.putString(PASSWORD_TAG, password)
						}
					}
				}
				////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//					createOneTimeWorkRequest() :
				val inputData = Data.Builder()
					.putInt("workRequestType", 1)
					.putString("USERNAME", username)
					.putString("USERCODE", usercode)
					.putString("PASSWORD", password)
					.build()

				with(clalitSharedViewModel) {
					viewModelScope.launch {
						val work = OneTimeWorkRequestBuilder<AppointmentWorker>()
							.setInputData(inputData)
							.build()

						oneTimeWorkRequestUUID = work.id
						startWorkManager(requireContext(), uniqueOneTimeWorkTag, work)
						workManager.getWorkInfoByIdLiveData(oneTimeWorkRequestUUID!!).observe(viewLifecycleOwner) {
							if (it != null && it.state.isFinished && WorkInfo.State.SUCCEEDED == it.state) {
								shredPrefs.edit().putBoolean("runOneTimeRequest", true).apply()

								val clalitHomeFrag = ClalitHomeFragment()
								clalitHomeFrag.arguments = b

								requireActivity().supportFragmentManager.beginTransaction()
									.replace(R.id.fragmentContainerView, clalitHomeFrag)
									.commit()
							}
						}
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_loginBinding = null
	}

}