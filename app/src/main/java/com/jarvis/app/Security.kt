package com.jarvis.app

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.util.zip.ZipFile

data class Finding(val level: Int, val title: String, val detail: String) // 3 alto, 2 médio, 1 info

val BAD_EXT = setOf("apk", "exe", "scr", "bat", "cmd", "vbs", "msi", "jar", "dex")

/** Verificação por heurísticas (não é antivírus): apps, configurações e arquivos. */
class Security(private val ctx: Context) {
    private val stores = setOf("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia",
        "com.huawei.appmarket", "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market", "com.vivo.appstore")

    fun scan(): List<Finding> = apps() + settings() + files()

    private fun granted(p: PackageInfo, perm: String): Boolean {
        val req = p.requestedPermissions ?: return false
        val fl = p.requestedPermissionsFlags ?: return false
        val i = req.indexOf(perm)
        return i >= 0 && (fl[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
    }

    private fun pkgsOf(key: String): Set<String> =
        (Settings.Secure.getString(ctx.contentResolver, key) ?: "").split(":")
            .map { it.substringBefore("/") }.filter { it.isNotBlank() }.toSet()

    @Suppress("DEPRECATION")
    private fun apps(): List<Finding> {
        val out = mutableListOf<Finding>()
        val pm = ctx.packageManager
        val acc = pkgsOf(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val listeners = pkgsOf("enabled_notification_listeners")
        val admins = try {
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).activeAdmins?.map { it.packageName }?.toSet()
        } catch (e: Exception) { null } ?: emptySet()
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        var side = 0
        for (p in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val ai = p.applicationInfo ?: continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || p.packageName == ctx.packageName) continue
            val label = pm.getApplicationLabel(ai).toString()
            val installer = try {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(p.packageName).installingPackageName
                else pm.getInstallerPackageName(p.packageName)
            } catch (e: Exception) { null }
            val sideloaded = installer == null || installer !in stores
            if (sideloaded) side++
            var score = 0
            val why = mutableListOf<String>()
            fun add(pts: Int, t: String) { score += pts; why += t }
            if (granted(p, Manifest.permission.READ_SMS) || granted(p, Manifest.permission.RECEIVE_SMS)) add(2, "lê SMS")
            if (granted(p, Manifest.permission.SEND_SMS)) add(2, "envia SMS")
            val overlay = try {
                ao.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ai.uid, p.packageName) == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) { false }
            if (overlay) add(1, "desenha sobre outros apps")
            if (p.packageName in acc) add(3, "serviço de acessibilidade ativo (lê a tela e age por você)")
            if (p.packageName in listeners) add(2, "lê suas notificações")
            if (p.packageName in admins) add(3, "administrador do dispositivo")
            if (granted(p, Manifest.permission.READ_CONTACTS) && granted(p, Manifest.permission.READ_CALL_LOG)) add(1, "lê contatos e chamadas")
            val hidden = pm.getLaunchIntentForPackage(p.packageName) == null
            if (hidden && sideloaded && p.requestedPermissions?.contains(Manifest.permission.INTERNET) == true) add(2, "sem ícone no menu (app oculto)")
            if (sideloaded && Regex("(?i)google|system|android|update|security|wifi").containsMatchIn(label) &&
                !p.packageName.startsWith("com.google.") && !p.packageName.startsWith("com.android.")) add(2, "nome parecido com app do sistema")
            val lvl = when { score >= 5 -> 3; score >= 3 -> 2; else -> 0 }
            if (lvl > 0) out.add(Finding(lvl, "App: $label (${p.packageName})",
                why.joinToString("; ") + if (sideloaded) "; instalado fora da loja" else ""))
        }
        if (side > 0) out.add(Finding(1, "Apps fora de loja", "$side apps instalados fora da Play Store (normal para apps de desenvolvedor)"))
        return out
    }

    private fun settings(): List<Finding> {
        val out = mutableListOf<Finding>()
        try {
            if (Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1)
                out.add(Finding(1, "Depuração USB ativa", "Comum para quem programa; desative se não estiver usando."))
        } catch (e: Exception) { }
        return out
    }

    private fun files(): List<Finding> {
        val out = mutableListOf<Finding>()
        val root = Environment.getExternalStorageDirectory()
        val all = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        if (!all) out.add(Finding(1, "Acesso limitado aos arquivos", "Sem a permissão de todos os arquivos não escaneei as pastas."))
        if (!root.canRead() || !all) return out
        val dbl = Regex("\\.(pdf|jpe?g|png|docx?|xlsx?|txt|mp4|mp3|zip)\\.(apk|exe|scr|bat|cmd|js|vbs|sh|jar|dex)$")
        val apks = mutableListOf<String>(); val doubles = mutableListOf<String>()
        val exes = mutableListOf<String>(); val zips = mutableListOf<String>()
        var count = 0; var zipChecked = 0
        for (f in root.walkTopDown().maxDepth(6).onEnter { !(it.parentFile == root && it.name == "Android") }) {
            if (++count > 20000) break
            if (!f.isFile) continue
            val n = f.name.lowercase(); val ext = n.substringAfterLast('.', "")
            val rel = f.relativeTo(root).path
            when {
                dbl.containsMatchIn(n) -> doubles.add(rel)
                ext == "apk" -> apks.add(rel)
                ext in BAD_EXT -> exes.add(rel)
                ext == "zip" && zipChecked < 40 && f.length() < 300_000_000L -> {
                    zipChecked++
                    try {
                        ZipFile(f).use { z ->
                            if (z.entries().asSequence().take(500).any { it.name.lowercase().substringAfterLast('.', "") in BAD_EXT }) zips.add(rel)
                        }
                    } catch (e: Exception) { }
                }
            }
        }
        if (doubles.isNotEmpty()) out.add(Finding(3, "Arquivos com extensão dupla (truque comum de malware)", doubles.take(8).joinToString("\n")))
        if (apks.isNotEmpty()) out.add(Finding(2, "${apks.size} APKs soltos no armazenamento", apks.take(8).joinToString("\n")))
        if (exes.isNotEmpty()) out.add(Finding(2, "${exes.size} executáveis (exe/bat/jar...)", exes.take(8).joinToString("\n")))
        if (zips.isNotEmpty()) out.add(Finding(2, "Zips contendo APK ou executável", zips.take(8).joinToString("\n")))
        return out
    }

    fun report(f: List<Finding>): String {
        val real = f.filter { it.level >= 2 }.sortedByDescending { it.level }
        val info = f.filter { it.level == 1 }
        val sb = StringBuilder()
        if (real.isEmpty()) sb.append("Nada suspeito encontrado pelas minhas verificações.\n")
        real.forEach { sb.append(if (it.level >= 3) "ALTO: " else "MÉDIO: ").append(it.title).append("\n").append(it.detail).append("\n\n") }
        info.forEach { sb.append("Info: ").append(it.title).append(" - ").append(it.detail).append("\n") }
        sb.append("\nIsto é uma verificação por heurísticas e pode ter falsos positivos; não substitui um antivírus.")
        return sb.toString().take(6000)
    }
}
