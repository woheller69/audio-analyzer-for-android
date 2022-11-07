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

import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import androidx.preference.ListPreference;
import android.util.Log;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import androidx.preference.PreferenceFragmentCompat;

public class MyPreferences extends AppCompatActivity {

    private static final String TAG = "MyPreference";
    private static String[] audioSources;
    private static String[] audioSourcesName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        Context context;
        Intent intent;
        public SettingsFragment(Context context, Intent intent){
            this.context = context;
            this.intent = intent;
        }
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            // Get list of default sources
            final int[] asid = intent.getIntArrayExtra(AnalyzerActivity.MYPREFERENCES_MSG_SOURCE_ID);
            final String[] as = intent.getStringArrayExtra(AnalyzerActivity.MYPREFERENCES_MSG_SOURCE_NAME);

            int nExtraSources = 0;
            for (int id : asid) {
                // See SamplingLoop::run() for the magic number 1000
                if (id >= 1000) nExtraSources++;
            }

            // Get list of supported sources
            AnalyzerUtil au = new AnalyzerUtil(context);
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

            final ListPreference lp =  findPreference("audioSource");
            if (lp != null) {
                lp.setDefaultValue(MediaRecorder.AudioSource.VOICE_RECOGNITION);
                lp.setEntries(audioSourcesName);
                lp.setEntryValues(audioSources);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment(this, getIntent()))
                .commit();
    }
}
