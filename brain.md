# CashBook Business — Project Brain

> Purpose: a fast, accurate orientation guide for developers and AI tools working on this repository.
> Last project snapshot: 2026-11-25

## 1. What this project is

**CashBook Business** is a native Android bookkeeping and collaborative ledger app written in Kotlin. It manages business-level organizations, cashbooks (notebooks), income/expense transactions, parties, categories, members, events, receipts, reports, themes, and app-lock security.

Core model:

```text
User
  └── Business
        ├── Notebook / Cashbook
        │     ├── Transactions (cash in / cash out)
        │     ├── Parties
        │     ├── Categories
        │     └── Notebook members
        └── Business partners
```

The app has two main backend integrations:

- **Laravel REST API** for authentication and OTP workflows.
- **Firebase Authentication + Firebase Realtime Database** for session identity and live data.
- **Google Drive** for receipt-file storage.
- **Room** plus a sync queue for local persistence and synchronization support.

## 2. Quick start

### Requirements

- Android Studio with JDK 17 or newer
- Android SDK 34
- A Firebase project
- A Laravel OTP/auth backend compatible with `LaravelApiService`
- Google sign-in/Drive API configuration for receipt uploads
- Internet access for authentication and cloud sync

### Main build commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Install debug build
./gradlew installDebug
```

Release builds currently trigger a custom keystore-generation task if `release.keystore` is missing.

### Configuration files

- `app/google-services.json` — Firebase Android configuration
- `google-services.json` — workspace copy
- `local.properties` — local Android SDK path
- `gradle/libs.versions.toml` — selected dependency version catalog entries
- `app/build.gradle.kts` — actual module dependencies, SDK levels, signing, and build settings
- `app/src/main/AndroidManifest.xml` — activities, application class, permissions, and FileProvider

Do not commit secrets or signing credentials. Review signing configuration before release distribution.

## 3. Repository structure

```text
CashBookBusiness/
├── app/src/main/java/com/cashbk/app/
│   ├── CashBookApp.kt                 # Application startup and auth-driven sync
│   ├── splash/                        # Splash routing
│   ├── auth/                          # Auth UI, OTP, Firebase custom-token login, PIN/biometric
│   ├── business/                      # Business selection/detail/settings/cashbooks
│   ├── notebook/                      # Notebook shell, ledger, settings
│   │   └── filter/                    # Notebook filter criteria & modal bottom sheet
│   ├── transaction/                   # Add/edit transaction UI and adapters
│   ├── party/                         # Party management and business partners
│   ├── partner/                       # Partner/user models and adapters
│   ├── category/                      # Category management
│   ├── members/                       # Notebook/business member management
│   ├── events/                        # Calendar/event UI
│   ├── profile/                       # User profile, Drive, theme/PIN settings
│   ├── data/
│   │   ├── FirebaseService.kt         # Small direct-Firebase utility
│   │   ├── repository/                # AppRepository with Room-first operations
│   │   ├── di/AppContainer.kt         # Manual singleton container
│   │   ├── local/room/                # Room entities, DAOs, database
│   │   └── sync/                      # Sync manager, queue worker, network monitor
│   ├── network/                       # Retrofit/Laravel API client and response models
│   └── utils/                         # Theme, PDF, Drive, menu, dialog, toast helpers
├── app/src/main/res/
│   ├── layout/                        # XML Activity/Fragment/view layouts
│   ├── drawable/ drawable-night/      # Icons, cards, backgrounds
│   ├── menu/                          # Bottom and options menus
│   ├── values/                        # Themes, colors, strings, styles, dimensions
│   └── xml/                           # Backup, provider, and network configuration
├── app/src/androidTest/java/           # Android instrumentation tests
├── app/src/test/java/                  # JVM unit tests
├── README.md                           # Original product/setup overview
└── brain.md                            # This developer-maintenance guide
```

There is also legacy package code under `com.example.cashbookbusiness.MainActivity.kt`; the configured application flow uses `com.cashbk.app.*` and this legacy package should not be treated as the main app entry point.

## 4. Application navigation and user experience

### Main entry flow

```text
SplashActivity
  ├── no Firebase user → AuthActivity
  ├── Firebase user + app lock enabled → PinLockActivity
  └── Firebase user + app lock disabled → BusinessDetailActivity
