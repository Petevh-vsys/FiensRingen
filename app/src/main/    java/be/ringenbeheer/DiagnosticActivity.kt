package be.ringenbeheer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticActivity : Activity() {

    private val crashFile: File
        get() = File(filesDir, "crash_report.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bewaar onverwachte crashes in een lokaal bestand.
        val previousHandler =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))

                val date = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                crashFile.writeText(
                    "RINGENBEHEER - CRASHRAPPORT\n" +
                    "Datum: $date\n" +
                    "Android: ${android.os.Build.VERSION.RELEASE}\n" +
                    "Toestel: ${android.os.Build.MODEL}\n\n" +
                    writer.toString()
                )
            } catch (_: Throwable) {
                // De oorspronkelijke crash niet verbergen.
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(1)
            }
        }

        showDiagnosticScreen()
    }

    private fun showDiagnosticScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 30)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "💍 Ringenbeheer 1.2"
            textSize = 25f
            setTextColor(Color.BLACK)
        }
        layout.addView(title)

        val start = Button(this).apply {
            text = "Ringenbeheer starten"
            setOnClickListener {
                startActivity(
                    Intent(this@DiagnosticActivity, MainActivity::class.java)
                )
            }
        }
        layout.addView(start)

        val report = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
            text = if (crashFile.exists()) {
                crashFile.readText()
            } else {
                "Nog geen crash geregistreerd.\n\n" +
                "Druk op 'Ringenbeheer starten'.\n\n" +
                "Als de app afsluit, open Ringenbeheer opnieuw " +
                "om de foutmelding te bekijken."
            }
        }

        val scroll = ScrollView(this)
        scroll.addView(report)
        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copy = Button(this).apply {
            text = "Crashrapport kopiëren"
            setOnClickListener {
                val clipboard = getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "Ringenbeheer crashrapport",
                        report.text
                    )
                )

                Toast.makeText(
                    this@DiagnosticActivity,
                    "Rapport gekopieerd",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        layout.addView(copy)

        setContentView(layout)
    }
}
