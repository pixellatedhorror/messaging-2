# Smriti (स्मृति): a personal Goodreads

*Smṛti* means "memory, what is remembered": the app is a record of what you've read and what you thought of it.

Version 1.0 is built (`./build.sh` → `Smriti.apk`). The design notes it was built from are kept below.

## Using it
* **+** → search Open Library. One box takes a title, an author or an ISBN; `author:`, `title:` or `isbn:` forces one.
  Pick the book, then the edition (it sets the cover and page count), or **Type it in yourself**.
* **Book page**: **Start reading** (choose print / ebook / audiobook and the length), **Log progress**, the quick
  buttons (+10 / +25 pages, +1 / +5 %, +15 / +30 min), **Finished**, **Abandon**, journal **Note**.
  Finishing (or abandoning) opens the rating: stars, verdict, mood tags, category scores and final thoughts.
  Tap a past read to re-rate it, add a journal entry, or edit its dates, format and progress log.
  **Start a reread** gives the same book a new read with its own dates, rating and journal.
* **Home**: what you're reading now, with quick logging, and your recently finished books.
* **Library**: filter by status (worked out from your reads) or shelf, sort, search. **Edit shelves** to add,
  rename, reorder or delete shelves.
* **Stats**: per year or all time. Books finished (rereads included and noted), pages read, hours listened and
  average days per book; per-month charts (tap a bar for its value); longest/shortest; authors, verdicts,
  category averages, mood tags, shelves. **Year in review** from there.
* **Settings** (gear): switch rating layers on/off; edit score categories, verdicts and mood tags (renaming updates
  past ratings); backup / restore.

### Notes
* Data: one JSON file in the app's private storage (`smriti.json`, previous version kept as `smriti.json.prev`).
* Covers are downloaded from Open Library the first time they're shown and kept on the phone. Backups don't include
  them; they download again after a restore.
