package com.mybalance.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** Limite di fondi nella versione gratuita. */
const val FREE_FUND_LIMIT = 3

/** Codici dei prodotti da creare in Play Console (Prodotti > Prodotti in-app). */
const val PRODUCT_PRO = "pro"
val COFFEE_PRODUCTS = listOf("coffee_1" to "1 €", "coffee_2" to "2 €", "coffee_3" to "3 €", "coffee_5" to "5 €")

/**
 * Acquisti con Google Play Billing:
 * - "pro": acquisto unico, sblocca fondi illimitati (resta anche dopo la reinstallazione: viene riletto da Google Play)
 * - "coffee_*": donazioni, "consumate" subito cosi' si possono rifare.
 */
class BillingManager(app: Application, private val store: Store) : PurchasesUpdatedListener {
    var isPro by mutableStateOf(store.isPro)
        private set
    var prices by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** Messaggio da mostrare all'utente dopo un acquisto (null = niente). */
    var message by mutableStateOf<String?>(null)

    private var details: Map<String, ProductDetails> = emptyMap()

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        connect()
    }

    private fun connect() {
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        loadProducts()
                        restore()
                    }
                }

                override fun onBillingServiceDisconnected() {}
            })
        } catch (e: Exception) {
            // Google Play non disponibile: l'app funziona comunque (versione gratuita)
        }
    }

    private fun loadProducts() {
        val ids = listOf(PRODUCT_PRO) + COFFEE_PRODUCTS.map { it.first }
        val list = ids.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(list).build()
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = found.associateBy { it.productId }
                prices = found.mapNotNull { d ->
                    d.oneTimePurchaseOfferDetails?.formattedPrice?.let { d.productId to it }
                }.toMap()
            }
        }
    }

    /** Rilegge gli acquisti gia' fatti (Pro dopo una reinstallazione, acquisti rimasti in sospeso). */
    private fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val has = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(PRODUCT_PRO)
                }
                setPro(has)
                purchases.forEach { handle(it, silent = true) }
            }
        }
    }

    private fun setPro(value: Boolean) {
        store.isPro = value
        isPro = value
    }

    private fun handle(purchase: Purchase, silent: Boolean = false) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.products.contains(PRODUCT_PRO)) {
            setPro(true)
            if (!purchase.isAcknowledged) {
                val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(ack) { }
            }
            if (!silent) message = "pro"
        } else {
            val consume = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.consumeAsync(consume) { r, _ ->
                if (!silent && r.responseCode == BillingClient.BillingResponseCode.OK) message = "coffee"
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { handle(it) }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            message = "error"
        }
    }

    /** Avvia l'acquisto (apre la schermata di pagamento di Google Play). */
    fun buy(activity: Activity, productId: String) {
        val d = details[productId]
        if (d == null) {
            message = "unavailable"
            return
        }
        val pd = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pd)).build()
        client.launchBillingFlow(activity, flow)
    }

    /** Riprova a collegarsi (es. dopo un errore). */
    fun refresh() {
        if (!client.isReady) connect() else {
            loadProducts()
            restore()
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Finestre di Pro e "Offrimi un caffe'". */
@Composable
fun PurchaseDialogs(
    vm: MainViewModel,
    showPro: Boolean,
    onClosePro: () -> Unit,
    showCoffee: Boolean,
    onCloseCoffee: () -> Unit
) {
    val l = LocalL.current
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val b = vm.billing

    if (showPro) {
        val price = b.prices[PRODUCT_PRO] ?: "2 €"
        ConfirmDialog(
            title = l("MyBalance Pro", "MyBalance Pro"),
            message = l(
                "The free version lets you create up to $FREE_FUND_LIMIT funds. Go Pro for $price, one time only, to create unlimited funds.",
                "La versione gratuita permette di creare fino a $FREE_FUND_LIMIT fondi. Passa a Pro per $price, una sola volta, per avere fondi illimitati."
            ),
            confirm = l("Go Pro", "Passa a Pro"),
            onConfirm = {
                onClosePro()
                ctx.findActivity()?.let { b.buy(it, PRODUCT_PRO) }
            },
            onDismiss = onClosePro
        )
    }

    if (showCoffee) {
        var sel by remember { mutableStateOf<String?>(null) }
        FormDialog(
            title = l("Buy me a coffee", "Offrimi un caffè"),
            confirm = l("Donate", "Dona"),
            enabled = sel != null,
            onConfirm = {
                val id = sel
                onCloseCoffee()
                if (id != null) ctx.findActivity()?.let { b.buy(it, id) }
            },
            onDismiss = onCloseCoffee
        ) {
            Column {
                Text(
                    l(
                        "Thank you! A small donation from 1 to 5 euros helps me keep improving MyBalance.",
                        "Grazie! Una piccola donazione da 1 a 5 euro mi aiuta a migliorare MyBalance."
                    ),
                    color = p.muted, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ChipFlow {
                    for ((id, fallback) in COFFEE_PRODUCTS) {
                        Chip(b.prices[id] ?: fallback, sel == id, { sel = id })
                    }
                }
            }
        }
    }

    val msg = b.message
    if (msg != null) {
        val text = when (msg) {
            "pro" -> l("MyBalance Pro is active. Thank you!", "MyBalance Pro è attivo. Grazie!")
            "coffee" -> l("Thank you for the coffee!", "Grazie per il caffè!")
            "unavailable" -> l(
                "Purchases are not available right now. Install the app from Google Play and try again.",
                "Gli acquisti non sono disponibili ora. Installa l'app da Google Play e riprova."
            )
            else -> l("Something went wrong. Please try again.", "Qualcosa è andato storto. Riprova.")
        }
        ConfirmDialog(
            title = "MyBalance",
            message = text,
            confirm = "OK",
            onConfirm = { b.message = null },
            onDismiss = { b.message = null }
        )
    }
}
