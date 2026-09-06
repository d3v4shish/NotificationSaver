package dev.d3v.notificationsaver

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCaptureService : NotificationListenerService() {
    private val app: NotificationSaverApp
        get() = application as NotificationSaverApp

    override fun onListenerConnected() {
        super.onListenerConnected()
        app.operationalMetrics.recordListenerConnected()
        app.logger.info("NotificationCaptureService", "Notification listener connected")
        val rankingMap = currentRanking
        val recovered = activeNotifications.orEmpty().map { notification ->
            CaptureEvent.Posted(
                notification = notification,
                rankingImportance = rankingMap.importanceFor(notification.key),
                recovered = true,
            )
        }
        app.notificationIngestor.submit(CaptureEvent.Reconcile(recovered))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        app.operationalMetrics.recordListenerDisconnected()
        app.logger.warn("NotificationCaptureService", "Notification listener disconnected; requesting rebind")
        requestRebind(ComponentName(this, NotificationCaptureService::class.java))
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
    ) {
        val notification = sbn ?: return
        app.notificationIngestor.submit(
            CaptureEvent.Posted(
                notification = notification,
                rankingImportance = rankingMap.importanceFor(notification.key),
                recovered = false,
            ),
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        val notification = sbn ?: return
        app.notificationIngestor.submit(
            CaptureEvent.Removed(
                notification = notification,
                reason = reason,
                rankingImportance = rankingMap.importanceFor(notification.key),
            ),
        )
    }
}

private fun NotificationListenerService.RankingMap?.importanceFor(key: String?): Int? {
    if (this == null || key == null) return null
    val ranking = NotificationListenerService.Ranking()
    return if (getRanking(key, ranking)) ranking.importance else null
}
