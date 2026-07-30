package com.gysignalstudio.blackswan.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BillingManager {
    const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

    private const val TAG = "BillingManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionStarted = AtomicBoolean(false)

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null
    private var removeAdsProductDetails: ProductDetails? = null

    private val _isReady = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val isReady = _isReady.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val message = _message.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        Log.i(
            TAG,
            "PurchasesUpdatedListener: ${billingResult.toLogString()}, " +
                "purchaseCount=${purchases.orEmpty().size}, products=${purchases.orEmpty().flatMap { it.products }}"
        )
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach(::handlePurchase)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _message.value = "Purchase cancelled."
            }
            else -> {
                _message.value = billingResult.debugMessage.takeIf(String::isNotBlank)
                    ?: "Purchase failed."
            }
        }
        _isLoading.value = false
    }

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (!connectionStarted.compareAndSet(false, true)) {
            Log.d(TAG, "connect skipped: connection already started")
            return
        }

        Log.i(
            TAG,
            "Starting BillingClient connection. productId=$REMOVE_ADS_PRODUCT_ID, " +
                "productType=${BillingClient.ProductType.INAPP}"
        )
        val client = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.i(TAG, "onBillingSetupFinished: ${billingResult.toLogString()}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isReady.value = true
                    loadProductDetails()
                    restorePurchases()
                } else {
                    _isReady.value = false
                    _message.value = billingResult.debugMessage.takeIf(String::isNotBlank)
                        ?: "Billing setup failed."
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isReady.value = false
                connectionStarted.set(false)
            }
        })
    }

    fun loadProductDetails() {
        val client = billingClient ?: run {
            Log.w(TAG, "loadProductDetails skipped: billingClient is null")
            return
        }
        if (!client.isReady) {
            Log.w(TAG, "loadProductDetails skipped: billingClient is not ready")
            return
        }

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(REMOVE_ADS_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        Log.i(
            TAG,
            "queryProductDetailsAsync start. productId=$REMOVE_ADS_PRODUCT_ID, " +
                "productType=${BillingClient.ProductType.INAPP}"
        )
        client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            val productDetailsList = productDetailsResult.productDetailsList
            val unfetchedProductList = productDetailsResult.unfetchedProductList
            Log.i(
                TAG,
                "queryProductDetailsAsync result: ${billingResult.toLogString()}, " +
                    "productDetailsCount=${productDetailsList.size}, " +
                    "unfetchedProductCount=${unfetchedProductList.size}"
            )
            productDetailsList.forEach { productDetails ->
                Log.i(TAG, "Fetched product: ${productDetails.toLogString()}")
            }
            unfetchedProductList.forEach { unfetchedProduct ->
                Log.w(
                    TAG,
                    "Unfetched product: productId=${unfetchedProduct.productId}, " +
                        "productType=${unfetchedProduct.productType}, " +
                        "statusCode=${unfetchedProduct.statusCode}, " +
                        "serializedDocid=${unfetchedProduct.serializedDocid}"
                )
            }

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                removeAdsProductDetails = productDetailsList
                    .firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
                if (removeAdsProductDetails == null) {
                    val unfetchedRemoveAds = unfetchedProductList
                        .firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
                    Log.w(
                        TAG,
                        "remove_ads ProductDetails not returned. " +
                            "reason=${unfetchedRemoveAds?.let { "unfetchedStatusCode=${it.statusCode}" } ?: "not present in fetched or unfetched lists"}"
                    )
                }
            } else {
                _message.value = billingResult.debugMessage.takeIf(String::isNotBlank)
                    ?: "Could not load purchase details."
            }
        }
    }

    fun launchRemoveAdsPurchase(activity: Activity) {
        connect(activity.applicationContext)
        val client = billingClient
        val productDetails = removeAdsProductDetails
        if (client == null || !client.isReady) {
            Log.w(TAG, "launchRemoveAdsPurchase blocked: billingClientReady=${client?.isReady}")
            _message.value = "Billing is not ready yet."
            return
        }
        if (productDetails == null) {
            Log.w(
                TAG,
                "launchRemoveAdsPurchase blocked: ProductDetails is null for " +
                    "productId=$REMOVE_ADS_PRODUCT_ID, productType=${BillingClient.ProductType.INAPP}"
            )
            _message.value = "Remove Ads is not available yet. Please try again."
            loadProductDetails()
            return
        }

        _isLoading.value = true
        Log.i(TAG, "Launching billing flow for ${productDetails.toLogString()}")
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = client.launchBillingFlow(activity, billingFlowParams)
        Log.i(TAG, "launchBillingFlow result: ${result.toLogString()}")
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _isLoading.value = false
            _message.value = result.debugMessage.takeIf(String::isNotBlank)
                ?: "Could not start purchase."
        }
    }

    fun restorePurchases() {
        val client = billingClient ?: run {
            Log.w(TAG, "restorePurchases skipped: billingClient is null")
            return
        }
        val context = appContext ?: run {
            Log.w(TAG, "restorePurchases skipped: appContext is null")
            return
        }
        if (!client.isReady) {
            Log.w(TAG, "restorePurchases skipped: billingClient is not ready")
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        Log.i(TAG, "queryPurchasesAsync start. productType=${BillingClient.ProductType.INAPP}")
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            Log.i(
                TAG,
                "queryPurchasesAsync result: ${billingResult.toLogString()}, " +
                    "purchaseCount=${purchases.size}, products=${purchases.flatMap { it.products }}"
            )
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _message.value = billingResult.debugMessage.takeIf(String::isNotBlank)
                    ?: "Could not restore purchases."
                return@queryPurchasesAsync
            }

            val ownedRemoveAds = purchases.any { purchase ->
                purchase.products.contains(REMOVE_ADS_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.isAcknowledged
            }
            scope.launch {
                EntitlementManager.setAdFree(context, ownedRemoveAds)
            }
            purchases.filter { it.products.contains(REMOVE_ADS_PRODUCT_ID) }
                .forEach(::handlePurchase)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.i(
            TAG,
            "handlePurchase: products=${purchase.products}, state=${purchase.purchaseState}, " +
                "acknowledged=${purchase.isAcknowledged}"
        )
        if (!purchase.products.contains(REMOVE_ADS_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Log.w(TAG, "remove_ads purchase ignored: purchaseState=${purchase.purchaseState}")
            return
        }

        if (!purchase.isAcknowledged) {
            acknowledgePurchase(purchase)
            return
        }

        appContext?.let { context ->
            scope.launch {
                EntitlementManager.setAdFree(context, true)
                _message.value = "Ad-free is active."
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: run {
            Log.w(TAG, "acknowledgePurchase skipped: billingClient is null")
            return
        }
        val context = appContext ?: run {
            Log.w(TAG, "acknowledgePurchase skipped: appContext is null")
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        Log.i(TAG, "acknowledgePurchase start for products=${purchase.products}")
        client.acknowledgePurchase(params) { billingResult ->
            Log.i(TAG, "acknowledgePurchase result: ${billingResult.toLogString()}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch {
                    EntitlementManager.setAdFree(context, true)
                    _message.value = "Ad-free is active."
                }
            } else {
                _message.value = billingResult.debugMessage.takeIf(String::isNotBlank)
                    ?: "Purchase could not be acknowledged."
            }
            _isLoading.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun BillingResult.toLogString(): String =
        "responseCode=$responseCode(${responseCodeName(responseCode)}), " +
            "debugMessage=${debugMessage.ifBlank { "<empty>" }}"

    private fun ProductDetails.toLogString(): String {
        val offers = oneTimePurchaseOfferDetailsList.orEmpty().joinToString(
            prefix = "[",
            postfix = "]"
        ) { offer ->
            "formattedPrice=${offer.formattedPrice}, " +
                "currency=${offer.priceCurrencyCode}, " +
                "priceMicros=${offer.priceAmountMicros}, " +
                "offerId=${offer.offerId}, " +
                "purchaseOptionId=${offer.purchaseOptionId}"
        }
        return "productId=$productId, productType=$productType, title=$title, name=$name, offers=$offers"
    }

    private fun responseCodeName(responseCode: Int): String = when (responseCode) {
        BillingClient.BillingResponseCode.OK -> "OK"
        BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
        BillingClient.BillingResponseCode.ERROR -> "ERROR"
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR"
        else -> "UNKNOWN"
    }
}
