package com.dmitrybrant.altimeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity(), LocationListener {

    private var locationTracker: LocationTracker? = null

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                startLocationTracking()
            }else -> {
                Toast.makeText(this, "Permissions.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (haveLocationPermissions()) {
            startLocationTracking()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationTracker?.cleanup()
    }

    private fun haveLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationTracking() {
        locationTracker = LocationTracker(this)
    }

    override fun onLocationChanged(location: Location) {
        //Log.d("MainActivity", "onLocationChanged: $location")

        findViewById<TextView>(R.id.txtLatitude).text = "Latitude: ${location.latitude}"
        findViewById<TextView>(R.id.txtLongitude).text = "Longitude: ${location.longitude}"
        findViewById<TextView>(R.id.txtAltitude).text = "Altitude: ${location.altitude.toInt()} m (${(location.altitude * 3.28084).toInt()} ft)"

    }

    @SuppressLint("MissingPermission")
    class LocationTracker(
        private val activity: MainActivity,
    ) : Service() {

        private val MIN_DISTANCE_CHANGE_FOR_UPDATES = 10f
        private val MIN_TIME_BW_UPDATES = 1000L * 60 * 1
        private var locationManager: LocationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager

        init {

            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!gpsEnabled && !networkEnabled) {
                Toast.makeText(activity, "Location provider not available.", Toast.LENGTH_SHORT).show()
            } else {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES, activity)

                //loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                //if (loc != null) {
                //    latitude = loc.getLatitude();
                //    longitude = loc.getLongitude();
                //}
            }

        }

        fun cleanup() {
            locationManager.removeUpdates(activity)
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }
    }
}