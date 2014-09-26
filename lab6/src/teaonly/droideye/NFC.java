package teaonly.droideye;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class NFC extends Activity {
	public static final String TAG = "NFCS";
	private NfcAdapter mAdapter;
	private NdefMessage mMessage;

	private PendingIntent mPendingIntent;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;

	private TextView mText, mReadTag, alertTest;

	public static String sourceMsg;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sourceMsg = getLocalIpAddress();
		setContentView(R.layout.nfc);
		
		mText = (TextView) findViewById(R.id.text);
		mReadTag = (TextView) findViewById(R.id.readTag);
		//alertTest = (TextView)findViewById(R.id.aletText);
		
		mAdapter = NfcAdapter.getDefaultAdapter(this);

		if (mAdapter != null) {
			mText.setText(""+ sourceMsg
					+ "");
		} else {
			mText.setText("");
		}
		
		
		Intent targetIntent = new Intent(this, NFC.class);
		targetIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		mPendingIntent = PendingIntent.getActivity(this, 0, targetIntent, 0);

		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndef.addDataType("text/plain");
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}

		mFilters = new IntentFilter[] { ndef, };

		mTechLists = new String[][] { new String[] { NfcF.class.getName() } };

		Intent passedIntent = getIntent();
		if (passedIntent != null) {
			String action = passedIntent.getAction();
			if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
				processTag(passedIntent);
			}

		}

		mMessage = new NdefMessage(new NdefRecord[] { createTextRecord(
				sourceMsg, Locale.ENGLISH, true) });
	}
	public void onNewIntent(Intent passedIntent) {
		// NFC �쒓렇
		Tag tag = passedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tag != null) {
			byte[] tagId = tag.getId();
		}

		if (passedIntent != null) {
			processTag(passedIntent); // processTag 硫붿냼���몄텧
		}
	}
	private void processTag(Intent passedIntent) {

		Parcelable[] rawMsgs = passedIntent
				.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		if (rawMsgs == null) {
			return;
		}

		//alertTest.setText("�쒓렇 �ㅼ틪");

		NdefMessage[] msgs;
		if (rawMsgs != null) {
			msgs = new NdefMessage[rawMsgs.length];
			for (int i = 0; i < rawMsgs.length; i++) {
				msgs[i] = (NdefMessage) rawMsgs[i];
				showTag(msgs[i]);
			}
		}

		// showDialog(SHOW_PUSH_CONFIRM);
	}

	private int showTag(NdefMessage mMessage) {
		List<ParsedRecord> records = NdefMessageParser.parse(mMessage);
		final int size = records.size();
		for (int i = 0; i < size; i++) {
			ParsedRecord record = records.get(i);

			int recordType = record.getType();
			String recordStr = "";
			String IP;
			if (recordType == ParsedRecord.TYPE_TEXT) {
				IP = ((TextRecord) record).getText();
				Call.setIpForPanorama(IP);
				Call.setPanorama(true);
				recordStr = "TEXT : " +  IP + "\n";
				Call.setState(Call.State.OUTGOING);
				Intent callOutgoingIntent = new Intent(getApplicationContext(),
						ActivityCallOutgoing.class);
				startActivity(callOutgoingIntent);
				finish();
				break;
			} 
			
			Log.d(TAG, "record string : " + recordStr);
			mReadTag.setText(recordStr);
			//mReadTag.invalidate();
		}

		return size;
	}

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		if (mAdapter != null) {
			mAdapter.setNdefPushMessage(mMessage, this);
			mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters,
					mTechLists);
		}
	}

	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if (mAdapter != null) {
			mAdapter.setNdefPushMessage(mMessage, this);
			mAdapter.disableForegroundDispatch(this);
		}
	}


	public static NdefRecord createTextRecord(String text, Locale locale,
			boolean encodeInUtf8) {
		byte[] langBytes = locale.getLanguage().getBytes(
				Charset.forName("US-ASCII"));

		Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset
				.forName("UTF-16");
		byte[] textBytes = text.getBytes(utfEncoding);

		int utfBit = encodeInUtf8 ? 0 : (1 << 7);
		char status = (char) (utfBit + langBytes.length);

		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length,
				textBytes.length);

		return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT,
				new byte[0], data);
	}

	public String getLocalIpAddress() {
		WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
		DhcpInfo dhcpInfo = wm.getDhcpInfo();
		int serverIp = dhcpInfo.ipAddress;

		String ipAddress = String.format("%d.%d.%d.%d", (serverIp & 0xff),
				(serverIp >> 8 & 0xff), (serverIp >> 16 & 0xff),
				(serverIp >> 24 & 0xff));
		return ipAddress;
	}
}
