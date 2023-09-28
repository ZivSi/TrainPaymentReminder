package com.zs.trainpaymentreminder

import android.os.Parcel
import android.os.Parcelable

data class Location(var latitude: Double, var longitude: Double) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readDouble(), parcel.readDouble()
    )

    override fun toString(): String {
        return "$latitude, $longitude"
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Location> {
        override fun createFromParcel(parcel: Parcel): Location {
            return Location(parcel)
        }

        override fun newArray(size: Int): Array<Location?> {
            return arrayOfNulls(size)
        }
    }
}
