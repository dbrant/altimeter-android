package com.dmitrybrant.altimeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.dmitrybrant.altimeter.databinding.ActivityMainBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity(), MenuProvider, LocationListener, SensorEventListener {
    private lateinit var binding: ActivityMainBinding

    private var locationTracker: LocationTracker? = null
    private var lastLocation: Location? = null

    private var geoMagField: GeomagneticField? = null
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var latLongUnits = false
    private var altUnits = false

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
        restoreSettings()

        setSupportActionBar(binding.toolbar)
        addMenuProvider(this, this, Lifecycle.State.RESUMED)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        geoMagField = GeomagneticField(0f, 0f, 0f, System.currentTimeMillis())

        binding.refreshLayout.setOnRefreshListener {
            setUpLocationTracking()
        }

        setUpLocationTracking()
    }

    override fun onResume() {
        super.onResume()

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationTracker?.cleanup()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_latlongunits -> {
                latLongUnits = !latLongUnits
                saveSettings()
                updateLocationDisplay()
                true
            }
            R.id.menu_altunits -> {
                altUnits = !altUnits
                saveSettings()
                updateLocationDisplay()
                true
            }
            R.id.menu_about -> {
                //
                true
            }
            else -> false
        }
    }

    private fun setUpLocationTracking() {
        if (haveLocationPermissions()) {
            startLocationTracking()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        binding.txtLatitude.text = getString(R.string.val_unknown)
        binding.txtLongitude.text = getString(R.string.val_unknown)
        binding.txtAltitude.text = getString(R.string.val_unknown)

        binding.progressBar.isVisible = true
        binding.refreshLayout.isRefreshing = false
    }

    private fun saveSettings() {
        val editor = getPrefs(this).edit()
        editor.putBoolean(KEY_LATLONGUNITS, latLongUnits)
        editor.putBoolean(KEY_ALTUNITS, altUnits)
        editor.apply()
    }

    private fun restoreSettings() {
        try {
            val prefs = getPrefs(this)
            latLongUnits = prefs.getBoolean(KEY_LATLONGUNITS, false)
            altUnits = prefs.getBoolean(KEY_ALTUNITS, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        lastLocation = location
        binding.progressBar.isVisible = false
        updateLocationDisplay()
    }

    private fun updateLocationDisplay() {
        lastLocation?.let {
            binding.txtLatitude.text = if (latLongUnits) decToHms(it.latitude) else it.latitude.toString()
            binding.txtLongitude.text = if (latLongUnits) decToHms(it.longitude) else it.longitude.toString()
            binding.txtAltitude.text = if (altUnits) ((it.altitude * 3.28084).toInt().toString() + " m") else (it.altitude.toInt().toString() + " ft")

            geoMagField = GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(),
                it.altitude.toFloat(), System.currentTimeMillis())
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        updateCompass()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }

    private fun updateCompass() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        var degrees = -Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

        if (geoMagField != null) {
            degrees -= geoMagField!!.declination
        }

        binding.imgCompass.rotation = degrees
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

    companion object {
        private const val PREFS_NAME = "AltimeterPrefs"
        private const val KEY_LATLONGUNITS = "latLongUnits"
        private const val KEY_ALTUNITS = "altUnits"

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, 0)
        }

        private fun decToHms(dec: Double): String {
            val d = abs(dec)
            val deg = d.toInt()
            val min = ((d - deg) * 60).toInt()
            val sec = ((d - deg - min / 60.0) * 3600).toInt()
            return String.format("%d° %d' %d\"", dec.toInt(), min, sec)
        }
    }
}