package com.dmitrybrant.altimeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.dmitrybrant.altimeter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var binding: ActivityMainBinding

    private var locationTracker: LocationTracker? = null

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                startLocationTracking()
            }else -> {
                Toast.makeText(this, R.string.permissions_invalid, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.progressBar.isVisible = true

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
        try {
            locationTracker?.cleanup()
            locationTracker = LocationTracker(this)
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationChanged(location: Location) {
        binding.progressBar.isVisible = false

        binding.txtLatitude.text = location.latitude.toString()
        binding.txtLongitude.text = location.longitude.toString()
        binding.txtAltitude.text = location.altitude.toInt().toString() //"Altitude: ${location.altitude.toInt()} m (${(location.altitude * 3.28084).toInt()} ft)"

    }

    @SuppressLint("MissingPermission")
    class LocationTracker(
        private val activity: MainActivity,
    ) : Service() {

        private var locationManager: LocationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager

        init {
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            // val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!gpsEnabled) {
                Toast.makeText(activity, R.string.gps_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 1f, activity)
            }
        }

        fun cleanup() {
            try {
                locationManager.removeUpdates(activity)
            } catch (e: Exception) {
                // ignore
            }
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }
    }
}