package teaonly.droideye;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import edu.purdue.cs252.lab6.DirectoryCommand;

public class ActivityCallOngoing extends Activity {
	private static final String TAG = "ACOngoing";
	private Activity thisActivity;
	private VoipApp appState;

	/** Called when the activity is first created. */
	@Override
	// This function is called when the button is pushed
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.call_ongoing);
		Call.setState(Call.State.ONGOING); // sets call state

		appState = (VoipApp) getApplicationContext(); // creates application
														// context
		thisActivity = ActivityCallOngoing.this;

		// get the directory client
		final DirectoryClient dc = appState.getDirectoryClient(); // create
																	// directory

		final String server = dc.getServer();
		// dc.call_ready(); //the call is now ready

		Log.i(TAG, "S_REDIRECT_READY");
		VoiceCaptureClient voiceCaptureClient = new VoiceCaptureClient(server,
				Call.getPort());
		appState.setVoiceCaptureClient(voiceCaptureClient);
		voiceCaptureClient.start(); // starts voice capture client

		Handler callOngoingHandler = new Handler() { 	// preforms tasks based on
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
	}

	// Returns the screen back to he directory
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
}
