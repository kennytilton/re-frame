(ns rehiring.db
  (:require [clojure.string :as str]
            [re-frame.core :as rfr]
            [rehiring.utility :as utl]
            [rehiring.month-loader :as loader]))

(defn initial-db []
  (merge
    {:job-collapse-all       false
     :toggle-details-action  "expand"
     :job-display-max        42
     :job-sort               (nth utl/job-sorts 0)
     :show-filters           true
     :show-filtered-excluded false
     :rgx-match-case         false
     :rgx-xlate-or-and       true
     :search-history         {}
     :show-job-details       {}}))

(rfr/reg-cofx :storage-user-notes
  ;; load user notes from local storage
  ;; todo: name rectification
  ;; one glitch in naming: a user can have multiple notes on one job,
  ;; so I ended up with plurals at two levels. "unotes" are a collection
  ;; of notes on *one job*. "user-notes" are the collection of "unotes".
  ;;
  (fn [cofx _]
    (assoc cofx
      :storage-user-notes
      (utl/ls-get-wild (str utl/ls-key "-unotes-")))))

(rfr/reg-event-fx ::initialize-db
  [(rfr/inject-cofx :storage-user-notes)]

  (fn [{:keys [storage-user-notes]} _]
    (merge
      {:db (assoc (initial-db) :user-notes storage-user-notes)}
      (when-let [initial-month (nth (loader/gMonthlies-cljs) js/initialSearchMoIdx)]
        {:dispatch [:month-set (:hnId initial-month)]}))))

