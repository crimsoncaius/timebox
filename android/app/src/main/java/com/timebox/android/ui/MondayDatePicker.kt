package com.timebox.android.ui

import android.app.DatePickerDialog
import android.content.Context
import java.time.LocalDate
import java.util.Calendar

fun showMondayDatePicker(context: Context, initial: LocalDate, onSelect: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply {
        datePicker.firstDayOfWeek = Calendar.MONDAY
    }.show()
}
