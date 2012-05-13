package uk.co.aviva.gibbyj.scapi;
	import android.os.Bundle;
	import android.preference.PreferenceActivity;


public class QuickPrefsActivity extends PreferenceActivity {
		     
		    @Override
		    public void onCreate(Bundle savedInstanceState) {       
		        super.onCreate(savedInstanceState);       
		        addPreferencesFromResource(R.xml.preferences);       
		    }
		     
		}

