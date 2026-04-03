package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneOffset

private const val TAG = "SalesLeadRepository"
private const val CREATE_SALES_PIPELINE_LEAD_FUNCTION = "createSalesPipelineLead"
private const val SALES_PIPELINE_COLLECTION = "sales_pipeline"
private const val SALES_PIPELINE_STATUS_INITIATED = "initiated"
private val REQUEST_NUMBER_RANDOM = SecureRandom()

private fun normalizeLeadLocationPart(value: String?): String = value
    .orEmpty()
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")

private fun inferLeadCategory(productName: String, chatMessageText: String = ""): String {
    val haystack = "$productName $chatMessageText".trim().lowercase()
    return when {
        listOf("fertili", "urea", "dap", "npk", "potash", "micronutrient", "manure").any { haystack.contains(it) } -> "fertilizer"
        listOf("pesticide", "fungicide", "herbicide", "insecticide", "spray", "weedicide").any { haystack.contains(it) } -> "pesticide"
        listOf("seed", "seeds", "hybrid", "variety", "nursery", "sapling").any { haystack.contains(it) } -> "seed"
        else -> "other"
    }
}

private fun toPositiveDoubleOrNull(value: Any?): Double? = when (value) {
    is Number -> value.toDouble().takeIf { it > 0 }
    is String -> value.toDoubleOrNull()?.takeIf { it > 0 }
    else -> null
}

private fun buildInitialRoutingFields(
    farmerProfileData: Map<String, Any?>,
    productName: String,
    chatMessageText: String,
): Map<String, Any?> {
    val district = farmerProfileData["district"]?.toString()?.trim().orEmpty()
    val tehsil = farmerProfileData["tehsil"]?.toString()?.trim().orEmpty()
    val village = farmerProfileData["village"]?.toString()?.trim().orEmpty()
    val leadCategory = inferLeadCategory(productName, chatMessageText)

    return mapOf(
        "leadCategory" to leadCategory,
        "leadLocation" to mapOf(
            "district" to district,
            "districtKey" to normalizeLeadLocationPart(district),
            "tehsil" to tehsil,
            "tehsilKey" to normalizeLeadLocationPart(tehsil),
            "village" to village,
            "villageKey" to normalizeLeadLocationPart(village),
        ),
        "routingStatus" to "initiated",
        "recommendationStatus" to "pending",
        "suggestedSupplier" to null,
        "assignedSupplier" to null,
        "commissionPreview" to mapOf(
            "category" to leadCategory,
            "amount" to null,
            "currency" to "INR",
            "ruleId" to null,
        ),
        "suggestionGeneratedAt" to null,
        "assignmentPublishedAt" to null,
        "supplierResponseDeadlineAt" to null,
        "supplierRespondedAt" to null,
        "supplierRejectedReason" to null,
        "adminFallbackReason" to null,
        "lastRoutingUpdatedAt" to null,
    )
}

private fun buildLeadFarmerProfileSnapshot(
    farmerProfileData: Map<String, Any?>,
    authPhoneNumber: String?,
    authEmail: String?,
): Map<String, Any?> {
    val mobileNumber = normalizeLeadMobileNumber(
        farmerProfileData["mobileNumber"]?.toString()
            ?: farmerProfileData["phoneNumber"]?.toString()
            ?: authPhoneNumber
    )
    val emailId = normalizeLeadEmail(
        farmerProfileData["emailId"]?.toString()
            ?: farmerProfileData["email"]?.toString()
            ?: authEmail
    )

    val snapshot = linkedMapOf<String, Any?>()

    fun putIfNotBlank(key: String, value: Any?) {
        value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { snapshot[key] = it }
    }

    putIfNotBlank("name", farmerProfileData["name"])
    putIfNotBlank("village", farmerProfileData["village"])
    putIfNotBlank("tehsil", farmerProfileData["tehsil"])
    putIfNotBlank("district", farmerProfileData["district"])
    toPositiveDoubleOrNull(farmerProfileData["totalFarmAcres"])?.let { snapshot["totalFarmAcres"] = it }

    snapshot["mobileNumber"] = mobileNumber
    snapshot["phoneNumber"] = mobileNumber
    snapshot["emailId"] = emailId
    snapshot["email"] = emailId

    return snapshot
}

internal fun normalizeSalesLeadProductName(productName: String): String = productName
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")

internal fun buildSalesLeadDocId(userId: String, conversationId: String, productName: String): String {
    val input = "$userId|$conversationId|${normalizeSalesLeadProductName(productName)}"
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString(separator = "") { "%02x".format(it) }
        .take(32)
}

internal fun generateSalesLeadRequestNumber(now: LocalDate = LocalDate.now(ZoneOffset.UTC)): String {
    val randomBytes = ByteArray(3).also(REQUEST_NUMBER_RANDOM::nextBytes)
    val suffix = randomBytes.joinToString(separator = "") { "%02X".format(it) }
    val datePart = "%04d%02d%02d".format(now.year, now.monthValue, now.dayOfMonth)
    return "KR-$datePart-$suffix"
}

private fun validateDirectSubmissionRequest(request: SalesLeadRequest) {
    require(request.conversationId.isNotBlank()) { "We could not register this request. Please try again." }
    require(request.productName.isNotBlank()) { "We could not register this request. Please try again." }
    require(request.chatMessageText.isNotBlank()) { "We could not register this request. Please try again." }
}

private fun FirebaseFunctionsException.shouldFallbackToFirestore(): Boolean = when (code) {
    FirebaseFunctionsException.Code.NOT_FOUND,
    FirebaseFunctionsException.Code.UNAVAILABLE,
    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> true
    else -> false
}

/**
 * Client-side repository for submitting product-interest leads to the backend sales pipeline.
 * The backend owns dedupe, request number generation, and final persistence.
 */