* Restore can **merge** (adds what isn't already there) or **replace**.
* Not yet tested against the live Open Library API (the build sandbox couldn't reach it); tested with sample
  responses in the API's documented shape.

## Building
Same toolchain as Margins, no Gradle: `./build.sh` (aapt2 → javac → dx → zipalign → apksigner). Needs Ubuntu's
`aapt apksigner zipalign dalvik-exchange android-sdk-platform-23` packages; `ANDROID_JAR` defaults to the API 23
`android.jar` they install (compile only: the app targets API 34, min 24). `release.keystore` is the signing key:
keep it, or later versions won't install over this one.

A private reading tracker for Android. No account, no feed, no other users. Everything stays on the phone.
The only time it goes online is when you look up a book.

# Design notes

## Decisions so far
| Area | Decision |
|---|---|
| Platform | Android app, offline-first |
| Relationship to Margins / ReadShelf | Standalone, fresh start |
| Social | None. Just me |
| Adding books | Search Open Library by **title, author or ISBN** (one search box), plus manual entry |
| Ratings | All of: category scores (equal weight), verdict labels, stars + mood tags (each can be switched off) |
| Shelves | Fully custom, with no built-in shelves. Purely for your own grouping |
| Reading / finished | Worked out from your reads, not from shelves |
| Tracking | Pages for print, pages or % for ebooks, time for audiobooks. Rereads. **No goals** |
| Abandoned books | Don't count as finished, but the pages/minutes you got through do count |
| Widget | None |
| Personal extras | Custom ratings, stats & reading year, a journal for each book |

## Features

### Adding a book
- One search box. Type a title, an author or an ISBN (10 or 13 digits) and it works out which you meant.
  - Something like `author: le guin` or `isbn: …` forces that kind of search.
  - An author search lists their works, newest first, with the books you already have marked.
- Choose an **edition** (cover, page count, year), because the page count affects progress.
- Choose a **format**: print, ebook or audiobook. An audiobook needs its total length (h:mm), which you enter
  yourself because Open Library rarely has it.
- Covers are downloaded once and kept on the phone, so the app works fully offline after that.
- Manual entry for anything Open Library doesn't have.

### Shelves (fully custom)
- No defaults. On first launch, offer a few suggestions that are easy to delete (e.g. *To read / Favourites / Book club*)
  so the app isn't empty.
- A book can sit on several shelves.
- Shelves are only for your own grouping. Whether you're **reading** or have **finished** a book is worked out from its
  reads: a read in progress means reading, one with a finish date means finished, one marked abandoned means abandoned.
  So stats are correct however you name or arrange your shelves.
- The home screen shows "Reading now" from that status, not from a shelf.

### Ratings
Each layer can be switched off in settings:
- **Category scores**: you define the categories (Prose, Plot, Characters, Re-readability…), each scored 1–10.
  The overall score is their plain average (all categories count equally). A category left blank is skipped.
- **Verdict**: one label per read, from your own list (*Life-changing / Loved / Fine / Not for me*).
- **Stars + mood tags**: half stars, plus tags you make up (cozy, devastating, slow burn).
- Ratings belong to a **read**, not a book, so a reread can score differently. The book page shows how your view changed.

### Reading progress
- **Print**: log a page number.
- **Ebook**: log a page number **or** a %, whichever your ebook app shows. If the edition's page count is known,
  each is shown as the other too (e.g. "p. 182 · 47%").
- **Audiobook**: log the time position (e.g. 4:35 of 11:20).
- Each entry has a date, which gives pages per day (or listening time per day).
- Quick buttons for the book you're reading: +10 / +25 pages, +5% for ebooks, or +15 / +30 min for audio.
- **Rereads**: each read has its own format, start date, finish date, progress, rating and journal entries.
  An abandoned read is just a read that ends early, and it records where you stopped.

### Journal per book
- Dated entries, each tied to a read and, optionally, a position ("p. 142: didn't see that coming", "3:10:00: …").
- Optional quotes (typed in by hand; a Margins link is a possible later idea).
- A "final thoughts" entry that you're prompted to write when you finish a read.

### Stats & reading year
- No goals or targets. Stats only look back at what you've read.
- Books finished per month, pages read and hours listened per month (kept separate, never converted into each other),
  average days per book, longest and shortest book, and your most-read authors.
- Abandoned reads don't count as finished books and are listed separately ("abandoned this year: 3"),
  but the pages and minutes you got through **do** count toward pages read and hours listened.
- Ebook progress logged as % counts toward pages read when the page count is known.
- Breakdowns by shelf, mood tag, verdict, and the average of each rating category.
- **Year in review** page: covers of everything you finished, top-rated books, your most-used mood tags, first and last book of the year.

### Export / backup
- Full JSON backup + restore (the same approach as Margins).

## Rough data model
```
Book      id, title, authors, isbn, olid, cover, year, added
Shelf     id, name, order
BookShelf book_id, shelf_id
Read      id, book_id, format(print|ebook|audio), pages?, minutes?, started, finished, abandoned
Progress  read_id, date, page? | percent? | minute?
Rating    read_id, stars, verdict, scores{category: n}, moods[]
Journal   id, read_id, date, position?, text, kind(note|quote|final)
Settings  categories[], verdicts[], moods[], layers on/off
```
Status of a book = from its latest read: none → not started, in progress → reading, finished, or abandoned.

## Name
**Smriti** (स्मृति, *smṛti*): memory, what is remembered. The launcher label is "Smriti", without diacritics.

Other candidates considered: Svādhyāya, Bhāṣya, Granthālaya, Anukramaṇī, Pāṭha, Kośa.

## Decided against
- Yearly reading goals / targets.
- Home screen widget.

## Later / parked
- Import a Goodreads CSV export (shelves, ratings, dates, rereads).
- Barcode scanning for ISBNs.
- Pull quotes from Margins onto the book's journal.
- "What next?" suggestions from your to-read shelves, based on your mood tags and category scores.
