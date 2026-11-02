# Margins

A home for every passage you highlight: books, articles, PDFs, anything. A companion app to ReadShelf, with the same look.
Offline, no account. Everything stays on your phone.

## Saving a highlight
* Select text in any app → tap **Margins** in the selection menu, or **Share → Margins**.
* It's saved instantly. A card slides up where you can add a **note**, **tags** and the **source**,
  or tap **Undo**. Dismissing the card keeps the highlight.
* **Browsers**: page title, author and link are filled in (title/author fetched from the page if the browser
  doesn't send them). Chrome's "link to highlight" is kept, so **Open at passage** jumps straight to it.
* **Kindle / Apple-style / Play Books**: book title and author are read from the text the app shares
  ("From: … by …", "Excerpt From …", "— Title by Author"); the boilerplate is stripped.
* **PDF readers & other apps** that send only text: pick from your recent sources or add a new one, or leave it
  **Unsorted**. Your next highlights from the *same app within 3 hours* go to that source automatically
  (the card says so, and you can change it).
* Sharing the same passage twice doesn't make a duplicate.

## In the app
* **Highlights**: newest/oldest, grouped by source, or shuffled. Search covers text, notes, sources and tags.
* **Sources**: each book/article with its highlights. Edit title/author/link/type, share all its highlights as
  text, merge two sources, or delete one (its highlights move to Unsorted).
* **Tags**: counts per tag; tap to filter, rename (merges if the name exists) or delete.
* **+**: add a highlight by hand (e.g. from a paper book), with page/location.

## Daily highlight widget
Long-press the home screen → Widgets → **Margins: highlight of the day**. A random highlight, new each day.
Tap it to open the highlight; tap ↻ for another.

## Export
⋮ menu → **Excel** (Highlights sheet + Sources sheet, clickable links), **Markdown** (grouped by source, tags as
`#tags`, for Obsidian/Notion/Keep), or a **full backup** (.json) that **Restore** merges back without overwriting.

## Building
`./build.sh` builds `Margins.apk` without Gradle (aapt2 → javac → dx → zipalign → apksigner), same toolchain as
ReadShelf. Needs Ubuntu's `aapt apksigner zipalign dalvik-exchange` packages and an `android.jar` (API 34) at
`$ANDROID_JAR` (default `/tmp/ap/android-34/android.jar`).
`release.keystore` is the signing key: keep it, or future versions won't install over this one.
