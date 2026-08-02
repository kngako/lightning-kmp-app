package fr.acinq.phoenix.data

enum class UserTheme {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun safeValueOf(value: String?): UserTheme = when (value) {
            LIGHT.name -> LIGHT
            DARK.name -> DARK
            SYSTEM.name -> SYSTEM
            else -> SYSTEM
        }
    }
}
