# Plan: Mandi Prices Feature - Firestore Data Storage & Display

**TL;DR:** Implement a two-part solution: (1) A Firebase Cloud Function to periodically fetch data from the government API and store it in a flat Firestore collection with composite indexes, and (2) An Android screen accessible via the navigation drawer that queries Firestore with cascading filters (state → district → commodity) to display commodity prices. User preferences (state/district) are saved to Firestore and used to show a compact UI on subsequent visits.

## Implementation Status

### Completed ✅

1. **Firestore data structure for Mandi Prices**  
   - Created `MandiPrice.kt` data class with fields: `state`, `district`, `market`, `commodity`, `variety`, `grade`, `arrival_date`, `min_price`, `max_price`, `modal_price`
   - Created `MandiPreferences.kt` data class for user preferences
   - Created `firestore.indexes.json` with composite indexes for common filter combinations

2. **Firebase Cloud Function for data ingestion**  
   - Created `functions/index.js` with:
     - `syncMandiPrices`: Scheduled function (daily at 6 AM IST)
     - `syncMandiPricesManual`: HTTP endpoint for manual triggering
   - Uses batch writes for efficiency
   - Includes sync metadata tracking

3. **Added drawer menu items**  
   - `MandiPrices`: Access mandi prices screen
   - `MandiSettings`: Access preferences screen
   - Added `ic_price.xml` and `ic_settings.xml` icons

4. **Created data layer for Mandi Prices**  
   - `MandiPrice.kt`: Data model for Firestore documents
   - `MandiPreferences.kt`: Data model for user preferences
   - `MandiPriceRepository.kt`: Repository with:
     - Cascading filter queries (getStates, getDistricts, getCommodities, getMarkets, getMandiPrices)
     - User preferences methods (getUserPreferences, saveUserPreferences, updateLastCommodity)

5. **Created MandiPricesScreen with compact/full mode UI**  
   - **Compact mode** (returning user with saved preferences):
     - Shows location chip with saved state/district
     - "Change" button to switch to full mode
     - Commodity dropdown + Search button only
   - **Full mode** (first-time user or changing preferences):
     - State → District → Commodity cascading dropdowns
     - Cancel button to return to compact mode (if user has preferences)
   - Auto-saves preferences after first successful search
   - Remembers last searched commodity

6. **Created MandiPreferencesScreen for dedicated preference editing**  
   - Shows current saved location (if exists)
   - State and District dropdowns
   - Save Preferences button
   - Accessible via "Mandi Preferences" in drawer menu

7. **Wired navigation**  
   - Added `MandiPrices` and `MandiSettings` routes to `Screen.kt`
   - Updated `AppNavigation.kt` with both screens
   - Updated `ChatScreen.kt` to handle both drawer item clicks

### Files Created/Modified

**New Files:**
- `app/src/main/java/com/maswadkar/developers/androidify/data/MandiPrice.kt`
- `app/src/main/java/com/maswadkar/developers/androidify/data/MandiPreferences.kt`
- `app/src/main/java/com/maswadkar/developers/androidify/data/MandiPriceRepository.kt`
- `app/src/main/java/com/maswadkar/developers/androidify/ui/screens/MandiPricesScreen.kt`
- `app/src/main/java/com/maswadkar/developers/androidify/ui/screens/MandiPricesViewModel.kt`
- `app/src/main/java/com/maswadkar/developers/androidify/ui/screens/MandiPreferencesScreen.kt`
- `app/src/main/java/com/maswadkar/developers/androidify/ui/screens/MandiPreferencesViewModel.kt`
- `app/src/main/res/drawable/ic_price.xml`
- `app/src/main/res/drawable/ic_settings.xml`
- `functions/package.json`
- `functions/index.js`
- `functions/README.md`
- `firestore.indexes.json`

**Modified Files:**
- `app/src/main/res/values/strings.xml` - Added all Mandi-related strings
- `app/src/main/java/com/maswadkar/developers/androidify/ui/components/NavigationDrawer.kt` - Added MandiPrices and MandiSettings drawer items
- `app/src/main/java/com/maswadkar/developers/androidify/ui/navigation/Screen.kt` - Added MandiPrices and MandiSettings routes
- `app/src/main/java/com/maswadkar/developers/androidify/ui/navigation/AppNavigation.kt` - Added navigation for both screens
- `app/src/main/java/com/maswadkar/developers/androidify/ui/screens/ChatScreen.kt` - Added click handlers for drawer items

## User Flow

### First-time User:
1. Opens "Mandi Prices" from drawer
2. Sees full mode with State → District → Commodity dropdowns
3. Selects state, district, optionally commodity
4. Taps Search → sees prices
5. Preferences auto-saved → next visit shows compact mode

### Returning User:
1. Opens "Mandi Prices" from drawer
2. Sees compact mode with saved location chip
3. Selects commodity (optional), taps Search
4. Can tap "Change" to modify location

### Changing Preferences:
- Via "Mandi Preferences" in drawer, or
- Via "Change" button in compact mode

## Deployment Steps

### 1. Deploy Cloud Function

```bash
cd functions
npm install
firebase deploy --only functions
```

### 2. Deploy Firestore Indexes

```bash
firebase deploy --only firestore:indexes
```

### 3. Trigger Initial Data Sync

Call the manual sync endpoint:
```bash
curl https://asia-south1-<project-id>.cloudfunctions.net/syncMandiPricesManual
```

### 4. Build and Run Android App

The app is ready to build and run.

## Future Enhancements

1. **Location-based filtering:** 
   - Add `latitude`, `longitude` fields to market data
   - Query 3 nearest mandis using device location

2. **Price alerts:**
   - Allow users to set price alerts for specific commodities
   - Send push notifications when prices cross threshold

3. **Price trends:**
   - Store historical price data
   - Display price charts/graphs


