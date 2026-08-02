package fr.acinq.phoenix.utils

class DateUtils {

    companion object {
        const val SECOND_IN_MILLIS: Long = 1000

        const val MINUTE_IN_MILLIS: Long = SECOND_IN_MILLIS * 60

        const val HOUR_IN_MILLIS: Long = MINUTE_IN_MILLIS * 60

        const val DAY_IN_MILLIS: Long = HOUR_IN_MILLIS * 24

        const val WEEK_IN_MILLIS: Long = DAY_IN_MILLIS * 7

    }

}