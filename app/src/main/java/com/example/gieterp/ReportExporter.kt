package com.example.gieterp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

object ReportExporter {
    fun exportTableToCsv(context: Context, tableLayout: TableLayout, fileName: String) {
        if (tableLayout.childCount == 0) {
            Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val file = File(reportsDir, fileName)
            val writer = FileWriter(file)

            for (i in 0 until tableLayout.childCount) {
                val row = tableLayout.getChildAt(i) as? TableRow ?: continue
                val rowCells = mutableListOf<String>()
                for (j in 0 until row.childCount) {
                    val cellView = row.getChildAt(j)
                    // If it is a button (like "View Marks" in SemesterMarks), skip it in export
                    if (cellView is android.widget.Button) {
                        continue
                    }
                    val text = if (cellView is TextView) cellView.text.toString() else ""
                    // Escape commas and quotes for CSV
                    val escapedText = text.replace("\"", "\"\"")
                    rowCells.add("\"$escapedText\"")
                }
                writer.write(rowCells.joinToString(","))
                writer.write("\n")
            }

            writer.flush()
            writer.close()

            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName.substringBeforeLast("."))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Export Report"))
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
