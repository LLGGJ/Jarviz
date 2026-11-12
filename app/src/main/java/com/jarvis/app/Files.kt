package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/** Navegar pastas, ler arquivos de texto e extrair zips (com proteção contra zip-slip e zip-bomb). */
class FileTools(private val ctx: Context) {
    fun hasAll(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun requestAll() {
        if (Build.VERSION.SDK_INT >= 30)
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun resolve(p: String): File {
        val base = Environment.getExternalStorageDirectory()
        val t = p.trim()
        val f = when {
            t.isEmpty() || t == "/" -> base
            t.equals("downloads", true) || t.equals("download", true) -> File(base, "Download")
            t.startsWith("/") -> File(t)
            else -> File(base, t)
        }
        return f.canonicalFile
    }

    private fun inApp(f: File) = f.path.startsWith(ctx.filesDir.canonicalFile.parentFile!!.path)

    private fun need(f: File): String? {
        if (!inApp(f) && !hasAll()) {
            requestAll()
            return "Preciso da permissão de acesso a todos os arquivos. Abri a tela de permissão: ative para o Jarvis e peça de novo."
        }
        return null
    }

    private fun human(n: Long) = when {
        n >= 1_000_000_000 -> "%.1f GB".format(n / 1e9)
        n >= 1_000_000 -> "%.1f MB".format(n / 1e6)
        n >= 1000 -> "%.0f KB".format(n / 1e3)
        else -> "$n B"
    }

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "fs_list" -> list(a)
            "fs_read" -> read(a)
            "fs_extract_zip" -> extract(a)
            else -> "Ferramenta desconhecida: $name"
        }
    } catch (e: Exception) {
        "Erro: ${e.message}"
    }

    private fun list(a: JSONObject): String {
        val f = resolve(a.optString("path"))
        need(f)?.let { return it }
        if (!f.exists()) return "Não existe: ${f.path}"
        if (f.isFile) return "${f.path} (${human(f.length())})"
        val kids = f.listFiles() ?: return "Sem acesso a esta pasta."
        val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        val sorted = kids.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        val lines = sorted.take(80).joinToString("\n") {
            if (it.isDirectory) "[pasta] ${it.name}" else "${it.name} (${human(it.length())}, ${fmt.format(Date(it.lastModified()))})"
        }
        return "${f.path} - ${kids.size} itens\n$lines" + if (kids.size > 80) "\n... (mostrando 80)" else ""
    }

    private fun read(a: JSONObject): String {
        val f = resolve(a.getString("path"))
        need(f)?.let { return it }
        if (!f.isFile) return "Não é um arquivo: ${f.path}"
        val buf = ByteArray(60_000)
        val n = f.inputStream().use { it.read(buf) }
        if (n <= 0) return "(arquivo vazio)"
        if ((0 until minOf(n, 4096)).any { buf[it].toInt() == 0 }) return "Arquivo binário (${human(f.length())}); não dá para ler como texto."
        return String(buf, 0, n).take(20_000)
    }

    private fun extract(a: JSONObject): String {
        val zip = resolve(a.getString("path"))
        need(zip)?.let { return it }
        if (!zip.isFile) return "Não é um arquivo: ${zip.path}"
        val destArg = a.optString("dest")
        var dest = if (destArg.isBlank()) File(zip.parentFile, zip.nameWithoutExtension) else resolve(destArg)
        var k = 1
        while (dest.exists()) dest = File(dest.parentFile, "${dest.name}_${k++}")
        dest.mkdirs()
        val root = dest.canonicalPath + File.separator
        var total = 0L; var count = 0; var blocked = 0
        val flagged = mutableListOf<String>(); val names = mutableListOf<String>()
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val out = File(dest, e.name).canonicalFile
                if (!out.path.startsWith(root)) blocked++
                else if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { o ->
                        val b = ByteArray(8192)
                        while (true) {
                            val r = zis.read(b); if (r < 0) break
                            total += r
                            if (total > 2_000_000_000L) error("Zip grande demais (possível zip bomb); parei a extração.")
                            o.write(b, 0, r)
                        }
                    }
                    count++
                    if (count > 20000) error("Muitos arquivos no zip; parei a extração.")
                    if (names.size < 30) names.add(e.name)
                    if (e.name.lowercase().substringAfterLast('.', "") in BAD_EXT) flagged.add(e.name)
                }
                e = zis.nextEntry
            }
        }
        val sb = StringBuilder("Extraí $count arquivos (${human(total)}) em ${dest.path}\n")
        sb.append(names.joinToString("\n"))
        if (count > names.size) sb.append("\n...")
        if (flagged.isNotEmpty()) sb.append("\n\nATENÇÃO, itens executáveis no zip: ").append(flagged.take(10).joinToString(", "))
        if (blocked > 0) sb.append("\n\nATENÇÃO: $blocked entradas tentavam escapar da pasta (zip-slip) e foram bloqueadas. Zip suspeito.")
        return sb.toString()
    }
}
