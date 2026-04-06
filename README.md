# Helios Events ☀️
Helios Events is an Event Lottery System app designed for high-demand events at community centres, local organizations and other venues. Helios is built for modern Android, and developed in Android Studio using Java.

Helios allows users to join a waiting list during an Event registration period, preventing first-come-first-serve event registration and giving everyone a fair chance to be selected for participation in high-demand events!
Entering an event waiting list creates an entry for a lottery draw, where organizers can trigger random samples from the waiting list to invite entrants to participate.

## Features:
* Prospective entrants can browse local events according to their preferences, and view event details
* Entrants can join/leave waiting lists at any time
* Lottery-based selection draws, and replacements draws when Entrants do not commit to attending an event
* Organizer tools to customize an Event with posters, a maximum capacity and custom selection sampling
* Unique QR code generation and scanning for easy event access
* Supports Organizer-configurable geofencing for local events, as well as a map view of where entrants signed up from
* Notifications for lottery results and organizer updates
* A robust set of Admin moderation tools allow for management of:
   * Profiles
   * Uploaded images/media
   * Notification logs
* Firebase data storage and realtime updates

## Project Goals:
* Ensure everyone has fair access to limited-capacity events
* Ensure organizers have all the tools they need to plan and manage events effectively
* Ensure administrators can moderate the platform with ease

## Repo Structure:

```
helios-events/
├── app/                                  # Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/helios/
│   │   │   │   ├── auth/                # Authentication logic
│   │   │   │   ├── data/                # Data models and repositories
│   │   │   │   ├── model/               # Domain models
│   │   │   │   ├── service/             # Business logic services
│   │   │   │   └── ui/                  # UI components and activities
│   │   │   └── res/                     # Android resources (layouts, drawables, values)
│   │   ├── test/                        # Unit tests
│   │   └── androidTest/                 # Instrumented Android tests
│   └── build.gradle.kts                 # App-level build configuration
├── functions/                           # Firebase Cloud Functions (backend)
├── docs/
│   ├── javadoc/                         # Generated Java documentation
│   └── UML/                             # UML diagrams and documentation
├── build.gradle.kts                     # Root build configuration
├── settings.gradle.kts                  # Gradle settings
├── gradle.properties                    # Gradle properties
├── local.properties                     # Local build properties
└── README.md                            # This file
```

**Key Components:**
- **app/** - Main Android application written in Java
- **functions/** - Firebase Cloud Functions for backend services
- **docs/** - Documentation including Javadocs and UML diagrams

## Getting Started

### Prerequisites
- Android Studio (latest version)
- Java 11 or higher
- Android SDK (API level 24+)
- Firebase project with authentication and Firestore enabled
- Google Maps API key

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/CMPUT301W26-helios/helios-events.git
   cd helios-events
   ```

2. Create a `local.properties` file in the project root with your configuration:
   ```properties
   sdk.dir=/path/to/android/sdk
   MAPS_API_KEY=your_google_maps_api_key
   ```

3. Open the project in Android Studio and let Gradle sync dependencies

4. Configure Firebase:
   - Download your `google-services.json` from Firebase Console
   - Place it in `app/` directory

5. Build and run the app on an emulator or device

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate Javadoc
./gradlew javadocAll
```

## Development

### Code Organization
- **auth/** - Authentication and user session management
- **data/** - Data models, repositories, and database operations
- **model/** - Core domain models
- **service/** - Business logic and service layer
- **ui/** - Activities, fragments, and UI components

### Testing
- Unit tests located in `app/src/test/`
- Instrumented tests located in `app/src/androidTest/`
- Code coverage tracking with JaCoCo

### Documentation
- Javadoc comments for all public APIs
- Generated documentation available in `docs/javadoc/`
- Architecture diagrams and UML documentation in `docs/UML/`


## Support & Issues

For bug reports and feature requests, please open an issue on the GitHub repository.

## License

MIT License - Copyright (c) 2026 CMPUT301W26-helios

See [LICENSE](LICENSE) file for details.


