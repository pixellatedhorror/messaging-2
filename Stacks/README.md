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
| Ratings | All of: multi-category scores, verdict labels, stars + mood tags (each can be switched off) |
| Shelves | Fully custom, with no built-in shelves |
| Tracking | Page / % progress, time for audiobooks, rereads. **No goals** |
| Abandoned books | Don't count as finished in stats |
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
- No defaults. On first launch, offer a few suggestions that are easy to delete (e.g. *To read / Reading / Read / Abandoned*)
  so the app isn't empty.
- A book can sit on several shelves.
- Open question: should "currently reading" be a **status** (from progress) rather than a shelf? That keeps stats
  correct however you name your shelves. See below.

### Ratings
Each layer can be switched off in settings:
- **Category scores**: you define the categories (Prose, Plot, Characters, Re-readability…), each scored 1–10.
  You can choose to weight them into an overall score.
- **Verdict**: one label per read, from your own list (*Life-changing / Loved / Fine / Not for me*).
- **Stars + mood tags**: half stars, plus tags you make up (cozy, devastating, slow burn).
- Ratings belong to a **read**, not a book, so a reread can score differently. The book page shows how your view changed.

### Reading progress
- Print / ebook: log a page number or a %. Each entry has a date, which gives pages per day.
- Audiobook: log the time position (e.g. 4:35 of 11:20). Each entry has a date, which gives listening time per day.
- Quick buttons for the book you're reading: +10 / +25 pages, or +15 / +30 min for audio.
- **Rereads**: each read has its own start date, finish date, progress, rating and journal entries.
  An abandoned read is just a read that ends early, and it records where you stopped.

### Journal per book
- Dated entries, each tied to a read and, optionally, a page ("p. 142: didn't see that coming").
- Optional quotes (typed in by hand; a Margins link is a possible later idea).
- A "final thoughts" entry that you're prompted to write when you finish a read.

### Stats & reading year
- No goals or targets. Stats only look back at what you've read.
- Books finished per month, pages read and hours listened per month (kept separate, never converted into each other),
  average days per book, longest and shortest book, and your most-read authors.
- Abandoned reads don't count as finished books. They're listed separately ("abandoned this year: 3").
- Breakdowns by shelf, mood tag, verdict, and the average of each rating category.
- **Year in review** page: covers of everything you finished, top-rated books, your most-used mood tags, first and last book of the year.

### Export / backup
- Full JSON backup + restore (the same approach as Margins).

## Rough data model
```
Book      id, title, authors, isbn, olid, cover, year, added
Shelf     id, name, order
BookShelf book_id, shelf_id
Read      id, book_id, format(print|ebook|audio), length(pages or minutes), started, finished, abandoned
Progress  read_id, date, position(page, % or minutes)
Rating    read_id, stars, verdict, scores{category: n}, moods[]
Journal   id, read_id, date, position?, text, kind(note|quote|final)
Settings  categories[], verdicts[], moods[], layers on/off
```

## Open questions
1. **Status vs shelves.** With fully custom shelves, how does the app know what's "currently reading" or "finished"
   for stats? Suggestion: work it out from reads (one in progress = reading; one with a finish date = read)
   and keep shelves purely for your own grouping.
2. Do the pages/minutes of an **abandoned** read still count in "pages read" / "hours listened"?
   (Suggestion: yes, since you did read them. Only the "books finished" count leaves them out.)
3. When rating categories are weighted into one overall score, can you edit the weights, or are they all equal?
4. Ebooks: track **%** (what most ebook apps show) or pages?
5. Home screen widget: the current book's progress, or none?
6. Name.

## Decided against
- Yearly reading goals / targets.

## Later / parked
- Import a Goodreads CSV export (shelves, ratings, dates, rereads).
- Barcode scanning for ISBNs.
- Pull quotes from Margins onto the book's journal.
- "What next?" suggestions from your to-read shelves, based on your mood tags and category scores.
