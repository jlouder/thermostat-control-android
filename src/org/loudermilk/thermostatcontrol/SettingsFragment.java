package org.loudermilk.thermostatcontrol;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    private EditTextPreference urlPreference, usernamePreference,
    	passwordPreference;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_general);
        
        urlPreference = (EditTextPreference) getPreferenceScreen()
        		.findPreference("url");
        usernamePreference = (EditTextPreference) getPreferenceScreen()
        		.findPreference("username");
        passwordPreference = (EditTextPreference) getPreferenceScreen()
        		.findPreference("password");
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Setup the initial values
        urlPreference.setSummary(sharedPreferences.getString("url", ""));
        usernamePreference.setSummary(sharedPreferences.getString("username", ""));
    	// Don't display the real password.
    	String password = sharedPreferences.getString("password", "");
    	String displayPassword = password.equals("") ? "" : "********";
    	passwordPreference.setSummary(displayPassword);
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
	public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Let's do something a preference value changes
        if (key.equals("url")) {
            urlPreference.setSummary(sharedPreferences.getString("url", ""));
        } else if (key.equals("username")) {
        	usernamePreference.setSummary(sharedPreferences.getString("username", ""));
        } else if (key.equals("password")) {
        	// Don't display the real password.
        	String password = sharedPreferences.getString("password", "");
        	String displayPassword = password.equals("") ? "" : "********";
        	passwordPreference.setSummary(displayPassword);
        }
    }
}
