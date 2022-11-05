/* Copyright 2014 Eddy Xiao <bewantbe@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.woheller69.audio_analyzer_for_android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;

// I was using an old cell phone -- API level 9 (android 2.3.6),
// so here use PreferenceActivity instead of PreferenceFragment.
// http://developer.android.com/guide/topics/ui/settings.html
@SuppressWarnings("deprecation")
public class MyPreferences extends PreferenceActivity {
    private AppCompatDelegate mDelegate;
    private static final String TAG = "MyPreference";
    private static String[] audioSources;
    private static String[] audioSourcesName;

    private static String getAudioSourceNameFromId(int id) {
        for (int i = 0; i < audioSources.length; i++) {
            if (audioSources[i].equals(String.valueOf(id))) {
                return audioSourcesName[i];
            }
        }
        Log.e(TAG, "getAudioSourceName(): no this entry.");
        return "";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ActionBar actionBar = getDelegate().getSupportActionBar();
        if (actionBar != null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    // as soon as the user modifies a preference,
    // the system saves the changes to a default SharedPreferences file
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Log.i(TAG, key + "=" + prefs);
            if (key == null || key.equals("windowFunction")) {
                Preference connectionPref = findPreference(key);
                connectionPref.setSummary(prefs.getString(key, ""));
            }
            if (key == null || key.equals("audioSource")) {
                String asi = prefs.getString("audioSource", getString(R.string.audio_source_id_default));
                int audioSourceId = Integer.parseInt(asi);
                Preference connectionPref = findPreference(key);
                connectionPref.setSummary(getAudioSourceNameFromId(audioSourceId));
            }
            if (key == null || key.equals("spectrogramColorMap")) {
                Preference connectionPref = findPreference(key);
                connectionPref.setSummary(prefs.getString(key, ""));
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        // Get list of default sources
        Intent intent = getIntent();
        final int[] asid = intent.getIntArrayExtra(AnalyzerActivity.MYPREFERENCES_MSG_SOURCE_ID);
        final String[] as = intent.getStringArrayExtra(AnalyzerActivity.MYPREFERENCES_MSG_SOURCE_NAME);

        int nExtraSources = 0;
        for (int id : asid) {
            // See SamplingLoop::run() for the magic number 1000
            if (id >= 1000) nExtraSources++;
        }

        // Get list of supported sources
        AnalyzerUtil au = new AnalyzerUtil(this);
        final int[] audioSourcesId = au.GetAllAudioSource(4);
        Log.i(TAG, " n_as = " + audioSourcesId.length);
        Log.i(TAG, " n_ex = " + nExtraSources);
        audioSourcesName = new String[audioSourcesId.length + nExtraSources];
        for (int i = 0; i < audioSourcesId.length; i++) {
            audioSourcesName[i] = au.getAudioSourceName(audioSourcesId[i]);
        }

        // Combine these two sources
        audioSources = new String[audioSourcesName.length];
        int j = 0;
        for (; j < audioSourcesId.length; j++) {
            audioSources[j] = String.valueOf(audioSourcesId[j]);
        }
        for (int i = 0; i < asid.length; i++) {
            // See SamplingLoop::run() for the magic number 1000
            if (asid[i] >= 1000) {
                audioSources[j] = String.valueOf(asid[i]);
                audioSourcesName[j] = as[i];
                j++;
            }
        }

        final ListPreference lp = (ListPreference) findPreference("audioSource");
        lp.setDefaultValue(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        lp.setEntries(audioSourcesName);
        lp.setEntryValues(audioSources);

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    private AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, null);
        }
        return mDelegate;
    }
}
