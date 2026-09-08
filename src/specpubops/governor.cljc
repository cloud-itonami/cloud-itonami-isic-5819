(ns specpubops.governor
  "SpecPubGovernor -- the independent compliance layer that earns
  the SpecPubAdvisor the right to commit. The advisor has no notion
  of whether a title (a card/calendar/poster/catalog title in
  production) is actually registered and verified, whether its own
  proposed `:effect` secretly claims a direct actuation instead of a
  mere proposal, or whether it has silently drifted into a
  permanently out-of-scope decision area, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- OPERATIONS COORDINATION
  ONLY (production-record logging, production-operation scheduling,
  content-concern flagging, distribution coordination) for the
  illustrative product line of a specialty publisher of greeting
  cards, calendars, and posters (ISIC 5819's residual 'other
  publishing' category -- see `specpubops.store` docstring). It NEVER
  performs or authorizes:
    - finalizing an editorial-content decision (what a card/calendar/
      poster design actually depicts, whether it ships as designed)
    - a legal-risk clearance decision (copyright/trademark/licensed-
      character clearance)

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Title unverified         -- the target title record (this
                                    publisher's own record of a title
                                    in production) must exist AND be
                                    independently confirmed
                                    `:registered?`/`:verified?` in the
                                    store before ANY proposal for it may
                                    commit or even escalate. Never
                                    trusts a proposal's own claim about
                                    the title -- re-derived from the
                                    title's own store record, the same
                                    'ground truth, not self-report'
                                    discipline every sibling actor's
                                    governor uses.
    2. Effect not :propose      -- every proposal's `:effect` MUST
                                    be `:propose`. Any other effect
                                    value is, by construction, a
                                    claim to directly actuate/commit
                                    outside governance -- HARD block,
                                    not merely low-confidence.
    3. Scope exclusion          -- ANY proposal (regardless of op)
                                    whose op, rationale, summary,
                                    citations or draft value touches
                                    finalizing-an-editorial-content-
                                    decision / legal-risk-clearance
                                    territory is a HARD, PERMANENT
                                    block -- this actor's charter
                                    excludes that territory
                                    structurally, not as a rollout
                                    milestone. Evaluated
                                    UNCONDITIONALLY on every
                                    proposal. An op outside the
                                    closed four-op allowlist is the
                                    SAME failure mode (an advisor
                                    proposing something it was never
                                    authorized to propose) and is
                                    folded into this same check.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-content-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `specpubops.phase` independently agrees: `:flag-content-concern` is
  never a member of any phase's `:auto` set either -- two layers, not
  one. Per this fleet's Wave-4 person-facing-service safety guardrail
  (ADR-2607152500): the closed op allowlist never includes an op that
  directly finalizes an editorial-content decision or a legal-risk
  clearance, and any 'flag a concern' op always escalates and is never
  auto-commit-eligible.

  Scope-excluded terms below are deliberately phrased as
  finalization/execution ACTIONS ('finalize the editorial-content
  decision', 'issue a legal-risk clearance') rather than bare nouns
  ('editorial content', 'copyright') -- a known self-tripping bug class
  in this fleet is a bare-noun scope term accidentally matching inside
  the mock advisor's own DEFAULT rationale/disclaimer text for a
  legitimate, allowed proposal, causing the actor to self-block on its
  own happy path. `specpubops.advisor-test`'s
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  guards this directly against every default proposal this advisor
  produces."
  (:require [kotoba.lang.text :as str]
            [specpubops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`).
  None of these ops directly finalizes an editorial-content decision or
  a legal-risk clearance (ADR-2607152500 Wave-4 guardrail)."
  #{:log-production-record :schedule-production-operation
    :flag-content-concern :coordinate-distribution})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-content-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- finalizing an editorial-
  content decision, or issuing a legal-risk clearance (copyright/
  trademark/licensed-character clearance). Phrased as finalization/
  execution ACTIONS, never a bare noun, so a legitimate proposal that
  merely mentions 'copyright' or 'editorial content' in passing (e.g.
  while explaining it performed no such judgment) never self-trips this
  gate -- see the namespace docstring's note on this fleet's known
  bare-noun self-tripping bug class. Scanned across the proposal's op/
  summary/rationale/cites/value, never trusting the advisor's own
  framing of its intent."
  ["editorial-content decision" "editorial content decision"
   "finalize editorial content" "finalize the editorial content"
   "editorial decision" "編集内容の確定" "編集判断の確定" "編集決定"
   "legal-risk clearance" "legal risk clearance" "legal clearance"
   "copyright clearance" "copyright-clearance"
   "trademark clearance" "trademark-clearance"
   "license clearance" "license-clearance" "licensing clearance" "licensing-clearance"
   "著作権クリアランス" "商標クリアランス" "法的クリアランス" "法的リスクのクリアランス" "使用許諾クリアランス"])

;; ----------------------------- checks -----------------------------

(defn- title-unverified-violations
  "The target title must exist AND be independently `:registered?`/
  `:verified?` in the store -- never trust the proposal's own
  `:title-id` claim without a store lookup."
  [{:keys [title-id]} st]
  (let [r (store/title st title-id)]
    (when-not (and r (:registered? r) (:verified? r))
      [{:rule :title-unverified
        :detail (str title-id " は未登録または未検証のタイトル -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches finalizing-an-editorial-content-decision/
  legal-risk-clearance territory, regardless of confidence or how clean
  every other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "編集内容の確定判断/著作権・商標等の法的クリアランス判断の領域に触れる提案は永久に禁止"}])))

(defn check
  "Censors a SpecPubAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [title-id (or (:title-id proposal) (:title-id request))
        hard (into []
                   (concat (title-unverified-violations {:title-id title-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :title-id   (:title-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
