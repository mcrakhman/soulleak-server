(ns soulleak.session
    (:require [compojure.core :refer :all]
              [clojure.java.io :as io]
              [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
              [ring.util.response :refer [content-type response resource-response]]
              [ring.middleware.resource :refer [wrap-resource]]
              [me.raynes.fs :refer :all]
              [clojure.string :as string]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

;; Constants

(def resources-path "resources/public")

(def image-path "resources/public/images")

(def gif-path "resources/public/gifs")

(def audio-path "resources/public/audio")

(def audio-key :audio)
(def image-key :images)
(def gif-key :gifs)

(def image-portion-count 4)
(def audio-portion-count 4)
(def gif-portion-count 1)

;; Atoms

(def session-data (atom {}))

;; General helpers

(defn choose-last [x y] y)

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn randomized-range [min max] 
    (-> [] 
        (into (range min max))
        (shuffle)))

(defn insert-at [pos vec item] 
    (apply conj (subvec vec 0 pos) item (subvec vec pos)))

(defn merge-random [vector element]
    (-> (count vector)
        (rand-int)
        (insert-at vector element)))

(defn name-replacement [key] 
    (-> (str key)
        (string/split #":")
        (last)))

;; Database operations

(defn add-nested [key-array doc] (swap! session-data update-in key-array conj doc) doc)
(defn replace-nested [key-array doc] (swap! session-data update-in key-array choose-last doc) doc)

(defn add-reshuffled-array [session-id key n-max]
    (-> []
        (conj (keyword session-id) key)
        (replace-nested (randomized-range 0 n-max))))

;; File helpers

(defn get-image-ratio [filepath]
    (with-open [r (java.io.FileInputStream. filepath)]
        (let [image (javax.imageio.ImageIO/read r)]
            { :width (.getWidth image) :height (.getHeight image) }
        )))

(defn file-in-folder [folder index]
    (-> (list-dir folder)
        (nth index)
        (.getName)))

(defn files-in-folder [folder indices]
    (let [filepaths (list-dir folder)
          last-folder-name (last (string/split folder #"/"))]
        (into [] (map #(str last-folder-name "/" (.getName %)) (map #(nth filepaths %) indices)))))

(defn total-count-in-path [path] (count (list-dir path)))

(defn total-images [] (total-count-in-path image-path))
(defn total-gifs [] (total-count-in-path gif-path))
(defn total-audio [] (total-count-in-path audio-path))

(def all-folder-keys [audio-key gif-key image-key])
(def all-folder-file-amount-map { audio-key total-audio image-key total-images gif-key total-gifs })

(defn reshuffle-images [session-id] (add-reshuffled-array session-id image-key (total-images)))
(defn reshuffle-audio [session-id] (add-reshuffled-array session-id audio-key (total-audio)))
(defn reshuffle-gifs [session-id] (add-reshuffled-array session-id gif-key (total-gifs)))

;; Sessions

(defn create-session []
    (let [session-id (uuid)]
        (doall 
            (map #(add-reshuffled-array session-id % ((% all-folder-file-amount-map))) all-folder-keys))
        session-id))

(defn all-session-ids [] (map name-replacement (keys @session-data)))

(defn is-valid-session-id? [session-id] (.contains (all-session-ids) session-id))

(defn take-reshuffle [amount session-id key]
    (let [initial-array (key ((keyword session-id) @session-data))
          adjusted-amount (min (count initial-array) amount)
          previous-array (if (< (count initial-array) (* amount))
                            (add-reshuffled-array session-id key ((key all-folder-file-amount-map)))
                            initial-array)
          array-count (count previous-array)
          replaced-array (subvec previous-array 0 (- array-count amount))
          returned-array (take-last amount previous-array)]
          (do (replace-nested [(keyword session-id) key] replaced-array)
            (into [] (take-last amount previous-array)))))

(defn take-images [session-id]
    (files-in-folder image-path (into [] (take-reshuffle image-portion-count session-id image-key))))     

(defn take-audio [session-id]
    (files-in-folder audio-path (into [] (take-reshuffle audio-portion-count session-id audio-key))))     
        
(defn add-gif-if-needed [array needs-gif? session-id] 
    (if needs-gif? 
        (apply conj array
            (files-in-folder gif-path (take-reshuffle gif-portion-count session-id gif-key)))
        array))

(defn add-dimensions-data [array]
    (map #(assoc {} :file % :data (get-image-ratio (str resources-path "/" %))) array))

(defn take-images-and-gifs [session-id needs-gif?]
    (-> (take-images session-id)
        (add-gif-if-needed needs-gif? session-id)
        (add-dimensions-data)))
