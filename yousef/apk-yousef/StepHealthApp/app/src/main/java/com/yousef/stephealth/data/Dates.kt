package com.yousef.stephealth.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/*
 * أدوات التاريخ — كل التواريخ في قاعدة البيانات نصوص ISO (yyyy-MM-dd)
 * كما يحدد Schema v1. الهاتف هو مصدر التاريخ والوقت دائمًا (البند 5).
 * minSdk = 26 لذا java.time متاحة دون أي desugaring.
 */

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** تاريخ اليوم من ساعة الهاتف بصيغة ISO */
fun todayIso(): String = LocalDate.now().format(ISO)

fun parseIso(date: String): LocalDate = LocalDate.parse(date, ISO)

fun isoOf(date: LocalDate): String = date.format(ISO)

/** عدد الأيام من from إلى to (سالب إذا كان to قبل from) */
fun daysBetween(fromIso: String, toIso: String): Long =
    ChronoUnit.DAYS.between(parseIso(fromIso), parseIso(toIso))

fun addDaysIso(date: String, days: Long): String =
    parseIso(date).plusDays(days).format(ISO)

/** أسماء الشهور الميلادية العربية */
val ARABIC_MONTHS = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

/** أحرف أيام الأسبوع — الأسبوع يبدأ بالسبت (تقويم مصر) */
val WEEKDAY_LETTERS = listOf("س", "ح", "ن", "ث", "ر", "خ", "ج")

/**
 * فهرس عمود اليوم في شبكة تبدأ بالسبت:
 * java.time: MONDAY=1 … SUNDAY=7 — السبت(6) -> 0، الأحد(7) -> 1، الاثنين(1) -> 2 … الجمعة(5) -> 6
 */
fun weekColumnIndex(iso: String): Int = ((parseIso(iso).dayOfWeek.value + 1) % 7)

/** بيانات شبكة شهر معين: (عدد الخانات الفارغة قبل اليوم 1، عدد أيام الشهر، اسم الشهر، السنة) */
data class MonthGrid(
    val leadingBlanks: Int,
    val daysInMonth: Int,
    val monthLabel: String,
    val yearLabel: Int
)

fun monthGridOf(year: Int, month: Int): MonthGrid {
    val ym = YearMonth.of(year, month)
    val first = ym.atDay(1).dayOfWeek.value
    return MonthGrid(
        leadingBlanks = (first + 1) % 7,
        daysInMonth = ym.lengthOfMonth(),
        monthLabel = ARABIC_MONTHS[month - 1],
        yearLabel = year
    )
}
