(ns ui.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.string :as gstring]
            [cljs.core.async :as async :refer [>! <! chan]]
            [clojure.string :as string :refer [split-lines]]
            [utils.core :refer [parse-ini]]
            [tv-girl.core :as tv]))

(def pglist-path "./WinDVR.prog")
(def saved-pglist-path "./saved.prog")

(enable-console-print!)

(defonce app-state (atom {}))
(defonce state        (atom 0))
(defonce shell-result (atom ""))
(defonce command      (atom ""))

(defonce proc (js/require "child_process"))
(defonce fs (js/require "fs"))
(defonce util (js/require "util"))

(comment
  (def join-lines (partial string/join "\n"))

  (let [st (.statSync fs ".")]
    (println (.inspect util st)))
  (let [dir (.readdirSync fs ".")]
    (doall (map println dir)))
  (let [readme (.readFileSync fs "./README.md")]
    (println readme))
  #_(let [readme (clojure.core/slurp "./README.md")]
      (println readme))
  )

(let [c (chan)]
  (.readFile fs pglist-path "utf8" (fn [err res] (go (>! c res))))
  (go (let [pglist (<! c)]
        (->> pglist
             parse-ini
             tv/parse-pglist
             #_(swap! app-state (fn [state pglist] (update-in state [:play-list] #(identity %2) pglist)))
             (swap! app-state assoc :play-list)
             (:play-list)
             (map :title)
             println))))

(defn save-play-list
  []
  (let [trs (.toArray (js/$ "#play-list tr"))]
    (->> trs
         (map tv/dom->cljs)
         tv/compose-pglist
         (.writeFileSync fs saved-pglist-path))))
(comment (save-play-list))

(defn append-to-out [out]
  (swap! shell-result str out))

(defn run-process []
  (when-not (empty? @command)
    (println "Running command" @command)
    (let [[cmd & args] (string/split @command #"\s")
          js-args (clj->js (or args []))
          p (.spawn proc cmd js-args)]
      (.on p "error" (comp append-to-out
                           #(str % "\n")))
      (.on (.-stderr p) "data" append-to-out)
      (.on (.-stdout p) "data" append-to-out))
    (reset! command "")))

#_(-> (js/$ "h2.modal-title")
      (.css (clj->js {:display "none"})))

#_(reset! app-state {:play-list
                     [{:title "apple"}
                      {:title "orange"}
                      {:title "banana"}]})

(defn show-del-btn
  []
  (this-as
    this
    (-> this
        js/$
        (.parents "td")
        (.find ".row-control .row-del")
        (.show "fast"))))

(defn show-del-mark
  []
  (this-as
    this
    (-> this
        js/$
        (.parents "td")
        (.find ".del-mark")
        (.show "fast"))))

(defn click-del-mark
  [ev]
  (println ev.target)
  (-> ev
      .-currentTarget
      js/$
      (.hide "fast" show-del-btn)))

(defn click-row-del
  [ev]
  (let [sure (js/confirm "Are you sure?")
        $target (-> ev
                    .-currentTarget
                    js/$)
        $tr (.parents $target "tr")]
    (if sure
      (.remove $tr)
      (.hide $target "fast" show-del-mark))))

(defn col3-row
  [left middle right]
  [:div.row
   [:div.col-sm-5
    left]
   [:div.col-sm-2
    middle]
   [:div.col-sm-5
    right]])

(defn middle-btn
  [caption fn-click]
  [:div.text-center
   [:button.btn-success.middle-btn
    {:on-click fn-click}
    caption]])

(defn title-row
  []
  [col3-row
   [:h2 "プレイリスト"]
   [:div]
   [:h2 "未登録ファイル"]])

(defn bordered-table
  [id body]
  [:table.table.table-bordered
   {:id id}
   body])

(defn del-mark
  []
  [:div.del-mark
   {:on-click click-del-mark}
   [:span.glyphicon.glyphicon-minus]])

(defn row-handle
  []
  [:div.row-control.pull-right
   [:div.row-del.pull-right
    {:on-click click-row-del}
    "削除"]
   [:div.sort-handle.pull-right
    [:span.glyphicon.glyphicon-sort]]])

(defn pg-tr
  [item]
  [:tr.program
   (tv/pg->attr item)
   [:td
    [del-mark]
    [:div.pg-title
     (:title item)]
    [row-handle]]])

(defn sortable-helper
  [_ $ui]
  (let [$orgChildren (.children $ui)
        $helper (.clone $ui)]
    (-> $helper
        .children
        (.each (fn [i e]
                 (let [w (-> $orgChildren
                             (.eq i)
                             .width)]
                   (-> e
                       js/$
                       (.width w))))))
    $helper))

(defn pg-table
  []
  (reagent/create-class
    {:component-did-mount
     (fn [this]
       (println "did-mount")
       (-> this
           reagent/dom-node
           js/$
           (.sortable #js {:axis "y"
                           :cursor "move"
                           :scroll true
                           :items "tr"
                           :helper sortable-helper
                           })))
     :reagent-render
     (fn []
       [bordered-table
        "play-list"
        [:tbody
         (for [item (:play-list @app-state)]
           ^{:key item}
           [pg-tr item])]])}))

(defn unlisted-table
  []
  [bordered-table
   "unlisted"
   [:tbody
    [:tr.program [:td "hoge"]]
    [:tr.program [:td "fuga"]]
    [:tr.program [:td "piyo"]]
    ]])

(defn table-row
  []
  [col3-row
   [pg-table]
   [:div
    [middle-btn
     "保存"
     #(save-play-list)]
    [middle-btn
     "インポート"
     (fn [] nil)]]
   [unlisted-table]])

(defn container
  []
  [:div.container
   [title-row]
   [table-row]])

(defn root-component []
  [container])

(reagent/render
  [root-component]
  (.getElementById js/document "reagentTarget"))
