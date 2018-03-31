(ns soulleak.handler
  (:require [compojure.core :refer :all]
            [clojure.java.io :as io]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :refer [content-type response resource-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [me.raynes.fs :refer :all]
            [clojure.string :as string]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(def site-url "http://localhost:3000/")

(def resources-images-path "resources/public")
(def resources-audio-path "resources/audio")
(def session-ids [])
(def most-recent-session 0)
(def total-images-count (count (list-dir resources-images-path)))
(def number-of-images-in-pack 4)


(defn fill-default-images-array [session-id]
  (def session-image-map 
    (assoc
    session-image-map
    (keyword (str session-id))
    (into [] (range 0 total-images-count)))))

(defn create-session []
  (def most-recent-session (+ most-recent-session 1))
  (def session-ids (conj session-ids most-recent-session))
  (println "Created session")
  (fill-default-images-array most-recent-session)
  most-recent-session)

(def session-image-map {})

(defn parse-int [string] (-> string Double/parseDouble long))

(defn remove-image-from-session [session-id number]
  (let [session-index (keyword (str session-id))
        previous-session-array (session-index session-image-map)]
  (def session-image-map
      (assoc
      session-image-map
      session-index
      (into [] (filter #(not= number %) previous-session-array))))))

(defn remove-images-from-session [session-id numbers]
  (let [session-index (keyword (str session-id))
        previous-session-array (session-index session-image-map)]
  (def session-image-map
      (assoc
      session-image-map
      session-index
      (into [] (filter #(not (.contains numbers %)) previous-session-array))))))
      
(defn add-image-to-session [session-id number]
  (let [session-index (keyword (str session-id))
        previous-session-array (session-index session-image-map)]
  (def session-image-map
      (assoc
      session-image-map
      session-index
      (into [] (conj previous-session-array number))))))

(defn remove-session [n]
  (def session-ids (filter #(not= n %) session-ids)))

(defn file-in-folder [folder index]
  (-> (list-dir folder)
      (nth index)
      (.getName)))

(defn files-in-folder [folder indices]
  (let [filepaths (list-dir folder)]
    (map #(.getName %) (map #(nth filepaths %) indices))))

(defn get-image-ratio [filename]
  (let [path (str "resources/public/" filename)]
    (with-open [r (java.io.FileInputStream. path)]
      (let [image (javax.imageio.ImageIO/read r)]
        (/ (.getWidth image) (.getHeight image))))))
    
(defn jpg-resource [filename]
  (-> (response (io/file (str "resources/public/" filename)))
      (content-type "image/jpg")))
 
(defn image-url [filename]
  (str site-url "images/" filename))

(defn swap [v i1 i2] (assoc v i2 (v i1) i1 (v i2)))

(defn generate-random-numbers [max amount]
  (let [random-array (into [] (range 0 max))]
    (loop [result []
           current-amount (min max amount)
           current-array random-array]
      (if (= current-amount 0)
          result
          (let [array-count (count current-array)
                random (rand-int (- array-count (count result)))]
            (recur (conj result (nth current-array random))
                   (dec current-amount)
                   (swap current-array random (- array-count (count result) 1))))))))

(defn handle-images-pack-request [session-id amount]
  (if (.contains session-ids session-id)
    (let [session-index (keyword (str session-id))
          previous-session-array (session-index session-image-map)
          random-numbers (generate-random-numbers (count previous-session-array) amount)
          random-file-numbers (map #(nth previous-session-array %) random-numbers)
          random-files (filter #(string/ends-with? % ".jpg") (files-in-folder resources-images-path random-file-numbers))
          random-file-map (map #(assoc {} :file % :ratio (get-image-ratio %)) random-files)]
            (remove-images-from-session session-id random-file-numbers)
            (if (<= (count previous-session-array) (* amount 2))
              (fill-default-images-array session-id)) 
            (response {:image-url random-file-map}))
    (response {:error "Image not found"})))

(defn allow-cross-origin  
  "middleware function to allow crosss origin"  
  [handler]  
  (fn [request]  
    (let [response (handler request)]  
    (assoc-in response [:headers "Access-Control-Allow-Origin"]  
          "*"))))    

(defn all-files-in-folder [amount]
  (->> (list-dir resources-images-path)
        (shuffle)
        (filter #(string/ends-with? % ".jpg"))
        (take amount)
        (map #(.getName %))))

(defroutes app-routes
  (POST "/new-session" [] (response {:session-id (str (create-session))}))
  (GET "/image-pack/:session-id" [session-id] (handle-images-pack-request (parse-int session-id) number-of-images-in-pack))
  ;(GET "/images/:name" [name] (jpg-resource name))
  (route/resources "/images/")
  (route/not-found "Not Found")) 

(def app 
  (-> app-routes
    (wrap-json-body)
    (wrap-json-response)
    (wrap-defaults api-defaults)
    (allow-cross-origin)))
