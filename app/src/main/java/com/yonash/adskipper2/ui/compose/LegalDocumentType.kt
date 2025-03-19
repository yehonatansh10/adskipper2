package com.yonash.adskipper2.ui.compose

/**
 * סוג מסמך משפטי עם אפשרות בחירת שפה
 */
enum class LegalDocumentType(val fileName: String) {
    // מדיניות פרטיות
    PRIVACY_POLICY_HEBREW("privacy_policy_he.html"),
    PRIVACY_POLICY_ENGLISH("privacy_policy_en.html"),

    // תנאי שימוש
    TERMS_OF_SERVICE_HEBREW("terms_of_service_he.html"),
    TERMS_OF_SERVICE_ENGLISH("terms_of_service_en.html"),

    // תמיכה
    SUPPORT_HEBREW("support_he.html"),
    SUPPORT_ENGLISH("support_en.html");

    companion object {
        fun getPrivacyPolicy(isHebrew: Boolean = true): LegalDocumentType {
            return if (isHebrew) PRIVACY_POLICY_HEBREW else PRIVACY_POLICY_ENGLISH
        }

        fun getTermsOfService(isHebrew: Boolean = true): LegalDocumentType {
            return if (isHebrew) TERMS_OF_SERVICE_HEBREW else TERMS_OF_SERVICE_ENGLISH
        }

        fun getSupport(isHebrew: Boolean = true): LegalDocumentType {
            return if (isHebrew) SUPPORT_HEBREW else SUPPORT_ENGLISH
        }
    }
}