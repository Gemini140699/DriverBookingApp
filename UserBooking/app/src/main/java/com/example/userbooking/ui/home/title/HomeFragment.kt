package com.example.userbooking.ui.home.title

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.ArraySet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.userbooking.R
import com.example.userbooking.model.DriverGeoModel
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.util.*


class HomeFragment : Fragment(), OnMapReadyCallback {


    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    lateinit var mapFragment: SupportMapFragment

    //Location
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //assign with firebase realtime
    private lateinit var onlineRef: DatabaseReference
    private lateinit var usersLocationRef: DatabaseReference
    private var currentUserRef: DatabaseReference? = null
    private lateinit var geoFire: GeoFire

    // listen firebase realtime, remove location if current user disconnect
    private val onlineValueEventListener = object : ValueEventListener {
        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) {
                currentUserRef?.onDisconnect()?.removeValue()
            }
        }
    }

    //Load Driver Available
    private var distance = 1.0 //km
    private var LIMIT_RANGE = 10 //km
    private var previousLocation: Location? = null // calculate distance
    private var currentLocation: Location? = null

    //Listener
    iFire

    //first time?
    private var firstTime = true

    private lateinit var availDrivers: ArraySet<DriverGeoModel>


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
                ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        init()

        mapFragment = childFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        return root
    }

    override fun onResume() {
        super.onResume()
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    private fun init() {
        //initial onlineRef
        onlineRef = FirebaseDatabase.getInstance().reference.child(".info/connected")

        //set locationRequest
        locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.fastestInterval = 3000
        locationRequest.smallestDisplacement = 10f
        locationRequest.interval = 5000

        //on locationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                val newPos = LatLng(
                        locationResult!!.lastLocation.latitude,
                        locationResult!!.lastLocation.longitude
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                //check and load driver again if user changes location
                if (firstTime) {
                    //if is the first time, previous and current location is the same
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation
                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }

                if ((previousLocation.distanceTo(currentLocation)) / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrives()
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()
        )
    }



    private fun loadAvailableDrives() {
        if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.lastLocation.addOnFailureListener { e ->
            Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()

        }.addOnSuccessListener { location ->
            //Load all drivers in city
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses: List<Address>?
            try {
                addresses = geoCoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                )

                //cityName of user
                val cityName = addresses[0].locality

                //seach for drivers in the same city
                val driversLocationRef = FirebaseDatabase.getInstance().getReference("driversLocation").child(cityName)
                geoFire = GeoFire(driversLocationRef)

                //Query drivers around user location with radius = distance = 1.0km
                val geoQuery = geoFire.queryAtLocation(GeoLocation(location.latitude, location.latitude), distance)
                geoQuery.removeAllListeners()
                geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                    override fun onGeoQueryReady() {
                        TODO("Not yet implemented")
                        if(distance <= LIMIT_RANGE) {
                            distance++
                            loadAvailableDrives()
                        }  else {
                            distance = 0.0
                            addDriverMarker()
                        }
                    }

                    override fun onKeyEntered(key: String?, location: GeoLocation?) {
                        TODO("Not yet implemented")
                        availDrivers.add(DriverGeoModel(key!!, location!!))
                    }

                    override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        TODO("Not yet implemented")
                    }

                    override fun onKeyExited(key: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun onGeoQueryError(error: DatabaseError?) {
                        TODO("Not yet implemented")
                        Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_SHORT).show()
                    }

                })

            } catch (e: IOException) {
                Log.e("Error", e.message.toString())
            }
        }

    }

    private fun addDriverMarker() {
        if(availDrivers.size > 0) {
            Observable.fromIterable(availDrivers)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ driverGeoModel ->
                        //On next
                        findDriversByKey(driverGeoModel)
                    }, { throwable -> Snackbar.make(view, throwable.getMessage(), Snackbar.LENGTH_SHORT).show() }, {})
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //Request permission to add Location
        Dexter.withContext(requireContext())
                .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object : PermissionListener {
                    @SuppressLint("MissingPermission")
                    override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                        mMap.isMyLocationEnabled = true
                        mMap.uiSettings.isMyLocationButtonEnabled = true
                        mMap.setOnMyLocationButtonClickListener {

                            fusedLocationProviderClient.lastLocation.addOnFailureListener { e ->
                                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()

                            }.addOnSuccessListener { location ->
                                val userLatlng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatlng, 18f))
                            }
                            true
                        }
                        //Layout button
                        val locationButton =
                                (mapFragment.requireView().findViewById<View>("1".toInt()).parent as View)
                                        .findViewById<View>("2".toInt())
                        val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                        params.addRule(RelativeLayout.ALIGN_TOP, 0)
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                        params.bottomMargin = 250 // Move to see zoom control
                    }

                    override fun onPermissionRationaleShouldBeShown(
                            p0: PermissionRequest?,
                            p1: PermissionToken?
                    ) {


                    }

                    override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                        Snackbar.make(
                                requireView(),
                                p0!!.permissionName + "needed for run app",
                                Snackbar.LENGTH_SHORT
                        ).show()
                    }
                })
                .check()


        //Enable zoom
        mMap.uiSettings.isZoomControlsEnabled = true


        try {
            val success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            context,
                            R.raw.uber_maps_style
                    )
            )
            if (!success)
                Log.e(TAG, "onMapReady: Style Parsing Error")
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "onMapReady: " + e.message)
        }
    }

}