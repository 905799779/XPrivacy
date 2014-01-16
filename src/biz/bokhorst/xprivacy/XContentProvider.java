package biz.bokhorst.xprivacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.Context;
import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XContentProvider extends XHook {

	private String mClassName;
	private String mUriStart;

	private XContentProvider(String restrictionName, String providerName, String className) {
		super(restrictionName, "query", providerName);
		mClassName = className;
		mUriStart = null;
	}

	private XContentProvider(String restrictionName, String providerName, String className, String uriStart) {
		super(restrictionName, "query", uriStart.replace("content://com.android.", ""));
		mClassName = className;
		mUriStart = uriStart;
	}

	public String getClassName() {
		return mClassName;
	}

	// @formatter:off

	// public abstract Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	// public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal)
	// frameworks/base/core/java/android/content/ContentProvider.java
	// http://developer.android.com/reference/android/content/ContentProvider.html

	// packages/apps/Browser/src/com/android/browser/provider/BrowserProvider2.java
	// packages/providers/CalendarProvider/src/com/android/providers/calendar/CalendarProvider2.java
	// packages/providers/ContactsProvider/src/com/android/providers/contacts/ContactsProvider2.java
	// packages/providers/ContactsProvider/src/com/android/providers/contacts/CallLogProvider.java
	// packages/providers/ContactsProvider/src/com/android/providers/contacts/VoicemailContentProvider.java
	// packages/providers/TelephonyProvider/src/com/android/providers/telephony/SmsProvider.java
	// packages/providers/TelephonyProvider/src/com/android/providers/telephony/MmsProvider.java
	// packages/providers/TelephonyProvider/src/com/android/providers/telephony/MmsSmsProvider.java
	// packages/providers/TelephonyProvider/src/com/android/providers/telephony/TelephonyProvider.java

	// frameworks/base/core/java/android/provider/ContactsContract.java

	// @formatter:on

	public static List<XHook> getInstances(String packageName) {
		List<XHook> listHook = new ArrayList<XHook>();

		// Applications provider
		if (packageName.equals("com.android.providers.applications"))
			listHook.add(new XContentProvider(PrivacyManager.cSystem, "ApplicationsProvider",
					"com.android.providers.applications.ApplicationsProvider"));

		// Browser provider
		else if (packageName.equals("com.android.browser")) {
			listHook.add(new XContentProvider(PrivacyManager.cBrowser, "BrowserProvider",
					"com.android.browser.provider.BrowserProvider"));
			listHook.add(new XContentProvider(PrivacyManager.cBrowser, "BrowserProvider2",
					"com.android.browser.provider.BrowserProvider2"));
		}

		// Calendar provider
		else if (packageName.equals("com.android.providers.calendar"))
			listHook.add(new XContentProvider(PrivacyManager.cCalendar, "CalendarProvider2",
					"com.android.providers.calendar.CalendarProvider2"));

		// Contacts provider
		else if (packageName.equals("com.android.providers.contacts")) {
			String[] uris = new String[] { "contacts/contacts", "contacts/data", "contacts/raw_contacts",
					"contacts/phone_lookup", "contacts/profile" };
			for (String uri : uris)
				listHook.add(new XContentProvider(PrivacyManager.cContacts, "ContactsProvider2",
						"com.android.providers.contacts.ContactsProvider2", "content://com.android." + uri));

			listHook.add(new XContentProvider(PrivacyManager.cPhone, "CallLogProvider",
					"com.android.providers.contacts.CallLogProvider"));
			listHook.add(new XContentProvider(PrivacyManager.cMessages, "VoicemailContentProvider",
					"com.android.providers.contacts.VoicemailContentProvider"));
		}

		// Contacts provider of Motorola's Blur
		else if (packageName.equals("com.motorola.blur.providers.contacts")) {
			String[] uris = new String[] { "contacts/contacts", "contacts/data", "contacts/raw_contacts",
					"contacts/phone_lookup", "contacts/profile" };
			for (String uri : uris)
				listHook.add(new XContentProvider(PrivacyManager.cContacts, "ContactsProvider2",
						"com.android.providers.contacts.ContactsProvider2", "content://com.android." + uri));

			listHook.add(new XContentProvider(PrivacyManager.cPhone, "BlurCallLogProvider",
					"com.motorola.blur.providers.contacts.BlurCallLogProvider"));
		}

		// E-mail provider
		else if (packageName.equals("com.android.email"))
			listHook.add(new XContentProvider(PrivacyManager.cEMail, "EMailProvider",
					"com.android.email.provider.EmailProvider"));

		// Google services provider
		else if (packageName.equals("com.google.android.gsf"))
			listHook.add(new XContentProvider(PrivacyManager.cIdentification, "GservicesProvider",
					"com.google.android.gsf.gservices.GservicesProvider"));

		// Telephony providers
		else if (packageName.equals("com.android.providers.telephony")) {
			listHook.add(new XContentProvider(PrivacyManager.cMessages, "SmsProvider",
					"com.android.providers.telephony.SmsProvider"));
			listHook.add(new XContentProvider(PrivacyManager.cMessages, "MmsProvider",
					"com.android.providers.telephony.MmsProvider"));
			listHook.add(new XContentProvider(PrivacyManager.cMessages, "MmsSmsProvider",
					"com.android.providers.telephony.MmsSmsProvider"));
			listHook.add(new XContentProvider(PrivacyManager.cPhone, "TelephonyProvider",
					"com.android.providers.telephony.TelephonyProvider"));
		}

		// User dictionary
		else if (packageName.equals("com.android.providers.userdictionary"))
			listHook.add(new XContentProvider(PrivacyManager.cDictionary, "UserDictionary",
					"com.android.providers.userdictionary.UserDictionaryProvider"));

		return listHook;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		// Do nothing
	}

	@Override
	@SuppressLint("DefaultLocale")
	protected void after(MethodHookParam param) throws Throwable {
		// Check uri
		Uri uri = (param.args.length > 0 ? (Uri) param.args[0] : null);
		String sUri = (uri == null ? null : uri.toString().toLowerCase());
		if (sUri != null && (mUriStart == null || sUri.startsWith(mUriStart))) {
			Cursor cursor = (Cursor) param.getResult();
			if (cursor != null)
				if (sUri.startsWith("content://com.google.android.gsf.gservices")) {
					// Google services provider: block only android_id
					if (param.args.length > 3 && param.args[3] != null) {
						List<String> selectionArgs = Arrays.asList((String[]) param.args[3]);
						if (Util.containsIgnoreCase(selectionArgs, "android_id"))
							if (isRestricted(param)) {
								MatrixCursor gsfCursor = new MatrixCursor(cursor.getColumnNames());
								gsfCursor.addRow(new Object[] { "android_id",
										PrivacyManager.getDefacedProp(Binder.getCallingUid(), "GSF_ID") });
								gsfCursor.respond(cursor.getExtras());
								param.setResult(gsfCursor);
								cursor.close();
							}
					}

				} else if (sUri.startsWith("content://com.android.contacts")
						&& !sUri.startsWith("content://com.android.contacts/profile")) {
					// Contacts provider: allow selected contacts
					if (isRestricted(param)) {
						// Get contact ID index
						int iid = -1;
						if (sUri.startsWith("content://com.android.contacts/contacts"))
							iid = cursor.getColumnIndex("_id");
						else if (sUri.startsWith("content://com.android.contacts/data"))
							iid = cursor.getColumnIndex("contact_id");
						else if (sUri.startsWith("content://com.android.contacts/phone_lookup"))
							iid = cursor.getColumnIndex("_id");
						else if (sUri.startsWith("content://com.android.contacts/raw_contacts"))
							iid = cursor.getColumnIndex("contact_id");

						// Get raw contact ID index
						int irawid = -1;
						if (sUri.startsWith("content://com.android.contacts/contacts"))
							irawid = cursor.getColumnIndex("name_raw_contact_id");
						else if (sUri.startsWith("content://com.android.contacts/data"))
							irawid = cursor.getColumnIndex("raw_contact_id");
						else if (sUri.startsWith("content://com.android.contacts/phone_lookup"))
							irawid = cursor.getColumnIndex("name_raw_contact_id");
						else if (sUri.startsWith("content://com.android.contacts/raw_contacts"))
							irawid = cursor.getColumnIndex("_id");

						MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
						while (cursor.moveToNext()) {
							// Get contact ID
							long id = (iid < 0 ? -1 : cursor.getLong(iid));
							long rawid = (irawid < 0 ? -1 : cursor.getLong(irawid));

							// Check if can be copied
							boolean copy = false;
							if (id >= 0)
								copy = PrivacyManager.getSettingBool(this, null, 0,
										String.format("Contact.%d.%d", Binder.getCallingUid(), id), false, true);
							if (!copy && rawid >= 0)
								copy = PrivacyManager.getSettingBool(this, null, 0,
										String.format("RawContact.%d.%d", Binder.getCallingUid(), rawid), false, true);

							// Conditionally copy row
							if (copy)
								copyColumns(cursor, result);
						}
						result.respond(cursor.getExtras());
						param.setResult(result);
						cursor.close();
					}

				} else if (sUri.startsWith("content://applications")) {
					// Applications provider: allow selected applications
					if (isRestricted(param)) {
						MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
						while (cursor.moveToNext()) {
							int colPackage = cursor.getColumnIndex("package");
							String packageName = (colPackage < 0 ? null : cursor.getString(colPackage));
							if (packageName != null && XPackageManager.isPackageAllowed(packageName))
								copyColumns(cursor, result);
						}
						result.respond(cursor.getExtras());
						param.setResult(result);
						cursor.close();
					}
				} else {
					if (isRestricted(param)) {
						// Return empty cursor
						MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
						result.respond(cursor.getExtras());
						param.setResult(result);
						cursor.close();
					}
				}
		}
	}

	/**
	 * Returns data type of the given object's value.
	 * Copy from the android.database.DatabaseUtils
	 * @param object the object whose value type is to be returned
	 * @return object value type
	 */
	public static int getTypeOfObject(Object object) {
		if (object == null) {
			return Cursor.FIELD_TYPE_NULL;
		} else if (object instanceof byte[]) {
			return Cursor.FIELD_TYPE_BLOB;
		} else if (object instanceof Float || object instanceof Double) {
			return Cursor.FIELD_TYPE_FLOAT;
		} else if (object instanceof Long || object instanceof Integer || object instanceof Short || object instanceof Byte) {
			return Cursor.FIELD_TYPE_INTEGER;
		} else {
			return Cursor.FIELD_TYPE_STRING;
		}
	}

	/**
	 * Returns data type of the given cursor's column value
	 * @param cursor the cursor
	 * @param index the zero-based index of the target column.
	 * @return column value type
	 */
	public static int getTypeOfCursor(Cursor cursor, int index) {
		if (cursor.isNull(index)) {
			return Cursor.FIELD_TYPE_NULL;
		} else if (cursor instanceof AbstractWindowedCursor) {
			if (((AbstractWindowedCursor) cursor).isLong(index)) {
				return Cursor.FIELD_TYPE_INTEGER;
			} else if (((AbstractWindowedCursor) cursor).isFloat(index)) {
				return Cursor.FIELD_TYPE_FLOAT;
			} else if (((AbstractWindowedCursor) cursor).isString(index)) {
				return Cursor.FIELD_TYPE_STRING;
			} else if (((AbstractWindowedCursor) cursor).isBlob(index)) {
				return Cursor.FIELD_TYPE_BLOB;
			}
		} else if (cursor instanceof MatrixCursor) {
			try {
				Method method = cursor.getClass().getDeclaredMethod("get", int.class);
				method.setAccessible(true);
				return getTypeOfObject(method.invoke(cursor, index));
			} catch (Exception e) {
				// do nothing
			}
		} else {
			// for CursorWrapper, MergeCursor
			try {
				Field field = cursor.getClass().getDeclaredField("mCursor");
				field.setAccessible(true);
				return getTypeOfCursor((Cursor) field.get(cursor), index);
			} catch (Exception e) {
				// do nothing
			}
		}
		return -1;
	}

	private void copyColumns(Cursor cursor, MatrixCursor result) {
		try {
			Object[] columns = new Object[cursor.getColumnCount()];
			for (int i = 0; i < cursor.getColumnCount(); i++) {
				int type = -1;
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
					type = cursor.getType(i);
				} else {
					type = getTypeOfCursor(cursor, i);
				}
				switch (type) {
				case Cursor.FIELD_TYPE_NULL:
					columns[i] = null;
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					columns[i] = cursor.getInt(i);
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					columns[i] = cursor.getFloat(i);
					break;
				case Cursor.FIELD_TYPE_STRING:
					columns[i] = cursor.getString(i);
					break;
				case Cursor.FIELD_TYPE_BLOB:
					columns[i] = cursor.getBlob(i);
					break;
				default:
					Util.log(this, Log.WARN, "Unknown cursor data type=" + type);
				}
			}
			result.addRow(columns);
		} catch (Throwable ex) {
			Util.bug(this, ex);
		}
	}

	@Override
	protected boolean isRestricted(MethodHookParam param) throws Throwable {
		ContentProvider contentProvider = (ContentProvider) param.thisObject;
		Context context = contentProvider.getContext();
		int uid = Binder.getCallingUid();
		return getRestricted(context, uid, true);
	}
}
