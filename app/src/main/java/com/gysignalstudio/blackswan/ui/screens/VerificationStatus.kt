package com.gysignalstudio.blackswan.ui.screens

import androidx.compose.ui.graphics.Color

const val VERIFICATION_CONFIRMED = "CONFIRMED"
const val VERIFICATION_REPORTED = "REPORTED"
const val VERIFICATION_DISPUTED = "DISPUTED"

fun verificationLabel(status: String, isUk: Boolean): String = when (status.uppercase()) {
    VERIFICATION_CONFIRMED -> if (isUk) "Підтверджено" else "Confirmed"
    VERIFICATION_DISPUTED -> if (isUk) "Спірне" else "Disputed"
    else -> if (isUk) "Повідомлено" else "Reported"
}

fun verificationColor(status: String): Color = when (status.uppercase()) {
    VERIFICATION_CONFIRMED -> Color(0xFF10B981)
    VERIFICATION_DISPUTED -> Color(0xFFF87171)
    else -> Color(0xFFFBBF24)
}
