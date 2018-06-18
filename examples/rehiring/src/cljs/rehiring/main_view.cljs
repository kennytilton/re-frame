(ns rehiring.main-view
  (:require
    [reagent.core :as rgt]
    [re-frame.core :as rfr]
    [rehiring.utility :as utl]
    [rehiring.subs :as subs]
    [rehiring.utility :as utl]
    [rehiring.month-loader :as jld]
    [rehiring.month-loader-views :as mlv]
    [rehiring.job-list :as jls]
    [rehiring.control-panel :as cp]
    ))

(declare app-banner)

(defn main-panel []
  [:div
   [app-banner]
   [:div {:style {:margin 0 :background "#ffb57d"}}
    ;; we default to the current month and let user explore
    ;; a couple of earlier months. Left as an exercise is loading
    ;; all the months and not restricting search by month boundary.
    [mlv/pick-a-month]

    ;; next is a hidden workhorse that spawns an iframe when it finds
    ;; the URL of a page of to be scraped in [:month-load-pask :page-urls] or sth.
    [jld/job-listing-loader]

    ;; and now the interface...
    [cp/control-panel]
    [jls/job-list]]])

(def appHelpEntry
  (map identity
    ["Click any job header to show or hide the full listing."
     "Double-click job description to open listing on HN in new tab."
     "All filters are ANDed except as you direct within RegExp fields."
     "Your edits are kept in local storage, so stick to one browser."
     "Works off page scrapes taken often enough. E-mail <a href='mailto:kentilton@gmail.com'>Kenny</a> if they seem stopped."
     "RFEs welcome and can be raised <a href='https://github.com/kennytilton/whoshiring/issues'>here</a>. "
     "Built with <a href='https://github.com/kennytilton/matrix/blob/master/js/matrix/readme.md'>Matrix Inside</a>&trade;."
     "This page is not affiliated with Hacker News, but..."
     "..thanks to the HN crew for their assistance. All screw-ups remain <a href='https://news.ycombinator.com/user?id=kennytilton'>kennytilton</a>'s."
     "Graphic design by <a href='https://www.mloboscoart.com'>Michael Lobosco</a>."]))

(defn app-banner []
  (let [helping (rgt/atom false)]
    (fn []
      [:div {:style {:background "PAPAYAWHIP"}}
       [:header
        [:div.about {
               :title    "Usage hints, and credit where due."
               :on-click #(reset! helping (not @helping))}
         "Pro Tips"]
        [:div.headermain
         [:span.askhn "Ask HN:"]
         [:span.who "Who Is Hiring?"]]]
       [utl/help-list appHelpEntry helping]])))

