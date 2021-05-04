package com.example.userbooking.model

import com.firebase.geofire.GeoLocation

class DriverGeoModel() {
    var key: String? = null
    var geoLocal: GeoLocation? = null
    var driverInfoModel: DriverInfoModel? = null

    constructor(key: String, geoLocation: GeoLocation) {
        this.key = key
        this.geoLocal = geoLocal!!
    }
}