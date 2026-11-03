# Stacks (working name): a personal Goodreads

Brainstorm only. Nothing is built yet.

A private reading tracker for Android. No account, no feed, no other users. Everything stays on the phone.
The only time it goes online is when you look up a book.

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

## Name (undecided)
Sanskrit candidates:
| Name | Script | Meaning | Why it fits |
|---|---|---|---|
| **Svādhyāya** | स्वाध्याय | self-study; one's own reading | A private reading practice. The leading pick |
| **Bhāṣya** | भाष्य | commentary on a text | Your journal and ratings are your commentary on each book |
| **Granthālaya** | ग्रन्थालय | library, "home of books" | Literal, and it reads like a library |
| **Anukramaṇī** | अनुक्रमणी | index / catalogue of texts | The old Vedic catalogues. Fits custom shelves |
| **Pāṭha** | पाठ | reading, a reading of a text | Short. Each reread is another pāṭha |
| **Smṛti** | स्मृति | memory, what is remembered | The journal, and remembering what you read |
| **Kośa** | कोश | treasury; also a lexicon | A treasury of books |

Short app labels: *Svadhyaya*, *Bhashya*, *Patha*, *Kosha* (without diacritics for the launcher).

## Decided against
- Yearly reading goals / targets.
- Home screen widget.

## Later / parked
- Import a Goodreads CSV export (shelves, ratings, dates, rereads).
- Barcode scanning for ISBNs.
- Pull quotes from Margins onto the book's journal.
- "What next?" suggestions from your to-read shelves, based on your mood tags and category scores.