```

Authentication screens route from `AuthActivity` through login/register and OTP verification fragments.

### Business-level navigation

`BusinessDetailActivity` is the primary business shell. It contains:

- Cashbooks (`CashbooksFragment`): includes real-time notebook list, live search, and multi-criteria filtering bottom sheet (`NotebookFilterBottomSheet` filtering by sort order, net balance status, and date range with active badge indicator).
- Profile
- Events
- Business settings

Its business selector loads owned businesses and may include partner businesses. The selected business ID is passed to child fragments.

### Notebook-level navigation

`NotebookActivity` is opened with a `notebookId`. Its bottom navigation contains:

- Transactions / ledger (`NotebookHomeFragment`): features a floating glassmorphic top bar with circular tactile cards for back navigation, PDF export, and options menu, alongside the notebook avatar badge, net balance overview, transaction list, and quick cash in/out actions.
- Parties
- Categories
- Notebook settings

The fragment receives the notebook ID and a resolved role string.

### Main data entities

| Entity | Main purpose |
|---|---|
| User | Account profile: UID, name, email/phone details |
| Business | Top-level organization with owner UID |
| Notebook | Ledger/book belonging to a business |
| Transaction | Cash-in or cash-out ledger entry |
| Party | Customer, supplier, employee, vendor, or similar party |
| Category | Income/expense classification |
| Member | Notebook role membership |
| Partner | Business-wide partner membership |
| Event | Calendar or reminder entry associated with the app/business |

## 5. Authentication and identity

Authentication is hybrid:

```text
Laravel OTP/API
  → Laravel access token stored in SharedPreferences (AppPrefs/access_token)
  → Firebase custom token
  → Firebase Authentication user
  → UID used for Firebase data and sync
