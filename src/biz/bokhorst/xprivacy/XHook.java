package biz.bokhorst.xprivacy;

import android.os.Binder;
import android.os.Build;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public abstract class XHook {
	private String mRestrictionName;
	private String mMethodName;
	private String mSpecifier;
	private int mSdk;

	protected XHook(String restrictionName, String methodName, String specifier) {
		mRestrictionName = restrictionName;
		mMethodName = methodName;
		mSpecifier = specifier;
		mSdk = Build.VERSION_CODES.GINGERBREAD_MR1;
		PrivacyManager.registerMethod(restrictionName, getSpecifier(), mSdk);
	}

	protected XHook(String restrictionName, String methodName, String specifier, int sdk) {
		mRestrictionName = restrictionName;
		mMethodName = methodName;
		mSpecifier = specifier;
		mSdk = sdk;
		PrivacyManager.registerMethod(restrictionName, getSpecifier(), sdk);
	}

	public boolean isVisible() {
		return true;
	}

	abstract public String getClassName();

	public String getRestrictionName() {
		return mRestrictionName;
	}

	public String getMethodName() {
		return mMethodName;
	}

	public String getSpecifier() {
		return (mSpecifier == null ? mMethodName : mSpecifier);
	}

	public int getSdk() {
		return mSdk;
	}

	abstract protected void before(MethodHookParam param) throws Throwable;

	abstract protected void after(MethodHookParam param) throws Throwable;

	protected boolean isRestricted(MethodHookParam param) throws Throwable {
		return isRestricted(param, getSpecifier());
	}

	protected boolean isRestricted(MethodHookParam param, String methodName) throws Throwable {
		int uid = Binder.getCallingUid();
		return PrivacyManager.getRestricted(this, uid, mRestrictionName, methodName, true, true);
	}

	protected boolean getRestricted(int uid, boolean usage) throws Throwable {
		return PrivacyManager.getRestricted(this, uid, mRestrictionName, getSpecifier(), usage, true);
	}

	protected boolean getRestricted(int uid, String methodName, boolean usage) throws Throwable {
		return PrivacyManager.getRestricted(this, uid, mRestrictionName, methodName, usage, true);
	}
}
