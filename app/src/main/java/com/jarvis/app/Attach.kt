package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max

data class Attachment(val name: String, val mime: String, val file: File, val size: Long)

/** Anexos: copia para a pasta do app e prepara o conteúdo (imagem, PDF, texto ou lista do zip) para a IA. */
object Attach {
    private val textExt = setOf("txt", "md", "json", "csv", "kt", "java", "js", "ts", "py", "html", "css", "xml", "yml", "yaml",
        "log", "sh", "gradle", "kts", "properties", "sql", "ini", "toml", "env", "c", "cpp", "h", "go", "rs", "php")

    fun import(ctx: Context, uri: Uri): Attachment? {
        val cr = ctx.contentResolver
        var name = "arquivo"
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) ?: name }
        }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val dir = File(ctx.filesDir, "inbox").apply { mkdirs() }
        val f = File(dir, "${System.currentTimeMillis()}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        cr.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return null
        return Attachment(name, mime, f, f.length())
    }

    /** (mime, base64) para imagens (reduzidas) e PDFs até 15 MB. */
    fun media(a: Attachment): Pair<String, String>? {
        if (a.mime.startsWith("image/")) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(a.file.path, o)
            var s = 1
            while (max(o.outWidth, o.outHeight) / s > 1600) s *= 2
            val bmp = BitmapFactory.decodeFile(a.file.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            return "image/jpeg" to Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }
        if (a.mime == "application/pdf" && a.size <= 15_000_000L)
            return "application/pdf" to Base64.encodeToString(a.file.readBytes(), Base64.NO_WRAP)
        return null
    }

    /** Texto descrevendo o anexo para a IA. */
    fun describe(a: Attachment): String {
        val head = "[Anexo: ${a.name} (${a.mime}, ${a.size / 1024} KB) salvo em ${a.file.path}]"
        val ext = a.name.substringAfterLast('.', "").lowercase()
        return when {
            a.mime.startsWith("image/") || a.mime == "application/pdf" -> head
            ext == "zip" || a.mime.contains("zip") -> head + "\n" + zipList(a)
            a.mime.startsWith("text/") || ext in textExt -> {
                val buf = ByteArray(40_000)
                val n = a.file.inputStream().use { it.read(buf) }
                head + "\n" + (if (n > 0) String(buf, 0, n).take(20_000) else "")
            }
            else -> head + "\n(Formato binário; use as ferramentas de arquivos se precisar.)"
        }
    }

    private fun zipList(a: Attachment): String = try {
        ZipFile(a.file).use { z ->
            val names = z.entries().asSequence().take(80).map { it.name }.toList()
            val bad = names.filter { it.lowercase().substringAfterLast('.', "") in BAD_EXT }
            "Conteúdo do zip (até 80 itens):\n" + names.joinToString("\n") +
                (if (bad.isNotEmpty()) "\nATENÇÃO, executáveis no zip: ${bad.joinToString(", ")}" else "") +
                "\nPara extrair, use fs_extract_zip com o caminho acima."
        }
    } catch (e: Exception) { "Não consegui ler o zip (${e.message})." }
}
