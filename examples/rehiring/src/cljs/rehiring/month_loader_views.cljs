(ns rehiring.month-loader-views
  (:require [rehiring.month-loader :as loader]
            [rehiring.utility :refer [<sub >evt target-val] :as utl]
            [re-frame.core
             :refer [subscribe reg-sub dispatch reg-event-db reg-event-fx]
             :as rfr]
            [cljs.pprint :as pp]))

;;; --- The pick-a-month widget, sub-components first  ---------------------

(defn month-selector []
  ; The month <select> pop-up itself, driving and
  ; driven by the :month-hn-id subscription. See event-fx :rehiring.db/initialize-db
  ; for how we control which month loads on app start-up.

  (into [:select.searchMonth
         {
          :value     (or (<sub [:month-hn-id]) "")
          :on-change #(>evt [:month-set (utl/target-val %)])}]
    (map #(let [{:keys [hnId desc]} %]
            [:option {:value hnId} desc])
      (loader/gMonthlies-cljs))))

(defn hn-month-link []
  ;; An HN icon <a> tag linking to the actual HN page.

  [utl/view-on-hn {}
   (pp/cl-format nil "https://news.ycombinator.com/item?id=~a" (<sub [:month-hn-id]))])

(defn month-jobs-total []
  ;; A simple <span> announcing the job total once the load is complete

  [:span {:style  {:color  "#fcfcfc"
                   :margin "0 12px 0 12px"}
          :hidden (not (<sub [:month-load-fini?]))}
   (str "Total jobs: " (count (<sub [:month-jobs])))])

;;; -------------------------------------------------------------------
;;; --- The star of the show ------------------------------------------
;;; -------------------------------------------------------------------

(defn month-load-progress-bar []
  ;; A progress element preceded by a label since
  ;; this one bar shows the progress in turn of two phases, one
  ;; in which we load and collect nodes of class 'aThing' and
  ;; a second in which we scan those for job listings (replies are
  ;; also 'aThings' and parse those into descriptions later used
  ;; for rendering our own list.
  ;;
  ;; Yes, that complexified things a bit, but not too badly. See
  ;; the full story of implementing the progress bar in
  ;; the source rehiring.month-loader.cljs.

  (fn []
    (let [[phase max progress] (<sub [:month-progress])]

      [:div {:hidden (= phase :fini)}
       [:span
        (case phase
          :cull-athings "Scrape nodes "
          :parse-jobs "Parse jobs "
          "")]
       [:progress
        {:value progress
         :max   max}]])))

;;; ...supporting subscriptions

(rfr/reg-sub :month-progress
  ;; returns [phase max val(progress)]
  (fn [[_] _]
    (rfr/subscribe [:month-load-task]))
  (fn [{:keys [phase page-url-count page-urls-remaining athings jobs]}]
    (concat [phase]
      (if (= :cull-athings phase)
        [page-url-count (- page-url-count (count page-urls-remaining))]
        [(count athings) (count jobs)]))))

(reg-sub :month-load-fini?
  (fn [db]
    (= :fini
      (get-in db [:month-load-task :phase]))))

;;; --- the big picture -----------------------------------------------
;;; select a month and watch it load

(defn pick-a-month []
  [:div.pickAMonth
   [month-selector]

   [:div {:style utl/hz-flex-wrap}
    [hn-month-link]
    [month-jobs-total]
    [month-load-progress-bar]]])
