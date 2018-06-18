(ns rehiring.month-loader
  (:require
    [cljs.pprint :as pp]

    [clojure.walk :as walk]
    [re-frame.core
     :refer [subscribe reg-sub dispatch reg-event-db reg-event-fx]
     :as rfr]
    [rehiring.utility :refer [<sub] :as utl]
    [rehiring.job-parse :as parse]))

(defn gMonthlies-cljs
  "The gMonthlies table of contents is defined in index.html for extensibility,
  then translated here to CLJS-ese, except hnId: remains camel-case :hnId."
  []
  (walk/keywordize-keys (js->clj js/gMonthlies)))

(defn get-monthly-def
  "Retrieve month info based on HN message Id"
  [hn-id]
  (some (fn [month]
          (when (= (:hnId month) hn-id)
            month))
    (gMonthlies-cljs)))

(defn month-page-urls
  "Compute a vector of string URLs to be scraped, given month info
  hard-coded in index.html. Look for a script tag defining gMonthlies."

  [month-hn-id]

  (when month-hn-id
    (if-let [mo-def (get-monthly-def month-hn-id)]          ;; hard-coded table in index.html
      (map (fn [pg-offset]
             ;; files are numbered off-by-one to match the page param on HN
             (pp/cl-format nil "files/~a/~a.html" month-hn-id (inc pg-offset)))
        (range (:pgCount mo-def)))
      (throw (js/Exception. (str "msg id " month-hn-id " not defined in gMonthlies table."))))))

(defn unprocessed-month
  "This is the starting point of the state we will use to track a multi-page
  month load across two phases. Me start with this map and
  then assoc/update away at it for the life of the load.

  This, btw, is what we fell back on after internalizing that
  subscriptions are meant only for views and other subscriptions.

  What we will do during :cull-athings is 'consume' the list
  of page URLS like a work queue. That could easily be implemented
  instead by having a 'current-page-url-index' climbing from zero."

  [month-hn-id]

  (let [urls (month-page-urls month-hn-id)]
    {:month-hn-id          month-hn-id
     :phase                :cull-athings                    ;; ...or :parse-jobs
     :month-load-complete? false
     :page-url-count       (count urls)
     :page-urls-remaining  urls
     :jobs-seen            #{}
     :athings              []
     :jobs                 []}))

;;; --- subscriptions -------------------------------------------------------
(rfr/reg-sub :month-load-prop
  (fn [db [_ prop]]
    (get-in db [:month-load-task prop])))

(rfr/reg-sub :month-phase
  (fn [db]
    (get-in db [:month-load-task :phase])))

(rfr/reg-sub :month-jobs
  (fn [db]
    (get-in db [:month-load-task :jobs])))

;;; --- ACTION STARTS HERE: kick off month load  -----

(rfr/reg-event-db :month-set
  ;
  ; This is kicked off (a) by the initialize-db event and
  ; (b) when the user selects a new month.
  ;
  ; No ensuing process event is dispatched because month-loader/job-listing-loader, the view
  ; below,  subscribes to [:month-load-task :page-urls-to-load] and kicks off processing in
  ; the on-load event handler.
  ;

  (fn [db [_ month-hn-id]]
    (let [mo-def (get-monthly-def month-hn-id)]
      (assoc db :month-load-task
                (unprocessed-month month-hn-id)))))

;;; --- next, a component that loads pages into iframes and kicks off their processing ---------------------

(declare mk-page-loader job-page-athings)

(rfr/reg-sub :month-load-task
  (fn [db]
    (:month-load-task db)))

(defn job-listing-loader
  "This is a key player in loading a month. It spawns one
  child loader whenever there is a URL to be scraped. We
  then grab candidate nodes (class 'aThing') for later
  processing.

  Sadly, iframes offer no way to tell if the URL does not
  load, so this app hangs if a month is configured for more
  files than are in resources/public/files/<month>. Only
  fix is a timeout that would abort after N seconds.

  Left as an exercise."
  []
  (fn []
    [:div {:style {:display "none"}}
     (let [task (<sub [:month-load-task])]
       (when (seq (:page-urls-remaining task))
         (println :mk-pg-loader-for (:page-urls-remaining task))
         [mk-page-loader task]))]))

;;; --- dev-time limits -----------------------------
;;; n.b.: these will be limits *per page*

(def ATHING-PARSE-MAX 1000)

