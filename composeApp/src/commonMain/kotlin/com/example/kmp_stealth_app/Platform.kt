package com.example.kmp_stealth_app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform