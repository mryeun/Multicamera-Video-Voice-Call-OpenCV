package teaonly.droideye;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONArray;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import edu.purdue.cs252.lab6.DirectoryCommand;
import edu.purdue.cs252.lab6.User;
import edu.purdue.cs252.lab6.UserList;

/*
 * Class that contains the directory information after the user has logged in
 * The layout displayed is directory.xmll
 */
public class ActivityDirectory extends ListActivity {
	public static final int RESULT_INTERRUPTED = 1;
	public static final int RESULT_FAILED = 2;
	private DirectoryClient dc;
	ArrayList<String> CalleeList;
	private Handler handler;
	private String TAG = "ActivityDirectory";

	// ConcurrentHashMap<String, User> userMap;
	VoipApp appState;
	User user;
	ArrayAdapter<String> adapter;
	Button btnCall;
	JSONArray m_jsonArray;
	ConcurrentHashMap<String, User> userMapForThis;

	/*
	 * Function for creating the messages handler that control the server/client
	 * messages.
	 */
	public void createHandler() {
		handler = new Handler() {
			public void handleMessage(Message msg) {
				Log.i("AH", "adHandler");
				if (msg.what == DirectoryCommand.S_DIRECTORY_SEND.getCode()) {
					appState.userMap.clear();
					userMapForThis.clear();
					// userMap.putAll((Map<String,User>)msg.obj);
					// ArrayList<User> users = (ArrayList<User>)msg.obj;
					// Gett he userlist
					UserList uList = (UserList) msg.obj;
					for (int i = 0; i < uList.size(); i++) {
						User u = uList.get(i);
						appState.userMap.put(u.getUserName(), u);
						userMapForThis.put(u.getUserName(), u);
					}

					// Print the user list
					adapter.clear();
					for (String username2 : userMapForThis.keySet()) {
						if (!user.getUserName().equals(username2))
							adapter.add(username2);
						Log.i("AD", "directory: " + username2);
					}
				} else if (msg.what == DirectoryCommand.S_BC_USERLOGGEDIN
						.getCode()) {
					// Receive the message that the user has logged in
					User user2 = (User) msg.obj;
					String username2 = user2.getUserName();
					appState.userMap.put(username2, user2);
					userMapForThis.put(username2, user2);
					adapter.add(username2);
				} else if (msg.what == DirectoryCommand.S_BC_USERLOGGEDOUT
						.getCode()) {// 로그아웃했을때 유저 목록에서 제거하는거
					// /Updates the userlist when the user has logged out
					String username2 = (String) msg.obj;
					appState.userMap.remove(username2);
					userMapForThis.remove(username2);
					adapter.remove(username2);
				} else if (msg.what == DirectoryCommand.S_CALL_INCOMING
						.getCode()) {
					// Lets the user know that they are being called
					String usernames = (String) msg.obj;
					Call.setUsername2(usernames);
					Call.setState(Call.State.INCOMING);
					Intent callIncomingIntent = new Intent(
							ActivityDirectory.this, ActivityCallIncoming.class);
					callIncomingIntent.putExtra("usernames", usernames);
					startActivity(callIncomingIntent);
				} else {
					Log.e("AD", "unrecognized message " + msg.what);
					// unrecognized message
					// TODO: handle error
				}
			}
		};
	}

