(ns utils.core
  (:require
    [clojure.string :as str :refer (split-lines join)]))


(defn- line-type
  [line]
  (if (re-matches #"\[.+\]" line) :section
    (if (re-matches #".+=.*" line) :entry
      :ignore)))

(defn- merge-last-section
  [section line]
  (let [last-ix (dec (count section))
        last-section (last section)]
    (update-in section [last-ix] #(conj % line))))

(defn- section-name
  [line]
  (let [[_ name] (re-matches #"\[(.+)\]" line)]
    name))

(defn- split-entry
  [line]
  (let [[_ name val] (re-matches #"(.+)=(.*)" line)]
    [name (or val "")]))

(defn- ini-collector
  [inidata line]
  (case (line-type line)
    :section (conj inidata [(section-name line)])
    :entry (merge-last-section inidata (split-entry line))
    inidata))

(defn parse-ini
  "Parse an ini-file.
  Return a vector of [SECTION ENTRY1 ENTRY2...]
  "
  [inifile]
  (->> (split-lines inifile)
       (reduce ini-collector [])))

(defn mk-ini-section
  ([title]
   (str "[" title "]"))
  ([title & args]
   (mk-ini-section (str title (apply str args)))))

(defn mk-ini-entry
  [title value]
  (str title "=" value))

(defn join-lines
  "Join two strings as multi-line text.
  Or join an array of strings as well.
  "
  ([a b]
   (join-lines [a b]))
  ([lines]
  (str/join "\n" lines)))
