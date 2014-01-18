package biz.bokhorst.xprivacy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.UserHandle;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;

public class Util {
	private static boolean mPro = false;
	private static boolean mLog = true;
	private static boolean mLogDetermined = false;
	private static Version MIN_PRO_VERSION = new Version("1.12");
	private static String LICENSE_FILE_NAME = "XPrivacy_license.txt";

	public static void log(XHook hook, int priority, String msg) {
		// Check if logging enabled
		if (Process.myUid() > 0 && !mLogDetermined) {
			mLogDetermined = true;
			try {
				mLog = PrivacyManager.getSettingBool(null, 0, PrivacyManager.cSettingLog, false, true);
			} catch (Throwable ignored) {
				mLog = false;
			}
		}

		// Log if enabled
		if (priority != Log.DEBUG && (priority == Log.INFO ? mLog : true))
			if (hook == null)
				Log.println(priority, "XPrivacy", msg);
			else
				Log.println(priority, String.format("XPrivacy/%s", hook.getClass().getSimpleName()), msg);

		if (priority != Log.DEBUG && priority != Log.INFO)
			logData(hook, priority, msg);
	}

	public static void bug(XHook hook, Throwable ex) {
		log(hook, Log.ERROR, ex.toString() + " uid=" + Process.myUid() + "\n" + Log.getStackTraceString(ex));
	}

	public static void logStack(XHook hook) {
		log(hook, Log.WARN, Log.getStackTraceString(new Exception("StackTrace")));
	}

	public static File getDataFile() {
		return new File(Environment.getDataDirectory() + File.separator + "data" + File.separator
				+ Util.class.getPackage().getName() + File.separator + "log.txt");
	}

	public static void clearData() {
		File dataFile = getDataFile();
		dataFile.delete();
		try {
			dataFile.createNewFile();
			logData(null, Log.WARN, "Start " + Build.PRODUCT);
			dataFile.setReadable(true, false);
			dataFile.setWritable(true, false);
		} catch (Throwable ex) {
			Util.bug(null, ex);
		}
	}

	@SuppressLint("SimpleDateFormat")
	private static void logData(XHook hook, int priority, String message) {
		if (getDataFile().exists()) {
			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
			String prio;
			if (priority == Log.WARN)
				prio = "W";
			else if (priority == Log.ERROR)
				prio = "E";
			else
				prio = Integer.toString(priority);
			String tag = (hook == null ? "XPrivacy" : String.format("XPrivacy/%s", hook.getClass().getSimpleName()));

			FileWriter fw = null;
			try {
				fw = new FileWriter(getDataFile(), true);
				fw.write(String.format("%s %s/%s: %s\n", time, prio, tag, message));
				fw.flush();
			} catch (Throwable ex) {
				Util.bug(hook, ex);
			} finally {
				if (fw != null)
					try {
						fw.close();
						getDataFile().setReadable(true, false);
						getDataFile().setWritable(true, false);
					} catch (Throwable ex) {
						Util.bug(hook, ex);
					}
			}
		}
	}

