package com.gysignalstudio.blackswan.billing

import android.content.Context
import com.gysignalstudio.blackswan.ads.AdMobManager
import com.gysignalstudio.blackswan.data.local.LocalDataStore
import kotlinx.coroutines.flow.Flow

object EntitlementManager {
    fun isAdFree(context: Context): Flow<Boolean> =
        LocalDataStore(context.applicationContext).adsDisabled

    suspend fun setAdFree(context: Context, enabled: Boolean) {
        LocalDataStore(context.applicationContext).setAdsDisabled(enabled)
        AdMobManager.setAdsDisabled(enabled)
    }
}
