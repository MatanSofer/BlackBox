package com.blackbox

/**
 * Platform identification for KMP expect/actual pattern.
 * Each platform provides its own implementation.
 */
expect fun getPlatformName(): String