	public static int getXposedAppProcessVersion() {
		final Pattern PATTERN_APP_PROCESS_VERSION = Pattern.compile(".*with Xposed support \\(version (.+)\\).*");
		try {
			InputStream is = new FileInputStream("/system/bin/app_process");
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.contains("Xposed"))
					continue;
				Matcher m = PATTERN_APP_PROCESS_VERSION.matcher(line);
				if (m.find()) {
					br.close();
					is.close();
					return Integer.parseInt(m.group(1));
				}
			}
			br.close();
			is.close();
		} catch (Throwable ex) {
		}
		return -1;
	}

	public static boolean isXposedEnabled() {
		// Will be hooked to return true
		log(null, Log.WARN, "XPrivacy not enabled");
		return false;
	}

	public static void setPro(boolean enabled) {
		mPro = enabled;
	}

	public static boolean isProEnabled() {
		return mPro;
	}

	public static String hasProLicense(Context context) {
		try {
			// Disable storage restriction
			PrivacyManager.setRestricted(null, Process.myUid(), PrivacyManager.cStorage, null, false, false);

			// Get license
			String[] license = getProLicenseUnchecked();
			if (license == null)
				return null;
			String name = license[0];
			String email = license[1];
			String signature = license[2];

			// Get bytes
			byte[] bEmail = email.getBytes("UTF-8");
			byte[] bSignature = hex2bytes(signature);
			if (bEmail.length == 0 || bSignature.length == 0) {
				Util.log(null, Log.ERROR, "Licensing: invalid file");
				return null;
			}

			// Verify license
			boolean licensed = verifyData(bEmail, bSignature, getPublicKey(context));
			if (licensed)
				Util.log(null, Log.INFO, "Licensing: ok for " + name);
			else
				Util.log(null, Log.ERROR, "Licensing: invalid for " + name);

			// Return result
			if (licensed)
				return name;
		} catch (Throwable ex) {
			Util.bug(null, ex);
		}
		return null;
	}

	@SuppressLint("NewApi")
	public static int getAppId(int uid) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			try {
				// UserHandle: public static final int getAppId(int uid)
				Method method = (Method) UserHandle.class.getDeclaredMethod("getAppId", int.class);
				uid = (Integer) method.invoke(null, uid);
			} catch (Throwable ex) {
				Util.bug(null, ex);
			}
		return uid;
	}

	@SuppressLint("NewApi")
	public static int getUserId(int uid) {
		int userId = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			try {
				// UserHandle: public static final int getUserId(int uid)
				Method method = (Method) UserHandle.class.getDeclaredMethod("getUserId", int.class);
				userId = (Integer) method.invoke(null, uid);
			} catch (Throwable ex) {
				Util.bug(null, ex);
			}
		return userId;
	}

	public static String getUserDataDirectory(int uid) {
		// Build data directory
		String dataDir = Environment.getDataDirectory() + File.separator;
		int userId = getUserId(uid);
		if (userId == 0)
			dataDir += "data";
		else
			dataDir += "user" + File.separator + userId;
		dataDir += File.separator + Util.class.getPackage().getName();
		return dataDir;
	}

	public static String[] getProLicenseUnchecked() {
		// Get license file name
		String storageDir = Environment.getExternalStorageDirectory().getAbsolutePath();
		File licenseFile = new File(storageDir + File.separator + LICENSE_FILE_NAME);
		if (!licenseFile.exists())
			licenseFile = new File(storageDir + File.separator + ".xprivacy" + File.separator + LICENSE_FILE_NAME);

		// Get imported license file name
		String importedLicense = getUserDataDirectory(Process.myUid()) + File.separator + LICENSE_FILE_NAME;

		// Import license file
		if (licenseFile.exists()) {
			try {
				File out = new File(importedLicense);
				Util.log(null, Log.WARN, "Licensing: importing " + out.getAbsolutePath());
				InputStream is = new FileInputStream(licenseFile.getAbsolutePath());
				OutputStream os = new FileOutputStream(out.getAbsolutePath());
				byte[] buffer = new byte[1024];
				int read;
				while ((read = is.read(buffer)) != -1)
					os.write(buffer, 0, read);
				is.close();
				os.flush();
				os.close();

				out.setWritable(false);
				licenseFile.delete();
			} catch (Throwable ex) {
				Util.bug(null, ex);
			}
		}

		// Check license file
		licenseFile = new File(importedLicense);
		if (licenseFile.exists()) {
			// Read license
			try {
				IniFile iniFile = new IniFile(licenseFile);
				String name = iniFile.get("name", "");
				String email = iniFile.get("email", "");
				String signature = iniFile.get("signature", "");
				return new String[] { name, email, signature };
			} catch (Throwable ex) {
				bug(null, ex);
				return null;
			}
		} else
			Util.log(null, Log.INFO, "Licensing: no license file");
		return null;
	}

	public static Version getProEnablerVersion(Context context) {
		try {
			String proPackageName = context.getPackageName() + ".pro";
			PackageManager pm = context.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(proPackageName, 0);
			return new Version(pi.versionName);
		} catch (NameNotFoundException ignored) {
		} catch (Throwable ex) {
			Util.bug(null, ex);
		}
		return null;
	}

	public static boolean isValidProEnablerVersion(Version version) {
		return (version.compareTo(MIN_PRO_VERSION) >= 0);
	}

	private static boolean hasValidProEnablerSignature(Context context) {
		return (context.getPackageManager()
				.checkSignatures(context.getPackageName(), context.getPackageName() + ".pro") == PackageManager.SIGNATURE_MATCH);
	}

	public static boolean isProEnablerInstalled(Context context) {
		Version version = getProEnablerVersion(context);
		if (version != null && isValidProEnablerVersion(version) && hasValidProEnablerSignature(context)) {
			Util.log(null, Log.INFO, "Licensing: enabler installed");
			return true;
		}
		Util.log(null, Log.INFO, "Licensing: enabler not installed");
		return false;
	}

	public static boolean hasMarketLink(Context context, String packageName) {
		try {
			PackageManager pm = context.getPackageManager();
			String installer = pm.getInstallerPackageName(packageName);
			if (installer != null)
				return installer.equals("com.android.vending") || installer.contains("google");
		} catch (Exception ex) {
			log(null, Log.WARN, ex.toString());
		}
		return false;
	}

	public static void viewUri(Context context, Uri uri) {
		// Disable view restriction
		PrivacyManager.setRestricted(null, Process.myUid(), PrivacyManager.cView, null, false, false);

		Intent infoIntent = new Intent(Intent.ACTION_VIEW);
		infoIntent.setData(uri);
		context.startActivity(infoIntent);
	}

	private static byte[] hex2bytes(String hex) {
		// Convert hex string to byte array
		int len = hex.length();
		byte[] result = new byte[len / 2];
		for (int i = 0; i < len; i += 2)
			result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
		return result;
	}

	private static PublicKey getPublicKey(Context context) throws Throwable {
		// Read public key
		String sPublicKey = "";
		InputStreamReader isr = new InputStreamReader(context.getAssets().open("XPrivacy_public_key.txt"), "UTF-8");
		BufferedReader br = new BufferedReader(isr);
		String line = br.readLine();
		while (line != null) {
			if (!line.startsWith("-----"))
				sPublicKey += line;
			line = br.readLine();
		}
		br.close();
		isr.close();

		// Create public key
		byte[] bPublicKey = Base64.decode(sPublicKey, Base64.NO_WRAP);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		X509EncodedKeySpec encodedPubKeySpec = new X509EncodedKeySpec(bPublicKey);
		return keyFactory.generatePublic(encodedPubKeySpec);
	}

	private static boolean verifyData(byte[] data, byte[] signature, PublicKey publicKey) throws Throwable {
		// Verify signature
		Signature verifier = Signature.getInstance("SHA1withRSA");
		verifier.initVerify(publicKey);
		verifier.update(data);
		return verifier.verify(signature);
	}

	public static String sha1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		// SHA1
		String salt = PrivacyManager.getSetting(null, 0, PrivacyManager.cSettingSalt, "", true);
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		byte[] bytes = (text + salt).getBytes("UTF-8");
		digest.update(bytes, 0, bytes.length);
		bytes = digest.digest();
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes)
			sb.append(String.format("%02X", b));
		return sb.toString();
	}

	public static String md5(String string) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		byte[] bytes = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes)
			sb.append(String.format("%02X", b));
		return sb.toString();
	}

	public static boolean containsIgnoreCase(List<String> strings, String value) {
		for (String string : strings)
			if (string.equalsIgnoreCase(value))
				return true;
		return false;
	}

	public static boolean isIntentAvailable(Context context, Intent intent) {
		PackageManager packageManager = context.getPackageManager();
		return (packageManager.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES).size() > 0);
	}

	public static void copy(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
			out.write(buf, 0, len);
		in.close();
		out.close();
	}

	private static SparseArray<String> mPidPkg = new SparseArray<String>();

	public static String getPackageNameByPid(int pid) {
		// This doesn't work for all processes!

		synchronized (mPidPkg) {
			String pkg = mPidPkg.get(pid);
			if (pkg != null)
				return pkg;
		}

		String pkg = null;
		try {
			byte[] buffer = new byte[256];
			File cmdLineFile = new File(String.format("/proc/%d/cmdline", pid));
			FileInputStream is = new FileInputStream(cmdLineFile);
			int len = is.read(buffer);
			is.close();

			int i = 0;
			while (i < len && buffer[i] != 0)
				i++;
			pkg = new String(buffer, 0, i);
		} catch (Throwable ex) {
			pkg = ex.getMessage();
		}

		synchronized (mPidPkg) {
			if (mPidPkg.indexOfKey(pid) < 0)
				mPidPkg.put(pid, pkg);
		}

		return pkg;
	}

	public static Bitmap[] getTriStateCheckBox(Context context) {
		Bitmap[] bitmap = new Bitmap[3];

		// Get highlight color
		TypedArray ta1 = context.getTheme()
				.obtainStyledAttributes(new int[] { R.attr.colorActivatedHighlight });
		int highlightColor = ta1.getColor(0, 0xFF00FF);
		ta1.recycle();

		// Get off check box
		TypedArray ta2 = context.getTheme().obtainStyledAttributes(
				new int[] { android.R.attr.listChoiceIndicatorMultiple });
		Drawable off = ta2.getDrawable(0);
		ta2.recycle();
		off.setBounds(0, 0, off.getIntrinsicWidth(), off.getIntrinsicHeight());

		// Get check mark
		Drawable checkmark = context.getResources().getDrawable(R.drawable.checkmark);
		checkmark.setBounds(0, 0, off.getIntrinsicWidth(), off.getIntrinsicHeight());
		checkmark.setColorFilter(highlightColor, Mode.SRC_ATOP);

		// Get check mark outline
		Drawable checkmarkOutline = context.getResources().getDrawable(R.drawable.checkmark_outline);
		checkmarkOutline.setBounds(0, 0, off.getIntrinsicWidth(), off.getIntrinsicHeight());

		// Create off check box
		bitmap[0] = Bitmap.createBitmap(off.getIntrinsicWidth(), off.getIntrinsicHeight(), Config.ARGB_8888);
		Canvas canvas0 = new Canvas(bitmap[0]);
		off.draw(canvas0);

		// Create half check box
		bitmap[1] = Bitmap.createBitmap(off.getIntrinsicWidth(), off.getIntrinsicHeight(), Config.ARGB_8888);
		Canvas canvas1 = new Canvas(bitmap[1]);
		off.draw(canvas1);
		Paint paint1 = new Paint();
		paint1.setStyle(Paint.Style.FILL);
		paint1.setColor(highlightColor);
		float wborder = off.getIntrinsicWidth() / 3f;
		float hborder = off.getIntrinsicHeight() / 3f;
		canvas1.drawRect(wborder, hborder, off.getIntrinsicWidth() - wborder, off.getIntrinsicHeight() - hborder,
				paint1);

		// Create full check box
		bitmap[2] = Bitmap.createBitmap(off.getIntrinsicWidth(), off.getIntrinsicHeight(), Config.ARGB_8888);
		Canvas canvas2 = new Canvas(bitmap[2]);
		off.draw(canvas2);
		checkmark.draw(canvas2);
		checkmarkOutline.draw(canvas2);

		return bitmap;
	}

	public static int getThemed(Context context, int attr) {
		TypedValue tv = new TypedValue();
		context.getTheme().resolveAttribute(attr, tv, true);
		return tv.resourceId;
	}
}
