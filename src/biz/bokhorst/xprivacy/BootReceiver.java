package biz.bokhorst.xprivacy;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.support.v4.app.NotificationCompat;

public class BootReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent bootIntent) {
		// Randomize settings
		final Context fContext = context;
		new Thread(new Runnable() {
			public void run() {
				randomizeSettings(fContext, 0);
				for (ApplicationInfo aInfo : fContext.getPackageManager().getInstalledApplications(0))
					randomizeSettings(fContext, aInfo.uid);
			}
		}).start();

		// Check if Xposed enabled
		if (Util.isXposedEnabled())
			context.sendBroadcast(new Intent("biz.bokhorst.xprivacy.action.ACTIVE"));
		else {
			// Create Xposed installer intent
			Intent xInstallerIntent = context.getPackageManager().getLaunchIntentForPackage(
					"de.robv.android.xposed.installer");

			PendingIntent pi = (xInstallerIntent == null ? null : PendingIntent.getActivity(context, 0,
					xInstallerIntent, PendingIntent.FLAG_UPDATE_CURRENT));

			// Build notification
			NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
			notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
			notificationBuilder.setContentTitle(context.getString(R.string.app_name));
			notificationBuilder.setContentText(context.getString(R.string.app_notenabled));
			notificationBuilder.setWhen(System.currentTimeMillis());
			notificationBuilder.setAutoCancel(true);
			if (pi != null)
				notificationBuilder.setContentIntent(pi);
			Notification notification = notificationBuilder.build();

			// Display notification
			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(0, notification);
		}
	}

	private void randomizeSettings(Context context, int uid) {
		boolean random = PrivacyManager.getSettingBool(null, uid, PrivacyManager.cSettingRandom, false, false);
		if (random) {
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingLatitude, PrivacyManager.getRandomProp("LAT"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingLongitude, PrivacyManager.getRandomProp("LON"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingSerial, PrivacyManager.getRandomProp("SERIAL"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingMac, PrivacyManager.getRandomProp("MAC"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingPhone, PrivacyManager.getRandomProp("PHONE"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingImei, PrivacyManager.getRandomProp("IMEI"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingId, PrivacyManager.getRandomProp("ANDROID_ID"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingGsfId, PrivacyManager.getRandomProp("GSF_ID"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingAdId,
					PrivacyManager.getRandomProp("AdvertisingId"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingCountry,
					PrivacyManager.getRandomProp("ISO3166"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingSubscriber,
					PrivacyManager.getRandomProp("SubscriberId"));
			PrivacyManager.setSetting(null, uid, PrivacyManager.cSettingSSID, PrivacyManager.getRandomProp("SSID"));
		}
	}
}
