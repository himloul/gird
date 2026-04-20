# Issue: Intent-Based Spatial Journaling (Unified Feed & Sequential Input)

## **Problem Statement**
Traditional task management is often disconnected from the user's daily movement. Users have to "go" to a specific place in the UI to manage a specific place in the world. This creates a high cognitive load and friction.

## **The Refined Vision**
Transform Gird into a **Contextual Narrative Assistant**. Instead of a database of cards, the app is a single, intelligent feed that prioritizes your life based on where you are **right now**.

### **Core Interaction: Divide & Conquer**
To minimize friction, we moved from a complex syntax parser to a **Sequential Input Flow**:
1.  **Where?**: A dedicated location search bar. It defaults to your current geofence and provides one-tap autocomplete for others.
2.  **What?**: A focused task description field that appears only after the location (or "Inbox") is confirmed.
3.  **Visual Continuity**: A **Downward Arrow** icon indicates that the new task is being added to the top of the feed immediately below.

### **Key Features**
1.  **The Unified Feed**: A single vertical stream that categorizes your day:
    *   **Focus**: Glowing tasks for your current physical location.
    *   **Queue**: Dimmed tasks for other locations (The future).
    *   **Narrative**: A vertical timeline of past movements and achievements (The past).
2.  **Tonal Cohesion (Absolute Flat)**: A modern Material You aesthetic with **zero strokes, zero borders, and zero shadows**. Hierarchy is defined purely by typography and subtle tonal shifts.
3.  **Interactive Alerts**: System-level notifications that allow you to "Mark as Done" without opening the app.
4.  **Physics-Based Polling**: Velocity-aware adaptive location tracking to maximize battery while maintaining high accuracy.

---

## **Implementation Status (v1.1.0)**
- [x] **Contextual Task System**: Completed.
- [x] **Storyline Narrative**: Completed with auto-dwell time calculation.
- [x] **Sequential Input UI**: Completed with AnimatedContent transitions.
- [x] **Tonal Cohesion Refactor**: Completed across Tasks and Map screens.
- [x] **Smart State Observation**: Implemented via `derivedStateOf` for instant UI updates.
