(ns rehiring.subs
  (:require
   [re-frame.core :refer [reg-sub]]))

(reg-sub :month-hn-id
 (fn [db]
   (get-in db [:month-load-task :month-hn-id])))

(reg-sub :job-sort
  (fn [db]
    (:job-sort db)))

(reg-sub :show-job-details
  (fn [db [_ hn-id]]
    (get-in db [:show-job-details hn-id])))

(reg-sub :job-collapse-all
  (fn [db [_ hn-id]]
    (:job-collapse-all db)))

(reg-sub :toggle-key
  (fn [db [_ db-key]]
    (get db db-key)))

(reg-sub :show-filtered-excluded
  (fn [db]
    (:show-filtered-excluded db)))

(reg-sub :show-filters
  (fn [db]
    (:show-filters db)))