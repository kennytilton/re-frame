(ns rehiring.job-list
  (:require [re-frame.core :as rfr]
            [rehiring.events :as evt]
            [rehiring.filtering :as flt]
            [rehiring.user-annotations :as unt]
            [goog.string :as gs]
            [cljs.pprint :as pp]
            [rehiring.utility :refer [<sub >evt target-val] :as utl]))

(declare job-header job-details)

(defn job-list-sort [jobs]
  (let [{:keys [key-fn comp-fn order prep-fn]} (<sub [:job-sort])]
    (sort (fn [j k]
            (if comp-fn
              (comp-fn order j k)
              (* order (if (< (key-fn j) (key-fn k)) -1 1))))
      (map (or prep-fn identity) jobs))))

(defn jump-to-hn [hn-id]
  (.open js/window (pp/cl-format nil "https://news.ycombinator.com/item?id=~a" hn-id) "_blank"))

(defn job-list-item []
  (fn [job-no job]
      [:li {:style {:cursor     "pointer"
                    :display    (let [excluded (<sub [:unotes-prop (:hn-id job) :excluded])]
                                  (if (and excluded
                                           (not (<sub [:show-filtered-excluded]))
                                           (not (<sub [:filter-active "Excluded"])))
                                    "none" "block"))
                    :padding    "12px"
                    :background (if (zero? (mod job-no 2))
                                  "#eee" "#f8f8f8")}}
       [job-header job]
       [job-details job]]))

(defn job-list []
  (fn []
    [:ul {:style {:list-style-type "none"
                  :background      "#eee"
                  ;; these next defeat gratuitous default styling of ULs by browser
                  :padding         0
                  :margin          0}}
     (doall (map (fn [jn j]
                   ^{:key (:hn-id j)} [job-list-item jn j])
              (range)
              (take (<sub [:job-display-max])
                (job-list-sort (<sub [:jobs-filtered])))))]))

(defn job-details []
  (fn [job]
    (let [deets (<sub [:show-job-details (:hn-id job)])]
      [:div {:class (if deets "slideIn" "slideOut")
             :style {:margin     "6px"
                     :background "#fff"
                     :display    (if deets "block" "none")}}
       [unt/user-annotations job]
       [:div {:style           {:margin   "6px"
                                :overflow "auto"}
              :on-double-click #(jump-to-hn (:hn-id job))}
        (when (and (not (<sub [:job-collapse-all]))
                   deets)
          (map (fn [x node]
                 (case (.-nodeType node)
                   1 ^{:key (str (:hn-id job) "-p-" x)} [:p (.-innerHTML node)]
                   3 ^{:key (str (:hn-id job) "-p-" x)} [:p (.-textContent node)]
                   ^{:key (str (:hn-id job) "-p-" x)} ;; todo try just x
                   [:p (str "Unexpected node type = " (.-nodeType node))]))
            (range)
            (:body job)))]])))

(defn job-header []
  (fn [job]
    [:div {:style    {:cursor  "pointer"
                      :display "flex"}
           :on-click #(>evt [::evt/toggle-show-job-details (:hn-id job)])}
     [:span {:style {:color        "gray"
                     :max-height   "16px"
                     :margin-right "9px"
                     :display      "block"}}
      (utl/unesc "&#x2b51")]
     [:span {
             ;;:on-click #(>evt [::evt/toggle-show-job-details (:hn-id job)])
             }
      (:title-search job)]]))

