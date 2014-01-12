package biz.bokhorst.xprivacy;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Binder;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XNfcAdapter extends XHook {
	private Methods mMethod;

	protected XNfcAdapter(Methods method, String restrictionName) {
		super(restrictionName, method.name(), null);
		mMethod = method;
	}

	@Override
	public String getClassName() {
		return "android.nfc.NfcAdapter";
	}

	private enum Methods {
		getDefaultAdapter, getNfcAdapter
	};

	// public static NfcAdapter getDefaultAdapter()
	// public static NfcAdapter getDefaultAdapter(Context context)
	// public static synchronized NfcAdapter getNfcAdapter(Context context)
	// frameworks/base/core/java/android/nfc/NfcAdapter.java
	// http://developer.android.com/reference/android/nfc/NfcAdapter.html

	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();
		listHook.add(new XNfcAdapter(Methods.getDefaultAdapter, PrivacyManager.cNfc));
		listHook.add(new XNfcAdapter(Methods.getNfcAdapter, PrivacyManager.cNfc));
		return listHook;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		if (mMethod == Methods.getDefaultAdapter || mMethod == Methods.getNfcAdapter) {
			if (isRestricted(param))
				param.setResult(null);
		} else
			Util.log(this, Log.WARN, "Unknown method=" + param.method.getName());
	}

	@Override
	protected void after(MethodHookParam param) throws Throwable {
		// Do nothing
	}

	@Override
	protected boolean isRestricted(MethodHookParam param) throws Throwable {
		Context context = null;
		if (param.args.length > 0)
			context = (Context) param.args[0];
		int uid = Binder.getCallingUid();
		return getRestricted(context, uid, true);
	}
}
