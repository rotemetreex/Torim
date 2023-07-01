package com.rotemyanco.torim.adapters

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.rotemyanco.torim.models.Appointment
import com.rotemyanco.torim.models.AppointmentUIModel
import com.rotemyanco.torim.R
import com.rotemyanco.torim.databinding.AppointmentCardBinding


class AppointmentsAdapter(
	private val appointmentsUIModelList: List<AppointmentUIModel> = listOf(),
	private val onItemChecked: OnItemCheckedListener<Appointment>,
	private val mClickListener: OnClickListener
) :
	Adapter<AppointmentsAdapter.AppointmentVH>() {

	private lateinit var context: Context
	private lateinit var sharedPrefs: SharedPreferences
	private lateinit var sharedPrefsCheckedVisitsString: String

	private lateinit var mUniqueVisitId: String

	private val userLoginDataTag = "USER_LOGIN_DATA"

	private var switchState = false
	private var switchColor = 0
	private lateinit var switchText: String

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentVH {
		context = parent.context
		val appointmentCardBinding = AppointmentCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return AppointmentVH(appointmentCardBinding, appointmentsUIModelList, onItemChecked, mClickListener)
	}

	override fun onBindViewHolder(holder: AppointmentVH, position: Int) {
		val appointment = appointmentsUIModelList[position].appointment

		val mSpannableDrName = SpannableString(appointment.doctorName)
		val specialty = appointment.professionName
		val visitDetails = appointment.visitDateTime

		switchState = appointmentsUIModelList[position].switchState
		switchColor = appointmentsUIModelList[position].switchColor
		switchText = appointmentsUIModelList[position].switchText

		mSpannableDrName.setSpan(UnderlineSpan(), 0, mSpannableDrName.length, 0)

		with(holder) {
			with(_appointmentCardBinding) {


				tvDrName.text = mSpannableDrName
				tvDrProfession.text = specialty
				tvVisitDateTime.text = visitDetails

				try {
					sharedPrefs = context.getSharedPreferences(userLoginDataTag, Context.MODE_PRIVATE)
					sharedPrefsCheckedVisitsString = sharedPrefs.getString("checked_visits", "defaultCheckedVisitsString").toString()

					mUniqueVisitId = "${appointment.visitDate.replace(".", "")}_${
						appointment.doctorName
							.replace(" ", "")
							.replace("\"", "")
							.replace("\'", "")
					}"

					if (sharedPrefsCheckedVisitsString.contains(mUniqueVisitId)) {
						appointment.switchState = true
						appointment.switchColor = Color.parseColor("#53b882").toString()
						holder.itemView.setBackgroundColor(Color.parseColor("#53b882"))
						appointment.switchText = context.getString(R.string.switch_ON_state)
						switch1.isChecked = true
					} else {
						appointment.switchState = false
						appointment.switchText = context.getString(R.string.switch_OFF_state)
						switch1.isChecked = false
					}

				} catch (e: Exception) {
					Toast.makeText(context, "ERROR ===>> Message ${e.message}", Toast.LENGTH_SHORT).show()
				}

				//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

				try {
					val containsVisit = sharedPrefsCheckedVisitsString.contains(mUniqueVisitId)
					appointment.switchState = containsVisit
					appointment.switchText = if (containsVisit) {
//						switch1.isChecked = true
						Color.parseColor("#53b882").toString()
						holder.itemView.setBackgroundColor(Color.parseColor("#53b882"))
						context.getString(R.string.switch_ON_state)
					} else {
						switch1.isChecked = false
						context.getString(R.string.switch_OFF_state)
					}
				} catch (e: Exception) {
					Toast.makeText(context, "ERROR ===>> Message ${e.message}", Toast.LENGTH_SHORT).show()
				}


				//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

				switch1.setOnCheckedChangeListener { buttonView, isChecked ->
					onItemChecked.onItemChecked(appointment, isChecked)
					if (isChecked) {
						buttonView.text = context.getString(R.string.switch_ON_state)
						holder.itemView.setBackgroundColor(Color.parseColor("#53b882"))
					} else {
						buttonView.text = context.getString(R.string.switch_OFF_state)
						holder.itemView.setBackgroundColor(Color.parseColor("#bcbec4"))
					}
				}

				//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

				tvDrName.setOnClickListener {
					mClickListener.onClick(it)
				}
			}
		}
	}

	override fun getItemCount(): Int = appointmentsUIModelList.size

	class AppointmentVH(
		val _appointmentCardBinding: AppointmentCardBinding,
		private val appointmentsUIModelList: List<AppointmentUIModel>,
		private val onItemChecked: OnItemCheckedListener<Appointment>,
		private val mClickListener: OnClickListener
	) : ViewHolder(_appointmentCardBinding.root) {
		init {
			_appointmentCardBinding.tvDrName.setOnClickListener {
				mClickListener.onClick(it)
			}

			_appointmentCardBinding.switch1.setOnCheckedChangeListener { _, isChecked ->
				val appointment = appointmentsUIModelList[adapterPosition].appointment
				onItemChecked.onItemChecked(appointment, isChecked)
			}
		}
	}

}