(defn job-page-athings
  "Pretty simple. All messages are dom nodes with class aThing. Grab those
  and later we will check the opening text vertical bars | to identify jobs.

  Don't judge me."
  [ifr-dom]

  (when-let [cont-doc (.-contentDocument ifr-dom)]
    (let [hn-body (aget (.getElementsByTagName cont-doc "body") 0)]
      (let [a-things (take ATHING-PARSE-MAX (prim-seq (.querySelectorAll hn-body ".athing")))]
        (set! (.-innerHTML hn-body) "")                     ;; free up memory
        a-things))))

(defn mk-page-loader
  "Beside the actual gathering of aThing nodes, there are several key
  elements seen here for the first time that make the animation of
  the progress bar work:

  -- The work is broken up into steps, doing one step (here, scraping
  one page), and then kicking off the next step by dispatching a continuing event;

  -- pass around a map steadily updated with the changing state of the page load;

  -- in the events, assoc the task map into the db so the views change; and

  -- apply the ^:flush-dom meta to each event so the progress actually renders. Or...
     use, eg, ':dispatch-later [{:ms 42 :dispatch [:cull-jobs-from-athings]}]' instead
     of the usual :dispatch. Both worked for me (I used 0ms since I did not really
     want to delay anything."
  []

  (fn [task]
    (assert (first (:page-urls-remaining task)))
    [:iframe {:src     (first (:page-urls-remaining task))
              :on-load #(dispatch
                          ^:flush-dom [:page-athings-culled
                                       (utl/update-multi task
                                         [:page-urls-remaining rest]
                                         [:athings concat (job-page-athings (.-target %))])])}]))

(reg-event-fx :page-athings-culled
  (fn [{:keys [db]} [_ task]]

    (if (empty? (:page-urls-remaining task))
      ;; Here we make the phase transition from culling raw nodes
      ;; from HN pages to parsing those nodes identified as jobs,
      ;; triggering a second visual zero-to-completion with a
      ;; new label simply by altering the :phase property.
      ;;
      ;; How does that work?
      ;;
      ;; The view watches the phase directly to decide its label,
      ;; and the progress bar value subscriptions each tailor
      ;; their computation to the phase.
      ;;
      {:db       (assoc db :month-load-task
                           (assoc task :phase :parse-jobs))
       :dispatch ^:flush-dom [:cull-jobs-from-athings]}

      ;; Next db update triggers page-loader component to load next page so
      ;; there is no need to dispatch an event to keeo things movung -- the
      ;; page loader will kick off this event itself.
      {:db (assoc db :month-load-task task)})))

(def ATHING_CHUNK_SZ 20)                                    ;; bigger chunks zoom due, so use small value to see progress bar working

(reg-event-fx :cull-jobs-from-athings
  (fn [{:keys [db]} [_]]

    ;; Notice how first (take ATHING_CHUNK_SZ ...) then
    ;; (nthrest ATHING_CHUNK_SZ...) work together to treat
    ;; the :athings lazy sequence as a queue. Bigger chunk
    ;; sizes go quite fast (I think I use 100 in the live site),
    ;; smaller chunks let us see the progress bar progressing.

    (let [task (:month-load-task db)
          chunk (take ATHING_CHUNK_SZ (get-in db [:month-load-task :athings]))]

      (if (seq chunk)
        ;; here we see a rather huge part of the processing in which a fairly
        ;; interesting job-parse function traverses the aThing and extracts a
        ;; map describing a job, with :OK as false for things not jobs.
        ;;
        ;; job-parse was fun, but not relevant to the progress bar problem.

        (let [new-jobs (filter #(:OK %) (map #(parse/job-parse % (:jobs-seen task)) chunk))]

          {:db       (assoc db :month-load-task
                               (utl/update-multi task
                                 [:athings nthrest ATHING_CHUNK_SZ]
                                 [:jobs concat new-jobs]
                                 [:jobs-seen clojure.set/union (into #{} (map :hn-id new-jobs))]))
           ;;
           ;; either of these next two work. I prefer not guessing at a delay.
           ;; but zero works, so under the hood these may be the same
           ;;
           ;; :dispatch-later [{:ms 0 :dispatch [:cull-jobs-from-athings]}]
           :dispatch ^:flush-dom [:cull-jobs-from-athings]
           })

        ;; all athings have been processes...
        {:db (assoc-in db [:month-load-task :month-load-complete?] true)}))))


