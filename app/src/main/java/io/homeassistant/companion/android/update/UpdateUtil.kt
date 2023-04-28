package io.homeassistant.companion.android.update

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.util.getAppMetaDataString
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

object UpdateUtil {
    private var mDownloadId: Long = 0

    fun checkNew(context: Activity, okHttpClient: OkHttpClient) {
        val showHint = context.getSharedPreferences("config", Context.MODE_PRIVATE).getBoolean(
            "showHint",
            false
        )
        if (!showHint) {
            HintDialog(context).show()
            context.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
                .putBoolean("showHint", true)
                .apply()
        }

        val checkTime = context.getSharedPreferences("config", Context.MODE_PRIVATE).getLong(
            UpdateActivity.CHECK_TIME,
            0
        )
        if (System.currentTimeMillis() - checkTime < 24 * 60 * 60 * 1000) {
            return
        }

        val formBody: RequestBody = FormBody.Builder()
            .add("_api_key", context.getAppMetaDataString("pgy_api_key"))
            .add("appKey", "8a601dcac3098f0d5c89fa9fe416ca94")
            .add("buildVersion", BuildConfig.VERSION_NAME)
            .build()
        val request = Request.Builder().apply {
            url("https://www.pgyer.com/apiv2/app/check")
            post(formBody)
        }.build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("checkNew==>", e.toString())
                githubCheckNew(context, okHttpClient)
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                if (res.isNullOrEmpty()) {
                    githubCheckNew(context, okHttpClient)
                    return
                }
                //Log.e("onResponse==>", res)
                val jsonObject = JSONObject(res)
                if (jsonObject.getInt("code") != 0) {
                    githubCheckNew(context, okHttpClient)
                    return
                }
                val dataObject = jsonObject.getJSONObject("data")
                val buildHaveNewVersion = dataObject.getBoolean("buildHaveNewVersion")
                if (!buildHaveNewVersion) return
                val downloadURL = dataObject.getString("downloadURL")
                val ver = dataObject.getString("buildVersion")
                val desc = try {
                    dataObject.getString("buildUpdateDescription")
                } catch (e: Exception) {
                    "有新版本了！"
                }
                val updateInfo = UpdateInfo(ver, desc, downloadURL)
                val intent = Intent(context, UpdateActivity::class.java)
                intent.putExtra(UpdateActivity.UPDATE_INFO, updateInfo)
                context.startActivity(intent)
                context.overridePendingTransition(0, 0)
            }

        })
    }

    private fun githubCheckNew(context: Activity, okHttpClient: OkHttpClient) {
        Toast.makeText(
            context,
            "次数用尽检查更新失败，尝试备用更新，推荐关注公众号：UnknownExceptions 回复最新版进行更新",
            Toast.LENGTH_SHORT
        ).show()
        val request = Request.Builder().apply {
            url("https://github.com/nesror/Home-Assistant-Companion-for-Android/releases/latest")
        }.build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("checkNew==>", e.toString())
                Toast.makeText(
                    context,
                    "有新版本了！",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onResponse(call: Call, response: Response) {
                val url = response.request.url.toString()
                val ver = url.split("/").last()
                Log.d("checkNew==>ver:", ver)
                if (!BuildConfig.VERSION_NAME.contains(ver)) {
                    val apkUrl =
                        "https://github.com/nesror/Home-Assistant-Companion-for-Android/releases/download/$ver/app-full-release.apk"
                    Log.d("checkNew==>apkUrl:", apkUrl)
                    val updateInfo = UpdateInfo(
                        ver, "如果无法直接更新，可以关注公众号进行更新！\n" +
                                "公众号：UnknownExceptions 回复 最新版 获取新版本\n" +
                                "也可回复HA获取全新Flutter版本", apkUrl
                    )
                    val intent = Intent(context, UpdateActivity::class.java)
                    intent.putExtra(UpdateActivity.UPDATE_INFO, updateInfo)
                    context.startActivity(intent)
                    context.overridePendingTransition(0, 0)
                }
            }

        })
    }

    fun getActivityFromView(view: View): Activity? {
        var context: Context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    fun downLoadApk(context: Context, url: String, describeStr: String) {
        // 得到系统的下载管理
        clearCurrentTask(context)
        val saveFile = apkFile(context)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        // 以下两行代码可以让下载的apk文件被直接安装而不用使用Fileprovider,系统7.0或者以上才启动。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localBuilder = VmPolicy.Builder()
            StrictMode.setVmPolicy(localBuilder.build())
        }
        val requestApk = DownloadManager.Request(uri)
        // 设置在什么网络下下载
        requestApk.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        // 下载中和下载完后都显示通知栏
        requestApk.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (saveFile.exists()) {    //判断文件是否存在，存在的话先删除
            saveFile.delete()
        }
        requestApk.setDestinationUri(Uri.fromFile(saveFile))
        // 设置下载中通知栏的提示消息
        requestApk.setTitle(describeStr)
        // 设置设置下载中通知栏提示的介绍
        requestApk.setDescription("更新中")

        // 7.0以上的系统适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestApk.setRequiresDeviceIdle(false)
            requestApk.setRequiresCharging(false)
        }
        // 启动下载,该方法返回系统为当前下载请求分配的一个唯一的ID
        mDownloadId = downloadManager.enqueue(requestApk)
    }

    private fun clearCurrentTask(mContext: Context) {
        if (mDownloadId == 0L) return
        val dm = mContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.remove(mDownloadId)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun installApk(context: Context) {
        mDownloadId = 0
        val saveFile: File = apkFile(context)
        val intent = Intent(Intent.ACTION_VIEW)
        if (saveFile.exists()) {
            // 兼容7.0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                val contentUri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    saveFile
                )
                intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
                // 兼容8.0 测试发现小米会自动请求权限
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
//                    if (!hasInstallPermission) {
//                        // 没有权限
//                        Toast.makeText(context, "没有安装权限，请在设置中开启！", Toast.LENGTH_LONG).show()
//                        //return
//                    }
//                }
            } else {
                // <7.0
                intent.setDataAndType(
                    Uri.fromFile(saveFile),
                    "application/vnd.android.package-archive"
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // activity任务栈中Activity的个数>0
            if (context.packageManager.queryIntentActivities(intent, 0).size > 0) {
                context.startActivity(intent)
            }
        }
    }

    private fun apkFile(context: Context): File {
        val dir = File(context.externalCacheDir, "download")
        if (!dir.exists()) {
            dir.mkdir()
        }
        // 创建文件
        return File(dir, "temp.apk")
    }

    fun getDownloadId(): Long {
        return mDownloadId
    }

}