	// Main Method for the directory server listing activity
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.directory);

		btnCall = (Button) findViewById(R.id.btnCall);

		appState = (VoipApp) getApplicationContext();
		user = appState.getUser();
		CalleeList = new ArrayList<String>();
		m_jsonArray = new JSONArray();

		Log.d("Login", user.getUserName());

		appState.userMap = new ConcurrentHashMap<String, User>();
		userMapForThis = new ConcurrentHashMap<String, User>();

		final ArrayList<String> usernameList = new ArrayList<String>();

		final Spinner s = (Spinner) findViewById(R.id.sort_by);
		final ArrayAdapter<CharSequence> a = ArrayAdapter.createFromResource(
				this, R.array.sort_by, android.R.layout.simple_spinner_item);

		s.setAdapter(a);

		// Create an ArrayAdapter to user for our ListActivity
		Comparator<String> comparator = Collections.reverseOrder();
		Collections.sort(usernameList, comparator);
		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_multiple_choice, usernameList);
		final ListActivity thisActivity = this;
		thisActivity.setListAdapter(adapter);

		// SORTS The list from a-Z
		// or sorts the list from Z-A
		s.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				String str = parent.getItemAtPosition(pos).toString();
				if (!str.equals("A-Z")) {
					Comparator<String> comparator = Collections.reverseOrder();
					Collections.sort(usernameList, comparator);
					adapter.notifyDataSetChanged();
				}
				if (!str.equals("Z-A")) {
					Collections.sort(usernameList);
					adapter.notifyDataSetChanged();
				}
			}

			public void onNothingSelected(AdapterView<?> view) {
				// Do Nothing
			}
		});
		createHandler();

		// get the directory client
		dc = appState.getDirectoryClient();
		dc.setReadHandler(handler);
		dc.getDirectory();
		btnCall.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// TODO Auto-generated method stub
				// Log.d("Connect", "to the next user");
				//
				// Call.setUsername2("멤버십");
				// Call.setState(Call.State.OUTGOING);
				// Intent callOutgoingIntent = new Intent(v.getContext(),
				// ActivityCallOutgoing.class);
				// startActivity(callOutgoingIntent);
				Log.d("Connect", "to the next user");

				ListView l = getListView();

				CalleeList.clear();
				m_jsonArray.clear();

				for (int i = 0; i < l.getCount(); i++) {
					if (l.isItemChecked(i) == true) {
						Object o = l.getItemAtPosition(i);
						final String username2 = o.toString();
						CalleeList.add(username2);
						m_jsonArray.add(username2);
					}
				}

				// 선택한 아이디들이 나열되고 마지막엔 전화건 사람의 아이디가 저장 됨
				m_jsonArray.add(appState.getUser().getUserName());

				if (m_jsonArray.size() == 2) {
					AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
					builder.setMessage(
							"Are you sure you want to panaroma with other phone?")
							.setCancelable(false)
							.setPositiveButton("Yes",
									new DialogInterface.OnClickListener() {
										// Clicking Yes on the dialog box
										public void onClick(
												DialogInterface dialog, int id) {
											Call.setUsername2(m_jsonArray.toJSONString());
											Intent intent = new Intent(getApplicationContext(), NFC.class);
											startActivity(intent);
										}
									})
							.setNegativeButton("No",
									new DialogInterface.OnClickListener() {
										// Clicking No on the dialog box
										public void onClick(
												DialogInterface dialog, int id) {
											// cancel dialog action
											Call.setPanorama(false);
											dialog.cancel();
										}
									});
					// Create and show dialog box
					AlertDialog alert = builder.create();
					alert.show();
				} else if (m_jsonArray.size() >= 3 && m_jsonArray.size() <= 4) {

					Call.setUsername2(m_jsonArray.toJSONString());
					Call.setState(Call.State.OUTGOING);
					Intent callOutgoingIntent = new Intent(v.getContext(),
							ActivityCallOutgoing.class);
					startActivity(callOutgoingIntent);
				}
			}
		});

		final Button buttonCall = (Button) findViewById(R.id.ButtonLogout);
		buttonCall.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// TODO: Logout
				try {
					dc.logout();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Intent intent = new Intent();
				setResult(RESULT_OK, intent);
				finish();
			}
		});

	}

	/*
	 * Summary: Function called when a user is selected on the list Parameters:
	 * ListView, l, view v, int position, long id Return: void
	 */
	@Override
	protected void onListItemClick(ListView l, View view, int position, long id) {
		super.onListItemClick(l, view, position, id);

	}

	// Logouts out the user when the users clicks the logout button
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			dc.logout();
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onResume() {
		super.onResume();
		dc.setReadHandler(handler);

		/*
		 * // Capture ACTION_INCOMINGCALL broadcast IntentFilter intentFilter =
		 * new IntentFilter(RingerServer.ACTION_INCOMINGCALL);
		 * registerReceiver(new IncomingCallReceiver(),intentFilter);
		 */
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		// TODO: display notification if call fails
	}

}
