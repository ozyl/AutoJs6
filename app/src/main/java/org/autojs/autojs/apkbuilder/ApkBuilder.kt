package org.autojs.autojs.apkbuilder

import android.content.Context
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.reandroid.arsc.chunk.TableBlock
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.io.Zip
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.BuildInfo
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.EncryptedScriptFileHeader.writeHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.util.MD5Utils
import com.tencent.apphelper.BuildConfig
import com.tencent.apphelper.R
import pxb.android.StringItem
import pxb.android.axml.AxmlWriter
import zhao.arsceditor.ResDecoder.ARSCDecoder
import java.io.*
import java.util.concurrent.Callable

/**
 * Created by Stardust on Oct 24, 2017.
 * Modified by SuperMonster003 as of Jul 8, 2022.
 */
open class ApkBuilder(apkInputStream: InputStream?, private val outApkFile: File, private val workspacePath: String) {

    private var mProgressCallback: ProgressCallback? = null
    private var mArscPackageName: String? = null
    private var mManifestEditor: ManifestEditor? = null
    private var mInitVector: String? = null
    private var mKey: String? = null

    private lateinit var mAppConfig: AppConfig

    private val mApkPackager = ApkPackager(apkInputStream, workspacePath)

    private val mAssetManager: AssetManager by lazy { globalContext.assets }
    private val mNativePath by lazy { File(globalContext.cacheDir, "native-lib").path }

    private var mLibsIncludes = Libs.DEFALUT_INCLUDES.toMutableList()
    private var mAssetsFileIncludes = Assets.File.DEFAULT_INCLUDES.toMutableList()
    private var mAssetsDirExcludes = Assets.Dir.DEFAULT_EXCLUDES.toMutableList()

    private val mManifestFile
        get() = File(workspacePath, "AndroidManifest.xml")

    private val mResourcesArscFile
        get() = File(workspacePath, "resources.arsc")

    init {
        PFiles.ensureDir(outApkFile.path)
    }

    fun setProgressCallback(callback: ProgressCallback?) = also { mProgressCallback = callback }

