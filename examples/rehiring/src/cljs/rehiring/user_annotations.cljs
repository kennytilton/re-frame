(ns rehiring.user-annotations
  (:require
    [goog.string :as gs]
    [reagent.core :as rgt]
    [re-frame.core :refer [reg-sub subscribe] :as rfr]
    [rehiring.utility :refer [<sub] :as utl]))


;;; --- utilities -----------------------------------

(defn db-unotes-ensure [db hn-id]
  (if (contains? (:user-notes db) hn-id)
    db (assoc-in db [:user-notes hn-id] {:hn-id        hn-id
                                         :stars        0
                                         :notes        ""
                                         :note-editing false})))

(defn unote->local-storage
  [unote]
  (let [nkey (str utl/ls-key "-unotes-" (:hn-id unote))]
    (.setItem js/localStorage nkey (str unote))))

;;; --- re-frame-ese ---------------------------------

(rfr/reg-sub :user-notes
  (fn [db [_]]
    (:user-notes db)))

(rfr/reg-sub :unotes
  (fn [db [_ hn-id]]
    (get-in db [:user-notes hn-id])))

(rfr/reg-sub :unotes-prop
  ;; signal fn
  (fn [[_ hn-id prop] _]
    (subscribe [:unotes hn-id]))

  ;; compute
  (fn [unotes [_ id prop]]
    (get unotes prop)))

(rfr/reg-event-fx :unotes-prop-toggle
  (fn [{:keys [db]} [_ hn-id property]]
    (let [new-db (update-in (db-unotes-ensure db hn-id)
                   [:user-notes hn-id property] not)]
      {:db       new-db
       :dispatch [:persist-unote hn-id (get-in new-db [:user-notes hn-id])]})))

(rfr/reg-event-fx :unotes-prop-set
  (fn [{:keys [db]} [_ hn-id property new-value]]
    (let [new-db (assoc-in (db-unotes-ensure db hn-id)
                   [:user-notes hn-id property] new-value)]
      {:db       new-db
       :dispatch [:persist-unote hn-id (get-in new-db [:user-notes hn-id])]})))

(rfr/reg-event-fx :persist-unote
  (fn [cfx [_ hn-id unotes]]
    (unote->local-storage unotes)))

;;; --- components -----------------------------------

(def MAX-STARS 3)

(defn job-stars []
  (fn [job]
    [:div {:style utl/hz-flex-wrap-bottom}
     (let [j-stars (or (<sub [:unotes-prop (:hn-id job) :stars]) 0)]
       (for [sn (range MAX-STARS)]
         ^{:key sn} [:span {:style    {:cursor "pointer"
                                       :color  (if (>= j-stars (inc sn)) "red" "gray")}
                            :on-click (fn [e]
                                        #_(println :bamchg-stars
                                            sn (.-checked (.-target e)))
                                        (rfr/dispatch [:unotes-prop-set (:hn-id job) :stars
                                                       (if (= sn (dec j-stars))
                                                         0 (inc sn))]))}
                     (utl/unesc "&#x2605;")]))]))

(defn applied [job]
  (fn [job]
    (let [input-id (str "applied?" (:hn-id job))]
      [:div {:style utl/hz-flex-wrap-centered}
       [:input {:id        input-id
                :type      "checkbox"
                :style     {:margin-left "18px"}
                :checked   (<sub [:unotes-prop (:hn-id job) :applied])
                :on-change #(do                             ;;(println :applied-change!!!!! (.-value (.-target %)))
                              (rfr/dispatch [:unotes-prop-toggle (:hn-id job) :applied]))
                }]
       [:label {:for   input-id
                :style {:color (if (<sub [:unotes-prop (:hn-id job) :applied])
                                 "red" "black")}}
        "Applied"]])))

(defn exclude-job [job]
  (fn [job]
    (let [excluded? (<sub [:unotes-prop (:hn-id job) :excluded])]
      [:span {:style    {:color       (if excluded? "red" "black")
                         :font-size   "1em"
                         :font-weight (if excluded? "bolder" "lighter")
                         :margin      "4px 4px 8px 0"}
              :on-click #(rfr/dispatch [:unotes-prop-toggle (:hn-id job) :excluded])}
       (utl/unesc "&#x20E0;")])))

(defn note-editor [job]
  (let [set-notes (fn [e]
                    (rfr/dispatch [:unotes-prop-set (:hn-id job) :notes (.-value (.-target e))]))]
    (fn [job]
      [:textarea {:style        {:padding "8px"
                                 :margin  "0 12px 0 12px"
                                 :cols    20
                                 :display (if (<sub [:unotes-prop (:hn-id job) :note-editing])
                                            "flex" "none")}
                  :placeholder  "Your notes here"

                  :on-key-press #(when (= "Enter" (js->clj (.-key %)))
                                   (set-notes %))

                  :on-blur      set-notes
                  :defaultValue (or (<sub [:unotes-prop (:hn-id job) :notes]) "")}])))

(defn note-toggle [job]
  (fn [job]
    (let [notes (or (<sub [:unotes-prop (:hn-id job) :notes]) "")]
      [:span {:style    {:cursor      "pointer"
                         :margin-left "18px"
                         :color       (if (pos? (count notes)) "red" "black")}
              :title    "Show/hide editor for your own notes"
              :on-click #(rfr/dispatch [:unotes-prop-toggle (:hn-id job) :note-editing])}
       "Notes"])))

;;; --- main ------------------------------------------------------


(defn user-annotations []
  (fn [job]
    [:div {:style {:display        "flex"
                   :flex-direction "column"}}
     [:div {:class "userAnnotations"}
      [job-stars job]
      [applied job]
      [note-toggle job]
      [exclude-job job]]
     [note-editor job]]))

