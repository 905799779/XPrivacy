package biz.bokhorst.xprivacy;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import android.os.Process;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XIoBridge extends XHook {

	private String mFileName;

	private XIoBridge(String methodName, String restrictionName, String fileName) {
		super(restrictionName, methodName, fileName);
		mFileName = fileName;
	}

	public String getClassName() {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			return "dalvik.system.BlockGuard$WrappedFileSystem";
		} else {
			return "libcore.io.IoBridge";
		}
	}

	// public static FileDescriptor open(String path, int flags)
	// libcore/luni/src/main/java/libcore/io/IoBridge.java

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		listHook.add(new XIoBridge("open", PrivacyManager.cIdentification, "/proc"));
		listHook.add(new XIoBridge("open", PrivacyManager.cIdentification, "/system/build.prop"));
		return listHook;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		if (param.args.length > 0) {
			String fileName = (String) param.args[0];
			if (fileName != null && fileName.startsWith(mFileName)) {
				// Zygote, Android
				if (Process.myUid() <= 0 || Process.myUid() == PrivacyManager.cAndroidUid)
					return;

				// /proc
				if (mFileName.equals("/proc")) {
					// Allow command line
					if (fileName.equals("/proc/self/cmdline"))
						return;

					// Backward compatibility
					Version sVersion = new Version(PrivacyManager.getSetting(this, null, 0,
							PrivacyManager.cSettingVersion, "0.0", true));
					if (sVersion.compareTo(new Version("1.7")) < 0)
						return;
				}

				// Check if restricted
				if (isRestricted(param, mFileName))
					param.setThrowable(new FileNotFoundException());
			}
		}
	}

	@Override
	protected void after(MethodHookParam param) throws Throwable {
		// Do nothing
	}
}
