(ns rehiring.job-listing-control-bar
  (:require [re-frame.core :refer [subscribe] :as rfr]
            [rehiring.utility :refer [<sub >evt target-val] :as utl]))

;;; --- sub-components ---------------------------------------

(defn job-expansion-control []
  (fn []
    (let [mo-jobs (<sub [:month-jobs])]
      [:button {:style    {:font-size "1em"
                           :min-width "128px"
                           ;; display after jobs loaded todo
                           }
                :on-click #(>evt [:toggle-details-visibility-all mo-jobs])}
       (case (<sub [:toggle-details-action])
         "collapse" "Collapse all"
         "expand" "Expand all"
         "hunh all")])))

(defn excluded-count []
  (fn []
    (let [excluded-ct (<sub [:jobs-filtered-excluded-ct])]
      [:span {:style    {:padding-bottom "4px"
                         :cursor         "pointer"
                         :display        "flex"
                         :align-items    "center"
                         :font-size      "1em"
                         :visibility     (if (pos? excluded-ct) "visible" "hidden")
                         :border         (if (<sub [:show-filtered-excluded])
                                           "thin solid red" "none")
                         :title          "Show/hide items you have excluded"}
              :on-click #(>evt [:show-filtered-excluded-toggle])
              }
       (str (utl/unesc "&#x20E0;") ": " excluded-ct)])))

(defn result-max []
  (fn []
    [:div {:style (merge utl/hz-flex-wrap-centered {:margin-right "6px"})}
     [:span "Show:"]
     (let [rmax (<sub [:job-display-max])]
       [:input {:type         "number"
                :defaultValue rmax

                :on-key-press #(when (= "Enter" (js->clj (.-key %)))
                                 (>evt [:set-result-display-max (js/parseInt (target-val %))]))

                :on-blur      #(let [new (target-val %)]
                                 #_(println "blur new" new (js/parseInt new))
                                 (>evt [:set-result-display-max (js/parseInt new)]))

                :style        {:font-size    "1em"
                               :max-width    "48px"
                               :margin-left  "6px"
                               :margin-right "6px"
                               }}])]))

;;; --- the beef --------------------------------------------------

(defn job-listing-control-bar []
  (fn []
    [:div {:class "listingControlBar"}
     [:div {:style utl/hz-flex-wrap-centered}
      ;;; --- match count---------------------------------------------------
      [:span {:style {:font-size    "1em"
                      :margin-right "12px"}}
       (let [jobs (<sub [:jobs-filtered])]
         (str "Jobs: " (count jobs)))]

      [excluded-count]]
     [result-max]
     [job-expansion-control]]))

;;; --- reframe plumbing ------------------------------------------------

(rfr/reg-sub :job-display-max
  (fn [db]
    (:job-display-max db)))

(rfr/reg-event-db :set-result-display-max
  (fn [db [_ rmax]]
    (assoc db :job-display-max rmax)))

(rfr/reg-sub :toggle-details-action
  (fn [db]
    (:toggle-details-action db)))

(rfr/reg-event-db :toggle-details-visibility-all
  (fn [db [_ jobs]]
    (println :toggle-details-visibility-all (count jobs))
    (let [new-deets (into {} (for [hn-id (map :hn-id jobs)]
                               [hn-id (= "expand" (:toggle-details-action db))]))]

      (merge db {:toggle-details-action (case (:toggle-details-action db)
                                          "collapse" "expand"
                                          "expand" "collapse")
                 :show-job-details      new-deets}))))

(rfr/reg-sub :jobs-filtered-excluded-ct
  ;
  ; This is tricky. jobs-filtered includes excluded jobs (!) so we can let
  ; the user show/hide them while browsing a selection. Kinda like when
  ; we search gmail and it says "10 found, 3 more in trash". Sooo...
  ;
  ; This subscription provides the info needed to inform users
  ; how many jobs matched by their search are hidden
  ; because the user has excluded them.
  ;
  ;; signal fn
  (fn [query-v _]
    [(subscribe [:jobs-filtered])
     (subscribe [:user-notes])])

  ;; compute
  (fn [[jobs-filtered user-notes]]
    #_(println :jfilex-sees (count jobs-filtered) (count user-notes))
    (count (filter (fn [j]
                     (get-in user-notes [(:hn-id j) :excluded]))
             jobs-filtered))))
