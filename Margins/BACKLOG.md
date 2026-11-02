# Margins: things to improve

Running list. Items get built together in one update when you say go.

## 1. Bug: "same source as your last highlight" doesn't kick in
Expected: highlight from a PDF, pick its source on the card, highlight again from the same app
within 3 hours → it goes to that source automatically and the card says so. Actually: it stays Unsorted.

Likely cause: the rule only works if Margins can tell *which app* the text came from. It asks Android
for the app's name; when that fails (common when sharing goes through the system share sheet, and
because Android 11+ hides other apps' details by default), the app is recorded as blank, and the rule
silently does nothing.

Fix plan:
- Record the app's package id (e.g. `com.adobe.reader`) as well as its name. The id doesn't need
  permission to read, so it's available far more often.
- If the app still can't be identified, fall back to: your last *text-only* highlight (no link, no
  book info) from the last 3 hours, whatever app it came from.
- Also cover the case where the previous highlight was left Unsorted: keep consecutive text-only
  highlights together, so choosing a source for one offers to apply it to the others from that session.
- Check on the phone: open a PDF highlight in the app. The line under it should read
  "Saved … from <app name>". If it has no "from …", that confirms the cause.
