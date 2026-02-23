package com.blackbox.domain.query

import com.blackbox.domain.model.query.EntityType
import com.blackbox.domain.model.query.Language
import com.blackbox.domain.model.query.QueryEntity

/**
 * Extracts named entities from a normalized query.
 *
 * Identifies place names, activity types, and app names using
 * keyword dictionaries for both English and Hebrew. Extracted
 * entities are used to further scope database queries.
 */
class EntityExtractor {

    /**
     * Extracts all recognized entities from the query text.
     *
     * @param normalizedText The preprocessed, lowercased query text.
     * @param language The detected query language.
     * @return List of extracted [QueryEntity] instances.
     */
    fun extract(normalizedText: String, language: Language): List<QueryEntity> {
        val entities = mutableListOf<QueryEntity>()

        entities.addAll(extractPlaceNames(normalizedText, language))
        entities.addAll(extractActivityTypes(normalizedText, language))
        entities.addAll(extractAppNames(normalizedText))

        return entities
    }

    /** Extracts place name entities from the query. */
    private fun extractPlaceNames(text: String, language: Language): List<QueryEntity> {
        val placeKeywords = when (language) {
            Language.ENGLISH -> EN_PLACES
            Language.HEBREW -> HE_PLACES
        }

        return placeKeywords
            .filter { (keyword, _) -> text.contains(keyword) }
            .map { (keyword, normalized) ->
                QueryEntity(
                    type = EntityType.PLACE_NAME,
                    value = keyword,
                    normalizedValue = normalized,
                )
            }
    }

    /** Extracts activity type entities from the query. */
    private fun extractActivityTypes(text: String, language: Language): List<QueryEntity> {
        val activityKeywords = when (language) {
            Language.ENGLISH -> EN_ACTIVITIES
            Language.HEBREW -> HE_ACTIVITIES
        }

        return activityKeywords
            .filter { (keyword, _) -> text.contains(keyword) }
            .map { (keyword, normalized) ->
                QueryEntity(
                    type = EntityType.ACTIVITY_TYPE,
                    value = keyword,
                    normalizedValue = normalized,
                )
            }
    }

    /** Extracts app name entities from the query. */
    private fun extractAppNames(text: String): List<QueryEntity> {
        return APP_NAMES
            .filter { (keyword, _) -> text.contains(keyword) }
            .map { (keyword, normalized) ->
                QueryEntity(
                    type = EntityType.APP_NAME,
                    value = keyword,
                    normalizedValue = normalized,
                )
            }
    }

    companion object {
        /** English place keywords → normalized form. */
        private val EN_PLACES = listOf(
            "home" to "HOME",
            "office" to "OFFICE",
            "work" to "OFFICE",
            "gym" to "GYM",
            "school" to "SCHOOL",
            "university" to "UNIVERSITY",
            "hospital" to "HOSPITAL",
            "store" to "STORE",
            "shop" to "STORE",
            "mall" to "MALL",
            "restaurant" to "RESTAURANT",
            "cafe" to "CAFE",
            "park" to "PARK",
            "airport" to "AIRPORT",
            "station" to "STATION",
            "supermarket" to "SUPERMARKET",
        )

        /** Hebrew place keywords → normalized form. */
        private val HE_PLACES = listOf(
            "בית" to "HOME",
            "הבית" to "HOME",
            "משרד" to "OFFICE",
            "עבודה" to "OFFICE",
            "חדר כושר" to "GYM",
            "בית ספר" to "SCHOOL",
            "אוניברסיטה" to "UNIVERSITY",
            "בית חולים" to "HOSPITAL",
            "חנות" to "STORE",
            "קניון" to "MALL",
            "מסעדה" to "RESTAURANT",
            "בית קפה" to "CAFE",
            "פארק" to "PARK",
            "שדה תעופה" to "AIRPORT",
            "תחנה" to "STATION",
            "סופר" to "SUPERMARKET",
        )

        /** English activity keywords → normalized form. */
        private val EN_ACTIVITIES = listOf(
            "walking" to "WALKING",
            "walk" to "WALKING",
            "running" to "RUNNING",
            "run" to "RUNNING",
            "driving" to "IN_VEHICLE",
            "drive" to "IN_VEHICLE",
            "cycling" to "ON_BICYCLE",
            "biking" to "ON_BICYCLE",
            "sitting" to "STILL",
            "still" to "STILL",
            "sleeping" to "STILL",
        )

        /** Hebrew activity keywords → normalized form. */
        private val HE_ACTIVITIES = listOf(
            "הליכה" to "WALKING",
            "הולך" to "WALKING",
            "ריצה" to "RUNNING",
            "רץ" to "RUNNING",
            "נהיגה" to "IN_VEHICLE",
            "נוהג" to "IN_VEHICLE",
            "רכיבה" to "ON_BICYCLE",
            "יושב" to "STILL",
            "ישן" to "STILL",
        )

        /** App name keywords (language-independent) → normalized package hint. */
        private val APP_NAMES = listOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "youtube" to "com.google.android.youtube",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "spotify" to "com.spotify.music",
            "waze" to "com.waze",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera",
            "phone" to "com.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "tiktok" to "com.zhiliaoapp.musically",
        )
    }
}
