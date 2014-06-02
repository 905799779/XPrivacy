package biz.bokhorst.xprivacy;

import me.piebridge.util.GingerBreadUtil;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

public class Activity extends ActionBarActivity {

	private static String BUNDLE = "activity_compat_bundle";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1 && getIntent().hasExtra(BUNDLE)
				&& savedInstanceState == null) {
			savedInstanceState = getIntent().getExtras().getBundle(BUNDLE);
		}
		super.onCreate(savedInstanceState);
	}

	@Override
	public void recreate() {
		GingerBreadUtil.recreate(this);
	}

	@Override
	public void invalidateOptionsMenu() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			super.invalidateOptionsMenu();
		}
	}
}
