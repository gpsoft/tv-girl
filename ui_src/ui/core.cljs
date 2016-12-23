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
             #_println))))

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

(defn click-title
  [ev item]
  (swap! app-state assoc :editing item))

(defn index-in-pglist
  [id]
  (let [ixs (keep-indexed #(when (= (:id %2) id) %1) (:play-list @app-state))]
    (first ixs)))

(defn size-dial
  [id delta]
  (swap! app-state dissoc :editing)
  (swap! app-state update-in [:play-list (index-in-pglist id) :size]
         #(let [new-size (+ % delta)]
           (if (>= new-size 0) new-size 0))))

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
   [:span.glyphicon.glyphicon-minus-sign]])

(defn row-handle
  [item]
  [:div.row-control.pull-right
   [:div.size-dial
    [:div.size-up
     {:on-click #(size-dial (:id item) 1)}
     [:span.glyphicon.glyphicon-triangle-top]]
    [:div.size-down
     {:on-click #(size-dial (:id item) -1)}
     [:span.glyphicon.glyphicon-triangle-bottom]]]
   [:div.sort-handle
    [:span.glyphicon.glyphicon-menu-hamburger]]
   [:div.row-del
    {:on-click click-row-del}
    "削除"]
   ])

(defn finish-editing
  [id ev]
  (let [new-title (-> ev .-currentTarget .-value)]
    (swap! app-state assoc-in [:play-list (index-in-pglist id) :title] new-title))
  (swap! app-state dissoc :editing))

(defn pg-title
  [item]
  (let [editing-item (:editing @app-state)]
    (if (= item editing-item)
      [:div.pg-title
       [:input
        {:type "text"
         :name "pg-title"
         :size 15
         :defaultValue (:title editing-item)
         :on-blur #(finish-editing (:id editing-item) %)}]]
      [:div.pg-title
       {:on-click #(click-title % item)}
       (str (:title item) " (" (:size item) ")")]))
  )

(defn pg-tr
  [item]
  [:tr.program
   (tv/pg->attr item)
   [:td
    [del-mark]
    [pg-title item]
    [row-handle item]]])

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

(defn pg-sorted
  [_ _]
  (let [pg-list (->> (js/$ "#play-list tr")
                  .toArray
                  (mapv tv/dom->cljs))]
    (swap! app-state assoc :play-list pg-list)))

(defn pg-table
  []
  (reagent/create-class
    {:component-did-mount
     (fn [this]
       #_(println "did-mount")
       (-> this
           reagent/dom-node
           js/$
           (.sortable #js {:axis "y"
                           :cursor "move"
                           :scroll true
                           :items "tr"
                           :handle ".sort-handle"
                           :helper sortable-helper
                           :stop pg-sorted
                           })))
     ; :component-did-update
     ; (fn [this]
     ;   (when-let [editing-item (:editing @app-state)]
     ;     (-> (js/$ ".pg-title input")
     ;         .focus)))
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
