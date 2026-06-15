# CashBook Business - Collaborative Ledger Management Android App

**CashBook Business** is a collaborative, multi-level business ledger and bookkeeping application for Android. Built with a modern Kotlin codebase, it utilizes a Firebase backend for real-time synchronization with offline capabilities, and integrates with the Google Drive REST API for receipt storage. It is designed to handle multiple businesses, notebooks, categories, parties, and role-based membership access.

---

## 🚀 Key Features

*   **Multi-Level Organizational Hierarchy**:
    *   **Businesses**: Create and manage multiple business profiles.
    *   **Partners**: Invite business-level partners via phone numbers to share access across all notebooks under the business.
    *   **Notebooks (Cashbooks)**: Dedicated sub-ledgers (e.g., "Daily Sales", "Office Expenses") created under a business profile.
*   **Transaction Tracking**:
    *   Record **Cash In** (`in`) and **Cash Out (`out`)** entries.
    *   Log amounts, remarks, custom transaction dates, and times.
    *   Categorize transactions (e.g., "General", "Inventory", "Travel").
    *   Link transactions to specific **Parties** (customers/suppliers).
    *   Attach **receipt images/documents** directly to ledger entries.
*   **Google Drive Integration**:
    *   Receipts are uploaded to a dedicated folder (`CashBook_Assets`) in the user's personal Google Drive.
    *   Permissions are automatically configured to allow anyone with the link to view them, enabling seamless image loading for partners.
    *   Supports high-quality thumbnail extraction for image display via Glide.
    *   Deletes corresponding files from Google Drive when a transaction is deleted.
*   **Role-Based Access Control (RBAC)**:
    *   **Business Owner**: Complete control over the business profile, notebooks, partners, and settings.
    *   **Business Partner**: Full read/write access to all notebooks. Cannot delete the business profile.
    *   **Notebook Admin**: Manage notebook details and invite/remove members (maximum of 3 admins per notebook).
    *   **Notebook Writer**: Add, edit, or delete transactions in the specific notebook.
    *   **Notebook Reader**: View-only access to transactions.
*   **Advanced Reports & Filtering**:
    *   Filter transactions dynamically by Date (presets: Today, Yesterday, This Month, Last Month, This Year, or Custom range), Category, Party, Type, or Member.
    *   Generates A4-size PDF statements locally with a tabular format, showing descriptions, types, amounts, and a running balance.
*   **Offline Support**:
    *   Firebase Database offline persistence is enabled, allowing users to view, add, and modify transactions without an internet connection. Data is synced automatically once they go online.

---

## 🛠️ Technology Stack & Libraries

*   **Language**: Kotlin
*   **UI Framework**: ViewBinding with Android XML Layouts and Material Design 3 Components.
*   **Database & Auth**: Firebase Authentication & Realtime Database.
*   **Cloud Storage**: Google Drive REST API Client & Google Play Services Auth (OAuth2).
*   **Image Loading**: Glide (for loading cached receipt thumbnails).
*   **Responsive Layouts**: `sdp-android` (Scalable DP) and `ssp-android` (Scalable SP) libraries to ensure design responsiveness across various screen sizes.
*   **Concurrency**: Kotlin Coroutines.
*   **Build Automation**: Gradle Kotlin DSL.

---

## 📂 Project Architecture

The app is organized into feature-based packages inside `com.cashbk.app`:

```
com.cashbk.app
│
├── adapter               # RecyclerView adapters for lists (Transactions, Notebooks, Members, Filters, etc.)
├── data                  # Data layer configuration and database model definitions
├── dataclass             # DTO (Data Transfer Object) entities (Business, Notebook, Transaction, Category, Party, Member, etc.)
├── di                    # Manual dependency injection (AppModule provides singleton instances)
├── fragment              # Bottom sheets and dialog fragments (e.g., AddParty, EditProfile, AddBusiness)
├── ui                    # Presentation layer categorized by feature:
│   ├── auth              # LoginFragment, RegisterFragment, ForgotPasswordActivity, AuthActivity
│   ├── business          # ManagePartnersActivity, BusinessDetailActivity, CashbooksFragment, ProfileFragment, SettingsFragment
│   ├── members           # MembersActivity (Role-based member invitation & management)
│   ├── notebook          # NotebookActivity, NotebookHomeFragment, ManagePartiesFragment, ManageCategoriesFragment, SettingsFragment
│   └── transaction       # AddTransactionFragment (Create & Edit transactions)
└── utils                 # Helper classes:
    ├── GoogleDriveManager.kt  # Authentication and file management for Google Drive
    ├── PdfGenerator.kt        # Statement generation using Android's PdfDocument
    └── CustomOptionsMenu.kt   # Context-aware custom popup menus
```

