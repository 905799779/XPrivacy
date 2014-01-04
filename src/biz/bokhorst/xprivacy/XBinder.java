package biz.bokhorst.xprivacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XBinder extends XHook {
	private Methods mMethod;

	private static byte mMagic = 0;
	private static boolean mMagical = false;
	private static int FLAG_XPRIVACY = 0x00000010;
	private static int BITS_MAGIC = 16;
	private static boolean cEnabled = false;
	private static boolean cRestrict = false;

	private XBinder(Methods method, String restrictionName) {
		super(restrictionName, method.name(), null);
		mMethod = method;
	}

	public String getClassName() {
		return (mMethod == Methods.transact ? "android.os.BinderProxy" : "android.os.Binder");
	}

	public boolean isVisible() {
		return (mMethod != Methods.execTransact);
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
		if (cEnabled) {
			listHook.add(new XBinder(Methods.execTransact, null)); // Binder
			listHook.add(new XBinder(Methods.transact, null)); // BinderProxy
		}
		return listHook;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		if (mMethod == Methods.execTransact) {
			checkIPC(param);
		} else if (mMethod == Methods.transact) {
			markIPC(param);
		} else
			Util.log(this, Log.WARN, "Unknown method=" + param.method.getName());
	}

	private void markIPC(MethodHookParam param) {
		int flags = (Integer) param.args[3];
		if (flags != 0 && flags != IBinder.FLAG_ONEWAY)
			Log.w("XPrivacy/XBinder", "flags=" + Integer.toHexString(flags));
		flags |= FLAG_XPRIVACY;
		flags |= getMagic() << BITS_MAGIC;
		param.args[3] = flags;
		// Interface name is not always available
	}

	private void checkIPC(MethodHookParam param) {
		// Entry point from android_util_Binder.cpp's onTransact
		int flags = (Integer) param.args[3];
		byte magic = (byte) (flags >> BITS_MAGIC);
		boolean flagged = ((flags & FLAG_XPRIVACY) != 0);
		flags &= IBinder.FLAG_ONEWAY;
		param.args[3] = flags;

		try {
			if (Process.myUid() > 0) {
				int uid = Binder.getCallingUid();
				if ((flagged || magic != getMagic()) && PrivacyManager.isApplication(uid)) {
					// Application bypassed API
					Binder binder = (Binder) param.thisObject;
					String name = binder.getInterfaceDescriptor();
					Log.w("XPrivacy/XBinder", "restrict name=" + name + " uid=" + uid + " my=" + Process.myUid());

					if (cRestrict) {
						// Get reply parcel
						Parcel reply = null;
						try {
							// static protected final Parcel obtain(int obj)
							// frameworks/base/core/java/android/os/Parcel.java
							Method methodObtain = Parcel.class.getDeclaredMethod("obtain", int.class);
							methodObtain.setAccessible(true);
							reply = (Parcel) methodObtain.invoke(null, param.args[2]);
						} catch (NoSuchMethodException ex) {
							Util.bug(null, ex);
						}

						// Block IPC
						if (reply == null)
							Log.w("XPrivacy/XBinder", "reply is null uid=" + uid);
						else {
							reply.setDataPosition(0);
							reply.writeException(new SecurityException("XPrivacy"));
						}
						param.setResult(true);
					}
				}
			}
		} catch (Throwable ex) {
			Log.e("XPrivacy/XBinder", ex.toString());
		}
	}

	private static byte getMagic() {
		if (!mMagical) {
			// Proguard will generate new names for each release
			for (Field field : XBinder.class.getDeclaredFields())
				for (byte b : field.getName().getBytes())
					mMagic ^= b;
			for (Method method : XBinder.class.getDeclaredMethods())
				for (byte b : method.getName().getBytes())
					mMagic ^= b;
			mMagical = true;
		}
		return mMagic;
	}

	@Override
	protected void after(MethodHookParam param) throws Throwable {
		// Do nothing
	}
}