class SalesLeadRepository {

    companion object {
        @Volatile
        private var instance: SalesLeadRepository? = null

        fun getInstance(): SalesLeadRepository {
            return instance ?: synchronized(this) {
                instance ?: SalesLeadRepository().also { instance = it }
            }
        }
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun submitLead(request: SalesLeadRequest): SalesLeadSubmissionResult {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in required")

        val payload = hashMapOf(
            "conversationId" to request.conversationId,
            "productName" to request.productName,
            "quantity" to request.quantity,
            "unit" to request.unit,
            "chatMessageText" to request.chatMessageText,
            "source" to request.source,
            "userId" to uid
        )

        return try {
            val response = functions
                .getHttpsCallable(CREATE_SALES_PIPELINE_LEAD_FUNCTION)
                .call(payload)
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as? Map<String, Any?>
                ?: throw IllegalStateException("Unexpected response from sales pipeline")

            val requestNumber = data["requestNumber"] as? String
                ?: throw IllegalStateException("Missing request number")

            SalesLeadSubmissionResult(
                requestNumber = requestNumber,
                status = data["status"] as? String ?: SALES_PIPELINE_STATUS_INITIATED
            )
        } catch (e: FirebaseFunctionsException) {
            if (!e.shouldFallbackToFirestore()) {
                Log.e(TAG, "Cloud Function rejected sales lead submission: ${e.message}", e)
                throw e
            }

            Log.w(TAG, "Cloud Function unavailable for sales lead submission, falling back to Firestore create", e)
            submitLeadDirect(uid, request)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting sales lead: ${e.message}", e)
            throw e
        }
    }

    private suspend fun submitLeadDirect(userId: String, request: SalesLeadRequest): SalesLeadSubmissionResult {
        validateDirectSubmissionRequest(request)

        val conversationId = request.conversationId.trim()
        val productName = request.productName.trim()
        val chatMessageText = request.chatMessageText.trim()
        val quantity = request.quantity?.trim().orEmpty().ifBlank { null }
        val unit = request.unit?.trim().orEmpty().ifBlank { null }
        val source = request.source.trim().ifBlank { "chat_recommendation" }
        val normalizedProductName = normalizeSalesLeadProductName(productName)
        val docId = buildSalesLeadDocId(userId, conversationId, productName)
        val authPhoneNumber = auth.currentUser?.phoneNumber
        val authEmail = auth.currentUser?.email

        val farmerProfileRef = firestore.farmerProfileDocument(userId)
        val leadRef = firestore.collection(SALES_PIPELINE_COLLECTION).document(docId)

        return try {
            firestore.runTransaction { transaction ->
                val farmerProfileSnap = transaction.get(farmerProfileRef)
                val farmerProfile = farmerProfileSnap
                    .toObject(FarmerProfile::class.java)
                    ?.withLeadContactFallbacks(authPhoneNumber, authEmail)
                    ?: FarmerProfile().withLeadContactFallbacks(authPhoneNumber, authEmail)
                if (!farmerProfile.hasLeadRequiredFields()) {
                    throw IllegalStateException("Please complete your profile to continue")
                }

                val farmerProfileSnapshot = buildLeadFarmerProfileSnapshot(
                    farmerProfileSnap.data.orEmpty(),
                    authPhoneNumber,
                    authEmail,
                )

                val existingSnap = transaction.get(leadRef)
                if (existingSnap.exists()) {
                    val existingData = existingSnap.data.orEmpty()
                    val existingRequestNumber = existingData["requestNumber"] as? String
                        ?: generateSalesLeadRequestNumber()
                    val existingStatus = existingData["status"] as? String ?: SALES_PIPELINE_STATUS_INITIATED
                    return@runTransaction SalesLeadSubmissionResult(
                        requestNumber = existingRequestNumber,
                        status = existingStatus
                    )
                }

                val requestNumber = generateSalesLeadRequestNumber()
                transaction.set(
                    leadRef,
                    hashMapOf(
                        "userId" to userId,
                        "conversationId" to conversationId,
                        "requestNumber" to requestNumber,
                        "status" to SALES_PIPELINE_STATUS_INITIATED,
                        "source" to source,
                        "dedupeKey" to docId,
                        "productName" to productName,
                        "normalizedProductName" to normalizedProductName,
                        "quantity" to quantity,
                        "unit" to unit,
                        "chatMessageText" to chatMessageText,
                        "farmerProfileSnapshot" to farmerProfileSnapshot,
                        *buildInitialRoutingFields(
                            farmerProfileSnapshot,
                            productName,
                            chatMessageText,
                        ).toList().map { it.first to it.second }.toTypedArray(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                SalesLeadSubmissionResult(
                    requestNumber = requestNumber,
                    status = SALES_PIPELINE_STATUS_INITIATED
                )
            }.await()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting sales lead directly to Firestore: ${e.message}", e)
            throw IllegalStateException("Unable to register your request right now")
        }
    }
}

data class SalesLeadRequest(
    val conversationId: String,
    val productName: String,
    val quantity: String? = null,
    val unit: String? = null,
    val chatMessageText: String,
    val source: String = "chat_recommendation"
)

data class SalesLeadSubmissionResult(
    val requestNumber: String,
    val status: String
)

data class LeadProfileDraft(
    val name: String = "",
    val mobileNumber: String = "",
    val village: String = "",
    val tehsil: String = "",
    val district: String = "",
    val totalFarmAcres: String = ""
) {
    fun normalized(): LeadProfileDraft = copy(
        name = name.trim(),
        mobileNumber = sanitizeLeadMobileInput(mobileNumber),
        village = village.trim(),
        tehsil = tehsil.trim(),
        district = district.trim(),
        totalFarmAcres = totalFarmAcres.trim()
    )
}
