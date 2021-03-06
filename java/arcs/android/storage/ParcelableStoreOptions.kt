/*
 * Copyright 2019 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */

package arcs.android.storage

import android.os.Parcel
import android.os.Parcelable
import arcs.android.crdt.ParcelableCrdtType
import arcs.android.type.readType
import arcs.android.type.writeType
import arcs.core.storage.StorageKeyParser
import arcs.core.storage.StoreOptions

/** [Parcelable] variant for [StoreOptions]. */
data class ParcelableStoreOptions(
  val actual: StoreOptions,
  val crdtType: ParcelableCrdtType
) : Parcelable {
  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeInt(crdtType.ordinal)
    parcel.writeString(actual.storageKey.toString())
    parcel.writeType(actual.type, flags)
    parcel.writeString(actual.versionToken)
  }

  override fun describeContents(): Int = 0

  companion object CREATOR : Parcelable.Creator<ParcelableStoreOptions> {
    override fun createFromParcel(parcel: Parcel): ParcelableStoreOptions {
      val crdtType = ParcelableCrdtType.values()[parcel.readInt()]
      val storageKey = StorageKeyParser.parse(requireNotNull(parcel.readString()))
      val type = requireNotNull(parcel.readType()) { "Could not extract Type from Parcel" }
      val versionToken = parcel.readString()

      return ParcelableStoreOptions(
        StoreOptions(
          storageKey = storageKey,
          type = type,
          versionToken = versionToken
        ),
        crdtType
      )
    }

    override fun newArray(size: Int): Array<ParcelableStoreOptions?> = arrayOfNulls(size)
  }
}

/**
 * Wraps the [StoreOptions] in a [ParcelableStoreOptions], using the [ParcelableCrdtType] as a hint.
 */
fun StoreOptions.toParcelable(
  crdtType: ParcelableCrdtType
): ParcelableStoreOptions = ParcelableStoreOptions(this, crdtType)

/** Writes [StoreOptions] to the [Parcel]. */
fun Parcel.writeStoreOptions(
  storeOptions: StoreOptions,
  representingCrdtType: ParcelableCrdtType,
  flags: Int
) = writeTypedObject(storeOptions.toParcelable(representingCrdtType), flags)

/** Reads [StoreOptions] from the [Parcel]. */
fun Parcel.readStoreOptions(): StoreOptions? =
  readTypedObject(ParcelableStoreOptions)?.actual
