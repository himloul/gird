# Gird

Gird is a high-performance, privacy-focused Android geofencing application. It allows users to define custom geographic boundaries and receive local notifications upon entry or exit using entirely local processing.

*The name Gird is inspired by the Old English term meaning "to encircle, surround, or bind," reflecting the app's core purpose of protecting your locations with precise, secure boundaries.*

## Features

- **Privacy-Native Tracking**: Uses standard Android Location APIs, ensuring your coordinates are processed entirely on-device without relying on external service frameworks.
- **Offline Maps**: Powered by [Osmdroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap) for 100% offline-compatible mapping and tile caching.
- **Material You & Themed Icons**: A header-less, immersive minimalist interface that adapts to your system's dynamic colors, including support for themed home screen icons (Android 13+).
- **Customizable Power Modes**: Choose between **Battery Saver** (Network-only), **Balanced** (Adaptive), and **High Precision** (GPS-focused) to control the app's battery footprint.
- **Activity Log (History)**: A local-only private diary of your movements, recording arrival and departure times for every geofence.
- **Data Sovereignty**: Built-in tools to view data summaries and perform a complete "Factory Reset" of all local geofences and logs.
- **Persistent Awareness**: Low-impact background monitoring that automatically resumes after device reboots.
- **Color-Coded Zones**: Organize your boundaries with distinct visual themes (Red, Blue, Green).
- **Zero Telemetry**: No analytics, no tracking, and zero data leakage. Your data belongs to you.

## Technical Architecture

- **Foreground Service**: Ensures persistent monitoring on Android 10+ with a low-priority notification to comply with system power-saving rules.
- **Adaptive Power Management**: Scales polling frequency (from 30s to 10m) based on your proximity to the nearest geofence boundary to maximize battery life.
- **State Machine**: Uses a 10-meter hysteresis buffer to prevent "notification jitter" caused by signal bounce.
- **Boot Receiver**: Leverages `RECEIVE_BOOT_COMPLETED` to resume the monitoring lifecycle without user intervention.

## Privacy

Gird is built on the principle of **Zero Trust**:
- **Local-Only**: All location data is processed in volatile RAM.
- **No Cloud**: Coordinates and geofence data never leave the device.
- **Transparent**: No third-party tracking, analytics, or proprietary libraries.

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
