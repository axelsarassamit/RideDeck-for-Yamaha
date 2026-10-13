package com.axelsarassamit.gx12
import android.content.Context
import android.location.*
import android.os.Build
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
object SystemAddressSearch {
    fun search(context:Context,query:String):List<NavigationPlace> {
        if(!Geocoder.isPresent()) return emptyList()
        val geocoder=Geocoder(context,Locale.ENGLISH)
        val addresses=if(Build.VERSION.SDK_INT>=33) {
            val latch=CountDownLatch(1);var result:List<Address> = emptyList()
            geocoder.getFromLocationName(query,5,object:Geocoder.GeocodeListener {
                override fun onGeocode(values:MutableList<Address>) { result=values;latch.countDown() }
                override fun onError(message:String?) { latch.countDown() }
            });latch.await(8,TimeUnit.SECONDS);result
        } else { @Suppress("DEPRECATION") geocoder.getFromLocationName(query,5).orEmpty() }
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }.map {
            NavigationPlace(it.getAddressLine(0) ?: it.featureName ?: query,it.latitude,it.longitude,"system_address",0.0)
        }
    }
}
