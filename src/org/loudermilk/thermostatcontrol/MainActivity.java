package org.loudermilk.thermostatcontrol;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.RadioGroup.LayoutParams;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements NumberPicker.OnValueChangeListener {

	// View elements
	Button targetTempButton;
	TextView currentTempTextView;
	Switch holdSwitch;
	View progressSpinner;
	
	// State for the view elements
	float targetTemp = 0, currentTemp = 0;
	boolean isHoldOn = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
		
		currentTempTextView = (TextView) findViewById(R.id.current_temp);
		targetTempButton = (Button) findViewById(R.id.target_temp);
		targetTempButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showTempPicker();
			}
		});
		
		holdSwitch = (Switch) findViewById(R.id.hold_switch);
		holdSwitch.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String jsonString = "{\"hold\": " + (isHoldOn ? 0 : 1) + "}";
				setThermostat(jsonString);
				refreshThermostat();
			}
		});
		progressSpinner = findViewById(R.id.progress);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch(item.getItemId()) {
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case R.id.action_refresh:
				refreshThermostat();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void refreshThermostat() {
		Log.d("MainActivity", "refreshThermostat()");
		
		// Make sure we have network connectivity
		ConnectivityManager connMgr = (ConnectivityManager)
				getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo == null || !networkInfo.isConnected()) {
			Toast.makeText(getApplicationContext(),
					"No network connection available.",
					Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Make sure the connection details for the thermostat are defined
		URLConnection conn = getThermostatConnection();
		if (conn == null) {
			Toast.makeText(getApplicationContext(),
					"Set thermostat URL in settings.",
					Toast.LENGTH_SHORT).show();
			return;			
		}
		
		// Turn on the progress spinner
		progressSpinner.setVisibility(View.VISIBLE);
		new RefreshThermostatTask().execute(conn);
	}
	
	private void setThermostat(String jsonString) {
		Log.d("MainActivity", "setThermostat(" + jsonString + ")");

		// Make sure we have network connectivity
		ConnectivityManager connMgr = (ConnectivityManager)
				getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo == null || !networkInfo.isConnected()) {
			Toast.makeText(getApplicationContext(),
					"No network connection available.",
					Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Make sure the connection details for the thermostat are defined
		URLConnection conn = getThermostatConnection();
		if (conn == null) {
			Toast.makeText(getApplicationContext(),
					"Set thermostat URL in settings.",
					Toast.LENGTH_SHORT).show();
			return;			
		}
		
		// Turn on the progress spinner
		progressSpinner.setVisibility(View.VISIBLE);
		new SetThermostatTask().execute(conn, jsonString);
		
	}
	
	public void onHoldSwitchClicked(View view) {
		// Do something eventually!
	}
	
	@Override
	public void onValueChange(NumberPicker picker, int oldValue, int newValue) {
		// User has changed the target temperature.
	}
	
	public void showTempPicker() {
		final Dialog pickerDialog = new Dialog(MainActivity.this);
		pickerDialog.setTitle(R.string.set_target_temp_label);
		pickerDialog.setContentView(R.layout.temp_picker);

		final NumberPicker tempPicker = (NumberPicker)
				pickerDialog.findViewById(R.id.temp_picker);
		tempPicker.setMaxValue(85);
		tempPicker.setMinValue(65);
		if (targetTemp > 0) {
			tempPicker.setValue((int)targetTemp);
		}
		tempPicker.setWrapSelectorWheel(false);
		tempPicker.setOnValueChangedListener(this);

		Button cancelButton = (Button)
				pickerDialog.findViewById(R.id.temp_picker_cancel_button);
		cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pickerDialog.dismiss();
			}
		});

		Button setButton = (Button)
				pickerDialog.findViewById(R.id.temp_picker_set_button);
		setButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pickerDialog.dismiss();
				setThermostat("{\"t_cool\": " + tempPicker.getValue() + "}");
				refreshThermostat();
			}
		});

		pickerDialog.show();
		pickerDialog.getWindow().setLayout(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
	}
	
	protected URLConnection getThermostatConnection() {
		Log.d("MainActivity", "getThermostatConnection()");
		SharedPreferences sharedPreferences =
				PreferenceManager.getDefaultSharedPreferences(this);
		String url = sharedPreferences.getString("url", "");
		if (url.equals("")) {
			return null;
		}

		try {
			boolean accept_any_cert = sharedPreferences.getBoolean("accept_any_cert", false);
			if (accept_any_cert) {
				Log.d("MainActivity", "trusting any certificate");
				TrustManager[] trustAllCerts = new TrustManager[] {
						new X509TrustManager() {
							public java.security.cert.X509Certificate[] getAcceptedIssuers() {
								return null;
							}

							public void checkClientTrusted(X509Certificate[] certs, String authType) {  }

							public void checkServerTrusted(X509Certificate[] certs, String authType) {  }

						}
				};

				SSLContext sc = SSLContext.getInstance("SSL");
				sc.init(null, trustAllCerts, new java.security.SecureRandom());
				HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

				// Create all-trusting host name verifier
				HostnameVerifier allHostsValid = new HostnameVerifier() {
					public boolean verify(String hostname, SSLSession session) {
						return true;
					}
				};
				// Install the all-trusting host verifier
				HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
			}
			
			URLConnection conn = new URL(url).openConnection();
			conn.setReadTimeout(10000 /* ms */);
			conn.setConnectTimeout(10000 /* ms */);
			conn.setDoInput(true);

			String username = sharedPreferences.getString("username", "");
			String password = sharedPreferences.getString("password", "");
			if (username.length() > 0 && password.length() > 0) {
				String usernameAndPassword = username + ":" + password;
				String authHeader = "Basic " +
						Base64.encodeToString(usernameAndPassword.getBytes(),
								Base64.NO_WRAP);
				conn.setRequestProperty("Authorization", authHeader);
				Log.d("MainActivity", "Authorization: " + authHeader);
			}
			
			return conn;
		} catch (Exception e) {
			Log.d("MainActivity", "Exception in getThermostatConnection()", e);
			return null;
		}
	}

	private class RefreshThermostatTask extends AsyncTask<URLConnection, Void, Void> {
		@Override
		protected Void doInBackground(URLConnection... conns) {
			Log.d("MainActivity", "doInBackground()");
			URLConnection conn = conns[0];

			try {
				((HttpURLConnection) conn).setRequestMethod("GET");
				Log.d("MainActivity", "GET " + conn.getURL().toString());
				conn.connect();
				int responseCode = ((HttpURLConnection) conn).getResponseCode();
				Log.d("MainActivity", "HTTP response code: " + responseCode);
				if (responseCode != 200) {
					Log.e("MainActivity", "thermostat responded with HTTP " + responseCode);
					toast("Error response from thermostat.");
					return null;
				}
				InputStream is = conn.getInputStream();
				BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, "UTF-8")); 
			    StringBuilder responseStrBuilder = new StringBuilder();

			    String inputStr;
			    while ((inputStr = streamReader.readLine()) != null)
			        responseStrBuilder.append(inputStr);
			    Log.d("MainActivity", "HTTP response body: " + responseStrBuilder.toString());
			    JSONObject j = new JSONObject(responseStrBuilder.toString());
			    currentTemp = Float.valueOf(j.getString("temp"));
			    Log.d("MainActivity", "currentTemp=" + currentTemp);
			    targetTemp = Float.valueOf(j.getString("t_cool"));
			    Log.d("MainActivity", "targetTemp=" + targetTemp);
			    isHoldOn = j.getInt("hold") == 1;
			    Log.d("MainActivity", "isHoldOn=" + isHoldOn);
			} catch (Exception e) {
				Log.e("MainActivity", "Exception while refreshing Thermostat state", e);
				toast("Error getting current thermostat state.");
				return null;
			}
			
			return null;		
		}
		
		protected void toast(final String text) {
			runOnUiThread(new Runnable() {
				public void run() {
				    Toast.makeText(MainActivity.this,
				    		text,
				    		Toast.LENGTH_SHORT).show();
				    }
				});
		}
		
		@Override
		protected void onPostExecute(Void v) {
			Log.d("MainActivity", "onPostExecute()");

			// Turn off the progress spinner.
			progressSpinner.setVisibility(View.INVISIBLE);
			
			// Set the text/state of the view elements.
			if (currentTemp > 0) {
				currentTempTextView.setText(String.format("%.1f", currentTemp));
			}
			if (targetTemp > 0) {
				targetTempButton.setText(String.format("%d", (int)targetTemp));
			}
			holdSwitch.setChecked(isHoldOn);
		}
	}

	private class SetThermostatTask extends AsyncTask<Object, Void, Void> {
		@Override
		protected Void doInBackground(Object... params) {
			Log.d("MainActivity", "doInBackground()");
			URLConnection conn = (URLConnection) params[0];
			String postData = (String) params[1];

			try {
				conn.setDoOutput(true);
				((HttpURLConnection) conn).setRequestMethod("POST");
				int contentLength = postData.length();
				Log.d("MainActivity", "Content-Length: " + contentLength);
				//conn.setRequestProperty("Content-Length", String.valueOf(contentLength));
				((HttpURLConnection) conn).setFixedLengthStreamingMode(contentLength);
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				
				PrintWriter output = new PrintWriter(conn.getOutputStream());
				output.print(postData);
				output.close();
				
				Log.d("MainActivity", "POST " + conn.getURL().toString());
				Log.d("MainActivity", "Post data: " + postData);
				int responseCode = ((HttpURLConnection) conn).getResponseCode();
				Log.d("MainActivity", "HTTP response code: " + responseCode);
				if (responseCode != 200) {
					Log.e("MainActivity", "thermostat responded with HTTP " + responseCode);
					toast("Error response from thermostat.");
					return null;
				}
				InputStream is = conn.getInputStream();
				BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, "UTF-8")); 
			    StringBuilder responseStrBuilder = new StringBuilder();

			    String inputStr;
			    while ((inputStr = streamReader.readLine()) != null)
			        responseStrBuilder.append(inputStr);
			    Log.d("MainActivity", "HTTP response body: " + responseStrBuilder.toString());
			    JSONObject j = new JSONObject(responseStrBuilder.toString());
			    if (!j.has("success")) {
			    	Log.e("MainActivity", "set thermostat returned no 'success' key");
			    	toast("Error response from thermostat.");
			    	return null;
			    }
			} catch (Exception e) {
				Log.e("MainActivity", "Exception while refreshing Thermostat state", e);
				toast("Error getting current thermostat state.");
				return null;
			}
			
			return null;		
		}
		
		protected void toast(final String text) {
			runOnUiThread(new Runnable() {
				public void run() {
				    Toast.makeText(MainActivity.this,
				    		text,
				    		Toast.LENGTH_SHORT).show();
				    }
				});
		}
		
		@Override
		protected void onPostExecute(Void v) {
			Log.d("MainActivity", "onPostExecute()");

			// Turn off the progress spinner.
			progressSpinner.setVisibility(View.INVISIBLE);
		}
	}


}
