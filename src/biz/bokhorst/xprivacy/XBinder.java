package biz.bokhorst.xprivacy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XBinder extends XHook {
	private Methods mMethod;

	private static long mToken = 0;
	private static int BITS_TOKEN = 8;
	private static int MASK_TOKEN = 0xFFFFFF;

	public static List<String> cListService = Arrays.asList(new String[] { "account", "activity", "clipboard",
			"connectivity", "location", "telephony.registry", "telephony.msim.registry", "package", "iphonesubinfo",
			"iphonesubinfo_msim", "window", "wifi" });

	// @formatter:off
	public static List<String> cListDescription = Arrays.asList(new String[] {
		"android.accounts.IAccountManager",
		"android.app.IActivityManager",
		"android.content.IClipboard",
		"android.net.IConnectivityManager",
		"android.location.ILocationManager",
		// TODO: sensor interface
		"com.android.internal.telephony.ITelephonyRegistry",
		"com.android.internal.telephony.ITelephonyRegistryMSim",
		"android.content.pm.IPackageManager",
		"com.android.internal.telephony.IPhoneSubInfo",
		"com.android.internal.telephony.msim.IPhoneSubInfoMSim",
		"android.view.IWindowManager",
		"android.net.wifi.IWifiManager"
	});
	// @formatter:on

	private XBinder(Methods method, String restrictionName, int sdk) {
		super(restrictionName, method.name(), null, sdk);
		mMethod = method;
	}

	public String getClassName() {
		return (mMethod == Methods.transact ? "android.os.BinderProxy" : "android.os.Binder");
	}

	public boolean isVisible() {
		return (mMethod != Methods.execTransact);
	}

	@Override
	public void setSecret(String secret) {
		super.setSecret(secret);
		mToken = (secret.hashCode() & MASK_TOKEN);
	}

	// @formatter:off

	// private boolean execTransact(int code, int dataObj, int replyObj, int flags)
	// public final boolean transact(int code, Parcel data, Parcel reply, int flags)
	// public native boolean transact(int code, Parcel data, Parcel reply, int flags)
	// frameworks/base/core/java/android/os/Binder.java
	// http://developer.android.com/reference/android/os/Binder.html

	// @formatter:on

	private enum Methods {
		execTransact, transact
	};

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		listHook.add(new XBinder(Methods.execTransact, null, 1)); // Binder
		listHook.add(new XBinder(Methods.transact, null, 1)); // BinderProxy
		return listHook;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		if (mMethod == Methods.execTransact)
			checkIPC(param);

		else if (mMethod == Methods.transact)
			markIPC(param);

		else
			Util.log(this, Log.WARN, "Unknown method=" + param.method.getName());
	}

	private void markIPC(MethodHookParam param) {
		int flags = (Integer) param.args[3];
		if (flags != 0 && flags != IBinder.FLAG_ONEWAY)
			Util.log(this, Log.ERROR, "flags=" + Integer.toHexString(flags));
		flags |= (mToken << BITS_TOKEN);
		param.args[3] = flags;
	}

	private void checkIPC(MethodHookParam param) {
		// Entry point from android_util_Binder.cpp's onTransact
		int flags = (Integer) param.args[3];
		long token = (flags >> BITS_TOKEN) & MASK_TOKEN;
		flags &= IBinder.FLAG_ONEWAY;
		param.args[3] = flags;

		try {
			if (Process.myUid() > 0) {
				int uid = Binder.getCallingUid();
				if (token != mToken && PrivacyManager.isApplication(uid)) {
					// Get interface name
					Binder binder = (Binder) param.thisObject;
					String name = binder.getInterfaceDescriptor();
					if (cListDescription.contains(name)) {
						Util.log(this, Log.WARN, "restrict name=" + name + " uid=" + uid + " my=" + Process.myUid());
						if (getRestricted(uid, PrivacyManager.cIPC, name)) {
							// Get reply parcel
							Parcel reply = null;
							try {
								// static protected final Parcel obtain(int obj)
								// frameworks/base/core/java/android/os/Parcel.java
								Method methodObtain = Parcel.class.getDeclaredMethod("obtain", int.class);
								methodObtain.setAccessible(true);
								reply = (Parcel) methodObtain.invoke(null, param.args[2]);
							} catch (NoSuchMethodException ex) {
								Util.bug(this, ex);
							}

							// Block IPC
							if (reply == null)
								Util.log(this, Log.ERROR, "reply is null uid=" + uid);
							else {
								reply.setDataPosition(0);
								reply.writeException(new SecurityException("XPrivacy"));
							}
							param.setResult(true);
						}
					}
				}
			}
		} catch (Throwable ex) {
			Util.bug(this, ex);
		}
	}

	@Override
	protected void after(MethodHookParam param) throws Throwable {
		// Do nothing
	}
}
