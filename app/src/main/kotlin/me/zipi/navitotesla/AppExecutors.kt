package me.zipi.navitotesla

import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object AppExecutors {
    private val executor: Executor =
        ThreadPoolExecutor(0, 50, 30, TimeUnit.SECONDS, SynchronousQueue())

    fun execute(command: Runnable) {
        executor.execute(command)
    }
}