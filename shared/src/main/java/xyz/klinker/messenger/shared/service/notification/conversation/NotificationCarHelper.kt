package xyz.klinker.messenger.shared.service.notification.conversation

import android.app.PendingIntent
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.support.v4.app.RemoteInput
import xyz.klinker.messenger.shared.R
import xyz.klinker.messenger.shared.data.MimeType
import xyz.klinker.messenger.shared.data.pojo.NotificationConversation
import xyz.klinker.messenger.shared.service.NotificationMarkReadService
import xyz.klinker.messenger.shared.service.ReplyService
import xyz.klinker.messenger.shared.service.notification.NotificationService

class NotificationCarHelper(private val service: NotificationService) {

    fun buildExtender(conversation: NotificationConversation, remoteInput: RemoteInput): NotificationCompat.CarExtender {
        val carReply = Intent().addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction("xyz.klinker.messenger.CAR_REPLY")
                .putExtra(ReplyService.EXTRA_CONVERSATION_ID, conversation.id)
                .setPackage("xyz.klinker.messenger")
        val pendingCarReply = PendingIntent.getBroadcast(service, conversation.id.toInt(),
                carReply, PendingIntent.FLAG_UPDATE_CURRENT)

        val carRead = Intent().addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction("xyz.klinker.messenger.CAR_READ")
                .putExtra(NotificationMarkReadService.EXTRA_CONVERSATION_ID, conversation.id)
                .setPackage("xyz.klinker.messenger")
        val pendingCarRead = PendingIntent.getBroadcast(service, conversation.id.toInt(),
                carRead, PendingIntent.FLAG_UPDATE_CURRENT)

        // Android Auto extender
        val car = NotificationCompat.CarExtender.UnreadConversation.Builder(conversation.title)
                .setReadPendingIntent(pendingCarRead)
                .setReplyAction(pendingCarReply, remoteInput)
                .setLatestTimestamp(conversation.timestamp)

        for ((_, data, mimeType) in conversation.messages) {
            if (mimeType == MimeType.TEXT_PLAIN) {
                car.addMessage(data)
            } else {
                car.addMessage(service.getString(R.string.new_mms_message))
            }
        }

        return NotificationCompat.CarExtender().setUnreadConversation(car.build())
    }
}