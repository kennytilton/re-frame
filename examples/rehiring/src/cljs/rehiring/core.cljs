(ns rehiring.core
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [rehiring.main-view :as views]
   [rehiring.config :as config]
   [rehiring.db :as db]
   ))


(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    #_ (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [::db/initialize-db])
  (dev-setup)
  (mount-root))