---

## 🗄️ Database Schema (Firebase Realtime Database)

The database hierarchy is structured as follows:

### 1. Users (`/users/{uid}`)
```json
{
  "email": "user@example.com",
  "phone": "+919876543210"
}
```

### 2. Businesses (`/businesses/{businessId}`)
```json
{
  "id": "-Nxxxxxxxxxxxxxxxxx",
  "name": "Acme Corp",
  "ownerId": "owner_uid_here",
  "createdAt": 1718362350000,
  "isPartner": false
}
```

### 3. Business Members / Partners (`/business_members/{businessId}/{uid}`)
```json
{
  "uid": "partner_uid_here",
  "role": "partner",
  "addedAt": 1718362350000
}
```

### 4. Notebooks (`/notebooks/{notebookId}`)
```json
{
  "id": "-Nyyyyyyyyyyyyyyyyy",
  "name": "Office Expenses",
  "businessId": "-Nxxxxxxxxxxxxxxxxx",
  "createdBy": "owner_uid_here",
  "createdAt": 1718362350000
}
```

### 5. Notebook Members (`/members/{notebookId}/{uid}`)
```json
{
  "uid": "member_uid_here",
  "role": "writer", // "admin", "writer", "reader"
  "addedAt": 1718362350000
}
```

### 6. Transactions (`/transactions/{notebookId}/{transactionId}`)
```json
{
  "id": "-Nzzzzzzzzzzzzzzzzz",
  "amount": 2500.0,
  "type": "in", // "in" or "out"
  "remark": "Client payment",
  "createdBy": "member_uid_here",
  "date": "2026-06-14",
  "time": "02:15 PM",
  "createdAt": 1718362350000,
  "categoryId": "general",
  "categoryName": "General",
  "partyId": "-Nppppppppppppppppp",
  "partyName": "John Doe",
  "receiptUrl": "https://drive.google.com/file/d/...",
  "receiptName": "receipt_1718362350000.jpg"
}
```

---

## 🛠️ Getting Started & Setup

### 1. Prerequisites
*   Android Studio Jellyfish or newer.
*   Java Development Kit (JDK) 17+.
*   A Firebase project.
*   Google Developer Console project with Drive API enabled.

### 2. Firebase Configuration
1.  Register a new Android App in your Firebase console with the package name `com.cashbk.app`.
2.  Download the `google-services.json` config file and place it in the `app/` directory.
3.  Enable **Email/Password** and/or **Phone** authentication in the Firebase Auth tab.
4.  Create a **Realtime Database** instance and deploy the database rules.
5.  Update the database reference URL in [AppModule.kt](file:///c:/Users/Lenovo/AndroidStudioProjects/CashBookBusiness/app/src/main/java/com/cashbk/app/di/AppModule.kt#L8):
    ```kotlin
    val db = FirebaseDatabase.getInstance("YOUR_FIREBASE_DATABASE_URL")
    ```

### 3. Google API Configuration (for Drive backup)
1.  Go to the Google Cloud Console.
2.  Enable the **Google Drive API** for the Google project associated with your Firebase app.
3.  Configure the OAuth Consent Screen and add the scope `https://www.googleapis.com/auth/drive.file`.
4.  Add SHA-1 fingerprints from your debug and release keystores to your Firebase project settings to allow Google Sign-In to function properly.

---

## 📦 Building and Signing

The project features a custom automated release keystore generation task in Gradle.

If you compile the project for `release`, Gradle will automatically run the `generateReleaseKeystore` task defined in [build.gradle.kts](file:///c:/Users/Lenovo/AndroidStudioProjects/CashBookBusiness/app/build.gradle.kts#L115-L148) to construct a self-signed release signing key inside the root project directory.

To build the debug or release APKs:
```powershell
# Build Debug APK
./gradlew assembleDebug

# Build Release APK (Generates release.keystore if missing)
./gradlew assembleRelease
```
