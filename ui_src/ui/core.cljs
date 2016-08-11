(ns ui.core
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.string :as gstring]
            [clojure.string :as string :refer [split-lines]]))

(def join-lines (partial string/join "\n"))

(enable-console-print!)

(defonce state        (atom 0))
(defonce shell-result (atom ""))
(defonce command      (atom ""))

(defonce proc (js/require "child_process"))

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

(defn root-component []
  [:div
   [:div.container
    [:button.btn.btn-danger
     {:id "theModalBtn"
      :data-toggle "modal"
      :data-target "#modalDlg"}
     "Push me!"]]
   [:div.modal.fade {:id "modalDlg"}
    [:div.modal-dialog
     [:div.modal-content
      [:div.modal-header
       [:button.close
        {:type "button"
         :data-dismiss "modal"}
        (gstring/unescapeEntities "&times;")]
       [:h2.modal-title
        "Header"]]
      [:div.modal-body
       [:p "Hey, tv girl"]]]]]

   #_[:button
    {:on-click #(swap! state inc)}
    (str "Clicked " @state " times")]

   #_[:p
    [:form
     {:on-submit (fn [e]
                   (.preventDefault e)
                   (run-process))}
     [:input#command
      {:type :text
       :on-change (fn [e]
                    (reset! command
                            (.-value (.-target e))))
       :value @command
       :placeholder "type in shell command"}]]]
   #_[:pre (join-lines (take 100 (reverse (split-lines @shell-result))))]])

(reagent/render
  [root-component]
  (.-body js/document))