```

Implemented paths:

- `AuthManager.sendEmailOtp()` / `sendPhoneOtp()`
- `AuthManager.verifyAndAuthenticate()` / `verifyAndAuthenticatePhone()`
- Laravel endpoints include send OTP, verify OTP, register, login, and profile
- `PinLockActivity` provides PIN and biometric app-lock behavior
- `CashBookApp` starts/stops realtime sync depending on Firebase auth state

The base Laravel URL is intentionally obfuscated in `RetrofitClient.kt`. The actual request endpoints are defined in `LaravelApiService.kt`.

### Authentication UI

- Login and registration content is defined in `fragment_login.xml` and `fragment_register.xml`.
- The bottom auth navigation is in `activity_auth.xml`, controlled by `AuthActivity.kt`, and uses `bottom_auth_menu.xml`.
- Auth navigation is a compact floating pill-style bottom bar with a 64dp-scaled height, theme-aware margins, 20dp icons, and labeled active/inactive states.
- Light and dark themes use dedicated auth typography tokens: `auth_heading`, `auth_body`, `auth_hint`, `auth_field_text`, and `auth_nav_inactive`.
- Login/register headings, subtitles, fields, hints, Google actions, support text, and legal text now use theme-aware contrast colors rather than fixed dark-theme-oriented colors.

## 6. Firebase data model

The app uses Firebase Realtime Database paths. Important known roots:

```text
/users/{uid}
/businesses/{businessId}
/business_members/{businessId}/{uid}
/notebooks/{notebookId}
/members/{notebookId}/{uid}
/transactions/{notebookId}/{transactionId}
/categories/{notebookId}/{categoryId}
/parties/{notebookId}/{partyId}
/events/{businessId}/{eventId}             # used by EventsFragment
```

Representative user:

```json
{
  "name": "Example User",
  "phone": "+919876543210",
  "email": "user@example.com"
}
```

Representative business:

```json
{
  "name": "Acme Corp",
  "ownerId": "firebase_uid",
  "createdAt": 1718362350000,
  "isPartner": false,
  "updatedAt": 1718362350000
}
```

Representative notebook:

```json
{
  "name": "Daily Sales",
  "businessId": "business_id",
  "createdBy": "firebase_uid",
  "createdAt": 1718362350000,
  "updatedAt": 1718362350000
}
```

Representative transaction:

```json
{
  "amount": 2500.0,
  "type": "in",
  "remark": "Client payment",
  "createdBy": "firebase_uid",
  "date": "2026-06-14",
  "time": "02:15 PM",
  "createdAt": 1718362350000,
  "updatedAt": 1718362350000,
  "categoryId": "general",
  "categoryName": "General",
  "partyId": "party_id",
  "partyName": "Party Name",
  "receiptUrl": "https://drive.google.com/file/d/FILE_ID/view",
  "receiptName": "receipt.jpg"
}
```

`ServerValue.TIMESTAMP` is used for some added/created timestamps. The exact field names should be verified against the code that writes a record before changing data contracts.

## 7. Roles and permissions

### Business roles

- **Owner** — manages owned business, notebooks, partners, and settings.
- **Partner** — business-level access; partner membership is stored under `business_members/{businessId}/{uid}`.

Business member UI permits partner role and provides full access without owner delete privileges, subject to actual activity checks.

### Notebook roles

- **Owner** — resolved when the notebook's business owner is the current Firebase UID.
- **Admin** — manages notebook members and notebook data, up to a maximum of three admins enforced in the member dialog.
- **Writer** — can add/edit/delete ledger entries.
- **Reader** — view-only transaction access.

Role resolution is partly direct in activities/fragments and partly based on membership data. Before adding a permission-sensitive feature, search for existing role checks and enforce both UI and backend/database security rules.

## 8. Data flow

### Direct Firebase UI flow (current primary pattern)

Many screens read and write Firebase directly:

```text
Activity / Fragment
  → FirebaseDatabase / DatabaseReference
  → DataSnapshot / ValueEventListener
  → domain data class
  → RecyclerView adapter / XML binding
```

Examples:

- `NotebookHomeFragment` listens to transactions, parties, categories, and users.
- `CashbooksFragment` reads notebooks from Firebase.
- `MembersActivity` reads member IDs and resolves profiles under `/users`.
- `EventsFragment` reads business/notebook/calendar-related data.
- `AddTransactionFragment` writes transaction records directly to `/transactions/{notebookId}`.

### Room and sync flow (partial/offline infrastructure)

```text
UI → AppRepository
   → Room DAO
   → local SQLite entity
   → SyncQueueEntity
   → SyncManager
   → Firebase Realtime Database

Firebase listener
   → timestamp/version conflict check
   → Room entity insertion/update
   → UI Flow
