package vn.startupbench

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.os.IBinder

/** Keeps the collector alive while Android switches focus to the application under test. */
class RunService : Service() {
    companion object {
        fun update(context: Context, text: String, done: Boolean = false) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val builder = Notification.Builder(context, "benchmark").setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(if (done) "Phiên đo đã kết thúc" else "Đang đo startup")
                .setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(!done).setAutoCancel(done)
            if (!done) {
                val stop = PendingIntent.getService(context, 1, Intent(context, RunService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(Notification.Action.Builder(null, "Dừng", stop).build())
            }
            runCatching { context.getSystemService(NotificationManager::class.java).notify(if (done) 2 else 1, builder.build()) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("benchmark", "Phiên đo startup", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RunService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "benchmark")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history).setContentTitle("Đang đo startup")
            .setContentText("Chạm để xem tiến độ. Giữ điện thoại mở khóa.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Dừng", stop).build()).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") BenchEngine.cancel = true
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
