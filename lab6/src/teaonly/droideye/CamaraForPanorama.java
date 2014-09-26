package teaonly.droideye;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.http.conn.util.InetAddressUtils;
import org.json.simple.JSONArray;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.StrictMode;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class CamaraForPanorama extends Activity implements
		CameraView.CameraReadyCallback, OverlayView.UpdateDoneCallback {

	SurfaceView svCameraForPanorama;
	HttpCameraPreview cameraReview;

	TextView tvCameraForPanorama_MyIp;
	TextView tvCameraForPanorama_ReceiveIp;

	private static final String TAG = "TEAONLY";

	boolean inProcessing = false;
	final int maxVideoNumber = 3;
	VideoFrame[] videoFrames = new VideoFrame[maxVideoNumber];
	byte[] preFrame = new byte[1024 * 1024 * 8];

	TeaServer webServer = null;
	private CameraView cameraView_;

	private StreamingLoop audioLoop = null;

	private int viewWidth;
	private int viewHeight;

	private HttpCameraPreview cameraPreview;

	private JSONArray jsonArray;
	private DirectoryClient dc;

	private NfcAdapter mAdapter;
	private NdefMessage mMessage;

	private PendingIntent mPendingIntent;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;

	private TextView mText, mReadTag, alertTest;

	public static String sourceMsg;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.camera_for_panorama);

		tvCameraForPanorama_ReceiveIp = (TextView) findViewById(R.id.tvCameraForPanorama_ReceiveIp);
		tvCameraForPanorama_MyIp = (TextView) findViewById(R.id.tvCameraForPanorama_MyIp);

		Window win = getWindow();
		win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
				.permitAll().build();
		StrictMode.setThreadPolicy(policy);

		calculateDisplayDimensions();

		for (int i = 0; i < maxVideoNumber; i++) {
			videoFrames[i] = new VideoFrame(1024 * 1024 * 2);
		}

