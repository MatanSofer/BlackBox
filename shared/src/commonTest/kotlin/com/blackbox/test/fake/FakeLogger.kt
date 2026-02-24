package com.blackbox.test.fake

import com.blackbox.domain.util.BlackBoxLogger

/**
 * No-op logger for unit tests.
 */
class FakeLogger : BlackBoxLogger {
    override fun v(tag: String, message: String) {}
    override fun d(tag: String, message: String) {}
    override fun i(tag: String, message: String) {}
    override fun w(tag: String, message: String, throwable: Throwable?) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}
