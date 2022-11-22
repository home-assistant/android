package io.homeassistant.companion.android.update

import android.content.BroadcastReceiver
import android.content.Intent
import android.app.DownloadManager
import android.content.Context

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            AppUtil.installApk(context)
        } else if (intent.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) {
            // 如果还未完成下载，用户点击Notification ，跳转到下载中心
            val viewDownloadIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            viewDownloadIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(viewDownloadIntent)
        }
    }
}