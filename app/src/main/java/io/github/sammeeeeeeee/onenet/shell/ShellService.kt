package io.github.sammeeeeeeee.onenet.shell

import android.content.Context
import android.os.Bundle
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Runs in a process started by Shizuku, with its identity (shell, or root). */
class ShellService() : IShellService.Stub() {

    // Shizuku v13+ prefers this constructor.
    @Suppress("unused")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): Bundle {
        val process = ProcessBuilder("sh", "-c", command).start()
        var out = ""
        var err = ""
        val readers = listOf(
            thread { out = process.inputStream.bufferedReader().readText() },
            thread { err = process.errorStream.bufferedReader().readText() },
        )
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        readers.forEach { it.join(1000) }
        return Bundle().apply {
            putInt(KEY_CODE, if (finished) process.exitValue() else -1)
            putString(KEY_OUT, out)
            putString(KEY_ERR, err)
        }
    }

    companion object {
        const val KEY_CODE = "code"
        const val KEY_OUT = "out"
        const val KEY_ERR = "err"
        private const val TIMEOUT_SECONDS = 10L
    }
}
