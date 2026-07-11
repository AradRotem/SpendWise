# SpendWise

SpendWise is a personal finance management Android application that helps users track their income and expenses, manage their budget, and better understand their financial habits.

The application allows users to record transactions, organize them by category, monitor their balance, and review their financial activity in a clear and simple interface.

## Features

* Add income and expense transactions
* View all transactions in a structured list
* Categorize transactions
* Track the current balance
* Store transaction details such as:

  * Amount
  * Category
  * Date
  * Description
  * Transaction type
* Local data storage using Room Database
* Automatic UI updates using Kotlin Flow and StateFlow
* Modern user interface built with Jetpack Compose
* Form validation for transaction input
* Clean separation between the UI, data, and business logic layers

## Planned Features

* Monthly budget management
* Expense and income charts
* Spending analysis by category
* Monthly and weekly financial summaries
* Budget limit notifications
* Search and transaction filtering
* Transaction editing and deletion
* Cloud synchronization
* Personalized financial insights
* Dark mode support

## Technologies

* Kotlin
* Android Studio
* Jetpack Compose
* Material 3
* Room Database
* Kotlin Coroutines
* Flow and StateFlow
* ViewModel
* Repository Pattern
* MVVM Architecture
* Gradle

## Architecture

SpendWise follows the MVVM architectural pattern.

The project is divided into several main layers:

### UI Layer

Contains the Jetpack Compose screens and UI components displayed to the user.

### ViewModel Layer

Manages the UI state and connects the user interface to the application's data and business logic.

### Repository Layer

Provides a single access point for transaction data and separates the ViewModel from the local database implementation.

### Data Layer

Contains the Room database, entities, and DAO interfaces used to store and retrieve transactions.

## Project Structure

```text
app/
└── src/main/java/.../
    ├── data/
    │   ├── local/
    │   │   ├── TransactionDao
    │   │   ├── TransactionDatabase
    │   │   └── TransactionEntity
    │   └── repository/
    │       └── TransactionRepository
    ├── ui/
    │   ├── addtransaction/
    │   ├── transactions/
    │   ├── components/
    │   └── theme/
    ├── util/
    └── MainActivity
```

The exact folder structure may change as new features are added.

## Installation

1. Clone the repository:

```bash
git clone https://github.com/AradRotem/SpendWise.git
```

2. Open the project in Android Studio.

3. Allow Gradle to synchronize the project and download the required dependencies.

4. Run the application on an Android emulator or a physical Android device.

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

## Project Status

SpendWise is currently under active development.

The basic transaction management system has been implemented, including local storage, transaction creation, and transaction list display. Additional budgeting, analytics, filtering, and visualization features are planned for future versions.

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
