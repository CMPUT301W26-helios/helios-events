# Helios Events ☀️
Helios Events is an Event Lottery System app designed for high-demand events at community centres, local organizations and other venues. Helios is built for modern Android, and developed in Android Studio using Java.

Helios allows users to join a waiting list during an Event registration period, preventing first-come-first-serve event registration and giving everyone a fair chance to be selected for participation in high-demand events!
Entering an event waiting list creates an entry for a lottery draw, where organizers can trigger random samples from the waiting list to invite entrants to participate.

## (Planned) Features:
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

## License:
MIT License - Copyright (c) 2026 CMPUT301W26-helios


