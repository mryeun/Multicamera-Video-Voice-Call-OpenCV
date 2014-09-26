package teaonly.droideye;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.http.conn.util.InetAddressUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import android.app.Activity;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import edu.purdue.cs252.lab6.DirectoryCommand;
import edu.purdue.cs252.lab6.User;

public class MainActivity extends Activity implements View.OnTouchListener,
		CameraView.CameraReadyCallback, OverlayView.UpdateDoneCallback {
	private static final String TAG = "TEAONLY";

	private Activity thisActivity;
	private VoipApp appState;

	boolean inProcessing = false;
	final int maxVideoNumber = 3;
	VideoFrame[] videoFrames = new VideoFrame[maxVideoNumber];
	byte[] preFrame = new byte[1024 * 1024 * 8];

	TeaServer webServer = null;
	private CameraView cameraView_;
	private Button btnExit;
	private TextView tvMessage1;
	private TextView tvMessage2;

	private StreamingLoop audioLoop = null;

	private int viewWidth;
	private int viewHeight;

	private HttpCameraPreview cameraPreview[];

	private TextView tvIPAddress;
	private Button ButtonCallEnd;

	JSONArray jsonArray;
	DirectoryClient dc;

//	public static native String nativeQueryInternet();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main_activity);
		cameraPreview = new HttpCameraPreview[3];

		Call.setState(Call.State.ONGOING); // sets call state
		Object objTemp = JSONValue.parse(Call.getUsernames());
		jsonArray = (JSONArray) objTemp;

		appState = (VoipApp) getApplicationContext(); // creates application
		// context
		thisActivity = MainActivity.this;

		dc = appState.getDirectoryClient(); // create
		// directory

		final String server = dc.getServer();
		// dc.call_ready(); //the call is now ready

		Log.i(TAG, "S_REDIRECT_READY");
		VoiceCaptureClient voiceCaptureClient = new VoiceCaptureClient(server,
				Call.getPort());
		appState.setVoiceCaptureClient(voiceCaptureClient);
		voiceCaptureClient.start(); // starts voice capture client

		Handler callOngoingHandler = new Handler() { // preforms tasks based on
			// what is sent from the
			// server
			public void handleMessage(Message msg) {
				Log.i(TAG, "callOngoingHandler");
				if (msg.what == DirectoryCommand.S_REDIRECT_READY.getCode()) {
					Log.i(TAG, "S_REDIRECT_READY");
					VoiceCaptureClient voiceCaptureClient = new VoiceCaptureClient(
							server, Call.getPort());
					appState.setVoiceCaptureClient(voiceCaptureClient);
					voiceCaptureClient.start(); // starts voice capture client
				} else if (msg.what == DirectoryCommand.S_CALL_INCOMING
						.getCode()) {
					// ignore
				} else if (msg.what == DirectoryCommand.S_CALL_DISCONNECT
						.getCode()) { // if disconnecting
					Call.setState(Call.State.IDLE); // call state is idle
					returnToDirectory();
				} else if (msg.what == DirectoryCommand.S_STATUS_OK.getCode()) {
					if (msg.obj.equals(DirectoryCommand.C_CALL_HANGUP)) { // when
						// hanging
						// up
						Call.setState(Call.State.IDLE);
						returnToDirectory(); // returns to user screen
					}
				} else {
					// unrecognized message
					Log.e(TAG, "unrecognized message " + msg.what);
					if (msg.obj != null)
						Log.e(TAG, msg.obj.toString());
					// TODO: handle error
				}
			}
		};
		dc.setReadHandler(callOngoingHandler);

		// Have the WindowManager filter out touch events that are "too fat".
		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

		final Button buttonCallEnd = (Button) findViewById(R.id.ButtonCallEnd);
		buttonCallEnd.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// TODO: Code to end call (notify other caller, stop voice
				// capture & voice player)
				dc.call_hangup(Call.getUsernames());
			}
		});
		Window win = getWindow();
		// requestWindowFeature(Window.FEATURE_NO_TITLE);

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

		cameraPreview[0] = (HttpCameraPreview) findViewById(R.id.sur_1);
		cameraPreview[1] = (HttpCameraPreview) findViewById(R.id.sur_2);
		cameraPreview[2] = (HttpCameraPreview) findViewById(R.id.sur_3);

		tvIPAddress = (TextView) findViewById(R.id.tvIPAddress);
		tvIPAddress.setText(getLocalIpAddress());
	}

	void returnToDirectory() {
		VoiceCaptureClient vcc = appState.getVoiceCaptureClient(); // gets app
																	// state
		VoicePlayerServer vps = appState.getVoicePlayerServer();
		if (vcc != null) {
			appState.getVoiceCaptureClient().close();
			appState.getVoiceCaptureClient().interrupt();
		}
		if (vps != null) {
			appState.getVoicePlayerServer().close();
			appState.getVoicePlayerServer().interrupt();
		}
		appState.setVoicePlayerServer(null);
		appState.setVoiceCaptureClient(null);
		Intent directoryIntent = new Intent(thisActivity.getBaseContext(),
				ActivityDirectory.class);
		directoryIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(directoryIntent);
		// finish();
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

			Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				public void run() {
					int j = 0;
					int size = jsonArray.size();

					for (int i = 0; i < size; i++) {
						String username = jsonArray.get(i).toString();
						if (appState.getUser().getUserName().equals(username) == false) {
							cameraPreview[j].setVisibility(View.INVISIBLE);
							cameraPreview[j].setDimension(
									(int) (viewWidth * 0.35),
									(int) (viewHeight * 0.45));

							User user = (User) appState.userMap.get(username);

							cameraPreview[j].setUrl("http://"
									+ user.getUserIp()
									+ ":8080/stream/live.jpg");
							cameraPreview[j].setVisibility(View.VISIBLE);
							j++;
						}
					}
				}
			}, 3000);
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
	}

	@Override
	public void onPause() {
		super.onPause();
		inProcessing = true;
		if (webServer != null)
			webServer.stop();
		cameraView_.StopPreview();
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
		SurfaceView cameraSurface = (SurfaceView) findViewById(R.id.surface_camera);
		cameraView_ = new CameraView(cameraSurface);
		cameraView_.setCameraReadyCallback(this);
	}

	public String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					// if (!inetAddress.isLoopbackAddress() &&
					// !inetAddress.isLinkLocalAddress() &&
					// inetAddress.isSiteLocalAddress() ) {
					if (!inetAddress.isLoopbackAddress()
							&& InetAddressUtils.isIPv4Address(inetAddress
									.getHostAddress())) {
						String ipAddr = inetAddress.getHostAddress();
						return ipAddr;
					}
				}
			}
		} catch (SocketException ex) {
			Log.d(TAG, ex.toString());
		}
		return null;
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
			tvIPAddress.setText(getString(R.string.msg_access_local)
					+ " http://" + ipAddr + ":8080");
			return true;
		} else {
			tvIPAddress.setText(getString(R.string.msg_error));
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
}
