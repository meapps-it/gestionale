package com.meapps.gestionale.util

import com.meapps.gestionale.data.EntryEntity
import com.meapps.gestionale.data.FrequencyUnit
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val locale = Locale.ITALY
fun euro(cents: Long): String = NumberFormat.getCurrencyInstance(locale).format(cents / 100.0)
fun date(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
fun occurrenceDates(item: EntryEntity, from: LocalDate, to: LocalDate): Sequence<LocalDate> = sequence {
    var date = LocalDate.ofEpochDay(item.nextDate)
    while (date.isBefore(from)) date = advance(date, item.frequencyCount, item.frequencyUnit)
    while (!date.isAfter(to)) { yield(date); date = advance(date, item.frequencyCount, item.frequencyUnit) }
}
private fun advance(date: LocalDate, count: Int, unit: FrequencyUnit) = when(unit) {
    FrequencyUnit.DAYS -> date.plusDays(count.toLong())
    FrequencyUnit.WEEKS -> date.plusWeeks(count.toLong())
    FrequencyUnit.MONTHS -> date.plusMonths(count.toLong())
    FrequencyUnit.YEARS -> date.plusYears(count.toLong())
}