    @Throws(IOException::class)
    fun prepare() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onPrepare(this) } }
        File(workspacePath).mkdirs()
        mApkPackager.unzip()
        unzipLibs()
    }

    @Throws(IOException::class)
    fun setScriptFile(path: String?) = also {
        path?.let {
            when {
                PFiles.isDir(it) -> copyDir(it, "assets/project/")
                else -> replaceFile(it, "assets/project/main.js")
            }
        }
    }

    @Throws(IOException::class)
    @Suppress("SameParameterValue")
    private fun copyDir(srcPath: String, relativeDestPath: String) {
        copyDir(File(srcPath), relativeDestPath)
    }

    @Throws(IOException::class)
    fun copyDir(srcFile: File, relativeDestPath: String) {
        val destDirFile = File(workspacePath, relativeDestPath).apply { mkdir() }
        srcFile.listFiles()?.forEach { srcChildFile ->
            if (srcChildFile.isFile) {
                if (srcChildFile.name.endsWith(".js")) {
                    encryptToDir(srcChildFile, destDirFile)
                } else {
                    srcChildFile.copyTo(File(destDirFile, srcChildFile.name), true)
                }
            } else {
                if (!mAppConfig.ignoredDirs.contains(srcChildFile)) {
                    copyDir(srcChildFile, PFiles.join(relativeDestPath, srcChildFile.name + File.separator))
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun encrypt(srcFile: File, destFile: File) {
        destFile.outputStream().use { os ->
            writeHeader(os, JavaScriptFileSource(srcFile).executionMode.toShort())
            AdvancedEncryptionStandard(mKey!!.toByteArray(), mInitVector!!)
                .encrypt(PFiles.readBytes(srcFile.path))
                .let { bytes -> os.write(bytes) }
        }
    }

    private fun encryptToDir(srcFile: File, destDirFile: File) {
        val destFile = File(destDirFile, srcFile.name)
        encrypt(srcFile, destFile)
    }

    @Throws(IOException::class)
    fun replaceFile(srcPath: String, relativeDestPath: String) = replaceFile(File(srcPath), relativeDestPath)

    @Throws(IOException::class)
    fun replaceFile(srcFile: File, relativeDestPath: String) = also {
        val destFile = File(workspacePath, relativeDestPath)
        if (destFile.name.endsWith(".js")) {
            encrypt(srcFile, destFile)
        } else {
            srcFile.copyTo(destFile, true)
        }
    }

    @Throws(IOException::class)
    fun withConfig(config: AppConfig) = also {
        config.also { mAppConfig = it }.run {
            mManifestEditor = editManifest()
                .setAppName(appName)
                .setVersionName(versionName)
                .setVersionCode(versionCode)
                .setPackageName(packageName)

            setArscPackageName(packageName)
            updateProjectConfig(this)
            copyAssetsRecursively("", File(workspacePath, "assets"))
            copyLibraries(this)
            setScriptFile(sourcePath)
        }
    }

    @Throws(FileNotFoundException::class)
    fun editManifest(): ManifestEditor = ManifestEditorWithAuthorities(FileInputStream(mManifestFile)).also { mManifestEditor = it }

    private fun updateProjectConfig(appConfig: AppConfig) {
        let {
            if (PFiles.isDir(appConfig.sourcePath)) {
                ProjectConfig.fromProjectDir(appConfig.sourcePath).also {
                    val buildNumber = it.buildInfo.buildNumber
                    it.buildInfo = BuildInfo.generate(buildNumber + 1)
                    PFiles.write(ProjectConfig.configFileOfDir(appConfig.sourcePath), it.toJson())
                }
            } else {
                ProjectConfig()
                    .setMainScriptFile("main.js")
                    .setName(appConfig.appName)
                    .setPackageName(appConfig.packageName)
                    .setVersionName(appConfig.versionName)
                    .setVersionCode(appConfig.versionCode)
                    .also { config ->
                        config.buildInfo = BuildInfo.generate(appConfig.versionCode.toLong())
                        File(workspacePath, "assets/project/project.json").also { file ->
                            file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                        }.writeText(config.toJson())
                    }
            }
        }.run {
            mKey = MD5Utils.md5(packageName + versionName + mainScriptFile)
            mInitVector = MD5Utils.md5(buildInfo.buildId + name).substring(0, 16)
            if (appConfig.libs.contains(Constants.OPENCV)) {
                mLibsIncludes += Libs.OPENCV.toSet()
                mAssetsDirExcludes -= Assets.Dir.OPENCV.toSet()
            }
            if (appConfig.libs.contains(Constants.MLKIT_GOOGLE_OCR)) {
                mLibsIncludes += Libs.MLKIT_GOOGLE_OCR.toSet()
                mAssetsDirExcludes -= Assets.Dir.MLKIT_GOOGLE_OCR.toSet()
            }
            if (appConfig.libs.contains(Constants.PADDLE_LITE)) {
                mLibsIncludes += Libs.PADDLE_LITE.toSet() + Libs.PADDLE_LITE_EXT.toSet()
                mAssetsDirExcludes -= Assets.Dir.PADDLE_LITE.toSet()
            }
            if (appConfig.libs.contains(Constants.MLKIT_BARCODE)) {
                mLibsIncludes += Libs.MLKIT_BARCODE.toSet()
                mAssetsDirExcludes -= Assets.Dir.MLKIT_BARCODE.toSet()
            }
            if (appConfig.libs.contains(Constants.OPENCC)) {
                mLibsIncludes += Libs.OPENCC.toSet()
                mAssetsDirExcludes -= Assets.Dir.OPENCC.toSet()
            }
        }
    }

    @Throws(IOException::class)
    fun build() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onBuild(this) } }
        mAppConfig.icon?.let { callable ->
            try {
                val tableBlock = TableBlock.load(mResourcesArscFile)
                val packageName = "${GlobalAppContext.get().packageName}.inrt"
                val packageBlock = tableBlock.getOrCreatePackage(0x7f, packageName).also {
                    tableBlock.currentPackage = it
                }
                val appIcon = packageBlock.getOrCreate("", ICON_RES_DIR, ICON_NAME)
                val appIconPath = appIcon.resValue.decodeValue()
                Log.d(TAG, "Icon path: $appIconPath")
                val file = File(workspacePath, appIconPath).also {
                    if (!it.exists()) {
                        File(it.parent!!).mkdirs()
                        it.createNewFile()
                    }
                }
                callable.call()?.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        mManifestEditor?.apply { commit() }?.run { writeTo(FileOutputStream(mManifestFile)) }
        mArscPackageName?.let { buildArsc() }
    }

    private fun copyAssetsRecursively(assetPath: String, targetFile: File) {
        if (targetFile.isFile && targetFile.exists()) return
        val list = mAssetManager.list(assetPath) ?: return
        if (list.isEmpty()) /* asset is file */ {
            if (!assetPath.contains(File.separatorChar)) /* assets root dir */ {
                if (!mAssetsFileIncludes.contains(assetPath)) {
                    return
                }
            }
            mAssetManager.open(assetPath).use { input ->
                FileOutputStream(targetFile.absolutePath).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } else /* asset is folder */ {
            if (mAssetsDirExcludes.any { assetPath.matches(Regex("$it(/[^/]+)*")) }) {
                return
            }
            targetFile.delete()
            targetFile.mkdir()
            list.forEach {
                val sourcePath = if (assetPath.isEmpty()) it else "$assetPath/$it"
                copyAssetsRecursively(sourcePath, File(targetFile, it))
            }
        }
    }

    @Throws(Exception::class)
    fun sign() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onSign(this) } }
        val fos = FileOutputStream(outApkFile)
        TinySign.sign(File(workspacePath), fos)
        fos.close()
    }

    fun cleanWorkspace() = also {
        mProgressCallback?.let { callback -> GlobalAppContext.post { callback.onClean(this) } }
        delete(File(workspacePath))
    }

    @Throws(IOException::class)
    fun setArscPackageName(packageName: String?) = also { mArscPackageName = packageName }

    @Throws(IOException::class)
    private fun buildArsc() {
        val oldArsc = File(workspacePath, "resources.arsc")
        val newArsc = File(workspacePath, "resources.arsc.new")
        val decoder = ARSCDecoder(BufferedInputStream(FileInputStream(oldArsc)), null, false)
        decoder.CloneArsc(FileOutputStream(newArsc), mArscPackageName, true)
        oldArsc.delete()
        newArsc.renameTo(oldArsc)
    }

    private fun delete(file: File) {
        file.apply { if (isDirectory) listFiles()?.forEach { delete(it) } }.also { it.delete() }
    }

    interface ProgressCallback {

        fun onPrepare(builder: ApkBuilder)
        fun onBuild(builder: ApkBuilder)
        fun onSign(builder: ApkBuilder)
        fun onClean(builder: ApkBuilder)

    }

    class AppConfig {

        var appName: String? = null
            private set
        var versionName: String? = null
            private set
        var versionCode = 0
            private set
        var sourcePath: String? = null
            private set
        var packageName: String? = null
            private set
        var ignoredDirs = ArrayList<File>()
        var icon: Callable<Bitmap>? = null
            private set
        var abis: List<String> = emptyList()
            private set
        var libs: List<String> = emptyList()
            private set

        fun ignoreDir(dir: File) = also { ignoredDirs.add(dir) }

        fun setAppName(appName: String?) = also { appName?.let { this.appName = it } }

        fun setVersionName(versionName: String?) = also { versionName?.let { this.versionName = it } }

        fun setVersionCode(versionCode: Int?) = also { versionCode?.let { this.versionCode = it } }

        fun setSourcePath(sourcePath: String?) = also { sourcePath?.let { this.sourcePath = it } }

        fun setPackageName(packageName: String?) = also { packageName?.let { this.packageName = it } }

        fun setIcon(icon: Callable<Bitmap>?) = also { icon?.let { this.icon = it } }

        fun setIcon(iconPath: String?) = also { iconPath?.let { this.icon = Callable { BitmapFactory.decodeFile(it) } } }

        fun setAbis(abis: List<String>?) = also { abis?.let { this.abis = it } }

        fun setLibs(libs: List<String>?) = also { libs?.let { this.libs = it } }

        companion object {
            @JvmStatic
            fun fromProjectConfig(projectDir: String?, projectConfig: ProjectConfig) = AppConfig()
                .setAppName(projectConfig.name)
                .setPackageName(projectConfig.packageName)
                .ignoreDir(File(projectDir, projectConfig.buildDir))
                .setVersionCode(projectConfig.versionCode)
                .setVersionName(projectConfig.versionName)
                .setSourcePath(projectDir)
                .setIcon(projectConfig.icon?.let { File(projectDir, it).path })
        }
    }

    private inner class ManifestEditorWithAuthorities(manifestInputStream: InputStream?) : ManifestEditor(manifestInputStream) {
        override fun onAttr(attr: AxmlWriter.Attr) {
            attr.apply {
                if (name.data == "authorities" && value is StringItem) {
                    (value as StringItem).data = "${mAppConfig.packageName}.fileprovider"
                } else {
                    super.onAttr(this)
                }
            }
        }
    }

    private fun unzipLibs() {
        Zip.unzip(appApkFile, File(mNativePath), "lib/")
    }

    private fun copyLibraries(config: AppConfig) {
        config.abis.forEach { abi ->
            mLibsIncludes.distinct().forEach { name ->
                runCatching {
                    File(mNativePath, "$abi/$name").copyTo(
                        File(workspacePath, "lib/$abi/$name"),
                        overwrite = true
                    )
                }
            }
        }
    }

    companion object {

        const val ICON_NAME = "ic_launcher"
        const val ICON_RES_DIR = "mipmap"

        const val TEMPLATE_APK_NAME = "template.apk"

        private val TAG = ApkBuilder::class.java.simpleName

        private val globalContext: Context by lazy { GlobalAppContext.get() }

        val appApkFile by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                File(globalContext.packageManager.getApplicationInfo(globalContext.packageName, ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES.toLong())).sourceDir)
            } else {
                File(globalContext.packageManager.getApplicationInfo(globalContext.packageName, 0).sourceDir)
            }
        }

    }

    @Suppress("SpellCheckingInspection")
    object Libs {

        private val BASIC = listOf(
            "libjackpal-androidterm5.so", /* Terminal Emulator. */
            "libjackpal-termexec2.so", /* Terminal Emulator. */
        )

        private val MISCELLANEOUS = emptyList<String>()

        @JvmField
        val OPENCV = listOf(
            "libc++_shared.so",
            "libopencv_java4.so"
        )

        @JvmField
        val MLKIT_GOOGLE_OCR = listOf(
            "libmlkit_google_ocr_pipeline.so"
        )

        @JvmField
        val PADDLE_LITE = listOf(
            "libc++_shared.so",
            "libpaddle_light_api_shared.so",
            "libNative.so"
        )

        val PADDLE_LITE_EXT = listOf(
            "libhiai.so",
            "libhiai_ir.so",
            "libhiai_ir_build.so",
        )

        @JvmField
        val MLKIT_BARCODE = emptyList<String>()

        @JvmField
        val OPENCC = emptyList<String>()

        val DEFALUT_INCLUDES: List<String> = (BASIC + MISCELLANEOUS).distinct()

        @JvmStatic
        fun ensure(name: String, libNameList: List<String>) {
            if (!BuildConfig.isInrt) return
            val nativeLibraryDir = File(globalContext.applicationInfo.nativeLibraryDir)
            val primaryNativeLibraries = nativeLibraryDir.list()?.toList() ?: emptyList()
            if (!primaryNativeLibraries.containsAll(libNameList)) {
                throw Exception(globalContext.getString(R.string.error_module_does_not_work_due_to_the_lack_of_necessary_library_files, name))
            }
        }

    }

    @Suppress("SpellCheckingInspection")
    object Assets {
        object File {

            private val BASIC = listOf(
                "init.js", "roboto_medium.ttf"
            )

            private val MISCELLANEOUS = emptyList<String>()

            val DEFAULT_INCLUDES = (BASIC + MISCELLANEOUS).distinct()

        }

        object Dir {

            private val BASIC = listOf("doc", "docs", "editor", "indices", "js-beautify", "sample")

            private val MISCELLANEOUS = listOf("stored-locales")

            @JvmField
            val OPENCV = emptyList<String>()

            @JvmField
            val MLKIT_GOOGLE_OCR = listOf("mlkit-google-ocr-models")

            @JvmField
            val MLKIT_BARCODE = listOf("mlkit_barcode_models")

            @JvmField
            val PADDLE_LITE = listOf("models")

            @JvmField
            val OPENCC = listOf("openccdata")

            val DEFAULT_EXCLUDES = (BASIC + MLKIT_GOOGLE_OCR + MLKIT_BARCODE + PADDLE_LITE + OPENCC + MISCELLANEOUS).distinct()

        }
    }

    object Constants {
        const val OPENCV = "OpenCV"
        const val MLKIT_GOOGLE_OCR = "MLKit Google OCR"
        const val PADDLE_LITE = "MLKit Barcode"
        const val MLKIT_BARCODE = "Paddle Lite"
        const val OPENCC = "OpenCC"
    }

}