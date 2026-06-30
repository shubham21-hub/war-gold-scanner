package com.wgs.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wgs.app.data.db.ScanRecord
import java.io.File
import java.io.FileWriter
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportHelper @Inject constructor() {

    private val nf = NumberFormat.getNumberInstance(Locale.US)

    fun buildCsv(records: List<ScanRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("Base,Player,Gold,Date")
        records.forEach { r ->
            val name = r.playerName.replace(",", " ")
            sb.appendLine("${r.baseNumber},${name},${r.goldValue},${r.date}")
        }
        return sb.toString()
    }

    fun buildPlainText(records: List<ScanRecord>): String {
        val sb = StringBuilder()
        records.forEach { r ->
            sb.appendLine("${r.baseNumber}. ${r.playerName} - ${nf.format(r.goldValue)}")
        }
        return sb.toString()
    }

    fun writeCsvFile(context: Context, records: List<ScanRecord>, warId: String): Uri {
        val csv = buildCsv(records)
        val file = File(context.cacheDir, "wgs_${warId}.csv")
        FileWriter(file).use { it.write(csv) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(context: Context, uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "War Gold Scanner Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyToClipboard(context: Context, records: List<ScanRecord>) {
        val text = buildPlainText(records)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("WGS Records", text)
        clipboard.setPrimaryClip(clip)
    }
}
