package biz.bokhorst.xprivacy;

import me.piebridge.util.GingerBreadUtil;

import android.os.Build;
import android.support.v7.app.ActionBarActivity;

public class Activity extends ActionBarActivity {
	@Override
	public void recreate() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			super.recreate();
		} else {
			GingerBreadUtil.recreate(this);
		}
	}

	@Override
	public void invalidateOptionsMenu() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			super.invalidateOptionsMenu();
		}
	}
}
