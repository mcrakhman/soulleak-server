(ns soulleak.handler
  (:require [compojure.core :refer :all]
            [soulleak.session :as session]
            [clojure.java.io :as io]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :refer [content-type response resource-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [me.raynes.fs :refer :all]
            [clojure.string :as string]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

;; Constants

(def site-url "http://localhost:3000/")

;; Helpers

(defn string-to-boolean [string] (if (= string "true") true false))

(defn create-new-session-response [] (response {:session-id (session/create-session)}))

(defn handle-image-request [session-id needs-gif]
  (if (not (session/is-valid-session-id? session-id)) 
    (response {:error "Image not found"})
    (-> (session/take-images-and-gifs session-id needs-gif)
        ((partial assoc {} :image-url))
        (response))))

(defn allow-cross-origin  
  "middleware function to allow crosss origin"  
  [handler]  
  (fn [request]  
    (let [response (handler request)]  
    (assoc-in response [:headers "Access-Control-Allow-Origin"] "*"))))    

;; Routes

(defroutes app-routes
  (POST "/new-session" [] (create-new-session-response))
  (GET "/image-pack/:session-id" [session-id needs-gif] (handle-image-request session-id (string-to-boolean needs-gif)))
  (route/resources "/")
  (route/not-found "Not Found")) 

(def app 
  (-> app-routes
    (wrap-json-body)
    (wrap-json-response)
    (wrap-defaults api-defaults)
    (allow-cross-origin)))
