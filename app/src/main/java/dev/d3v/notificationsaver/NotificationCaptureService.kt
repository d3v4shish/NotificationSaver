package dev.d3v.notificationsaver

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val app: NotificationSaverApp
        get() = application as NotificationSaverApp
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingEvents = ConcurrentHashMap<String, NotificationEvent>()
    private val drainSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            for (ignored in drainSignal) {
                while (true) {
                    val next = pendingEvents.entries.firstOrNull() ?: break
                    if (!pendingEvents.remove(next.key, next.value)) {
                        continue
                    }
                    when (val event = next.value) {
                        is NotificationEvent.Posted -> app.repository.recordPosted(event.notification)
                        is NotificationEvent.Removed -> app.repository.recordRemoved(event.notification)
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        app.operationalMetrics.recordListenerConnected()
        app.logger.info("NotificationCaptureService", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        app.operationalMetrics.recordListenerDisconnected()
        app.logger.info("NotificationCaptureService", "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        enqueue(NotificationEvent.Posted(notification))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        enqueue(NotificationEvent.Removed(notification))
    }

    override fun onDestroy() {
        drainSignal.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enqueue(event: NotificationEvent) {
        pendingEvents[event.sourceKey] = event
        if (drainSignal.trySend(Unit).isFailure) {
            app.operationalMetrics.recordQueueDrop()
            app.logger.error("NotificationCaptureService", "Dropping notification event because the queue is unavailable")
        }
    }
}

private sealed interface NotificationEvent {
    val sourceKey: String

    data class Posted(val notification: StatusBarNotification) : NotificationEvent {
        override val sourceKey: String = notification.stableSourceKey()
    }

    data class Removed(val notification: StatusBarNotification) : NotificationEvent {
        override val sourceKey: String = notification.stableSourceKey()
    }
}

private fun StatusBarNotification.stableSourceKey(): String {
    return key ?: "$packageName:$id:${tag.orEmpty()}"
}
