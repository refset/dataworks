(ns dataworks.internals
  (:require
   [dataworks.db.user-db :refer [user-db
                                 submit-tx
                                 query
                                 entity]]
   [dataworks.common :refer :all]
   [cheshire.core :as cheshire]
   [crux.api :as crux]
   [dataworks.transactor :refer [transact!]]
   [dataworks.stream-utils :refer [produce!]]
   [tick.alpha.api :as tick]))

(def internal-ns *ns*)

;; This is where the actual internals live.
;; They only live here at runtime.
;; DO NOT PUT CODE IN THIS NAMESPACE.
