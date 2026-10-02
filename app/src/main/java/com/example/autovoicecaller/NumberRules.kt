package com.example.autovoicecaller

object NumberRules {
    fun normalize(raw: String): String? {
        val value = raw.trim().replace(Regex("[\\s()\\-]"), "")
        // Reject short codes, USSD, pauses, extensions and emergency numbers.
        return value.takeIf { Regex("\\+?[0-9]{7,15}").matches(it) }
    }
}
