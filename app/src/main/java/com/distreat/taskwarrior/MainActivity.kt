package com.distreat.taskwarrior

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.datepicker.MaterialDatePicker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class UnsupportedPlatformException : Exception("No Taskwarrior binary for the target device")

data class CommandResult(val output: String, val exitCode: Int)
data class TaskwarriorFiles(
    val applicationDirectory: File,
    val taskRcFile: File,
    val dataDirectory: File
)

class MainActivity : AppCompatActivity() {
    companion object {
        val LOG_TAG: String = R.string.app_name.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initializeApplicationDirectory()
        findViewById<AppCompatImageView>(R.id.button_execute).setOnClickListener {
            handleCommandInput()
        }
        findViewById<AppCompatImageView>(R.id.button_calendar).setOnClickListener {
            promptCalendarDate()
        }
        findViewById<EditText>(R.id.input_command).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                handleCommandInput()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    private fun getTaskwarriorFiles(): TaskwarriorFiles {
        var applicationDirectory = getExternalFilesDir(null)
        if (applicationDirectory === null) {
            applicationDirectory = filesDir
        }
        return TaskwarriorFiles(
            applicationDirectory = applicationDirectory,
            taskRcFile = File(applicationDirectory, "taskrc"),
            dataDirectory = File(applicationDirectory, "data"),
        )
    }

    private fun initializeApplicationDirectory() {
        val taskwarriorFiles = getTaskwarriorFiles()
        if (!taskwarriorFiles.taskRcFile.exists()) {
            taskwarriorFiles.taskRcFile.createNewFile()
        }
        if (!taskwarriorFiles.dataDirectory.exists()) {
            taskwarriorFiles.dataDirectory.mkdir()
        }
    }

    private fun executeTaskwarriorCommand(taskwarriorArguments: List<String>): CommandResult {
        val executablePath = File("${applicationInfo.nativeLibraryDir}/libtask.so")
        if (!executablePath.isFile) throw UnsupportedPlatformException()
        Log.d(LOG_TAG, "Found existing taskwarrior binary: ${executablePath.absolutePath}")
        val arguments: MutableList<String> = ArrayList()
        arguments.add(executablePath.absolutePath)
        arguments.add("rc.verbose=no")
        arguments.add("rc.confirmation=no")
        arguments.addAll(taskwarriorArguments)
        val processBuilder = ProcessBuilder(arguments)
        processBuilder.redirectErrorStream(true)
        processBuilder.directory(cacheDir)
        processBuilder.environment()["HOME"] = cacheDir.absolutePath
        val taskwarriorFiles = getTaskwarriorFiles()
        processBuilder.environment()["TASKDATA"] = taskwarriorFiles.dataDirectory.absolutePath
        processBuilder.environment()["TASKRC"] = taskwarriorFiles.taskRcFile.absolutePath
        Log.d(LOG_TAG, "Executing Taskwarrior command: $arguments")
        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return CommandResult(output = stdout, exitCode = exitCode)
    }

    private fun handleCommandInput() {
        val editTextCommandInput = this.findViewById<EditText>(R.id.input_command)
        val scrollViewVertical = this.findViewById<ScrollView>(R.id.scroll_vertical)
        val textViewCommandOutput = this.findViewById<TextView>(R.id.text_command_output)

        try {
            val commandArguments = editTextCommandInput.text.split(Regex("""\s+"""))
            val commandResult = executeTaskwarriorCommand(commandArguments)

            textViewCommandOutput.append("$ task ${editTextCommandInput.text}\n")
            textViewCommandOutput.append("${commandResult.output}\n")
            editTextCommandInput.setText("")
            scrollViewVertical.post {
                run {
                    scrollViewVertical.scrollTo(0, scrollViewVertical.bottom)
                }
            }
        } catch (_: UnsupportedPlatformException) {
            textViewCommandOutput.append(getString(R.string.message_unsupported_abi))
            return
        }
    }

    private fun promptCalendarDate() {
        val editTextCommandInput = this.findViewById<EditText>(R.id.input_command)
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Insert calendar date")
            .build()
        datePicker.show(supportFragmentManager, "DATE")
        datePicker.addOnPositiveButtonClickListener {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.format(it)
            editTextCommandInput.append(date)
        }
    }
}
