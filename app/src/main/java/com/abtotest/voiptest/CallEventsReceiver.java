package com.abtotest.voiptest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;

import org.abtollc.sdk.AbtoPhone;

public class CallEventsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if ( bundle == null ) return;

        if ( bundle.getBoolean(AbtoPhone.IS_INCOMING, false) ) {

            // Incoming call
            buildIncomingCallNotification(context, bundle);

        } else if ( bundle.getBoolean(KEY_REJECT_CALL, false) ) {

            // Reject call
            int callId = bundle.getInt(AbtoPhone.CALL_ID);
            CallEventsReceiver.cancelIncCallNotification(context, callId);
            try {
                App.getApp().getAbtoPhone().rejectCall();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        } else if ( bundle.getInt(AbtoPhone.CODE) == -1 ) {

            // Cancel call
            int callId = bundle.getInt(AbtoPhone.CALL_ID);
            cancelIncCallNotification(context, callId);
        }
    }

    public static final String CHANEL_CALL_ID = "abto_phone_call";
    private static NotificationChannel channelCall;
    public static final String KEY_PICK_UP_AUDIO = "KEY_PICK_UP_AUDIO";
    public static final String KEY_PICK_UP_VIDEO = "KEY_PICK_UP_VIDEO";
    public static final String KEY_REJECT_CALL = "KEY_REJECT_CALL";
    private static final int NOTIFICATION_INCOMING_CALL_ID = 1000;

    private void buildIncomingCallNotification(Context context, Bundle bundle) {
        Intent intent = new Intent(context, ScreenAV.class);
        intent.putExtras(bundle);

        if ( !App.getApp().isAppInBackground() ) {//App is foreground - start activity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        boolean isVideoCall = bundle.getBoolean(AbtoPhone.HAS_VIDEO, false);
        String title        = isVideoCall ? "Incoming video call" : "Incoming call";
        String remoteContact= bundle.getString(AbtoPhone.REMOTE_CONTACT);
        int callId          = bundle.getInt(AbtoPhone.CALL_ID);

        // Create channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelCall == null) {
            channelCall = new NotificationChannel(CHANEL_CALL_ID, context.getString(R.string.app_name) + " Call", NotificationManager.IMPORTANCE_HIGH);
            channelCall.setDescription(context.getString(R.string.app_name));
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channelCall);
            }
        }

        // Intent for launch ScreenAV
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent for pickup audio call
        Intent pickUpAudioIntent = new Intent(context, ScreenAV.class);
        pickUpAudioIntent.putExtras(bundle);
        pickUpAudioIntent.putExtra(KEY_PICK_UP_AUDIO, true);
        PendingIntent pickUpAudioPendingIntent = PendingIntent.getActivity(context, 2, pickUpAudioIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent for reject call
        Intent rejectCallIntent = new Intent();
        rejectCallIntent.setPackage(context.getPackageName());
        rejectCallIntent.setAction(AbtoPhone.ACTION_ABTO_CALL_EVENT);
        rejectCallIntent.putExtra(AbtoPhone.CALL_ID, callId);
        rejectCallIntent.putExtra(KEY_REJECT_CALL, true);
        PendingIntent pendingRejectCall = PendingIntent.getBroadcast(context, 4, rejectCallIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Style for popup notification
        NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();
        bigText.bigText(remoteContact);
        bigText.setBigContentTitle(title);

        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANEL_CALL_ID);
        builder.setSmallIcon(R.drawable.ic_notif_pick_up_audio)
                .setColor(0xff00ff00)
                .setContentTitle(title)
                .setContentIntent(notificationPendingIntent)
                .setContentText(remoteContact)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(bigText)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(R.drawable.ic_notif_cancel_call, "Hang Up", pendingRejectCall)
                .addAction(R.drawable.ic_notif_pick_up_audio, "Audio", pickUpAudioPendingIntent)
                .setFullScreenIntent(notificationPendingIntent, true);

        if (isVideoCall) {
            // Intent for pickup video call
            Intent pickUpVideoIntent = new Intent(context, ScreenAV.class);
            pickUpVideoIntent.putExtras(bundle);
            pickUpVideoIntent.putExtra(KEY_PICK_UP_VIDEO, true);
            PendingIntent pickUpVideoPendingIntent = PendingIntent.getActivity(context, 3, pickUpVideoIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.ic_notif_pick_up_video, "Video", pickUpVideoPendingIntent);
        }

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = builder.build();
        mNotificationManager.notify(NOTIFICATION_INCOMING_CALL_ID + callId, notification);
    }

    public static void cancelIncCallNotification(Context context, int callId) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_INCOMING_CALL_ID + callId);
        }
    }
}
