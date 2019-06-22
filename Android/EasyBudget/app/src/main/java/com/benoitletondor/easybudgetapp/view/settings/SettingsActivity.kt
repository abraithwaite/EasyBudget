/*
 *   Copyright 2019 Benoit LETONDOR
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.benoitletondor.easybudgetapp.view.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem

import com.benoitletondor.easybudgetapp.R
import com.benoitletondor.easybudgetapp.view.premium.PremiumActivity
import kotlinx.android.synthetic.main.activity_settings.*

/**
 * Activity that displays settings using the [PreferencesFragment]
 *
 * @author Benoit LETONDOR
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PREMIUM_ACTIVITY) {
            if (resultCode == Activity.RESULT_OK) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(USER_GONE_PREMIUM_INTENT))
            }
        }
    }

    companion object {
        /**
         * Key to specify that the premium popup should be shown to the user
         */
        const val SHOW_PREMIUM_INTENT_KEY = "showPremium"
        /**
         * Intent action broadcast when the user has successfully completed the [PremiumActivity]
         */
        const val USER_GONE_PREMIUM_INTENT = "user.ispremium"
        /**
         * Request code used by premium activity
         */
        const val PREMIUM_ACTIVITY = 20020
    }

}
