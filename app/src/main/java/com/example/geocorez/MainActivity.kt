package com.example.geocorez

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.location.LocationManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
//Librerias para Mapbox
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.location

class MainActivity : AppCompatActivity() {

    //variables para reconocer elementos en layout
    private lateinit var mapView: MapView
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var locationManager: LocationManager

    private val locationPermissionRequestCode = 1
    private val locationUpdateInterval = 1000L

    // Para el control de la vista
    private var isFirstLocationUpdate = true
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        initializeLocationListener() // inicializa el listener de ubicación
        checkLocationPermissions() // verifica si se tienen los permisos necesarios
    }

    private fun initializeLocationListener() {
        locationListener = LocationListener { location ->
            updateCoordinates(location)
            if (isFirstLocationUpdate) {
                updateCamera(location)
                isFirstLocationUpdate = false
            }
        }
    }

    @SuppressLint("MissingPermission") // suprime advertencias sobre permisos (ya se verifican en otra parte)
    private fun startLocationUpdates() { // inicia las actualizaciones de ubicación
        try {
            locationManager.requestLocationUpdates( // solicita actualizaciones desde GPS
                LocationManager.GPS_PROVIDER, // proveedor GPS
                locationUpdateInterval, // intervalo de tiempo
                0f, // distancia mínima entre actualizaciones
                locationListener!! // el listener de ubicación
            )
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { // si el proveedor de red está disponible
                locationManager.requestLocationUpdates( // solicita también actualizaciones desde red
                    LocationManager.NETWORK_PROVIDER,
                    locationUpdateInterval,
                    0f,
                    locationListener!!
                )
            }
        } catch (e: SecurityException) { // captura errores de permisos
            e.printStackTrace() // imprime el error en la consola
        }
    }

    private fun updateCoordinates(location: Location) { // muestra latitud y longitud en pantalla
        runOnUiThread {
            val latitudeText = "Latitud: %.6f".format(location.latitude) // formatea latitud
            val longitudeText = "Longitud: %.6f".format(location.longitude) // formatea longitud

            latitudeTextView.text = latitudeText // muestra latitud
            longitudeTextView.text = longitudeText // muestra longitud
        }
    }

    private fun updateCamera(location: Location) { // actualiza la cámara del mapa
        val cameraOptions = CameraOptions.Builder() // constructor de opciones de cámara
            .center(
                Point.fromLngLat(
                    location.longitude,
                    location.latitude
                )
            ) // centro de la cámara en la ubicación actual
            .zoom(14.0) // nivel de zoom
            .build()

        mapView.getMapboxMap().setCamera(cameraOptions) // aplica las opciones de cámara al mapa
    }

    @SuppressLint("MissingPermission")
    private fun initializeMap() { // inicializa el mapa y sus componentes
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style -> // carga el estilo del mapa (calles)

            val locationComponentPlugin = mapView.location // obtiene el componente de ubicación de Mapbox

            val desiredSizePx = (48 * resources.displayMetrics.density).toInt() // convierte dp a píxeles
            val originalBitmap =
                BitmapFactory.decodeResource(resources, R.drawable.pointermap) // carga el icono personalizado
            val resizedBitmap = Bitmap.createScaledBitmap( // redimensiona el icono
                originalBitmap,
                desiredSizePx,
                desiredSizePx,
                true
            )

            val pointerImageHolder = ImageHolder.from(resizedBitmap) // convierte el bitmap en ImageHolder para Mapbox

            val locationPuck = LocationPuck2D( // crea el icono de ubicación personalizada
                topImage = pointerImageHolder,
                bearingImage = pointerImageHolder
            )

            locationComponentPlugin.locationPuck = locationPuck // asigna el icono de ubicación
            locationComponentPlugin.enabled = true // habilita el componente de ubicación

            // Obtener la última ubicación conocida
            var lastLocation: Location? = null
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { // si GPS está activo
                lastLocation =
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) // obtiene la última ubicación por GPS
            }
            if (lastLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { // si no hay GPS y red está activa
                lastLocation =
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) // obtiene ubicación por red
            }
            lastLocation?.let { // si se obtuvo una última ubicación
                if (isFirstLocationUpdate) { // si es la primera vez
                    updateCamera(location = it) // mueve la cámara
                    isFirstLocationUpdate = false
                }
                updateCoordinates(location = it) // muestra latitud y longitud
            }

            startLocationUpdates() // comienza a recibir actualizaciones de ubicación
        }
    }
    private fun checkLocationPermissions() { // verifica si los permisos de ubicación han sido concedidos
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions( // solicita permisos si no se tienen
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationPermissionRequestCode
            )
        } else {
            checkGpsStatus() // si ya tiene permisos, verificar el estado del GPS
        }
    }

    private fun checkGpsStatus() { // verifica si el GPS está activado
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { // si el GPS está desactivado
            showGpsDisabledDialog() // muestra un diálogo para activarlo
        } else {
            initializeMap() // si el GPS está activado, inicializa el mapa
        }
    }
    private fun showGpsDisabledDialog() { // muestra un diálogo cuando el GPS está apagado
        AlertDialog.Builder(this)
            .setTitle("GPS Desactivado")
            .setMessage("Para usar esta aplicación necesitas activar el GPS. ¿Deseas activarlo ahora?")
            .setPositiveButton("Activar GPS") { _, _ ->
                openLocationSettings() // abre configuración de ubicación
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss() // cierra el diálogo
                Toast.makeText(this, "La aplicación necesita GPS para funcionar correctamente", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false) // evita que se cierre el diálogo accidentalmente
            .show()
    }
    private fun openLocationSettings() { // abre la configuración de ubicación del sistema
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) // crea un intent para abrir configuraciones
        startActivity(intent) // lanza la actividad
    }
    override fun onRequestPermissionsResult( // maneja el resultado de la solicitud de permisos
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkGpsStatus() // Verificar GPS después de obtener permisos
            } else {
                // Manejar caso cuando permisos son denegados
                latitudeTextView.text = "Permisos de ubicación denegados"
                longitudeTextView.text = ""
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // Verificar si el GPS fue activado manualmente por el usuario
        if (hasLocationPermission() && ::locationManager.isInitialized && locationListener != null) {
            checkGpsStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        locationListener = null
    }
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    @SuppressLint("MissingPermission")
    private fun stopLocationUpdates() {
        if (::locationManager.isInitialized && locationListener != null) {
            locationManager.removeUpdates(locationListener!!)
        }
    }
}