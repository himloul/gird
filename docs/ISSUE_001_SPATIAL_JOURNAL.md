# Issue: Intent-Based Spatial Journaling (@Location Syntax)

## **Problem Statement**
Current task management requires navigating to specific points of interest (POIs) on a map or through cards. This creates high friction for users who think in terms of their "Daily Log" or "Narrative."

## **The "Cool Idea" (Vision)**
Transform Gird from a UI-driven app into a **Command-Line for Real Life**. Users should be able to type their intent in a single stream, and the app should intelligently "Spatial-ize" the note.

### **Core Syntax**
- `Buy milk @Grocery`: Links the task "Buy milk" to the POI named "Grocery".
- `Check mail @Home !daily`: Creates a recurring task.
- `Generic note`: Stays as a global task if no `@` is found.

### **Key Features**
1.  **Markdown-Driven Dashboard:** The home screen looks like a note for the day.
2.  **Contextual Alarms:** Tasks tagged with `!daily` or `!frequent` automatically reset to "pending" when the user leaves the geofence.
3.  **The Parser:** A lightweight regex engine that identifies `@` tags and suggests autocompletions from existing geofences.

---

## **Implementation Goals**
- [ ] Refactor `Task` model to support `isRecurring`.
- [ ] Implement `SpatialParser` to map strings to `fenceId`.
- [ ] Update `LocationService` to handle "Auto-Reset" logic on exit.
- [ ] Refactor `TasksScreen` to prioritize the "Journal Input" and a unified "Narrative List".
