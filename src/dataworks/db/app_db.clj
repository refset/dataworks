(ns dataworks.db.app-db
  (:require
   [clojure.java.io :as io]
   [dataworks.common :refer :all]
   [crux.api :as crux]
   [mount.core :refer [defstate]]))

(defn internal-kafka-settings []
  (if-let [settings (-> "config.edn"
                        slurp
                        read-string
                        :internal-kafka-settings)]
    settings
    {:crux.kafka/bootstrap-servers "localhost:9092"
     :crux.kafka/tx-topic (str "dataworks-internal."
                               "crux-transaction-log")
     :crux.kafka/doc-topic "dataworks-internal.crux-docs"}))

(defstate app-db
  :start
  (crux/start-node
   (merge
    {:crux.node/topology '[crux.kafka/topology
                           crux.kv.memdb/kv-store]} ;; can't rocksdb twice
    (internal-kafka-settings)))
  :stop
  (.close app-db))

(defn get-stored-function
  ([eid]
   (let [db (crux/db app-db)]
     (crux/entity db eid)))
  ([name function-type]
   (get-stored-function
    (get-entity-param name function-type))))

(defn get-stored-functions
  ([]
   (map (comp get-stored-function first)
        (crux/q (crux/db app-db)
                {:find '[e]
                 :where [['e :stored-function/type]]})))
  ([function-type]
   (map (comp get-stored-function first)
        (crux/q (crux/db app-db)
                {:find '[e]
                 :where [['e :stored-function/type
                          function-type]]}))))

(defn function-already-exists?
  [{:keys [name] :as params} function-type]
  (println "checking for duplicate" name)
  (if (get-stored-function name function-type)
    {:status :failure
     :message (generate-message function-type
                                "%-already-exists")}
    params))

(defn add-current-stored-function
  "Takes the map received by the endpoints for creation
   and modification of stored functions and returns a
   vector containing that map, as well as a map of the
   current stored function. Useful for threading through
   functions which require both the new stored function
   and the current stored function for comparison."
  [{:keys [name] :as params} function-type]
  (println "Adding current" function-type ":" name ".")
  (if-let [current (get-stored-function
                    name function-type)]
    [params (get-stored-function name function-type)]
    {:status :failure
     :message :stored-function-does-not-exist
     :details (str "The " (stringify-keyword function-type)
                   ": " name " doesn't exist yet. "
                   "You have to create it before you "
                   "can update it.")}))

(defn added-to-db?
  [params]
  (println "adding-to-db")
  (let [db-fn (first params)
        success [db-fn (last params)]]
    (try
      (let [tx (cond (> 3  (count params))
                     [:crux.tx/put db-fn]
                     :else
                     [:crux.tx/cas
                      (second params)
                      db-fn])]
        (println tx)
        (println (crux/await-tx app-db
                       (crux/submit-tx app-db [tx]))))
      success
      (catch Exception e
        {:status :failure
         :message :db-failed-to-update
         :details (.getMessage e)}))))