```

`AppContainer` constructs shared `AppDatabase`, `NetworkMonitor`, `SyncManager`, and `AppRepository` singletons.

Key details:

- `AppDatabase` database name: `cashbook_business_db`
- Room schema version: `1`
- Missing/changed schema currently uses destructive fallback migration.
- `SyncQueueEntity` statuses: `PENDING`, `SYNCING`, `FAILED` (and retry behavior in `SyncManager`).
- Sync path mapping covers business, notebook, transaction, category, party, and user.
- Realtime sync listens for authenticated-user data and nested notebook content.
- `SyncWorker` exists for pending queue execution but should be confirmed in WorkManager scheduling before relying on it as a complete offline guarantee.

### Important architecture caveat

The application is currently mixed: many UI features use Firebase directly, while the repository/Room layer is not yet the source of truth for every feature. Do not assume that all writes pass through `AppRepository` or `SyncManager`.

## 9. Google Drive receipts

Google Drive is used as file storage for transaction receipts.

- `GoogleDriveManager` creates/finds the `CashBook_Assets` folder.
- Files are uploaded with the `drive.file` OAuth scope.
- Public-link metadata is configured so receipt links can be consumed by the app and collaborators.
- Glide loads receipt thumbnails in applicable UI.
- Deleting a transaction can also attempt deletion of the linked Drive file.
- `FileProvider` is declared for app-local file sharing.

Google Sign-In, OAuth consent, Android SHA-1/SHA-256 fingerprints, and Drive scopes must match the Firebase/Google Cloud project.

## 10. PDF reports

`utils/PdfGenerator.kt` generates A4 PDF statements locally using Android `PdfDocument`.

Typical inputs:

- Notebook name
- Displayed transaction list
- Current net balance

Statements include ledger descriptions, cash-in/cash-out values, dates, and running balance depending on the implementation selected by the filter UI.

## 11. UI and resources

- XML layouts use Android ViewBinding.
- Material 3 Components provide bottom sheets, dialogs, filter chips, cards, spinners, and bottom navigation.
- Glassmorphism design pattern (`Widget.App.GlassCard`, `@drawable/bg_glass_card`, `@drawable/bg_bottom_sheet_glass`) is used across top bars, summary cards, and bottom sheets for a unified premium aesthetic.
- `sdp` and `ssp` provide scalable dimensions across phone sizes.
- Glide provides image loading and profile/receipt avatar rendering.
- Light/dark theme preference is stored in `theme_prefs/app_theme` with smooth circular reveal animations (`ThemeTransitionHelper`) eliminating screen blink, while avoiding artificial progress dialogs during intra-app navigation.
- App-lock preference is stored in `app_lock_prefs/app_lock_enabled`.
- Custom utilities include toast, alert, popup-menu, status/navigation bars, and animations.
- Dynamic color state lists (`sel_chip_text.xml`, `sel_chip_background.xml`, `sel_chip_stroke.xml`) ensure optimal contrast and readability across Light and Dark themes for filter chips and text fields.

Main layout areas live under `app/src/main/res/layout`. Keep resources localized in `values/` and use drawable-night variants for theme-specific assets where appropriate.

## 12. Build and dependencies

### Android/Kotlin

- Application ID / namespace: `com.cashbk.app`
- `compileSdk`: 34
- `targetSdk`: 34
- `minSdk`: 24
- Java/Kotlin JVM target: 1.8
- ViewBinding: enabled

### Important libraries

- Firebase BOM 32.7.0: Auth, Database, Analytics
- Retrofit 2.9 + OkHttp logging: Laravel API
- Google Play Services Auth + Google Drive API
- Room 2.6.1
- WorkManager 2.9.0
- Glide 4.16.0
- Kotlin Coroutines 1.7.3
- SDP/SSP responsive sizing
- AndroidX Biometric
- Gson

### Build behavior

- Debug builds have shrink and minify disabled.
- Release builds enable minification and resource shrinking.
- Release signing reads `keystore.properties` when it exists.
- A custom release keystore task is configured at the root/app Gradle level.
- ProGuard rules are in `app/proguard-rules.pro`.

## 13. Known implementation hotspots

Before making structural changes, inspect these areas:

- `auth/AuthManager.kt` — OTP/login/Firebase custom-token contract
- `data/sync/manager/SyncManager.kt` — Firebase-to-Room mapping and conflict resolution
- `data/repository/AppRepository.kt` — Room-first and sync queue behavior
- `splash/SplashActivity.kt` — startup routing and app lock
- `business/BusinessDetailActivity.kt` — business shell and selected-business state
- `notebook/NotebookActivity.kt` — notebook shell and role resolution
- `notebook/NotebookHomeFragment.kt` — ledger reads, filters, reports, and totals
- `transaction/AddTransactionFragment.kt` — transaction validation and Drive upload
- `members/MembersActivity.kt` — membership and role management
- `profile/ProfileFragment.kt` — profile, app lock, theme, and migration/Drive-related behavior
- `events/EventsFragment.kt` — calendar/event feature
- `utils/PdfGenerator.kt` — statement generation
- `network/RetrofitClient.kt` — auth API base URL and HTTP configuration

## 14. Maintenance rules

When modifying this project:

1. **Update `brain.md` after every meaningful project update.**
2. Record changed files, behavior, data paths, dependencies, and migration impact.
3. If the database contract changes, update the schema section and any Room entities/DAOs.
4. If a new Activity/Fragment is added, update navigation and structure sections.
5. If a role or permission changes, update the Roles and Permissions section.
6. If authentication or API endpoints change, update the Authentication and Network sections.
7. If a dependency is added or removed, update the dependency section.
8. Keep this file concise enough to understand quickly, but include enough detail for safe debugging.
9. Add tests for data-contract, sync, authentication, and permission changes.
10. Do not expose production secrets, Firebase credentials, API keys, tokens, or signing passwords in this file.

## 15. Screen preview and navigation index

Use this table as the UI map when updating layouts or implementing navigation. Screen names correspond to Kotlin classes and the preview layout identifies the XML source.

| Preview / Screen | Layout | Main properties and components | Entry route / Navigation |
|---|---|---|---|
| Splash | `activity_splash.xml` | App branding, loading/progress UI, startup routing screen | Launcher → SplashActivity → AuthActivity, PinLockActivity, or BusinessDetailActivity |
| Authentication shell | `activity_auth.xml` | Floating pill bottom bar, theme-aware container, fragment host (`nav_host_fragment`) | AuthActivity; bottom navigation: Login (`nav_login`) / Register (`nav_register`) |
| Login | `fragment_login.xml` | Email/phone login fields, password field, Google sign-in, forgot-password link, register link | AuthActivity → LoginFragment → BusinessDetailActivity after success; Forgot password → ForgotPasswordActivity |
| Register | `fragment_register.xml` | Name, email/phone, password fields, registration action, sign-in link | AuthActivity → RegisterFragment → OTP verification flow |
| OTP Verification | `fragment_otp_verification.xml` | OTP input fields, verify/resend actions, back navigation | Register/login OTP route → OTP verification → BusinessDetailActivity |
| Forgot Password | `activity_forgot_password.xml` | Email/identifier input, send action, back control | LoginFragment → ForgotPasswordActivity → return to Login |
| PIN Lock | `activity_pin_lock.xml` | Numeric keypad, delete/biometric controls, switch-user action | Splash/Auth route after app-lock is enabled; unlock → BusinessDetailActivity |
| Business shell | `activity_business_detail.xml` | Top business selector/theme toggle, fragment container, floating bottom navigation | Splash/Auth/Pin → BusinessDetailActivity. Bottom tabs: Books, Events, Profile, Settings |
| Books / Cashbooks | `fragment_cashbooks.xml` | Search, notebook list, notebook filter, add-notebook action, net-book cards | Business shell `Books` tab → NotebookActivity by selecting a notebook |
| Add Business | `fragment_add_business.xml` | Business name and save/cancel controls | Business selector bottom sheet → Add Business dialog/fragment |
| Business selector | `bottom_fragment_select_business.xml` | Owned/partner business list, add-new-business action | Business top-bar selector → bottom sheet |
| Events | `fragment_events.xml` | Calendar, event date states, event list/add-event actions | Business shell `Events` tab |
| Profile | `fragment_profile.xml` | Avatar, edit profile, theme selector, app-lock switch, Google Drive controls, reset password, sign out | Business shell `Profile` tab |
| Business Settings | `fragment_settings.xml` | Manage members, leave/delete business, logout | Business shell `Settings` tab |
| Notebook shell | `activity_notebook.xml` | Transparent bottom navigation with labeled tabs and notebook fragment host; 65dp-scaled height | Cashbooks/Notebook selection → NotebookActivity with `notebookId` |
| Notebook Transactions | `fragment_notebook_home.xml` | Glass top bar, back/PDF/menu controls, notebook avatar, balance summary, filtered transaction list, Cash In/Cash Out actions | Notebook bottom nav `Transactions`; PDF/report, add transaction, member options |
| Add/Edit Transaction | `fragment_add_transaction.xml` | Cash in/out type switch, amount, party/category, date/time, receipt attachment, save actions | Notebook transaction screen → bottom sheet/fragment; cancel pops back stack |
| Parties | `fragment_manage_parties.xml` | Party search, party list, add-party FAB/placeholders, delete/edit actions | Notebook bottom nav `Parties` → Add Party screen/bottom sheet |
| Add Party | `fragment_add_party.xml` or `add_party_bottom_sheet.xml` | Party name/type/details, save/cancel, contacts action | Parties screen/add button or transaction add-party control |
| Categories | `fragment_manage_categories.xml` | Income/expense category list, add category, search/actions | Notebook bottom nav `Categories` |
| Add/Edit Category | `fragment_add_category.xml` | Category name/icon/type, save/cancel | Categories screen → Add/Edit Category |
| Notebook Settings | `fragment_notebook_setting.xml` | Notebook name, manage members/categories, duplicate/delete notebook | Notebook bottom nav `Settings` |
| Members | `activity_members.xml` | Member list, role assignment/add dialog, remove/delete controls | Business/Notebook settings or notebook menu → MembersActivity |
| Manage Business Partners | `activity_manage_partners.xml` | Partner list, add/remove partner dialogs | Business settings/manage partners flow |
| Notebook filter | `bottom_sheet_notebook_filter.xml` | Sort, balance, period filter chips, reset and apply actions | Cashbooks search/filter control → modal bottom sheet |
| Notebook options | `layout_options_menu.xml` | Rename, report, members, settings, delete actions | Notebook top-menu button or notebook card options |
| PIN setup | `dialog_pin_setup.xml` | Numeric PIN setup/change/delete controls | Profile app-lock section → bottom sheet/dialog |
| Edit profile | `dialog_edit_profile.xml` | Name/email/profile details and update action | Profile → Edit Profile |
| Custom alert | `dialog_custom_alert.xml` | Positive/negative/neutral themed actions | Sign-out, delete, leave, reset, and destructive confirmations |
| Custom toast | `layout_custom_toast.xml` | Theme-aware status message toast | Used globally through `CustomToast.show()` |

### Navigation property reference

- **Business bottom navigation:** `bottomNavigation` / `bottom_nav_menu.xml`; selected tabs load `CashbooksFragment`, `EventsFragment`, `ProfileFragment`, or `SettingsFragment`.
- **Notebook bottom navigation:** `bottom_nav` / `notebook_bottom_nav.xml`; selected tabs load `NotebookHomeFragment`, `ManagePartiesFragment`, `ManageCategoriesFragment`, or `SettingsFragment`.
- **Auth bottom navigation:** `bottom_auth_navigation` / `bottom_auth_menu.xml`; selected tabs load `LoginFragment` or `RegisterFragment`.
- **Notebook shell navigation properties:** `labelVisibilityMode="labeled"`, transparent background/tint, no elevation, 20dp icon size, 58dp item minimum height, and theme-aware `nav_item_color`.
- **Screen preview source:** prefer XML layout files for static structure, Kotlin classes for behavior/navigation, and resources under `drawable/`, `drawable-night/`, `menu/`, and `values*/` for visual styling.

## 16. Update log

### 2026-11-25 — Screen preview index and notebook navigation polish

- Added a full screen preview and navigation table for Activities, Fragments, bottom sheets, dialogs, and their layout files.
- Documented business, notebook, and authentication bottom-navigation property names and destination fragments.
- Updated `activity_notebook.xml` to use a transparent navigation background/tint and no elevation, preventing corner artifacts in Dark theme.
- Added explicit notebook bottom-navigation label and item sizing properties for consistent labeled text visibility.
- Added report icon maintenance note: `ic_menu_report.xml` is the shared PDF/report drawable for notebook and options-menu actions.

### 2026-11-21 — Notebook filtering, glassmorphic top bar redesign & theme polish

- **Notebook Multi-Criteria Filter Feature**:
  - Implemented `NotebookFilterCriteria` model supporting Sort By (Name A-Z, Most Recent, Highest Balance, Lowest Balance), Net Balance Status (All, Net Positive, Net Negative), and Time Period (All time, Today, This Month, This Year).
  - Built `NotebookFilterBottomSheet` using Material 3 Modal Bottom Sheet with interactive filter chips (`Widget.App.FilterChip`, `sel_chip_text.xml`, `sel_chip_background.xml`, `sel_chip_stroke.xml`).
  - Added real-time filtering, active filter badge indicator (`bg_indicator_active.xml`), and expandable search container to `CashbooksFragment`.
  - Resolved filter chip text contrast in light and dark themes.
- **Notebook Home Top Bar Redesign**:
  - Modernized `fragment_notebook_home.xml`'s top bar into a floating glass card with `bg_glass_card`, margins, and elevation matching `activity_business_detail.xml`.
  - Encased back button (`btn_back`), PDF report button (`btn_pdf`), and menu button (`btn_menu_notebook`) in sleek circular cards with ripple feedback and glass borders.
  - Added a dedicated notebook avatar badge and refined typography hierarchy for `tvTitle` and `tvSubtitle` using theme-aware colors (`text_secondary`, `on_surface_variant`).
- **Notebook Bottom Navigation Redesign**:
  - Restyled `activity_notebook.xml`'s navigation into a floating pill container (`bg_bottom_nav_pill`) with `@dimen/_14sdp` horizontal and `@dimen/_10sdp` bottom margins and `@dimen/_6sdp` elevation, matching the visual identity in `activity_business_detail.xml`.
  - Replaced the opaque solid gradient `bg_bottom_nav_item_selected_20` with the translucent pill `bg_bottom_nav_item_selected`, restoring contrast for the active teal text and icon (`nav_item_color`).
  - Tuned item padding and heights (`@dimen/_56sdp`) for optimal touch targets and spacing in both Light (crisp white + glass border) and Dark (dark surface + glowing teal stroke) themes.
- **Theme Polish & Transition Fixes**:
  - Eliminated screen blink during theme changes with circular reveal transitions while preventing unwanted progress bars from triggering during routine fragment navigation.
  - Fixed low-contrast text and background issues across Profile views and popup menus (`bg_popup_container.xml`, `bg_segmented_container.xml`).

### 2026-11-18 — Authentication UI improved

- Added theme-aware authentication text and hint color tokens for light and dark modes.
- Improved login/register heading, subtitle, field, Google button, support-link, and legal-text readability.
- Reduced and restyled the auth bottom navigation into a compact floating pill with clearer selected/unselected contrast.
- Updated auth bottom navigation icon and label sizing for more consistent appearance.
- Debug resource validation and `assembleDebug` build completed.

### 2026-11-18 — Initial project brain created

- Documented native Android project purpose and hierarchy.
- Documented auth, Firebase, Room, sync, and Google Drive architecture.
- Documented main Activities, Fragments, permissions, navigation, and data paths.
- Added maintenance instructions for future updates.
- Captured the current mixed Firebase-direct and Room-repository architecture.

### 2026-11-18 — Improved login/register UI and theme-aware auth tokens

- Documented the new login and registration XML layouts and their integration in `AuthActivity`.
- Described the compact floating pill-style bottom bar for auth navigation.
- Explained the use of theme-aware typography tokens and contrast colors in light and dark themes for auth screens.
