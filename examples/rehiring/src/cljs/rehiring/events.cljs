;;; A home for uninteresting plumbing

(ns rehiring.events
  (:require
    [re-frame.core :as rfr]
    [rehiring.db :as db]
    [rehiring.month-loader :as jbld]))

(rfr/reg-event-db ::toggle-show-job-details
  (fn [db [_ job-no]]
    (update-in db [:show-job-details job-no] not)))

(rfr/reg-event-db :job-sort-set
  (fn [db [_ new-sort]]
    (assoc db :job-sort new-sort)))

;;; --- filtering -------------------------------------------------------

(rfr/reg-event-db :filter-activate
  (fn [db [_ tag active?]]
    (assoc-in db [:filter-active tag] active?)))

(rfr/reg-event-db :show-filtered-excluded-toggle
  (fn [db [_ active?]]
    (update db :show-filtered-excluded not)))

(rfr/reg-event-db :toggle-key
  (fn [db [_ db-key]]
    (update db db-key not)))