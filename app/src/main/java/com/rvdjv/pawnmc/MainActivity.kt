package com.rvdjv.pawnmc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvSelectedFile: TextView
    private lateinit var tvOutput: TextView
    private lateinit var scrollOutput: ScrollView
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnCompile: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator

    private var selectedFilePath: String? = null
    private lateinit var config: CompilerConfig

    // file picker
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedFile(it) }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStoragePermission()) {
            appendOutput("Storage permission granted\n")
        } else {
            appendOutput("Storage permission denied\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupListeners()
        setupCompilerCallbacks()
        checkStoragePermission()
        config = CompilerConfig(this)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun initViews() {
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        tvOutput = findViewById(R.id.tvOutput)
        scrollOutput = findViewById(R.id.scrollOutput)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnCompile = findViewById(R.id.btnCompile)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnSelectFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        btnCompile.setOnClickListener {
            selectedFilePath?.let { path ->
                compileFile(path)
            }
        }
    }

    private fun setupCompilerCallbacks() {
        PawnCompiler.setOutputListener { message ->
            appendOutput(message)
        }

        PawnCompiler.setErrorListener { error ->
            val type = when {
                error.isWarning -> "Warning"
                error.isFatal -> "Fatal Error"
                error.isError -> "Error"
                else -> "Info"
            }
            val fileName = File(error.file).name
            appendOutput("$type ${String.format("%03d", error.number)}: $fileName${error.lineInfo}: ${error.message}\n")
        }
    }

    private fun checkStoragePermission() {
        if (!hasStoragePermission()) {
            AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs access to all files to compile .pwn files and write .amx output to any location.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    requestStoragePermission()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // older versions of Android do not require this permission.
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStorageLauncher.launch(intent)
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val path = getPathFromUri(uri)
        
        if (path != null && (path.endsWith(".pwn", ignoreCase = true) || path.endsWith(".p", ignoreCase = true))) {
            selectedFilePath = path
            tvSelectedFile.text = File(path).name
            btnCompile.isEnabled = true
            appendOutput("Selected: $path\n")
        } else if (path != null) {
            tvSelectedFile.text = "Invalid file type"
            btnCompile.isEnabled = false
            appendOutput("Error: Please select a .pwn file\n")
        } else {
            tvSelectedFile.text = "Cannot access file"
            btnCompile.isEnabled = false
            appendOutput("Error: Cannot access file. Make sure storage permission is granted.\n")
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }

        if (uri.scheme == "content") {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                
                if (docId.startsWith("primary:")) {
                    val relativePath = docId.removePrefix("primary:")
                    return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                }
                
                if (docId.contains(":")) {
                    val parts = docId.split(":")
                    if (parts.size == 2) {
                        val type = parts[0]
                        val path = parts[1]
                        
                        // Check common locations
                        when (type.lowercase()) {
                            "home" -> return "${Environment.getExternalStorageDirectory().absolutePath}/$path"
                            "downloads" -> return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath}/$path"
                            "raw" -> return path // Raw file path
                        }
                        
                        // Try external storage with volume name
                        val externalDirs = getExternalFilesDirs(null)
                        for (dir in externalDirs) {
                            if (dir != null) {
                                val root = dir.absolutePath.substringBefore("/Android")
                                val testPath = "$root/$path"
                                if (File(testPath).exists()) {
                                    return testPath
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // DocumentsContract failed, try other methods
            }

            try {
                contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_data")
                        if (index >= 0) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // _data column not available
            }
        }

        return null
    }

    private fun compileFile(filePath: String) {
        btnCompile.isEnabled = false
        btnSelectFile.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvOutput.text = ""

        lifecycleScope.launch {
            val options = config.buildOptions()
            val result = withContext(Dispatchers.IO) {
                PawnCompiler.compile(filePath, options)
            }

            progressBar.visibility = View.GONE
            btnCompile.isEnabled = true
            btnSelectFile.isEnabled = true

            // if (result.success) {
            //     val amxPath = filePath.replace(".pwn", ".amx", ignoreCase = true)
            //     appendOutput("Output: $amxPath\n")
            // } else {
            //     val errorCount = result.errors.count { it.isError || it.isFatal }
            //     val warningCount = result.errors.count { it.isWarning }
            //     appendOutput("Errors: $errorCount, Warnings: $warningCount\n")
            // }
        }
    }

    private fun appendOutput(text: String) {
        tvOutput.append(text)
        scrollOutput.post {
            scrollOutput.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PawnCompiler.clearCallbacks()
    }
}
