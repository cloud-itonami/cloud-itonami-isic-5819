(ns specpubops.store
  "SSoT for the ISIC-5819 'other publishing activities' COORDINATION
  actor, behind a `Store` protocol so the backend is a swap, not a
  rewrite -- the same seam every `cloud-itonami-isic-*` actor in this
  fleet uses.

  Concrete illustrative product line (documented plainly per this
  fleet's fresh-scaffold convention): a specialty publisher producing
  greeting cards, calendars, and posters -- the classic ISIC 5819
  residual category (catalogs/greeting cards/calendars/posters/forms,
  not elsewhere classified as books [5811], newspapers [5813],
  directories/mailing lists [5812], or software [5820]).

  This actor coordinates the back-office operations of that publisher:
  design/print-run production-record logging, design/print-run
  scheduling proposals, outbound distribution coordination (to
  retailers/wholesalers), and copyright/content-risk-concern flagging
  (trademark or licensed-character similarity, cultural/seasonal
  sensitivity, plagiarism of another publisher's design observed in a
  design proof). It never touches finalizing an editorial-content
  decision (what a card/calendar/poster design actually depicts,
  whether it ships as designed) or a legal-risk clearance decision
  (copyright/trademark/licensed-character clearance) -- see
  `specpubops.governor`'s `scope-exclusion-violations`, a HARD,
  permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `titles` directory keyed by `:title-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued an earlier shepherd attempt).

  A registered/verified title record (this publisher's own record of a
  card/calendar/poster/catalog title in production) must exist before
  ANY proposal for that title may ever commit or escalate --
  `specpubops.governor`'s `title-unverified-violations` re-derives this
  from the title's own `:registered?`/`:verified?` fields, never from
  proposal self-report, the SAME 'ground truth, not self-report'
  discipline every sibling actor's own governor uses.

  The ledger stays append-only: which title a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.")

(defprotocol Store
  (title [s title-id] "Registered title record, or nil.
    Title map: {:title-id .. :name .. :registered? bool :verified? bool}.
    A `title` here is any card/calendar/poster/catalog/forms title-line
    in production -- purely descriptive, the governor treats all
    uniformly.")
  (all-titles [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-titles [s titles] "replace/seed the title directory (map title-id->title)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained title directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:titles
   {"title-1" {:title-id "title-1" :name "Four Seasons Greeting Card Collection -- Spring/Summer 2027 (design approved, license clearance confirmed)"
               :registered? true :verified? true}
    "title-2" {:title-id "title-2" :name "2027 Wall Calendar -- National Parks Photography Series (design approved, license clearance confirmed)"
               :registered? true :verified? true}
    "title-3" {:title-id "title-3" :name "Retro Arcade Poster Series (licensed-character artwork, clearance pending)"
               :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (title [_ title-id] (get-in @a [:titles title-id]))
  (all-titles [_] (sort-by :title-id (vals (:titles @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-titles [s titles] (when (seq titles) (swap! a assoc :titles titles)) s))

(defn seed-db
  "A MemStore seeded with the demo title directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `titles` map (title-id string ->
  title map) -- the primary test/dev entry point. `titles` may be empty
  (an unregistered-everywhere store)."
  [titles]
  (->MemStore (atom {:titles (or titles {}) :ledger [] :coordination-log []})))
