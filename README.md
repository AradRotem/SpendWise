# SpendWise

SpendWise is a personal finance management Android application that helps users track their income and expenses, manage their budget, and better understand their financial habits.

The application allows users to record transactions, organize them by category, monitor their balance, and review their financial activity in a clear and simple interface.

## Features

### Transactions

* Add, edit, and delete income and expense transactions
* View all transactions in a structured, filterable list
* Categorize transactions, including user-defined custom categories
* Track the current balance
* Attach, store, and view a receipt image for a transaction
* All amounts are in Israeli Shekels (₪) — SpendWise v1 is single-currency (ILS only)

### Recurring Transactions

* Recurring monthly payments (e.g. subscriptions)
* Installment plans (fixed number of payments for a purchase)
* Recurring monthly income (salary)
* Pause, stop, and edit existing recurring plans, including "edit this and future occurrences"

### Budgets

* Monthly and category-level budgets
* Budget vs. actual tracking
* Budget threshold alerts/notifications

### Reports & Analytics

* Monthly reports
* Category spending analytics and drill-down
* Monthly income/expense trends
* Budget vs. actual analytics
* Additional visual analytics (e.g. spending by weekday, top payees, cumulative spending, month-end projection)

### Account & Authentication

* Firebase Authentication (sign up, log in, log out)
* Password reset ("forgot password") flow
* Account management screen

### Shared Expense Groups

* Create and manage shared expense groups and members
* Group owner/member roles and permissions
* Shared group expenses with equal or custom splits
* Group balance and settlement calculations
* Group invitations for other SpendWise users
* Multi-user synchronization of shared groups via Cloud Firestore

### Cloud Sync

* Cloud synchronization of transactions, budgets, and recurring plans via Cloud Firestore
* Receipt image storage via Firebase Storage

### Notifications

* Local notifications for budget threshold alerts and recurring payment reminders
* Notifications for shared-group activity and invitations
* In-app notification settings (enable/disable by category)

### General

* Local-first data storage using Room Database, kept in sync with the cloud
* Automatic UI updates using Kotlin Flow and StateFlow
* Modern user interface built with Jetpack Compose and Material 3
* Form validation across input screens
* Clean separation between the UI, ViewModel, domain, and data layers

## Planned Features

Most previously planned near-term features (budgets, analytics, notifications, cloud sync, transaction editing/filtering) have now been implemented. Remaining polish items include:

* Dark mode support
* Further personalized financial insights

## Future Roadmap (Postponed Features)

The following features were part of the original SpendWise concept but are intentionally postponed and are **not** part of the current development scope. They are documented here for future reference only.

### 1. Multi-Currency Support

SpendWise v1 operates in Israeli Shekels (ILS / ₪) only — every transaction, budget, report, and shared expense is recorded and displayed in ILS, with no currency selection anywhere in the app. A future version may add support for recording and analyzing transactions in additional currencies, for example:

* USD — US Dollar
* EUR — Euro
* GBP — British Pound
* and other currencies

Possible future functionality may include:

* Selecting a currency per transaction
* Choosing a main/default (base) currency
* Retrieving current exchange rates from an external exchange-rate service/API
* Converting transactions into the user's chosen base currency
* Preserving both the original amount/currency and the converted value
* Showing converted totals in reports and analytics
* Exchange-rate caching/offline behavior where appropriate
* Synchronization of currency metadata between devices
* Supporting shared-group expenses involving different currencies

This entire item remains a **postponed, future-only** feature — no part of it is implemented in SpendWise v1.

### 2. Receipt Item Extraction & Price Tracking

SpendWise currently supports attaching, storing, and viewing receipt **images** for a transaction — this part is **already implemented** (see Features above). Automatically reading the contents of a receipt is a separate, **not yet implemented** capability.

Not yet implemented, and planned only for a later stage, is a more advanced version of receipt handling that could include:

* OCR / automatic receipt parsing
* Detecting individual products/items from a receipt
* Extracting details such as product name, quantity (when available), individual price, store/business, and purchase date
* Storing receipt items in a structured form associated with the original transaction

**Product price comparison** is a longer-term goal built on top of this: using historical receipt data to compare prices of the same product across stores and over time (e.g. cheapest store, previous prices paid, price trends, lowest/highest/average recorded price). This would be a personal, historical comparison based on the user's own recorded receipts, not a live supermarket price-comparison service.