//		System.loadLibrary("natpmp");

		initCamera();

		// Init NFC

		sourceMsg = getLocalIpAddress();

		mText = (TextView) findViewById(R.id.tvCameraForPanorama_MyIp);
		mReadTag = (TextView) findViewById(R.id.tvCameraForPanorama_ReceiveIp);

		mAdapter = NfcAdapter.getDefaultAdapter(this);

		if (mAdapter != null) {
			mText.setText("" + sourceMsg + "");
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

	private void calculateDisplayDimensions() {
		Display display = getWindowManager().getDefaultDisplay();
		viewWidth = display.getWidth();
		viewHeight = display.getHeight();
	}

	public void onCameraReady() {
		if (initWebServer()) {
			int wid = cameraView_.Width();
			int hei = cameraView_.Height();
			cameraView_.StopPreview();
			cameraView_.setupCamera(wid, hei, previewCb_);
			cameraView_.StartPreview();

			// cameraPreview.setVisibility(View.INVISIBLE);
			// cameraPreview.setDimension((int) (viewWidth * 0.35),
			// (int) (viewHeight * 0.45));
			//
			// cameraPreview.setUrl("http://" + "" + ":8080/stream/live.jpg");
			// cameraPreview.setVisibility(View.VISIBLE);
		}
	}

	public void onUpdateDone() {
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mAdapter != null) {
			mAdapter.setNdefPushMessage(mMessage, this);
			mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters,
					mTechLists);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		inProcessing = true;
		if (webServer != null)
			webServer.stop();
		cameraView_.StopPreview();
		if (mAdapter != null) {
			mAdapter.setNdefPushMessage(mMessage, this);
			mAdapter.disableForegroundDispatch(this);
		}
		finish();
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}

	public boolean onTouch(View v, MotionEvent evt) {

		return false;
	}

	private void initCamera() {
		SurfaceView cameraSurface = (SurfaceView) findViewById(R.id.svCameraForPanorama);
		cameraView_ = new CameraView(cameraSurface);
		cameraView_.setCameraReadyCallback(this);
	}

	private boolean initWebServer() {
		String ipAddr = getLocalIpAddress();
		if (ipAddr != null) {
			try {
				webServer = new TeaServer(8080, this);
				webServer.registerCGI("/cgi/query", doQuery);
				webServer.registerCGI("/cgi/setup", doSetup);
				webServer.registerCGI("/stream/live.jpg", doCapture);
			} catch (IOException e) {
				webServer = null;
			}
		}
		if (webServer != null) {

			return true;
		} else {
			return false;
		}

	}

	private OnClickListener exitAction = new OnClickListener() {
		public void onClick(View v) {
			onPause();
		}
	};

	private PreviewCallback previewCb_ = new PreviewCallback() {
		public void onPreviewFrame(byte[] frame, Camera c) {
			if (!inProcessing) {
				inProcessing = true;

				int picWidth = cameraView_.Width();
				int picHeight = cameraView_.Height();
				ByteBuffer bbuffer = ByteBuffer.wrap(frame);
				bbuffer.get(preFrame, 0, picWidth * picHeight + picWidth
						* picHeight / 2);

				inProcessing = false;
			}
		}
	};

	private TeaServer.CommonGatewayInterface doQuery = new TeaServer.CommonGatewayInterface() {
		public String run(Properties parms) {
			String ret = "";
			List<Camera.Size> supportSize = cameraView_
					.getSupportedPreviewSize();
			ret = ret + "" + cameraView_.Width() + "x" + cameraView_.Height()
					+ "|";
			for (int i = 0; i < supportSize.size() - 1; i++) {
				ret = ret + "" + supportSize.get(i).width + "x"
						+ supportSize.get(i).height + "|";
			}
			int i = supportSize.size() - 1;
			ret = ret + "" + supportSize.get(i).width + "x"
					+ supportSize.get(i).height;
			return ret;
		}

		public InputStream streaming(Properties parms) {
			return null;
		}
	};

	private TeaServer.CommonGatewayInterface doSetup = new TeaServer.CommonGatewayInterface() {
		public String run(Properties parms) {
			int wid = Integer.parseInt(parms.getProperty("wid"));
			int hei = Integer.parseInt(parms.getProperty("hei"));
			Log.d("TEAONLY", ">>>>>>>run in doSetup wid = " + wid + " hei="
					+ hei);
			cameraView_.StopPreview();
			cameraView_.setupCamera(wid, hei, previewCb_);
			cameraView_.StartPreview();
			return "OK";
		}

		public InputStream streaming(Properties parms) {
			return null;
		}
	};

	private TeaServer.CommonGatewayInterface doCapture = new TeaServer.CommonGatewayInterface() {
		public String run(Properties parms) {
			return null;
		}

		public InputStream streaming(Properties parms) {
			VideoFrame targetFrame = null;
			for (int i = 0; i < maxVideoNumber; i++) {
				if (videoFrames[i].acquire()) {
					targetFrame = videoFrames[i];
					break;
				}
			}
			// return 503 internal error
			if (targetFrame == null) {
				Log.d("TEAONLY", "No free videoFrame found!");
				return null;
			}

			// compress yuv to jpeg
			int picWidth = cameraView_.Width();
			int picHeight = cameraView_.Height();
			YuvImage newImage = new YuvImage(preFrame, ImageFormat.NV21,
					picWidth, picHeight, null);
			targetFrame.reset();
			boolean ret;
			inProcessing = true;
			try {
				ret = newImage.compressToJpeg(new Rect(0, 0, picWidth,
						picHeight), 30, targetFrame);
			} catch (Exception ex) {
				ret = false;
			}
			inProcessing = false;

			// compress success, return ok
			if (ret == true) {
				parms.setProperty("mime", "image/jpeg");
				InputStream ins = targetFrame.getInputStream();
				return ins;
			}
			// send 503 error
			targetFrame.release();

			return null;
		}
	};

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

		// alertTest.setText("�쒓렇 �ㅼ틪");

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
				recordStr = "TEXT : " + IP + "\n";
				Call.setState(Call.State.OUTGOING);
				Intent callOutgoingIntent = new Intent(getApplicationContext(),
						ActivityCallOutgoing.class);
				startActivity(callOutgoingIntent);
				finish();
				break;
			}

			Log.d(TAG, "record string : " + recordStr);
			mReadTag.setText(recordStr);
			// mReadTag.invalidate();
		}

		return size;
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

//	static private native String nativeQueryInternet();
}
