package com.rotemyanco.torim.adapters


interface OnItemCheckedListener<T> {
	fun onItemChecked(item: T, isChecked: Boolean)
}