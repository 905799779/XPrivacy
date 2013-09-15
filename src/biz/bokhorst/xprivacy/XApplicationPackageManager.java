package biz.bokhorst.xprivacy;

import static de.robv.android.xposed.XposedHelpers.findField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import android.os.Binder;
import android.util.Log;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XApplicationPackageManager extends XHook {
	private Methods mMethod;

	private XApplicationPackageManager(Methods method, String restrictionName) {
		super(restrictionName, method.name(), null);
		mMethod = method;
	}

	public String getClassName() {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
			return "android.app.ContextImpl$ApplicationPackageManager";
		} else {
			return "android.app.ApplicationPackageManager";
		}
	}

	// @formatter:off

	// public List<ApplicationInfo> getInstalledApplications(int flags)
	// public List<PackageInfo> getInstalledPackages(int flags)
	// public List<PackageInfo> getPreferredPackages(int flags)
	// public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags)
	// public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags)
	// public List<ResolveInfo> queryIntentActivities(Intent intent, int flags)
	// public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics, Intent intent, int flags)
	// public List<ResolveInfo> queryIntentServices(Intent intent, int flags)
	// frameworks/base/core/java/android/app/ApplicationPackageManager.java
	
	// @formatter:on

	private enum Methods {
		getInstalledApplications, getInstalledPackages, getPreferredPackages, queryBroadcastReceivers, queryContentProviders, queryIntentActivities, queryIntentActivityOptions, queryIntentServices
	};

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		for (Methods am : Methods.values())
			listHook.add(new XApplicationPackageManager(am, PrivacyManager.cSystem));
		return listHook;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		// Do nothing
	}

	@Override
	protected void after(MethodHookParam param) throws Throwable {
		if (mMethod == Methods.getInstalledApplications) {
			if (param.getResult() != null && isRestricted(param))
				param.setResult(new ArrayList<ApplicationInfo>());
		} else if (mMethod == Methods.getInstalledPackages || mMethod == Methods.getPreferredPackages) {
			if (param.getResult() != null && isRestricted(param))
				param.setResult(new ArrayList<PackageInfo>());
		} else if (mMethod == Methods.queryBroadcastReceivers || mMethod == Methods.queryIntentActivities
				|| mMethod == Methods.queryIntentActivityOptions || mMethod == Methods.queryIntentServices) {
			if (param.getResult() != null && isRestricted(param))
				param.setResult(new ArrayList<ResolveInfo>());
		} else if (mMethod == Methods.queryContentProviders) {
			if (param.getResult() != null && isRestricted(param))
				param.setResult(new ArrayList<ProviderInfo>());
		} else
			Util.log(this, Log.WARN, "Unknown method=" + param.method.getName());
	}

	@Override
	protected boolean isRestricted(MethodHookParam param) throws Throwable {
		Context context = null;
		try {
			Field fieldContext = findField(param.thisObject.getClass(), "mContext");
			context = (Context) fieldContext.get(param.thisObject);
		} catch (Throwable ex) {
			Util.bug(this, ex);
		}
		int uid = Binder.getCallingUid();
		return getRestricted(context, uid, true);
	}
}