## Technologies

* Kotlin
* Android Studio
* Jetpack Compose
* Material 3
* Navigation Compose
* Room Database
* Kotlin Coroutines
* Flow and StateFlow
* ViewModel
* Repository Pattern
* MVVM Architecture
* Firebase Authentication
* Cloud Firestore
* Firebase Storage
* Coil (image loading, for receipt images)
* Gradle Kotlin DSL

## Architecture

SpendWise follows the MVVM architectural pattern with an added domain layer for business logic and a sync layer for cloud integration.

The project is divided into several main layers:

### UI Layer

Jetpack Compose screens, navigation, and reusable UI components.

### ViewModel Layer

Manages UI state and connects the UI to the domain and repository layers.

### Domain Layer

Business logic that doesn't belong in a ViewModel or repository: recurring payment scheduling/generation, budget alert evaluation, group balance/settlement/splitting calculations, and analytics calculations.

### Repository Layer

Provides a single access point per feature area (transactions, budgets, recurring plans, categories, groups, notifications, auth) and separates ViewModels from the local database and cloud implementations.

### Data Layer

* **Local**: Room database, entities, and DAOs used to store transactions, budgets, categories, recurring plans, and shared-group data.
* **Sync**: Adapters that synchronize local Room data with Cloud Firestore.
* **Auth**: Firebase Authentication integration.
* **Receipt**: Receipt image handling backed by Firebase Storage.

## Project Structure

```text
app/
└── src/main/java/com/aradrotem/spendwise/
    ├── data/
    │   ├── auth/            # Firebase Authentication, user profiles
    │   ├── local/            # Room database, entities, DAOs
    │   ├── notifications/     # Notification scheduling/preferences
    │   ├── receipt/          # Receipt image processing
    │   ├── repository/       # Repositories (transactions, budgets, groups, ...)
    │   └── sync/              # Cloud Firestore sync engine and adapters
    ├── domain/                # Business logic: recurring schedules, budget alerts,
    │                          # group balances/settlement, analytics calculations
    ├── navigation/            # Navigation graph and screen routes
    ├── ui/
    │   ├── screens/           # Compose screens and ViewModels (transactions,
    │   │                      # budgets, recurring payments, reports/analytics,
    │   │                      # receipts, shared groups, account, settings)
    │   ├── components/        # Reusable UI components (e.g. charts)
    │   ├── format/            # Formatting helpers
    │   └── theme/             # App theme
    └── util/
```

The exact folder structure may change as new features are added.

## Installation

1. Clone the repository:

```bash
git clone https://github.com/AradRotem/SpendWise.git
```

2. Open the project in Android Studio.

3. Add your own Firebase project's `google-services.json` file to the `app/` directory. This file is required to build the app (it configures Firebase Authentication, Cloud Firestore, and Firebase Storage) and is intentionally excluded from Git — it is not included in this repository.

4. Allow Gradle to synchronize the project and download the required dependencies.

5. Run the application on an Android emulator or a physical Android device.

## Build

To build the debug version of the application, run:

### Windows

```powershell
.\gradlew assembleDebug
```

### macOS or Linux

```bash
./gradlew assembleDebug
```

The generated APK will be located in:

```text
app/build/outputs/apk/debug/
```

## Requirements

* Android Studio
* Android SDK
* JDK compatible with the project's Gradle version
* Android emulator or physical Android device
* A Firebase project with Authentication, Firestore, and Storage enabled, and its `google-services.json` added to `app/` (see Installation)

## Project Status

SpendWise is currently under active development.

The core functionality planned through Step 19 has been implemented: transaction management, recurring transactions and installments, budgets, reports and analytics, receipt image attachment, Firebase Authentication, cloud synchronization, and shared expense groups with multi-user Firestore sync and notifications. The project is now in a final QA and polish phase. This version is not intended for Google Play publication.

SpendWise v1 operates in Israeli Shekels (ILS / ₪) only; multi-currency support is a postponed future feature (see Future Roadmap).

## Purpose

This project was developed as part of a mobile application development course.

Its purpose is to demonstrate Android development skills, including:

* Kotlin programming
* Jetpack Compose UI development
* Local database management
* Reactive state management
* MVVM architecture
* Repository pattern
* Clean and maintainable application structure

## Author

**Arad Rotem**

GitHub: [AradRotem](https://github.com/AradRotem)

## License

This project was created for educational purposes.
