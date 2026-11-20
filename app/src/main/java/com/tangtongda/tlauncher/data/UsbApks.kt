package com.tangtongda.tlauncher.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An installable APK found on a USB drive. */
data class UsbApk(
    val file: File,
    val drive: String,
    val label: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    /** Version already on the TV, or null if not installed. */
    val installedVersion: String?,
    val installedCode: Long,
    val icon: Bitmap?,
) {
    val sizeMb: Float get() = file.length() / 1_048_576f
    val isUpgrade: Boolean get() = installedVersion != null && versionCode > installedCode
    val isSameOrOlder: Boolean get() = installedVersion != null && versionCode <= installedCode
}

/**
 * U 盘安装: finds APK files on plugged-in USB drives and hands them to the system installer.
 *
 * APKs aren't media, so MediaStore doesn't list them; the drive is read as plain files instead.
 * - Android 10 (Sony A80J etc.): READ_EXTERNAL_STORAGE + requestLegacyExternalStorage.
 * - Android 11+: needs "所有文件访问权限" (MANAGE_EXTERNAL_STORAGE), asked for on first use.
 */
class UsbApkRepository(private val context: Context) {

    sealed interface Access {
        data object Granted : Access
        /** Ask with the runtime permission dialog (Android 10). */
        data object NeedsRuntimePermission : Access
        /** Send the user to the "all files access" settings page (Android 11+). */
        data object NeedsAllFilesAccess : Access
    }

    fun access(): Access = when {
        Build.VERSION.SDK_INT >= 30 ->
            if (Environment.isExternalStorageManager()) Access.Granted else Access.NeedsAllFilesAccess
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ->
            Access.Granted
        else -> Access.NeedsRuntimePermission
    }

    fun openAllFilesAccess() {
        val ownPage = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        val listPage = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        for (intent in listOf(ownPage, listPage)) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    /** Mounted removable drives: display name → root folder. */
    fun drives(): List<Pair<String, File>> {
        val storage = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return storage.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { v ->
                val dir = if (Build.VERSION.SDK_INT >= 30) v.directory
                else runCatching { v.javaClass.getMethod("getPathFile").invoke(v) as? File }.getOrNull()
                    ?: v.uuid?.let { File("/storage/$it") }
                dir?.takeIf { it.isDirectory }?.let { (v.getDescription(context) ?: "U 盘") to it }
            }
    }

    /** All APKs on all drives (root and up to 3 folders deep), newest first. */
    suspend fun scan(): List<UsbApk> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Pair<String, File>>()
        for ((name, root) in drives()) collect(root, name, depth = 0, out = found)
        found.mapNotNull { (drive, file) -> describe(drive, file) }
            .sortedByDescending { it.file.lastModified() }
    }

    private fun collect(dir: File, drive: String, depth: Int, out: MutableList<Pair<String, File>>) {
        if (out.size >= MAX_FILES) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in children) {
            if (out.size >= MAX_FILES) return
            val name = f.name
            when {
                f.isFile && name.endsWith(".apk", ignoreCase = true) && !name.startsWith("._") -> out += drive to f
                f.isDirectory && depth < MAX_DEPTH && !name.startsWith(".") && name !in SKIP_DIRS ->
                    collect(f, drive, depth + 1, out)
            }
        }
    }

    private fun describe(drive: String, file: File): UsbApk? {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageArchiveInfo(file.path, 0) }.getOrNull()
        val app = info?.applicationInfo?.apply {
            // Needed for loadLabel / loadIcon to read resources from the archive.
            sourceDir = file.path
            publicSourceDir = file.path
        }
        val installed = info?.packageName?.let { pkg -> runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() }
        return UsbApk(
            file = file,
            drive = drive,
            label = app?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() } ?: file.nameWithoutExtension,
            packageName = info?.packageName,
            versionName = info?.versionName,
            versionCode = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: 0,
            installedVersion = installed?.versionName ?: installed?.let { "" },
            installedCode = installed?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } ?: 0,
            icon = app?.let { runCatching { it.loadIcon(pm).toBitmap(96, 96) }.getOrNull() },
        )
    }

    /**
     * Copies the APK next to our downloads (the system installer can't read the USB drive through
     * our FileProvider) and returns the copy.
     */
    suspend fun stage(apk: UsbApk): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("没有可用的存储空间"), "usb")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // only keep the one being installed
        val copy = File(dir, "install.apk")
        apk.file.inputStream().use { input -> copy.outputStream().use { input.copyTo(it, 256 * 1024) } }
        copy
    }

    private companion object {
        const val MAX_DEPTH = 3
        const val MAX_FILES = 200
        val SKIP_DIRS = setOf("Android", "LOST.DIR", "System Volume Information", "\$RECYCLE.BIN")
    }
}
