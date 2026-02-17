package com.blackbox

/**
 * Simple greeting class to verify shared module compilation.
 * Will be replaced with actual domain code in subsequent steps.
 */
class Greeting {
    fun greet(): String = "BlackBox running on ${getPlatformName()}"
}
