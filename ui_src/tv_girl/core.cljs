(ns tv-girl.core
  (:require
    [clojure.string :refer [split join]]
    [utils.core :refer [mk-ini-section mk-ini-entry join-lines]]))

(defn- join-sections
  ([a b]
   (join-sections [a b]))
  ([sections]
   (->> sections
        (interpose "")
        join-lines)))
(comment
  (join-sections "a" "b"))

(defn- section-type
  [[line]]
  (if (= line "PROG_MGR") :pglist-head
    (if (re-matches #"Prog_\d+(_List)?" line) :pg-head
      :pg-body)))

(defn- merge-to-last-pg
  [pglist section]
  (let [last-ix (dec (count pglist))]
    (update-in pglist [last-ix] #(conj % section))))

(defn- pglist-collector
  [pglist section]
  (case (section-type section)
    :pglist-head (conj pglist section)
    :pg-head (conj pglist [section])
    :pg-body (merge-to-last-pg pglist section)
    :blank pglist
    pglist
    ))

(defn- parse-pg
  [ix [[_ & pairs] & sub-sections]]
  (let [m (apply hash-map (flatten pairs))]
    {:id (inc ix)
     :size (count sub-sections)  ;; this can be zero.
     :title (m "ProgName")
     :path (m "ProgPath")}))

(defn parse-pglist
  [inidata]
  (->> inidata
       (reduce pglist-collector [])
       (drop 1)
       (map parse-pg (range))))

(defn pg->attr
  [{:keys [id size title path]}]
  {:data-id id
   :data-size size
   :data-title title
   :data-path path})

(defn dom->cljs
  [tr]
  (let [$tr (js/$ tr)]
    {:id (int (.attr $tr "data-id"))
     :size (int (.attr $tr "data-size"))
     :title (.attr $tr "data-title")
     :path (.attr $tr "data-path")}))

(defn- mk-path
  [path ix]
  (if (zero? ix)
    path
    (let [parts (split path #"\.")
          base (join "." (drop-last parts))
          ext (last parts)
          suffix (->> (inc  ix)
                      (str "00")
                      (take-last 2)
                      (apply str))]
      (str base ".-vol-" suffix "." ext))))
(comment (mk-path "v:\\stream\\from_air\\all.mpeg" 1))

(defn- compose-pglist-head
  [n]
  (join-lines
    [(mk-ini-section "PROG_MGR")
     (mk-ini-entry "PROG_NUM" n)]))
(comment (compose-pglist-head 12))

(defn- compose-pg-misc
  []
  (join-lines
    [(mk-ini-entry "ProgLength" 1)
     (mk-ini-entry "CreateTime" 1472850000)
     (mk-ini-entry "MediaType" 52)
     (mk-ini-entry "VideoFormat" 2)]))

(defn- mk-pg-body-composer
  [pix title path]
  (fn [ix]
    (join-lines
      [(mk-ini-section "Prog_" pix "_List_Item_" ix)
       (mk-ini-entry "ProgName" (if (zero? ix) title (str "file" ix)))
       (mk-ini-entry "ProgPath" (mk-path path ix))
       (compose-pg-misc)])))

(defn- compose-pg-single
  [ix title path]
  (println title path)
  (join-lines
    [(mk-ini-section "Prog_" ix)
     (mk-ini-entry "ProgName" title)
     (mk-ini-entry "ProgPath" path)
     (compose-pg-misc)]))

(defn- compose-pg-multi
  [ix title path size]
  (let [head-section
        (join-lines
          [(mk-ini-section "Prog_" ix "_List")
           (mk-ini-entry "Playlist_Size" size)
           (mk-ini-entry "Playlist_Name" title)
           (mk-ini-entry "ProgName" title)
           (mk-ini-entry "ProgPath" path)
           (compose-pg-misc)])
        items (range size)
        compose-pg-body (mk-pg-body-composer ix title path)]
    (->> items
         (map compose-pg-body)
         join-sections
         (join-sections head-section))))

(defn- compose-pg
  [ix {:keys [size title path] :as pg}]
  (if (zero? size)
    (compose-pg-single ix title path)
    (compose-pg-multi ix title path size)))

(defn compose-pglist
  [pglist]
  (let [n (count pglist)
        pglhead (compose-pglist-head n)]
    (->> pglist
         (map compose-pg (range))
         join-sections
         (join-sections pglhead))))
