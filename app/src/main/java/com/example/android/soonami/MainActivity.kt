/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.soonami

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.TextView

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Displays information about a single earthquake.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Kick off an {@link AsyncTask} to perform the network request
        val task = TsunamiAsyncTask()
        task.execute()
    }

    /**
     * Update the screen to display information from the given [Event].
     */
    private fun updateUi(earthquake: Event) {
        // Display the earthquake title in the UI
        val titleTextView: TextView = findViewById<View>(R.id.title) as TextView
        titleTextView.text = earthquake.title

        // Display the earthquake date in the UI
        val dateTextView: TextView = findViewById<View>(R.id.date) as TextView
        dateTextView.text = getDateString(earthquake.time)

        // Display whether or not there was a tsunami alert in the UI
        val tsunamiTextView: TextView = findViewById<View>(R.id.tsunami_alert) as TextView
        tsunamiTextView.text = getTsunamiAlertString(earthquake.tsunamiAlert)
    }

    /**
     * Returns a formatted date and time string for when the earthquake happened.
     */
    private fun getDateString(timeInMilliseconds: Long): String {
        val formatter = SimpleDateFormat("EEE, d MMM yyyy 'at' HH:mm:ss z", Locale.US)
        return formatter.format(timeInMilliseconds)
    }

    /**
     * Return the display string for whether or not there was a tsunami alert for an earthquake.
     */
    private fun getTsunamiAlertString(tsunamiAlert: Int): String {
        when (tsunamiAlert) {
            0 -> return getString(R.string.alert_no)
            1 -> return getString(R.string.alert_yes)
            else -> return getString(R.string.alert_not_available)
        }
    }

    /**
     * [AsyncTask] to perform the network request on a background thread, and then
     * update the UI with the first earthquake in the response.
     */
    private inner class TsunamiAsyncTask : AsyncTask<URL, Void, Event>() {

        override fun doInBackground(vararg urls: URL): Event? {
            // Create URL object
            val url: URL? = createUrl(USGS_REQUEST_URL)

            // Perform HTTP request to the URL and receive a JSON response back
            var jsonResponse = ""
            try {
                jsonResponse = makeHttpRequest(url!!)
            } catch (e: IOException) {
                // TODO Handle the IOException
            }

            // Extract relevant fields from the JSON response and create an {@link Event} object

            // Return the {@link Event} object as the result fo the {@link TsunamiAsyncTask}
            return extractFeatureFromJson(jsonResponse)
        }

        /**
         * Update the screen with the given earthquake (which was the result of the
         * [TsunamiAsyncTask]).
         */
        override fun onPostExecute(earthquake: Event?) {
            if (earthquake == null) {
                return
            }

            updateUi(earthquake)
        }

        /**
         * Returns new URL object from the given string URL.
         */
        private fun createUrl(stringUrl: String): URL? {
            val url: URL?
            try {
                url = URL(stringUrl)
            } catch (exception: MalformedURLException) {
                Log.e(LOG_TAG, "Error with creating URL", exception)
                return null
            }

            return url
        }

        /**
         * Make an HTTP request to the given URL and return a String as the response.
         */
        @Throws(IOException::class)
        private fun makeHttpRequest(url: URL): String {
            var jsonResponse: String = ""

            // If the URL is null, then return early.
            if(url == null) {
                return jsonResponse
            }

            var urlConnection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.readTimeout = 10000
                urlConnection.connectTimeout = 15000
                urlConnection.connect()

                // If the request was successful (response code 200),
                // then read the input stream and parse the response.
                if (urlConnection.getResponseCode() == 200) {
                    inputStream = urlConnection.getInputStream()
                    jsonResponse = this.readFromStream(inputStream)
                }

                inputStream = urlConnection.inputStream
                jsonResponse = readFromStream(inputStream)
            } catch (e: IOException) {
                // TODO: Handle the exception
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect()
                }
                if (inputStream != null) {
                    // function must handle java.io.IOException here
                    inputStream.close()
                }
            }
            return jsonResponse
        }

        /**
         * Convert the [InputStream] into a String which contains the
         * whole JSON response from the server.
         */
        @Throws(IOException::class)
        private fun readFromStream(inputStream: InputStream?): String {
            val output = StringBuilder()
            if (inputStream != null) {
                val inputStreamReader = InputStreamReader(inputStream, Charset.forName("UTF-8"))
                val reader = BufferedReader(inputStreamReader)
                var line: String? = reader.readLine()
                while (line != null) {
                    output.append(line)
                    line = reader.readLine()
                }
            }
            return output.toString()
        }

        /**
         * Return an [Event] object by parsing out information
         * about the first earthquake from the input earthquakeJSON string.
         */
        private fun extractFeatureFromJson(earthquakeJSON: String): Event? {
            // If the JSON string is empty or null, then return early.
            if (TextUtils.isEmpty(earthquakeJSON)) {
                return null
            }

            try {
                val baseJsonResponse = JSONObject(earthquakeJSON)
                val featureArray: JSONArray = baseJsonResponse.getJSONArray("features")

                // If there are results in the features array
                if (featureArray.length() > 0) {
                    // Extract out the first feature (which is an earthquake)
                    val firstFeature: JSONObject = featureArray.getJSONObject(0)
                    val properties: JSONObject = firstFeature.getJSONObject("properties")

                    // Extract out the title, time, and tsunami values
                    val title: String = properties.getString("title")
                    val time: Long = properties.getLong("time")
                    val tsunamiAlert: Int = properties.getInt("tsunami")

                    // Create a new {@link Event} object
                    return Event(title, time, tsunamiAlert)
                }
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "Problem parsing the earthquake JSON results", e)
            }

            return null
        }
    }

    companion object {

        /** Tag for the log messages  */
        val LOG_TAG = MainActivity::class.java.simpleName

        /** URL to query the USGS dataset for earthquake information  */
        private val USGS_REQUEST_URL: String = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2012-01-01&endtime=2012-12-01&minmagnitude=6"
    }
}
